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
 * Copyright (c) 2008, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengrok.indexer.analysis.JFlexXref;
import org.opengrok.indexer.analysis.plain.PlainXref;
import org.opengrok.indexer.authorization.AuthorizationPlugin;
import org.opengrok.indexer.authorization.AuthorizationStack;
import org.opengrok.indexer.history.RepositoryInfo;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.util.IOUtils;


/**
 * Test the RuntimeEnvironment class.
 *
 * @author Trond Norbye
 */
public class RuntimeEnvironmentTest {

    private static File originalConfig;

    public RuntimeEnvironmentTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        // preserve the original
        originalConfig = File.createTempFile("config", ".xml");
        RuntimeEnvironment.getInstance().writeConfiguration(originalConfig);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        // restore the configuration
        RuntimeEnvironment.getInstance().readConfiguration(originalConfig);
        originalConfig.delete();
    }

    @Before
    public void setUp() {
        // Create a default configuration
        Configuration config = new Configuration();
        RuntimeEnvironment.getInstance().setConfiguration(config);
    }

    @Test
    public void testDataRoot() throws IOException {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertNull(instance.getDataRootFile());
        assertNull(instance.getDataRootPath());
        File f = File.createTempFile("dataroot", null);
        String path = f.getCanonicalPath();
        assertTrue(f.delete());
        assertFalse(f.exists());
        instance.setDataRoot(path);
        // setDataRoot() used to create path if it didn't exist, but that
        // logic has been moved. Verify that it is so.
        assertFalse(f.exists());
        assertTrue(f.mkdirs());
        assertEquals(path, instance.getDataRootPath());
        assertEquals(path, instance.getDataRootFile().getCanonicalPath());
    }

    @Test
    public void testIncludeRoot() throws IOException {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertNull(instance.getIncludeRootPath());
        
        // set data root
        File f = File.createTempFile("dataroot", null);
        String path = f.getCanonicalPath();
        instance.setDataRoot(path);
        
        // verify they are the same
        assertEquals(instance.getDataRootPath(), instance.getIncludeRootPath());
        
        // set include root
        f = File.createTempFile("includeroot", null);
        path = f.getCanonicalPath();
        instance.setIncludeRoot(path);
        assertEquals(path, instance.getIncludeRootPath());
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
        instance.setProjectsEnabled(true);
        assertFalse(instance.hasProjects());
        assertNotNull(instance.getProjects());
        assertEquals(0, instance.getProjects().size());
        assertNull(instance.getDefaultProjects());

        File file = new File("/opengrok_automatic_test/foo/bar");
        File folder = new File("/opengrok_automatic_test/foo");
        instance.setSourceRoot(folder.getCanonicalPath());
        Project p = new Project("bar");
        p.setPath("/bar");
        assertEquals("/bar", p.getId());
        instance.getProjects().put(p.getName(), p);
        assertEquals(p, Project.getProject(file));
        instance.setProjects(null);
        assertNull(instance.getProjects());
    }

    @Test
    public void testGroups() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertFalse(instance.hasGroups());
        assertNotNull(instance.getGroups());
        assertEquals(0, instance.getGroups().size());

        Group g = new Group("Random", "xyz.*");

        instance.getGroups().add(g);
        assertEquals(1, instance.getGroups().size());
        assertEquals(g, instance.getGroups().iterator().next());
        assertEquals("Random", instance.getGroups().iterator().next().getName());

        instance.setGroups(null);
        assertNull(instance.getGroups());
    }

    @Test
    public void testPerThreadConsistency() throws InterruptedException {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        String path = "/tmp/dataroot1";
        instance.setDataRoot(path);
        Thread t = new Thread(() -> {
            Configuration c = new Configuration();
            c.setDataRoot("/tmp/dataroot2");
            RuntimeEnvironment.getInstance().setConfiguration(c);
        });
        t.start();
        t.join();
        assertEquals("/tmp/dataroot2", instance.getDataRootPath());
    }

    @Test
    public void testUrlPrefix() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertEquals("/source/s?", instance.getUrlPrefix());
    }

    @Test
    public void testCtags() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        String instanceCtags = instance.getCtags();
        assertNotNull(instanceCtags);
        assertTrue("instance ctags should equals 'ctags' or the sys property",
            instanceCtags.equals("ctags") || instanceCtags.equals(
            System.getProperty("org.opengrok.indexer.analysis.Ctags")));
        String path = "/usr/bin/ctags";
        instance.setCtags(path);
        assertEquals(path, instance.getCtags());

        instance.setCtags(null);
        instanceCtags = instance.getCtags();
        assertTrue("instance ctags should equals 'ctags' or the sys property",
            instanceCtags.equals("ctags") || instanceCtags.equals(
            System.getProperty("org.opengrok.indexer.analysis.Ctags")));
    }

    @Test
    public void testHistoryReaderTimeLimit() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertEquals(30, instance.getHistoryReaderTimeLimit());
        instance.setHistoryReaderTimeLimit(50);
        assertEquals(50, instance.getHistoryReaderTimeLimit());
    }

    @Test
    public void testFetchHistoryWhenNotInCache() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertTrue(instance.isFetchHistoryWhenNotInCache());
        instance.setFetchHistoryWhenNotInCache(false);
        assertFalse(instance.isFetchHistoryWhenNotInCache());
    }

    @Test
    public void testUseHistoryCache() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertTrue(instance.useHistoryCache());
        instance.setUseHistoryCache(false);
        assertFalse(instance.useHistoryCache());
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
        instance.removeRepositories();
        assertNull(instance.getRepositories());
        List<RepositoryInfo> reps = new ArrayList<>();
        instance.setRepositories(reps);
        assertSame(reps, instance.getRepositories());
    }

    @Test
    public void testRamBufferSize() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertEquals(16, instance.getRamBufferSize(), 0);  //default is 16
        instance.setRamBufferSize(256);
        assertEquals(256, instance.getRamBufferSize(), 0);
    }

    @Test
    public void testAllowLeadingWildcard() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertTrue(instance.isAllowLeadingWildcard());
        instance.setAllowLeadingWildcard(false);
        assertFalse(instance.isAllowLeadingWildcard());
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
        String page = "http://www.myserver.org/viewProfile.jspa?username=";
        assertNull(instance.getUserPage());   // default value is null
        instance.setUserPage(page);
        assertEquals(page, instance.getUserPage());
    }

    @Test
    public void testBugPage() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        String page = "http://bugs.myserver.org/bugdatabase/view_bug.do?bug_id=";
        assertNull(instance.getBugPage());   // default value is null
        instance.setBugPage(page);
        assertEquals(page, instance.getBugPage());
    }

    @Test
    public void testBugPattern() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        String[] tests = new String[]{
            "\\b([12456789][0-9]{6})\\b",
            "\\b(#\\d+)\\b",
            "(BUG123)",
            "\\sbug=(\\d+[a-t])*(\\W*)"
        };
        for (String test : tests) {
            try {
                instance.setBugPattern(test);
                assertEquals(test, instance.getBugPattern());
            } catch (IllegalArgumentException ex) {
                fail("The pattern '" + test + "' should not throw an exception");
            }
        }
    }

    @Test
    public void testInvalidBugPattern() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        String[] tests = new String[]{
            "\\b([",
            "\\b({,6})\\b",
            "\\b6)\\b",
            "*buggy",
            "BUG123", // does not contain a group
            "\\b[a-z]+\\b" // does not contain a group
        };
        for (String test : tests) {
            try {
                instance.setBugPattern(test);
                fail("The pattern '" + test + "' should throw an exception");
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Test
    public void testReviewPage() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        String page = "http://arc.myserver.org/caselog/PSARC/";
        assertNull(instance.getReviewPage());   // default value is null
        instance.setReviewPage(page);
        assertEquals(page, instance.getReviewPage());
    }

    @Test
    public void testReviewPattern() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        String[] tests = new String[]{
            "\\b(\\d{4}/\\d{3})\\b",
            "\\b(#PSARC\\d+)\\b",
            "(REVIEW 123)",
            "\\sreview=(\\d+[a-t])*(\\W*)"
        };
        for (String test : tests) {
            try {
                instance.setReviewPattern(test);
                assertEquals(test, instance.getReviewPattern());
            } catch (IllegalArgumentException ex) {
                fail("The pattern '" + test + "' should not throw an exception");
            }
        }
    }

    @Test
    public void testInvalidReviewPattern() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        String[] tests = new String[]{
            "\\b([",
            "\\b({,6})\\b",
            "\\b6)\\b",
            "*reviewy",
            "REVIEW 123", // does not contain a group
            "\\b[a-z]+\\b" // does not contain a group
        };
        for (String test : tests) {
            try {
                instance.setReviewPattern(test);
                fail("The pattern '" + test + "' should throw an exception");
            } catch (IllegalArgumentException ignored) {
            }
        }
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
        assertEquals(Configuration.RemoteSCM.OFF, instance.getRemoteScmSupported());
        instance.setRemoteScmSupported(Configuration.RemoteSCM.ON);
        assertEquals(Configuration.RemoteSCM.ON, instance.getRemoteScmSupported());
        instance.setRemoteScmSupported(Configuration.RemoteSCM.DIRBASED);
        assertEquals(Configuration.RemoteSCM.DIRBASED, instance.getRemoteScmSupported());
        instance.setRemoteScmSupported(Configuration.RemoteSCM.UIONLY);
        assertEquals(Configuration.RemoteSCM.UIONLY, instance.getRemoteScmSupported());
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
        assertEquals(LuceneLockName.OFF, instance.getLuceneLocking());
    }

    @Test
    public void testIndexVersionedFilesOnly() {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        assertFalse(instance.isIndexVersionedFilesOnly());
        instance.setIndexVersionedFilesOnly(true);
        assertTrue(instance.isIndexVersionedFilesOnly());
    }

    @Test
    public void testXMLencdec() throws IOException {
        Configuration c = new Configuration();
        String m = c.getXMLRepresentationAsString();
        Configuration o = Configuration.makeXMLStringAsConfiguration(m);
        assertNotNull(o);
        m = m.replace('a', 'm');
        try {
            o = Configuration.makeXMLStringAsConfiguration(m);
            fail("makeXmlStringsAsConfiguration should throw exception");
        } catch (Throwable t) {
        }
    }

    @Test
    public void testAuthorizationFlagDecode() throws IOException {
        String confString = "<?xml version='1.0' encoding='UTF-8'?>\n"
                + "<java class=\"java.beans.XMLDecoder\" version=\"1.8.0_121\">\n"
                + " <object class=\"org.opengrok.indexer.configuration.Configuration\">\n"
                + "\t<void property=\"pluginStack\">\n"
                + "\t\t<void method=\"add\">\n"
                + "\t\t\t<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "\t\t\t\t<void property=\"flag\">\n"
                + "\t\t\t\t\t<string>sufficient</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t\t<void property=\"name\">\n"
                + "\t\t\t\t\t<string>Plugin</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t</object>\n"
                + "\t\t</void>\n"
                + "\t\t<void method=\"add\">\n"
                + "\t\t\t<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "\t\t\t\t<void property=\"flag\">\n"
                + "\t\t\t\t\t<string>required</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t\t<void property=\"name\">\n"
                + "\t\t\t\t\t<string>OtherPlugin</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t</object>\n"
                + "\t\t</void>\n"
                + "\t\t<void method=\"add\">\n"
                + "\t\t\t<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "\t\t\t\t<void property=\"flag\">\n"
                + "\t\t\t\t\t<string>REQUISITE</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t\t<void property=\"name\">\n"
                + "\t\t\t\t\t<string>AnotherPlugin</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t</object>\n"
                + "\t\t</void>\n"
                + "\t\t<void method=\"add\">\n"
                + "\t\t\t<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "\t\t\t\t<void property=\"flag\">\n"
                + "\t\t\t\t\t<string>reQuIrEd</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t\t<void property=\"name\">\n"
                + "\t\t\t\t\t<string>DifferentPlugin</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t</object>\n"
                + "\t\t</void>\n"
                + "\t</void>\n"
                + " </object>\n"
                + "</java>";
        Configuration conf = Configuration.makeXMLStringAsConfiguration(confString);
        assertNotNull(conf.getPluginStack());
        AuthorizationStack pluginConfiguration = conf.getPluginStack();
        assertEquals(4, pluginConfiguration.getStack().size());
        assertTrue(pluginConfiguration.getStack().get(0).getFlag().isSufficient());
        assertEquals("Plugin", pluginConfiguration.getStack().get(0).getName());
        assertTrue(pluginConfiguration.getStack().get(1).getFlag().isRequired());
        assertEquals("OtherPlugin", pluginConfiguration.getStack().get(1).getName());
        assertTrue(pluginConfiguration.getStack().get(2).getFlag().isRequisite());
        assertEquals("AnotherPlugin", pluginConfiguration.getStack().get(2).getName());
        assertTrue(pluginConfiguration.getStack().get(3).getFlag().isRequired());
        assertEquals("DifferentPlugin", pluginConfiguration.getStack().get(3).getName());
    }

    @Test
    public void testAuthorizationStackDecode() throws IOException {
        String confString = "<?xml version='1.0' encoding='UTF-8'?>\n"
                + "<java class=\"java.beans.XMLDecoder\" version=\"1.8.0_121\">\n"
                + " <object class=\"org.opengrok.indexer.configuration.Configuration\">\n"
                + "\t<void property=\"pluginStack\">\n"
                + "\t\t<void method=\"add\">\n"
                + "\t\t\t<object id=\"first_plugin\" class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "\t\t\t\t<void property=\"flag\">\n"
                + "\t\t\t\t\t<string>sufficient</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t\t<void property=\"name\">\n"
                + "\t\t\t\t\t<string>Plugin</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t</object>\n"
                + "\t\t</void>\n"
                + "\t\t<void method=\"add\">\n"
                + "\t\t\t<object id=\"first_stack\" class=\"org.opengrok.indexer.authorization.AuthorizationStack\">\n"
                + "\t\t\t\t<void property=\"flag\">\n"
                + "\t\t\t\t\t<string>required</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t\t<void property=\"name\">\n"
                + "\t\t\t\t\t<string>basic stack</string>\n"
                + "\t\t\t\t</void>\n"
                + "                             <void property=\"stack\">"
                + "                                 <void method=\"add\">"
                + "\t                 \t\t<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "\t                 \t\t\t<void property=\"flag\">\n"
                + "\t                 \t\t\t\t<string>required</string>\n"
                + "\t                 \t\t\t</void>\n"
                + "\t                 \t\t\t<void property=\"name\">\n"
                + "\t                 \t\t\t\t<string>NestedPlugin</string>\n"
                + "\t                 \t\t\t</void>\n"
                + "\t\t                 \t</object>\n"
                + "                                 </void>"
                + "                                 <void method=\"add\">"
                + "\t                 \t\t<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "\t                 \t\t\t<void property=\"flag\">\n"
                + "\t                 \t\t\t\t<string>requisite</string>\n"
                + "\t                 \t\t\t</void>\n"
                + "\t                 \t\t\t<void property=\"name\">\n"
                + "\t                 \t\t\t\t<string>NestedPlugin</string>\n"
                + "\t                 \t\t\t</void>\n"
                + "                                             <void property=\"setup\">"
                + "                                                 <void method=\"put\">"
                + "                                                     <string>key</string>"
                + "                                                     <string>value</string>"
                + "                                                 </void>"
                + "                                                 <void method=\"put\">"
                + "                                                     <string>plugin</string>"
                + "                                                     <object idref=\"first_plugin\" />"
                + "                                                 </void>"
                + "                                             </void>"
                + "\t\t                 \t</object>\n"
                + "                                 </void>"
                + "                             </void>"
                + "\t\t\t</object>\n"
                + "\t\t</void>\n"
                + "\t\t<void method=\"add\">\n"
                + "\t\t\t<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "\t\t\t\t<void property=\"flag\">\n"
                + "\t\t\t\t\t<string>requisite</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t\t<void property=\"name\">\n"
                + "\t\t\t\t\t<string>Requisite</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t</object>\n"
                + "\t\t</void>\n"
                + "\t\t<void method=\"add\">\n"
                + "\t\t\t<object class=\"org.opengrok.indexer.authorization.AuthorizationStack\">\n"
                + "\t\t\t\t<void property=\"flag\">\n"
                + "\t\t\t\t\t<string>required</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t\t<void property=\"name\">\n"
                + "\t\t\t\t\t<string>advanced stack</string>\n"
                + "\t\t\t\t</void>\n"
                + "                             <void property=\"stack\">"
                + "                                 <void method=\"add\">"
                + "\t                 \t\t<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "\t                 \t\t\t<void property=\"flag\">\n"
                + "\t                 \t\t\t\t<string>required</string>\n"
                + "\t                 \t\t\t</void>\n"
                + "\t                 \t\t\t<void property=\"name\">\n"
                + "\t                 \t\t\t\t<string>NestedPlugin</string>\n"
                + "\t                 \t\t\t</void>\n"
                + "\t\t                 \t</object>\n"
                + "                                 </void>"
                + "                                 <void method=\"add\">"
                + "\t                 \t\t<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "\t                 \t\t\t<void property=\"flag\">\n"
                + "\t                 \t\t\t\t<string>requisite</string>\n"
                + "\t                 \t\t\t</void>\n"
                + "\t                 \t\t\t<void property=\"name\">\n"
                + "\t                 \t\t\t\t<string>NestedPlugin</string>\n"
                + "\t                 \t\t\t</void>\n"
                + "                                             <void property=\"setup\">"
                + "                                                 <void method=\"put\">"
                + "                                                     <string>key</string>"
                + "                                                     <string>other value</string>"
                + "                                                 </void>"
                + "                                                 <void method=\"put\">"
                + "                                                     <string>plugin</string>"
                + "                                                     <object idref=\"first_plugin\" />"
                + "                                                 </void>"
                + "                                             </void>"
                + "\t\t                 \t</object>\n"
                + "                                 </void>"
                + "                             </void>"
                + "\t\t\t</object>\n"
                + "\t\t</void>\n"
                + "\t\t<void method=\"add\">\n"
                + "\t\t\t<object idref=\"first_stack\" />"
                + "\t\t</void>\n"
                + "\t</void>\n"
                + " </object>\n"
                + "</java>";

        Configuration conf = Configuration.makeXMLStringAsConfiguration(confString);
        assertNotNull(conf.getPluginStack());
        AuthorizationStack pluginConfiguration = conf.getPluginStack();
        assertEquals(5, pluginConfiguration.getStack().size());

        // single plugins
        assertTrue(pluginConfiguration.getStack().get(0).getFlag().isSufficient());
        assertEquals("Plugin", pluginConfiguration.getStack().get(0).getName());
        assertTrue(pluginConfiguration.getStack().get(2).getFlag().isRequisite());
        assertEquals("Requisite", pluginConfiguration.getStack().get(2).getName());

        /**
         * Third element is a stack which defines two nested plugins.
         */
        assertTrue(pluginConfiguration.getStack().get(1) instanceof AuthorizationStack);
        AuthorizationStack stack = (AuthorizationStack) pluginConfiguration.getStack().get(1);
        assertTrue(stack.getFlag().isRequired());
        assertEquals("basic stack", stack.getName());

        assertEquals(2, stack.getStack().size());
        assertTrue(stack.getStack().get(0) instanceof AuthorizationPlugin);
        assertEquals("NestedPlugin", stack.getStack().get(0).getName());
        assertTrue(stack.getStack().get(0).isRequired());
        assertTrue(stack.getStack().get(1) instanceof AuthorizationPlugin);
        assertEquals("NestedPlugin", stack.getStack().get(1).getName());
        assertTrue(stack.getStack().get(1).isRequisite());
        AuthorizationPlugin plugin = (AuthorizationPlugin) stack.getStack().get(1);
        assertTrue(plugin.getSetup().containsKey("key"));
        assertEquals("value", plugin.getSetup().get("key"));
        assertTrue(plugin.getSetup().containsKey("plugin"));
        assertTrue(plugin.getSetup().get("plugin") instanceof AuthorizationPlugin);
        assertEquals(pluginConfiguration.getStack().get(0), plugin.getSetup().get("plugin"));

        /**
         * Fourth element is a stack slightly changed from the previous stack.
         * Only the setup for the particular plugin is changed.
         */
        assertTrue(pluginConfiguration.getStack().get(3) instanceof AuthorizationStack);
        stack = (AuthorizationStack) pluginConfiguration.getStack().get(3);
        assertTrue(stack.getFlag().isRequired());
        assertEquals("advanced stack", stack.getName());

        assertEquals(2, stack.getStack().size());
        assertTrue(stack.getStack().get(0) instanceof AuthorizationPlugin);
        assertEquals("NestedPlugin", stack.getStack().get(0).getName());
        assertTrue(stack.getStack().get(0).isRequired());
        assertTrue(stack.getStack().get(1) instanceof AuthorizationPlugin);
        assertEquals("NestedPlugin", stack.getStack().get(1).getName());
        assertTrue(stack.getStack().get(1).isRequisite());
        plugin = (AuthorizationPlugin) stack.getStack().get(1);
        assertTrue(plugin.getSetup().containsKey("key"));
        assertEquals("other value", plugin.getSetup().get("key"));
        assertTrue(plugin.getSetup().containsKey("plugin"));
        assertTrue(plugin.getSetup().get("plugin") instanceof AuthorizationPlugin);
        assertEquals(pluginConfiguration.getStack().get(0), plugin.getSetup().get("plugin"));

        /**
         * Fifth element is a direct copy of the first stack.
         */
        assertTrue(pluginConfiguration.getStack().get(4) instanceof AuthorizationStack);
        stack = (AuthorizationStack) pluginConfiguration.getStack().get(4);
        assertTrue(stack.getFlag().isRequired());
        assertEquals("basic stack", stack.getName());

        assertEquals(2, stack.getStack().size());
        assertTrue(stack.getStack().get(0) instanceof AuthorizationPlugin);
        assertEquals("NestedPlugin", stack.getStack().get(0).getName());
        assertTrue(stack.getStack().get(0).isRequired());
        assertTrue(stack.getStack().get(1) instanceof AuthorizationPlugin);
        assertEquals("NestedPlugin", stack.getStack().get(1).getName());
        assertTrue(stack.getStack().get(1).isRequisite());
        plugin = (AuthorizationPlugin) stack.getStack().get(1);
        assertTrue(plugin.getSetup().containsKey("key"));
        assertEquals("value", plugin.getSetup().get("key"));
        assertTrue(plugin.getSetup().containsKey("plugin"));
        assertTrue(plugin.getSetup().get("plugin") instanceof AuthorizationPlugin);
        assertEquals(pluginConfiguration.getStack().get(0), plugin.getSetup().get("plugin"));
    }

    /**
     * Testing invalid flag property.
     *
     * @throws IOException I/O exception
     */
    @Test(expected = IOException.class)
    public void testAuthorizationFlagDecodeInvalid() throws IOException {
        String confString = "<?xml version='1.0' encoding='UTF-8'?>\n"
                + "<java class=\"java.beans.XMLDecoder\" version=\"1.8.0_121\">\n"
                + " <object class=\"org.opengrok.indexer.configuration.Configuration\">\n"
                + "\t<void property=\"pluginStack\">\n"
                + "\t\t<void method=\"add\">\n"
                + "\t\t\t<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "\t\t\t\t<void property=\"flag\">\n"
                + "\t\t\t\t\t<string>noflag</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t\t<void property=\"name\">\n"
                + "\t\t\t\t\t<string>Plugin</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t</object>\n"
                + "\t\t</void>\n"
                + "\t</void>\n"
                + " </object>\n"
                + "</java>";
        Configuration.makeXMLStringAsConfiguration(confString);
    }

    /**
     * Testing invalid class names for authorization checks.
     *
     * @throws IOException I/O exception
     */
    @Test(expected = IOException.class)
    public void testAuthorizationDecodeInvalid() throws IOException {
        String confString = "<?xml version='1.0' encoding='UTF-8'?>\n"
                + "<java class=\"java.beans.XMLDecoder\" version=\"1.8.0_121\">\n"
                + " <object class=\"org.opengrok.indexer.configuration.Configuration\">\n"
                + "\t<void property=\"pluginStack\">\n"
                + "\t\t<void method=\"add\">\n"
                + "\t\t\t<object class=\"org.bad.package.authorization.NoCheck\">\n"
                + "\t\t\t\t<void property=\"flag\">\n"
                + "\t\t\t\t\t<string>sufficient</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t\t<void property=\"name\">\n"
                + "\t\t\t\t\t<string>Plugin</string>\n"
                + "\t\t\t\t</void>\n"
                + "\t\t\t</object>\n"
                + "\t\t</void>\n"
                + "\t</void>\n"
                + " </object>\n"
                + "</java>";
        Configuration.makeXMLStringAsConfiguration(confString);
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
        // The point of this test was to verify that setDataRoot() created
        // the directory, but that logic has been moved as of bug 16986, so
        // expect that the file does not exist.
        assertFalse(file.exists());
    }

    @Test
    public void testObfuscateEMail() throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        // By default, don't obfuscate.
        assertObfuscated(false, env);

        env.setObfuscatingEMailAddresses(true);
        assertObfuscated(true, env);

        env.setObfuscatingEMailAddresses(false);
        assertObfuscated(false, env);
    }

    private void assertObfuscated(boolean expected, RuntimeEnvironment env)
            throws IOException {
        assertEquals(expected, env.isObfuscatingEMailAddresses());

        String address = "discuss@opengrok.java.net";

        JFlexXref xref = new JFlexXref(new PlainXref(new StringReader(address)));
        StringWriter out = new StringWriter();
        xref.write(out);

        String expectedAddress = expected
                ? address.replace("@", " (at) ") : address;

        String expectedOutput
                = "<a class=\"l\" name=\"1\" href=\"#1\">1</a>"
                + expectedAddress;

        assertEquals(expectedOutput, out.toString());
    }

    @Test
    public void isChattyStatusPage() {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        // By default, status page should not be chatty.
        assertFalse(env.isChattyStatusPage());

        env.setChattyStatusPage(true);
        assertTrue(env.isChattyStatusPage());

        env.setChattyStatusPage(false);
        assertFalse(env.isChattyStatusPage());
    }

    /**
     * Verify that getPathRelativeToSourceRoot() returns path relative to
     * source root for both directories and symbolic links.
     * @throws java.io.IOException I/O exception
     * @throws ForbiddenSymlinkException forbidden symlink exception
     */
    @Test
    public void testGetPathRelativeToSourceRoot() throws IOException,
            ForbiddenSymlinkException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        // Create and set source root.
        File sourceRoot = Files.createTempDirectory("src").toFile();
        assertTrue(sourceRoot.exists());
        assertTrue(sourceRoot.isDirectory());
        env.setSourceRoot(sourceRoot.getPath());

        // Create directory underneath source root and check.
        String filename = "foo";
        File file = new File(env.getSourceRootFile(), filename);
        file.createNewFile();
        assertTrue(file.exists());
        assertEquals(File.separator + filename,
                env.getPathRelativeToSourceRoot(file));

        // Create symlink underneath source root.
        String symlinkName = "symlink";
        Path realDir = Files.createTempDirectory("realdir");
        File symlink = new File(sourceRoot, symlinkName);
        Files.createSymbolicLink(symlink.toPath(), realDir);
        assertTrue(symlink.exists());
        env.setAllowedSymlinks(new HashSet<>());
        ForbiddenSymlinkException expex = null;
        try {
            env.getPathRelativeToSourceRoot(symlink);
        } catch (ForbiddenSymlinkException e) {
            expex = e;
        }
        assertNotNull("getPathRelativeToSourceRoot() should have thrown " +
                "IOexception for symlink that is not allowed", expex);

        // Allow the symlink and retest.
        env.setAllowedSymlinks(new HashSet<>(Arrays.asList(symlink.getPath())));
        assertEquals(File.separator + symlinkName,
                env.getPathRelativeToSourceRoot(symlink));

        // cleanup
        IOUtils.removeRecursive(sourceRoot.toPath());
        IOUtils.removeRecursive(realDir);
    }

    @Test
    public void testPopulateGroupsMultipleTimes() {
        // create a structure with two repositories
        final RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        Project project1 = new Project("bar", "/bar");
        env.getProjects().put(project1.getName(), project1);
        Project project2 = new Project("barfoo", "/barfoo");
        env.getProjects().put(project2.getName(), project2);
        final Group group1 = new Group("group1", "bar");
        env.getGroups().add(group1);
        final Group group2 = new Group("group2", "bar.*");
        env.getGroups().add(group2);

        final RepositoryInfo repository1 = new RepositoryInfo();
        repository1.setDirectoryNameRelative("/bar");
        env.getRepositories().add(repository1);
        final RepositoryInfo repo2 = new RepositoryInfo();
        repository1.setDirectoryNameRelative("/barfoo");
        env.getRepositories().add(repo2);
        env.getProjectRepositoriesMap().put(project1, Arrays.asList(repository1));
        env.getProjectRepositoriesMap().put(project2, Arrays.asList(repo2));

        Assert.assertEquals(2, env.getProjects().size());
        Assert.assertEquals(2, env.getRepositories().size());
        Assert.assertEquals(2, env.getProjectRepositoriesMap().size());
        Assert.assertEquals(2, env.getGroups().size());

        // populate groups for the first time
        env.populateGroups(env.getGroups(), new TreeSet<>(env.getProjects().values()));

        Assert.assertEquals(2, env.getProjects().size());
        Assert.assertEquals(2, env.getRepositories().size());
        Assert.assertEquals(2, env.getProjectRepositoriesMap().size());
        Assert.assertEquals(2, env.getGroups().size());

        Assert.assertEquals(0, group1.getProjects().size());
        Assert.assertEquals(1, group1.getRepositories().size());
        Assert.assertEquals(0, group2.getProjects().size());
        Assert.assertEquals(2, group2.getRepositories().size());

        // remove a single repository object => project1 will become a simple project
        env.getProjectRepositoriesMap().remove(project1);
        env.getRepositories().remove(repository1);

        // populate groups for the second time
        env.populateGroups(env.getGroups(), new TreeSet<>(env.getProjects().values()));

        Assert.assertEquals(2, env.getProjects().size());
        Assert.assertEquals(1, env.getRepositories().size());
        Assert.assertEquals(1, env.getProjectRepositoriesMap().size());
        Assert.assertEquals(2, env.getGroups().size());
        Assert.assertEquals(1, group1.getProjects().size());
        Assert.assertEquals(0, group1.getRepositories().size());
        Assert.assertEquals(1, group2.getProjects().size());
        Assert.assertEquals(1, group2.getRepositories().size());
    }
}
