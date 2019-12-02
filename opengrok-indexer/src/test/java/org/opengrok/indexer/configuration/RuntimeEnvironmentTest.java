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
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2019, Chris Fraire <cfraire@me.com>.
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
 * Test the RuntimeEnvironment class
 *
 * @author Trond Norbye
 */
@net.jcip.annotations.NotThreadSafe
public class RuntimeEnvironmentTest {

    private static RuntimeEnvironment env;
    private static File originalConfig;

    @BeforeClass
    public static void setUpClass() throws Exception {
        env = RuntimeEnvironment.getInstance();

        // preserve the original
        originalConfig = File.createTempFile("config", ".xml");
        env.writeConfiguration(originalConfig);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        // restore the configuration
        env.readConfiguration(originalConfig);
        originalConfig.delete();
    }

    @Before
    public void setUp() {
        // Create a default configuration
        Configuration config = new Configuration();
        env.setConfiguration(config);
    }

    @Test
    public void testDataRoot() throws IOException {
        assertNull(env.getDataRootFile());
        assertNull(env.getDataRootPath());
        File f = File.createTempFile("dataroot", null);
        String path = f.getCanonicalPath();
        assertTrue(f.delete());
        assertFalse(f.exists());
        env.setDataRoot(path);
        // setDataRoot() used to create path if it didn't exist, but that
        // logic has been moved. Verify that it is so.
        assertFalse(f.exists());
        assertTrue(f.mkdirs());
        assertEquals(path, env.getDataRootPath());
        assertEquals(path, env.getDataRootFile().getCanonicalPath());
    }

    @Test
    public void testIncludeRoot() throws IOException {
        assertNull(env.getIncludeRootPath());
        
        // set data root
        File f = File.createTempFile("dataroot", null);
        String path = f.getCanonicalPath();
        env.setDataRoot(path);
        
        // verify they are the same
        assertEquals(env.getDataRootPath(), env.getIncludeRootPath());
        
        // set include root
        f = File.createTempFile("includeroot", null);
        path = f.getCanonicalPath();
        env.setIncludeRoot(path);
        assertEquals(path, env.getIncludeRootPath());
    }
    
    @Test
    public void testSourceRoot() throws IOException {
        assertNull(env.getSourceRootFile());
        assertNull(env.getSourceRootPath());
        File f = File.createTempFile("sourceroot", null);
        String path = f.getCanonicalPath();
        assertTrue(f.delete());
        env.setSourceRoot(path);
        assertEquals(path, env.getSourceRootPath());
        assertEquals(path, env.getSourceRootFile().getCanonicalPath());
    }

    @Test
    public void testProjects() throws IOException {
        env.setProjectsEnabled(true);
        assertFalse(env.hasProjects());
        assertNotNull(env.getProjects());
        assertEquals(0, env.getProjects().size());
        assertNull(env.getDefaultProjects());

        File file = new File("/opengrok_automatic_test/foo/bar");
        File folder = new File("/opengrok_automatic_test/foo");
        env.setSourceRoot(folder.getCanonicalPath());
        Project p = new Project("bar");
        p.setPath("/bar");
        assertEquals("/bar", p.getId());
        env.getProjects().put(p.getName(), p);
        assertEquals(p, Project.getProject(file));
        env.setProjects(null);
        assertNull(env.getProjects());
    }

    @Test
    public void testGroups() {
        assertFalse(env.hasGroups());
        assertNotNull(env.getGroups());
        assertEquals(0, env.getGroups().size());

        Group g = new Group("Random", "xyz.*");

        env.getGroups().add(g);
        assertEquals(1, env.getGroups().size());
        assertEquals(g, env.getGroups().iterator().next());
        assertEquals("Random", env.getGroups().iterator().next().getName());

        env.setGroups(null);
        assertNull(env.getGroups());
    }

    @Test
    public void testPerThreadConsistency() throws InterruptedException {
        String path = "/tmp/dataroot1";
        env.setDataRoot(path);
        Thread t = new Thread(() -> {
            Configuration c = new Configuration();
            c.setDataRoot("/tmp/dataroot2");
            env.setConfiguration(c);
        });
        t.start();
        t.join();
        assertEquals("/tmp/dataroot2", env.getDataRootPath());
    }

    @Test
    public void testUrlPrefix() {
        assertEquals("/source/s?", env.getUrlPrefix());
    }

    @Test
    public void testCtags() {
        String instanceCtags = env.getCtags();
        assertNotNull(instanceCtags);
        assertTrue("instance ctags should equals 'ctags' or the sys property",
            instanceCtags.equals("ctags") || instanceCtags.equals(
            System.getProperty("org.opengrok.indexer.analysis.Ctags")));
        String path = "/usr/bin/ctags";
        env.setCtags(path);
        assertEquals(path, env.getCtags());

        env.setCtags(null);
        instanceCtags = env.getCtags();
        assertTrue("instance ctags should equals 'ctags' or the sys property",
            instanceCtags.equals("ctags") || instanceCtags.equals(
            System.getProperty("org.opengrok.indexer.analysis.Ctags")));
    }

    @Test
    public void testHistoryReaderTimeLimit() {
        assertEquals(30, env.getHistoryReaderTimeLimit());
        env.setHistoryReaderTimeLimit(50);
        assertEquals(50, env.getHistoryReaderTimeLimit());
    }

    @Test
    public void testFetchHistoryWhenNotInCache() {
        assertTrue(env.isFetchHistoryWhenNotInCache());
        env.setFetchHistoryWhenNotInCache(false);
        assertFalse(env.isFetchHistoryWhenNotInCache());
    }

    @Test
    public void testUseHistoryCache() {
        assertTrue(env.useHistoryCache());
        env.setUseHistoryCache(false);
        assertFalse(env.useHistoryCache());
    }

    @Test
    public void testGenerateHtml() {
        assertTrue(env.isGenerateHtml());
        env.setGenerateHtml(false);
        assertFalse(env.isGenerateHtml());
    }

    @Test
    public void testCompressXref() {
        assertTrue(env.isCompressXref());
        env.setCompressXref(false);
        assertFalse(env.isCompressXref());
    }

    @Test
    public void testQuickContextScan() {
        assertTrue(env.isQuickContextScan());
        env.setQuickContextScan(false);
        assertFalse(env.isQuickContextScan());
    }

    @Test
    public void testRepositories() {
        assertNotNull(env.getRepositories());
        env.removeRepositories();
        assertNull(env.getRepositories());
        List<RepositoryInfo> reps = new ArrayList<>();
        env.setRepositories(reps);
        assertSame(reps, env.getRepositories());
    }

    @Test
    public void testRamBufferSize() {
        assertEquals(16, env.getRamBufferSize(), 0);  //default is 16
        env.setRamBufferSize(256);
        assertEquals(256, env.getRamBufferSize(), 0);
    }

    @Test
    public void testAllowLeadingWildcard() {
        assertTrue(env.isAllowLeadingWildcard());
        env.setAllowLeadingWildcard(false);
        assertFalse(env.isAllowLeadingWildcard());
    }

    @Test
    public void testIgnoredNames() {
        assertNotNull(env.getIgnoredNames());
        env.setIgnoredNames(null);
        assertNull(env.getIgnoredNames());
    }

    @Test
    public void testUserPage() {
        String page = "http://www.myserver.org/viewProfile.jspa?username=";
        assertNull(env.getUserPage());   // default value is null
        env.setUserPage(page);
        assertEquals(page, env.getUserPage());
    }

    @Test
    public void testBugPage() {
        String page = "http://bugs.myserver.org/bugdatabase/view_bug.do?bug_id=";
        assertNull(env.getBugPage());   // default value is null
        env.setBugPage(page);
        assertEquals(page, env.getBugPage());
    }

    @Test
    public void testBugPattern() {
        String[] tests = new String[]{
            "\\b([12456789][0-9]{6})\\b",
            "\\b(#\\d+)\\b",
            "(BUG123)",
            "\\sbug=(\\d+[a-t])*(\\W*)"
        };
        for (String test : tests) {
            try {
                env.setBugPattern(test);
                assertEquals(test, env.getBugPattern());
            } catch (IOException ex) {
                fail("The pattern '" + test + "' should not throw an exception");

            }
        }
    }

    @Test
    public void testInvalidBugPattern() {
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
                env.setBugPattern(test);
                fail("The pattern '" + test + "' should throw an exception");
            } catch (IOException ex) {
            }
        }
    }

    @Test
    public void testReviewPage() {
        String page = "http://arc.myserver.org/caselog/PSARC/";
        assertNull(env.getReviewPage());   // default value is null
        env.setReviewPage(page);
        assertEquals(page, env.getReviewPage());
    }

    @Test
    public void testReviewPattern() {
        String[] tests = new String[]{
            "\\b(\\d{4}/\\d{3})\\b",
            "\\b(#PSARC\\d+)\\b",
            "(REVIEW 123)",
            "\\sreview=(\\d+[a-t])*(\\W*)"
        };
        for (String test : tests) {
            try {
                env.setReviewPattern(test);
                assertEquals(test, env.getReviewPattern());
            } catch (IOException ex) {
                fail("The pattern '" + test + "' should not throw an exception");

            }
        }
    }

    @Test
    public void testInvalidReviewPattern() {
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
                env.setReviewPattern(test);
                fail("The pattern '" + test + "' should throw an exception");
            } catch (IOException ex) {
            }
        }
    }

    @Test
    public void testWebappLAF() {
        assertEquals("default", env.getWebappLAF());
        env.setWebappLAF("foo");
        assertEquals("foo", env.getWebappLAF());
    }

    @Test
    public void testRemoteScmSupported() {
        assertEquals(Configuration.RemoteSCM.OFF, env.getRemoteScmSupported());
        env.setRemoteScmSupported(Configuration.RemoteSCM.ON);
        assertEquals(Configuration.RemoteSCM.ON, env.getRemoteScmSupported());
        env.setRemoteScmSupported(Configuration.RemoteSCM.DIRBASED);
        assertEquals(Configuration.RemoteSCM.DIRBASED, env.getRemoteScmSupported());
        env.setRemoteScmSupported(Configuration.RemoteSCM.UIONLY);
        assertEquals(Configuration.RemoteSCM.UIONLY, env.getRemoteScmSupported());
    }

    @Test
    public void testOptimizeDatabase() {
        assertTrue(env.isOptimizeDatabase());
        env.setOptimizeDatabase(false);
        assertFalse(env.isOptimizeDatabase());
    }

    @Test
    public void testUsingLuceneLocking() {
        assertEquals(LuceneLockName.OFF, env.getLuceneLocking());
    }

    @Test
    public void testIndexVersionedFilesOnly() {
        assertFalse(env.isIndexVersionedFilesOnly());
        env.setIndexVersionedFilesOnly(true);
        assertTrue(env.isIndexVersionedFilesOnly());
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
                + "	<void property=\"pluginStack\">\n"
                + "		<void method=\"add\">\n"
                + "			<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "				<void property=\"flag\">\n"
                + "					<string>sufficient</string>\n"
                + "				</void>\n"
                + "				<void property=\"name\">\n"
                + "					<string>Plugin</string>\n"
                + "				</void>\n"
                + "			</object>\n"
                + "		</void>\n"
                + "		<void method=\"add\">\n"
                + "			<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "				<void property=\"flag\">\n"
                + "					<string>required</string>\n"
                + "				</void>\n"
                + "				<void property=\"name\">\n"
                + "					<string>OtherPlugin</string>\n"
                + "				</void>\n"
                + "			</object>\n"
                + "		</void>\n"
                + "		<void method=\"add\">\n"
                + "			<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "				<void property=\"flag\">\n"
                + "					<string>REQUISITE</string>\n"
                + "				</void>\n"
                + "				<void property=\"name\">\n"
                + "					<string>AnotherPlugin</string>\n"
                + "				</void>\n"
                + "			</object>\n"
                + "		</void>\n"
                + "		<void method=\"add\">\n"
                + "			<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "				<void property=\"flag\">\n"
                + "					<string>reQuIrEd</string>\n"
                + "				</void>\n"
                + "				<void property=\"name\">\n"
                + "					<string>DifferentPlugin</string>\n"
                + "				</void>\n"
                + "			</object>\n"
                + "		</void>\n"
                + "	</void>\n"
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
                + "	<void property=\"pluginStack\">\n"
                + "		<void method=\"add\">\n"
                + "			<object id=\"first_plugin\" class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "				<void property=\"flag\">\n"
                + "					<string>sufficient</string>\n"
                + "				</void>\n"
                + "				<void property=\"name\">\n"
                + "					<string>Plugin</string>\n"
                + "				</void>\n"
                + "			</object>\n"
                + "		</void>\n"
                + "		<void method=\"add\">\n"
                + "			<object id=\"first_stack\" class=\"org.opengrok.indexer.authorization.AuthorizationStack\">\n"
                + "				<void property=\"flag\">\n"
                + "					<string>required</string>\n"
                + "				</void>\n"
                + "				<void property=\"name\">\n"
                + "					<string>basic stack</string>\n"
                + "				</void>\n"
                + "                             <void property=\"stack\">"
                + "                                 <void method=\"add\">"
                + "	                 		<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "	                 			<void property=\"flag\">\n"
                + "	                 				<string>required</string>\n"
                + "	                 			</void>\n"
                + "	                 			<void property=\"name\">\n"
                + "	                 				<string>NestedPlugin</string>\n"
                + "	                 			</void>\n"
                + "		                 	</object>\n"
                + "                                 </void>"
                + "                                 <void method=\"add\">"
                + "	                 		<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "	                 			<void property=\"flag\">\n"
                + "	                 				<string>requisite</string>\n"
                + "	                 			</void>\n"
                + "	                 			<void property=\"name\">\n"
                + "	                 				<string>NestedPlugin</string>\n"
                + "	                 			</void>\n"
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
                + "		                 	</object>\n"
                + "                                 </void>"
                + "                             </void>"
                + "			</object>\n"
                + "		</void>\n"
                + "		<void method=\"add\">\n"
                + "			<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "				<void property=\"flag\">\n"
                + "					<string>requisite</string>\n"
                + "				</void>\n"
                + "				<void property=\"name\">\n"
                + "					<string>Requisite</string>\n"
                + "				</void>\n"
                + "			</object>\n"
                + "		</void>\n"
                + "		<void method=\"add\">\n"
                + "			<object class=\"org.opengrok.indexer.authorization.AuthorizationStack\">\n"
                + "				<void property=\"flag\">\n"
                + "					<string>required</string>\n"
                + "				</void>\n"
                + "				<void property=\"name\">\n"
                + "					<string>advanced stack</string>\n"
                + "				</void>\n"
                + "                             <void property=\"stack\">"
                + "                                 <void method=\"add\">"
                + "	                 		<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "	                 			<void property=\"flag\">\n"
                + "	                 				<string>required</string>\n"
                + "	                 			</void>\n"
                + "	                 			<void property=\"name\">\n"
                + "	                 				<string>NestedPlugin</string>\n"
                + "	                 			</void>\n"
                + "		                 	</object>\n"
                + "                                 </void>"
                + "                                 <void method=\"add\">"
                + "	                 		<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "	                 			<void property=\"flag\">\n"
                + "	                 				<string>requisite</string>\n"
                + "	                 			</void>\n"
                + "	                 			<void property=\"name\">\n"
                + "	                 				<string>NestedPlugin</string>\n"
                + "	                 			</void>\n"
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
                + "		                 	</object>\n"
                + "                                 </void>"
                + "                             </void>"
                + "			</object>\n"
                + "		</void>\n"
                + "		<void method=\"add\">\n"
                + "			<object idref=\"first_stack\" />"
                + "		</void>\n"
                + "	</void>\n"
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
                + "	<void property=\"pluginStack\">\n"
                + "		<void method=\"add\">\n"
                + "			<object class=\"org.opengrok.indexer.authorization.AuthorizationPlugin\">\n"
                + "				<void property=\"flag\">\n"
                + "					<string>noflag</string>\n"
                + "				</void>\n"
                + "				<void property=\"name\">\n"
                + "					<string>Plugin</string>\n"
                + "				</void>\n"
                + "			</object>\n"
                + "		</void>\n"
                + "	</void>\n"
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
                + "	<void property=\"pluginStack\">\n"
                + "		<void method=\"add\">\n"
                + "			<object class=\"org.bad.package.authorization.NoCheck\">\n"
                + "				<void property=\"flag\">\n"
                + "					<string>sufficient</string>\n"
                + "				</void>\n"
                + "				<void property=\"name\">\n"
                + "					<string>Plugin</string>\n"
                + "				</void>\n"
                + "			</object>\n"
                + "		</void>\n"
                + "	</void>\n"
                + " </object>\n"
                + "</java>";
        Configuration.makeXMLStringAsConfiguration(confString);
    }

    @Test
    public void testBug3095() throws IOException {
        File file = new File("foobar");
        assertTrue(file.createNewFile());
        assertFalse(file.isAbsolute());
        env.setDataRoot(file.getName());
        File f = env.getDataRootFile();
        assertNotNull(f);
        assertEquals("foobar", f.getName());
        assertTrue(f.isAbsolute());
        assertTrue(file.delete());
    }

    @Test
    public void testBug3154() throws IOException {
        File file = File.createTempFile("dataroot", null);
        assertTrue(file.delete());
        assertFalse(file.exists());
        env.setDataRoot(file.getAbsolutePath());
        // The point of this test was to verify that setDataRoot() created
        // the directory, but that logic has been moved as of bug 16986, so
        // expect that the file does not exist.
        assertFalse(file.exists());
    }

    @Test
    public void testObfuscateEMail() throws IOException {
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
