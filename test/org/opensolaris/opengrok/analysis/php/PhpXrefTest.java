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
        args = new String[] {"C:\\opengrok\\opengrok-dev\\testdata\\sources\\php\\sample.php"};
        
        System.err.println(
                "<!DOCTYPE html><html><head><meta http-equiv=\"content-type\" content=\"text/html;charset=UTF-8\" /><link rel=\"stylesheet\" type=\"text/css\" "
                + "href=\"http://localhost:8080/source/default/style.css\" /></head>");
        System.err.println("<body><div id=\"src\"><pre>");
        Writer w = new StringWriter();
        PhpAnalyzer.writeXref(
                new InputStreamReader(new FileInputStream(new File(args[0])), "UTF-8"),
                w, null, null, null);
        System.err.print(w.toString());
        System.err.println("</pre></div></body></html>");
    }
}
