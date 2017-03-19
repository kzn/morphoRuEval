package name.kazennikov.morphoRuEval;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by kzn on 3/1/17.
 */
public class ExtractTags {
    public static void main(String[] args) throws Exception {
        Map<String, Set<String>> tags = new HashMap<>();

        try(CorpusReader r = new CorpusReader(new File("/storage/data/morphoRuEval/full_corpora.txt"))) {
            while(true) {
                Sentence s = r.next();
                if(s == null)
                    break;

                for(int i = 0; i < s.size(); i++) {
                    Word w = s.word(i);

                    if(w.wf.isEmpty()) {
                        w = w;
                    }
                    Set<String> set = tags.get("POS");
                    if(set == null) {
                        set = new HashSet<>();
                        tags.put("POS", set);
                    }
                    set.add(w.pos);


                    for(Map.Entry<String, String> e : w.feats.entrySet()) {
                        Set<String> sset = tags.get(e.getKey());
                        if(sset == null) {
                            sset = new HashSet<>();
                            tags.put(e.getKey(), sset);
                        }

                        sset.add(e.getValue());
                    }
                }
            }
        }

        Set<String> unique = new HashSet<>();

        System.out.println("Tags:");
        for(Map.Entry<String, Set<String>> e : tags.entrySet()) {
            System.out.printf("%s: %s%n", e.getKey(), e.getValue());

            for(String t : e.getValue()) {
                if(!unique.add(t)) {
                    System.err.println("Duplicate: " + t);
                }
            }
        }

        System.out.printf("Total tags: %d%n", unique.size());

    }
}
