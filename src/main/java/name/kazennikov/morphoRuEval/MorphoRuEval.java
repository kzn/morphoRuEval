package name.kazennikov.morphoRuEval;

import net.didion.jwnl.data.Exc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created on 3/8/17.
 *
 * @author Anton Kazennikov
 */
public class MorphoRuEval {
    public static void main(String[] args) throws Exception {
        Mod2 mod2 = new Mod2();


        List<Sentence> train = CorpusReader.readAll(new File("morphoRuEval/Baseline/source/gikrya_train.txt"));
        List<Sentence> test = CorpusReader.readAll(new File("morphoRuEval/Baseline/source/gikrya_test.txt"));
        List<Sentence> testSyntagrus = CorpusReader.readAll(new File("morphoRuEval/Baseline/source/syntagrus_test.txt"));

        boolean trainModel = true;

        if(trainModel) {
            mod2.train(train, 1 << 21);
            mod2.writeModel(new File("morphoRuEval.gikrya.mod2.dat"));
        }

        mod2.readModel(new File("morphoRuEval.gikrya.mod2.dat"));


        mod2.morpoRuEval(new File("morphoRuEval/Baseline/gikrya_test.mod2.txt"), test);
        mod2.morpoRuEval(new File("morphoRuEval/Baseline/syntagrus_test.mod2.txt"), testSyntagrus);


//
        mod2.evalNG("morphoRuEval/Baseline/kzn.gikrya_mod2", test);
        mod2.evalNG("morphoRuEval/Baseline/kzn.syntagrus_mod2", testSyntagrus);
//        mod2.evalNG("morphoRuEval.mod2.train", train);







    }
}
