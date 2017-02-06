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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.index.IgnoredNames;

/**
 *
 * @author Krystof Tulinger
 */
public class AcceptHelperTest {

    private File tempDir;

    @Before
    public void setUp() throws IOException {
        tempDir = File.createTempFile("temp", Long.toString(System.currentTimeMillis()));
        if (!tempDir.delete()) {
            throw new IOException("Could not delete temporary file to create a directory: " + tempDir.getAbsolutePath());
        }
        if (!tempDir.mkdir()) {
            throw new IOException("Could not create a temporary directory: " + tempDir.getAbsolutePath());
        }
    }

    @After
    public void tearDown() throws IOException {
        IOUtils.removeRecursive(tempDir.toPath());
    }

    protected void runAcceptTests(File sourceRoot, File[] tests, boolean accept) throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        IgnoredNames ignoredNames = env.getIgnoredNames();

        createSourceRoot(sourceRoot);
        String oldSourceRoot = env.getSourceRootPath();
        env.setSourceRoot(sourceRoot.getAbsolutePath());

        IgnoredNames newIgnored = new IgnoredNames();
        newIgnored.add("f:inside.c");
        newIgnored.add("d:foo");
        newIgnored.add("d:subdir");
        newIgnored.add("d:ignoredDir");
        newIgnored.add("*/ignored/*");
        newIgnored.add("foolink");
        env.setIgnoredNames(newIgnored);

        for (File test : tests) {
            Assert.assertEquals(accept, AcceptHelper.accept(test));
        }

        env.setIgnoredNames(ignoredNames);
        if (oldSourceRoot != null) {
            env.setSourceRoot(oldSourceRoot);
        }
    }

    /**
     * Test of accept method, of class AcceptHelper.
     */
    @Test
    public void testAcceptIgnored() throws IOException {
        File sourceRoot = new File(tempDir.getAbsolutePath(), "source");

        File ignored[] = new File[]{
            createFile(sourceRoot + File.separator + "inside.c"),
            createFile(sourceRoot + File.separator + "subdir" + File.separator + "inside.c"),
            createFile(sourceRoot + File.separator + "subdir" + File.separator + "subdir" + File.separator + "inside.c"),
            createFile(sourceRoot + File.separator + "ignored" + File.separator + "main.c"),
            createFile(sourceRoot + File.separator + "ignored" + File.separator + "random.c"),
            createDirectory(sourceRoot + File.separator + "ignoredDir"),
            createDirectory(sourceRoot + File.separator + "subdir" + File.separator + "ignoredDir")
        };

        createFile(sourceRoot + File.separator + "main.c");
        createFile(sourceRoot + File.separator + "random.c");
        createFile(sourceRoot + File.separator + "subdir" + File.separator + "main.c");
        createFile(sourceRoot + File.separator + "subdir" + File.separator + "random.c");
        createSymlink(sourceRoot + File.separator + "subdir", sourceRoot + File.separator + "link");
        createSymlink(sourceRoot + File.separator + "subdir", sourceRoot + File.separator + "subdir" + File.separator + "link");
        createSymlink(sourceRoot.getAbsolutePath(), sourceRoot + File.separator + "foolink");
        createSymlink(sourceRoot.getAbsolutePath(), sourceRoot + File.separator + "subdir" + File.separator + "foolink");

        runAcceptTests(sourceRoot, ignored, false);
        IOUtils.removeRecursive(sourceRoot.toPath());
    }

    /**
     * Test of accept method, of class AcceptHelper.
     */
    @Test
    public void testAcceptAccepted() throws IOException {

        File sourceRoot = new File(tempDir.getAbsolutePath(), "source");

        createFile(sourceRoot + File.separator + "inside.c");
        createFile(sourceRoot + File.separator + "subdir" + File.separator + "inside.c");
        createFile(sourceRoot + File.separator + "subdir" + File.separator + "subdir" + File.separator + "inside.c");
        createFile(sourceRoot + File.separator + "ignored" + File.separator + "main.c");
        createFile(sourceRoot + File.separator + "ignored" + File.separator + "random.c");
        createDirectory(sourceRoot + File.separator + "ignoredDir");
        createDirectory(sourceRoot + File.separator + "subdir" + File.separator + "ignoredDir");

        File accepted[] = new File[]{
            createFile(sourceRoot + File.separator + "main.c"),
            createFile(sourceRoot + File.separator + "random.c"),
            createFile(sourceRoot + File.separator + "subdir" + File.separator + "main.c"),
            createFile(sourceRoot + File.separator + "subdir" + File.separator + "random.c"),
            createSymlink(sourceRoot + File.separator + "subdir", sourceRoot + File.separator + "link"),
            createSymlink(sourceRoot + File.separator + "subdir", sourceRoot + File.separator + "subdir" + File.separator + "link"),
            createSymlink(sourceRoot.getAbsolutePath(), sourceRoot + File.separator + "foolink"),
            createSymlink(sourceRoot.getAbsolutePath(), sourceRoot + File.separator + "subdir" + File.separator + "foolink")
        };

        runAcceptTests(sourceRoot, accepted, true);
        IOUtils.removeRecursive(sourceRoot.toPath());
    }

    /**
     * Test of acceptSymlink method, of class AcceptHelper.
     */
    @Test
    public void testAcceptSymlink() throws IOException {
        File sourceRoot = createSourceRoot(new File(tempDir.getAbsolutePath(), "source/root"));
        File symlinkRoot = sourceRoot.getParentFile();

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        Set<String> allowedSymlinks = env.getAllowedSymlinks();

        File t1 = createFile(symlinkRoot + File.separator + "file1.c");
        File t2 = createFile(symlinkRoot + File.separator + "file2.c");
        File t3 = createFile(symlinkRoot + File.separator + "file3.c");

        env.setAllowedSymlinks(new TreeSet<>(Arrays.asList(new String[]{
            sourceRoot + File.separator + "link1",
            sourceRoot + File.separator + "link2",
            sourceRoot + File.separator + "link3"
        })));

        File[] tests = new File[]{
            createSymlink(t1.getAbsolutePath(), sourceRoot + File.separator + "link1"),
            createSymlink(t2.getAbsolutePath(), sourceRoot + File.separator + "link2"),
            createSymlink(t3.getAbsolutePath(), sourceRoot + File.separator + "link3")
        };
        runSymlinkTests(sourceRoot, tests, true);

        env.setAllowedSymlinks(new TreeSet<>());
        runSymlinkTests(sourceRoot, tests, false);

        env.setAllowedSymlinks(allowedSymlinks);
        IOUtils.removeRecursive(sourceRoot.toPath());
    }

    private void runSymlinkTests(File sourceRoot, File[] tests, boolean expected) throws IOException {
        createSourceRoot(sourceRoot);

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String oldSourceRoot = env.getSourceRootPath();
        env.setSourceRoot(sourceRoot.getAbsolutePath());

        for (File file : tests) {
            Assert.assertEquals(expected, AcceptHelper.acceptSymlink(null, file));
        }

        if (oldSourceRoot != null) {
            env.setSourceRoot(oldSourceRoot);
        }
    }

    /**
     *
     * @param sourceRoot
     * @param tests
     * @throws IOException
     */
    protected void runLocalTests(File sourceRoot, Object[][] tests) throws IOException {
        createSourceRoot(sourceRoot);

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        List<Project> projects = env.getProjects();
        String oldSourceRoot = env.getSourceRootPath();
        env.setProjects(new ArrayList<>());
        env.setSourceRoot(sourceRoot.getAbsolutePath());

        for (Object[] test : tests) {
            Project project = (Project) test[0];
            if (project != null && !env.getProjects().contains(project)) {
                env.getProjects().add(project);
            }
            File file = (File) test[1];
            Boolean expected = (Boolean) test[2];
            Assert.assertEquals(expected.booleanValue(), AcceptHelper.isLocal(project, file.getCanonicalPath()));
        }

        env.setProjects(projects);
        if (oldSourceRoot != null) {
            env.setSourceRoot(oldSourceRoot);
        }
    }

    protected File createSourceRoot(File sourceRoot) throws IOException {
        if (!sourceRoot.exists() && !sourceRoot.mkdirs()) {
            throw new IOException("Could not create a testing source root: " + sourceRoot.getAbsolutePath());
        }
        return sourceRoot;
    }

    /**
     * Test of isLocal method, of class AcceptHelper.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testIsLocalWithoutProjects() throws IOException {
        File sourceRoot = new File(tempDir.getAbsolutePath(), "source");
        Object[][] tests = new Object[][]{
            {null, createFile(sourceRoot + File.separator + "inside.c"), true},
            {null, createFile(sourceRoot + File.separator + "subdir" + File.separator + "inside.c"), true},
            {null, createFile(sourceRoot + File.separator + "subdir" + File.separator + "subdir" + File.separator + "inside.c"), true},
            {null, createSymlink(sourceRoot + File.separator + "subdir", sourceRoot + File.separator + "link"), true},
            {null, createSymlink(sourceRoot + File.separator + "subdir", sourceRoot + File.separator + "subdir" + File.separator + "link"), true},
            {null, createSymlink(sourceRoot.getAbsolutePath(), sourceRoot + File.separator + "foolink"), true},
            {null, createSymlink(sourceRoot.getAbsolutePath(), sourceRoot + File.separator + "subdir" + File.separator + "foolink"), true}
        };
        runLocalTests(sourceRoot, tests);
        IOUtils.removeRecursive(sourceRoot.toPath());
    }

    /**
     * Test of isLocal method, of class AcceptHelper.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testIsLocalWithProjects() throws IOException {
        File sourceRoot = new File(tempDir.getAbsolutePath(), "source/subroot");
        Object[][] tests = new Object[][]{
            {createProject("/project1"), createFile(sourceRoot + File.separator + "project1" + File.separator + "inside.c"), true},
            {createProject("/project1"), createFile(sourceRoot + File.separator + "project1" + File.separator + "subdir" + File.separator + "inside.c"), true},
            {createProject("/project1"), createFile(sourceRoot + File.separator + "project1" + File.separator + "subdir" + File.separator + "subdir" + File.separator + "inside.c"), true},
            {createProject("/project1"), createSymlink(sourceRoot + File.separator + "project1" + File.separator + "subdir", sourceRoot + File.separator + "project1" + File.separator + "link"), true},
            {createProject("/project1"), createSymlink(sourceRoot + File.separator + "project1" + File.separator + "subdir", sourceRoot + File.separator + "project1" + File.separator + "subdir" + File.separator + "link"), true},
            {createProject("/project1"), createSymlink(sourceRoot.getAbsolutePath() + File.separator + "project1", sourceRoot + File.separator + "project1" + File.separator + "foolink"), true},
            {createProject("/project1"), createSymlink(sourceRoot.getAbsolutePath() + File.separator + "project1", sourceRoot + File.separator + "project1" + File.separator + "subdir" + File.separator + "foolink"), true},
            {createProject("/project2"), createFile(sourceRoot + File.separator + "project2" + File.separator + "inside.c"), true},
            {createProject("/project2"), createFile(sourceRoot + File.separator + "project2" + File.separator + "subdir" + File.separator + "inside.c"), true},
            {createProject("/project2"), createFile(sourceRoot + File.separator + "project2" + File.separator + "subdir" + File.separator + "subdir" + File.separator + "inside.c"), true},
            {createProject("/project2"), createSymlink(sourceRoot + File.separator + "project2" + File.separator + "subdir", sourceRoot + File.separator + "project2" + File.separator + "link"), true},
            {createProject("/project2"), createSymlink(sourceRoot + File.separator + "project2" + File.separator + "subdir", sourceRoot + File.separator + "project2" + File.separator + "subdir" + File.separator + "link"), true},
            {createProject("/project2"), createSymlink(sourceRoot.getAbsolutePath() + File.separator + "project2", sourceRoot + File.separator + "project2" + File.separator + "foolink"), true},
            {createProject("/project2"), createSymlink(sourceRoot.getAbsolutePath() + File.separator + "project2", sourceRoot + File.separator + "project2" + File.separator + "subdir" + File.separator + "foolink"), true}
        };
        runLocalTests(sourceRoot, tests);
    }

    /**
     * Test of isLocal method, of class AcceptHelper.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testIsNotLocalWithProjects() throws IOException {
        File sourceRoot = new File(tempDir.getAbsolutePath(), "source/subroot");
        Object[][] tests = new Object[][]{
            {createProject("/project1"), createFile(sourceRoot + File.separator + "inside.c"), false},
            {createProject("/project1"), createFile(sourceRoot + File.separator + "subdir" + File.separator + "inside.c"), false},
            {createProject("/project1"), createFile(sourceRoot + File.separator + "subdir" + File.separator + "subdir" + File.separator + "inside.c"), false},
            {createProject("/project1"), createSymlink(sourceRoot + File.separator + "subdir", sourceRoot + File.separator + "link"), false},
            {createProject("/project1"), createSymlink(sourceRoot + File.separator + "subdir", sourceRoot + File.separator + "subdir" + File.separator + "link"), false},
            {createProject("/project1"), createSymlink(sourceRoot.getAbsolutePath(), sourceRoot + File.separator + "foolink"), false},
            {createProject("/project1"), createSymlink(sourceRoot.getAbsolutePath(), sourceRoot + File.separator + "subdir" + File.separator + "foolink"), false},
            {createProject("/project1"), createFile(sourceRoot.getParentFile() + File.separator + "inside.c"), false},
            {createProject("/project1"), createFile(sourceRoot.getParentFile() + File.separator + "subdir" + File.separator + "inside.c"), false},
            {createProject("/project1"), createFile(sourceRoot.getParentFile() + File.separator + "subdir" + File.separator + "subdir" + File.separator + "inside.c"), false},
            {createProject("/project1"), createSymlink(sourceRoot.getParentFile() + File.separator + "subdir", sourceRoot.getParentFile() + File.separator + "link"), false},
            {createProject("/project1"), createSymlink(sourceRoot.getParentFile() + File.separator + "subdir", sourceRoot.getParentFile() + File.separator + "subdir" + File.separator + "link"), false},
            {createProject("/project1"), createSymlink(sourceRoot.getParentFile().getAbsolutePath(), sourceRoot.getParentFile() + File.separator + "foolink"), false},
            {createProject("/project1"), createSymlink(sourceRoot.getParentFile().getAbsolutePath(), sourceRoot.getParentFile() + File.separator + "subdir" + File.separator + "foolink"), false},
            {createProject("/project1"), createFile(sourceRoot + File.separator + "project2" + File.separator + "subdir" + File.separator + "main.c"), false},
            {createProject("/project2"), createFile(sourceRoot + File.separator + "project1" + File.separator + "main.c"), false},
            {createProject("/project1"), createSymlink(sourceRoot + File.separator + "project2", sourceRoot + File.separator + "project1" + File.separator + "external"), false}
        };
        runLocalTests(sourceRoot, tests);
    }

    /**
     * Test of isLocal method, of class AcceptHelper.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testIsNotLocalWithoutProjects() throws IOException {
        File sourceRoot = new File(tempDir.getAbsolutePath(), "source/subroot");
        Object[][] tests = new Object[][]{
            {null, createFile(sourceRoot.getParentFile() + File.separator + "inside.c"), false},
            {null, createFile(sourceRoot.getParentFile() + File.separator + "subdir" + File.separator + "inside.c"), false},
            {null, createFile(sourceRoot.getParentFile() + File.separator + "subdir" + File.separator + "subdir" + File.separator + "inside.c"), false},
            {null, createSymlink(sourceRoot.getParentFile() + File.separator + "subdir", sourceRoot.getParentFile() + File.separator + "link"), false},
            {null, createSymlink(sourceRoot.getParentFile() + File.separator + "subdir", sourceRoot.getParentFile() + File.separator + "subdir" + File.separator + "link"), false},
            {null, createSymlink(sourceRoot.getParentFile().getAbsolutePath(), sourceRoot.getParentFile() + File.separator + "foolink"), false},
            {null, createSymlink(sourceRoot.getParentFile().getAbsolutePath(), sourceRoot.getParentFile() + File.separator + "subdir" + File.separator + "foolink"), false}
        };
        runLocalTests(sourceRoot, tests);
    }

    protected Project createProject(String path) {
        Project p = new Project();
        p.setDescription(path);
        p.setPath(path);
        return p;
    }

    protected File createSymlink(String target, String name) throws IOException {
        Path link = Files.createSymbolicLink(
                new File(name).toPath(),
                new File(target).toPath());
        return link.toFile();
    }

    protected File createFile(String path) throws IOException {
        return createFile(path, false);
    }

    protected File createDirectory(String path) throws IOException {
        return createFile(path, true);
    }

    protected File createFile(String path, boolean directory) throws IOException {
        File test = new File(path);
        if (!test.exists()) {
            if (!test.getParentFile().exists() && !test.getParentFile().mkdirs()) {
                throw new IOException("Could not create the path: " + test.getParentFile().getAbsolutePath());
            }
            if (directory) {
                if (!test.mkdir()) {
                    throw new IOException("Could not create the directory: " + test.getAbsolutePath());
                }
            } else {
                if (!test.createNewFile()) {
                    throw new IOException("Could not create the file: " + test.getAbsolutePath());
                }
            }
        }
        return test;
    }
}
