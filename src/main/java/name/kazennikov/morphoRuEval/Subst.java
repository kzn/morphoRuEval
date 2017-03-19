package name.kazennikov.morphoRuEval;

import com.google.common.base.MoreObjects;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * Created on 2/26/17.
 *
 * @author Anton Kazennikov
 */
public class Subst implements Serializable {
    public final String ending;
    public final String nfEnding;
    public final String pos;
    public final Map<String, String> feats;

    public Subst(String ending, String nfEnding, String pos, Map<String, String> feats) {
        this.ending = ending;
        this.nfEnding = nfEnding;
        this.pos = pos;
        this.feats = feats;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("end", ending)
                .add("nfEnd", nfEnding)
                .add("pos", pos)
                .add("feats", feats)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        Subst subst = (Subst) o;
        return Objects.equals(ending, subst.ending) &&
                Objects.equals(nfEnding, subst.nfEnding) &&
                Objects.equals(pos, subst.pos) &&
                Objects.equals(feats, subst.feats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ending, nfEnding, pos, feats);
    }

    public MorphInfo asMorphInfo() {
        return new MorphInfo(pos, feats);
    }
}
