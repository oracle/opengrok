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
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * Portions Copyright 2011 Jens Elkner.
 */
package org.opensolaris.opengrok.index;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.opensolaris.opengrok.Info;
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.messages.Message;
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
            boolean getHistory = false;
            Set<String> defaultProjects = new TreeSet<>();
            boolean listFiles = false;
            boolean listRepos = false;
            boolean createDict = false;
            int noThreads = 2 + (2 * Runtime.getRuntime().availableProcessors());
            String host = null;
            int port = 0;
            boolean noindex = false;

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
                        case 'D':
                            cfg.setHandleHistoryOfRenamedFiles(getopt.getOptarg().
                                    equals("on"));
                            break;
                        case 'e':
                            cfg.setGenerateHtml(false);
                            break;
                        case 'G':
                            cfg.setTagsEnabled(true);
                            break;
                        case 'H':
                            getHistory = true;
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
                            cfg.setCTagsExtraOptionsFile(CTagsExtraOptionsFile);
                            break;
                        case 'P':
                            addProjects = true;
                            cfg.setProjectsEnabled(true);
                            break;
                        case 'p':
                            defaultProjects.add(getopt.getOptarg());
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
                                System.exit(1);
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
                        case 'y':
                            noindex = true;
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

                RuntimeEnvironment env = RuntimeEnvironment.getInstance();
                cfg.setHistoryEnabled(getHistory);

                if (configHost != null) {
                    String[] configHostArray = configHost.split(":");
                    if (configHostArray.length == 2) {
                        host = configHostArray[0];
                        try {
                            port = Integer.parseInt(configHostArray[1]);
                        } catch (NumberFormatException ex) {
                            System.err.println("Failed to parse: " + configHost);
                            System.exit(1);
                        }
                    } else {
                        System.err.println("Syntax error: ");
                        for (String s : configHostArray) {
                            System.err.println(s);
                        }
                        System.exit(1);
                    }

                    env.setConfigHost(host);
                    env.setConfigPort(port);
                }

                // Complete the configuration of repository types.
                List<Class<? extends Repository>> repositoryClasses
                        = RepositoryFactory.getRepositoryClasses();
                for (Class<? extends Repository> clazz : repositoryClasses) {
                    // Set external repository binaries from System properties.
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

                // Logging starts here.
                if (cfg.isVerbose()) {
                    String fn = LoggerUtil.getFileHandlerPattern();
                    if (fn != null) {
                        System.out.println("Logging filehandler pattern: " + fn);
                    }
                }

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
                        String path = Paths.get(cfg.getSourceRoot(), argv[optind++]).toString();
                        subFilesList.add(path);
                    }
                }

                // Set updated configuration in RuntimeEnvironment.
                env.setConfiguration(cfg, subFilesList);

                // Let repository types to add items to ignoredNames.
                // This changes env so is called after the setConfiguration()
                // call above.
                RepositoryFactory.initializeIgnoredNames(env);

                if (noindex) {
                    getInstance().sendToConfigHost(env, host, port);
                    writeConfigToFile(env, configFilename);
                    System.exit(0);
                }

                /*
                 * Add paths to directories under source root. If projects
                 * are enabled the path should correspond to a project because
                 * project path is necessary to correctly set index directory
                 * (otherwise the index files will end up in index data root
                 * directory and not per project data root directory).
                 * For the check we need to have 'env' already set.
                 */
                for (String path : subFilesList) {
                    String srcPath = env.getSourceRootPath();
                    if (srcPath == null) {
                        System.err.println("Error getting source root from environment. Exiting.");
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

                // If the webapp is running with a config that does not contain
                // 'projectsEnabled' property (case of upgrade or transition
                // from project-less config to one with projects), set the property
                // using a message so that the 'project/indexed' messages
                // emitted during indexing do not cause validation error.
                if (addProjects && host != null && port > 0) {
                    Message m = Message.createMessage("config");
                    m.addTag("set");
                    m.setText("projectsEnabled = true");
                    m.write(host, port);
                }

                // Get history first.
                getInstance().prepareIndexer(env, searchRepositories, addProjects,
                        defaultProjects,
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

                writeConfigToFile(env, configFilename);

                // Finally ping webapp to refresh indexes in the case of partial reindex
                // or send new configuration to the web application in the case of full reindex.
                if (host != null) {
                    if (!subFiles.isEmpty()) {
                        getInstance().refreshSearcherManagers(env, subFiles, host, port);
                    } else {
                        getInstance().sendToConfigHost(env, host, port);
                    }
                }
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

    /**
     * Write configuration to a file
     * @param env runtime environment
     * @param filename file name to write the configuration to
     */
    public static void writeConfigToFile(RuntimeEnvironment env, String filename) throws IOException {
        if (filename != null) {
            LOGGER.log(Level.INFO, "Writing configuration to {0}", filename);
            env.writeConfiguration(new File(filename));
            LOGGER.info("Done...");
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
            Set<String> defaultProjects,
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
            if (env.isHistoryEnabled()) {
                env.setRepositories(env.getSourceRootPath());
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
                        LOGGER.log(Level.WARNING, "Clearing history cache failed: {0}",
                                e.getLocalizedMessage());
                    }
                }
                return;
            }
        }

        if (addProjects) {
            File files[] = env.getSourceRootFile().listFiles();
            Map<String,Project> projects = env.getProjects();

            // Keep a copy of the old project list so that we can preserve
            // the customization of existing projects.
            Map<String, Project> oldProjects = new HashMap<>();
            for (Project p : projects.values()) {
                oldProjects.put(p.getName(), p);
            }

            projects.clear();

            // Add a project for each top-level directory in source root.
            for (File file : files) {
                String name = file.getName();
                String path = "/" + name;
                if (oldProjects.containsKey(name)) {
                    // This is an existing object. Reuse the old project,
                    // possibly with customizations, instead of creating a
                    // new with default values.
                    projects.put(name, oldProjects.get(name));
                } else if (!name.startsWith(".") && file.isDirectory()) {
                    // Found a new directory with no matching project, so
                    // create a new project with default properties.
                    Project p = new Project(name, path);
                    p.setTabSize(env.getConfiguration().getTabSize());
                    projects.put(p.getName(), p);
                }
            }
        }

        if (defaultProjects != null && !defaultProjects.isEmpty()) {
            Set<Project> projects = new TreeSet<>();
            for (String projectPath : defaultProjects) {
                if (projectPath.equals("__all__")) {
                    projects.addAll(env.getProjects().values());
                    break;
                }
                for (Project p : env.getProjectList()) {
                    if (p.getPath().equals(projectPath)) {
                        projects.add(p);
                        break;
                    }
                }
            }
            if (!projects.isEmpty()) {
                env.setDefaultProjects(projects);
            }
        }

        if (env.isHistoryEnabled()) {
            if (repositories != null && !repositories.isEmpty()) {
                LOGGER.log(Level.INFO, "Generating history cache for repositories: " +
                    repositories.stream().collect(Collectors.joining(",")));
                HistoryGuru.getInstance().createCache(repositories);
                LOGGER.info("Done...");
              } else {
                  LOGGER.log(Level.INFO, "Generating history cache for all repositories ...");
                  HistoryGuru.getInstance().createCache();
                  LOGGER.info("Done...");
              }
        }

        if (listFiles) {
            for (String file : IndexDatabase.getAllFiles(subFiles)) {
                LOGGER.fine(file);
            }
        }

        if (createDict) {
            IndexDatabase.listFrequentTokens(subFiles);
        }
    }

    /**
     * This is the second phase of the indexer which generates Lucene index
     * by passing source code files through ctags, generating xrefs
     * and storing data from the source files in the index (along with history,
     * if any).
     * 
     * @param update if set to true, index database is updated, otherwise optimized
     * @param noThreads number of threads in the pool that participate in the indexing
     * @param subFiles index just some subdirectories
     * @param progress object to receive notifications as indexer progress is made
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

    public void refreshSearcherManagers(RuntimeEnvironment env, List<String> projects, String host, int port) {
        LOGGER.log(Level.INFO, "Refreshing searcher managers to: {0}", host);
        try {
            env.signalTorefreshSearcherManagers(projects, host, port);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to refresh searcher managers on " + host, ex);
        }
    }

    public void sendToConfigHost(RuntimeEnvironment env, String host, int port) {
        LOGGER.log(Level.INFO, "Sending configuration to: {0}:{1}", new Object[]{host, Integer.toString(port)});
        try {
            env.writeConfiguration(host, port);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, String.format(
                    "Failed to send configuration to %s:%d "
                    + "(is web application server running with opengrok deployed?)", host, port), ex);
        }
        LOGGER.info("Configuration update routine done, check log output for errors.");
    }

    private Indexer() {
    }
}
