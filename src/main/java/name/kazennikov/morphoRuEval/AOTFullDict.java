package name.kazennikov.morphoRuEval;

import EDU.oswego.cs.dl.util.concurrent.FJTask;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import name.kazennikov.Utils;
import name.kazennikov.alphabet.Alphabet;
import name.kazennikov.count.IntCounts;
import name.kazennikov.fsa.BooleanFSABuilder;
import name.kazennikov.fsa.walk.WalkFSABoolean;
import name.kazennikov.morph.aot.*;
import name.kazennikov.trove.TroveUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Created on 3/8/17.
 *
 * @author Anton Kazennikov
 */
public class AOTFullDict implements Serializable {

    private static final long serialVersionUID = 1L;

    public static class Subst implements Serializable {
        private static final long serialVersionUID = 1L;

        String ending;
        String nfEnding;
        String pos;
        List<String> feats;

        public Subst(String ending, String nfEnding, String pos, List<String> feats) {
            this.ending = ending;
            this.nfEnding = nfEnding;
            this.pos = pos;
            this.feats = feats;
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) return true;
            if(o == null || getClass() != o.getClass()) return false;
            Subst subst = (Subst) o;
            return Objects.equals(ending, subst.ending) &&
                    Objects.equals(nfEnding, subst.nfEnding) &&
                    Objects.equals(pos, subst.pos) &&
                    Objects.equals(feats, subst.feats);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ending, nfEnding, pos, feats);
        }

        public String lemma(String wf) {
            return wf.substring(0, wf.length() - ending.length()) + nfEnding;
        }

        @Override
        public String toString() {
            return String.format("%s|%s|%s|%s", ending, nfEnding, pos, feats);
        }
    }

    public static class UDParses {
        public final List<name.kazennikov.morphoRuEval.Subst> substs;
        public final int[] freqs;

        public UDParses(List<name.kazennikov.morphoRuEval.Subst> substs, int[] freqs) {
            this.substs = substs;
            this.freqs = freqs;
        }

        public boolean isEmpty() {
            return substs.isEmpty();
        }

        public int size() {
            return substs.size();
        }
    }


    AOT2UD conv;


    Alphabet<Subst> substs = new Alphabet<>();
    WalkFSABoolean fsa;

    TIntArrayList substDictFreq = new TIntArrayList();

    TIntObjectHashMap<Set<name.kazennikov.morphoRuEval.Subst>> aot2udSubstFeats = new TIntObjectHashMap<>();



    public List<Paradigm> adjComp(List<Paradigm> pars) {

        List<Paradigm> out = new ArrayList<>();


        for(Paradigm p : pars) {

            List<Paradigm> res = new ArrayList<>();
            List<Paradigm> resADV = new ArrayList<>();

            Paradigm immut = new Paradigm();

            for(int i = 0; i < p.size(); i++) {
                Paradigm.Entry e = p.get(i);

                // ADJ, сравн -> ADV, сравн
                if((e.getRec().pos.equals("П") || e.getRec().pos.equals("КР_ПРИЛ")) && e.getRec().feats.contains("сравн")) {
                    Paradigm pp = new Paradigm();

                    GramTable.Record r = new GramTable.Record("ADV", e.getRec().type, Arrays.asList("Cmp"));
                    pp.addEntry(e.getEnding(), e.getPrefix(), r);

                    resADV.add(pp);
                }

                // КР_ПРИЛ, ср, ед  -> ADV
                if(e.getRec().pos.equals("КР_ПРИЛ") && e.getRec().feats.contains("ср") && e.getRec().feats.contains("ед")) {
                    Paradigm pp = new Paradigm();

                    GramTable.Record r = new GramTable.Record("ADV", e.getRec().type, new ArrayList<>());
                    pp.addEntry(e.getEnding(), e.getPrefix(), r);

                    resADV.add(pp);
                }
            }

            out.add(p);


            out.addAll(resADV);
        }

        return out;
    }


    public List<Paradigm> convertPredicate(List<Paradigm> pars) {
        Set<String> adv2Remove = new HashSet<>(Arrays.asList("нст", "мр", "жр", "ед", "мр"));
        List<Paradigm> out = new ArrayList<>();


        for(Paradigm p : pars) {

            List<Paradigm> resADV = new ArrayList<>();

            Paradigm filtered = new Paradigm();



            for(int i = 0; i < p.size(); i++) {
                Paradigm.Entry e = p.get(i);

                // ADJ, сравн -> ADV, сравн
                if(e.getRec().pos.equals("ПРЕДК")) {
                    Paradigm pp = new Paradigm();

                    List<String> feats = new ArrayList<>(e.getRec().feats);
                    feats.removeAll(adv2Remove);


                    GramTable.Record r = new GramTable.Record("ADV", e.getRec().type, feats);
                    pp.addEntry(e.getEnding(), e.getPrefix(), r);
                    resADV.add(pp);
                } else {
                    filtered.addEntry(e.getEnding(), e.getPrefix(), e.getRec());
                }
            }

            if(resADV.size() != 0) {
                out.addAll(resADV);
                if(filtered.size() != 0) {
                    out.add(filtered);
                }

            } else {
                out.add(p);
            }
        }

        return out;
    }

    // выделение причастий в отдельные парадигмы (и разделяя страдательные/нестрадательные причастия)
    public List<Paradigm> immutNoun(List<Paradigm> pars) {


        List<Paradigm> out = new ArrayList<>();

        for(Paradigm p : pars) {
            Paradigm immut = new Paradigm();
            Paradigm filtered = new Paradigm();


            for(int i = 0; i < p.size(); i++) {
                Paradigm.Entry e = p.get(i);

                if(e.getRec().pos.equals("С") && e.getRec().feats.contains("0")) {


                    List<String> baseFeats = e.getRec().feats;

                    List<String> feats = new ArrayList<>(baseFeats);
                    feats.add("им");
                    feats.add("ед");
                    immut.addEntry("", e.getPrefix(), new GramTable.Record(e.getRec().pos, e.getRec().type, feats));

                    feats = new ArrayList<>(baseFeats);
                    feats.add("рд");
                    feats.add("ед");
                    immut.addEntry("", e.getPrefix(), new GramTable.Record(e.getRec().pos, e.getRec().type, feats));

                    feats = new ArrayList<>(baseFeats);
                    feats.add("дт");
                    feats.add("ед");
                    immut.addEntry("", e.getPrefix(), new GramTable.Record(e.getRec().pos, e.getRec().type, feats));

                    feats = new ArrayList<>(baseFeats);
                    feats.add("вн");
                    feats.add("ед");
                    immut.addEntry("", e.getPrefix(), new GramTable.Record(e.getRec().pos, e.getRec().type, feats));

                    feats = new ArrayList<>(baseFeats);
                    feats.add("тв");
                    feats.add("ед");
                    immut.addEntry("", e.getPrefix(), new GramTable.Record(e.getRec().pos, e.getRec().type, feats));

                    feats = new ArrayList<>(baseFeats);
                    feats.add("пр");
                    feats.add("ед");
                    immut.addEntry("", e.getPrefix(), new GramTable.Record(e.getRec().pos, e.getRec().type, feats));

                } else {
                    filtered.addEntry(e.getEnding(), e.getPrefix(), e.getRec());
                }
            }

            if(immut.size() != 0) {
                out.add(immut);

                if(filtered.size() != 0) {
                    out.add(filtered);
                }

            } else {
                out.add(p);
            }
        }

        return out;
    }




    // выделение причастий в отдельные парадигмы (и разделяя страдательные/нестрадательные причастия)
    public List<Paradigm> splitVerb(List<Paradigm> pars) {

        List<Paradigm> out = new ArrayList<>();

        for(Paradigm p : pars) {

            Paradigm filtered = new Paradigm();
            Paradigm adj0 = new Paradigm(); // стр, нст
            Paradigm adj1 = new Paradigm(); // дст, прш
            Paradigm adj2 = new Paradigm(); // стр, нст
            Paradigm adj3 = new Paradigm(); // дст, прш


            for(int i = 0; i < p.size(); i++) {
                Paradigm.Entry e = p.get(i);

                List<String> feats = new ArrayList<>(e.getRec().feats);

                if(Objects.equals(e.getRec().pos, "ПРИЧАСТИЕ") || Objects.equals(e.getRec().pos, "КР_ПРИЧАСТИЕ")) {

                    boolean hasStr = e.getRec().feats.contains("стр");
                    boolean hasPast = e.getRec().feats.contains("прш");

                    Paradigm adj = null;

                    if(hasStr) {
                        if(hasPast) {
                            adj = adj0;
                        } else {
                            adj = adj1;
                        }
                    } else {
                        if(hasPast) {
                            adj = adj2;

                        } else {
                            adj = adj3;
                        }
                    }



                    feats.remove("нст");
                    feats.remove("буд");
                    feats.remove("прш");
                    GramTable.Record r = new GramTable.Record(e.getRec().pos, e.getRec().type, feats);
                    adj.addEntry(e.getEnding(), e.getPrefix(), r);
                } else {
                    filtered.addEntry(e.getEnding(), e.getPrefix(), new GramTable.Record(e.getRec().pos, e.getRec().type, feats));
                }
            }

            if(adj0.size() == 0 && adj1.size() == 0 && adj2.size() == 0 && adj3.size() == 0) {
                out.add(p);
                continue;
            }


            if(filtered.size() != 0) {
                out.add(filtered);
            }

            if(adj0.size() != 0) {
                out.add(adj0);
            }

            if(adj1.size() != 0) {
                out.add(adj1);
            }

            if(adj2.size() != 0) {
                out.add(adj2);
            }

            if(adj3.size() != 0) {
                out.add(adj3);
            }
        }

        return out;
    }


    public List<Paradigm> convert(Paradigm p) {


        List<Paradigm> out = splitVerb(Arrays.asList(p));
        out = adjComp(out);
        out = immutNoun(out);
        out = convertPredicate(out);

        return out;
    }



    public void incSubstFreq(int substId) {
        while(substDictFreq.size() < substId + 1) {
            substDictFreq.add(0);
        }

        substDictFreq.set(substId, substDictFreq.get(substId) + 1);
    }



    public void build() throws Exception {


        AOTFullDict fd = new AOTFullDict();

        MorphConfig mc = MorphConfig.newInstance(new File("modules/jaot/russian.xml"));

        MorphLanguage ml = new MorphLanguage(mc);

        long st = System.currentTimeMillis();
        MorphDict md = ml.readDict();

        BooleanFSABuilder builder = new BooleanFSABuilder();

        int count = 0;




        TIntArrayList l = new TIntArrayList();

        for(MorphDict.Lemma entry : md.getLemmas()) {
            int stemLength = entry.getStem().length();

            if(entry.getLemma().equals("СИДЕТЬ") || entry.getLemma().equals("ОТСИДЕТЬ")) {
                entry = entry;
            }


            for(Paradigm p : fd.convert(entry.getParadigm())) {

                String lemma = MorphDict.constructLemma(entry.getStem(), p).toLowerCase().replace('ё', 'е');



                List<MorphDict.WordForm> wfs = p.expand(entry.getStem(), lemma, entry.getCommonFeats(), false);


                for(MorphDict.WordForm w : wfs) {
                    String wf = w.getWordForm().toLowerCase().replace('ё', 'е');

                    String pos = w.getFeats().pos;
                    List<String> feats = w.getFeats().feats;

                    String ending = wf.substring(stemLength);
                    String nfEdings = lemma.substring(stemLength);

                    Subst s = new Subst(ending, nfEdings, pos, feats);




                    int substId = substs.get(s);

                    l.resetQuick();
                    TroveUtils.expand(l, wf);
                    l.add(0);
                    l.add(substId);
                    incSubstFreq(substId);
                    builder.addMinWord(l);


                    if(s.pos.equals("КР_ПРИЛ") && wf.endsWith("ски")) {
                        s = new Subst("", "", "Н", new ArrayList<>());


                        substId = substs.get(s);

                        l.resetQuick();
                        TroveUtils.expand(l, wf);
                        l.add(0);
                        l.add(substId);
                        incSubstFreq(substId);
                        builder.addMinWord(l);
                    }
                }
            }

            count++;
            if(count % 10000 == 0) {
                System.out.printf("%d...%n", count);
            }
        }


        File morphoRuEvalDict = new File("morphoRuEval.dict.txt");
        if(morphoRuEvalDict.exists()) {

            try(BufferedReader br = Files.newReader(morphoRuEvalDict, Charset.forName("UTF-8"))) {
                while(true) {
                    String s = br.readLine();
                    if(s == null)
                        break;
                    if(s.isEmpty() || s.startsWith("#"))
                        continue;

                    String[] parts = s.split("\t");

                    String wf = parts[0];
                    String lemma = parts[1];
                    String pos = parts[2];

                    if(lemma.isEmpty()) {
                        lemma = wf;
                    }


                    List<String> feats = new ArrayList<>();

                    if(parts.length == 4) {
                        feats.addAll(Arrays.asList(parts[3].split("\\s*,\\s*")));
                    }

                    int common = Utils.commonPrefix(wf, lemma);



                    Subst subst = new Subst(wf.substring(common), lemma.substring(common), pos, feats);
                    int substId = substs.get(subst);

                    l.resetQuick();
                    TroveUtils.expand(l, wf);
                    l.add(0);
                    l.add(substId);
                    incSubstFreq(substId);
                    builder.addMinWord(l);
                }
            }
        }



        System.out.printf("Count: %d%n", count);

        System.out.printf("FSA size: %d, substs: %d%n", builder.size(), substs.size());

        fsa = builder.build();

    }


    public TIntArrayList aotSubsts(String wf) {
        wf = wf.toLowerCase().replace('ё', 'е');
        TIntArrayList states = fsa.walk(0, wf, 0, wf.length());
        int lastState = states.get(states.size() - 1);
        if(states.size() != wf.length() + 1 || !fsa.hasAnnotStart(lastState))
            return new TIntArrayList();

        return fsa.collectAnnotationsSimple(lastState);
    }

    public List<Subst> aotParses(String wf) {
        List<Subst> res = new ArrayList<>();
        TIntArrayList l = aotSubsts(wf);

        for(int i = 0; i < l.size(); i++) {
            res.add(substs.get(l.get(i)));
        }

        return res;
    }




    public void mapSubst(File corpusFile) throws Exception {

        Set<String> skip = new HashSet<>(Arrays.asList("PUNCT", "H", "ADP", "CONJ", "DET", "PART", "PRON", "X"));

        Set<String> retainFeats = new HashSet<>(Arrays.asList(
                "Sing","Plur",
                "Nom", "Gen","Dat", "Acc", "Ins", "Loc",
                "Masc", "Fem", "Neut",
                "1", "2", "3",
                "Past", "Notpast",
                "Inf", "Fin", "Conv", "Imp"//,
        ));

        int total = 0;

        Map<Subst, Set<List<String>>> substSetMap = new HashMap<>();



        try(CorpusReader r = new CorpusReader(corpusFile)) {
            while(true) {
                Sentence s = r.next();

                if(s == null)
                    break;

                for(int i = 0; i < s.size(); i++) {

                    TIntArrayList substIds = aotSubsts(s.wfLC(i));

                    if(substIds.isEmpty())
                        continue;

                    if(skip.contains(s.pos(i)))
                        continue;

                    total++;


                    String refPos = s.pos(i);

                    if(Objects.equals(refPos, "PROPN")) {
                        refPos = "NOUN";
                    }

                    String refLemma = s.lemma(i).toLowerCase().replace('ё', 'е');
                    List<String> refFeats = new ArrayList<>(s.feats(i).values());

                    for(int substIndex = 0; substIndex < substIds.size(); substIndex++) {
                        int substId = substIds.get(substIndex);
                        Subst subst = substs.get(substId);

                        List<String> cfeats = new ArrayList<>(conv.conv(subst.pos));
                        String cpos = cfeats.get(0);




                        Set<String> tfeats = new TreeSet<>(cfeats);

                        for(String f : subst.feats) {
                            if(Objects.equals(f, "2"))
                                continue;

                            tfeats.addAll(conv.conv(f));
                        }


                        tfeats.retainAll(retainFeats);

                        String lemma = subst.lemma(s.wfLC(i)).toLowerCase().replace('ё', 'е');

                        if(!Objects.equals(cpos, refPos))
                            continue;



                        if(!Objects.equals(lemma, refLemma))
                            continue;

                        if(refFeats.containsAll(tfeats)) {
                            Set<name.kazennikov.morphoRuEval.Subst> set = aot2udSubstFeats.get(substId);
                            if(set == null) {
                                set = new HashSet<>();
                                aot2udSubstFeats.put(substId, set);
                            }

                            List<String> featList = s.word(i).asMorphInfo().asList();
                            featList.remove("Anim");
                            featList.remove("Inan");
                            set.add(new name.kazennikov.morphoRuEval.Subst(subst.ending, subst.nfEnding, refPos, s.feats(i)));
                        }
                    }
                }
            }
        }
    }

    public UDParses parse(String wf) {
        TIntArrayList substIds = aotSubsts(wf);
        List<name.kazennikov.morphoRuEval.Subst> substs = new ArrayList<>();
        TIntArrayList freqs = new TIntArrayList();

        for(int i = 0; i < substIds.size(); i++) {
            int substId = substIds.get(i);
            Set<name.kazennikov.morphoRuEval.Subst> udSubsts = aot2udSubstFeats.get(substId);

            if(udSubsts == null)
                continue;

            for(name.kazennikov.morphoRuEval.Subst subst : udSubsts) {
                substs.add(subst);
                freqs.add(substDictFreq.get(substId));
            }

        }

        return new UDParses(substs, freqs.toArray());

    }


    public void write(File cacheFile) throws Exception {
        try(ObjectOutputStream s = new ObjectOutputStream(new FileOutputStream(cacheFile))) {
            s.writeObject(substs);
            s.writeObject(fsa);
            s.writeObject(substDictFreq);
            s.writeObject(aot2udSubstFeats);
        }
    }

    public void read(File cacheFile) throws Exception {
        try(ObjectInputStream s = new ObjectInputStream(new FileInputStream(cacheFile))) {
            substs = (Alphabet<Subst>) s.readObject();
            fsa = (WalkFSABoolean) s.readObject();
            substDictFreq = (TIntArrayList) s.readObject();
            aot2udSubstFeats = (TIntObjectHashMap<Set<name.kazennikov.morphoRuEval.Subst>>) s.readObject();
        }
    }




    public static void main(String[] args) throws Exception {

        AOTFullDict dict = new AOTFullDict();
        AOT2UD conv = new AOT2UD();
        conv.readMap(new File("aot2ud.txt"));

        dict.conv = conv;
        dict.build();
        dict.mapSubst(new File("morphoRuEval/full_corpora.txt"));
        dict.write(new File("morphoRuEval.aot_dict.dat"));
        dict.read(new File("morphoRuEval.aot_dict.dat"));

        UDParses parses = dict.parse("она");




        Set<String> retainFeats = new HashSet<>(Arrays.asList(
                "Sing","Plur",


                "Nom", "Gen","Dat", "Acc", "Ins", "Loc",
                "Masc", "Fem", "Neut",
                "1", "2", "3",
                "Past", "Notpast",
                "Anim", "Inan",
                "Inf", "Fin", "Conv", "Imp"//,
//                "Ind", "Imp"//,
//                "Brev", "Pos", "Cmp"


        ));

        evalMissed(dict, conv, retainFeats);



            //MorphCompiler morphCompiler = new MorphCompiler(ml);
//
        //morphCompiler.compile(md, 5, 0, new File("russian.dat"));



    }



    private static void evalMissed(AOTFullDict dict, AOT2UD conv, Set<String> retainFeats) throws IOException {
        Set<String> skip = new HashSet<>(Arrays.asList("PUNCT", "H", "ADP", "CONJ", "DET", "PART", "PRON", "X"));


        TObjectIntHashMap<String> missed = new TObjectIntHashMap<>();
        TObjectIntHashMap<String> ambig = new TObjectIntHashMap<>();


        int total = 0;

        Map<Subst, Set<List<String>>> substSetMap = new HashMap<>();


        //try(CorpusReader r = new CorpusReader(new File("morphoRuEval/gikrya_fixed.txt"))) {
        try(CorpusReader r = new CorpusReader(new File("morphoRuEval/full_corpora.txt"))) {
            while(true) {
                Sentence s = r.next();

                if(s == null)
                    break;

                for(int i = 0; i < s.size(); i++) {

                    List<Subst> substs = dict.aotParses(s.wfLC(i));

                    if(substs.isEmpty())
                        continue;

                    if(skip.contains(s.pos(i)))
                        continue;



                    total++;

                    TreeSet<String> lemmas = new TreeSet<>();

                    String refPos = s.pos(i);

                    if(Objects.equals(refPos, "PROPN")) {
                        refPos = "NOUN";
                    }

                    String refLemma = s.lemma(i).toLowerCase().replace('ё', 'е');
                    List<String> refFeats = new ArrayList<>(s.feats(i).values());


                    if(Objects.equals(s.wfLC(i), "отсидим")) {
                        refLemma = refLemma;
                    }


                    int matchCount = 0;

                    for(Subst subst : substs) {


                        List<String> cfeats = new ArrayList<>(conv.conv(subst.pos));
                        String cpos = cfeats.get(0);




                        Set<String> tfeats = new TreeSet<>(cfeats);

                        for(String f : subst.feats) {
                            if(Objects.equals(f, "2"))
                                continue;


                            tfeats.addAll(conv.conv(f));
                        }


                        tfeats.retainAll(retainFeats);

                        String lemma = subst.lemma(s.wfLC(i)).toLowerCase().replace('ё', 'е');

                        lemmas.add(lemma + "_" + cpos + "_" + tfeats);

                        if(!Objects.equals(cpos, refPos))
                            continue;



                        if(!Objects.equals(lemma, refLemma))
                            continue;

                        if(refFeats.containsAll(tfeats)) {
                            matchCount++;
                            Set<List<String>> set = substSetMap.get(subst);
                            if(set == null) {
                                set = new HashSet<>();
                                substSetMap.put(subst, set);
                            }

                            List<String> featList = s.word(i).asMorphInfo().asList();
                            featList.remove("Anim");
                            featList.remove("Inan");
                            set.add(featList);
                        }




                    }

                    if(matchCount == 0) {
                        missed.adjustOrPutValue(s.wfLC(i) + "\t" + refLemma + "_" + refPos + "\t" + refFeats + "\t" + Joiner.on(", ").join(lemmas) , 1, 1);

                    }

                    if(matchCount > 1) {
                        ambig.adjustOrPutValue(s.wfLC(i) + "\t" + refLemma + "_" + refPos + "\t" + refFeats + "\t" + Joiner.on(", ").join(lemmas) , 1, 1);

                    }
                }



            }
        }

        int totmissed = 0;
        int totambig = 0;

        try(PrintWriter pw = new PrintWriter("morphoRuEval.aot.miss.feats.txt")) {
            IntCounts<String> c = new IntCounts<String>(missed);
            c.inplaceSort(c.DESC);

            pw.printf("count\twf\tlemma\tpred lemmas\tparses%n");

            for(int i = 0; i < c.size(); i++) {
                pw.printf("%d\t%s%n", c.getCount(i), c.getObject(i));
                totmissed += c.getCount(i);
            }
        }

        try(PrintWriter pw = new PrintWriter("morphoRuEval.aot.ambig.feats.txt")) {
            IntCounts<String> c = new IntCounts<String>(ambig);
            c.inplaceSort(c.DESC);

            pw.printf("count\twf\tlemma\tpred lemmas\tparses%n");

            for(int i = 0; i < c.size(); i++) {
                pw.printf("%d\t%s%n", c.getCount(i), c.getObject(i));
                totambig += c.getCount(i);
            }
        }

        int ambigSubst = 0;
        int totalDictFreq = 0;
        try(PrintWriter pw = new PrintWriter("morphoRuEval.aot.ambig.subst.txt")) {

            for(Map.Entry<Subst, Set<List<String>>> e : substSetMap.entrySet()) {
                if(e.getValue().size() > 1) {
                    pw.printf("%s\t%s%n", e.getKey(), Joiner.on(", ").join(e.getValue()));
                    ambigSubst++;

                }

                totalDictFreq += dict.substDictFreq.get(dict.substs.get(e.getKey()));
            }
        }



        System.out.printf("Total missed/ambig: %d/%d/%d words%n", totmissed, totambig, total);

        System.out.printf("Ambig subst: %d/%d%n", ambigSubst, substSetMap.size());
        System.out.printf("Total subst coverage: %d words%n", totalDictFreq);
    }
}
