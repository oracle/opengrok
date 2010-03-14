package org.opensolaris.opengrok.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import org.apache.lucene.document.Document;

public abstract class TextAnalyzer extends FileAnalyzer {
	public TextAnalyzer(FileAnalyzerFactory factory) {
		super(factory);
	}

    public final void analyze(Document doc, InputStream in) throws IOException {
    	String charset = null;
    	
    	in.mark(3);
    	
    	byte[] head = new byte[3];
    	int br = in.read(head, 0, 3);

    	if (br >= 2) {
    		if ((head[0] == (byte)0xFE && head[1] == (byte)0xFF) || (head[0] == (byte)0xFF && (byte)head[1] == (byte)0xFE)) {
    			charset = "UTF16";
    			in.reset();	
    		}
    	}
    	if (br >= 3) {
    		if (head[0] == (byte)0xEF && head[1] == (byte)0xBB && head[2] == (byte)0xBF) {
    			/* InputStreamReader does not properly discard BOM on UTF8 streams,
    			 * so don't reset the stream. */ 
    			charset = "UTF8";
    		}
    	}
    	
    	if (charset == null) {
    		in.reset();
    		charset = Charset.defaultCharset().name();
    	}
    	
        analyze(doc, new InputStreamReader(in, charset));
    }
    
    protected abstract void analyze(Document doc, Reader reader) throws IOException;
}
