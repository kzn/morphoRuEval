package name.kazennikov.morphoRuEval;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import gnu.trove.list.array.TIntArrayList;
import name.kazennikov.IOUtils;
import name.kazennikov.Utils;
import name.kazennikov.alphabet.Alphabet;
import name.kazennikov.dafsa.obsolete.IntFSA;
import name.kazennikov.fsa.BooleanFSABuilder;
import name.kazennikov.fsa.walk.WalkFSABoolean;
import name.kazennikov.logger.Logger;
import name.kazennikov.morph.ISegGuesserSubst;
import name.kazennikov.morph.Paradigm;
import name.kazennikov.trove.TroveUtils;


import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Created on 2/26/17.
 *
 * @author Anton Kazennikov
 */
public class Lemmer {
    private static final Logger logger = Logger.getLogger();
    private WalkFSABoolean endingFSA;
    private WalkFSABoolean stemFSA;

    private WalkFSABoolean dictFSA;

    private int[] endingStartStates;                    // кеш первого перехода для stemFSA endingStartState[endingId] -> nextState
    private int[] endingCache;                          // кеш state[endingFSA] -> endingId


    Alphabet<Subst> substAlphabet = new Alphabet<>();
    Alphabet<String> endingsAlphabet = new Alphabet<>();
    TIntArrayList substFreq = new TIntArrayList();
    AOTFullDict aotDict = new AOTFullDict();


    /**
     * Потенциальный разбор (результат применения предиктивной морфологии)
     */
    public static class Parse {
        public String word;             // анализируемое слово
        public String lemma;            // предсказанная лемма
        public int endState;            // номер состояния КА endingFSA этого разбора
        public int endId;               // индекс набора морфологических признаков
        public int stemState;           // номер состояния КА stemFSA этого разбора
        public int matchLength;         // длина совпавшей части по КА (основа + окончание)
        public int substId;             // индекс нормализующей подстановки
        public float score;             // оценка разбора, по умолчанию 0.0
        public Subst subst;
        public String pos;
        public Map<String, String> feats;

        public Parse(String word, String lemma, int endState, int endId, int stemState, int matchLength, int substId, Subst subst) {
            this.lemma = lemma;
            this.word = word;
            this.endState = endState;
            this.endId = endId;
            this.stemState = stemState;
            this.matchLength = matchLength;
            this.substId = substId;
            this.subst = subst;
            this.pos = subst.pos;
            this.feats = subst.feats;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("lemma", lemma)
                    .add("nfEnding", subst.nfEnding)
                    .add("matchLength", matchLength)
                    .add("score", score)
                    .toString()
                    ;
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) return true;
            if(o == null || getClass() != o.getClass()) return false;
            Parse parse = (Parse) o;
            return endState == parse.endState &&
                    endId == parse.endId &&
                    stemState == parse.stemState &&
                    matchLength == parse.matchLength &&
                    substId == parse.substId &&
                    Float.compare(parse.score, score) == 0 &&
                    Objects.equals(word, parse.word) &&
                    Objects.equals(lemma, parse.lemma) &&
                    Objects.equals(subst, parse.subst) &&
                    Objects.equals(pos, parse.pos) &&
                    Objects.equals(feats, parse.feats);
        }

        @Override
        public int hashCode() {
            return Objects.hash(word, lemma, endState, endId, stemState, matchLength, substId, score, subst, pos, feats);
        }
    }


    int minStemLength = 1;
    int minMatchLength = 2;

    public Lemmer(File dataFile, File cacheFile) throws Exception {

        boolean loaded = false;
        if(cacheFile.exists()) {
            try {
                read(cacheFile);
                loaded = true;
            } catch(Exception e) {

            }
        }

        if(!loaded) {
            compileFromSource(dataFile);
            write(cacheFile);
        }
    }


    public void readAOT(File cacheFile) throws Exception {
        aotDict.read(cacheFile);
    }




    public void read(File f) throws Exception {
        try(ObjectInputStream s = new ObjectInputStream(new FileInputStream(f))) {

            endingFSA = (WalkFSABoolean) s.readObject();
            stemFSA = (WalkFSABoolean) s.readObject();

            dictFSA = (WalkFSABoolean) s.readObject();

            endingStartStates = (int[]) s.readObject();
            endingCache = (int[]) s.readObject();


            substAlphabet = (Alphabet<Subst>) s.readObject();
            endingsAlphabet = (Alphabet<String>) s.readObject();
            substFreq = (TIntArrayList) s.readObject();
        }
    }

    public void write(File f) throws Exception {
        try(ObjectOutputStream s = new ObjectOutputStream(new FileOutputStream(f))) {
            s.writeObject(endingFSA);
            s.writeObject(stemFSA);

            s.writeObject(dictFSA);

            s.writeObject(endingStartStates);
            s.writeObject(endingCache);


            s.writeObject(substAlphabet);
            s.writeObject(endingsAlphabet);
            s.writeObject(substFreq);
        }
    }

    public void compileFromSource(File f) throws IOException {
        BooleanFSABuilder endingsBuilder = new BooleanFSABuilder();
        BooleanFSABuilder stemBuilder = new BooleanFSABuilder();
        BooleanFSABuilder dictBuilder = new BooleanFSABuilder();
        TIntArrayList s = new TIntArrayList();

        try(CorpusReader r = new CorpusReader(f)) {
            while(true) {
                Sentence sent = r.next();

                if(sent == null)
                    break;

                for(int i = 0; i < sent.size(); i++) {
                    String wf = sent.wf(i).toLowerCase().replace('ё', 'е');
                    String lemma = sent.lemma(i).toLowerCase().replace('ё', 'е');
                    String pos = sent.pos(i);

                    if(wf.equals("гор") && lemma.equals("гора")) {
                        wf = wf;
                    }


                    Map<String, String> feats = sent.feats(i);

                    int length = Utils.commonPrefix(wf, lemma);



                    String ending = wf.substring(length);
                    String nfEnding = lemma.substring(length);

                    Subst subst = new Subst(ending, nfEnding, pos, feats);

                    int substId = substAlphabet.get(subst);
                    int endingId = endingsAlphabet.get(ending);

                    if(!Objects.equals(pos, "PUNCT")) {
                        s.resetQuick();
                        TroveUtils.expand(s, wf);
                        s.add(0);
                        s.add(substId);
                        dictBuilder.addMinWord(s);
                    }

                    if(UDConst.CLOSED_POS.contains(pos))
                        continue;


                    while(substFreq.size() < substId + 1) {
                        substFreq.add(0);
                    }

                    substFreq.set(substId, substFreq.get(substId) + 1);

                    s.resetQuick();
                    TroveUtils.expand(s, ending);
                    s.reverse();
                    s.add(0);
                    s.add(endingId);
                    s.add(substId);

                    endingsBuilder.addMinWord(s);
                    String stem = Utils.reverse(wf.substring(0, length));

                    if(stem.isEmpty())
                        continue;

                    for (int j = minStemLength; j < Math.max(Math.min(4, stem.length() + 1), minStemLength + 1); j++) {

                        s.resetQuick();
                        s.add(endingId);

                        TroveUtils.expand(s, stem, 0, j);
                        //s.reverse(1, s.size());
                        s.add(0);
                        s.add(substId);
                        stemBuilder.addMinWord(s);
                    }

                }
            }

        }
        endingFSA = endingsBuilder.build();
        stemFSA = stemBuilder.build();
        dictFSA = dictBuilder.build();
        computeEndingCache();
        System.out.printf("FSA size: dict %d, stem %d, ending: %d%n", dictFSA.size(), stemBuilder.size(), endingsBuilder.size());
    }

    public List<Parse> parseDict(String w) {
        w = w.toLowerCase();
        TIntArrayList states = dictFSA.walk(0, w, 0, w.length());
        List<Parse> parses = new ArrayList<>();
        int lastState = states.get(states.size() -1);
        if(states.size() != w.length() + 1 || !dictFSA.hasAnnotStart(lastState))
            return parses;

        TIntArrayList substList = dictFSA.collectAnnotationsSimple(lastState);
        for(int i = 0; i != substList.size(); i++) {
            int substId = substList.get(i);
            Subst s = substAlphabet.get(substId);
            String lemma = w.substring(0, w.length() - s.ending.length()) + s.nfEnding;
            Parse p = new Parse(w, lemma, 0, 0, 0, w.length(), substId, s);
            parses.add(p);
        }

        return parses;
    }

    public List<Parse> parse(String w) {
        w = w.toLowerCase();
        List<Parse> parses = parseDict(w);

        if(parses.isEmpty()) {
            return guess(w);
        }

        return parses;
    }



    /**
     * Предсказать список возможных разборов неизвестного слова
     * @param word слово в lowercase
     * @return
     */
    public List<Parse> guess(String word) {
        word = word.toLowerCase();
        List<Parse> res = new ArrayList<>();
        String s = Utils.reverse(word);
        TIntArrayList endingStates = endingFSA.walk(0, s, 0, s.length());

        int[] seenSubst = new int[substAlphabet.size() + 1];

        StringBuilder sb = new StringBuilder();
        // обход окончаний, начиная с самого длинного
        for(int i = endingStates.size() - 1; i >= 0; i--) {
            int endState = endingStates.get(i);

            if(!endingFSA.hasAnnotStart(endState))
                continue;

            TIntArrayList anns = endingFSA.collectAnnotationsSimple(endState);

            int endingId = anns.get(0);
            String ending = endingsAlphabet.get(endingId);

            if(ending.length() == word.length()) {
                int startState = endingStartStates[endingId];

                // обход основ
                TIntArrayList stemStates = stemFSA.walk(startState, s, i, s.length());

                if(stemStates.size() == 1) {

                    for(int j = 0; j < anns.size(); j += 2) {
                        sb.setLength(0);
                        int substId = anns.get(j + 1);
                        Subst subst = substAlphabet.get(substId);
                        // нет основы, только окончание, и оно полностью совпадает с исследуемым словом

                        sb.append(subst.nfEnding);
                        Parse parse = new Parse(word, sb.toString(), endState, endingId, 0, ending.length(), substId, subst);
                        parse.score = score(parse);
                        res.add(parse);


                    }
                }

                continue;
            }




            int startState = endingStartStates[endingId];


            // обход основ
            TIntArrayList stemStates = stemFSA.walk(startState, s, i, s.length());



            int maxMatchLength = stemStates.size() - 1 + ending.length();

            // проверка на минимальную длину "хвоста" - основы + окончания
            if(maxMatchLength < minMatchLength && maxMatchLength < word.length())
                continue;

            // проверка на минимальную длину основы
            if(stemStates.size() < minStemLength && maxMatchLength < word.length())
                continue;


            int endAnnotStart = endingFSA.next(endingFSA.next(endState, 0), endingId);
            sb.setLength(0);
            sb.append(word, 0, word.length() - ending.length());
            int len = sb.length();

            for(int k = stemStates.size() - 1; k >= minStemLength; k--) {
                int stemState = stemStates.get(k);
                int annotState = stemFSA.next(stemState, 0);
                int matchLength = k - 1 + ending.length();

                if(annotState == -1)
                    continue;

                if(matchLength < minMatchLength && matchLength != word.length())
                    continue;

                int start = stemFSA.stateStart(annotState);
                int end = stemFSA.stateEnd(annotState);

                for(int substIndex = start; substIndex < end; substIndex++) {
                    int substId = stemFSA.label(substIndex);
                    Subst subst = substAlphabet.get(substId);

                    int prevMatchLength = seenSubst[substId];

                    if(prevMatchLength > matchLength)
                        continue;

                    sb.setLength(len);
                    sb.append(subst.nfEnding);

                    Parse parse = new Parse(word, sb.toString(), endState, endingId, stemState, matchLength, substId, subst);
                    parse.score = score(parse);
                    res.add(parse);

                }
            }

        }


        return filterDuplicates(res);
    }

    public void computeEndingCache() {
        int start = stemFSA.stateStart(0);
        int end = stemFSA.stateEnd(0);
        endingStartStates = new int[endingsAlphabet.size() + 1];

        for(int i = start; i < end; i++) {
            endingStartStates[stemFSA.label(i)] = stemFSA.dest(i);
        }

        endingCache = new int[endingFSA.size()];

        for(int i = 0; i < endingFSA.size(); i++) {

            if(!endingFSA.hasAnnotStart(i))
                continue;

            TIntArrayList annots = endingFSA.collectAnnotationsSimple(i);

            if(!annots.isEmpty()) {
                endingCache[i] = annots.get(0);
            }
        }
    }

    public List<Lemmer.Parse> morphan(String wf) {

        if(wf.matches("\\d+")) {
            HashMap<String, String> feats = new HashMap<>();
            feats.put("Form", "Digit");

            return Arrays.asList(new Parse(wf, wf, 0, 0, 0, 0, 0, new Subst("", "", "NUM", feats)));
        }


        String orth = orth(wf);

        Set<Lemmer.Parse> out = new HashSet<>();

        AOTFullDict.UDParses dictParses = aotDict.parse(wf.toLowerCase().replace('ё', 'е'));

        for(int i = 0; i < dictParses.size(); i++) {
            Subst s = dictParses.substs.get(i);
            String lemma = wf.substring(0, wf.length() - s.ending.length()) + s.nfEnding;

            out.add(new Parse(wf, lemma, 0, 0, 0, wf.length(), substAlphabet.get(s), s));
        }





        List<Lemmer.Parse> dict = parseDict(wf);

        // "закрытые" части речи
        for(Lemmer.Parse p : dict) {
            Subst s = substAlphabet.get(p.substId);

            out.add(p);

        }


        if(out.isEmpty()) {
            out.addAll(guess(wf));
        }

        return new ArrayList<>(out);
    }

    public List<Parse> filterDuplicates(List<Parse> parses) {

        if(parses.isEmpty() || parses.size() == 1)
            return parses;


        Collections.sort(parses, new Comparator<Parse>() {
            @Override
            public int compare(Parse o1, Parse o2) {
                int res = Integer.compare(o1.substId, o2.substId);
                if(res != 0)
                    return res;
                return Integer.compare(o2.matchLength, o1.matchLength);
            }
        });

        List<Parse> out = new ArrayList<>();
        out.add(parses.get(0));
        Parse prev = parses.get(0);
        for(Parse p : parses) {
            // skip non-max parses
            if(p.substId == prev.substId)
                continue;

            out.add(p);
            prev = p;
        }

        return out;
    }

    public static String orth(String s) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < s.length(); i++) {
            sb.append(orth(s.charAt(i)));
        }

        return sb.toString();
    }

    private static char orth(char ch) {
        switch(Character.getType(ch)) {
            case Character.UPPERCASE_LETTER:
                return 'A';
            case Character.LOWERCASE_LETTER:
                return 'a';
            case Character.DECIMAL_DIGIT_NUMBER:
                return '0';
            case Character.DASH_PUNCTUATION:
                return '-';

            default:
                return ch;
        }
    }

    public static String orthShort(String s) {
        if(s.length() == 0)
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append(orth(s.charAt(0)));

        char prev = 0;

        for(int i = 1; i < s.length(); i++) {
            char ch = orth(s.charAt(i));
            if(prev == ch)
                continue;
            sb.append(ch);
            prev = ch;
        }

        return sb.toString();
    }

    public List<Parse> filter(List<Parse> parses) {

        if(parses.isEmpty())
            return parses;

        Comparator<Parse> c = new Comparator<Parse>() {
            @Override
            public int compare(Parse o1, Parse o2) {
//                int stemMatch1 = o1.matchLength - o1.subst.ending.length();
//                int stemMatch2 = o2.matchLength - o2.subst.ending.length();
//
//                int cappedLength1 = Math.min(2, stemMatch1) + o1.subst.ending.length();
//                int cappedLength2 = Math.min(2, stemMatch2) + o2.subst.ending.length();
//
//                //return o2.matchLength - o1.matchLength;
//                return cappedLength2 - cappedLength1;
                return Float.compare(o2.score, o1.score);
            }
        };
        Collections.sort(parses, c);

        Parse top = parses.get(0);
        List<Parse> out = new ArrayList<>();
        double minScore = 100;
        for(Parse p : parses) {
            if(p.score < minScore)
                break;

            out.add(p);
        }

        return out;
    }

    public float score(Parse p) {
        return (float) Math.pow(2, p.matchLength) * substFreq.get(p.substId);
    }



    public static void main(String[] args) throws Exception {
        Lemmer l = new Lemmer(new File("/storage/data/morphoRuEval/GIKRYA_texts.txt"), new File("morphoRuEval.dict.dat"));
        l.readAOT(new File("morphoRuEval.aot_dict.dat"));
        l.morphan("все");
        l.morphan("стати");
        l.morphan("батарея");
        l.morphan("устроенного");



        //l.guess("дятла");

        //List<Parse> p = l.guess("абырвалга");
        //List<Parse> p1 = l.parse("полковником");

        //l.guess("люди");

        //perfTest(l);
        for(int i = 0; i < 100; i++) {
            String wf = "гора";
            l.filter(l.guess(wf));
        }

        eval(l);

    }

    private static void eval(Lemmer l) throws IOException {
        long st = System.currentTimeMillis();
        int total = 0;
        int missStrict = 0;
        int missLemma = 0;
        int totParses = 0;
        int totParsedFiltered = 0;
        int dictParses = 0;
        int dictTotal = 0;
        try(CorpusReader r = new CorpusReader(new File("morphoRuEval/gikrya_fixed.txt"));
            PrintWriter pwMissStrict = new PrintWriter("morphoRuEval.lemmer.strict.miss");
            PrintWriter pwMissLemma = new PrintWriter("morphoRuEval.lemmer.lemma.miss");
            //PrintWriter pwGuess = new PrintWriter("morphoRuEval.lemmer.guess");
            //PrintWriter pwFull = new PrintWriter("morphoRuEval.lemmer.full");
        ) {
            while(true) {
                Sentence sent = r.next();

                if(sent == null)
                    break;

                for(int i = 0; i < sent.size(); i++) {
                    String wf = sent.wf(i).toLowerCase().replace('ё', 'е');
                    String lemma = sent.lemma(i).toLowerCase().replace('ё', 'е');
                    String pos = sent.pos(i);
                    Map<String, String> feats = sent.feats(i);

                    if(UDConst.CLOSED_POS.contains(pos))
                        continue;

                    if(Objects.equals(pos, "NUM"))
                        continue;

                    if(Objects.equals(pos, "PUNCT"))
                        continue;

                    if(Objects.equals(pos, "INTJ"))
                        continue;

                    if(wf.contains(".") || lemma.contains(".") || lemma.length() == 2 || wf.length() == 2)
                        continue;

                    if(Objects.equals(wf, wf.toUpperCase()))
                        continue;


                    total++;


                    List<Parse> guess = l.morphan(wf);
                    List<Parse> dict = l.parseDict(wf);

                    if(!dict.isEmpty()) {
                        dictTotal++;
                        dictParses += dict.size();
                    }

                    totParses += guess.size();
                    List<Parse> parses = guess;//l.filter(guess);
                    totParsedFiltered += parses.size();

                    Set<String> guesses = new TreeSet<>();

                    boolean guessedFull = false;
                    boolean guessedLemma = false;

                    for(Parse parse : parses) {
                       if(Objects.equals(parse.lemma, lemma) && Objects.equals(parse.pos, pos) && Objects.equals(parse.feats, feats)) {
                           guessedFull = true;



                       }
                       if(Objects.equals(parse.lemma, lemma)) {
                            guessedLemma = true;
                        }
                       guesses.add(parse.lemma);

                    }


                    if(!guessedFull) {
                        missStrict++;
                    }

                    if(!guessedLemma) {
                        missLemma++;
                    }

                    //PrintWriter pw = guessed? pwGuess : pwMiss;

                    if(!guessedFull) {
                        pwMissStrict.printf("%s\t%s\t%s%n", wf, lemma, Joiner.on(", ").join(guesses));
                    }

                    if(!guessedLemma) {
                        pwMissLemma.printf("%s\t%s\t%s%n", wf, lemma, Joiner.on(", ").join(guesses));
                    }

                }
            }
        }

        System.out.println("done");
        System.out.printf("Strict Missed: %d/%d (err = %.4f)%n", missStrict, total, 1.0*missStrict/total);
        System.out.printf("Missed lemma: %d/%d (err = %.4f)%n", missLemma, total, 1.0*missLemma/total);
        System.out.printf("Filtered Avg %.4f parses/word, raw avg %.4f parses/word%n", 1.0*totParsedFiltered/total, 1.0*totParses/total);
        System.out.printf("Avg dict parses: %.4f parses/word%n", 1.0*dictParses/dictTotal);
        System.out.printf("%d in %d ms (avg speed = %.2f parse/sec%n",
                total,
                System.currentTimeMillis() - st,
                1000.0* total/(System.currentTimeMillis() - st)
        );
    }

    private static void perfTest(Lemmer l) throws IOException {
        long st = System.currentTimeMillis();
        int total = 0;
        try(CorpusReader r = new CorpusReader(new File("/storage/data/morphoRuEval/GIKRYA_texts.txt"));
//            PrintWriter pwMiss = new PrintWriter("morphoRuEval.lemmer.miss");
//            PrintWriter pwGuess = new PrintWriter("morphoRuEval.lemmer.guess");
//            PrintWriter pwFull = new PrintWriter("morphoRuEval.lemmer.full");
        ) {
            while(true) {
                Sentence sent = r.next();

                if(sent == null)
                    break;

                for(int i = 0; i < sent.size(); i++) {
                    String wf = sent.wf(i).toLowerCase().replace('ё', 'е');
                    String lemma = sent.lemma(i).toLowerCase().replace('ё', 'е');
                    String pos = sent.pos(i);

                    if(UDConst.CLOSED_POS.contains(pos))
                        continue;

                    total++;

                    List<Parse> parses = l.guess(wf);
                    Set<String> guesses = new TreeSet<>();

//                    pwFull.printf("%s\t%s\t%s\t%s%n", wf, lemma, pos, sent.feats(i));
//                    for(Parse parse : parses) {
//                        guesses.add(parse.lemma);
//                        Subst subst = l.substAlphabet.get(parse.substId);
//                        pwFull.printf("\t%s\t%s\t%s%n", parse.lemma, subst.pos, subst.feats);
//                    }
//                    pwFull.println();
//                    pwFull.println();

//                    if(guesses.isEmpty()) {
//                        guesses.add(wf);
//                    }

//                    PrintWriter pw = guesses.contains(lemma)? pwGuess : pwMiss;
//
//                    pw.printf("%s\t%s\t%s%n", wf, lemma, Joiner.on(", ").join(guesses));

                }
            }
        }

        System.out.println("done");
        System.out.printf("%d in %d ms (avg speed = %.2f parse/sec%n",
                total,
                System.currentTimeMillis() - st,
                1000.0* total/(System.currentTimeMillis() - st)
        );
    }


}
