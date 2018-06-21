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
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;

/**
 * This is a factory class for the different repositories.
 *
 * @author austvik
 */
public final class RepositoryFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryFactory.class);

    private static final Repository repositories[] = {
        new BitKeeperRepository(),
        new MercurialRepository(),
        new AccuRevRepository(),
        new BazaarRepository(),
        new GitRepository(),
        new MonotoneRepository(),
        new SubversionRepository(),
        new SCCSRepository(),
        new RazorRepository(),
        new ClearCaseRepository(),
        new PerforceRepository(),
        new RCSRepository(),
        new CVSRepository(),
        new RepoRepository(),
        new SSCMRepository(),};

    private RepositoryFactory() {
        // Factory class, should not be constructed
    }

    /**
     * Get a list of all available repository handlers.
     *
     * @return a list which contains none-{@code null} values, only.
     */
    public static List<Class<? extends Repository>> getRepositoryClasses() {
        ArrayList<Class<? extends Repository>> list
                = new ArrayList<>(repositories.length);
        for (int i = repositories.length - 1; i >= 0; i--) {
            list.add(repositories[i].getClass());
        }
        
        return list;
    }

    public static Repository getRepository(File file)
        throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException{
        return getRepository(file, false);
    }
    
    /**
     * Returns a repository for the given file, or null if no repository was
     * found.
     *
     * Note that the operations performed by this method take quite a long time
     * thanks to external commands being executed. For that reason, when run
     * on multiple files, it should be parallelized (e.g. like it is done in
     * {@code invalidateRepositories()}) and the commands run within should
     * use interactive command timeout (as specified in {@code Configuration}).
     *
     * @param file File that might contain a repository
     * @param interactive true if running in interactive mode
     * @return Correct repository for the given file
     * @throws InstantiationException in case we cannot create the repository object
     * @throws IllegalAccessException in case no permissions to repository file
     * @throws NoSuchMethodException in case we cannot create the repository object
     * @throws InvocationTargetException in case we cannot create the repository object
     */
    public static Repository getRepository(File file, boolean interactive)
            throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        Repository repo = null;

        for (Repository rep : repositories) {
            if (rep.isRepositoryFor(file, interactive)) {
                repo = rep.getClass().getDeclaredConstructor().newInstance();
                repo.setDirectoryName(file);

                if (!repo.isWorking()) {
                    LOGGER.log(Level.WARNING,
                            "{0} not working (missing binaries?): {1}",
                            new Object[]{
                                repo.getClass().getSimpleName(),
                                file.getPath()
                            });
                }

                if (repo.getType() == null || repo.getType().length() == 0) {
                    repo.setType(repo.getClass().getSimpleName());
                }

                if (repo.getParent() == null || repo.getParent().length() == 0) {
                    try {
                        repo.setParent(repo.determineParent(interactive));
                    } catch (IOException ex) {
                        LOGGER.log(Level.WARNING,
                                "Failed to get parent for {0}: {1}",
                                new Object[]{file.getAbsolutePath(), ex});
                    }
                }

                if (repo.getBranch() == null || repo.getBranch().length() == 0) {
                    try {
                        repo.setBranch(repo.determineBranch(interactive));
                    } catch (IOException ex) {
                        LOGGER.log(Level.WARNING,
                                "Failed to get branch for {0}: {1}",
                                new Object[]{file.getAbsolutePath(), ex});
                    }
                }

                if (repo.getCurrentVersion() == null || repo.getCurrentVersion().length() == 0) {
                    try {
                        repo.setCurrentVersion(repo.determineCurrentVersion(interactive));
                    } catch (IOException ex) {
                        LOGGER.log(Level.WARNING,
                                "Failed to determineCurrentVersion for {0}: {1}",
                                new Object[]{file.getAbsolutePath(), ex});
                    }
                }

                // If this repository displays tags only for files changed by tagged
                // revision, we need to prepare list of all tags in advance.
                if (env.isTagsEnabled() && repo.hasFileBasedTags()) {
                    repo.buildTagList(file, interactive);
                }

                repo.fillFromProject();
                
                break;
            }
        }
        
        return repo;
    }

    /**
     * Returns a repository for the given file, or null if no repository was
     * found.
     *
     * @param info Information about the repository
     * @param interactive true if used in interactive mode
     * @return Correct repository for the given file
     * @throws InstantiationException in case we cannot create the repository object
     * @throws IllegalAccessException in case no permissions to repository
     * @throws NoSuchMethodException in case we cannot create the repository object
     * @throws InvocationTargetException in case we cannot create the repository object
     */
    public static Repository getRepository(RepositoryInfo info, boolean interactive)
            throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        return getRepository(new File(info.getDirectoryName()), interactive);
    }

    /**
     * Go through all repository types and add items to lists of ignored
     * files/directories. This way per-repository ignored entries are set
     * inside repository classes rather than globally in IgnoredFiles/Dirs.
     * Should be called after {@code setConfiguration()}.
     * 
     * @param env runtime environment
     */
    public static void initializeIgnoredNames(RuntimeEnvironment env) {
        for (Repository repo : repositories) {
            for (String file : repo.getIgnoredFiles()) {
                env.getIgnoredNames().add("f:" + file);
            }

            for (String dir : repo.getIgnoredDirs()) {
                env.getIgnoredNames().add("d:" + dir);
            }
        }
    }
}
