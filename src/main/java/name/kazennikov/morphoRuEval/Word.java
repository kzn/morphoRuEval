package name.kazennikov.morphoRuEval;

import com.google.common.base.MoreObjects;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created on 2/23/17.
 *
 * @author Anton Kazennikov
 */
public class Word {

    public static final Word FILLER = new Word(-1, "", "", "", new HashMap<>());

    int id;
    public String wf;
    public String wfLC;
    public String lemma;
    public String pos;
    public Map<String, String> feats = new HashMap<>();

    public Word() {

    }

    public Word(int id, String wf, String lemma, String pos, Map<String, String> feats) {
        this.id = id;
        this.wf = wf;
        this.lemma = lemma;
        this.pos = pos;
        this.feats = feats;
        this.wfLC = wf.toLowerCase().replace('ё', 'е');;
    }


    public MorphInfo asMorphInfo() {
        return new MorphInfo(pos, feats);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("wf", wf)
                .add("lemma", lemma)
                .add("pos", pos)
                .add("feats", feats)
                .toString();
    }
}
