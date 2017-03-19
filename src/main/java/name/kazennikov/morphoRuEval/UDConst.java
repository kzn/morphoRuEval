package name.kazennikov.morphoRuEval;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by kzn on 3/1/17.
 */
public class UDConst {
    public static final String TENSE = "Tense";
    public static final String VARIANT = "Variant";
    public static final String ANIMACY = "Animacy";
    public static final String MOOD = "Mood";
    public static final String NUMBER = "Number";
    public static final String DEGREE = "Degree";
    public static final String FORM = "Form";
    public static final String VERB_FORM = "VerbForm";
    public static final String GENDER = "Gender";
    public static final String VOICE = "Voice";
    public static final String PERSON = "Person";
    public static final String CASE = "Case";

    public static final List<String> ALL_GROUPS = Arrays.asList(CASE, PERSON, VOICE, GENDER, VERB_FORM, FORM, DEGREE, NUMBER, MOOD, ANIMACY, VARIANT, TENSE);
    public final static Set<String> CLOSED_POS = new HashSet<>(Arrays.asList("PUNCT", "H", "ADP", "CONJ", "DET", "PART", "PRON"));
}
