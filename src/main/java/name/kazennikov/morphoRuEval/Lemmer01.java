package name.kazennikov.morphoRuEval;

import gnu.trove.list.array.TIntArrayList;
import name.kazennikov.Utils;
import name.kazennikov.alphabet.Alphabet;
import name.kazennikov.fsa.BooleanFSABuilder;
import name.kazennikov.fsa.walk.WalkFSABoolean;
import name.kazennikov.trove.TroveUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * Created by kzn on 3/14/17.
 */
public class Lemmer01 {

    public static class Subst {
        public final String postfix;
        public final int endingLength;
        public final String nfEnding;

        public Subst(String postfix, int endingLength, String nfEnding) {
            this.postfix = postfix;
            this.endingLength = endingLength;
            this.nfEnding = nfEnding;
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) return true;
            if(o == null || getClass() != o.getClass()) return false;
            Subst subst = (Subst) o;
            return endingLength == subst.endingLength &&
                    Objects.equals(postfix, subst.postfix) &&
                    Objects.equals(nfEnding, subst.nfEnding);
        }

        @Override
        public int hashCode() {
            return Objects.hash(postfix, endingLength, nfEnding);
        }

        @Override
        public String toString() {
            return String.format("%s|%d%s", postfix, endingLength, nfEnding);
        }
    }

    private WalkFSABoolean endingFSA;

    Alphabet<Subst> substs = new Alphabet<>();
    TIntArrayList substCounts = new TIntArrayList();

    public void compileFromSource(File f) throws IOException {
        BooleanFSABuilder endingsBuilder = new BooleanFSABuilder();
        TIntArrayList s = new TIntArrayList();

        try(CorpusReader r = new CorpusReader(f)) {
            while(true) {
                Sentence sent = r.next();

                if(sent == null)
                    break;

                for(int i = 0; i < sent.size(); i++) {
                    String wf = sent.wf(i).toLowerCase().replace('ё', 'е');
                    String lemma = sent.lemma(i).toLowerCase().replace('ё', 'е');

                    if(wf.length() < 3)
                        continue;

                    int common = Utils.commonPrefix(wf, lemma);
                    String nfEnding = lemma.substring(common);
                    int endingLength = wf.length() - common;

                    String postfix = wf.substring(wf.length() - 3);

                    int substId = substs.get(new Subst(postfix, endingLength, nfEnding));

                    s.resetQuick();
                    TroveUtils.expand(s, postfix);
                    s.reverse();
                    s.add(0);
                    s.add(substId);

                    endingsBuilder.addMinWord(s);

                    while(substCounts.size() - 1 < substId) {
                        substCounts.add(0);
                    }

                    substCounts.set(substId, substCounts.get(substId) + 1);
                }
            }
        }

        endingFSA = endingsBuilder.build();

        System.out.printf("Ending FSA: %d, subst count: %d%n", endingFSA.size(), substs.size());
    }



    public static void main(String[] args) throws Exception {

        Lemmer01 lemmer = new Lemmer01();
        lemmer.compileFromSource(new File("/storage/data/morphoRuEval/fullcorpus.txt"));

        int totword = 0;
        int totmiss = 0;

        TIntArrayList s = new TIntArrayList();

        try(CorpusReader r = new CorpusReader(new File("/storage/data/morphoRuEval/fullcorpus.txt"))) {
            while(true) {
                Sentence sent = r.next();

                if(sent == null)
                    break;

                for(int i = 0; i < sent.size(); i++) {
                    String wf = sent.wf(i).toLowerCase().replace('ё', 'е');
                    String lemma = sent.lemma(i).toLowerCase().replace('ё', 'е');

                    if(wf.length() < 3)
                        continue;

                    totword++;

                    s.resetQuick();
                    TroveUtils.expand(s, wf.substring(wf.length() - 3));
                    s.reverse();



                    TIntArrayList states = lemmer.endingFSA.walk(0, s, 0, s.size());

                    if(states.size() !=s.size() + 1) {
                        totmiss++;
                        continue;
                    }


                    TIntArrayList substIds = lemmer.endingFSA.collectAnnotationsSimple(states.get(states.size() - 1));

                    Subst maxSubst = null;
                    int maxFreq = 0;
                    for(int j = 0; j < substIds.size(); j++) {
                        Subst subst = lemmer.substs.get(substIds.get(j));

                        // sanity check
                        if(subst.endingLength >  wf.length())
                            continue;

                        int freq = lemmer.substCounts.get(substIds.get(j));
                        if(freq > maxFreq) {
                            maxFreq = freq;
                            maxSubst = subst;
                        }
                    }


                    if(maxSubst == null) {
                        totmiss++;
                        continue;
                    }

                    String glemma = wf.substring(0, wf.length() - maxSubst.endingLength) + maxSubst.nfEnding;

                    if(!Objects.equals(glemma, lemma)) {
                        totmiss++;
                    }
                }
            }
        }


        System.out.printf("Errors: %d/%d (%.4f)%n", totmiss, totword, 1.0* totmiss/totword);
    }
}
