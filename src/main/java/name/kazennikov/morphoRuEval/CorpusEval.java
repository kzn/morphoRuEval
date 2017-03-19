package name.kazennikov.morphoRuEval;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TObjectIntHashMap;
import name.kazennikov.count.IntCounts;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

/**
 * Created by kzn on 3/6/17.
 */
public class CorpusEval {
    File baseDir = new File("/storage/data/morphoRuEval");
    Map<String, String> corporaFiles = new HashMap<>();

    public CorpusEval() {
        corporaFiles.put("gikrya", "gikrya_fixed.txt");
        corporaFiles.put("rnc", "RNCgoldInUD_Morpho.conll");
        corporaFiles.put("syntagrus", "syntagrus_full.ud");
        corporaFiles.put("opencorpora", "unamb_sent.txt");

    }

    public void diffLemma(int minFreq) throws Exception {

        Map<String, TObjectIntHashMap<String>> c = new HashMap<>();

        for(Map.Entry<String, String> e : corporaFiles.entrySet()) {
            String key = e.getKey();

            try(CorpusReader r = new CorpusReader(new File(baseDir, e.getValue()))) {
                while(true) {
                    Sentence s = r.next();
                    if(s == null)
                        break;

                    for(int i = 0; i < s.size(); i++) {
                        Map<String, String> f = new HashMap<>(s.feats(i));
                        f.remove("Case");
                        MorphInfo mi = new MorphInfo(s.pos(i), f);
                        String k = s.wfLC(i) + "_" + mi.asString(",");
                        TObjectIntHashMap<String> map = c.get(k);

                        if(map == null) {
                            map = new TObjectIntHashMap<>();
                            c.put(k, map);
                        }

                        map.adjustOrPutValue(key + "_" + s.lemma(i).toLowerCase().replace('ё', 'е'), 1, 1);
                    }
                }
            }
        }

        try(PrintWriter pw = new PrintWriter("morphoRuEval.corpus.diff_lemma.txt")) {

            List<Map.Entry<String, TObjectIntHashMap<String>>> entries = new ArrayList<>(c.entrySet());
            Collections.sort(entries, new Comparator<Map.Entry<String, TObjectIntHashMap<String>>>() {
                @Override
                public int compare(Map.Entry<String, TObjectIntHashMap<String>> o1, Map.Entry<String, TObjectIntHashMap<String>> o2) {
                    return new TIntArrayList(o2.getValue().valueCollection()).sum() - new TIntArrayList(o1.getValue().valueCollection()).sum();
                }
            });

            for(Map.Entry<String, TObjectIntHashMap<String>> e : entries) {
                Set<String> lemmas = new HashSet<>();

                for(String lemma : e.getValue().keySet()) {
                    if(e.getValue().get(lemma) >= minFreq) {
                        lemmas.add(lemma.substring(lemma.indexOf('_') + 1));
                    }
                }

                if(lemmas.size() > 1) {
                    IntCounts<String> counts = new IntCounts<String>(e.getValue());
                    counts.inplaceSort(counts.DESC);

                    pw.println(e.getKey());
                    for(int i = 0; i < counts.size(); i++) {
                        pw.printf("\t%s\t%d%n", counts.getObject(i), counts.getCount(i));
                    }

                    pw.println();
                }
            }
        }
    }

    public void diffFeats(int minFreq) throws Exception {

        Map<String, TObjectIntHashMap<String>> c = new HashMap<>();

        for(Map.Entry<String, String> e : corporaFiles.entrySet()) {
            String key = e.getKey();

            try(CorpusReader r = new CorpusReader(new File(baseDir, e.getValue()))) {
                while(true) {
                    Sentence s = r.next();
                    if(s == null)
                        break;

                    for(int i = 0; i < s.size(); i++) {
                        Map<String, String> f = new HashMap<>(s.feats(i));
                        f.remove(UDConst.CASE);
                        f.remove(UDConst.ANIMACY);
                        String lemma = s.lemma(i).toLowerCase().replace('ё', 'е');
                        MorphInfo mi = new MorphInfo(s.pos(i), f);
                        String k = s.wfLC(i) + "_" + lemma + "_" + s.pos(i);
                        TObjectIntHashMap<String> map = c.get(k);

                        if(map == null) {
                            map = new TObjectIntHashMap<>();
                            c.put(k, map);
                        }

                        map.adjustOrPutValue(key + "_" + mi.toString(), 1, 1);
                    }
                }
            }
        }

        try(PrintWriter pw = new PrintWriter("morphoRuEval.corpus.diff_feats.txt")) {

            List<Map.Entry<String, TObjectIntHashMap<String>>> entries = new ArrayList<>(c.entrySet());
            Collections.sort(entries, new Comparator<Map.Entry<String, TObjectIntHashMap<String>>>() {
                @Override
                public int compare(Map.Entry<String, TObjectIntHashMap<String>> o1, Map.Entry<String, TObjectIntHashMap<String>> o2) {
                    return new TIntArrayList(o2.getValue().valueCollection()).sum() - new TIntArrayList(o1.getValue().valueCollection()).sum();
                }
            });

            for(Map.Entry<String, TObjectIntHashMap<String>> e : entries) {
                Set<String> lemmas = new HashSet<>();

                for(String lemma : e.getValue().keySet()) {
                    if(e.getValue().get(lemma) >= minFreq) {
                        lemmas.add(lemma.substring(lemma.indexOf('_') + 1));
                    }
                }

                if(lemmas.size() > 1) {
                    IntCounts<String> counts = new IntCounts<String>(e.getValue());
                    counts.inplaceSort(counts.DESC);

                    pw.println(e.getKey());
                    for(int i = 0; i < counts.size(); i++) {
                        pw.printf("\t%s\t%d%n", counts.getObject(i), counts.getCount(i));
                    }

                    pw.println();
                }
            }
        }
    }




    public static void main(String[] args) throws Exception {
        CorpusEval corpusEval = new CorpusEval();
        corpusEval.diffLemma(5);
        corpusEval.diffFeats(5);
    }

}
