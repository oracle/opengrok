package org.opensolaris.opengrok.analysis.php;

import org.opensolaris.opengrok.analysis.JFlexTokenizer;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.TokenStream;
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
        List<String> l = new LinkedList<String>();
        JFlexTokenizer ts = (JFlexTokenizer)
                this.analyzer.overridableTokenStream("refs", null);
        ts.yyreset(new StringReader(s));
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
        String s = "foobar 0sdf _ds˙d";
        String[] termsFor = getTermsFor(s);
        assertArrayEquals(
                new String[] { "foobar", "sdf", "_ds˙d" },
                termsFor);
    }
}
