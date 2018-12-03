package org.opengrok.indexer.index;

import javax.ws.rs.ProcessingException;
import org.junit.Test;

public class IndexerUtilTest {
    @Test(expected = ProcessingException.class)
    public void testEnableProjectsInvalidUrl() {
        IndexerUtil.enableProjects("http://non-existent.server.com:123");
    }
}
