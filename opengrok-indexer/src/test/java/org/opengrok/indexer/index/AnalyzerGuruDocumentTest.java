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
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.AnalyzerGuru;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.Repository;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.NullWriter;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

class AnalyzerGuruDocumentTest {
    private RuntimeEnvironment env;

    private static TestRepository testRepository;

    @BeforeEach
    void setUpClass() throws Exception {
        env = RuntimeEnvironment.getInstance();

        testRepository = new TestRepository();
        URL resourceURL = HistoryGuru.class.getResource("/repositories");
        assertNotNull(resourceURL);
        testRepository.create(resourceURL);

        env.setSourceRoot(testRepository.getSourceRoot());
        env.setDataRoot(testRepository.getDataRoot());
        env.setHistoryEnabled(true);
        env.setProjectsEnabled(true);
        RepositoryFactory.initializeIgnoredNames(env);

        // Restore the project and repository information.
        env.setProjects(new HashMap<>());
        env.setRepositories(testRepository.getSourceRoot());
        HistoryGuru.getInstance().invalidateRepositories(env.getRepositories(), CommandTimeoutType.INDEXER);
        env.generateProjectRepositoriesMap();
    }

    @AfterEach
    void tearDownClass() throws Exception {
        testRepository.destroy();
    }

    /**
     * {@link AnalyzerGuru#populateDocument(Document, File, String, AbstractAnalyzer, Writer)} should populate
     * the history of the document only if the repository related to the file allows for it.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testPopulateDocumentHistory(boolean historyEnabled) throws Exception {
        AnalyzerGuru analyzerGuru = new AnalyzerGuru();
        Document doc = Mockito.mock(Document.class);
        Path filePath = Path.of(env.getSourceRootPath(), "git", "main.c");
        File file = filePath.toFile();
        assertTrue(file.exists());
        HistoryGuru histGuru = HistoryGuru.getInstance();
        Repository repository = histGuru.getRepository(file);
        assertNotNull(repository);
        repository.setHistoryEnabled(historyEnabled);
        String relativePath = env.getPathRelativeToSourceRoot(file);
        analyzerGuru.populateDocument(doc, file, relativePath,
                IndexDatabase.getAnalyzerFor(file, relativePath), new NullWriter());
        ArgumentCaptor<TextField> argument = ArgumentCaptor.forClass(TextField.class);
        verify(doc, atLeast(1)).add(argument.capture());
        assertEquals(historyEnabled,
                argument.getAllValues().stream().anyMatch(e -> e.name().equals(QueryBuilder.HIST)));
    }
}
