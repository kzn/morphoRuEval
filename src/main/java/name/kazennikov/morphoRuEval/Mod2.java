package name.kazennikov.morphoRuEval;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import name.kazennikov.alphabet.Alphabet;
import name.kazennikov.common.MurmurHash;
import name.kazennikov.count.IntCounts;
import name.kazennikov.ml.core.Instance;
import name.kazennikov.ml.core.MulticlassProblem;
import name.kazennikov.ml.core.SimpleInstance;
import name.kazennikov.ml.svm.AbstractHKMulticlassCS;
import name.kazennikov.ml.svm.DCDMCLinearHK;
import name.kazennikov.ThreadPoolUtils;
import ru.iitp.proling.svm.MulticlassProblemBasic;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Модель FULL
 */
public class Mod2 {

    List<double[]> w = new ArrayList<>();
    int dim;


    Alphabet<String> groupAlphabet = new Alphabet<>(0, -1);
    List<Alphabet<String>> featAlphabet = new ArrayList<>();

    Alphabet<String> featsAlphabet = new Alphabet<>(0, -1);

    Map<String, String> feat2group = new HashMap<>();
    Lemmer lemmer;

    double c = 0.05;



    public Mod2() throws Exception {
        lemmer = new Lemmer(new File("morphoRuEval/gikrya_fixed.txt"), new File("morphoRuEval.dict.dat"));
        lemmer.readAOT(new File("morphoRuEval.aot_dict.dat"));
    }

    public void train(List<Sentence> train, int dim) throws Exception {

        List<Instance> instances = new ArrayList<>();


        // extract all samples, using same features for all groups
        long st = System.currentTimeMillis();

        for(Sentence s : train) {
            List<List<Lemmer.Parse>> parses = new ArrayList<>();

            for(int i = 0; i < s.size(); i++) {
                parses.add(lemmer.morphan(s.wf(i).toLowerCase()));

            }

            for(int i = 0; i < s.size(); i++) {
                instances.add(extractSample(s, parses, i));
            }
        }


        TIntArrayList posLabels = new TIntArrayList();

        Alphabet<String> posAlphabet = new Alphabet<>(0, -1);
        featAlphabet.add(posAlphabet);


        for(Sentence s : train) {
            groupAlphabet.get("POS");

            for(int i = 0; i < s.size(); i++) {
                String tag = s.pos(i);
                feat2group.put(tag, "POS");
                featsAlphabet.get(tag);
                posLabels.add(posAlphabet.get(tag));
            }
        }



        System.out.printf("train: extracted %d samples in %d ms%n", instances.size(), System.currentTimeMillis() - st);

        st = System.currentTimeMillis();

        this.dim = dim;



        ThreadPoolExecutor executor = ThreadPoolUtils.newBlockingThreadPoolExecutor(2, 2, 300, TimeUnit.SECONDS, 2);


        System.out.printf("Training for POS%n");

        List<Future<double[]>> models = new ArrayList<>();

        models.add(executor.submit(new Callable<double[]>() {
            @Override
            public double[] call() throws Exception {
                MulticlassProblem p = new MulticlassProblemBasic(instances, posLabels.toArray());
                Mod1.MCSolver trainer = new Mod1.MCSolver(p, dim, c, 0.1, 1000);
                trainer.solve();

                return trainer.w();
            }
        }));

        for(String g : UDConst.ALL_GROUPS) {
            TIntArrayList labels = new TIntArrayList();
            groupAlphabet.get(g);
            Alphabet<String> alphabet = new Alphabet<>(0, -1);
            featAlphabet.add(alphabet);


            for(Sentence s : train) {
                for(int i = 0; i < s.size(); i++) {
                    String tag = s.feat(i, g);
                    if(tag == null) {
                        tag = "";
                    }
                    feat2group.put(tag, g);
                    featsAlphabet.get(tag);
                    labels.add(alphabet.get(tag));
                }
            }


            models.add(executor.submit(new Callable<double[]>() {
                @Override
                public double[] call() throws Exception {
                    MulticlassProblem p = new MulticlassProblemBasic(instances, labels.toArray());
                    Mod1.MCSolver trainer = new Mod1.MCSolver(p, dim, c, 0.1, 1000);
                    trainer.solve();

                    return trainer.w();
                }
            }));
        }

        executor.shutdown();
        executor.awaitTermination(40, TimeUnit.MINUTES);

        for(Future<double[]> model : models) {
            w.add(model.get());
        }


    }


    public Instance extractSample(Sentence s, List<List<Lemmer.Parse>> parses, int index) {
        return hashFeats(feats(s, parses, index));
    }


    public Instance hashFeats(List<String> feats) {
        TIntArrayList x = new TIntArrayList();
        TDoubleArrayList y = new TDoubleArrayList();


        for(String f : feats) {
            x.add((MurmurHash.hash32(f) >>> 1));
            y.add(1.0);
        }


        return new SimpleInstance(x, y);
    }

    public Set<String> apos(List<List<Lemmer.Parse>> parses, int wordIndex) {
        Set<String> set = new HashSet<>();
        List<Lemmer.Parse> p = wordIndex >= 0 && wordIndex < parses.size()? parses.get(wordIndex) : new ArrayList<>();

        for(Lemmer.Parse parse : p) {
            set.add(parse.pos);
        }

        return set;
    }

    public Set<String> afeat(List<List<Lemmer.Parse>> parses, int wordIndex, String feat) {
        Set<String> set = new HashSet<>();
        List<Lemmer.Parse> p = wordIndex >=0 && wordIndex < parses.size()? parses.get(wordIndex) : new ArrayList<>();

        for(Lemmer.Parse parse : p) {
            set.add(parse.feats.get(feat));
        }

        return set;
    }





    public List<String> feats(Sentence s, List<List<Lemmer.Parse>> parses, int wordIndex) {
        List<String> feats = new ArrayList<>();

        for(int i = -3; i < 4; i++) {
            feats.add("suff2_" + i + "=" + s.suff(wordIndex + i, 2));
            feats.add("suff3_" + i + "=" + s.suff(wordIndex + i, 3));
            feats.add("suff4_" + i + "=" + s.suff(wordIndex + i, 4));

            feats.add("pref2_" + i + "=" + s.pref(wordIndex + i, 2));
            feats.add("pref3_" + i + "=" + s.pref(wordIndex + i, 3));
            feats.add("pref4_" + i + "=" + s.pref(wordIndex + i, 4));

            feats.add("wf_" + i + "=" + s.wf(wordIndex + i));
            feats.add("wflc_" + i + "=" + s.wfLC(wordIndex + i));
            //feats.add("orth" + i + "=" + Lemmer.orth(s.wf(wordIndex + i)));
            //feats.add("orthShort" + i + "=" + Lemmer.orthShort(s.wf(wordIndex + i)));
        }

        // disambig
        for(int i = -3; i < 0; i++) {
            feats.add("pos_" + i + "=" + s.pos(wordIndex + i));
            feats.add("pos/suff3_" + i + "=" + s.pos(wordIndex + i) + "/" + s.suff(wordIndex + i, 3));
            feats.add("pos/suff3_0/" + i + "=" + s.pos(wordIndex + i) + "/" + s.suff(wordIndex, 3));

            feats.add("case_" + i + "=" + s.feat(wordIndex + i, UDConst.CASE));
            feats.add("number_" + i + "=" + s.feat(wordIndex + i, UDConst.NUMBER));
            feats.add("gender_" + i + "=" + s.feat(wordIndex + i, UDConst.GENDER));

            feats.add("pos/case_" + i + "=" + s.feat(wordIndex + i, UDConst.CASE) + s.pos(wordIndex + i));
            feats.add("pos/number_" + i + "=" + s.feat(wordIndex + i, UDConst.NUMBER) + s.pos(wordIndex + i));
            feats.add("pos/gender_" + i + "=" + s.feat(wordIndex + i, UDConst.GENDER) + s.pos(wordIndex + i));
            feats.add("ngc_" + i + "=" + s.feat(wordIndex + i, UDConst.GENDER) + s.feat(wordIndex + i, UDConst.CASE) + s.feat(wordIndex + i, UDConst.NUMBER));
            feats.add("nc_" + i + "=" + s.feat(wordIndex + i, UDConst.CASE) + s.feat(wordIndex + i, UDConst.NUMBER));

            feats.add("case/suff3_0/" + i + "=" + s.feat(wordIndex + i, UDConst.CASE) + "/" + s.suff(wordIndex, 3));
            feats.add("number/suff3_0/" + i + "=" + s.feat(wordIndex + i, UDConst.NUMBER) + "/" + s.suff(wordIndex, 3));
            feats.add("gender/suff3_0/" + i + "=" + s.feat(wordIndex + i, UDConst.GENDER) + "/" + s.suff(wordIndex, 3));

            feats.add("ending" + i + "=" + s.ending(wordIndex + i));
            feats.add("stem" + i + "=" + s.stem(wordIndex + i));


        }

        // ambig
        for(int i = 0; i < 4; i++) {
            Set<String> apos = apos(parses, wordIndex + i);
            feats.add("apos_" + i + "=" + apos);
            feats.add("acase_" + i + "=" + afeat(parses, wordIndex + i, UDConst.CASE));
            feats.add("anumber_" + i + "=" + afeat(parses, wordIndex + i, UDConst.NUMBER));
            feats.add("agender" + i + "=" + afeat(parses, wordIndex + i, UDConst.GENDER));

            feats.add("pos1/apos" + i + "=" + s.pos(wordIndex - 1) + apos);
            feats.add("pos2/apos" + i + "=" + s.pos(wordIndex - 2) + apos);
            feats.add("pos12/apos" + i + "=" + s.pos(wordIndex - 2) + s.pos(wordIndex - 1) + apos);
            feats.add("apos/suff3_" + i + "=" + apos + "/" + s.suff(wordIndex + i, 3));

            feats.add("apos/suff3_" + i + "=" + afeat(parses, wordIndex + i, UDConst.CASE) + "/" + s.suff(wordIndex + i, 3));

            feats.add("apos/suff3_0/" + i + "=" + apos + "/" + s.suff(wordIndex, 3));


            feats.add("apos/case_" + i + "=" + afeat(parses, wordIndex + i, UDConst.CASE) + apos(parses, wordIndex + i));
            feats.add("apos/number_" + i + "=" + afeat(parses, wordIndex + i, UDConst.NUMBER) + apos(parses, wordIndex + i));
            feats.add("apos/gender_" + i + "=" + afeat(parses, wordIndex + i, UDConst.GENDER) + apos(parses, wordIndex + i));
            feats.add("angc_" + i + "=" + afeat(parses, wordIndex + i, UDConst.GENDER) + afeat(parses, wordIndex + i, UDConst.CASE) + afeat(parses, wordIndex + i, UDConst.NUMBER));
            feats.add("anc_" + i + "=" + afeat(parses, wordIndex + i, UDConst.CASE) + afeat(parses, wordIndex + i, UDConst.NUMBER));

            feats.add("acase/suff3_0/" + i + "=" + afeat(parses, wordIndex + i, UDConst.CASE) + "/" + s.suff(wordIndex, 3));
            feats.add("anumber/suff3_0/" + i + "=" + afeat(parses, wordIndex + i, UDConst.NUMBER) + "/" + s.suff(wordIndex, 3));
            feats.add("agender/suff3_0/" + i + "=" + afeat(parses, wordIndex + i, UDConst.GENDER) + "/" + s.suff(wordIndex, 3));


        }

        feats.add("pos12="+ s.pos(wordIndex - 2) + s.pos(wordIndex - 1));
        feats.add("pos1/acase0" + s.pos(wordIndex - 1) + afeat(parses, wordIndex, UDConst.CASE));
        feats.add("wf1/acase0" + s.wfLC(wordIndex - 1) + afeat(parses, wordIndex, UDConst.CASE));


        feats.add("start=" + (wordIndex == 0));

        feats.add("cap=" + (Character.isUpperCase(s.wf(wordIndex).charAt(0))));


        return feats;
    }


    public static int hash(int v, int x, int dim) {
        long z = v;
        z <<= 32;
        z += x;
        return (MurmurHash.hash(z, 0xcafebabe) >>> 1) % dim;
    }



    public static class MCSolver extends AbstractHKMulticlassCS {

        public MCSolver(MulticlassProblem problem, int dim, double c, double eps, int maxIter) {
            super(problem, dim, c, eps, maxIter);
        }


        @Override
        public int h(int v, int x) {
            return hash(v, x, dim());
        }
    }

    public static class MCSolver1 extends DCDMCLinearHK {

        int dim;
        public MCSolver1(List<Instance> instances, int[] targets, int dim, int numClasses, double c, int iter,
                         double eps, int threshold) {
            super(instances, targets, dim, numClasses, c, iter, eps, threshold);
            this.dim = dim;

        }

        @Override
        public int h(int c, int index) {
            return hash(c, index, dim);
        }



    }

    public double dot(double[] w, int cls, Instance x) {
        double d = 0.0;
        for(int i = 0; i < x.size(); i++) {
            d += w[hash(cls, x.indexAt(i), dim)] * x.valueAt(i);
        }

        return d;
    }

    public Lemmer.Parse predict(List<Lemmer.Parse> parses, Instance x) {
        Set<String> set = new HashSet<>();

        if(parses.size() == 1) {
            return parses.get(0);
        }

        for(Lemmer.Parse p : parses) {
            set.addAll(p.subst.asMorphInfo().asList());
        }

        TObjectDoubleHashMap<String> scores = new TObjectDoubleHashMap<>();
        for(String feat : set) {
            String g = feat2group.get(feat);

            if(g == null)
                continue;

            int groupId = this.groupAlphabet.get(g);
            Alphabet<String> groupAlphabet = featAlphabet.get(groupId);
            int featId = groupAlphabet.get(feat);
            double[] w = this.w.get(groupId);
            double score = dot(w, featId, x);
            scores.put(feat,  score);
        }

        String pos = "";
        double maxPosScore = -Double.MAX_VALUE;
        for(String s : set) {
            String group = feat2group.get(s);
            if(Objects.equals(group, "POS")) {
                if(scores.get(s) > maxPosScore) {
                    maxPosScore = scores.get(s);
                    pos = s;
                }
            }
        }



        Lemmer.Parse res = parses.get(0);
        double maxScore = -Double.MAX_VALUE;
        double gScore = 0;


        for(Lemmer.Parse p : parses) {
            double score = 0;

            if(!Objects.equals(p.pos, pos))
                continue;

            for(String s : p.subst.asMorphInfo().asList()) {
                score += scores.get(s);
            }
            if(score >= maxScore) {
                maxScore = score;

//                if(p.score != 0 && p.score > gScore) {
                    res = p;
//                }
            }

        }


        return res;
    }



    public void writeModel(File f) throws Exception {
        try(ObjectOutputStream s = new ObjectOutputStream(new FileOutputStream(f))) {
            s.writeObject(feat2group);
            s.writeObject(groupAlphabet);
            s.writeObject(featAlphabet);
            s.writeObject(featsAlphabet);
            s.writeInt(dim);
            s.writeObject(w);
        }
    }

    public void readModel(File f) throws Exception {
        try(ObjectInputStream s = new ObjectInputStream(new FileInputStream(f))) {
            feat2group = (Map<String, String>) s.readObject();
            groupAlphabet = (Alphabet<String>) s.readObject();
            featAlphabet = (List<Alphabet<String>>) s.readObject();
            featsAlphabet = (Alphabet<String>) s.readObject();
            dim = s.readInt();
            w = (List<double[]>) s.readObject();
        }
    }


    public Sentence predict(Sentence ref) {

        Sentence out = new Sentence();
        List<List<Lemmer.Parse>> parses = new ArrayList<>();
        for(int i = 0; i < ref.size(); i++) {
            Word rw = ref.word(i);
            Word w = new Word();
            w.id = rw.id;
            w.wf = rw.wf;
            w.wfLC = rw.wfLC;
            out.words.add(w);

            List<Lemmer.Parse> parse = lemmer.morphan(ref.wf(i).toLowerCase());

            if(parse.isEmpty()) {
                parse.add(new Lemmer.Parse(ref.wf(i), ref.wf(i), 0, 0, 0, 0, -1, new Subst("", "", "PUNCT", new HashMap<>())));
            }

            parses.add(parse);
        }


        for(int i = 0; i < ref.size(); i++) {


            Word word = out.word(i);

            Instance inst = extractSample(out, parses, i);
            Lemmer.Parse p = predict(parses.get(i), inst);

            if(p == null) {
                p = predict(parses.get(i), inst);
            }

            word.pos = p.pos;
            word.feats = p.feats;
            word.lemma = p.lemma;
        }

        return out;
    }

    public void evalNG(String prefix, Iterable<Sentence> sents) throws Exception {
        try(ComparatorEvaluator evaluator = new ComparatorEvaluator(prefix, MorphoComparator.FINAL)) {
            for(Sentence s : sents) {
                Sentence test = predict(s);
                evaluator.eval(s, test);
            }
        }
    }

    public void morpoRuEval(File out, Iterable<Sentence> sents) throws IOException {
        try(PrintWriter pw = new PrintWriter(out)) {
            for(Sentence s : sents) {
                Sentence s1 = predict(s);
                s1.writeEval(pw);
            }
        }
    }



    public static void main(String[] args) throws Exception {

        Random rnd = new Random(0xdeadbeef);
        double f = 0.1;
        Mod2 mod2 = new Mod2();

        List<Sentence> train = new ArrayList<>();
        List<Sentence> test = new ArrayList<>();

        //try(CorpusReader r = new CorpusReader(new File("/storage/data/morphoRuEval/open_corpora.txt"))) {
        try(CorpusReader r = new CorpusReader(new File("morphoRuEval/gikrya_fixed.txt"))) {
            //try(CorpusReader r = new CorpusReader(new File("/storage/data/ud_english/en.corp2"))) {
            while(true) {
                Sentence s = r.next();
                if(s == null)
                    break;
                //s = s.reverse();

                for(int i = 0; i < s.size(); i++) {
                    if(Objects.equals(s.pos(i), "PROPN")) {
                        s.words.get(i).pos = "NOUN";
                    }
                }

                if(rnd.nextDouble() < f) {
                    test.add(s);
                } else {
                    train.add(s);

                }
            }
        }


        boolean trainModel = true;

        if(trainModel) {
            mod2.train(train, 1 << 21);
            mod2.writeModel(new File("morphoRuEval.mod2.dat"));
        }




        mod2.readModel(new File("morphoRuEval.mod2.dat"));

        mod2.evalNG("morphoRuEval.mod2.test", test);
        mod2.evalNG("morphoRuEval.mod2.train", train);

        /*mod2.evalNG("morphoRuEval.mod2.syntagrus", CorpusReader.iter(new File("morphoRuEval/syntagrus_full.ud")));
        mod2.evalNG("morphoRuEval.mod2.rnc", CorpusReader.iter(new File("morphoRuEval/RNCgoldInUD_Morpho.conll")));
        mod2.evalNG("morphoRuEval.mod2.oc", CorpusReader.iter(new File("morphoRuEval/unamb_sent.txt")));*/






    }
}
