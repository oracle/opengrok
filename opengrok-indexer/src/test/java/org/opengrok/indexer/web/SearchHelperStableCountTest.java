package org.opengrok.indexer.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link SearchHelper} produces an exact and stable {@code totalHits}
 * regardless of the {@code maxItems} window, so the UI search total matches the REST
 * API and does not vary across repeated searches. The previous implementation could
 * return an approximate lower-bound count for queries matching more than about a
 * thousand documents.
 *
 * <p>The test creates a synthetic corpus of {@value #SYNTHETIC_DOC_COUNT} files,
 * each containing a unique marker token, large enough to exercise that boundary.</p>
 */
class SearchHelperStableCountTest {

    private static final String MARKER = "searchhelperbug3239marker";
    private static final int SYNTHETIC_DOC_COUNT = 1500;
    private static final String SYNTHETIC_PROJECT = "synthetic_bulk";

    private static TestRepository repository;
    private static RuntimeEnvironment env;

    @BeforeAll
    static void setUpClass() throws Exception {
        repository = new TestRepository();
        repository.create(SearchHelperStableCountTest.class.getClassLoader().getResource("sources"));

        Path syntheticDir = Path.of(repository.getSourceRoot(), SYNTHETIC_PROJECT);
        Files.createDirectories(syntheticDir);
        // Make one document score much higher than the rest. Without this variance
        // the bug does not reproduce reliably on a small corpus.
        for (int i = 0; i < SYNTHETIC_DOC_COUNT; i++) {
            int repeat = (i == 0) ? 10_000 : 1;
            Files.writeString(syntheticDir.resolve("doc_" + i + ".txt"),
                    (MARKER + " ").repeat(repeat) + "\n");
        }

        env = RuntimeEnvironment.getInstance();
        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setHistoryEnabled(false);
        env.setProjectsEnabled(true);

        Indexer.getInstance().prepareIndexer(env, true, true, null, null);
        env.setDefaultProjectsFromNames(
                new TreeSet<>(Collections.singletonList("/" + SYNTHETIC_PROJECT)));
        Indexer.getInstance().doIndexerExecution(null, null);
    }

    @AfterAll
    static void tearDownClass() {
        repository.destroy();
    }

    @Test
    void testTotalHitsIsExactAndStable() {
        SortedSet<String> projectNames = new TreeSet<>();
        projectNames.add(SYNTHETIC_PROJECT);

        // Run with a large maxItems first to capture the true matching document count;
        // the pre-fix implementation returned the exact count only with a sufficiently
        // large result window.
        long reference = runSearchForTotalHits(projectNames, SYNTHETIC_DOC_COUNT);
        assertTrue(reference > 1000,
                "need more than a thousand matches to reliably trigger the bug, got " + reference);

        for (int maxItems : new int[]{1, 2, 10, 100}) {
            assertEquals(reference, runSearchForTotalHits(projectNames, maxItems),
                    "totalHits must be exact and stable regardless of maxItems");
        }
    }

    @Test
    void testTotalHitsIsStableAcrossRepeatedCalls() {
        SortedSet<String> projectNames = new TreeSet<>();
        projectNames.add(SYNTHETIC_PROJECT);

        long first = runSearchForTotalHits(projectNames, 1);
        assertTrue(first > 1000,
                "need more than a thousand matches to reliably trigger the bug, got " + first);
        for (int i = 0; i < 5; i++) {
            assertEquals(first, runSearchForTotalHits(projectNames, 1),
                    "totalHits must not drift across repeated queries");
        }
    }

    private long runSearchForTotalHits(SortedSet<String> projectNames, int maxItems) {
        SearchHelper searchHelper = new SearchHelper.Builder(env.getDataRootFile(),
                env.getSourceRootFile(), null,
                new QueryBuilder().setFreetext(MARKER), env.getUrlPrefix())
                .maxItems(maxItems)
                .build()
                .prepareExec(projectNames)
                .executeQuery();
        try {
            assertNull(searchHelper.getErrorMsg());
            return searchHelper.getTotalHits();
        } finally {
            searchHelper.destroy();
        }
    }
}
