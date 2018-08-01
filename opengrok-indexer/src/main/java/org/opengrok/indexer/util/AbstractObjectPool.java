/*
 * The contents of this file are Copyright (c) 2012, Swaranga Sarma, DZone MVB
 * made available under free license,
 * http://javawithswaranga.blogspot.com/2011/10/generic-and-concurrent-object-pool.html
 * https://dzone.com/articles/generic-and-concurrent-object : "Feel free to use
 * it, change it, add more implementations. Happy coding!"
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.util;

/**
 * Represents an abstract implementation of {@link ObjectPool} that defines the
 * procedure of returning an object to the pool.
 * @author Swaranga
 * @param <T> the type of pooled objects.
 */
public abstract class AbstractObjectPool<T> implements ObjectPool<T> {

    /**
     * Returns the object to the pool.
     * The method first validates the object if it is
     * re-usable and then puts returns it to the pool.
     *
     * If the object validation fails,
     * {@link #handleInvalidReturn(java.lang.Object)} is called.
     * Some implementations
     * will try to create a new object
     * and put it into the pool; however
     * this behaviour is subject to change
     * from implementation to implementation.
     */
    public final void release(T t) {
        if (isValid(t)) {
            returnToPool(t);
        } else {
            handleInvalidReturn(t);
        }
    }

    protected abstract void handleInvalidReturn(T t);

    protected abstract void returnToPool(T t);

    protected abstract boolean isValid(T t);
}
