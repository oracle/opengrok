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
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

public class AnalyzerFrameworkTest {

    private final File pluginDirectory;

    public AnalyzerFrameworkTest() throws URISyntaxException {
        pluginDirectory = Paths.get(getClass().getResource("/analysis/plugins/testplugins.jar").toURI()).toFile().getParentFile();
    }

    /**
     * Analyzer framework should be started reloaded from the plugin directory.
     */
    @Test
    public void testReloadDefault() {
        AnalyzerFramework framework = new AnalyzerFramework(null);

        Assert.assertFalse("Analyzer framework should load the default analyzers", framework.getAnalyzersInfo().factories.isEmpty());
        Assert.assertFalse("Analyzer framework should load the default analyzers", framework.getAnalyzersInfo().extensions.isEmpty());
        Assert.assertFalse("Analyzer framework should load the default analyzers", framework.getAnalyzersInfo().magics.isEmpty());
        Assert.assertFalse("Analyzer framework should load the default analyzers", framework.getAnalyzersInfo().prefixes.isEmpty());
        Assert.assertFalse("Analyzer framework should load the default analyzers", framework.getAnalyzersInfo().matchers.isEmpty());
    }

    /**
     * Analyzer framework should be started reloaded from the plugin directory.
     */
    @Test
    public void testReloadSimple() {
        AnalyzerFramework framework = new AnalyzerFramework(pluginDirectory.getPath());
        framework.setLoadClasses(false); // to avoid noise when loading classes of other tests

        // Ensure the framework was setup correctly.
        assertNotNull(framework.getPluginDirectory());
        assertEquals(pluginDirectory, framework.getPluginDirectory());

        final List<AnalyzerFactory> factories = framework.getAnalyzersInfo().factories;
        Assert.assertTrue(factories.stream().map(f -> f.getClass().getSimpleName()).anyMatch(name -> "DummyAnalyzerFactory".equals(name)));
        Assert.assertTrue(factories.stream().map(AnalyzerFactory::getName).anyMatch(name -> "Dummy".equals(name)));
        Assert.assertTrue(framework.getAnalyzersInfo().extensions.containsKey("DUMMY"));
    }

    @Test
    public void testGuruRegisteredExtension() {
        final RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setPluginDirectory(pluginDirectory.getAbsolutePath());
        final AnalyzerGuru guru = env.getAnalyzerGuru();

        Assert.assertNotNull(guru.find("file.dummy"));
        Assert.assertEquals("DummyAnalyzerFactory", guru.find("file.dummy").getClass().getSimpleName());
        Assert.assertEquals("Dummy", guru.find("file.dummy").getName());
    }

    @Test
    public void testAnalyzeStream() throws IOException {
        final Path file = Files.createTempFile("opengrok", "analyzer-framework-test.dummy");
        final List<String> lines = Arrays.asList(new String[]{
                "Some random content",
                "\tsecond line is padded",
                "james.bond@italy.com",
                "opengrok is the best"
        });

        Files.write(file, lines);

        try (Reader reader = Files.newBufferedReader(file); StringWriter out = new StringWriter()) {
            final RuntimeEnvironment env = RuntimeEnvironment.getInstance();
            env.setPluginDirectory(pluginDirectory.getAbsolutePath());
            final AnalyzerGuru guru = env.getAnalyzerGuru();

            final AnalyzerFactory factory = guru.find(file.toString());
            final AbstractAnalyzer analyzer = factory.getAnalyzer();

            Assert.assertEquals(AbstractAnalyzer.Genre.PLAIN, analyzer.getGenre());

            final Xrefer xrefer = analyzer.writeXref(new WriteXrefArgs(reader, out));

            Assert.assertEquals(4, xrefer.getLineNumber());
            Assert.assertEquals(4, xrefer.getLOC());

            // assert content
            Assert.assertEquals(lines.stream().collect(Collectors.joining("\n")), out.toString());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
