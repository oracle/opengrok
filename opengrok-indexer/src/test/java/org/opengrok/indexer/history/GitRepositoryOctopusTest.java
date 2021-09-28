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
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.indexer.web.Util;

import java.io.File;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * @author austvik
 */
public class GitRepositoryOctopusTest {

    private static TestRepository repository = new TestRepository();

    @BeforeAll
    public static void setUpClass() throws Exception {
        repository = new TestRepository();
        repository.create(GitRepositoryOctopusTest.class.getResourceAsStream(
                "/history/git-octopus.zip"));
        RuntimeEnvironment.getInstance().setMergeCommitsEnabled(true);
    }

    @AfterAll
    public static void tearDownClass() {
        repository.destroy();
        repository = null;
    }

    /*
     * $ cd git-octopus
     * $ git log --abbrev-commit --abbrev=8 --name-only --pretty=fuller --date=iso8601-strict -m
     * commit 206f862b (from 517209a6)
     * Merge: 517209a6 c0d60053 24fca17b 3603a5e2
     * Author:     Chris Fraire <cfraire@me.com>
     * AuthorDate: 2019-03-10T20:30:09-05:00
     * Commit:     Chris Fraire <cfraire@me.com>
     * CommitDate: 2019-03-10T20:31:12-05:00
     *
     *     Merge branches 'file_a', 'file_b' and 'file_c' into master, and add d
     *
     * a
     * b
     * c
     * d
     *
     * commit 206f862b (from c0d60053)
     * Merge: 517209a6 c0d60053 24fca17b 3603a5e2
     * Author:     Chris Fraire <cfraire@me.com>
     * AuthorDate: 2019-03-10T20:30:09-05:00
     * Commit:     Chris Fraire <cfraire@me.com>
     * CommitDate: 2019-03-10T20:31:12-05:00
     *
     *     Merge branches 'file_a', 'file_b' and 'file_c' into master, and add d
     *
     * b
     * c
     * d
     *
     * commit 206f862b (from 24fca17b)
     * Merge: 517209a6 c0d60053 24fca17b 3603a5e2
     * Author:     Chris Fraire <cfraire@me.com>
     * AuthorDate: 2019-03-10T20:30:09-05:00
     * Commit:     Chris Fraire <cfraire@me.com>
     * CommitDate: 2019-03-10T20:31:12-05:00
     *
     *     Merge branches 'file_a', 'file_b' and 'file_c' into master, and add d
     *
     * a
     * c
     * d
     *
     * commit 206f862b (from 3603a5e2)
     * Merge: 517209a6 c0d60053 24fca17b 3603a5e2
     * Author:     Chris Fraire <cfraire@me.com>
     * AuthorDate: 2019-03-10T20:30:09-05:00
     * Commit:     Chris Fraire <cfraire@me.com>
     * CommitDate: 2019-03-10T20:31:12-05:00
     *
     *     Merge branches 'file_a', 'file_b' and 'file_c' into master, and add d
     *
     * a
     * b
     * d
     *
     * commit 517209a6
     * Author:     Chris Fraire <cfraire@me.com>
     * AuthorDate: 2019-03-10T20:17:53-05:00
     * Commit:     Chris Fraire <cfraire@me.com>
     * CommitDate: 2019-03-10T20:17:53-05:00
     *
     *     Initial, empty commit
     *
     * commit 3603a5e2
     * Author:     Chris Fraire <cfraire@me.com>
     * AuthorDate: 2019-03-10T20:17:30-05:00
     * Commit:     Chris Fraire <cfraire@me.com>
     * CommitDate: 2019-03-10T20:17:30-05:00
     *
     *     Add c
     *
     * c
     *
     * commit 24fca17b
     * Author:     Chris Fraire <cfraire@me.com>
     * AuthorDate: 2019-03-10T20:17:11-05:00
     * Commit:     Chris Fraire <cfraire@me.com>
     * CommitDate: 2019-03-10T20:17:11-05:00
     *
     *     Add b
     *
     * b
     *
     * commit c0d60053
     * Author:     Chris Fraire <cfraire@me.com>
     * AuthorDate: 2019-03-10T20:16:32-05:00
     * Commit:     Chris Fraire <cfraire@me.com>
     * CommitDate: 2019-03-10T20:16:32-05:00
     *
     *     Add a
     *
     * a
     */

    @Test
    public void testOctopusHistory() throws Exception {
        File root = new File(repository.getSourceRoot(), "git-octopus");
        GitRepository gitRepo = (GitRepository) RepositoryFactory.getRepository(root);

        History history = gitRepo.getHistory(root);
        assertNotNull(history, "git-octopus getHistory()");

        List<HistoryEntry> entries = history.getHistoryEntries();
        assertNotNull(entries, "git-octopus getHistoryEntries()");
        assertEquals(5, entries.size(), "git-octopus log entries");

        SortedSet<String> allFiles = new TreeSet<>();
        for (HistoryEntry entry : entries) {
            allFiles.addAll(entry.getFiles().stream().map(Util::fixPathIfWindows).
                    collect(Collectors.toList()));
        }

        assertTrue(allFiles.contains("/git-octopus/d"), "should contain /git-octopus/d");
        assertTrue(allFiles.contains("/git-octopus/c"), "should contain /git-octopus/c");
        assertTrue(allFiles.contains("/git-octopus/b"), "should contain /git-octopus/b");
        assertTrue(allFiles.contains("/git-octopus/a"), "should contain /git-octopus/a");
        assertEquals(4, allFiles.size(), "git-octopus files from log");

        HistoryEntry first = entries.get(0);
        assertEquals("206f862b", first.getRevision(), "should be merge commit hash");
        assertEquals("Merge branches 'file_a', 'file_b' and 'file_c' into master, and add d",
                first.getMessage(),
                "should be merge commit message");
        assertEquals(4, first.getFiles().size(), "git-octopus files for merge");
    }
}
