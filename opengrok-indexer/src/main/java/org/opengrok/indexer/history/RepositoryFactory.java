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
 * Copyright (c) 2008, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.Configuration;
import org.opengrok.indexer.configuration.IgnoredNames;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.ForbiddenSymlinkException;

/**
 * This is a factory class for the different repositories.
 *
 * @author austvik
 */
public final class RepositoryFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryFactory.class);

    private static final Repository[] repositories = {
            /*
             * The following do cheap checks to determine isRepositoryFor(),
             * but still put the most popular at the head of the repositories
             * array.
             */
            new GitRepository(),
            new MercurialRepository(),
            new RepoRepository(),
            new BitKeeperRepository(),
            new BazaarRepository(),
            new MonotoneRepository(),
            new SubversionRepository(),
            new SCCSRepository(),
            new RazorRepository(),
            new RCSRepository(),
            new CVSRepository(),
            new SSCMRepository(),
            /*
             * The following do expensive checks to determine isRepositoryFor(),
             * so put them at the end of the repositories array.
             */
            new AccuRevRepository(),
            new ClearCaseRepository(),
            new PerforceRepository()
    };

    private static final Map<String, Class<? extends Repository>> byName = new HashMap<>();

    static {
        final String REPOSITORY = "Repository";
        for (Repository repository : repositories) {
            Class<? extends Repository> clazz = repository.getClass();
            String repoName = clazz.getSimpleName();
            byName.put(repoName, clazz);
            byName.put(repoName.toLowerCase(Locale.ROOT), clazz);
            if (repoName.endsWith(REPOSITORY)) {
                String shortName = repoName.substring(0, repoName.length() - REPOSITORY.length());
                if (shortName.length() > 0) {
                    byName.put(shortName, clazz);
                    byName.put(shortName.toLowerCase(Locale.ROOT), clazz);
                }
            }
        }
    }

    /** Private to enforce static. */
    private RepositoryFactory() {
    }

    /**
     * Get a list of all available repository handlers.
     *
     * @return a list that contains non-{@code null} values only
     */
    public static List<Class<? extends Repository>> getRepositoryClasses() {
        ArrayList<Class<? extends Repository>> list = new ArrayList<>(repositories.length);
        for (int i = repositories.length - 1; i >= 0; i--) {
            Class<? extends Repository> clazz = repositories[i].getClass();
            if (isEnabled(clazz)) {
                list.add(clazz);
            }
        }

        return list;
    }

    /**
     * Gets a list of all disabled repository handlers.
     * @return a list that contains non-{@code null} values only
     */
    public static List<Class<? extends Repository>> getDisabledRepositoryClasses() {
        ArrayList<Class<? extends Repository>> list = new ArrayList<>();
        for (int i = repositories.length - 1; i >= 0; i--) {
            Class<? extends Repository> clazz = repositories[i].getClass();
            if (!isEnabled(clazz)) {
                list.add(clazz);
            }
        }

        return list;
    }

    /**
     * Calls {@link #getRepository(File, CommandTimeoutType)} with {@code file} and {@code false}.
     */
    public static Repository getRepository(File file)
            throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException,
            IOException, ForbiddenSymlinkException {
        return getRepository(file, CommandTimeoutType.INDEXER);
    }

    /**
     * Calls {@link #getRepository(File, CommandTimeoutType, boolean)} with {@code file}, {@code interactive}, and {@code false}.
     * @param file file
     * @param cmdType command timeout type
     * @return repository object
     */
    public static Repository getRepository(File file, CommandTimeoutType cmdType)
            throws IllegalAccessException, InvocationTargetException, ForbiddenSymlinkException, InstantiationException,
            NoSuchMethodException, IOException {
        return getRepository(file, cmdType, false);
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
     * @param cmdType command timeout type
     * @param isNested a value indicating if a nestable {@link Repository} is required
     * @return Correct repository for the given file
     * @throws InstantiationException in case we cannot create the repository object
     * @throws IllegalAccessException in case no permissions to repository file
     * @throws NoSuchMethodException in case we cannot create the repository object
     * @throws InvocationTargetException in case we cannot create the repository object
     * @throws IOException when resolving repository path
     * @throws ForbiddenSymlinkException when resolving repository path
     */
    public static Repository getRepository(File file, CommandTimeoutType cmdType, boolean isNested)
            throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException,
            IOException, ForbiddenSymlinkException {

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String relFile = env.getPathRelativeToSourceRoot(file);

        Repository repo = null;
        for (Repository referenceRepo : repositories) {
            Class<? extends Repository> clazz = referenceRepo.getClass();

            if ((!isNested || referenceRepo.isNestable()) && isEnabled(clazz) &&
                    referenceRepo.isRepositoryFor(file, cmdType)) {
                repo = clazz.getDeclaredConstructor().newInstance();

                if (env.isProjectsEnabled() && relFile.equals(File.separator)) {
                    LOGGER.log(Level.WARNING, "{0} was detected as {1} repository however with directory " +
                            "matching source root. This is invalid because projects are enabled. Ignoring this " +
                            "repository.",
                            new Object[]{file, repo.getType()});
                    return null;
                }
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
                        repo.setParent(repo.determineParent(cmdType));
                    } catch (IOException ex) {
                        LOGGER.log(Level.WARNING,
                                "Failed to get parent for {0}: {1}",
                                new Object[]{file.getAbsolutePath(), ex});
                    }
                }

                if (repo.getBranch() == null || repo.getBranch().length() == 0) {
                    try {
                        repo.setBranch(repo.determineBranch(cmdType));
                    } catch (IOException ex) {
                        LOGGER.log(Level.WARNING,
                                "Failed to get branch for {0}: {1}",
                                new Object[]{file.getAbsolutePath(), ex});
                    }
                }

                if (repo.getCurrentVersion() == null || repo.getCurrentVersion().length() == 0) {
                    try {
                        repo.setCurrentVersion(repo.determineCurrentVersion(cmdType));
                    } catch (IOException ex) {
                        LOGGER.log(Level.WARNING,
                                "Failed to determineCurrentVersion for {0}: {1}",
                                new Object[]{file.getAbsolutePath(), ex});
                    }
                }

                // If this repository displays tags only for files changed by tagged
                // revision, we need to prepare list of all tags in advance.
                if (env.isTagsEnabled() && repo.hasFileBasedTags()) {
                    repo.buildTagList(file, cmdType);
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
     * @param cmdType command timeout type
     * @return Correct repository for the given file
     * @throws InstantiationException in case we cannot create the repository object
     * @throws IllegalAccessException in case no permissions to repository
     * @throws NoSuchMethodException in case we cannot create the repository object
     * @throws InvocationTargetException in case we cannot create the repository object
     * @throws IOException when resolving repository path
     * @throws ForbiddenSymlinkException when resolving repository path
     */
    public static Repository getRepository(RepositoryInfo info, CommandTimeoutType cmdType)
            throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException,
            IOException, ForbiddenSymlinkException {
        return getRepository(new File(info.getDirectoryName()), cmdType);
    }

    /**
     * Go through all supported repository types, and add their ignored items to
     * the environment's lists of ignored files/directories -- but skip any
     * repository types which are named in
     * {@link RuntimeEnvironment#getDisabledRepositories()} ()}. This way
     * per-repository ignored entries are set inside repository classes rather
     * than globally in IgnoredFiles/Dirs.
     * <p>
     * (Should be called after
     * {@link RuntimeEnvironment#setConfiguration(Configuration)}.)
     */
    public static void initializeIgnoredNames(RuntimeEnvironment env) {
        IgnoredNames ignoredNames = env.getIgnoredNames();
        for (Repository repo : repositories) {
            if (isEnabled(repo.getClass())) {
                for (String file : repo.getIgnoredFiles()) {
                    ignoredNames.add("f:" + file);
                }
                for (String dir : repo.getIgnoredDirs()) {
                    ignoredNames.add("d:" + dir);
                }
            }
        }
    }

    /**
     * Tries to match a supported repositories by name or nickname -- e.g.
     * {@code "CVSRepository"} or {@code "CVS"} or {@code "cvs"}.
     * @return a defined, class simple name (e.g. {@code "CVSRepository"} when
     * {@code "cvs"} is passed); or {@code null} if no match found
     */
    public static String matchRepositoryByName(String name) {
        Class<? extends Repository> clazz = byName.get(name);
        if (clazz != null) {
            return clazz.getSimpleName();
        }
        return null;
    }

    private static boolean isEnabled(Class<? extends Repository> clazz) {
        Set<String> disabledRepos = RuntimeEnvironment.getInstance().getDisabledRepositories();
        return disabledRepos == null || !disabledRepos.contains(clazz.getSimpleName());
    }
}
