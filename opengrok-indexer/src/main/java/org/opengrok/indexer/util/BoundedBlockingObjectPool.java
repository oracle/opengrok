/*
 * The contents of this file are Copyright (c) 2012, Swaranga Sarma, DZone MVB
 * made available under free license,
 * http://javawithswaranga.blogspot.com/2011/10/generic-and-concurrent-object-pool.html
 * https://dzone.com/articles/generic-and-concurrent-object : "Feel free to use
 * it, change it, add more implementations. Happy coding!"
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 */

package org.opengrok.indexer.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opengrok.indexer.configuration.OpenGrokThreadFactory;
import org.opengrok.indexer.logger.LoggerFactory;

/**
 * Represents a subclass of {@link AbstractObjectPool} and implementation of
 * {@link BlockingObjectPool} with a defined limit of objects and a helper
 * to validate instances on {@link #release(java.lang.Object)}.
 * <p>An object failing validation is discarded, and a new one is created and
 * made available.
 * @author Swaranga
 * @param <T> the type of objects to pool.
 */
public final class BoundedBlockingObjectPool<T> extends AbstractObjectPool<T>
        implements BlockingObjectPool<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        BoundedBlockingObjectPool.class);

    private final int size;
    private final LinkedBlockingDeque<T> objects;
    private final ObjectValidator<T> validator;
    private final ObjectFactory<T> objectFactory;
    private final ExecutorService executor = Executors.newCachedThreadPool(new OpenGrokThreadFactory("bounded"));
    private volatile boolean puttingLast;
    private volatile boolean shutdownCalled;

    public BoundedBlockingObjectPool(int size, ObjectValidator<T> validator,
        ObjectFactory<T> objectFactory) {

        this.objectFactory = objectFactory;
        this.size = size;
        this.validator = validator;

        objects = new LinkedBlockingDeque<>(size);
        initializeObjects();
    }

    @Override
    public T get(long timeOut, TimeUnit unit) {
        if (!shutdownCalled) {
            T ret = null;
            try {
                ret = objects.pollFirst(timeOut, unit);
                /*
                 * When the queue first empties, switch to a strategy of putting
                 * returned objects last instead of first.
                 */
                if (!puttingLast && objects.size() < 1) {
                    puttingLast = true;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            return ret;
        }
        throw new IllegalStateException("Object pool is already shutdown");
    }

    @Override
    public T get() {
        if (!shutdownCalled) {
            T ret = null;
            try {
                ret = objects.takeFirst();
                /*
                 * When the queue first empties, switch to a strategy of putting
                 * returned objects last instead of first.
                 */
                if (!puttingLast && objects.size() < 1) {
                    puttingLast = true;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            return ret;
        }
        throw new IllegalStateException("Object pool is already shutdown");
    }

    @Override
    public void shutdown() {
        shutdownCalled = true;
        executor.shutdownNow();
        clearResources();
    }

    private void clearResources() {
        for (T t : objects) {
            validator.invalidate(t);
        }
    }

    @Override
    protected void returnToPool(T t) {
        if (validator.isValid(t)) {
            executor.submit(new ObjectReturner<>(objects, t, puttingLast));
        }
    }

    /*
     * Creates a new instance, and returns that instead to the pool.
     */
    @Override
    protected void handleInvalidReturn(T t) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "createNew() to handle invalid {0}",
                t.getClass());
        }

        t = objectFactory.createNew();
        executor.submit(new ObjectReturner<>(objects, t, puttingLast));
    }

    @Override
    protected boolean isValid(T t) {
        return validator.isValid(t);
    }

    private void initializeObjects() {
        for (int i = 0; i < size; i++) {
            objects.add(objectFactory.createNew());
        }
    }

    private static class ObjectReturner<E> implements Callable<Void> {
        private final LinkedBlockingDeque<E> queue;
        private final E e;
        private final boolean puttingLast;

        ObjectReturner(LinkedBlockingDeque<E> queue, E e, boolean puttingLast) {
            this.queue = queue;
            this.e = e;
            this.puttingLast = puttingLast;
        }

        @Override
        public Void call() {
            while (true) {
                try {
                    if (puttingLast) {
                        queue.putLast(e);
                    } else {
                        queue.putFirst(e);
                    }
                    break;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }

            return null;
        }
    }
}
