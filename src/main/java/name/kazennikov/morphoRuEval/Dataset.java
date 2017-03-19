package name.kazennikov.morphoRuEval;

import gnu.trove.list.array.TIntArrayList;
import name.kazennikov.ml.core.Instance;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kzn on 2/28/17.
 */
public class Dataset {
    TIntArrayList labels;
    List<Instance> samples;

    public Dataset() {
        this(new TIntArrayList(), new ArrayList<>());
    }

    public Dataset(TIntArrayList labels, List<Instance> samples) {
        this.labels = labels;
        this.samples = samples;
    }


    public int label(int sampleIndex) {
        return labels.get(sampleIndex);
    }


    public Instance sample(int sampleIndex) {
        return samples.get(sampleIndex);
    }


    public int size() {
        return samples.size();
    }

}
