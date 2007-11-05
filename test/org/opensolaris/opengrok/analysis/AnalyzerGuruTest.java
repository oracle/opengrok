package org.opensolaris.opengrok.analysis;

import java.io.ByteArrayInputStream;
import org.junit.Test;
import org.opensolaris.opengrok.analysis.sh.ShAnalyzer;
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
        assertEquals(ShAnalyzer.class, fa.getClass());
    }
}
