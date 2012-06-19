package org.opensolaris.opengrok.analysis.php;

import java.io.Reader;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Test;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import static org.junit.Assert.*;

/**
 * Tests the {@link PhpSymbolTokenizer} class.
 * @author Gustavo Lopes
 */
public class PhpSymbolTokenizerTest {

    private FileAnalyzer analyzer;

    public PhpSymbolTokenizerTest() {
        PhpAnalyzerFactory analFact = new PhpAnalyzerFactory();
        this.analyzer = analFact.getAnalyzer();
    }

    private String[] getTermsFor(String s) {
        return getTermsFor(new StringReader(s));
    }

    private String[] getTermsFor(Reader r) {
        List<String> l = new LinkedList<String>();
        JFlexTokenizer ts = (JFlexTokenizer)
                this.analyzer.overridableTokenStream("refs", null);
        ts.yyreset(r);
        CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
        try {
            while (ts.yylex()) {
                l.add(term.toString());
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return l.toArray(new String[l.size()]);
    }

    @Test
    public void basicTest() {
        String s = "<?php foobar eval $eval 0sdf _ds˙d";
        String[] termsFor = getTermsFor(s);
        assertArrayEquals(
                new String[]{"foobar", "eval", "sdf", "_ds˙d"},
                termsFor);
    }

    @Test
    public void sampleTest() throws UnsupportedEncodingException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
                "org/opensolaris/opengrok/analysis/php/sample.php");
        InputStreamReader r = new InputStreamReader(res, "UTF-8");
        String[] termsFor = getTermsFor(r);
        System.out.println(Arrays.toString(termsFor));
        assertArrayEquals(
                new String[]{
                    "a", //line 3
                    "foo", "bar", //line 5
                    "g", "a", "c", //line 6
                    "b", "c", "a", "a", //line 7
                    "doo", //line 9
                    "a", //line 10
                    "foo", "bar", //line 12
                    "name", //line 13
                    "foo", "bar", //line 14
                    "foo", //line 15
                    "ff", //line 20
                    "foo", //line 21
                    "FooException", //line 28
                    "Foo", "Bar", //line 29
                    "Foo", "Foo", "param", //line 30
                    "gata", //line 36
                    "gata", //line 37
                    "foo", "_SERVER", "_SERVER", "_SERVER", //line 38
                    "foo", "bar", "foo", "bar", "foo", "a", //line 39
                },
                termsFor);
    }
}
