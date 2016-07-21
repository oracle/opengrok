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
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * Portions Copyright 2011 Jens Elkner.
 */
package org.opensolaris.opengrok.index;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.Info;
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryException;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.history.Repository;
import org.opensolaris.opengrok.history.RepositoryFactory;
import org.opensolaris.opengrok.history.RepositoryInfo;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.logger.LoggerUtil;
import org.opensolaris.opengrok.util.Executor;
import org.opensolaris.opengrok.util.Getopt;
import org.opensolaris.opengrok.util.Statistics;

/**
 * Creates and updates an inverted source index as well as generates Xref, file
 * stats etc., if specified in the options
 */
@SuppressWarnings({"PMD.AvoidPrintStackTrace", "PMD.SystemPrintln"})
public final class Indexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Indexer.class);

    /* tunables for -r (history for remote repositories) */
    private static final String ON = "on";
    private static final String OFF = "off";
    private static final String DIRBASED = "dirbased";
    private static final String UIONLY = "uionly";

    private static final Indexer index = new Indexer();
    private static final String DERBY_EMBEDDED_DRIVER
            = "org.apache.derby.jdbc.EmbeddedDriver";
    private static final String DERBY_CLIENT_DRIVER
            = "org.apache.derby.jdbc.ClientDriver";

    public static Indexer getInstance() {
        return index;
    }

    private static void A_usage() {
        System.err.println("ERROR: You must specify: -A .extension:class or -A prefix.:class");
        System.err.println("       Ex: -A .foo:org.opensolaris.opengrok.analysis.c.CAnalyzer");
        System.err.println("           will use the C analyzer for all files ending with .foo");
        System.err.println("       Ex: -A bar.:org.opensolaris.opengrok.analysis.c.CAnalyzer");
        System.err.println("           will use the C analyzer for all files starting with bar.");
        System.err.println("       Ex: -A .c:-");
        System.err.println("           will disable the c-analyzer for for all files ending with .c");
        System.exit(1);
    }

    /**
     * Program entry point
     *
     * @param argv argument vector
     */
    @SuppressWarnings("PMD.UseStringBufferForStringAppends")
    public static void main(String argv[]) {
        Statistics stats = new Statistics(); //this won't count JVM creation though
        boolean runIndex = true;
        boolean update = true;
        boolean optimizedChanged = false;
        ArrayList<String> zapCache = new ArrayList<>();
        CommandLineOptions cmdOptions = new CommandLineOptions();

        if (argv.length == 0) {
            System.err.println(cmdOptions.getUsage());
            System.exit(1);
        } else {
            Executor.registerErrorHandler();
            boolean searchRepositories = false;
            ArrayList<String> subFiles = new ArrayList<>();
            ArrayList<String> subFilesList = new ArrayList<>();
            ArrayList<String> repositories = new ArrayList<>();
            HashSet<String> allowedSymlinks = new HashSet<>();
            String configFilename = null;
            String configHost = null;
            boolean addProjects = false;
            boolean refreshHistory = false;
            String defaultProject = null;
            boolean listFiles = false;
            boolean listRepos = false;
            boolean createDict = false;
            int noThreads = 2 + (2 * Runtime.getRuntime().availableProcessors());

            // Parse command line options:
            Getopt getopt = new Getopt(argv, cmdOptions.getCommandString());

            try {
                getopt.parse();
            } catch (ParseException ex) {
                System.err.println("OpenGrok: " + ex.getMessage());
                System.err.println(cmdOptions.getUsage());
                System.exit(1);
            }

            try {
                Configuration cfg = null;
                int cmd;

                // We need to read the configuration file first, since we
                // will try to overwrite options..
                while ((cmd = getopt.getOpt()) != -1) {
                    if (cmd == 'R') {
                        cfg = Configuration.read(new File(getopt.getOptarg()));
                        break;
                    }
                }

                if (cfg == null) {
                    cfg = new Configuration();
                }

                String databaseDriver = cfg.getDatabaseDriver();
                String databaseURL = cfg.getDatabaseUrl();

                // Now we can handle all the other options..
                getopt.reset();
                while ((cmd = getopt.getOpt()) != -1) {
                    switch (cmd) {
                        case 'A': {
                            String[] arg = getopt.getOptarg().split(":");
                            boolean prefix = false;

                            if (arg.length != 2) {
                                A_usage();
                            }

                            if (arg[0].endsWith(".")) {
                                arg[0] = arg[0].substring(0, arg[0].lastIndexOf('.')).toUpperCase();
                                prefix = true;
                            } else if (arg[0].startsWith(".")) {
                                arg[0] = arg[0].substring(arg[0].lastIndexOf('.') + 1).toUpperCase();
                            } else {
                                A_usage();
                            }

                            if (arg[1].equals("-")) {
                                if (prefix) {
                                    AnalyzerGuru.addPrefix(arg[0], null);
                                } else {
                                    AnalyzerGuru.addExtension(arg[0], null);
                                }
                                break;
                            }

                            if (prefix) {
                                try {
                                    AnalyzerGuru.addPrefix(
                                            arg[0],
                                            AnalyzerGuru.findFactory(arg[1]));
                                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                                    LOGGER.log(Level.SEVERE, "Unable to use {0} as a FileAnalyzerFactory", arg[1]);
                                    LOGGER.log(Level.SEVERE, "Stack: ", e.fillInStackTrace());
                                    System.exit(1);
                                }
                            } else {
                                try {
                                    AnalyzerGuru.addExtension(
                                            arg[0],
                                            AnalyzerGuru.findFactory(arg[1]));
                                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                                    LOGGER.log(Level.SEVERE, "Unable to use {0} as a FileAnalyzerFactory", arg[1]);
                                    LOGGER.log(Level.SEVERE, "Stack: ", e.fillInStackTrace());
                                    System.exit(1);
                                }
                            }
                        }
                        break;
                        case 'a':
                            if (getopt.getOptarg().equalsIgnoreCase(ON)) {
                                cfg.setAllowLeadingWildcard(true);
                            } else if (getopt.getOptarg().equalsIgnoreCase(OFF)) {
                                cfg.setAllowLeadingWildcard(false);
                            } else {
                                System.err.println("ERROR: You should pass either \"on\" or \"off\" as argument to -a");
                                System.err.println("       Ex: \"-a on\" will allow a search to start with a wildcard");
                                System.err.println("           \"-a off\" will disallow a search to start with a wildcard");
                                System.exit(1);
                            }

                            break;
                        case 'B':
                            cfg.setUserPage(getopt.getOptarg());
                            break;
                        case 'C':
                            cfg.setPrintProgress(true);
                            break;
                        case 'c':
                            cfg.setCtags(getopt.getOptarg());
                            break;
                        case 'D':
                            cfg.setHistoryCacheInDB(true);
                            break;
                        case 'd': {
                            File dataRoot = new File(getopt.getOptarg());
                            if (!dataRoot.exists() && !dataRoot.mkdirs()) {
                                System.err.println("ERROR: Cannot create data root");
                                System.exit(1);
                            }
                            if (!dataRoot.isDirectory()) {
                                System.err.println("ERROR: Data root must be a directory");
                                System.exit(1);
                            }
                            cfg.setDataRoot(dataRoot.getCanonicalPath());
                            break;
                        }
                        case 'e':
                            cfg.setGenerateHtml(false);
                            break;
                        case 'G':
                            cfg.setTagsEnabled(true);
                            break;
                        case 'H':
                            refreshHistory = true;
                            break;
                        case 'h':
                            repositories.add(getopt.getOptarg());
                            break;
                        case 'I':
                            cfg.getIncludedNames().add(getopt.getOptarg());
                            break;
                        case 'i':
                            cfg.getIgnoredNames().add(getopt.getOptarg());
                            break;
                        case 'j':
                            databaseDriver = getopt.getOptarg();
                            // Should be a full class name, but we also accept
                            // the shorthands "client" and "embedded". Expand
                            // the shorthands here.
                            switch (databaseDriver) {
                                case "client":
                                    databaseDriver = DERBY_CLIENT_DRIVER;
                                    break;
                                case "embedded":
                                    databaseDriver = DERBY_EMBEDDED_DRIVER;
                                    break;
                            }
                            break;
                        case 'K':
                            listRepos = true;
                            break;
                        case 'k':
                            zapCache.add(getopt.getOptarg());
                            break;
                        case 'L':
                            cfg.setWebappLAF(getopt.getOptarg());
                            break;
                        case 'l':
                            if (getopt.getOptarg().equalsIgnoreCase(ON)) {
                                cfg.setUsingLuceneLocking(true);
                            } else if (getopt.getOptarg().equalsIgnoreCase(OFF)) {
                                cfg.setUsingLuceneLocking(false);
                            } else {
                                System.err.println("ERROR: You should pass either \"on\" or \"off\" as argument to -l");
                                System.err.println("       Ex: \"-l on\" will enable locks in Lucene");
                                System.err.println("           \"-l off\" will disable locks in Lucene");
                            }
                            break;
                        case 'm': {
                            try {
                                cfg.setRamBufferSize(Double.parseDouble(getopt.getOptarg()));
                            } catch (NumberFormatException exp) {
                                System.err.println("ERROR: Failed to parse argument to \"-m\": " + exp.getMessage());
                                System.exit(1);
                            }
                            break;
                        }
                        case 'N':
                            allowedSymlinks.add(getopt.getOptarg());
                            break;
                        case 'n':
                            runIndex = false;
                            break;
                        case 'O': {
                            boolean oldval = cfg.isOptimizeDatabase();
                            if (getopt.getOptarg().equalsIgnoreCase(ON)) {
                                cfg.setOptimizeDatabase(true);
                            } else if (getopt.getOptarg().equalsIgnoreCase(OFF)) {
                                cfg.setOptimizeDatabase(false);
                            } else {
                                System.err.println("ERROR: You should pass either \"on\" or \"off\" as argument to -O");
                                System.err.println("       Ex: \"-O on\" will optimize the database as part of the index generation");
                                System.err.println("           \"-O off\" disable optimization of the index database");
                            }
                            if (oldval != cfg.isOptimizeDatabase()) {
                                optimizedChanged = true;
                            }
                            break;
                        }
                        case 'o':
                            String CTagsExtraOptionsFile = getopt.getOptarg();
                            File CTagsFile = new File(CTagsExtraOptionsFile);
                            if (!(CTagsFile.isFile() && CTagsFile.canRead())) {
                                System.err.println("ERROR: File '"
                                        + CTagsExtraOptionsFile
                                        + "' not found for the -o option");
                                System.exit(1);
                            }
                            System.err.println("INFO: file with extra "
                                    + "options for ctags: " + CTagsExtraOptionsFile);
                            cfg.setCTagsExtraOptionsFile(CTagsExtraOptionsFile);
                            break;
                        case 'P':
                            addProjects = true;
                            break;
                        case 'p':
                            defaultProject = getopt.getOptarg();
                            break;
                        case 'Q':
                            if (getopt.getOptarg().equalsIgnoreCase(ON)) {
                                cfg.setQuickContextScan(true);
                            } else if (getopt.getOptarg().equalsIgnoreCase(OFF)) {
                                cfg.setQuickContextScan(false);
                            } else {
                                System.err.println("ERROR: You should pass either \"on\" or \"off\" as argument to -Q");
                                System.err.println("       Ex: \"-Q on\" will just scan a \"chunk\" of the file and insert \"[..all..]\"");
                                System.err.println("           \"-Q off\" will try to build a more accurate list by reading the complete file.");
                            }

                            break;
                        case 'q':
                            cfg.setVerbose(false);
                            LoggerUtil.setBaseConsoleLogLevel(Level.WARNING);
                            break;
                        case 'R':
                            // already handled
                            break;
                        case 'r':
                            if (getopt.getOptarg().equalsIgnoreCase(ON)) {
                                cfg.setRemoteScmSupported(Configuration.RemoteSCM.ON);
                            } else if (getopt.getOptarg().equalsIgnoreCase(OFF)) {
                                cfg.setRemoteScmSupported(Configuration.RemoteSCM.OFF);
                            } else if (getopt.getOptarg().equalsIgnoreCase(DIRBASED)) {
                                cfg.setRemoteScmSupported(Configuration.RemoteSCM.DIRBASED);
                            } else if (getopt.getOptarg().equalsIgnoreCase(UIONLY)) {
                                cfg.setRemoteScmSupported(Configuration.RemoteSCM.UIONLY);
                            } else {
                                System.err.println("ERROR: You should pass either \"on\" or \"off\" or \"uionly\" as argument to -r");
                                System.err.println("       Ex: \"-r on\" will allow retrieval for remote SCM systems");
                                System.err.println("           \"-r off\" will ignore SCM for remote systems");
                                System.err.println("           \"-r dirbased\" will allow retrieval during history index "
                                        + "only for repositories which allow getting history for directories");
                                System.err.println("           \"-r uionly\" will support remote SCM for UI only");
                            }
                            break;
                        case 'S':
                            searchRepositories = true;
                            break;
                        case 's': {
                            File sourceRoot = new File(getopt.getOptarg());
                            if (!sourceRoot.isDirectory()) {
                                System.err.println("ERROR: Source root "
                                        + getopt.getOptarg() + " must be a directory");
                                System.exit(1);
                            }
                            cfg.setSourceRoot(sourceRoot.getCanonicalPath());
                            break;
                        }
                        case 'T':
                            try {
                                noThreads = Integer.parseInt(getopt.getOptarg());
                            } catch (NumberFormatException exp) {
                                System.err.println("ERROR: Failed to parse argument to \"-T\": "
                                        + exp.getMessage());
                                System.exit(1);
                            }
                            break;
                        case 't':
                            try {
                                int tmp = Integer.parseInt(getopt.getOptarg());
                                cfg.setTabSize(tmp);
                            } catch (NumberFormatException exp) {
                                System.err.println("ERROR: Failed to parse argument to \"-t\": "
                                        + exp.getMessage());
                                System.exit(1);
                            }
                            break;
                        case 'U':
                            configHost = getopt.getOptarg();
                            break;
                        case 'u':
                            databaseURL = getopt.getOptarg();
                            break;
                        case 'V':
                            System.out.println(Info.getFullVersion());
                            System.exit(0);
                            break;
                        case 'v':
                            cfg.setVerbose(true);
                            LoggerUtil.setBaseConsoleLogLevel(Level.INFO);
                            break;
                        case 'W':
                            configFilename = getopt.getOptarg();
                            break;
                        case 'w': {
                            String webapp = getopt.getOptarg();
                            if (webapp.charAt(0) != '/' && !webapp.startsWith("http")) {
                                webapp = "/" + webapp;
                            }
                            if (webapp.endsWith("/")) {
                                cfg.setUrlPrefix(webapp + "s?");
                            } else {
                                cfg.setUrlPrefix(webapp + "/s?");
                            }
                        }
                        break;
                        case 'X':
                            cfg.setUserPageSuffix(getopt.getOptarg());
                            break;
                        case 'z':
                            try {
                                cfg.setScanningDepth(Integer.parseInt(getopt.getOptarg()));
                            } catch (NumberFormatException exp) {
                                System.err.println("ERROR: Failed to parse argument to \"-z\": "
                                        + exp.getMessage());
                                System.exit(1);
                            }
                            break;
                        case '?':
                            System.err.println(cmdOptions.getUsage());
                            System.exit(0);
                            break;
                        default:
                            System.err.println("Internal Error - Unimplemented cmdline option: " + (char) cmd);
                            System.exit(1);
                    }
                }

                List<Class<? extends Repository>> repositoryClasses
                        = RepositoryFactory.getRepositoryClasses();
                for (Class<? extends Repository> clazz : repositoryClasses) {
                    try {
                        Field f = clazz.getDeclaredField("CMD_PROPERTY_KEY");
                        Object key = f.get(null);
                        if (key != null) {
                            cfg.setRepoCmd(clazz.getCanonicalName(),
                                    System.getProperty(key.toString()));
                        }
                    } catch (Exception e) {
                        // don't care
                    }
                }

                //logging starts here
                if (cfg.isVerbose()) {
                    String fn = LoggerUtil.getFileHandlerPattern();
                    if (fn != null) {
                        System.out.println("Logging filehandler pattern: " + fn);
                    }
                }

                if (cfg.isHistoryCacheInDB()) {
                    // The default database driver is Derby's client driver.
                    if (databaseDriver == null) {
                        databaseDriver = DERBY_CLIENT_DRIVER;
                    }

                    // The default URL depends on the database driver.
                    if (databaseURL == null) {
                        StringBuilder defaultURL = new StringBuilder();
                        defaultURL.append("jdbc:derby:");
                        if (databaseDriver.equals(DERBY_EMBEDDED_DRIVER)) {
                            defaultURL.append(cfg.getDataRoot())
                                    .append(File.separator);
                        } else {
                            defaultURL.append("//localhost/");
                        }
                        defaultURL.append("cachedb;create=true");
                        databaseURL = defaultURL.toString();
                    }
                }

                cfg.setDatabaseDriver(databaseDriver);
                cfg.setDatabaseUrl(databaseURL);

                // automatically allow symlinks that are directly in source root
                String file = cfg.getSourceRoot();
                if (file != null) {
                    File sourceRootFile = new File(file);
                    File[] projectDirs = sourceRootFile.listFiles();
                    if (projectDirs != null) {
                        for (File projectDir : projectDirs) {
                            if (!projectDir.getCanonicalPath().equals(projectDir.getAbsolutePath())) {
                                allowedSymlinks.add(projectDir.getAbsolutePath());
                            }
                        }
                    }
                }

                allowedSymlinks.addAll(cfg.getAllowedSymlinks());
                cfg.setAllowedSymlinks(allowedSymlinks);

                // Assemble the unprocessed command line arguments (possibly
                // a list of paths). This will be used to perform more fine
                // grained checking in invalidateRepositories().
                int optind = getopt.getOptind();
                if (optind != -1) {
                    while (optind < argv.length) {
                        subFilesList.add(cfg.getSourceRoot() + argv[optind++]);
                    }
                }

                // Set updated configuration in RuntimeEnvironment.
                RuntimeEnvironment env = RuntimeEnvironment.getInstance();
                env.setConfiguration(cfg, subFilesList);

                /*
                 * Add paths to directories under source root. If projects
                 * are enabled the path should correspond to a project because
                 * project path is necessary to correctly set index directory
                 * (otherwise the index files will end up in index data root
                 * directory and not per project data root directory).
                 * For the check we need to have 'env' already set.
                 */
                for (String path : subFilesList) {
                    String srcPath=env.getSourceRootPath();
                    if (srcPath==null) { System.err.println("Error getting source root from environment. Exiting.");
                    System.exit(1);
                      }
                    path = path.substring(srcPath.length());
                    if (env.hasProjects()) {
                        // The paths need to correspond to a project.
                        if (Project.getProject(path) != null) {
                            subFiles.add(path);
                        } else {
                            System.err.println("The path " + path
                                    + " does not correspond to a project");
                        }
                    } else {
                        subFiles.add(path);
                    }
                }

                if (!subFilesList.isEmpty() && subFiles.isEmpty()) {
                    System.err.println("None of the paths were added, exiting");
                    System.exit(1);
                }

                // Issue a warning when JDBC is used with renamed file handling.
                // This causes heavy slowdown when used with JavaDB (issue #774).
                if (env.isHandleHistoryOfRenamedFiles() && cfg.isHistoryCacheInDB()) {
                    System.out.println("History stored in DB and renamed file "
                            + "handling is on - possible performance degradation");
                }

                // Get history first.
                getInstance().prepareIndexer(env, searchRepositories, addProjects,
                        defaultProject, configFilename, refreshHistory,
                        listFiles, createDict, subFiles, repositories,
                        zapCache, listRepos);
                if (listRepos || !zapCache.isEmpty()) {
                    return;
                }

                // And now index it all.
                if (runIndex || (optimizedChanged && env.isOptimizeDatabase())) {
                    IndexChangedListener progress = new DefaultIndexChangedListener();
                    getInstance().doIndexerExecution(update, noThreads, subFiles,
                            progress);
                }

                // Finally send new configuration to the web application.
                getInstance().sendToConfigHost(env, configHost);
            } catch (IndexerException ex) {
                LOGGER.log(Level.SEVERE, "Exception running indexer", ex);
                System.err.println(cmdOptions.getUsage());
                System.exit(1);
            } catch (Throwable e) {
                System.err.println("Exception: " + e.getLocalizedMessage());
                LOGGER.log(Level.SEVERE, "Unexpected Exception", e);
                System.exit(1);
            } finally {
                stats.report(LOGGER);
            }
        }
    }

    /*
     * This is the first phase of the indexing where history cache is being
     * generated for repositories (at least for those which support getting
     * history per directory).
     *
     * PMD wants us to use length() > 0 && charAt(0) instead of startsWith()
     * for performance. We prefer clarity over performance here, so silence it.
     */
    @SuppressWarnings("PMD.SimplifyStartsWith")
    public void prepareIndexer(RuntimeEnvironment env,
            boolean searchRepositories,
            boolean addProjects,
            String defaultProject,
            String configFilename,
            boolean refreshHistory,
            boolean listFiles,
            boolean createDict,
            List<String> subFiles,
            List<String> repositories,
            List<String> zapCache,
            boolean listRepoPaths) throws IndexerException, IOException {

        if (env.getDataRootPath() == null) {
            throw new IndexerException("ERROR: Please specify a DATA ROOT path");
        }

        if (env.getSourceRootFile() == null) {
            throw new IndexerException("ERROR: please specify a SRC_ROOT with option -s !");
        }

        if (zapCache.isEmpty() && !env.validateExuberantCtags()) {
            throw new IndexerException("Didn't find Exuberant Ctags");
        }
        if (zapCache == null) {
            throw new IndexerException("Internal error, zapCache shouldn't be null");
        }

        if (searchRepositories || listRepoPaths || !zapCache.isEmpty()) {
            LOGGER.log(Level.INFO, "Scanning for repositories...");
            long start = System.currentTimeMillis();
            if (refreshHistory == true) {
                HistoryGuru.getInstance().addRepositories(env.getSourceRootPath());
            }
            long time = (System.currentTimeMillis() - start) / 1000;
            LOGGER.log(Level.INFO, "Done scanning for repositories ({0}s)", time);
            if (listRepoPaths || !zapCache.isEmpty()) {
                List<RepositoryInfo> repos = env.getRepositories();
                String prefix = env.getSourceRootPath();
                if (listRepoPaths) {
                    if (repos.isEmpty()) {
                        System.out.println("No repositories found.");
                        return;
                    }
                    System.out.println("Repositories in " + prefix + ":");
                    for (RepositoryInfo info : env.getRepositories()) {
                        String dir = info.getDirectoryName();
                        System.out.println(dir.substring(prefix.length()));
                    }
                }
                if (!zapCache.isEmpty()) {
                    HashSet<String> toZap = new HashSet<>(zapCache.size() << 1);
                    boolean all = false;
                    for (String repo : zapCache) {
                        if ("*".equals(repo)) {
                            all = true;
                            break;
                        }
                        if (repo.startsWith(prefix)) {
                            repo = repo.substring(prefix.length());
                        }
                        toZap.add(repo);
                    }
                    if (all) {
                        toZap.clear();
                        for (RepositoryInfo info : env.getRepositories()) {
                            toZap.add(info.getDirectoryName()
                                    .substring(prefix.length()));
                        }
                    }
                    try {
                        HistoryGuru.getInstance().removeCache(toZap);
                    } catch (HistoryException e) {
                        LOGGER.log(Level.WARNING, "Clearing history cache failed: {0}", e.getLocalizedMessage());
                    }
                }
                return;
            }
        }

        if (addProjects) {
            File files[] = env.getSourceRootFile().listFiles();
            List<Project> projects = env.getProjects();

            // Keep a copy of the old project list so that we can preserve
            // the customization of existing projects.
            Map<String, Project> oldProjects = new HashMap<>();
            for (Project p : projects) {
                oldProjects.put(p.getPath(), p);
            }

            projects.clear();

            // Add a project for each top-level directory in source root.
            for (File file : files) {
                String name = file.getName();
                String path = "/" + name;
                if (oldProjects.containsKey(path)) {
                    // This is an existing object. Reuse the old project,
                    // possibly with customizations, instead of creating a
                    // new with default values.
                    projects.add(oldProjects.get(path));
                } else if (!name.startsWith(".") && file.isDirectory()) {
                    // Found a new directory with no matching project, so
                    // create a new project with default properties.
                    Project p = new Project();
                    p.setDescription(name);
                    p.setPath(path);
                    p.setTabSize(env.getConfiguration().getTabSize());
                    projects.add(p);
                }
            }

            // The projects should be sorted...
            Collections.sort(projects, new Comparator<Project>() {
                @Override
                public int compare(Project p1, Project p2) {
                    String s1 = p1.getDescription();
                    String s2 = p2.getDescription();

                    int ret;
                    if (s1 == null) {
                        ret = (s2 == null) ? 0 : 1;
                    } else {
                        ret = s1.compareTo(s2);
                    }
                    return ret;
                }
            });
        }

        if (defaultProject != null) {
            for (Project p : env.getProjects()) {
                if (p.getPath().equals(defaultProject)) {
                    env.setDefaultProject(p);
                    break;
                }
            }
        }

        if (configFilename != null) {
            LOGGER.log(Level.INFO, "Writing configuration to {0}", configFilename);
            env.writeConfiguration(new File(configFilename));
            LOGGER.info("Done...");
        }

         if (refreshHistory) {
             if (repositories != null && !repositories.isEmpty()) {
                  LOGGER.log(Level.INFO, "Generating history cache for specified repositories ...");
                  HistoryGuru.getInstance().createCache(repositories);
                  LOGGER.info("Done...");
              } else {
                  LOGGER.log(Level.INFO, "Generating history cache for all repositories ...");
                  HistoryGuru.getInstance().createCache();
                  LOGGER.info("Done...");
              }
        }
        if (listFiles) {
            IndexDatabase.listAllFiles(subFiles);
        }

        if (createDict) {
            IndexDatabase.listFrequentTokens(subFiles);
        }
    }

    /*
     * This is the second phase of the indexer which generates Lucene index
     * by passing source code files through Exuberant ctags, generating xrefs
     * and storing data from the source files in the index (along with history,
     * if any).
     */
    public void doIndexerExecution(final boolean update, int noThreads, List<String> subFiles,
            IndexChangedListener progress)
            throws IOException {
        Statistics elapsed = new Statistics();
        RuntimeEnvironment env = RuntimeEnvironment.getInstance().register();
        LOGGER.info("Starting indexing");

        ExecutorService executor = Executors.newFixedThreadPool(noThreads);

        if (subFiles == null || subFiles.isEmpty()) {
            if (update) {
                IndexDatabase.updateAll(executor, progress);
            } else if (env.isOptimizeDatabase()) {
                IndexDatabase.optimizeAll(executor);
            }
        } else {
            List<IndexDatabase> dbs = new ArrayList<>();

            for (String path : subFiles) {
                Project project = Project.getProject(path);
                if (project == null && env.hasProjects()) {
                    LOGGER.log(Level.WARNING, "Could not find a project for \"{0}\"", path);
                } else {
                    IndexDatabase db;
                    if (project == null) {
                        db = new IndexDatabase();
                    } else {
                        db = new IndexDatabase(project);
                    }
                    int idx = dbs.indexOf(db);
                    if (idx != -1) {
                        db = dbs.get(idx);
                    }

                    if (db.addDirectory(path)) {
                        if (idx == -1) {
                            dbs.add(db);
                        }
                    } else {
                        LOGGER.log(Level.WARNING, "Directory does not exist \"{0}\"", path);
                    }
                }
            }

            for (final IndexDatabase db : dbs) {
                final boolean optimize = env.isOptimizeDatabase();
                db.addIndexChangedListener(progress);
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (update) {
                                db.update();
                            } else if (optimize) {
                                db.optimize();
                            }
                        } catch (Throwable e) {
                            LOGGER.log(Level.SEVERE, "An error occured while "
                                    + (update ? "updating" : "optimizing")
                                    + " index", e);
                        }
                    }
                });
            }
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            try {
                // Wait forever
                executor.awaitTermination(999, TimeUnit.DAYS);
            } catch (InterruptedException exp) {
                LOGGER.log(Level.WARNING, "Received interrupt while waiting for executor to finish", exp);
            }
        }
        try {
            // It can happen that history index is not done in prepareIndexer()
            // but via db.update() above in which case we must make sure the
            // thread pool for renamed file handling is destroyed.
            RuntimeEnvironment.destroyRenamedHistoryExecutor();
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE,
                    "destroying of renamed thread pool failed", ex);
        }
        elapsed.report(LOGGER, "Done indexing data of all repositories");
    }

    public void sendToConfigHost(RuntimeEnvironment env, String configHost) {
        if (configHost != null) {
            String[] cfg = configHost.split(":");
            LOGGER.log(Level.INFO, "Send configuration to: {0}", configHost);
            if (cfg.length == 2) {
                try {
                    InetAddress host = InetAddress.getByName(cfg[0]);
                    env.writeConfiguration(host, Integer.parseInt(cfg[1]));
                } catch (NumberFormatException | IOException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to send configuration to "
                            + configHost + " (is web application server running with opengrok deployed?)", ex);
                }
            } else {
                LOGGER.severe("Syntax error: ");
                for (String s : cfg) {
                    LOGGER.log(Level.SEVERE, "[{0}]", s);
                }
            }
            LOGGER.info("Configuration update routine done, check log output for errors.");
        }
    }

    private Indexer() {
    }
}
