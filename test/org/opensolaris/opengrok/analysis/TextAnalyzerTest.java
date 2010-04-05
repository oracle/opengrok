package org.opensolaris.opengrok.analysis;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import junit.framework.Assert;

import org.apache.lucene.document.Document;
import org.junit.Test;

public class TextAnalyzerTest {

    private String defaultEncoding = new InputStreamReader(new ByteArrayInputStream(new byte[0])).getEncoding();
    private String encoding;
    private String contents;

    @Test
    public void defaultEncoding() throws IOException {
        new TestableTextAnalyzer().analyze(new Document(),
                new ByteArrayInputStream("hello".getBytes()));

        Assert.assertEquals(defaultEncoding, encoding);

        Assert.assertEquals("hello", contents);
    }

    @Test
    public void resetsStreamOnShortInput() throws IOException {
        new TestableTextAnalyzer().analyze(new Document(),
                new ByteArrayInputStream("hi".getBytes()));

        Assert.assertEquals(defaultEncoding, encoding);

        Assert.assertEquals("hi", contents);
    }

    @Test
    public void utf8WithBOM() throws IOException {
        byte[] buffer = new byte[]{(byte) 239, (byte) 187, (byte) 191, 'h', 'e', 'l', 'l', 'o'};
        new TestableTextAnalyzer().analyze(new Document(),
                new ByteArrayInputStream(buffer));

        Assert.assertEquals("hello", contents);
        Assert.assertEquals("UTF8", encoding);
    }

    @Test
    public void utf16WithBOM() throws IOException {
        final ByteBuffer utf16str = Charset.forName("UTF-16").encode("hello");
        byte[] bytes = new byte[utf16str.remaining()];
        utf16str.get(bytes, 0, bytes.length);

        new TestableTextAnalyzer().analyze(new Document(),
                new ByteArrayInputStream(bytes));

        Assert.assertEquals("UTF-16", encoding);

        Assert.assertEquals("hello", contents);
    }

    @Test
    public void utf16WithBOMAlternate() throws IOException {
        final ByteBuffer utf16str = Charset.forName("UTF-16").encode("hello");
        byte[] bytes = new byte[utf16str.remaining()];
        utf16str.get(bytes, 0, bytes.length);

        for (int i = 0; i < bytes.length; i += 2) {
            byte b = bytes[i];
            bytes[i] = bytes[i + 1];
            bytes[i + 1] = b;
        }

        new TestableTextAnalyzer().analyze(new Document(),
                new ByteArrayInputStream(bytes));

        Assert.assertEquals("UTF-16", encoding);

        Assert.assertEquals("hello", contents);
    }

    public class TestableTextAnalyzer extends TextAnalyzer {

        public TestableTextAnalyzer() {
            super(null);
        }

        @Override
        protected void analyze(Document doc, Reader r) throws IOException {
            encoding = ((InputStreamReader) r).getEncoding();

            char[] buf = new char[1024];
            int br = r.read(buf);

            contents = new String(buf, 0, br);
        }
    }
}
