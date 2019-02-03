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
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.analysis;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opengrok.indexer.analysis.ada.AdaAnalyzerFactory;
import org.opengrok.indexer.analysis.archive.BZip2AnalyzerFactory;
import org.opengrok.indexer.analysis.archive.GZIPAnalyzerFactory;
import org.opengrok.indexer.analysis.archive.TarAnalyzerFactory;
import org.opengrok.indexer.analysis.archive.ZipAnalyzerFactory;
import org.opengrok.indexer.analysis.c.CAnalyzerFactory;
import org.opengrok.indexer.analysis.c.CxxAnalyzerFactory;
import org.opengrok.indexer.analysis.clojure.ClojureAnalyzerFactory;
import org.opengrok.indexer.analysis.csharp.CSharpAnalyzerFactory;
import org.opengrok.indexer.analysis.data.IgnorantAnalyzerFactory;
import org.opengrok.indexer.analysis.data.ImageAnalyzerFactory;
import org.opengrok.indexer.analysis.document.MandocAnalyzerFactory;
import org.opengrok.indexer.analysis.document.TroffAnalyzerFactory;
import org.opengrok.indexer.analysis.eiffel.EiffelAnalyzerFactory;
import org.opengrok.indexer.analysis.erlang.ErlangAnalyzerFactory;
import org.opengrok.indexer.analysis.executables.ELFAnalyzerFactory;
import org.opengrok.indexer.analysis.executables.JarAnalyzerFactory;
import org.opengrok.indexer.analysis.executables.JavaClassAnalyzerFactory;
import org.opengrok.indexer.analysis.fortran.FortranAnalyzerFactory;
import org.opengrok.indexer.analysis.golang.GolangAnalyzerFactory;
import org.opengrok.indexer.analysis.haskell.HaskellAnalyzerFactory;
import org.opengrok.indexer.analysis.java.JavaAnalyzerFactory;
import org.opengrok.indexer.analysis.javascript.JavaScriptAnalyzerFactory;
import org.opengrok.indexer.analysis.json.JsonAnalyzerFactory;
import org.opengrok.indexer.analysis.kotlin.KotlinAnalyzerFactory;
import org.opengrok.indexer.analysis.lisp.LispAnalyzerFactory;
import org.opengrok.indexer.analysis.lua.LuaAnalyzerFactory;
import org.opengrok.indexer.analysis.pascal.PascalAnalyzerFactory;
import org.opengrok.indexer.analysis.perl.PerlAnalyzerFactory;
import org.opengrok.indexer.analysis.php.PhpAnalyzerFactory;
import org.opengrok.indexer.analysis.plain.PlainAnalyzerFactory;
import org.opengrok.indexer.analysis.plain.XMLAnalyzerFactory;
import org.opengrok.indexer.analysis.powershell.PowershellAnalyzerFactory;
import org.opengrok.indexer.analysis.python.PythonAnalyzerFactory;
import org.opengrok.indexer.analysis.ruby.RubyAnalyzerFactory;
import org.opengrok.indexer.analysis.rust.RustAnalyzerFactory;
import org.opengrok.indexer.analysis.scala.ScalaAnalyzerFactory;
import org.opengrok.indexer.analysis.sh.ShAnalyzerFactory;
import org.opengrok.indexer.analysis.sql.PLSQLAnalyzerFactory;
import org.opengrok.indexer.analysis.sql.SQLAnalyzerFactory;
import org.opengrok.indexer.analysis.swift.SwiftAnalyzerFactory;
import org.opengrok.indexer.analysis.tcl.TclAnalyzerFactory;
import org.opengrok.indexer.analysis.uue.UuencodeAnalyzerFactory;
import org.opengrok.indexer.analysis.vb.VBAnalyzerFactory;
import org.opengrok.indexer.analysis.verilog.VerilogAnalyzerFactory;
import org.opengrok.indexer.framework.PluginFramework;
import org.opengrok.indexer.logger.LoggerFactory;

/**
 * A framework for loading the analyzers from .jar or .class files.
 */
public class AnalyzerFramework extends PluginFramework<IAnalyzerPlugin, AnalyzersInfo> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyzerFramework.class);

    /**
     * The default {@code FileAnalyzerFactory} instance.
     */
    public static final AnalyzerFactory DEFAULT_ANALYZER_FACTORY = new FileAnalyzerFactory();

    private static final IAnalyzerPlugin[] DEFAULT_PLUGINS = {
            () -> DEFAULT_ANALYZER_FACTORY,
            () -> new IgnorantAnalyzerFactory(),
            () -> new BZip2AnalyzerFactory(),
            () -> new XMLAnalyzerFactory(),
            () -> MandocAnalyzerFactory.DEFAULT_INSTANCE,
            () -> TroffAnalyzerFactory.DEFAULT_INSTANCE,
            () -> new ELFAnalyzerFactory(),
            () -> JavaClassAnalyzerFactory.DEFAULT_INSTANCE,
            () -> new ImageAnalyzerFactory(),
            () -> JarAnalyzerFactory.DEFAULT_INSTANCE,
            () -> ZipAnalyzerFactory.DEFAULT_INSTANCE,
            () -> new TarAnalyzerFactory(),
            () -> new CAnalyzerFactory(),
            () -> new CSharpAnalyzerFactory(),
            () -> new VBAnalyzerFactory(),
            () -> new CxxAnalyzerFactory(),
            () -> new ErlangAnalyzerFactory(),
            () -> new ShAnalyzerFactory(),
            () -> new PowershellAnalyzerFactory(),
            () -> PlainAnalyzerFactory.DEFAULT_INSTANCE,
            () -> new UuencodeAnalyzerFactory(),
            () -> new GZIPAnalyzerFactory(),
            () -> new JavaAnalyzerFactory(),
            () -> new JavaScriptAnalyzerFactory(),
            () -> new KotlinAnalyzerFactory(),
            () -> new SwiftAnalyzerFactory(),
            () -> new JsonAnalyzerFactory(),
            () -> new PythonAnalyzerFactory(),
            () -> new RustAnalyzerFactory(),
            () -> new PerlAnalyzerFactory(),
            () -> new PhpAnalyzerFactory(),
            () -> new LispAnalyzerFactory(),
            () -> new TclAnalyzerFactory(),
            () -> new ScalaAnalyzerFactory(),
            () -> new ClojureAnalyzerFactory(),
            () -> new SQLAnalyzerFactory(),
            () -> new PLSQLAnalyzerFactory(),
            () -> new FortranAnalyzerFactory(),
            () -> new HaskellAnalyzerFactory(),
            () -> new GolangAnalyzerFactory(),
            () -> new LuaAnalyzerFactory(),
            () -> new PascalAnalyzerFactory(),
            () -> new AdaAnalyzerFactory(),
            () -> new RubyAnalyzerFactory(),
            () -> new EiffelAnalyzerFactory(),
            () -> new VerilogAnalyzerFactory()
    };

    /**
     * Instance of analyzers info, which should be used in the outside world.
     */
    private AtomicReference<AnalyzersInfo> analyzersInfo = new AtomicReference<>(new AnalyzersInfo());

    /**
     * List of lambdas to perform customizations to the analyzers info when the {@link #reload()}
     * is in progress. This is needed because customizations would be lost with next
     * call to {@link #reload()}.
     *
     * @see #reload()
     */
    private List<Consumer<AnalyzersInfo>> customizations = new LinkedList<>();


    /**
     * Construct the analyzer framework with a plugin directory.
     * <p>
     * NOTE: This call ensures the framework is calling {@link #reload()} with default plugins.
     *
     * @param pluginDirectory directory with plugins
     * @see #reload()
     */
    public AnalyzerFramework(String pluginDirectory) {
        super(IAnalyzerPlugin.class, pluginDirectory);
        reload();
    }

    /**
     * Get the current analyzers info.
     *
     * @return current instance of analyzers info, never {@code null}.
     */
    public AnalyzersInfo getAnalyzersInfo() {
        return analyzersInfo.get();
    }

    /**
     * Prepare the analyzer plugin and register the analyzer to the analyzers info.
     *
     * @param localInfo an analyzers info used for loading the plugins
     * @param plugin    the loaded plugin interface with the factory.
     */
    @Override
    protected void classLoaded(AnalyzersInfo localInfo, IAnalyzerPlugin plugin) {
        final AnalyzerFactory analyzer = plugin.getFactory();

        registerAnalyzer(localInfo, analyzer);

        if (analyzer.getName() != null && !analyzer.getName().isEmpty()) {
            localInfo.fileTypeDescriptions.put(analyzer.getAnalyzer().getFileTypeName(), analyzer.getName());
        }

        LOGGER.log(Level.INFO, "An analyzer factory {0} has been loaded.", analyzer.getClass().getCanonicalName());
    }

    /**
     * Prepare the loading info and load the default plugins.
     *
     * @return an analyzers info used for loading the plugins
     * @see #DEFAULT_PLUGINS
     */
    @Override
    protected AnalyzersInfo beforeReload() {
        final AnalyzersInfo localInfo = new AnalyzersInfo();
        for (IAnalyzerPlugin plugin : DEFAULT_PLUGINS) {
            classLoaded(localInfo, plugin);
        }
        return localInfo;
    }

    /**
     * Run the customizations and swap the instance in {@code analyzersInfo}.
     *
     * @param localInfo an analyzers info used for loading the plugins
     * @see #customizations
     * @see #analyzersInfo
     */
    @Override
    protected void afterReload(AnalyzersInfo localInfo) {
        if (getPluginDirectory() == null || !getPluginDirectory().isDirectory() || !getPluginDirectory().canRead()) {
            LOGGER.log(Level.INFO, "No plugin directory for analyzers.");
        }

        // apply custom settings for this framework
        customizations.stream().forEach(customization -> customization.accept(localInfo));

        this.analyzersInfo.set(localInfo.freeze());
    }

    /**
     * Register the analyzer factory in the framework.
     * <p>
     * NOTE: This method calls {@link #reload()} so it may take some time to complete.
     *
     * @param factory the analyzer factory
     */
    public void registerAnalyzer(AnalyzerFactory factory) {
        customizations.add(analyzersInfo -> {
            registerAnalyzer(analyzersInfo, factory);
        });

        reload();
    }

    /**
     * Register a {@code FileAnalyzerFactory} instance into the given {@code analyzersInfo}.
     */
    private static void registerAnalyzer(AnalyzersInfo analyzersInfo, AnalyzerFactory factory) {
        for (String name : factory.getFileNames()) {
            AnalyzerFactory old = analyzersInfo.fileNames.put(name, factory);
            assert old == null :
                    "name '" + name + "' used in multiple analyzers";
        }
        for (String prefix : factory.getPrefixes()) {
            AnalyzerFactory old = analyzersInfo.prefixes.put(prefix, factory);
            assert old == null :
                    "prefix '" + prefix + "' used in multiple analyzers";
        }
        for (String suffix : factory.getSuffixes()) {
            AnalyzerFactory old = analyzersInfo.extensions.put(suffix, factory);
            assert old == null :
                    "suffix '" + suffix + "' used in multiple analyzers";
        }
        for (String magic : factory.getMagicStrings()) {
            AnalyzerFactory old = analyzersInfo.magics.put(magic, factory);
            assert old == null :
                    "magic '" + magic + "' used in multiple analyzers";
        }

        analyzersInfo.matchers.addAll(factory.getMatchers());
        analyzersInfo.factories.add(factory);

        AbstractAnalyzer fa = factory.getAnalyzer();
        String fileTypeName = fa.getFileTypeName();
        analyzersInfo.filetypeFactories.put(fileTypeName, factory);
        analyzersInfo.analyzerVersions.put(fileTypeName, fa.getVersionNo());
    }


    /**
     * Instruct the AnalyzerGuru to use a given analyzer for a given file prefix.
     * <p>
     * NOTE: This method calls {@link #reload()} so it may take some time to complete.
     *
     * @param prefix  the file prefix to add
     * @param factory a factory which creates the analyzer to use for the given
     *                extension (if you pass null as the analyzer, you will disable the
     *                analyzer used for that extension)
     */
    public void addPrefix(String prefix, AnalyzerFactory factory) {
        customizations.add(analyzersInfo -> {
            AnalyzerFactory oldFactory;
            if (factory == null) {
                oldFactory = analyzersInfo.prefixes.remove(prefix);
                LOGGER.log(Level.INFO,
                        "Removing a mapping for prefix {0}{1}",
                        new Object[]{
                                prefix,
                                oldFactory != null ? String.format(". And has removed existing factory '%s'.", oldFactory.getClass().getCanonicalName()) : ""
                        }
                );

            } else {
                oldFactory = analyzersInfo.prefixes.put(prefix, factory);
                LOGGER.log(Level.INFO,
                        "Adding a factory {0} for matching prefix {1}{2}",
                        new Object[]{
                                factory.getClass().getCanonicalName(),
                                prefix,
                                oldFactory != null ? String.format(". And has replaced existing factory '%s'.", oldFactory.getClass().getCanonicalName()) : ""

                        }
                );
            }

            if (factoriesDifferent(factory, oldFactory)) {
                analyzersInfo.customizations.add("p:" + prefix);
            }
        });

        reload();
    }

    /**
     * Instruct the AnalyzerGuru to use a given analyzer for a given file extension.
     * <p>
     * NOTE: This method calls {@link #reload()} so it may take some time to complete.
     *
     * @param extension the file-extension to add
     * @param factory   a factory which creates the analyzer to use for the given
     *                  extension (if you pass null as the analyzer, you will disable the
     *                  analyzer used for that extension)
     */
    public void addExtension(String extension, AnalyzerFactory factory) {
        customizations.add(analyzersInfo -> {
            AnalyzerFactory oldFactory;
            if (factory == null) {
                oldFactory = analyzersInfo.extensions.remove(extension);
                LOGGER.log(Level.INFO,
                        "Removing a mapping for suffix {0}{1}",
                        new Object[]{
                                extension,
                                oldFactory != null ? String.format(". And has removed existing factory '%s'.", oldFactory.getClass().getCanonicalName()) : ""
                        }
                );
            } else {
                oldFactory = analyzersInfo.extensions.put(extension, factory);
                LOGGER.log(Level.INFO,
                        "Adding a factory {0} for matching suffix {1}{2}",
                        new Object[]{
                                factory.getClass().getCanonicalName(),
                                extension,
                                oldFactory != null ? String.format(". And has replaced existing factory '%s'.", oldFactory.getClass().getCanonicalName()) : ""
                        }
                );
            }

            if (factoriesDifferent(factory, oldFactory)) {
                analyzersInfo.customizations.add("s:" + extension);
            }
        });

        reload();
    }


    /**
     * Free resources associated with all registered analyzers.
     */
    public void returnAnalyzers() {
        getAnalyzersInfo().factories.forEach(AnalyzerFactory::returnAnalyzer);
    }

    /**
     * Decode about two factory instances if they are different.
     *
     * @param a the first instance
     * @param b the second instance
     * @return true if we consider them different, false otherwise
     */
    private static boolean factoriesDifferent(AnalyzerFactory a, AnalyzerFactory b) {
        String a_name = null;
        if (a != null) {
            a_name = a.getName();
            if (a_name == null) {
                a_name = a.getClass().getSimpleName();
            }
        }
        String b_name = null;
        if (b != null) {
            b_name = b.getName();
            if (b_name == null) {
                b_name = b.getClass().getSimpleName();
            }
        }
        if (a_name == null && b_name == null) {
            return false;
        }
        return a_name == null || b_name == null || !a_name.equals(b_name);
    }

}
