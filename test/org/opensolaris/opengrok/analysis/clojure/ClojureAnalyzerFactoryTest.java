package org.opensolaris.opengrok.analysis.clojure;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.RAMDirectory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.analysis.Ctags;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.Scopes;
import org.opensolaris.opengrok.analysis.StreamSource;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.opensolaris.opengrok.util.TestRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.opensolaris.opengrok.analysis.AnalyzerGuru.string_ft_nstored_nanalyzed_norms;

/**
 * @author Farid Zakaria
 */
public class ClojureAnalyzerFactoryTest {

    FileAnalyzer analyzer;
    private final String ctagsProperty = "org.opensolaris.opengrok.analysis.Ctags";
    private static Ctags ctags;
    private static TestRepository repository;

    public ClojureAnalyzerFactoryTest() {
        ClojureAnalyzerFactory analFact = new ClojureAnalyzerFactory();
        this.analyzer = analFact.getAnalyzer();
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setCtags(System.getProperty(ctagsProperty, "ctags"));
        if (env.validateExuberantCtags()) {
            this.analyzer.setCtags(new Ctags());
        }
    }

    private static StreamSource getStreamSource(final String fname) {
        return new StreamSource() {
            @Override
            public InputStream getStream() throws IOException {
                return new FileInputStream(fname);
            }
        };
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        ctags = new Ctags();
        ctags.setBinary(RuntimeEnvironment.getInstance().getCtags());

        repository = new TestRepository();
        repository.create(ClojureAnalyzerFactoryTest.class.getResourceAsStream(
                "/org/opensolaris/opengrok/index/source.zip"));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        ctags.close();
        ctags = null;
    }

    /**
     * Test of writeXref method, of class CAnalyzerFactory.
     *
     * @throws java.lang.Exception
     */
    @Test
    public void testScopeAnalyzer() throws Exception {
        String path = repository.getSourceRoot() + "/clojure/ants.clj";
        File f = new File(path);
        if (!(f.canRead() && f.isFile())) {
            fail("clojure testfile " + f + " not found");
        }

        Document doc = new Document();
        doc.add(new Field(QueryBuilder.FULLPATH, path,
                          string_ft_nstored_nanalyzed_norms));
        StringWriter xrefOut = new StringWriter();
        analyzer.setCtags(ctags);
        analyzer.analyze(doc, getStreamSource(path), xrefOut);

        Definitions definitions = Definitions.deserialize(doc.getField(QueryBuilder.TAGS).binaryValue().bytes);
        // Construct a RAMDirectory to hold the in-memory representation
        // of the index.
        RAMDirectory inMemoryIndex = new RAMDirectory();
        IndexWriter writer = new IndexWriter(inMemoryIndex, new IndexWriterConfig(analyzer));
        writer.addDocument(doc);
        writer.close();

        // Build an IndexSearcher using the in-memory index
        IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(inMemoryIndex));

        IndexableField defsField = doc.getField(QueryBuilder.DEFS);
        assertNotNull(defsField);

        int intValueOfChar;
        String targetString = "";
        while ((intValueOfChar = defsField.readerValue().read()) != -1) {
            targetString += (char) intValueOfChar;
        }
        defsField.readerValue().close();

        Definitions scopes = Definitions.deserialize(defsField.binaryValue().bytes);

        /*
        Scopes.Scope globalScope = scopes.getScope(-1);
        assertEquals(5, scopes.size()); // foo, bar, main

        for (int i=0; i<74; ++i) {
            if (i >= 29 && i <= 31) {
                assertEquals("Sample", scopes.getScope(i).getName());
                assertEquals("class:Sample", scopes.getScope(i).getScope());
            } else if (i >= 33 && i <= 41) {
                assertEquals("Method", scopes.getScope(i).getName());
                assertEquals("class:Sample", scopes.getScope(i).getScope());
            } else if (i == 43) {
                assertEquals("AbstractMethod", scopes.getScope(i).getName());
                assertEquals("class:Sample", scopes.getScope(i).getScope());
            } else if (i >= 47 && i <= 56) {
                assertEquals("InnerMethod", scopes.getScope(i).getName());
                assertEquals("class:Sample.InnerClass", scopes.getScope(i).getScope());
            } else if (i >= 60 && i <= 72) {
                assertEquals("main", scopes.getScope(i).getName());
                assertEquals("class:Sample", scopes.getScope(i).getScope());
            } else {
                assertEquals(scopes.getScope(i), globalScope);
                assertNull(scopes.getScope(i).getScope());
            }
        }
        */
    }


}
