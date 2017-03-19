package name.kazennikov.morphoRuEval;

import gnu.trove.map.hash.TObjectIntHashMap;
import name.kazennikov.count.IntCounts;
import name.kazennikov.ml.core.Instance;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Created on 3/4/17.
 *
 * @author Anton Kazennikov
 */
public class Evaluator implements Closeable {

    String prefix;
    PrintWriter pw;

    int words = 0;
    int miss = 0;
    int missPos = 0;
    int missLemma = 0;
    int missFeats = 0;

    int seqId = 0;

    TObjectIntHashMap<String> confusion = new TObjectIntHashMap<>();



    public Evaluator(String prefix) throws IOException {
        this.prefix = prefix;
        pw = new PrintWriter(prefix + ".eval.dat");

        pw.printf("Seq\tHas Error\tWordform\tpred lemma\tref lemma\tpred label\tref label\tpred score\tref score\tscore diff%n");
    }


    @Override
    public void close() throws IOException {
        pw.close();

        try(PrintWriter pw = new PrintWriter(prefix + ".result.dat")) {
            pw.printf("Full: error=%d/%d (%.04f prec)%n", miss, words, 1.0 * (words - miss) / words);

            pw.printf("POS: error=%d/%d (%.04f prec)%n", missPos, words, 1.0 * (words - missPos) / words);
            pw.printf("Feats: error=%d/%d (%.04f prec)%n", missFeats, words, 1.0 * (words - missFeats) / words);
            pw.printf("Lemma: error=%d/%d (%.04f prec)%n", missLemma, words, 1.0 * (words - missLemma) / words);
        }

        IntCounts<String> confusionCounts = new IntCounts<>(confusion);
        confusionCounts.inplaceSort(confusionCounts.DESC);
        try(PrintWriter pw = new PrintWriter(prefix + ".confusion.dat")) {
            for(int i = 0; i < confusionCounts.size(); i++) {
                pw.printf("%s\t%d%n", confusionCounts.getObject(i), confusionCounts.getCount(i));
            }
        }


    }

    public boolean skipEval(Word refWord) {
        return false;
    }

    public boolean comparePOS(Word refWord, Word testWord) {
        return Objects.equals(refWord.pos, testWord.pos);
    }

    public boolean compareLemma(Word ref, Word test) {
        return Objects.equals(ref.lemma, test.lemma);
    }

    public boolean compareFeats(Word ref, Word test) {
        return Objects.equals(ref.feats, test.feats);
    }


    /**
     * Evaluate sentences. assumes the correct tokenization.
     * @param ref reference sentence
     * @param test test sentence
     */
    public void eval(Sentence ref, Sentence test) throws IOException {

        boolean hasErr = false;
        seqId++;

        if(seqId == 0) {
            seqId = seqId;
        }

        for(int wordIndex = 0; wordIndex < ref.size(); wordIndex++) {
            Word refWord = ref.word(wordIndex);
            Word testWord = test.word(wordIndex);

            if(skipEval(refWord))
                continue;

            words++;


            boolean missedPOS = false;
            boolean missedFeats = false;
            boolean missedLemma = false;

            if(!compareLemma(refWord, testWord)) {
                hasErr = true;
                missedLemma = true;
                missLemma++;
            }

            if(!comparePOS(refWord, testWord)) {
                hasErr = true;
                missedPOS = true;
                missPos++;
            }

            if(!compareFeats(refWord, testWord)) {
                hasErr = true;
                missedFeats = true;
                missFeats++;
            }

            if(hasErr) {
                miss++;
            }

        }

        if(hasErr) {
            writeResult(ref, test);
        }
    }

    public void writeResult(Sentence ref, Sentence test) throws IOException {
        pw.printf("#%d%n", seqId);
        for(int wordIndex = 0; wordIndex < ref.size(); wordIndex++) {
            Word refWord = ref.word(wordIndex);
            Word testWord = test.word(wordIndex);



            boolean skip = skipEval(refWord);
            StringBuilder err = new StringBuilder();

            err.append(skip || comparePOS(refWord, testWord)? "_" : "*");
            err.append(skip || compareFeats(refWord, testWord)? "_" : "*");
            err.append(skip || compareLemma(refWord, testWord)? "_" : "*");

            MorphInfo refFeats = refWord.asMorphInfo();
            MorphInfo testFeats = testWord.asMorphInfo();


            pw.printf("%d\t%s\t%s\t%s\t%s\t%s\t%s%n",
                    wordIndex + 1,
                    err.toString(),
                    refWord.wf,
                    testWord.lemma,
                    refWord.lemma,
                    testFeats.asString(","),
                    refFeats.asString(",")
            );
        }


        pw.println();
        pw.println();
    }
}
