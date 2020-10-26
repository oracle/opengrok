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
 * Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.index;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.opengrok.indexer.analysis.Ctags;
import org.opengrok.indexer.analysis.CtagsValidator;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.BoundedBlockingObjectPool;
import org.opengrok.indexer.util.CtagsUtil;
import org.opengrok.indexer.util.LazilyInstantiate;
import org.opengrok.indexer.util.ObjectFactory;
import org.opengrok.indexer.util.ObjectPool;

/**
 * Represents a container for executors that enable parallelism for indexing
 * across projects and repositories and also within any {@link IndexDatabase}
 * instance -- with global limits for all execution.
 * <p>A fixed-thread pool is used for parallelism across repositories, and a
 * work-stealing {@link ForkJoinPool} is used for parallelism within any
 * {@link IndexDatabase}. Threads in the former pool are customers of the
 * latter, and the bulk of work is done in the latter pool. The work-stealing
 * {@link ForkJoinPool} makes use of a corresponding fixed pool of {@link Ctags}
 * instances.
 * <p>Additionally there are pools for executing for history, for renamings in
 * history, and for watching the {@link Ctags} instances for timing purposes.
 */
public class IndexerParallelizer implements AutoCloseable {

    private final RuntimeEnvironment env;
    private final int indexingParallelism;

    private LazilyInstantiate<ForkJoinPool> lzForkJoinPool;
    private LazilyInstantiate<ObjectPool<Ctags>> lzCtagsPool;
    private LazilyInstantiate<ExecutorService> lzFixedExecutor;
    private LazilyInstantiate<ExecutorService> lzHistoryExecutor;
    private LazilyInstantiate<ExecutorService> lzHistoryRenamedExecutor;
    private LazilyInstantiate<ExecutorService> lzCtagsWatcherExecutor;

    /**
     * Initializes a new instance using settings from the specified environment
     * instance.
     * @param env a defined instance
     */
    public IndexerParallelizer(RuntimeEnvironment env) {
        if (env == null) {
            throw new IllegalArgumentException("env is null");
        }
        this.env = env;
        /*
         * Save the following value explicitly because it must not change for
         * an IndexerParallelizer instance.
         */
        this.indexingParallelism = env.getIndexingParallelism();

        createLazyForkJoinPool();
        createLazyCtagsPool();
        createLazyFixedExecutor();
        createLazyHistoryExecutor();
        createLazyHistoryRenamedExecutor();
        createLazyCtagsWatcherExecutor();
    }

    /**
     * @return the fixedExecutor
     */
    public ExecutorService getFixedExecutor() {
        return lzFixedExecutor.get();
    }

    /**
     * @return the forkJoinPool
     */
    public ForkJoinPool getForkJoinPool() {
        return lzForkJoinPool.get();
    }

    /**
     * @return the ctagsPool
     */
    public ObjectPool<Ctags> getCtagsPool() {
        return lzCtagsPool.get();
    }

    /**
     * @return the ExecutorService used for history parallelism
     */
    public ExecutorService getHistoryExecutor() {
        return lzHistoryExecutor.get();
    }

    /**
     * @return the ExecutorService used for history-renamed parallelism
     */
    public ExecutorService getHistoryRenamedExecutor() {
        return lzHistoryRenamedExecutor.get();
    }

    /**
     * @return the Executor used for ctags parallelism
     */
    public ExecutorService getCtagsWatcherExecutor() {
        return lzCtagsWatcherExecutor.get();
    }

    /**
     * Calls {@link #bounce()}, which prepares for -- but does not start -- new
     * pools.
     */
    @Override
    public void close() {
        bounce();
    }

    /**
     * Shuts down the instance's executors if any of the getters were called,
     * releasing all resources; and prepares them to be called again to return
     * new instances.
     * <p>
     * N.b. this method is not thread-safe w.r.t. the getters, so care must be
     * taken that any scheduled work has been completed and that no other
     * thread might call those methods simultaneously with this method.
     * <p>
     * The JVM will await any instantiated thread pools until they are
     * explicitly shut down. A principle intention of this method is to
     * facilitate OpenGrok test classes that run serially. The non-test
     * processes using {@link IndexerParallelizer} -- i.e. {@code opengrok.jar}
     * indexer or opengrok-web -- would only need a one-way shutdown; but they
     * call this method satisfactorily too.
     */
    public void bounce() {
        bounceForkJoinPool();
        bounceFixedExecutor();
        bounceCtagsPool();
        bounceHistoryExecutor();
        bounceHistoryRenamedExecutor();
        bounceCtagsWatcherExecutor();
    }

    private void bounceForkJoinPool() {
        if (lzForkJoinPool.isActive()) {
            ForkJoinPool formerForkJoinPool = lzForkJoinPool.get();
            createLazyForkJoinPool();
            formerForkJoinPool.shutdown();
        }
    }

    private void bounceFixedExecutor() {
        if (lzFixedExecutor.isActive()) {
            ExecutorService formerFixedExecutor = lzFixedExecutor.get();
            createLazyFixedExecutor();
            formerFixedExecutor.shutdown();
        }
    }

    private void bounceCtagsPool() {
        if (lzCtagsPool.isActive()) {
            ObjectPool<Ctags> formerCtagsPool = lzCtagsPool.get();
            createLazyCtagsPool();
            formerCtagsPool.shutdown();
        }
    }

    private void bounceHistoryExecutor() {
        if (lzHistoryExecutor.isActive()) {
            ExecutorService formerHistoryExecutor = lzHistoryExecutor.get();
            createLazyHistoryExecutor();
            formerHistoryExecutor.shutdown();
        }
    }

    private void bounceHistoryRenamedExecutor() {
        if (lzHistoryRenamedExecutor.isActive()) {
            ExecutorService formerHistoryRenamedExecutor = lzHistoryRenamedExecutor.get();
            createLazyHistoryRenamedExecutor();
            formerHistoryRenamedExecutor.shutdown();
        }
    }

    private void bounceCtagsWatcherExecutor() {
        if (lzCtagsWatcherExecutor.isActive()) {
            ExecutorService formerCtagsWatcherExecutor = lzCtagsWatcherExecutor.get();
            createLazyCtagsWatcherExecutor();
            formerCtagsWatcherExecutor.shutdown();
        }
    }

    private void createLazyForkJoinPool() {
        lzForkJoinPool = LazilyInstantiate.using(() ->
                new ForkJoinPool(indexingParallelism));
    }

    private void createLazyCtagsPool() {
        lzCtagsPool = LazilyInstantiate.using(() ->
                new BoundedBlockingObjectPool<>(indexingParallelism,
                        new CtagsValidator(), new CtagsObjectFactory()));
    }

    private void createLazyCtagsWatcherExecutor() {
        lzCtagsWatcherExecutor = LazilyInstantiate.using(() ->
                new ScheduledThreadPoolExecutor(1, runnable -> {
                    Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                    thread.setName("ctags-watcher-" + thread.getId());
                    return thread;
                }));
    }

    private void createLazyFixedExecutor() {
        lzFixedExecutor = LazilyInstantiate.using(() ->
                Executors.newFixedThreadPool(indexingParallelism));
    }

    private void createLazyHistoryExecutor() {
        lzHistoryExecutor = LazilyInstantiate.using(() ->
                Executors.newFixedThreadPool(env.getHistoryParallelism()));
    }

    private void createLazyHistoryRenamedExecutor() {
        lzHistoryRenamedExecutor = LazilyInstantiate.using(() ->
                Executors.newFixedThreadPool(env.getHistoryRenamedParallelism()));
    }

    private class CtagsObjectFactory implements ObjectFactory<Ctags> {

        public Ctags createNew() {
            return CtagsUtil.newInstance(env);
        }
    }
}
