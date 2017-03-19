package name.kazennikov.morphoRuEval;

import com.google.common.base.Joiner;
import gnu.trove.map.hash.TObjectIntHashMap;
import name.kazennikov.count.IntCount;
import name.kazennikov.count.IntCounts;
import name.kazennikov.morph.ISegGuesserSubst;
import ru.iitp.cl.core.MorphConfig;
import ru.iitp.cl.core.MorphDict;
import ru.iitp.cl.core.MorphParse;
import ru.iitp.proling.etap.common.Terms;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Created on 3/5/17.
 *
 * @author Anton Kazennikov
 */
public class EtapGuesserEval {
    public static void writeCounts(File out, IntCounts<?> counts) throws IOException {
        try(PrintWriter pw = new PrintWriter(out)) {
            for(int i = 0; i < counts.size(); i++) {
                pw.printf("%d\t%s%n", counts.getCount(i), counts.getObject(i).toString());
            }
        }
    }


    public static void main(String[] args) throws Exception {
        File baseDir = new File("data/gate/etap/");
        Terms terms = new Terms(new File(baseDir, "terms.xml"));



        MorphConfig mc = MorphConfig.build(terms, new File(baseDir, "morph/russian.des"));
        MorphDict md = new MorphDict(mc, new File(baseDir, "morph"));
        md.init();



        ISegGuesserSubst guesser = new ISegGuesserSubst(mc.getTerms());
        guesser.setCacheFile(new File(baseDir, "guesser/guesser.bin"));
        guesser.setParadigmsFile(new File(baseDir, "guesser/pars.out.txt"));
        guesser.setSubstCountsFile(new File(baseDir, "guesser/subst.counts.txt"));

        guesser.setMinParadigmFreq(5);
        guesser.setMinStemLength(1);
        guesser.setMinMatchLength(2);
        guesser.init();

        Set<String> skipPOS = new HashSet<>(Arrays.asList("PUNCT", "H", "SYM", "X"));



        try(CorpusReader r = new CorpusReader(new File("/storage/data/morphoRuEval/full_corpora.txt"))) {
            int sentCount = 0;
            int missDict = 0;
            int missGuesser = 0;
            int totalWords = 0;

            TObjectIntHashMap<String> emptyDictCounts = new TObjectIntHashMap<>();
            TObjectIntHashMap<String> missedDictCounts = new TObjectIntHashMap<>();
            TObjectIntHashMap<String> missedGuesserCounts = new TObjectIntHashMap<>();
            while(true) {
                Sentence s = r.next();
                if(s == null)
                    break;
                sentCount++;

                if(sentCount % 10000 == 0) {
                    System.out.printf("%d... %n", sentCount);
                }

                for(int i = 0; i < s.size(); i++) {
                    if(skipPOS.contains(s.pos(i)))
                        continue;

                    String wf = s.wfLC(i);

                    if(wf.matches("\\d+"))
                        continue;

                    if(wf.matches("\\d+[-]..?"))
                        continue;

                    if(wf.matches("\\d+[,.-]\\d+"))
                        continue;

                    if(wf.contains("_"))
                        continue;

                    totalWords++;

                    List<MorphParse> dictParses = md.analyze(s.wfLC(i), false);

                    boolean dictFound = false;

                    String refLemma = s.lemma(i).toLowerCase().replace('ё', 'е');

                    if(dictParses.isEmpty()) {
                        emptyDictCounts.adjustOrPutValue(wf + "\t" + refLemma, 1, 1);
                    }

                    TreeSet<String> dictLemmas = new TreeSet<>();

                    for(MorphParse p : dictParses) {
                        String lemma = p.getLemma().toLowerCase().replace('ё', 'е');
                        dictLemmas.add(lemma);

                        if(Objects.equals(lemma, refLemma)) {
                            dictFound = true;
                            break;
                        }

                    }

                    if(dictFound)
                        continue;


                    missedDictCounts.adjustOrPutValue(wf + "\t" + refLemma + "\t" + Joiner.on(",").join(dictLemmas), 1, 1);
                    missDict++;


                    List<ISegGuesserSubst.Parse> guesses = guesser.guess(wf);
                    TreeSet<String> guesserLemmas = new TreeSet<>();

                    boolean guesserFound = false;

                    for(ISegGuesserSubst.Parse p : guesses) {
                        String lemma = p.lemma.toLowerCase().replace('ё', 'е');
                        if(Objects.equals(lemma, refLemma)) {
                            guesserFound = true;
                            break;
                        }

                        guesserLemmas.add(lemma);
                    }

                    if(!guesserFound) {
                        missedGuesserCounts.adjustOrPutValue(wf + "\t" + refLemma + "\t" + Joiner.on(",").join(guesserLemmas), 1, 1);
                        missGuesser++;
                    }



                }
            }

            IntCounts<String> emptyDictC = new IntCounts<String>(emptyDictCounts);
            IntCounts<String> missDictC = new IntCounts<String>(missedDictCounts);
            IntCounts<String> missGuesserC = new IntCounts<String>(missedGuesserCounts);
            emptyDictC.inplaceSort(emptyDictC.DESC);
            missDictC.inplaceSort(missDictC.DESC);
            missGuesserC.inplaceSort(missGuesserC.DESC);

            writeCounts(new File("morphoRuEval.etap.emptyDict.txt"), emptyDictC);
            writeCounts(new File("morphoRuEval.etap.missDict.txt"), missDictC);
            writeCounts(new File("morphoRuEval.etap.missGuesser.txt"), missGuesserC);


            System.out.printf("Dict/Guesser miss: %d/%d/%d%n", missDict, missGuesser, totalWords);
        }


    }
}
