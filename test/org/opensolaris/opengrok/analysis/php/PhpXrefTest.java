package org.opensolaris.opengrok.analysis.php;
import java.io.*;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Tests the {@link PhpXref} class.
 * @author Gustavo Lopes
 */
public class PhpXrefTest {
    @Test
    public void basicTest() throws IOException {
        String s = "foo bar";
        Writer w = new StringWriter();
        PhpAnalyzer.writeXref(new StringReader(s), w, null, null, null);
        assertEquals(
                "<a class=\"l\" name=\"1\" href=\"#1\">1</a><a href=\"/"
                + "source/s?defs=foo\">foo</a> <a href=\"/source/s?defs=bar\">bar</a>",
                w.toString());
    }

    public static void main(String args[]) throws IOException {
        Writer w = new StringWriter();
        PhpAnalyzer.writeXref(
                new InputStreamReader(new FileInputStream(new File(args[0])), "UTF-8"),
                w, null, null, null);
        System.err.print(w.toString());
    }
}
