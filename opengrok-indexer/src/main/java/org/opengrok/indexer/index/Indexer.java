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
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright 2011 Jens Elkner.
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.opengrok.indexer.Info;
import org.opengrok.indexer.analysis.AnalyzerGuru;
import org.opengrok.indexer.analysis.AnalyzerGuruHelp;
import org.opengrok.indexer.configuration.Configuration;
import org.opengrok.indexer.configuration.ConfigurationHelp;
import org.opengrok.indexer.configuration.LuceneLockName;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryException;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.Repository;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.history.RepositoryInfo;
import org.opengrok.indexer.index.IndexVersion.IndexVersionException;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.logger.LoggerUtil;
import org.opengrok.indexer.util.Executor;
import org.opengrok.indexer.util.OptionParser;
import org.opengrok.indexer.util.Statistics;

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
    private static Configuration cfg = null;
    private static boolean checkIndexVersion = false;
    private static boolean listRepos = false;
    private static boolean runIndex = true;
    private static boolean optimizedChanged = false;
    private static boolean addProjects = false;
    private static boolean searchRepositories = false;
    private static boolean noindex = false;
    private static boolean awaitProfiler;

    private static boolean help;
    private static String helpUsage;
    private static boolean helpDetailed;

    private static String configFilename = null;
    private static int status = 0;

    private static final Set<String> repositories = new HashSet<>();
    private static final HashSet<String> allowedSymlinks = new HashSet<>();
    private static final Set<String> defaultProjects = new TreeSet<>();
    private static final ArrayList<String> zapCache = new ArrayList<>();
    private static RuntimeEnvironment env = null;
    private static String webappURI = null;

    private static OptionParser optParser = null;
    private static boolean verbose = false;

    public static Indexer getInstance() {
        return index;
    }

    /**
     * Program entry point
     *
     * @param argv argument vector
     */
    @SuppressWarnings("PMD.UseStringBufferForStringAppends")
    public static void main(String argv[]) {
        Statistics stats = new Statistics(); //this won't count JVM creation though
        boolean update = true;

        Executor.registerErrorHandler();
        ArrayList<String> subFiles = new ArrayList<>();
        ArrayList<String> subFilesList = new ArrayList<>();

        boolean listFiles = false;
        boolean createDict = false;

        try {
            argv = parseOptions(argv);
            if (help) {
                status = 1;
                System.err.println(helpUsage);
                if (helpDetailed) {
                    System.err.println(AnalyzerGuruHelp.getUsage());
                    System.err.println(
                        ConfigurationHelp.getSamples());
                }
                System.exit(status);
            }

            checkConfiguration();

            if (awaitProfiler) {
                pauseToAwaitProfiler();
            }

            env = RuntimeEnvironment.getInstance();

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
            if (verbose) {
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
            for (String arg : argv) {
                String path = Paths.get(cfg.getSourceRoot(), arg).toString();
                subFilesList.add(path);
            }

            // If an user used customizations for projects he perhaps just
            // used the key value for project without a name but the code
            // expects a name for the project. Therefore we fill the name
            // according to the project key which is the same.
            for (Entry<String, Project> entry : cfg.getProjects().entrySet()) {
                if (entry.getValue().getName() == null) {
                    entry.getValue().setName(entry.getKey());
                }
            }

            // Set updated configuration in RuntimeEnvironment.
            env.setConfiguration(cfg, subFilesList, false);

            // Check version of index(es) versus current Lucene version and exit
            // with return code upon failure.
            if (checkIndexVersion) {
                if (cfg == null) {
                    System.err.println("Need configuration to check index version (use -R)");
                    System.exit(1);
                }

                try {
                    IndexVersion.check(subFilesList);
                } catch (IndexVersionException e) {
                    System.err.printf("Index version check failed: %s\n", e);
                    System.err.printf("You might want to remove " +
                            (subFilesList.size() > 0 ?
                                    "data for projects " + String.join(",", subFilesList) : "all data") +
                            " under the DATA_ROOT and to reindex\n");
                    status = 1;
                    System.exit(status);
                }
            }

            // Let repository types to add items to ignoredNames.
            // This changes env so is called after the setConfiguration()
            // call above.
            RepositoryFactory.initializeIgnoredNames(env);

            if (noindex) {
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
            for (String path : subFilesList) {
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
                                    stream().map(x -> x.getDirectoryNameRelative()).collect(Collectors.toSet()));
                        }
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
            // so that the 'project/indexed' messages
            // emitted during indexing do not cause validation error.
            if (addProjects && webappURI != null) {
                try {
                    IndexerUtil.enableProjects(webappURI);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Mis-configuration of webapp webappURI", e);
                    System.err.println("Couldn't notify the webapp: " + e.getLocalizedMessage());
                }
            }

            LOGGER.log(Level.INFO, "Indexer version {0} ({1})",
                    new Object[]{Info.getVersion(), Info.getRevision()});
            
            // Get history first.
            getInstance().prepareIndexer(env, searchRepositories, addProjects,
                    defaultProjects,
                    listFiles, createDict, subFiles, new ArrayList(repositories),
                    zapCache, listRepos);
            if (listRepos || !zapCache.isEmpty()) {
                return;
            }

            // And now index it all.
            if (runIndex || (optimizedChanged && env.isOptimizeDatabase())) {
                IndexChangedListener progress = new DefaultIndexChangedListener();
                getInstance().doIndexerExecution(update, subFiles, progress);
            }

            writeConfigToFile(env, configFilename);

            // Finally ping webapp to refresh indexes in the case of partial reindex
            // or send new configuration to the web application in the case of full reindex.
            if (webappURI != null) {
                if (!subFiles.isEmpty()) {
                    getInstance().refreshSearcherManagers(env, subFiles, webappURI);
                } else {
                    getInstance().sendToConfigHost(env, webappURI);
                }
            }

        } catch (ParseException e) {
            System.err.println("** " +e.getMessage());
            System.exit(1);
        } catch (IndexerException ex) {
            LOGGER.log(Level.SEVERE, "Exception running indexer", ex);
            System.err.println(optParser.getUsage());
            System.exit(1);
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Unexpected Exception", e);
            System.err.println("Exception: " + e.getLocalizedMessage());
            System.exit(1);
        } finally {
            stats.report(LOGGER);
        }
    }

    /**
     * Web address consisting of webappURI and port of a web address
     */
    public static class WebAddress {
        private String host;
        private int port;

        WebAddress(String host, String port) throws NumberFormatException {
            this.host = host;
            this.port = Integer.parseInt(port);
        }
        public String getHost() {
            return host;
        }
        public int getPort() {
            return port;
        }
    }

     /**
      * Parse a web address into its webappURI and port components
      * This method along with the WebAddress class is used by OptionParser
      * to validate user entry of a web address.
      * @param webAddr expected to be in the form webappURI:port
      * @return WebAddress object
      * @throws NumberFormatException or IllegalArgumentException
      */
    public static WebAddress parseWebAddress(String webAddr) {
        String[] hp = webAddr.split(":");

        if (hp.length != 2) {
            throw new IllegalArgumentException("WebAddress syntax error (expecting webappURI:port)");
        }

        return new WebAddress(hp[0], hp[1]);
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
        String[] usage = {"--help"};
        String program = "opengrok.jar";
        final String[] ON_OFF = {ON, OFF};
        final String[] REMOTE_REPO_CHOICES = {ON, OFF, DIRBASED, UIONLY};
        final String[] LUCENE_LOCKS = {ON, OFF, "simple", "native"};

        if (argv.length == 0) {
            argv = usage;  // will force usage output
            status = 1;
        }

        OptionParser configure = OptionParser.scan(parser -> {
            parser.on("-R configPath").Do(cfgFile -> {
                try {
                    cfg = Configuration.read(new File((String)cfgFile));
                } catch(IOException e) {
                    die(e.getMessage());
                }
            });
        });

        // An example of how to add a data type for option parsing
        OptionParser.accept(WebAddress.class, s -> { return parseWebAddress(s); });

        optParser = OptionParser.Do(parser -> {
            parser.setPrologue(
                String.format("\nUsage: java -jar %s [options] [subDir1 [...]]\n", program));

            parser.on("-?", "-h", "--help", "Display this usage summary.").Do(v -> {
                help = true;
                helpUsage = parser.getUsage();
            });

            parser.on("--detailed",
                "Display additional help with -h,--help.").Do(v -> {
                helpDetailed = true;
            });

            parser.on(
                "-A (.ext|prefix.):(-|analyzer)", "--analyzer", "/(\\.\\w+|\\w+\\.):(-|[a-zA-Z_0-9.]+)/",
                    "Files with the named prefix/extension should be analyzed",
                    "with the given analyzer, where 'analyzer' may be specified",
                    "using a simple class name (RubyAnalyzer) or language name (C)",
                    "(Note, analyzer specification is case sensitive)",
                    "  Ex: -A .foo:CAnalyzer",
                    "      will use the C analyzer for all files ending with .FOO",
                    "  Ex: -A bar.:Perl",
                    "      will use the Perl analyzer for all files starting",
                    "      with \"BAR\" (no full-stop)",
                    "  Ex: -A .c:-",
                    "      will disable specialized analyzers for all files ending with .c").
                Do(analyzerSpec -> {
                    String[] arg = ((String)analyzerSpec).split(":");
                    String fileSpec = arg[0];
                    String analyzer = arg[1];
                    configureFileAnalyzer(fileSpec, analyzer);
                }
            );

            parser.on("-c", "--ctags","=/path/to/ctags",
                "Path to Universal Ctags",
                "By default takes the Universal Ctags in PATH.").
                Do(ctagsPath -> {
                    cfg.setCtags((String)ctagsPath);
                }
            );

            parser.on("--checkIndexVersion",
                    "Check if current Lucene version matches index version").Do(v -> {
                checkIndexVersion = true;
            });

            parser.on("-d", "--dataRoot", "=/path/to/data/root",
                "The directory where OpenGrok stores the generated data.").
                Do(drPath -> {
                    File dataRoot = new File((String)drPath);
                    if (!dataRoot.exists() && !dataRoot.mkdirs()) {
                        die("Cannot create data root: " + dataRoot);
                    }
                    if (!dataRoot.isDirectory()) {
                        die("Data root must be a directory");
                    }
                    try {
                        cfg.setDataRoot(dataRoot.getCanonicalPath());
                    } catch(IOException e) {
                        die(e.getMessage());
                    }
                }
            );

            parser.on("--deleteHistory", "=/path/to/repository",
                "Delete the history cache for the given repository and exit.",
                "Use '*' to delete the cache for all repositories.").Do(repo -> {
                zapCache.add((String)repo);
            });

            parser.on("--depth", "=number", Integer.class,
                "Scanning depth for repositories in directory structure relative to",
                "source root. Default is " + Configuration.defaultScanningDepth + ".").Do(depth -> {
                cfg.setScanningDepth((Integer)depth);
            });

            parser.on("-e", "--economical",
                "Economical, consumes less disk space.",
                "It does not generate hyper text cross reference files offline,",
                "but will do so on demand, which could be sightly slow.").Do(v -> {
                cfg.setGenerateHtml(false);
            });

            parser.on("-G", "--assignTags",
                "Assign commit tags to all entries in history for all repositories.").Do(v -> {
                cfg.setTagsEnabled(true);
            });

            parser.on("-H", "--history", "Enable history.").Do(v -> {
                cfg.setHistoryEnabled(true);
            });

            parser.on("-I", "--include", "=pattern",
                "Only files matching this pattern will be examined.",
                "(supports wildcards, example: -I *.java -I *.c)").Do(pattern -> {
                cfg.getIncludedNames().add((String)pattern);
            });

            parser.on("-i", "--ignore", "=pattern",
                "Ignore the named files (prefixed with 'f:')",
                "or directories (prefixed with 'd:').",
                "Supports wildcards (example: -i *.so -i *.dll)").Do(pattern -> {
                cfg.getIgnoredNames().add((String)pattern);
            });

            parser.on("-l", "--lock", "=on|off|simple|native", LUCENE_LOCKS,
                "Set OpenGrok/Lucene locking mode of the Lucene database",
                "during index generation. \"on\" is an alias for \"simple\".",
                "Default is off.").Do(v -> {
                try {
                    if (v != null) {
                        String vuc = v.toString().toUpperCase(Locale.ROOT);
                        cfg.setLuceneLocking(LuceneLockName.valueOf(vuc));
                    }
                } catch (IllegalArgumentException e) {
                    System.err.println(String.format(
                        "`--lock %s' is invalid and ignored", v));
                }
            });

            parser.on("--leadingWildCards", "=on|off", ON_OFF, Boolean.class,
                "Allow or disallow leading wildcards in a search.").Do(v -> {
                cfg.setAllowLeadingWildcard((Boolean)v);
            });

            parser.on("--listRepos", "List all repository paths and exit.").Do(v -> {
                listRepos = true;
            });

            parser.on("-m", "--memory", "=number", Double.class,
                "Amount of memory that may be used for buffering added documents and",
                "deletions before they are flushed to the directory (default "+Configuration.defaultRamBufferSize+"MB).",
                "Please increase JVM heap accordingly, too.").Do(memSize -> {
                cfg.setRamBufferSize((Double)memSize);
            });

            parser.on("--man", "Generate OpenGrok XML manual page.").Do(v -> {
                try {
                    System.out.print(parser.getManPage());
                } catch(IOException e) {
                    System.err.println(e.getMessage());
                    status = 1;
                }
                System.exit(status);
            });

            parser.on("--mandoc","=/path/to/mandoc",
                "Path to mandoc(1) binary.").
                Do(mandocPath -> {
                    cfg.setMandoc((String)mandocPath);
                }
            );

            parser.on("-n", "--noIndex",
                "Do not generate indexes, but process all other command line options.").Do(v -> {
                runIndex = false;
            });

            parser.on("-O", "--optimize", "=on|off", ON_OFF, Boolean.class,
                "Turn on/off the optimization of the index database",
                "as part of the indexing step.").
                Do(v -> {
                    boolean oldval = cfg.isOptimizeDatabase();
                    cfg.setOptimizeDatabase((Boolean)v);
                    if (oldval != cfg.isOptimizeDatabase()) {
                        optimizedChanged = true;
                    }
                }
            );

            parser.on("-o", "--ctagOpts", "=path",
                "File with extra command line options for ctags.").
                Do(path -> {
                    String CTagsExtraOptionsFile = (String)path;
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
                "Generate a project for each top-level directory in source root.").Do(v -> {
                addProjects = true;
                cfg.setProjectsEnabled(true);
            });

            parser.on("-p", "--defaultProject", "=/path/to/default/project",
                "This is the path to the project that should be selected",
                "by default in the web application (when no other project",
                "set either in cookie or in parameter). May be used multiple",
                "times for several projects. Use \"__all__\" for all projects.",
                "You should strip off the source root.").Do(v -> {
                defaultProjects.add((String)v);
            });

            parser.on("--profiler", "Pause to await profiler or debugger.").
                Do(v -> awaitProfiler = true);

            parser.on("--progress",
                "Print per project percentage progress information.",
                "(I/O extensive, since one read through directory structure is",
                "made before indexing, needs -v, otherwise it just goes to the log)").
                Do(v -> {
                    cfg.setPrintProgress(true);
                }
            );

            parser.on("-Q", "--quickScan",  "=on|off", ON_OFF, Boolean.class,
                "Turn on/off quick context scan. By default, only the first",
                "1024k of a file is scanned, and a '[..all..]' link is inserted",
                "when the file is bigger. Activating this may slow the server down.",
                "(Note: this is setting only affects the web application)").Do(v -> {
                cfg.setQuickContextScan((Boolean)v);
            });

            parser.on("-q", "--quiet", "Run as quietly as possible.",
                    "Sets logging level to WARNING.").Do(v -> {
                LoggerUtil.setBaseConsoleLogLevel(Level.WARNING);
            });

            parser.on("--repository", "=repository",
                    "Generate history for specific repository specified as relative path to source root. ",
                    "Can be used multiple times. Assumes history is on.").Do(repo -> {
                repositories.add((String)repo);
            });

            parser.on("-R /path/to/configuration",
                "Read configuration from the specified file.").Do(v-> {
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
                Do(v -> {
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
                "with lots of renamed files.").Do(v -> {
                    cfg.setHandleHistoryOfRenamedFiles((Boolean)v);
            });

            parser.on("-S", "--search",
                "Search for \"external\" source repositories and add them.").Do(v -> {
                searchRepositories = true;
            });

            parser.on("-s", "--source", "=/path/to/source/root",
                "The root directory of the source tree.").
                Do(source -> {
                    File sourceRoot = new File((String)source);
                    if (!sourceRoot.isDirectory()) {
                        die("Source root " + sourceRoot + " must be a directory");
                    }
                    try {
                        cfg.setSourceRoot(sourceRoot.getCanonicalPath());
                    } catch(IOException e) {
                        die(e.getMessage());
                    }
                }
            );

            parser.on("--style", "=path",
                "Path to the subdirectory in the web-application containing the",
                "requested stylesheet. The factory-setting is: \"default\".").
                Do(stylePath -> {
                    cfg.setWebappLAF((String)stylePath);
                }
            );

            parser.on("--symlink", "=/path/to/symlink",
                "Allow this symlink to be followed. Option may be repeated.",
                "By default only symlinks directly under source root directory",
                "are allowed.").Do(symlink -> {
                allowedSymlinks.add((String)symlink);
            });

            parser.on("-T", "--threads", "=number", Integer.class,
                "The number of threads to use for index generation.",
                "By default the number of threads will be set to the number",
                "of available CPUs.").Do(threadCount -> {
                cfg.setIndexingParallelism((Integer)threadCount);
            });

            parser.on("-t", "--tabSize", "=number", Integer.class,
                "Default tab size to use (number of spaces per tab character).").Do(tabSize -> {
                cfg.setTabSize((Integer)tabSize);
            });

            parser.on("-U", "--uri", "=protocol://webappURI:port/contextPath",
                "Send the current configuration to the specified webappURI").Do(webAddr -> {
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
                "Populate the webapp with bare configuration and exit.").Do(v -> {
                noindex = true;
            });

            parser.on("--userPage", "=URL",
                "Base URL of the user Information provider.",
                "Example: \"http://www.myserver.org/viewProfile.jspa?username=\".",
                "Use \"none\" to disable link.").Do(v -> {
                cfg.setUserPage((String)v);
            });

            parser.on("--userPageSuffix", "=URL-suffix",
                "URL Suffix for the user Information provider. Default: \"\".").Do(suffix -> {
                cfg.setUserPageSuffix((String)suffix);
            });

            parser.on("-V", "--version", "Print version and quit.").Do(v -> {
                System.out.println(Info.getFullVersion());
                System.exit(0);
            });

            parser.on("-v", "--verbose", "Set logging level to INFO.").Do(v -> {
                verbose = true;
                LoggerUtil.setBaseConsoleLogLevel(Level.INFO);
            });

            parser.on("-W", "--writeConfig", "=/path/to/configuration",
                "Write the current configuration to the specified file",
                "(so that the web application can use the same configuration)").Do(configFile -> {
                configFilename = (String)configFile;
            });
        });

        // Need to read the configuration file first
        // so that options may be overwritten later.
        configure.parse(argv);

        LOGGER.log(Level.INFO, "Indexer options: {0}", Arrays.toString(argv));

        if (cfg == null) {
            cfg = new Configuration();
        }

        cfg.setHistoryEnabled(false);  // force user to turn on history capture

        argv = optParser.parse(argv);

        return argv;
    }

    private static void checkConfiguration() {
        env = RuntimeEnvironment.getInstance();

        if (noindex && (env.getConfigURI() == null || env.getConfigURI().isEmpty())) {
            die("Missing webappURI URL");
        }

        if (repositories.size() > 0 && !cfg.isHistoryEnabled()) {
            die("Repositories were specified however history is off");
        }
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
        fileSpec = fileSpec.toUpperCase();

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
     * Write configuration to a file
     * @param env runtime environment
     * @param filename file name to write the configuration to
     * @throws IOException if I/O exception occurred
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

        if (zapCache.isEmpty() && !env.validateUniversalCtags()) {
            throw new IndexerException("Didn't find Universal Ctags");
        }
        if (zapCache == null) {
            throw new IndexerException("Internal error, zapCache shouldn't be null");
        }

        // Projects need to be created first since when adding repositories below,
        // some of the project properties might be needed for that.
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
                String path = File.separator + name;
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
        
        if (searchRepositories || listRepoPaths || !zapCache.isEmpty()) {
            LOGGER.log(Level.INFO, "Scanning for repositories...");
            Statistics stats = new Statistics();
            env.setRepositories(env.getSourceRootPath());
            stats.report(LOGGER, String.format("Done scanning for repositories, found %d repositories",
                    env.getRepositories().size()));
            
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
                        System.out.println(String.format("%s (%s)", dir.substring(prefix.length()), info.getType()));
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

        // Even if history is disabled globally, it can be enabled for some repositories.
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
     * @param subFiles index just some subdirectories
     * @param progress object to receive notifications as indexer progress is made
     * @throws IOException if I/O exception occurred
     */
    public void doIndexerExecution(final boolean update, List<String> subFiles,
        IndexChangedListener progress)
            throws IOException {
        Statistics elapsed = new Statistics();
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        LOGGER.info("Starting indexing");

        IndexerParallelizer parallelizer = new IndexerParallelizer(env);

        if (subFiles == null || subFiles.isEmpty()) {
            if (update) {
                IndexDatabase.updateAll(parallelizer, progress);
            } else if (env.isOptimizeDatabase()) {
                IndexDatabase.optimizeAll(parallelizer);
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
                parallelizer.getFixedExecutor().submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (update) {
                                db.update(parallelizer);
                            } else if (optimize) {
                                db.optimize();
                            }
                        } catch (Throwable e) {
                            LOGGER.log(Level.SEVERE, "An error occurred while "
                                    + (update ? "updating" : "optimizing")
                                    + " index", e);
                        }
                    }
                });
            }
        }

        parallelizer.getFixedExecutor().shutdown();
        while (!parallelizer.getFixedExecutor().isTerminated()) {
            try {
                // Wait forever
                parallelizer.getFixedExecutor().awaitTermination(999,
                    TimeUnit.DAYS);
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
        try {
            parallelizer.close();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "parallelizer.close() failed", ex);
        }
        elapsed.report(LOGGER, "Done indexing data of all repositories");
    }

    public void refreshSearcherManagers(RuntimeEnvironment env, List<String> projects, String host) {
        LOGGER.log(Level.INFO, "Refreshing searcher managers to: {0}", host);

        env.signalTorefreshSearcherManagers(projects, host);
    }

    public void sendToConfigHost(RuntimeEnvironment env, String host) {
        LOGGER.log(Level.INFO, "Sending configuration to: {0}", host);
        try {
            env.writeConfiguration(host);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, String.format(
                    "Failed to send configuration to %s "
                    + "(is web application server running with opengrok deployed?)", host), ex);
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

    private Indexer() {
    }
}
