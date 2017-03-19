package name.kazennikov.morphoRuEval;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import name.kazennikov.alphabet.Alphabet;
import name.kazennikov.common.MurmurHash;
import name.kazennikov.count.IntCount;
import name.kazennikov.count.IntCounts;
import name.kazennikov.ml.core.Instance;
import name.kazennikov.ml.core.MulticlassProblem;
import name.kazennikov.ml.core.SimpleInstance;
import name.kazennikov.ml.svm.AbstractHKMulticlassCS;
import name.kazennikov.ml.svm.DCDMCLinearHK;
import net.didion.jwnl.data.Exc;
import ru.iitp.proling.svm.MulticlassProblemBasic;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Модель POS без использования морфологии.
 *
 * @author Anton Kazennikov
 */
public class Mod0 {

    public static class Dataset {
        List<Instance> samples = new ArrayList<>();
        TIntArrayList labels = new TIntArrayList();
    }

    List<Sentence> train = new ArrayList<>();
    List<Sentence> test = new ArrayList<>();


    double[] w;
//    double[] wCorr;
    int dim;



    Alphabet<String> labelAlphabet = new Alphabet<>(0, -1);
    Lemmer lemmer;


    public Mod0() throws Exception {
        lemmer = new Lemmer(new File("/storage/data/morphoRuEval/GIKRYA_texts.txt"), new File("morphoRuEval.dict.dat"));
    }

    public Dataset extractSamples(Dataset dataset, Sentence s) {
        if(dataset == null) {
            dataset = new Dataset();
        }

        for(int wordIndex = 0; wordIndex < s.size(); wordIndex++) {
            dataset.labels.add(extractLabel(s, wordIndex));
            dataset.samples.add(extractSample(s, wordIndex));
        }

        return dataset;
    }

//    public Dataset extractCorrSamples(Dataset dataset, Sentence s) {
//        if(dataset == null) {
//            dataset = new Dataset();
//        }
//
//        for(int wordIndex = 0; wordIndex < s.size(); wordIndex++) {
//            dataset.labels.add(extractLabel(s, wordIndex));
//            dataset.samples.add(extractCorrSample(s, wordIndex));
//        }
//
//        return dataset;
//    }

    private int extractLabel(Sentence s, int wordIndex) {
        return labelAlphabet.get(s.pos(wordIndex));
    }


    public Instance extractSample(Sentence s, int wordIndex) {
        TIntArrayList x = new TIntArrayList();
        TDoubleArrayList y = new TDoubleArrayList();



        for(String f : feats(s, wordIndex)) {
            x.add((MurmurHash.hash32(f) >>> 1));
            y.add(1.0);
        }


        return new SimpleInstance(x, y);
    }

//    public Instance extractCorrSample(Sentence s, int wordIndex) {
//        TIntArrayList x = new TIntArrayList();
//        TDoubleArrayList y = new TDoubleArrayList();
//
//
//
//        for(String f : feats(s, wordIndex)) {
//            x.add((MurmurHash.hash32(f) >>> 1));
//            y.add(1.0);
//        }
//
//
//        return new SimpleInstance(x, y);
//    }


    public List<String> feats(Sentence s, int wordIndex) {
        List<String> feats = new ArrayList<>();

        for(int i = -3; i < 4; i++) {
            feats.add("suff2_" + i + "=" + s.suff(wordIndex + i, 2));
            feats.add("suff3_" + i + "=" + s.suff(wordIndex + i, 3));
            feats.add("suff4_" + i + "=" + s.suff(wordIndex + i, 4));

            feats.add("pref2_" + i + "=" + s.pref(wordIndex + i, 2));
            feats.add("pref3_" + i + "=" + s.pref(wordIndex + i, 3));
            feats.add("pref4_" + i + "=" + s.pref(wordIndex + i, 4));

            feats.add("wf_" + i + "=" + s.wf(wordIndex + i));
        }


        for(int i = -3; i < 0; i++) {
            feats.add("pos_" + i + "=" + s.pos(wordIndex + i));


            feats.add("pos/suff3_" + i + "=" + s.pos(wordIndex + i) + "/" + s.suff(wordIndex + i, 3));

            feats.add("pos/suff3_0/" + i + "=" + s.pos(wordIndex + i) + "/" + s.suff(wordIndex, 3));

        }

        feats.add("pos-12_" + "=" + s.pos(wordIndex - 1) + s.pos(wordIndex - 2));
        feats.add("pos-13_" + "=" + s.pos(wordIndex - 1) + s.pos(wordIndex - 3));
        feats.add("pos-123_" + "=" + s.pos(wordIndex - 1) + s.pos(wordIndex - 2) + s.pos(wordIndex - 3));

        feats.add("start=" + (wordIndex == 0));
        feats.add("cap=" + (Character.isUpperCase(s.wf(wordIndex).charAt(0))));


        return feats;
    }

    public List<String> corrFeats(Sentence s, int wordIndex) {
        List<String> feats = new ArrayList<>();

        for(int i = -3; i < 4; i++) {
            feats.add("suff2_" + i + "=" + s.suff(wordIndex + i, 2));
            feats.add("suff3_" + i + "=" + s.suff(wordIndex + i, 3));
            feats.add("suff4_" + i + "=" + s.suff(wordIndex + i, 4));

            feats.add("pref2_" + i + "=" + s.pref(wordIndex + i, 2));
            feats.add("pref3_" + i + "=" + s.pref(wordIndex + i, 3));
            feats.add("pref4_" + i + "=" + s.pref(wordIndex + i, 4));

            feats.add("wf_" + i + "=" + s.wf(wordIndex + i));


            if(i != 0) {
                feats.add("pos_" + i + "=" + s.pos(wordIndex + i));
                feats.add("pos/suff3_" + i + "=" + s.pos(wordIndex + i) + "/" + s.suff(wordIndex + i, 3));

                feats.add("pos/suff3_0/" + i + "=" + s.pos(wordIndex + i) + "/" + s.suff(wordIndex, 3));
            }
        }

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
        TIntHashSet set = new TIntHashSet();
        List<Lemmer.Parse> guess = lemmer.guess(wf);
        List<Lemmer.Parse> dict = lemmer.parseDict(wf);

        for(Lemmer.Parse p : guess) {
            Subst s = lemmer.substAlphabet.get(p.substId);
            set.add(labelAlphabet.get(s.pos));
        }

        // "закрытые" части речи
        for(Lemmer.Parse p : dict) {
            Subst s = lemmer.substAlphabet.get(p.substId);
            if(UDConst.CLOSED_POS.contains(s.pos) || Objects.equals(s.pos, "INTJ")) {
                set.add(labelAlphabet.get(s.pos));
            }
        }

        return new TIntArrayList(set);
    }


    public void writeModel(File f) throws Exception {
        try(ObjectOutputStream s = new ObjectOutputStream(new FileOutputStream(f))) {
            s.writeObject(labelAlphabet);
            s.writeInt(dim);
            s.writeObject(w);
            //s.writeObject(wCorr);
        }
    }

    public void readModel(File f) throws Exception {
        try(ObjectInputStream s = new ObjectInputStream(new FileInputStream(f))) {
            labelAlphabet = (Alphabet<String>) s.readObject();
            dim = s.readInt();
            w = (double[]) s.readObject();
            //wCorr = (double[]) s.readObject();
        }
    }

    public void eval(String prefix, List<Sentence> sents) throws Exception {
        int miss = 0;
        int total = 0;

        TObjectIntHashMap<String> counts = new TObjectIntHashMap<>();

        try(PrintWriter pw = new PrintWriter(prefix + ".eval.dat")) {
            pw.printf("Seq\tHas Error\tWordform\tLemma\tpred label\tref label\tpred score\tref score\tscore diff%n");
            for(Sentence s : sents) {
                TIntArrayList refLabels = new TIntArrayList();

                for(int i = 0; i < s.size(); i++) {
                    int label = extractLabel(s, i);
                    refLabels.add(label);
                    s.words.get(i).pos = null;
                    total++;
                }

                TIntArrayList predicted = new TIntArrayList();
                TDoubleArrayList scoreRef = new TDoubleArrayList();
                TDoubleArrayList scorePred = new TDoubleArrayList();
                boolean hasErr = false;

                for(int i = 0; i < s.size(); i++) {
                    Instance inst = extractSample(s, i);
                    TIntArrayList labels = possiblePOS(s.wf(i));

                    int cls = !labels.isEmpty()? cls(w, inst, labels) : cls(w, inst);
                    predicted.add(cls);

                    scoreRef.add(dot(w, refLabels.get(i), inst));
                    scorePred.add(dot(w, cls, inst));

                    s.words.get(i).pos = labelAlphabet.get(cls);

                    if(cls != refLabels.get(i)) {
                        miss++;
                        counts.adjustOrPutValue(labelAlphabet.get(cls) + "/" + labelAlphabet.get(refLabels.get(i)), 1, 1);
                        hasErr = true;
                    }
                }

                if(hasErr) {
                    for(int i = 0; i < s.size(); i++) {
                        // seq miss? word lemma pos_ref pos_pred
                        pw.printf("%d\t%s\t%s\t%s\t%s\t%s\t%f\t%f\t%f%n",
                                i + 1,
                                refLabels.get(i) != predicted.get(i)? "*" : "",
                                s.wf(i),
                                s.lemma(i),
                                labelAlphabet.get(predicted.get(i)),
                                labelAlphabet.get(refLabels.get(i)),
                                scorePred.get(i),
                                scoreRef.get(i),
                                scoreRef.get(i) - scorePred.get(i)
                        );
                    }


                    pw.println();
                    pw.println();
                }
            }
        }

        try(PrintWriter pw = new PrintWriter(prefix + ".result.dat")) {
            pw.printf("Error=%d/%d (%.04f prec)%n", miss, total, 1.0 * (total - miss) / total);
        }

        IntCounts<String> confusionCounts = new IntCounts<>(counts);
        confusionCounts.inplaceSort(confusionCounts.DESC);
        try(PrintWriter pw = new PrintWriter(prefix + ".confusion.dat")) {
            for(int i = 0; i < confusionCounts.size(); i++) {
                pw.printf("%s\t%d%n", confusionCounts.getObject(i), confusionCounts.getCount(i));
            }
        }

    }


//    public void evalCorr(String prefix, List<Sentence> sents) throws Exception {
//        int miss = 0;
//        int total = 0;
//
//        TObjectIntHashMap<String> counts = new TObjectIntHashMap<>();
//
//        try(PrintWriter pw = new PrintWriter(prefix + ".eval.dat")) {
//            pw.printf("Seq\tHas Error\tWordform\tLemma\tpred label\tref label\tpred score\tref score\tscore diff%n");
//            for(Sentence s : sents) {
//                TIntArrayList refLabels = new TIntArrayList();
//
//                for(int i = 0; i < s.size(); i++) {
//                    int label = extractLabel(s, i);
//                    refLabels.add(label);
//                    s.words.get(i).pos = null;
//                    total++;
//                }
//
//                TIntArrayList predicted = new TIntArrayList();
//                TDoubleArrayList scoreRef = new TDoubleArrayList();
//                TDoubleArrayList scorePred = new TDoubleArrayList();
//                boolean hasErr = false;
//
//                // 1st run
//                for(int i = 0; i < s.size(); i++) {
//                    Instance inst = extractSample(s, i);
//                    int cls = cls(w, inst);
//                    predicted.add(cls);
//
//                    scoreRef.add(dot(w, refLabels.get(i), inst));
//                    scorePred.add(dot(w, cls, inst));
//
//                    s.words.get(i).pos = labelAlphabet.get(cls);
//                }
//
//                // 2nd run, corrections
//                for(int i = 0; i < s.size(); i++) {
//                    Instance inst = extractCorrSample(s, i);
//                    int cls = cls(wCorr, inst);
//                    predicted.add(cls);
//
//                    scoreRef.add(dot(wCorr, refLabels.get(i), inst));
//                    scorePred.add(dot(wCorr, cls, inst));
//
//                    s.words.get(i).pos = labelAlphabet.get(cls);
//
//                    if(cls != refLabels.get(i)) {
//                        miss++;
//                        counts.adjustOrPutValue(labelAlphabet.get(cls) + "/" + labelAlphabet.get(refLabels.get(i)), 1, 1);
//                        hasErr = true;
//                    }
//                }
//
//
//                if(hasErr) {
//                    for(int i = 0; i < s.size(); i++) {
//                        // seq miss? word lemma pos_ref pos_pred
//                        pw.printf("%d\t%s\t%s\t%s\t%s\t%s\t%f\t%f\t%f%n",
//                                i + 1,
//                                refLabels.get(i) != predicted.get(i)? "*" : "",
//                                s.wf(i),
//                                s.lemma(i),
//                                labelAlphabet.get(predicted.get(i)),
//                                labelAlphabet.get(refLabels.get(i)),
//                                scorePred.get(i),
//                                scoreRef.get(i),
//                                scoreRef.get(i) - scorePred.get(i)
//                        );
//                    }
//
//
//                    pw.println();
//                    pw.println();
//                }
//            }
//        }
//
//        try(PrintWriter pw = new PrintWriter(prefix + ".result.dat")) {
//            pw.printf("Error=%d/%d (%.04f prec)%n", miss, total, 1.0 * (total - miss) / total);
//        }
//
//        IntCounts<String> confusionCounts = new IntCounts<>(counts);
//        confusionCounts.inplaceSort(confusionCounts.DESC);
//        try(PrintWriter pw = new PrintWriter(prefix + ".confusion.dat")) {
//            for(int i = 0; i < confusionCounts.size(); i++) {
//                pw.printf("%s\t%d%n", confusionCounts.getObject(i), confusionCounts.getCount(i));
//            }
//        }
//
//    }

    public static void main(String[] args) throws Exception {

        Random rnd = new Random(0xdeadbeef);
        double f = 0.1;
        Mod0 mod0 = new Mod0();
        try(CorpusReader r = new CorpusReader(new File("/storage/data/morphoRuEval/GIKRYA_texts.txt"))) {
        //try(CorpusReader r = new CorpusReader(new File("/storage/data/ud_english/en.corp2"))) {
            while(true) {
                Sentence s = r.next();
                if(s == null)
                    break;
                if(rnd.nextDouble() < f) {
                    mod0.test.add(s);
                } else {
                    mod0.train.add(s);

                }
            }
        }

        long st = System.currentTimeMillis();
        Dataset train = new Dataset();

        for(Sentence s : mod0.train) {
            mod0.extractSamples(train, s);
        }



        System.out.printf("train: extracted %d samples in %d ms%n", train.samples.size(), System.currentTimeMillis() - st);

        st = System.currentTimeMillis();

        int dim = 1 << 20;

        boolean trainModel = true;

        if(trainModel) {
            //MCSolver1 trainer = new MCSolver1(train.samples, train.labels.toArray(), dim, mod0.labelAlphabet.size(), 0.1, 1000, 0.1, 5000000);
            MulticlassProblem p = new MulticlassProblemBasic(train.samples, train.labels.toArray());
            MCSolver trainer = new MCSolver(p, dim, 0.1, 0.1, 1000);

            //trainer.setVerbosity(1000);
            trainer.solve();
            //System.out.printf("Solution 0/1: %f%n", trainer.zero_one_loss());

            mod0.w = trainer.w();
            mod0.dim = dim;

            MulticlassProblem pCorr = new MulticlassProblemBasic(train.samples, train.labels.toArray());
            //MCSolver trainerCorr = new MCSolver(pCorr, dim, 0.1, 0.1, 1000);

            //trainer.setVerbosity(1000);
            //trainerCorr.solve();
            //System.out.printf("Solution 0/1: %f%n", trainer.zero_one_loss());

            mod0.w = trainer.w();
            //mod0.wCorr = trainerCorr.w();
            mod0.dim = dim;

            mod0.writeModel(new File("morphoRuEval.mod0.dat"));
        }


        mod0.readModel(new File("morphoRuEval.mod0.dat"));

        mod0.eval("morphoRuEval.mod0.train", mod0.train);
        mod0.eval("morphoRuEval.mod0.test", mod0.test);







    }
}
