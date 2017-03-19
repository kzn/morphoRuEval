package name.kazennikov.morphoRuEval;

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Created on 2/26/17.
 *
 * @author Anton Kazennikov
 */
public class MorphInfo {
    public final String pos;
    public final Map<String, String> feats;

    public MorphInfo(String pos, Map<String, String> feats) {
        this.pos = pos;
        this.feats = feats;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        MorphInfo morphInfo = (MorphInfo) o;
        return Objects.equals(pos, morphInfo.pos) &&
                Objects.equals(feats, morphInfo.feats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pos, feats);
    }


    public List<String> asList() {
        ArrayList<String> l = new ArrayList<>();
        l.add(pos);
        l.addAll(feats.values());

        return l;
    }

    @Override
    public String toString() {
        return asList().toString();
    }

    public String asString(String sep) {
        return Joiner.on(sep).join(asList());
    }


}
