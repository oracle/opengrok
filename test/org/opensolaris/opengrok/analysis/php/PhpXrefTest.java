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

    public static void writePhpXref(InputStream is, PrintStream os) throws IOException {
        os.println(
                "<!DOCTYPE html><html><head><meta http-equiv=\"content-type\" content=\"text/html;charset=UTF-8\" /><link rel=\"stylesheet\" type=\"text/css\" "
                + "href=\"http://localhost:8080/source/default/style.css\" /><title>PHP Xref Test</title></head>");
        os.println("<body><div id=\"src\"><pre>");
        Writer w = new StringWriter();
        PhpAnalyzer.writeXref(
                new InputStreamReader(is, "UTF-8"),
                w, null, null, null);
        os.print(w.toString());
        os.println("</pre></div></body></html>");
    }

    public static void main(String args[]) throws IOException {
        if (args.length == 0) {
            args = new String[]{"C:\\opengrok\\opengrok-dev\\test\\org\\"
                    + "opensolaris\\opengrok\\analysis\\php\\sample.php"};
        }

        writePhpXref(new FileInputStream(new File(args[0])), System.err);
    }

    @Test
    public void sampleTest() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
                "org/opensolaris/opengrok/analysis/php/sample.php");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayOutputStream baosExp = new ByteArrayOutputStream();

        try {
            writePhpXref(res, new PrintStream(baos));
        } finally {
            res.close();
        }

        InputStream exp = getClass().getClassLoader().getResourceAsStream(
                "org/opensolaris/opengrok/analysis/php/sampleXrefRes.html");

        try {
            byte buffer[] = new byte[8192];
            int read;
            do {
                read = exp.read(buffer, 0, buffer.length);
                if (read > 0) {
                    baosExp.write(buffer, 0, read);
                }
            } while (read >= 0);
        } finally {
            baosExp.close();
        }

        String gotten[] = new String(baos.toByteArray(), "UTF-8").split("\n");
        String expected[] = new String(baosExp.toByteArray(), "UTF-8").split("\n");

        assertEquals(expected.length, gotten.length);

        for (int i = 0; i < gotten.length; i++) {
            assertEquals(gotten[i].trim(), expected[i].trim());
        }
    }
}
