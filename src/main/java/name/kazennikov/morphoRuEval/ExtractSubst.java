package name.kazennikov.morphoRuEval;

import gnu.trove.map.hash.TObjectIntHashMap;
import name.kazennikov.Utils;
import name.kazennikov.count.IntCounts;

import java.io.File;
import java.io.PrintWriter;
import java.util.Map;

/**
 * Created on 2/26/17.
 *
 * @author Anton Kazennikov
 */
public class ExtractSubst {
    public static void main(String[] args) throws Exception {
        TObjectIntHashMap<Subst> substCounter = new TObjectIntHashMap<>();

        try(CorpusReader r = new CorpusReader(new File("/storage/data/morphoRuEval/GIKRYA_texts.txt"))) {
            while(true) {
                Sentence s = r.next();
                if(s == null)
                    break;

                for(int i = 0; i < s.size(); i++) {
                    String wf = s.wf(i).toLowerCase();
                    String lemma = s.lemma(i).toLowerCase();
                    String pos = s.pos(i);
                    Map<String, String> feats = s.feats(i);

                    int length = Utils.commonPrefix(wf, lemma);

                    String ending = wf.substring(length);
                    String nfEnding = lemma.substring(length);

                    Subst subst = new Subst(ending, nfEnding, pos, feats);
                    substCounter.adjustOrPutValue(subst, 1, 1);

                }
            }
        }

        IntCounts<Subst> intCounts = new IntCounts<>(substCounter);
        intCounts.inplaceSort(intCounts.DESC);
        try(PrintWriter pw = new PrintWriter("morphoRuEval.subst.txt")) {
            for(int i = 0; i < intCounts.size(); i++) {
                Subst s = intCounts.getObject(i);
                pw.printf("%d\t%s\t%s\t%s\t",
                        intCounts.getCount(i),
                        s.ending,
                        s.nfEnding,
                        s.pos
                        );

                int j = 0;
                for(Map.Entry<String, String> e : s.feats.entrySet()) {
                    if(j != 0) {
                        pw.print("|");
                    }
                    pw.printf("%s=%s", e.getKey(), e.getValue());
                    j++;

                }

                pw.println();
            }
        }
    }
}
