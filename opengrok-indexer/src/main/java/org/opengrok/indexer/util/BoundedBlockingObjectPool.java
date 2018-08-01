/*
 * The contents of this file are Copyright (c) 2012, Swaranga Sarma, DZone MVB
 * made available under free license,
 * http://javawithswaranga.blogspot.com/2011/10/generic-and-concurrent-object-pool.html
 * https://dzone.com/articles/generic-and-concurrent-object : "Feel free to use
 * it, change it, add more implementations. Happy coding!"
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    private final BlockingQueue<T> objects;
    private final ObjectValidator<T> validator;
    private final ObjectFactory<T> objectFactory;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private volatile boolean shutdownCalled;

    public BoundedBlockingObjectPool(int size, ObjectValidator<T> validator,
        ObjectFactory<T> objectFactory) {

        this.objectFactory = objectFactory;
        this.size = size;
        this.validator = validator;

        objects = new LinkedBlockingQueue<>(size);
        initializeObjects();
    }

    @Override
    public T get(long timeOut, TimeUnit unit) {
        if (!shutdownCalled) {
            try {
                return objects.poll(timeOut, unit);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            return null;
        }
        throw new IllegalStateException("Object pool is already shutdown");
    }

    @Override
    public T get() {
        if (!shutdownCalled) {
            try {
                return objects.take();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            return null;
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
            executor.submit(new ObjectReturner<T>(objects, t));
        }
    }

    /**
     * Creates a new instance, and returns that instead to the pool.
     * @param t 
     */
    @Override
    protected void handleInvalidReturn(T t) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "createNew() to handle invalid {0}",
                t.getClass());
        }

        t = objectFactory.createNew();
        executor.submit(new ObjectReturner<T>(objects, t));
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

    private class ObjectReturner<E> implements Callable<Void> {
        private final BlockingQueue<E> queue;
        private final E e;

        public ObjectReturner(BlockingQueue<E> queue, E e) {
            this.queue = queue;
            this.e = e;
        }

        @Override
        public Void call() {
            while (true) {
                try {
                    queue.put(e);
                    break;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }

            return null;
        }
    }
}
