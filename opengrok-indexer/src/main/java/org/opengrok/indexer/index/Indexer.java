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
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2011, Jens Elkner.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SystemUtils;
import org.opengrok.indexer.Info;
import org.opengrok.indexer.Metrics;
import org.opengrok.indexer.analysis.AnalyzerGuru;
import org.opengrok.indexer.analysis.AnalyzerGuruHelp;
import org.opengrok.indexer.analysis.Ctags;
import org.opengrok.indexer.configuration.CanonicalRootValidator;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.Configuration;
import org.opengrok.indexer.configuration.ConfigurationHelp;
import org.opengrok.indexer.configuration.LuceneLockName;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.RepositoriesHelp;
import org.opengrok.indexer.history.Repository;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.history.RepositoryInfo;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.logger.LoggerUtil;
import org.opengrok.indexer.util.CtagsUtil;
import org.opengrok.indexer.util.Executor;
import org.opengrok.indexer.util.HostUtil;
import org.opengrok.indexer.util.OptionParser;
import org.opengrok.indexer.util.Statistics;

/**
 * Creates and updates an inverted source index as well as generates Xref, file
 * stats etc., if specified in the options.
 *
 * We shall use / as path delimiter in whole opengrok for uuids and paths
 * from Windows systems, the path shall be converted when entering the index or web
 * and converted back if needed* to access original file
 *
 * *Windows already supports opening /var/opengrok as C:\var\opengrok
 */
@SuppressWarnings({"PMD.AvoidPrintStackTrace", "PMD.SystemPrintln"})
public final class Indexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Indexer.class);

    /* tunables for -r (history for remote repositories) */
    private static final String ON = "on";
    private static final String OFF = "off";
    private static final String DIRBASED = "dirbased";
    private static final String UIONLY = "uionly";

    //whole app uses this separator
    public static final char PATH_SEPARATOR = '/';
    public static final String PATH_SEPARATOR_STRING = Character.toString(PATH_SEPARATOR);

    private static final String HELP_OPT_1 = "--help";
    private static final String HELP_OPT_2 = "-?";
    private static final String HELP_OPT_3 = "-h";

    private static final Indexer indexer = new Indexer();
    private static Configuration cfg = null;
    private static boolean checkIndex = false;
    private static boolean runIndex = true;
    private static boolean optimizedChanged = false;
    private static boolean addProjects = false;
    private static boolean searchRepositories = false;
    private static boolean bareConfig = false;
    private static boolean awaitProfiler;

    private static boolean help;
    private static String helpUsage;
    private static HelpMode helpMode = HelpMode.DEFAULT;

    private static String configFilename = null;
    private static int status = 0;

    private static final Set<String> repositories = new HashSet<>();
    private static Set<String> searchPaths = new HashSet<>();
    private static final HashSet<String> allowedSymlinks = new HashSet<>();
    private static final HashSet<String> canonicalRoots = new HashSet<>();
    private static final Set<String> defaultProjects = new TreeSet<>();
    private static final HashSet<String> disabledRepositories = new HashSet<>();
    private static RuntimeEnvironment env = null;
    private static String webappURI = null;

    private static OptionParser optParser = null;
    private static boolean verbose = false;

    private static final String[] ON_OFF = {ON, OFF};
    private static final String[] REMOTE_REPO_CHOICES = {ON, OFF, DIRBASED, UIONLY};
    private static final String[] LUCENE_LOCKS = {ON, OFF, "simple", "native"};
    private static final String OPENGROK_JAR = "opengrok.jar";

    private static final int WEBAPP_CONNECT_TIMEOUT = 1000;  // in milliseconds

    public static Indexer getInstance() {
        return indexer;
    }

    /**
     * Program entry point.
     *
     * @param argv argument vector
     */
    @SuppressWarnings("PMD.UseStringBufferForStringAppends")
    public static void main(String[] argv) {
        Statistics stats = new Statistics(); //this won't count JVM creation though
        boolean update = true;

        Executor.registerErrorHandler();
        List<String> subFiles = RuntimeEnvironment.getInstance().getSubFiles();
        Set<String> subFilesArgs = new HashSet<>();

        boolean createDict = false;

        try {
            argv = parseOptions(argv);

            if (webappURI != null && !HostUtil.isReachable(webappURI, WEBAPP_CONNECT_TIMEOUT)) {
                System.err.println(webappURI + " is not reachable.");
                System.exit(1);
            }

            /*
             * Attend to disabledRepositories here in case exitWithHelp() will
             * need to report about repos.
             */
            disabledRepositories.addAll(cfg.getDisabledRepositories());
            cfg.setDisabledRepositories(disabledRepositories);
            for (String repoName : disabledRepositories) {
                LOGGER.log(Level.FINEST, "Disabled {0}", repoName);
            }

            if (help) {
                exitWithHelp();
            }

            checkConfiguration();

            if (awaitProfiler) {
                pauseToAwaitProfiler();
            }

            env = RuntimeEnvironment.getInstance();
            env.setIndexer(true);

            // Complete the configuration of repository types.
            List<Class<? extends Repository>> repositoryClasses = RepositoryFactory.getRepositoryClasses();
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
            if (verbose) {
                String fn = LoggerUtil.getFileHandlerPattern();
                if (fn != null) {
                    System.out.println("Logging filehandler pattern: " + fn);
                }
            }

            // automatically allow symlinks that are directly in source root
            File sourceRootFile = new File(cfg.getSourceRoot());
            File[] projectDirs = sourceRootFile.listFiles();
            if (projectDirs != null) {
                for (File projectDir : projectDirs) {
                    if (!projectDir.getCanonicalPath().equals(projectDir.getAbsolutePath())) {
                        allowedSymlinks.add(projectDir.getAbsolutePath());
                    }
                }
            }

            allowedSymlinks.addAll(cfg.getAllowedSymlinks());
            cfg.setAllowedSymlinks(allowedSymlinks);

            canonicalRoots.addAll(cfg.getCanonicalRoots());
            cfg.setCanonicalRoots(canonicalRoots);

            // Assemble the unprocessed command line arguments (possibly a list of paths).
            // This will be used to perform more fine-grained checking in invalidateRepositories()
            // called from the setConfiguration() below.
            for (String arg : argv) {
                String path = Paths.get(cfg.getSourceRoot(), arg).toString();
                subFilesArgs.add(path);
            }

            // If a user used customizations for projects he perhaps just
            // used the key value for project without a name but the code
            // expects a name for the project. Therefore, we fill the name
            // according to the project key which is the same.
            for (Entry<String, Project> entry : cfg.getProjects().entrySet()) {
                if (entry.getValue().getName() == null) {
                    entry.getValue().setName(entry.getKey());
                }
            }

            // Check version of index(es) versus current Lucene version and exit
            // with return code upon failure.
            if (checkIndex) {
                if (cfg.getDataRoot() == null || cfg.getDataRoot().isEmpty()) {
                    System.err.println("Need data root in configuration for index check (use -R)");
                    System.exit(1);
                }

                if (!IndexCheck.check(cfg, subFilesArgs)) {
                    System.err.printf("Index check failed%n");
                    System.err.print("You might want to remove " +
                            (!subFilesArgs.isEmpty() ? "data for projects " + String.join(",", subFilesArgs) :
                                    "all data") + " under the data root and reindex\n");
                    System.exit(1);
                }

                System.exit(0);
            }

            // Set updated configuration in RuntimeEnvironment. This is called so that the tunables set
            // via command line options are available.
            env.setConfiguration(cfg, subFilesArgs, CommandTimeoutType.INDEXER);

            // Let repository types to add items to ignoredNames.
            // This changes env so is called after the setConfiguration()
            // call above.
            RepositoryFactory.initializeIgnoredNames(env);

            if (bareConfig) {
                // Set updated configuration in RuntimeEnvironment.
                env.setConfiguration(cfg, subFilesArgs, CommandTimeoutType.INDEXER);

                getInstance().sendToConfigHost(env, webappURI);
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
            for (String path : subFilesArgs) {
                String srcPath = env.getSourceRootPath();
                if (srcPath == null) {
                    System.err.println("Error getting source root from environment. Exiting.");
                    System.exit(1);
                }

                path = path.substring(srcPath.length());
                if (env.hasProjects()) {
                    // The paths need to correspond to a project.
                    Project project;
                    if ((project = Project.getProject(path)) != null) {
                        subFiles.add(path);
                        List<RepositoryInfo> repoList = env.getProjectRepositoriesMap().get(project);
                        if (repoList != null) {
                            repositories.addAll(repoList.
                                    stream().map(RepositoryInfo::getDirectoryNameRelative).collect(Collectors.toSet()));
                        }
                    } else {
                        System.err.println("The path " + path
                                + " does not correspond to a project");
                    }
                } else {
                    subFiles.add(path);
                }
            }

            if (!subFilesArgs.isEmpty() && subFiles.isEmpty()) {
                System.err.println("None of the paths were added, exiting");
                System.exit(1);
            }

            if (!subFiles.isEmpty() && configFilename != null) {
                LOGGER.log(Level.WARNING, "The collection of entries to process is non empty ({0}), seems like " +
                        "the intention is to perform per project reindex, however the -W option is used. " +
                        "This will likely not work.", subFiles);
            }

            Metrics.updateSubFiles(subFiles);

            // If the webapp is running with a config that does not contain
            // 'projectsEnabled' property (case of upgrade or transition
            // from project-less config to one with projects), set the property
            // so that the 'project/indexed' messages
            // emitted during indexing do not cause validation error.
            if (addProjects && webappURI != null) {
                try {
                    IndexerUtil.enableProjects(webappURI);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, String.format("Couldn't notify the webapp on %s.", webappURI), e);
                    System.err.printf("Couldn't notify the webapp on %s: %s.%n", webappURI, e.getLocalizedMessage());
                }
            }

            LOGGER.log(Level.INFO, "Indexer version {0} ({1}) running on Java {2}",
                    new Object[]{Info.getVersion(), Info.getRevision(), System.getProperty("java.version")});

            // Create history cache first.
            if (searchRepositories) {
                if (searchPaths.isEmpty()) {
                    String[] dirs = env.getSourceRootFile().
                            list((f, name) -> f.isDirectory() && env.getPathAccepter().accept(f));
                    if (dirs != null) {
                        searchPaths.addAll(Arrays.asList(dirs));
                    }
                }

                searchPaths = searchPaths.stream().
                        map(t -> Paths.get(env.getSourceRootPath(), t).toString()).
                        collect(Collectors.toSet());
            }
            getInstance().prepareIndexer(env, searchPaths, addProjects,
                    createDict, runIndex, subFiles, new ArrayList<>(repositories));

            // Set updated configuration in RuntimeEnvironment. This is called so that repositories discovered
            // in prepareIndexer() are stored in the Configuration used by RuntimeEnvironment.
            env.setConfiguration(cfg, subFilesArgs, CommandTimeoutType.INDEXER);

            // prepareIndexer() populated the list of projects so now default projects can be set.
            env.setDefaultProjectsFromNames(defaultProjects);

            // And now index it all.
            if (runIndex || (optimizedChanged && env.isOptimizeDatabase())) {
                IndexChangedListener progress = new DefaultIndexChangedListener();
                getInstance().doIndexerExecution(update, subFiles, progress);
            }

            writeConfigToFile(env, configFilename);

            // Finally, send new configuration to the web application in the case of full reindex.
            if (webappURI != null && subFiles.isEmpty()) {
                getInstance().sendToConfigHost(env, webappURI);
            }

            env.getIndexerParallelizer().bounce();
        } catch (ParseException e) {
            System.err.println("** " + e.getMessage());
            System.exit(1);
        } catch (IndexerException ex) {
            LOGGER.log(Level.SEVERE, "Exception running indexer", ex);
            System.err.println("Exception: " + ex.getLocalizedMessage());
            System.err.println(optParser.getUsage());
            System.exit(1);
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Unexpected Exception", e);
            System.err.println("Exception: " + e.getLocalizedMessage());
            System.exit(1);
        } finally {
            stats.report(LOGGER, "Indexer finished", "indexer.total");
        }
    }

    private static void checkConfiguration() {
        if (bareConfig && (env.getConfigURI() == null || env.getConfigURI().isEmpty())) {
            die("Missing webappURI setting");
        }

        if (!repositories.isEmpty() && !cfg.isHistoryEnabled()) {
            die("Repositories were specified; history is off however");
        }

        try {
            cfg.checkConfiguration();
        } catch (Configuration.ConfigurationException e) {
            die(e.getMessage());
        }
    }

    /**
     * Parse OpenGrok Indexer options
     * This method was created so that it would be easier to write unit
     * tests against the Indexer option parsing mechanism.
     *
     * @param argv the command line arguments
     * @return array of remaining non option arguments
     * @throws ParseException if parsing failed
     */
    public static String[] parseOptions(String[] argv) throws ParseException {
        final String[] usage = {HELP_OPT_1};

        if (argv.length == 0) {
            argv = usage;  // will force usage output
            status = 1; // with non-zero EXIT STATUS
        }

        /*
         * Pre-match any of the --help options so that some possible exception-generating args handlers (e.g. -R)
         * can be short-circuited.
         */
        boolean preHelp = Arrays.stream(argv).anyMatch(s -> HELP_OPT_1.equals(s) ||
                HELP_OPT_2.equals(s) || HELP_OPT_3.equals(s));

        OptionParser configure = OptionParser.scan(parser ->
                parser.on("-R configPath").execute(cfgFile -> {
            try {
                cfg = Configuration.read(new File((String) cfgFile));
            } catch (IOException e) {
                if (!preHelp) {
                    die(e.getMessage());
                } else {
                    System.err.printf("Warning: failed to read -R %s%n", cfgFile);
                }
            }
        }));

        searchPaths.clear();

        // Limit usage lines to 72 characters for concise formatting.

        optParser = OptionParser.execute(parser -> {
            parser.setPrologue(String.format("%nUsage: java -jar %s [options] [subDir1 [...]]%n", OPENGROK_JAR));

            parser.on(HELP_OPT_3, HELP_OPT_2, HELP_OPT_1, "=[mode]",
                    "With no mode specified, display this usage summary. Or specify a mode:",
                    "  config - display configuration.xml examples.",
                    "   ctags - display ctags command-line.",
                    "    guru - display AnalyzerGuru details.",
                    "   repos - display enabled repositories.").execute(v -> {
                        help = true;
                        helpUsage = parser.getUsage();
                        String mode = (String) v;
                        if (mode != null && !mode.isEmpty()) {
                            try {
                                helpMode = HelpMode.valueOf(((String) v).toUpperCase(Locale.ROOT));
                            } catch (IllegalArgumentException ex) {
                                die("mode '" + v + "' is not valid.");
                            }
                        }
            });

            parser.on("--apiTimeout", "=number", Integer.class,
                    "Set timeout for asynchronous API requests.").execute(v -> cfg.setApiTimeout((Integer) v));

            parser.on("--connectTimeout", "=number", Integer.class,
                    "Set connect timeout. Used for API requests.").execute(v -> cfg.setConnectTimeout((Integer) v));

            parser.on(
                "-A (.ext|prefix.):(-|analyzer)", "--analyzer",
                    "/(\\.\\w+|\\w+\\.):(-|[a-zA-Z_0-9.]+)/",
                    "Associates files with the specified prefix or extension (case-",
                    "insensitive) to be analyzed with the given analyzer, where 'analyzer'",
                    "may be specified using a class name (case-sensitive e.g. RubyAnalyzer)",
                    "or analyzer language name (case-sensitive e.g. C). Option may be",
                    "repeated.",
                    "  Ex: -A .foo:CAnalyzer",
                    "      will use the C analyzer for all files ending with .FOO",
                    "  Ex: -A bar.:Perl",
                    "      will use the Perl analyzer for all files starting with",
                    "      \"BAR\" (no full-stop)",
                    "  Ex: -A .c:-",
                    "      will disable specialized analyzers for all files ending with .c").
                execute(analyzerSpec -> {
                    String[] arg = ((String) analyzerSpec).split(":");
                    String fileSpec = arg[0];
                    String analyzer = arg[1];
                    configureFileAnalyzer(fileSpec, analyzer);
                }
            );

            parser.on("-c", "--ctags", "=/path/to/ctags",
                    "Path to Universal Ctags. Default is ctags in environment PATH.").execute(
                            v -> cfg.setCtags((String) v));

            parser.on("--canonicalRoot", "=/path/",
                    "Allow symlinks to canonical targets starting with the specified root",
                    "without otherwise needing to specify -N,--symlink for such symlinks. A",
                    "canonical root must end with a file separator. For security, a canonical",
                    "root cannot be the root directory. Option may be repeated.").execute(v -> {
                String root = (String) v;
                String problem = CanonicalRootValidator.validate(root, "--canonicalRoot");
                if (problem != null) {
                    die(problem);
                }
                canonicalRoots.add(root);
            });

            parser.on("--checkIndex", "Check index, exit with 0 on success,",
                    "with 1 on failure.").execute(v -> checkIndex = true);

            parser.on("-d", "--dataRoot", "=/path/to/data/root",
                "The directory where OpenGrok stores the generated data.").
                execute(drPath -> {
                    File dataRoot = new File((String) drPath);
                    if (!dataRoot.exists() && !dataRoot.mkdirs()) {
                        die("Cannot create data root: " + dataRoot);
                    }
                    if (!dataRoot.isDirectory()) {
                        die("Data root must be a directory");
                    }
                    try {
                        cfg.setDataRoot(dataRoot.getCanonicalPath());
                    } catch (IOException e) {
                        die(e.getMessage());
                    }
                }
            );

            parser.on("--depth", "=number", Integer.class,
                "Scanning depth for repositories in directory structure relative to",
                "source root. Default is " + Configuration.defaultScanningDepth + ".").execute(depth ->
                    cfg.setScanningDepth((Integer) depth));

            parser.on("--disableRepository", "=type_name",
                    "Disables operation of an OpenGrok-supported repository. See also",
                    "-h,--help repos. Option may be repeated.",
                    "  Ex: --disableRepository git",
                    "      will disable the GitRepository",
                    "  Ex: --disableRepository MercurialRepository").execute(v -> {
                String repoType = (String) v;
                String repoSimpleType = RepositoryFactory.matchRepositoryByName(repoType);
                if (repoSimpleType == null) {
                    System.err.printf("'--disableRepository %s' does not match a type and is ignored%n", v);
                } else {
                    disabledRepositories.add(repoSimpleType);
                }
            });

            parser.on("-e", "--economical",
                    "To consume less disk space, OpenGrok will not generate and save",
                    "hypertext cross-reference files but will generate on demand, which could",
                    "be slightly slow.").execute(v -> cfg.setGenerateHtml(false));

            parser.on("-G", "--assignTags",
                "Assign commit tags to all entries in history for all repositories.").execute(v ->
                    cfg.setTagsEnabled(true));

            // for backward compatibility
            parser.on("-H", "Enable history.").execute(v -> cfg.setHistoryEnabled(true));

            parser.on("--historyBased", "=on|off", ON_OFF, Boolean.class,
                            "If history based reindex is in effect, the set of files ",
                            "changed/deleted since the last reindex is determined from history ",
                            "of the repositories. This needs history, history cache and ",
                            "projects to be enabled. This should be much faster than the ",
                            "classic way of traversing the directory structure. ",
                            "The default is on. If you need to e.g. index files untracked by ",
                            "SCM, set this to off. Currently works only for Git.",
                            "All repositories in a project need to support this in order ",
                            "to be indexed using history.").
                    execute(v -> cfg.setHistoryBasedReindex((Boolean) v));

            parser.on("--historyThreads", "=number", Integer.class,
                    "The number of threads to use for history cache generation on repository level. " +
                    "By default the number of threads will be set to the number of available CPUs.",
                    "Assumes -H/--history.").execute(threadCount ->
                    cfg.setHistoryParallelism((Integer) threadCount));

            parser.on("--historyFileThreads", "=number", Integer.class,
                    "The number of threads to use for history cache generation when dealing with individual files.",
                    "By default the number of threads will be set to the number of available CPUs.",
                    "Assumes -H/--history.").execute(threadCount ->
                    cfg.setHistoryFileParallelism((Integer) threadCount));

            parser.on("-I", "--include", "=pattern",
                    "Only files matching this pattern will be examined. Pattern supports",
                    "wildcards (example: -I '*.java' -I '*.c'). Option may be repeated.").execute(
                            pattern -> cfg.getIncludedNames().add((String) pattern));

            parser.on("-i", "--ignore", "=pattern",
                    "Ignore matching files (prefixed with 'f:' or no prefix) or directories",
                    "(prefixed with 'd:'). Pattern supports wildcards (example: -i '*.so'",
                    "-i d:'test*'). Option may be repeated.").execute(pattern ->
                    cfg.getIgnoredNames().add((String) pattern));

            parser.on("-l", "--lock", "=on|off|simple|native", LUCENE_LOCKS,
                    "Set OpenGrok/Lucene locking mode of the Lucene database during index",
                    "generation. \"on\" is an alias for \"simple\". Default is off.").execute(v -> {
                try {
                    if (v != null) {
                        String vuc = v.toString().toUpperCase(Locale.ROOT);
                        cfg.setLuceneLocking(LuceneLockName.valueOf(vuc));
                    }
                } catch (IllegalArgumentException e) {
                    System.err.printf("`--lock %s' is invalid and ignored%n", v);
                }
            });

            parser.on("--leadingWildCards", "=on|off", ON_OFF, Boolean.class,
                "Allow or disallow leading wildcards in a search. Default is on.").execute(v ->
                    cfg.setAllowLeadingWildcard((Boolean) v));

            parser.on("-m", "--memory", "=number", Double.class,
                    "Amount of memory (MB) that may be used for buffering added documents and",
                    "deletions before they are flushed to the directory (default " +
                            Configuration.defaultRamBufferSize + ").",
                    "Please increase JVM heap accordingly too.").execute(memSize ->
                    cfg.setRamBufferSize((Double) memSize));

            parser.on("--mandoc", "=/path/to/mandoc", "Path to mandoc(1) binary.")
                    .execute(mandocPath -> cfg.setMandoc((String) mandocPath));

            parser.on("-N", "--symlink", "=/path/to/symlink",
                    "Allow the symlink to be followed. Other symlinks targeting the same",
                    "canonical target or canonical children will be allowed too. Option may",
                    "be repeated. (By default only symlinks directly under the source root",
                    "directory are allowed. See also --canonicalRoot)").execute(v ->
                    allowedSymlinks.add((String) v));

            parser.on("-n", "--noIndex",
                    "Do not generate indexes and other data (such as history cache and xref",
                    "files), but process all other command line options.").execute(v ->
                    runIndex = false);

            parser.on("--nestingMaximum", "=number", Integer.class,
                    "Maximum depth of nested repositories. Default is 1.").execute(v ->
                    cfg.setNestingMaximum((Integer) v));

            parser.on("-O", "--optimize", "=on|off", ON_OFF, Boolean.class,
                    "Turn on/off the optimization of the index database as part of the",
                    "indexing step. Default is on.").
                execute(v -> {
                    boolean oldval = cfg.isOptimizeDatabase();
                    cfg.setOptimizeDatabase((Boolean) v);
                    if (oldval != cfg.isOptimizeDatabase()) {
                        optimizedChanged = true;
                    }
                }
            );

            parser.on("-o", "--ctagOpts", "=path",
                "File with extra command line options for ctags.").
                execute(path -> {
                    String CTagsExtraOptionsFile = (String) path;
                    File CTagsFile = new File(CTagsExtraOptionsFile);
                    if (!(CTagsFile.isFile() && CTagsFile.canRead())) {
                        die("File '" + CTagsExtraOptionsFile + "' not found for the -o option");
                    }
                    System.err.println("INFO: file with extra "
                        + "options for ctags: " + CTagsExtraOptionsFile);
                    cfg.setCTagsExtraOptionsFile(CTagsExtraOptionsFile);
                }
            );

            parser.on("-P", "--projects",
                "Generate a project for each top-level directory in source root.").execute(v -> {
                addProjects = true;
                cfg.setProjectsEnabled(true);
            });

            parser.on("-p", "--defaultProject", "=path/to/default/project",
                    "Path (relative to the source root) to a project that should be selected",
                    "by default in the web application (when no other project is set either",
                    "in a cookie or in parameter). Option may be repeated to specify several",
                    "projects. Use the special value __all__ to indicate all projects.").execute(v ->
                    defaultProjects.add((String) v));

            parser.on("--profiler", "Pause to await profiler or debugger.").
                execute(v -> awaitProfiler = true);

            parser.on("--progress",
                    "Print per-project percentage progress information.").execute(v ->
                    cfg.setPrintProgress(true));

            parser.on("-Q", "--quickScan",  "=on|off", ON_OFF, Boolean.class,
                    "Turn on/off quick context scan. By default, only the first 1024KB of a",
                    "file is scanned, and a link ('[..all..]') is inserted when the file is",
                    "bigger. Activating this may slow the server down. (Note: this setting",
                    "only affects the web application.) Default is on.").execute(v ->
                    cfg.setQuickContextScan((Boolean) v));

            parser.on("-q", "--quiet",
                    "Run as quietly as possible. Sets logging level to WARNING.").execute(v ->
                    LoggerUtil.setBaseConsoleLogLevel(Level.WARNING));

            parser.on("-R /path/to/configuration",
                "Read configuration from the specified file.").execute(v -> {
                // Already handled above. This populates usage.
            });

            parser.on("-r", "--remote", "=on|off|uionly|dirbased",
                REMOTE_REPO_CHOICES,
                "Specify support for remote SCM systems.",
                "      on - allow retrieval for remote SCM systems.",
                "     off - ignore SCM for remote systems.",
                "  uionly - support remote SCM for user interface only.",
                "dirbased - allow retrieval during history index only for repositories",
                "           which allow getting history for directories.").
                execute(v -> {
                    String option = (String) v;
                    if (option.equalsIgnoreCase(ON)) {
                        cfg.setRemoteScmSupported(Configuration.RemoteSCM.ON);
                    } else if (option.equalsIgnoreCase(OFF)) {
                        cfg.setRemoteScmSupported(Configuration.RemoteSCM.OFF);
                    } else if (option.equalsIgnoreCase(DIRBASED)) {
                        cfg.setRemoteScmSupported(Configuration.RemoteSCM.DIRBASED);
                    } else if (option.equalsIgnoreCase(UIONLY)) {
                        cfg.setRemoteScmSupported(Configuration.RemoteSCM.UIONLY);
                    }
                }
            );

            parser.on("--renamedHistory", "=on|off", ON_OFF, Boolean.class,
                "Enable or disable generating history for renamed files.",
                "If set to on, makes history indexing slower for repositories",
                "with lots of renamed files. Default is off.").execute(v ->
                    cfg.setHandleHistoryOfRenamedFiles((Boolean) v));

            parser.on("--repository", "=[path/to/repository|@file_with_paths]",
                    "Path (relative to the source root) to a repository for generating",
                    "history (if -H,--history is on). By default all discovered repositories",
                    "are history-eligible; using --repository limits to only those specified.",
                    "File containing paths can be specified via @path syntax.",
                    "Option may be repeated.")
                .execute(v -> handlePathParameter(repositories, ((String) v).trim()));

            parser.on("-S", "--search", "=[path/to/repository|@file_with_paths]",
                    "Search for source repositories under -s,--source, and add them. Path",
                    "(relative to the source root) is optional. ",
                    "File containing paths can be specified via @path syntax.",
                    "Option may be repeated.")
                .execute(v -> {
                        searchRepositories = true;
                        String value = ((String) v).trim();
                        if (!value.isEmpty()) {
                            handlePathParameter(searchPaths, value);
                        }
                    });

            parser.on("-s", "--source", "=/path/to/source/root",
                "The root directory of the source tree.").
                execute(source -> {
                    File sourceRoot = new File((String) source);
                    if (!sourceRoot.isDirectory()) {
                        die("Source root " + sourceRoot + " must be a directory");
                    }
                    try {
                        cfg.setSourceRoot(sourceRoot.getCanonicalPath());
                    } catch (IOException e) {
                        die(e.getMessage());
                    }
                }
            );

            parser.on("--style", "=path",
                    "Path to the subdirectory in the web application containing the requested",
                    "stylesheet. The factory-setting is: \"default\".").execute(stylePath ->
                    cfg.setWebappLAF((String) stylePath));

            parser.on("-T", "--threads", "=number", Integer.class,
                    "The number of threads to use for index generation, repository scan",
                    "and repository invalidation.",
                    "By default the number of threads will be set to the number of available",
                    "CPUs. This influences the number of spawned ctags processes as well.").
                    execute(threadCount -> cfg.setIndexingParallelism((Integer) threadCount));

            parser.on("-t", "--tabSize", "=number", Integer.class,
                "Default tab size to use (number of spaces per tab character).")
                    .execute(tabSize -> cfg.setTabSize((Integer) tabSize));

            parser.on("--token", "=string|@file_with_string",
                    "Authorization bearer API token to use when making API calls",
                    "to the web application").
                    execute(optarg -> {
                        String value = ((String) optarg).trim();
                        if (value.startsWith("@")) {
                            try (BufferedReader in = new BufferedReader(new InputStreamReader(
                                    new FileInputStream(Path.of(value).toString().substring(1))))) {
                                String token = in.readLine().trim();
                                cfg.setIndexerAuthenticationToken(token);
                            } catch (IOException e) {
                                die("Failed to read from " + value);
                            }
                        } else {
                            cfg.setIndexerAuthenticationToken(value);
                        }
                    });

            parser.on("-U", "--uri", "=SCHEME://webappURI:port/contextPath",
                "Send the current configuration to the specified web application.").execute(webAddr -> {
                    webappURI = (String) webAddr;
                    try {
                        URI uri = new URI(webappURI);
                        String scheme = uri.getScheme();
                        if (!scheme.equals("http") && !scheme.equals("https")) {
                            die("webappURI '" + webappURI + "' does not have HTTP/HTTPS scheme");
                        }
                    } catch (URISyntaxException e) {
                        die("URL '" + webappURI + "' is not valid.");
                    }

                    env = RuntimeEnvironment.getInstance();
                    env.setConfigURI(webappURI);
                }
            );

            parser.on("---unitTest");  // For unit test only, will not appear in help

            parser.on("--updateConfig",
                    "Populate the web application with a bare configuration, and exit.").execute(v ->
                    bareConfig = true);

            parser.on("--userPage", "=URL",
                "Base URL of the user Information provider.",
                "Example: \"https://www.example.org/viewProfile.jspa?username=\".",
                "Use \"none\" to disable link.").execute(v -> cfg.setUserPage((String) v));

            parser.on("--userPageSuffix", "=URL-suffix",
                "URL Suffix for the user Information provider. Default: \"\".")
                    .execute(suffix -> cfg.setUserPageSuffix((String) suffix));

            parser.on("-V", "--version", "Print version, and quit.").execute(v -> {
                System.out.println(Info.getFullVersion());
                System.exit(0);
            });

            parser.on("-v", "--verbose", "Set logging level to INFO.").execute(v -> {
                verbose = true;
                LoggerUtil.setBaseConsoleLogLevel(Level.INFO);
            });

            parser.on("-W", "--writeConfig", "=/path/to/configuration",
                    "Write the current configuration to the specified file (so that the web",
                    "application can use the same configuration).").execute(configFile ->
                    configFilename = (String) configFile);

            parser.on("--webappCtags", "=on|off", ON_OFF, Boolean.class,
                    "Web application should run ctags when necessary. Default is off.").
                    execute(v -> cfg.setWebappCtags((Boolean) v));
        });

        // Need to read the configuration file first, so that options may be overwritten later.
        configure.parse(argv);

        LOGGER.log(Level.INFO, "Indexer options: {0}", Arrays.toString(argv));

        if (cfg == null) {
            cfg = new Configuration();
        }

        cfg.setHistoryEnabled(false);  // force user to turn on history capture

        argv = optParser.parse(argv);

        return argv;
    }

    private static void die(String message) {
        System.err.println("ERROR: " + message);
        System.exit(1);
    }

    private static void configureFileAnalyzer(String fileSpec, String analyzer) {

        boolean prefix = false;

        // removing '.' from file specification
        // expecting either ".extensionName" or "prefixName."
        if (fileSpec.endsWith(".")) {
            fileSpec = fileSpec.substring(0, fileSpec.lastIndexOf('.'));
            prefix = true;
        } else {
            fileSpec = fileSpec.substring(1);
        }
        fileSpec = fileSpec.toUpperCase(Locale.ROOT);

        // Disable analyzer?
        if (analyzer.equals("-")) {
            if (prefix) {
                AnalyzerGuru.addPrefix(fileSpec, null);
            } else {
                AnalyzerGuru.addExtension(fileSpec, null);
            }
        } else {
            try {
                if (prefix) {
                    AnalyzerGuru.addPrefix(
                        fileSpec,
                        AnalyzerGuru.findFactory(analyzer));
                } else {
                    AnalyzerGuru.addExtension(
                        fileSpec,
                        AnalyzerGuru.findFactory(analyzer));
                }

            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | NoSuchMethodException
                    | InvocationTargetException e) {
                LOGGER.log(Level.SEVERE, "Unable to locate FileAnalyzerFactory for {0}", analyzer);
                LOGGER.log(Level.SEVERE, "Stack: ", e.fillInStackTrace());
                System.exit(1);
            }
        }
    }

    /**
     * Write configuration to a file.
     * @param env runtime environment
     * @param filename file name to write the configuration to
     * @throws IOException if I/O exception occurred
     */
    public static void writeConfigToFile(RuntimeEnvironment env, String filename) throws IOException {
        if (filename != null) {
            LOGGER.log(Level.INFO, "Writing configuration to {0}", filename);
            env.writeConfiguration(new File(filename));
            LOGGER.log(Level.INFO, "Done writing configuration to {0}", filename);
        }
    }

    // Wrapper for prepareIndexer() that always generates history cache.
    public void prepareIndexer(RuntimeEnvironment env,
                               boolean searchRepositories,
                               boolean addProjects,
                               boolean createDict,
                               List<String> subFiles,
                               List<String> repositories) throws IndexerException, IOException {

        prepareIndexer(env,
                searchRepositories ? Collections.singleton(env.getSourceRootPath()) : Collections.emptySet(),
                addProjects, createDict, true, subFiles, repositories);
    }

    /**
     * Generate history cache and/or scan the repositories.
     *
     * This is the first phase of the indexing where history cache is being
     * generated for repositories (at least for those which support getting
     * history per directory).
     *
     * @param env runtime environment
     * @param searchPaths list of paths in which to search for repositories
     * @param addProjects if true, add projects
     * @param createDict if true, create dictionary
     * @param createHistoryCache create history cache flag
     * @param subFiles list of directories
     * @param repositories list of repositories
     * @throws IndexerException indexer exception
     * @throws IOException I/O exception
     */
    public void prepareIndexer(RuntimeEnvironment env,
            Set<String> searchPaths,
            boolean addProjects,
            boolean createDict,
            boolean createHistoryCache,
            List<String> subFiles,
            List<String> repositories) throws IndexerException, IOException {

        if (!env.validateUniversalCtags()) {
            throw new IndexerException("Didn't find Universal Ctags");
        }

        // Projects need to be created first since when adding repositories below,
        // some project properties might be needed for that.
        if (addProjects) {
            File[] files = env.getSourceRootFile().listFiles();
            Map<String, Project> projects = env.getProjects();

            addProjects(files, projects);
        }

        if (!searchPaths.isEmpty()) {
            LOGGER.log(Level.INFO, "Scanning for repositories in {0} (down to {1} levels below source root)",
                    new Object[]{searchPaths, env.getScanningDepth()});
            Statistics stats = new Statistics();
            env.setRepositories(searchPaths.toArray(new String[0]));
            stats.report(LOGGER, String.format("Done scanning for repositories, found %d repositories",
                    env.getRepositories().size()), "indexer.repository.scan");
        }

        if (createHistoryCache) {
            // Even if history is disabled globally, it can be enabled for some repositories.
            if (repositories != null && !repositories.isEmpty()) {
                LOGGER.log(Level.INFO, "Generating history cache for repositories: {0}",
                        String.join(",", repositories));
                HistoryGuru.getInstance().createCache(repositories);
            } else {
                LOGGER.log(Level.INFO, "Generating history cache for all repositories ...");
                HistoryGuru.getInstance().createCache();
            }
            LOGGER.info("Done generating history cache");
        }

        if (createDict) {
            IndexDatabase.listFrequentTokens(subFiles);
        }
    }

    private void addProjects(File[] files, Map<String, Project> projects) {
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
            String path = '/' + name;
            if (oldProjects.containsKey(name)) {
                // This is an existing object. Reuse the old project,
                // possibly with customizations, instead of creating a
                // new with default values.
                Project p = oldProjects.get(name);
                p.setPath(path);
                p.setName(name);
                p.completeWithDefaults();
                projects.put(name, p);
            } else if (!name.startsWith(".") && file.isDirectory()) {
                // Found a new directory with no matching project, so
                // create a new project with default properties.
                projects.put(name, new Project(name, path));
            }
        }
    }

    /**
     * This is the second phase of the indexer which generates Lucene index
     * by passing source code files through ctags, generating xrefs
     * and storing data from the source files in the index (along with history,
     * if any).
     *
     * @param update if set to true, index database is updated, otherwise optimized
     * @param subFiles index just some subdirectories
     * @param progress object to receive notifications as indexer progress is made
     * @throws IOException if I/O exception occurred
     */
    public void doIndexerExecution(final boolean update, List<String> subFiles,
        IndexChangedListener progress)
            throws IOException {
        Statistics elapsed = new Statistics();
        LOGGER.info("Starting indexing");

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        IndexerParallelizer parallelizer = env.getIndexerParallelizer();
        final CountDownLatch latch;
        if (subFiles == null || subFiles.isEmpty()) {
            if (update) {
                latch = IndexDatabase.updateAll(progress);
            } else if (env.isOptimizeDatabase()) {
                latch = IndexDatabase.optimizeAll();
            } else {
                latch = new CountDownLatch(0);
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

            latch = new CountDownLatch(dbs.size());
            for (final IndexDatabase db : dbs) {
                final boolean optimize = env.isOptimizeDatabase();
                db.addIndexChangedListener(progress);
                parallelizer.getFixedExecutor().submit(() -> {
                    try {
                        if (update) {
                            db.update();
                        } else if (optimize) {
                            db.optimize();
                        }
                    } catch (Throwable e) {
                        LOGGER.log(Level.SEVERE, "An error occurred while "
                                + (update ? "updating" : "optimizing")
                                + " index", e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        // Wait forever for the executors to finish.
        try {
            LOGGER.info("Waiting for the executors to finish");
            latch.await();
        } catch (InterruptedException exp) {
            LOGGER.log(Level.WARNING, "Received interrupt while waiting" +
                    " for executor to finish", exp);
        }
        elapsed.report(LOGGER, "Done indexing data of all repositories", "indexer.repository.indexing");

        CtagsUtil.deleteTempFiles();
    }

    public void sendToConfigHost(RuntimeEnvironment env, String host) {
        LOGGER.log(Level.INFO, "Sending configuration to: {0}", host);
        try {
            env.writeConfiguration(host);
        } catch (IOException | IllegalArgumentException ex) {
            LOGGER.log(Level.SEVERE, String.format(
                    "Failed to send configuration to %s "
                    + "(is web application server running with opengrok deployed?)", host), ex);
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "interrupted while sending configuration");
        }
        LOGGER.info("Configuration update routine done, check log output for errors.");
    }

    private static void pauseToAwaitProfiler() {
        Scanner scan = new Scanner(System.in);
        String in;
        do {
            System.out.print("Start profiler. Continue (Y/N)? ");
            in = scan.nextLine().toLowerCase(Locale.ROOT);
        } while (!in.equals("y") && !in.equals("n"));

        if (in.equals("n")) {
            System.exit(1);
        }
    }

    // Visible for testing
    static void handlePathParameter(Collection<String> paramValueStore, String pathValue) {
        if (pathValue.startsWith("@")) {
            paramValueStore.addAll(loadPathsFromFile(pathValue.substring(1)));
        } else {
            paramValueStore.add(pathValue);
        }
    }

    private static List<String> loadPathsFromFile(String filename) {
        try {
            return Files.readAllLines(Path.of(filename));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, String.format("Could not load paths from %s", filename), e);
            throw new UncheckedIOException(e);
        }
    }

    private static void exitWithHelp() {
        PrintStream helpStream = status != 0 ? System.err : System.out;
        switch (helpMode) {
            case CONFIG:
                helpStream.print(ConfigurationHelp.getSamples());
                break;
            case CTAGS:
                /*
                 * Force the environment's ctags, because this method is called
                 * before main() does the heavyweight setConfiguration().
                 */
                env.setCtags(cfg.getCtags());
                helpStream.println("Ctags command-line:");
                helpStream.println();
                helpStream.println(getCtagsCommand());
                helpStream.println();
                break;
            case GURU:
                helpStream.println(AnalyzerGuruHelp.getUsage());
                break;
            case REPOS:
                /*
                 * Force the environment's disabledRepositories (as above).
                 */
                env.setDisabledRepositories(cfg.getDisabledRepositories());
                helpStream.println(RepositoriesHelp.getText());
                break;
            default:
                helpStream.println(helpUsage);
                break;
        }
        System.exit(status);
    }

    private static String getCtagsCommand() {
        Ctags ctags = CtagsUtil.newInstance(env);
        return Executor.escapeForShell(ctags.getArgv(), true, SystemUtils.IS_OS_WINDOWS);
    }

    private enum HelpMode {
        CONFIG, CTAGS, DEFAULT, GURU, REPOS
    }

    private Indexer() {
    }
}
