/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.configuration;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.history.RepositoryInfo;
import static org.junit.Assert.*;

/**
 * Test the RuntimeEnvironment class
 * 
 * @author Trond Norbye
 */
public class RuntimeEnvironmentTest {
    private static File orgiginalConfig;

    public RuntimeEnvironmentTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        // preserve the original
        orgiginalConfig = File.createTempFile("config", ".xml");
        RuntimeEnvironment.getInstance().writeConfiguration(orgiginalConfig);

        // Create a default configuration
        Configuration config = new Configuration();
        RuntimeEnvironment.getInstance().setConfiguration(config);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        // restore the configuration
        RuntimeEnvironment.getInstance().readConfiguration(orgiginalConfig);
        RuntimeEnvironment.getInstance().register();
        orgiginalConfig.delete();
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testDataRoot() throws IOException {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertNull(instance.getDataRootFile());
        assertNull(instance.getDataRootPath());
        File f = File.createTempFile("dataroot", null);
        String path = f.getCanonicalPath();
        assertTrue(f.delete());
        instance.setDataRoot(path);
        assertTrue(f.delete());
        assertEquals(path, instance.getDataRootPath());
        assertEquals(path, instance.getDataRootFile().getCanonicalPath());
    }

    @Test
    public void testSourceRoot() throws IOException {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertNull(instance.getSourceRootFile());
        assertNull(instance.getSourceRootPath());
        File f = File.createTempFile("sourceroot", null);
        String path = f.getCanonicalPath();
        assertTrue(f.delete());
        instance.setSourceRoot(path);
        assertEquals(path, instance.getSourceRootPath());
        assertEquals(path, instance.getSourceRootFile().getCanonicalPath());
    }

    @Test
    public void testProjects() throws IOException {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertFalse(instance.hasProjects());
        assertNotNull(instance.getProjects());
        assertEquals(0, instance.getProjects().size());
        assertNull(instance.getDefaultProject());

        File file = new File("/opengrok_automatic_test/foo/bar");
        instance.setSourceRoot("/opengrok_automatic_test/foo");
        Project p = new Project();
        p.setPath("/bar");
        assertEquals("/bar", p.getId());
        instance.getProjects().add(p);
        assertEquals(p, Project.getProject(file));
        instance.setProjects(null);
        assertNull(instance.getProjects());
    }

    @Test
    public void testRegister() throws InterruptedException, IOException {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        String path = "/tmp/dataroot";
        instance.setDataRoot(path);
        instance.register();
        Thread t = new Thread(new Runnable() {

            public void run() {
                Configuration c = new Configuration();
                RuntimeEnvironment.getInstance().setConfiguration(c);
                
            }
        });
        t.start();
        t.join();
        assertEquals(new File(path).getCanonicalFile().getAbsolutePath(), instance.getDataRootPath());
    }

    @Test
    public void testUrlPrefix() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertEquals("/source/s?", instance.getUrlPrefix());
        String prefix = "/opengrok/s?";
        instance.setUrlPrefix(prefix);
        assertEquals(prefix, instance.getUrlPrefix());
    }

    @Test
    public void testCtags() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertEquals("ctags", instance.getCtags());
        String path = "/usr/bin/ctags";
        instance.setCtags(path);
        assertEquals(path, instance.getCtags());
    }

    @Test
    public void testHistoryReaderTimeLimit() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertEquals(30, instance.getHistoryReaderTimeLimit());
        instance.setHistoryReaderTimeLimit(50);
        assertEquals(50, instance.getHistoryReaderTimeLimit());
    }

    @Test
    public void testUseHistoryCache() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertEquals(true, instance.useHistoryCache());
        instance.setUseHistoryCache(false);
        assertEquals(false, instance.useHistoryCache());
    }

    @Test
    public void testStoreHistoryCacheInDB() {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        assertFalse(env.storeHistoryCacheInDB());
        env.setStoreHistoryCacheInDB(true);
        assertTrue(env.storeHistoryCacheInDB());
    }

    @Test
    public void testGenerateHtml() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertTrue(instance.isGenerateHtml());
        instance.setGenerateHtml(false);
        assertFalse(instance.isGenerateHtml());
    }

    @Test
    public void testCompressXref() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertTrue(instance.isCompressXref());
        instance.setCompressXref(false);
        assertFalse(instance.isCompressXref());
    }

    @Test
    public void testQuickContextScan() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertTrue(instance.isQuickContextScan());
        instance.setQuickContextScan(false);
        assertFalse(instance.isQuickContextScan());
    }

    @Test
    public void testRepositories() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertNotNull(instance.getRepositories());
        instance.setRepositories(null);
        assertNull(instance.getRepositories());
        List<RepositoryInfo> reps = new ArrayList<RepositoryInfo>();
        instance.setRepositories(reps);
        assertSame(reps, instance.getRepositories());
    }

    @Test
    public void testIndexWordLimit() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertEquals(Integer.MAX_VALUE, instance.getIndexWordLimit());  //default is unlimited
        instance.setIndexWordLimit(100000);
        assertEquals(100000, instance.getIndexWordLimit());
    }

    @Test
    public void testVerbose() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertFalse(instance.isVerbose());
        instance.setVerbose(true);
        assertTrue(instance.isVerbose());
    }

    @Test
    public void testAllowLeadingWildcard() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertFalse(instance.isAllowLeadingWildcard());
        instance.setAllowLeadingWildcard(true);
        assertTrue(instance.isAllowLeadingWildcard());
    }

    @Test
    public void testIgnoredNames() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertNotNull(instance.getIgnoredNames());
        instance.setIgnoredNames(null);
        assertNull(instance.getIgnoredNames());
    }

    @Test
    public void testUserPage() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        String page = "http://www.opensolaris.org/viewProfile.jspa?username=";
        assertEquals(page, instance.getUserPage());
        instance.setUserPage(page.substring(5));
        assertEquals(page.substring(5), instance.getUserPage());
    }

    @Test
    public void testBugPage() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        String page = "http://bugs.opensolaris.org/bugdatabase/view_bug.do?bug_id=";
        assertEquals(page, instance.getBugPage());
        instance.setBugPage(page.substring(5));
        assertEquals(page.substring(5), instance.getBugPage());
    }

    @Test
    public void testBugPattern() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        String page = "\\b([12456789][0-9]{6})\\b";
        assertEquals(page, instance.getBugPattern());
        instance.setBugPattern(page.substring(5));
        assertEquals(page.substring(5), instance.getBugPattern());
    }

    @Test
    public void testReviewPage() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        String page = "http://www.opensolaris.org/os/community/arc/caselog/";
        assertEquals(page, instance.getReviewPage());
        instance.setReviewPage(page.substring(5));
        assertEquals(page.substring(5), instance.getReviewPage());
    }

    @Test
    public void testReviewPattern() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        String page = "\\b(\\d{4}/\\d{3})\\b";
        assertEquals(page, instance.getReviewPattern());
        instance.setReviewPattern(page.substring(5));
        assertEquals(page.substring(5), instance.getReviewPattern());
    }

    @Test
    public void testWebappLAF() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertEquals("default", instance.getWebappLAF());
        instance.setWebappLAF("foo");
        assertEquals("foo", instance.getWebappLAF());
    }

    @Test
    public void testRemoteScmSupported() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertFalse(instance.isRemoteScmSupported());
        instance.setRemoteScmSupported(true);
        assertTrue(instance.isRemoteScmSupported());
    }

    @Test
    public void testOptimizeDatabase() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertTrue(instance.isOptimizeDatabase());
        instance.setOptimizeDatabase(false);
        assertFalse(instance.isOptimizeDatabase());
    }

    @Test
    public void testUsingLuceneLocking() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertFalse(instance.isUsingLuceneLocking());
        instance.setUsingLuceneLocking(true);
        assertTrue(instance.isUsingLuceneLocking());
    }

    @Test
    public void testIndexVersionedFilesOnly() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertFalse(instance.isIndexVersionedFilesOnly());
        instance.setIndexVersionedFilesOnly(true);
        assertTrue(instance.isIndexVersionedFilesOnly());
    }

    @Test
    public void testConfigListenerThread() throws IOException {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        SocketAddress addr = new InetSocketAddress(0);
        assertTrue(instance.startConfigurationListenerThread(addr));
        try {
            Thread.sleep(1000);
        } catch (InterruptedException exp) {
            // do nothing
        }
        instance.writeConfiguration();
        instance.stopConfigurationListenerThread();
    }

    @Test
    public void testXMLencdec() throws IOException {
        Configuration c = new Configuration();
        String m = c.getXMLRepresentationAsString();
        Configuration o = Configuration.makeXMLStringAsConfiguration(m);
        assertNotNull(o);
        m = m.replaceAll("a", "m");
        try {
             o = Configuration.makeXMLStringAsConfiguration(m);
             fail("makeXmlStringsAsConfiguration should throw exception");
        } catch (Throwable t) {
        }
    }

    @Test
    public void testBug3095() throws IOException {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        File file = new File("foobar");
        assertTrue(file.createNewFile());
        assertFalse(file.isAbsolute());
        instance.setDataRoot(file.getName());
        File f = instance.getDataRootFile();
        assertNotNull(f);
        assertEquals("foobar", f.getName());
        assertTrue(f.isAbsolute());
        assertTrue(file.delete());
    }
    
    @Test
    public void testBug3154() throws IOException {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        File file = File.createTempFile("dataroot", null);
        assertTrue(file.delete());
        assertFalse(file.exists());
        instance.setDataRoot(file.getAbsolutePath());
        assertTrue(file.exists());
        assertTrue(file.delete());
    }
}
