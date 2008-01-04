package org.opensolaris.opengrok.analysis;

import java.io.ByteArrayInputStream;
import org.junit.Test;
import org.opensolaris.opengrok.analysis.plain.XMLAnalyzer;
import org.opensolaris.opengrok.analysis.sh.ShAnalyzer;
import org.opensolaris.opengrok.analysis.sh.ShAnalyzerFactory;
import static org.junit.Assert.*;

/**
 * Tests for the functionality provided by the AnalyzerGuru class.
 */
public class AnalyzerGuruTest {
    /**
     * Test that we get the correct analyzer if the file name exactly matches a
     * known extension.
     */
    @Test
    public void testFileNameSameAsExtension() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "#!/bin/sh\nexec /usr/bin/zip \"$@\"\n".getBytes("US-ASCII"));
        String file = "/dummy/path/to/source/zip";
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, file);
        assertSame(ShAnalyzer.class, fa.getClass());
    }

    @Test
    public void testUTF8ByteOrderMark() throws Exception {
        byte[] xml = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, // UTF-8 BOM
                       '<', '?', 'x', 'm', 'l', ' ',
                       'v', 'e', 'r', 's', 'i', 'o', 'n', '=',
                       '"', '1', '.', '0', '"', '?', '>' };
        ByteArrayInputStream in = new ByteArrayInputStream(xml);
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame(XMLAnalyzer.class, fa.getClass());
    }

    @Test
    public void addExtension() throws Exception {
        // should not find analyzer for this unlikely extension
        assertNull(AnalyzerGuru.find("file.unlikely_extension"));

        FileAnalyzerFactory
            faf = AnalyzerGuru.findFactory(ShAnalyzerFactory.class.getName());
        // should be the same factory as the built-in analyzer for sh scripts
        assertSame(AnalyzerGuru.find("myscript.sh"), faf);

        // add an analyzer for the extension and see that it is picked up
        AnalyzerGuru.addExtension("UNLIKELY_EXTENSION", faf);
        assertSame(ShAnalyzerFactory.class,
                   AnalyzerGuru.find("file.unlikely_extension").getClass());

        // remove the mapping and verify that it is gone
        AnalyzerGuru.addExtension("UNLIKELY_EXTENSION", null);
        assertNull(AnalyzerGuru.find("file.unlikely_extension"));
    }
}
