package name.kazennikov.morphoRuEval;

import java.io.IOException;

/**
 * Created on 3/4/17.
 *
 * @author Anton Kazennikov
 */
public class ComparatorEvaluator extends Evaluator {

    MorphoComparator c;

    public ComparatorEvaluator(String prefix, MorphoComparator c) throws IOException {
        super(prefix);
        this.c = c;
    }

    @Override
    public boolean skipEval(Word refWord) {
        return c.skipEval(refWord);
    }

    @Override
    public boolean comparePOS(Word refWord, Word testWord) {
        return c.comparePOS(refWord, testWord);
    }

    @Override
    public boolean compareLemma(Word ref, Word test) {
        return c.compareLemma(ref, test);
    }

    @Override
    public boolean compareFeats(Word ref, Word test) {
        return c.compareFeats(ref, test);
    }
}
