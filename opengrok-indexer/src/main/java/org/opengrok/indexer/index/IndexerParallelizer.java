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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.index;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Logger;
import org.opengrok.indexer.analysis.Ctags;
import org.opengrok.indexer.analysis.CtagsValidator;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.BoundedBlockingObjectPool;
import org.opengrok.indexer.util.ObjectFactory;
import org.opengrok.indexer.util.ObjectPool;

/**
 * Represents a container for executors that enable parallelism for indexing
 * across projects and repositories and also within any {@link IndexDatabase}
 * instance -- with global limits for all execution.
 * <p>A fixed-thread pool is used for parallelism across repositories, and a
 * work-stealing {@link ForkJoinPool} is used for parallelism within any
 * {@link IndexDatabase}. Threads in the former pool are customers of the
 * latter, and the bulk of work is done in the latter pool.
 */
public class IndexerParallelizer implements AutoCloseable {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(IndexerParallelizer.class);

    private final ExecutorService fixedExecutor;
    private final ForkJoinPool forkJoinPool;
    private final ObjectPool<Ctags> ctagsPool;

    /**
     * Initializes a new instance using settings from the specified environment
     * instance.
     * @param env a defined instance
     */
    public IndexerParallelizer(RuntimeEnvironment env) {

        int indexingParallelism = env.getIndexingParallelism();

        // The order of the following is important.
        this.fixedExecutor = Executors.newFixedThreadPool(indexingParallelism);
        this.forkJoinPool = new ForkJoinPool(indexingParallelism);
        this.ctagsPool = new BoundedBlockingObjectPool<>(indexingParallelism,
            new CtagsValidator(), new CtagsObjectFactory(env));
    }

    /**
     * @return the fixedExecutor
     */
    public ExecutorService getFixedExecutor() {
        return fixedExecutor;
    }

    /**
     * @return the forkJoinPool
     */
    public ForkJoinPool getForkJoinPool() {
        return forkJoinPool;
    }

    /**
     * @return the ctagsPool
     */
    public ObjectPool<Ctags> getCtagsPool() {
        return ctagsPool;
    }

    @Override
    public void close() {
        if (ctagsPool != null) {
            ctagsPool.shutdown();
        }
        if (forkJoinPool != null) {
            forkJoinPool.shutdown();
        }
        if (fixedExecutor != null) {
            fixedExecutor.shutdown();
        }
    }

    /**
     * Creates a new instance, and attempts to configure it from the specified
     * environment instance.
     * @param env
     * @return a defined instance, possibly with a {@code null} ctags binary
     * setting if a value was not available from {@link RuntimeEnvironment}.
     */
    private static Ctags getNewCtags(RuntimeEnvironment env) {
        Ctags ctags = new Ctags();

        String ctagsBinary = env.getCtags();
        if (ctagsBinary == null) {
            LOGGER.severe("Unable to run ctags!" +
                " searching definitions will not work!");
        } else {
            ctags.setBinary(ctagsBinary);

            String filename = env.getCTagsExtraOptionsFile();
            if (filename != null) {
                ctags.setCTagsExtraOptionsFile(filename);
            }
        }
        return ctags;
    }

    private class CtagsObjectFactory implements ObjectFactory<Ctags> {

        private final RuntimeEnvironment env;

        public CtagsObjectFactory(RuntimeEnvironment env) {
            this.env = env;
        }

        public Ctags createNew() {
            return getNewCtags(env);
        }
    }
}
