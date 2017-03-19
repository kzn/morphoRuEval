package name.kazennikov.morphoRuEval;

import com.google.common.io.Files;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created on 3/8/17.
 *
 * @author Anton Kazennikov
 */
public class AOT2UD {

    Map<String, List<String>> featMap = new HashMap<>();

    public void readMap(File f) throws IOException {
        try(BufferedReader br = Files.newReader(f, Charset.forName("UTF-8"))) {
            while(true) {
                String s = br.readLine();
                if(s == null)
                    break;

                if(s.isEmpty() || s.startsWith("#"))
                    continue;
                String[] parts = s.split("\t");

                if(parts.length > 1) {
                    featMap.put(parts[0], Arrays.asList(parts[1].split("\\s*,\\s*")));
                }


            }
        }
    }

    public List<String> conv(String feat) {
        List<String> s = featMap.get(feat);

        return s == null? Arrays.asList(feat) : s;
    }




}
