package name.kazennikov.morphoRuEval;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import name.kazennikov.alphabet.Alphabet;
import name.kazennikov.common.MurmurHash;
import name.kazennikov.ml.core.Instance;
import name.kazennikov.ml.core.MulticlassProblem;
import name.kazennikov.ml.core.SimpleInstance;
import name.kazennikov.ml.svm.AbstractHKMulticlassCS;
import name.kazennikov.ml.svm.DCDMCLinearHK;
import ru.iitp.proling.svm.MulticlassProblemBasic;

import java.io.*;
import java.util.*;

/**
 * Модель POS+Case с морфологией
 *
 * @author Anton Kazennikov
 */
public class Model3 {

    double[] w;
    int dim;



    Alphabet<String> labelAlphabet = new Alphabet<>(0, -1);
    Lemmer lemmer;


    public Model3() throws Exception {
        lemmer = new Lemmer(new File("/storage/data/morphoRuEval/GIKRYA_texts.txt"), new File("morphoRuEval.dict.dat"));
    }

    public static void poscase(Sentence s) {
        for(Word w : s.words) {
            String pos = w.pos;

            String cs = w.feats.get("Case");
            if(cs != null) {
                w.pos = w.pos + "_" + cs;

            }
        }
    }



    public Dataset extractSamples(Dataset dataset, Sentence s) {
        if(dataset == null) {
            dataset = new Dataset();
        }

        TIntArrayList[] possiblePOS = new TIntArrayList[s.size()];

        for(int wordIndex = 0; wordIndex < s.size(); wordIndex++) {
            possiblePOS[wordIndex] = possiblePOS(s.wf(wordIndex));
            possiblePOS[wordIndex].sort();
        }

        for(int wordIndex = 0; wordIndex < s.size(); wordIndex++) {
            dataset.labels.add(extractLabel(s, wordIndex));
            dataset.samples.add(extractSample(s, possiblePOS, wordIndex));
        }

        return dataset;
    }


    private int extractLabel(Sentence s, int wordIndex) {
        return labelAlphabet.get(s.pos(wordIndex));
    }


    public Instance extractSample(Sentence s, TIntArrayList[] possiblePOS, int wordIndex) {
        TIntArrayList x = new TIntArrayList();
        TDoubleArrayList y = new TDoubleArrayList();



        for(String f : feats(s, possiblePOS, wordIndex)) {
            x.add((MurmurHash.hash32(f) >>> 1));
            y.add(1.0);
        }


        return new SimpleInstance(x, y);
    }


    public List<String> feats(Sentence s, TIntArrayList[] possiblePOS, int wordIndex) {
        List<String> feats = new ArrayList<>();

        for(int i = -3; i < 4; i++) {
            feats.add("suff2_" + i + "=" + s.suff(wordIndex + i, 2));
            feats.add("suff3_" + i + "=" + s.suff(wordIndex + i, 3));
            feats.add("suff4_" + i + "=" + s.suff(wordIndex + i, 4));

            feats.add("pref2_" + i + "=" + s.pref(wordIndex + i, 2));
            feats.add("pref3_" + i + "=" + s.pref(wordIndex + i, 3));
            feats.add("pref4_" + i + "=" + s.pref(wordIndex + i, 4));

            feats.add("wf_" + i + "=" + s.wf(wordIndex + i));
            feats.add("wfLC_" + i + "=" + s.wfLC(wordIndex + i));
            feats.add("orth" + i + "=" + Lemmer.orthShort(s.wf(i)));
        }

        // disambig
        for(int i = -3; i < 0; i++) {
            feats.add("pos_" + i + "=" + s.pos(wordIndex + i));
            feats.add("pos/suff3_" + i + "=" + s.pos(wordIndex + i) + "/" + s.suff(wordIndex + i, 3));

            feats.add("pos/suff3_0/" + i + "=" + s.pos(wordIndex + i) + "/" + s.suff(wordIndex, 3));
        }

        // ambig
        for(int i = 0; i < 4; i++) {
            TIntArrayList apos = wordIndex + i < possiblePOS.length? possiblePOS[wordIndex + i] : new TIntArrayList();
            feats.add("apos_" + i + "=" + apos);
            feats.add("pos1/apos" + i + "=" + s.pos(wordIndex - 1) + apos);
//            feats.add("pos2/apos" + i + "=" + s.pos(wordIndex - 2) + apos);
//            feats.add("pos12/apos" + i + "=" + s.pos(wordIndex - 2) + s.pos(wordIndex - 1) + apos);
            feats.add("apos/suff3_" + i + "=" + apos + "/" + s.suff(wordIndex + i, 3));

            feats.add("apos/suff3_0/" + i + "=" + apos + "/" + s.suff(wordIndex, 3));
        }

        //feats.add("pos12="+ s.pos(wordIndex - 2) + s.pos(wordIndex - 1));


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


    public int cls(double[] w, Instance x) {
        int index = 0;
        double max = -Double.MAX_VALUE;

        for(int i = 0; i < labelAlphabet.size(); i++) {
            double dot = dot(w, i, x);
            if(dot > max) {
                index = i;
                max = dot;
            }
        }

        return index;
    }

    public int cls(double[] w, Instance x, TIntArrayList classes) {
        int index = 0;
        double max = -Double.MAX_VALUE;

        for(int i = 0; i < classes.size(); i++) {
            double dot = dot(w, classes.get(i), x);
            if(dot > max) {
                index = classes.get(i);
                max = dot;
            }
        }

        return index;
    }

    public TIntArrayList possiblePOS(String wf) {

        Set<Lemmer.Parse> out = new HashSet<>(lemmer.morphan(wf));


        TIntHashSet set = new TIntHashSet();
        for(Lemmer.Parse p: out) {
            String cs = p.feats.get("Case");

            set.add(labelAlphabet.get(cs == null? p.pos : p.pos + "_" + cs));
        }

        return new TIntArrayList(set);
    }


    public void writeModel(File f) throws Exception {
        try(ObjectOutputStream s = new ObjectOutputStream(new FileOutputStream(f))) {
            s.writeObject(labelAlphabet);
            s.writeInt(dim);
            s.writeObject(w);
        }
    }

    public void readModel(File f) throws Exception {
        try(ObjectInputStream s = new ObjectInputStream(new FileInputStream(f))) {
            labelAlphabet = (Alphabet<String>) s.readObject();
            dim = s.readInt();
            w = (double[]) s.readObject();
        }
    }

    public Sentence predict(Sentence ref) {
        Sentence out = new Sentence();

        TIntArrayList[] possiblePOS = new TIntArrayList[ref.size()];
        for(int i = 0; i < ref.size(); i++) {
            Word rw = ref.word(i);
            possiblePOS[i] = possiblePOS(rw.wfLC);
            possiblePOS[i].sort();


            Word w = new Word();
            w.id = rw.id;
            w.wf = rw.wf;
            w.wfLC = rw.wfLC;
            out.words.add(w);
        }



        for(int i = 0; i < out.size(); i++) {
            Instance inst = extractSample(out, possiblePOS, i);
            TIntArrayList labels = possiblePOS(out.wf(i));

            Word w = out.word(i);

            int cls = !labels.isEmpty()? cls(this.w, inst, labels) : cls(this.w, inst);
            w.pos = labelAlphabet.get(cls);
        }

        return out;
    }

    public void eval(String prefix, List<Sentence> sents) throws Exception {
        try(ComparatorEvaluator evaluator = new ComparatorEvaluator(prefix, MorphoComparator.POS_ONLY)) {
            for(Sentence s : sents) {
                Sentence test = predict(s);
                evaluator.eval(s, test);
            }
        }
    }


    public static void main(String[] args) throws Exception {

        Random rnd = new Random(0xdeadbeef);
        double f = 0.1;

        List<Sentence> train = new ArrayList<>();
        List<Sentence> test = new ArrayList<>();

        Model3 mod3 = new Model3();
        //try(CorpusReader r = new CorpusReader(new File("/storage/data/morphoRuEval/gikrya_fixed.txt"))) {
        try(CorpusReader r = new CorpusReader(new File("/storage/data/morphoRuEval/full_corpora.txt"))) {
        //try(CorpusReader r = new CorpusReader(new File("/storage/data/ud_english/en.corp2"))) {
            while(true) {
                Sentence s = r.next();

                if(s == null)
                    break;
                poscase(s);

                //s = s.reverse();
                if(rnd.nextDouble() < f) {
                    test.add(s);
                } else {
                    train.add(s);

                }
            }
        }


        boolean trainModel = false;

        if(trainModel) {

            long st = System.currentTimeMillis();
            Dataset trainds = new Dataset();

            for(Sentence s : train) {
                mod3.extractSamples(trainds, s);
            }



            System.out.printf("train: extracted %d samples in %d ms%n", trainds.samples.size(), System.currentTimeMillis() - st);

            st = System.currentTimeMillis();

            int dim = 1 << 20;

            //MCSolver1 trainer = new MCSolver1(train.samples, train.labels.toArray(), dim, mod0.labelAlphabet.size(), 0.1, 1000, 0.1, 5000000);
            MulticlassProblem p = new MulticlassProblemBasic(trainds.samples, trainds.labels.toArray());
            MCSolver trainer = new MCSolver(p, dim, 0.05, 0.1, 1000);

            //trainer.setVerbosity(1000);
            trainer.solve();

            mod3.w = trainer.w();
            mod3.dim = dim;

            mod3.w = trainer.w();
            mod3.dim = dim;

            mod3.writeModel(new File("morphoRuEval.mod3.dat"));
        }


        mod3.readModel(new File("morphoRuEval.mod3.dat"));

        mod3.eval("morphoRuEval.mod3.test", test);
        mod3.eval("morphoRuEval.mod3.train", train);








    }
}
