package name.kazennikov.morphoRuEval;

import name.kazennikov.Utils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created on 2/23/17.
 *
 * @author Anton Kazennikov
 */
public class Sentence {
    int id;

    List<Word> words = new ArrayList<>();

    public Word word(int index) {
        return index >= 0 && index < words.size()? words.get(index) : Word.FILLER;
    }

    public String wf(int index) {
        Word w = word(index);

        return w != null? w.wf : null;
    }

    public String wfLC(int index) {
        Word w = word(index);

        return w != null? w.wfLC : null;
    }

    public String lemma(int index) {
        Word w = word(index);

        return w != null? w.lemma : null;
    }

    public String pos(int index) {
        Word w = word(index);
        return w != null? w.pos : null;
    }

    public String suff(int index, int length) {
        String wf = wfLC(index);
        if(wf == null)
            return null;

        length = Math.min(length, wf.length());
        return wf.substring(wf.length() - length, wf.length());
    }

    public String pref(int index, int length) {
        String wf = wfLC(index);
        if(wf == null)
            return null;

        length = Math.min(length, wf.length());
        return wf.substring(0, length);
    }


    public int size() {
        return words.size();
    }

    public Map<String,String> feats(int index) {
        Word w = word(index);
        return w.feats;
    }

    public Sentence reverse() {
        List<Word> words = new ArrayList<>(this.words);
        Collections.reverse(words);
        Sentence sent = new Sentence();
        sent.words = words;
        sent.id = this.id;
        return sent;
    }

    public String feat(int index, String name) {
        return word(index).feats.get(name);
    }

    public String ending(int wordIndex) {
        Word w = word(wordIndex);

        int common = Utils.commonPrefix(w.wfLC, w.lemma.toLowerCase().replace('ё', 'е'));
        return w.wfLC.substring(common);
    }

    public String stem(int wordIndex) {
        Word w = word(wordIndex);
        int common = Utils.commonPrefix(w.wfLC, w.lemma.toLowerCase().replace('ё', 'е'));
        return w.wfLC.substring(0, common);
    }

    public void writeEval(PrintWriter pw) {
        for(int i = 0; i < size(); i++) {

            StringBuilder featSB = new StringBuilder();

            for(Map.Entry<String, String> e : feats(i).entrySet()) {
                if(featSB.length() != 0) {
                    featSB.append("|");
                }

                featSB.append(e.getKey());
                featSB.append("=");
                featSB.append(e.getValue());

            }

            if(featSB.length() == 0) {
                featSB.append("_");
            }

            pw.printf("%d\t%s\t%s\t%s\t%s%n",
                    i + 1,
                    wf(i),
                    lemma(i),
                    pos(i),
                    featSB.toString()
            );
        }

        pw.println();
    }
}
