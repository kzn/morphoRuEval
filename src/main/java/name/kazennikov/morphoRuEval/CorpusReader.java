package name.kazennikov.morphoRuEval;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.io.Files;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created on 2/23/17.
 *
 * @author Anton Kazennikov
 */
public class CorpusReader implements Closeable {

    private static final Interner<String> tags = Interners.newStrongInterner();
    private BufferedReader br;
    private int sentIndex = 1;

    public CorpusReader(File file) throws FileNotFoundException {
        br = Files.newReader(file, Charset.forName("UTF-8"));
    }

    public Sentence next() throws IOException {
        Sentence sent = new Sentence();
        sent.id = sentIndex++;

        int wordIndex = 1;

        while(true) {
            String s = br.readLine();

            if(s == null || s.isEmpty())
                break;

            if(s.charAt(0) == '#')
                continue;

            if(s.startsWith("=="))
                continue;


            String[] parts = s.split("\t");

            if(parts[1].isEmpty())
                continue;

            Word w = new Word();
            w.id = wordIndex++;
            w.wf = parts[1].replace('ё', 'е').replace('Ё', 'е');
            w.wfLC = w.wf.toLowerCase();

            if(parts.length > 2) {
                w.lemma = parts[2];
            }



            if(parts.length > 3) {
                w.pos = tags.intern(parts[3].equals("PROPN")? "NOUN" : parts[3]);
            }

            if(parts.length > 4) {
                if(!Objects.equals(parts[4], "_")) {
                    String[] p = parts[4].split("\\|");

                    for(String fpair : p) {
                        int eqPos = fpair.indexOf('=');
                        String key = tags.intern(fpair.substring(0, eqPos));
                        String value = tags.intern(fpair.substring(eqPos + 1, fpair.length()));
                        w.feats.put(key, value);
                    }
                }
            }


            sent.words.add(w);
        }

        return sent.words.isEmpty()? null : sent;
    }


    @Override
    public void close() throws IOException {
        br.close();
    }

    public static void main(String[] args) throws Exception {
        try(CorpusReader r = new CorpusReader(new File("/storage/data/morphoRuEval-2017/GIKRYA_texts.txt"))) {

            int sentCount = 0;
            int wordCount = 0;

            while(true) {
                Sentence s = r.next();
                if(s == null)
                    break;
                sentCount++;
                wordCount += s.words.size();
            }

            System.out.printf("Sentences: %d, words: %d%n", sentCount, wordCount);


        }
    }

    public static List<Sentence> readAll(File f) throws IOException {
        List<Sentence> sents = new ArrayList<>();

        try(CorpusReader r = new CorpusReader(f)) {
            while(true) {
                Sentence s = r.next();
                if(s == null)
                    break;

                sents.add(s);
            }
        }

        return sents;
    }

    public static class CorpusIter implements Iterator<Sentence>, Closeable {
        CorpusReader r;
        Sentence cur;

        public CorpusIter(File f) throws IOException {
            r = new CorpusReader(f);
            cur = r.next();
        }

        @Override
        public void close() throws IOException {
            r.close();
        }

        @Override
        public boolean hasNext() {
            return cur != null;
        }

        @Override
        public Sentence next() {

            try {
                Sentence prev = cur;
                cur = r.next();
                return prev;
            } catch(IOException e) {
                throw new IOError(e);
            }
        }
    }

    public static Iterable<Sentence> iter(File f) throws IOException {
        return new Iterable<Sentence>() {
            @Override
            public Iterator<Sentence> iterator() {
                try {
                    return new CorpusIter(f);
                } catch(IOException e) {
                    throw new IOError(e);
                }
            }
        };
    }
}
