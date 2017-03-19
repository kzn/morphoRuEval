package name.kazennikov.morphoRuEval;

import gate.creole.morph.Morph;

import java.util.*;

/**
 * Created on 3/4/17.
 *
 * @author Anton Kazennikov
 */
public interface MorphoComparator {



    public boolean comparePOS(Word refWord, Word testWord);


    public boolean compareLemma(Word ref, Word test);


    public boolean compareFeats(Word ref, Word test);


    public boolean skipEval(Word refWord);

    public static final MorphoComparator POS_ONLY = new POSOnly();
    public static final MorphoComparator FULL_SIMPLE = new FullSimple();
    public static final MorphoComparator FINAL = new Final();
    public static final MorphoComparator ROBUST = new Robust();



    public static class POSOnly implements MorphoComparator {

        protected static final Set<String> skip = new HashSet<>();

        public POSOnly() {
            skip.add("PUNCT");
            skip.add("H");
        }

        @Override
        public boolean comparePOS(Word refWord, Word testWord) {
            return Objects.equals(refWord.pos, testWord.pos);
        }

        @Override
        public boolean compareLemma(Word ref, Word test) {
            return true;
        }

        @Override
        public boolean compareFeats(Word ref, Word test) {
            return true;
        }

        @Override
        public boolean skipEval(Word refWord) {
            return skip.contains(refWord.pos);
        }
    }

    public static class FullSimple implements MorphoComparator {

        @Override
        public boolean comparePOS(Word refWord, Word testWord) {
            return Objects.equals(refWord.pos, testWord.pos);
        }

        @Override
        public boolean compareLemma(Word ref, Word test) {
            String rlemma = ref.lemma.toLowerCase().replace('ё', 'е');
            String tlemma = test.lemma.toLowerCase().replace('ё', 'е');
            return Objects.equals(rlemma, tlemma);
        }

        @Override
        public boolean compareFeats(Word ref, Word test) {
            return Objects.equals(ref.feats, test.feats);
        }

        @Override
        public boolean skipEval(Word refWord) {
            return false;
        }
    }

    public static class Final implements MorphoComparator {

        protected static final Set<String> skip = new HashSet<>(Arrays.asList("PUNCT", "H", "ADP", "CONJ", "PART", "PRON", "SYM", "X"));
        protected static final Set<String> skipADV = new HashSet<>(Arrays.asList("как", "пока", "так", "когда"));


        @Override
        public boolean comparePOS(Word refWord, Word testWord) {
            return Objects.equals(refWord.pos, testWord.pos);
        }

        @Override
        public boolean compareLemma(Word ref, Word test) {
            String rlemma = ref.lemma.toLowerCase().replace('ё', 'е');
            String tlemma = test.lemma.toLowerCase().replace('ё', 'е');
            return Objects.equals(rlemma, tlemma);
        }

        @Override
        public boolean compareFeats(Word ref, Word test) {
            Map<String, String> rf = new HashMap<>(ref.feats);
            Map<String, String> tf = new HashMap<>(test.feats);
            rf.remove(UDConst.ANIMACY);
            tf.remove(UDConst.ANIMACY);
            return Objects.equals(rf, tf);
        }

        @Override
        public boolean skipEval(Word refWord) {
            return skip.contains(refWord.pos) ||
                    (Objects.equals(refWord.pos, "ADV") &&  skipADV.contains(refWord.lemma));
        }
    }

    public static class Robust implements MorphoComparator {

        protected static final Set<String> skip = new HashSet<>(Arrays.asList("PUNCT", "H", "ADP", "CONJ", "PART", "PRON", "SYM", "X", "NUM"));
        protected static final Set<String> skipADV = new HashSet<>(Arrays.asList("как", "пока", "так", "когда"));


        @Override
        public boolean comparePOS(Word refWord, Word testWord) {
            return Objects.equals(refWord.pos, testWord.pos);
        }

        @Override
        public boolean compareLemma(Word ref, Word test) {
            String rlemma = ref.lemma.toLowerCase().replace('ё', 'е');
            String tlemma = test.lemma.toLowerCase().replace('ё', 'е');
            return Objects.equals(rlemma, tlemma);
        }

        @Override
        public boolean compareFeats(Word ref, Word test) {
            Map<String, String> rf = new HashMap<>(ref.feats);
            Map<String, String> tf = new HashMap<>(test.feats);
            rf.keySet().retainAll(Arrays.asList(UDConst.NUMBER, UDConst.GENDER, UDConst.CASE, UDConst.PERSON));
            tf.keySet().retainAll(Arrays.asList(UDConst.NUMBER, UDConst.GENDER, UDConst.CASE, UDConst.PERSON));
            return Objects.equals(rf, tf);
        }

        @Override
        public boolean skipEval(Word refWord) {
            return skip.contains(refWord.pos) ||
                    (Objects.equals(refWord.pos, "ADV") &&  skipADV.contains(refWord.lemma));
        }
    }
}
