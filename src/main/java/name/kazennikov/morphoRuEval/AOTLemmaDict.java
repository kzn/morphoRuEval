package name.kazennikov.morphoRuEval;

import com.google.common.base.Joiner;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TObjectIntHashMap;
import name.kazennikov.alphabet.Alphabet;
import name.kazennikov.count.IntCounts;
import name.kazennikov.fsa.BooleanFSABuilder;
import name.kazennikov.fsa.walk.WalkFSABoolean;
import name.kazennikov.morph.aot.MorphConfig;
import name.kazennikov.morph.aot.MorphDict;
import name.kazennikov.morph.aot.MorphLanguage;
import name.kazennikov.morph.aot.Paradigm;
import name.kazennikov.trove.TroveUtils;


import java.io.File;
import java.io.PrintWriter;
import java.util.*;

/**
 * Created on 3/8/17.
 *
 * @author Anton Kazennikov
 */
public class AOTLemmaDict {

    // выделение причастий в отдельные парадигмы (и разделяя страдательные/нестрадательные причастия)
    public static List<Paradigm> splitVerb(Paradigm p) {
        boolean hasVerbAdj = false;





//        if(!Objects.equals(p.getNormal().getRec().pos, "ИНФИНИТИВ"))
//            return Arrays.asList(p);

        Paradigm filtered = new Paradigm();
        Paradigm adj0 = new Paradigm(); // стр
        Paradigm adj1 = new Paradigm(); // дст


        List<Paradigm> res = new ArrayList<>();
        List<Paradigm> resADV = new ArrayList<>();

        for(int i = 0; i < p.size(); i++) {
            Paradigm.Entry e = p.get(i);

            if(Objects.equals(e.getRec().pos, "ПРИЧАСТИЕ") || Objects.equals(e.getRec().pos, "КР_ПРИЧАСТИЕ")) {
                Paradigm adj = e.getRec().feats.contains("стр")? adj0 : adj1;

                adj.addEntry(e.getEnding(), e.getPrefix(), e.getRec());
            } else {
                filtered.addEntry(e.getEnding(), e.getPrefix(), e.getRec());
            }


            // ADJ, сравн -> ADV, сравн
            if(e.getRec().pos.equals("П") && e.getRec().feats.contains("сравн")) {
                Paradigm pp = new Paradigm();
                pp.addEntry(e.getEnding(), e.getPrefix(), e.getRec());

                resADV.add(pp);
            }
        }




        if(filtered.size() != 0 && (adj0.size() != 0 || adj1.size() != 0)) {
            res.add(filtered);
        }

        if(adj0.size() != 0) {
            res.add(adj0);
        }

        if(adj1.size() != 0) {
            res.add(adj1);
        }

        if(res.isEmpty()) {
            res.add(p);
        }

        res.addAll(resADV);

        return res;
    }


    public static class Subst {
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
    }

    Alphabet<Subst> substs = new Alphabet<>();
    WalkFSABoolean fsa;



    public void build() throws Exception {
        MorphConfig mc = MorphConfig.newInstance(new File("modules/jaot/russian.xml"));

        MorphLanguage ml = new MorphLanguage(mc);

        long st = System.currentTimeMillis();
        MorphDict md = ml.readDict();

        BooleanFSABuilder builder = new BooleanFSABuilder();

        int count = 0;





        for(MorphDict.Lemma entry : md.getLemmas()) {
            int stemLength = entry.getStem().length();
            TIntArrayList l = new TIntArrayList();


            for(Paradigm p : splitVerb(entry.getParadigm())) {


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
                    builder.addMinWord(l);
                }
            }


            count++;
            if(count % 10000 == 0) {
                System.out.printf("%d...%n", count);
            }
        }

        System.out.printf("Count: %d%n", count);

        System.out.printf("FSA size: %d, substs: %d%n", builder.size(), substs.size());

        fsa = builder.build();

    }


    public List<Subst> parses(String wf) {
        TIntArrayList states = fsa.walk(0, wf, 0, wf.length());
        List<Subst> res = new ArrayList<>();
        int lastState = states.get(states.size() - 1);
        if(states.size() != wf.length() + 1 || !fsa.hasAnnotStart(lastState))
            return res;

        TIntArrayList l = fsa.collectAnnotationsSimple(lastState);

        for(int i = 0; i < l.size(); i++) {
            res.add(substs.get(l.get(i)));
        }

        return res;
    }




    public static void main(String[] args) throws Exception {

        AOTLemmaDict dict = new AOTLemmaDict();
        dict.build();

        AOT2UD conv = new AOT2UD();

        Set<String> skip = new HashSet<>(Arrays.asList("PUNCT", "H", "ADP", "CONJ", "DET", "PART", "PRON"));


        TObjectIntHashMap<String> missed = new TObjectIntHashMap<>();
        Map<String, Set<String>> missParses = new HashMap<>();

        int total = 0;
        int totmissed = 0;
//        try(CorpusReader r = new CorpusReader(new File("/storage/data/morphoRuEval/gikrya_fixed.txt"))) {
        try(CorpusReader r = new CorpusReader(new File("/storage/data/morphoRuEval/full_corpora.txt"))) {
            while(true) {
                Sentence s = r.next();

                if(s == null)
                    break;

                for(int i = 0; i < s.size(); i++) {

                    List<Subst> substs = dict.parses(s.wfLC(i));

                    if(substs.isEmpty())
                        continue;
                    if(skip.contains(s.pos(i)))
                        continue;

                    total++;

                    TreeSet<String> lemmas = new TreeSet<>();
                    TreeSet<String> feats = new TreeSet<>();
                    for(Subst subst : substs) {
                        String lemma = subst.lemma(s.wfLC(i)).toLowerCase().replace('ё', 'е');
                        lemmas.add(lemma);
                        feats.add(lemma + "\t" + subst.pos + "\t" + subst.feats);


//                        String pos = conv.pos(subst.pos);
//
//                        if(Objects.equals(lemma, s.lemma(i).toLowerCase().replace('ё', 'е')) && !Objects.equals(pos, s.pos(i))) {
//                            System.out.printf("%s: expected: %s, got: %s (%s)%n", s.wfLC(i), s.pos(i), pos, subst.pos);
//
//
//                        }
                    }

                    String lemma = s.lemma(i).toLowerCase().replace('ё', 'е');

                    if(!lemmas.contains(lemma)) {
                        missed.adjustOrPutValue(s.wfLC(i) + "\t" + lemma + "\t" + Joiner.on(", ").join(lemmas) , 1, 1);
                        missParses.put(s.wfLC(i), feats);
                    }
                }



            }
        }

        try(PrintWriter pw = new PrintWriter("morphoRuEval.aot.miss.lemma.txt")) {
            IntCounts<String> c = new IntCounts<String>(missed);
            c.inplaceSort(c.DESC);

            pw.printf("count\twf\tlemma\tpred lemmas\tparses%n");
            for(int i = 0; i < c.size(); i++) {
                String[] parts = c.getObject(i).split("\t");
                pw.printf("%d\t%s\t%s%n", c.getCount(i), c.getObject(i), missParses.get(parts[0]));
                totmissed += c.getCount(i);
            }
        }

        System.out.printf("Total missed: %d/%d words%n", totmissed, total);



            //MorphCompiler morphCompiler = new MorphCompiler(ml);
//
        //morphCompiler.compile(md, 5, 0, new File("russian.dat"));



    }
}
