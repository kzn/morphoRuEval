package name.kazennikov.morphoRuEval;

import net.didion.jwnl.data.Exc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Created by kzn on 3/17/17.
 */
public class FinalTagger {
    public static void main(String[] args) throws Exception {
        double f = 0.1;
        System.out.println("Building dict..");
        AOTFullDict dict = new AOTFullDict();
        AOT2UD conv = new AOT2UD();
        conv.readMap(new File("modules/morphoRuEval/aot2ud.txt"));

        dict.conv = conv;
        dict.build(new File("modules/morphoRuEval/morphoRuEval.dict.txt"));
        dict.mapSubst(new File("morphoRuEval/full_corpora.txt"));
        dict.write(new File("morphoRuEval.aot_dict.dat"));
        dict.read(new File("morphoRuEval.aot_dict.dat"));
        System.out.println("Dict builded.");

        Lemmer lemmer = new Lemmer(new File("morphoRuEval/gikrya_fixed.txt"), new File("morphoRuEval.dict.dat"));
        lemmer.readAOT(new File("morphoRuEval.aot_dict.dat"));



        Mod2 mod2 = new Mod2(lemmer);

        List<Sentence> train = new ArrayList<>();


        try(CorpusReader r = new CorpusReader(new File("morphoRuEval/gikrya_fixed.txt"))) {
            while(true) {
                Sentence s = r.next();
                if(s == null)
                    break;

                for(int i = 0; i < s.size(); i++) {
                    if(Objects.equals(s.pos(i), "PROPN")) {
                        s.words.get(i).pos = "NOUN";
                    }
                }

                train.add(s);
            }
        }


        boolean trainModel = true;

        if(trainModel) {
            mod2.train(train, 1 << 21);
            mod2.writeModel(new File("morphoRuEval.mod2.final.dat"));
        } else {
            mod2.readModel(new File("morphoRuEval.mod2.final.dat"));
        }

        for(File testFile : new File("morphoRuEval/test").listFiles()) {
            if(!testFile.getName().endsWith(".txt"))
                continue;

            mod2.morpoRuEval(new File(testFile.getParent(), testFile.getName() + ".out"), CorpusReader.iter(testFile));
        }





    }
}
