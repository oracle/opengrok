/*
 * The contents of this file are Copyright (c) 2012, Swaranga Sarma, DZone MVB
 * made available under free license,
 * http://javawithswaranga.blogspot.com/2011/10/generic-and-concurrent-object-pool.html
 * https://dzone.com/articles/generic-and-concurrent-object : "Feel free to use
 * it, change it, add more implementations. Happy coding!"
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.util;

import java.util.concurrent.TimeUnit;

/**
 * Represents an API for a pool of objects that makes the
 * requesting threads wait if no object is available.
 * @author Swaranga
 * @param <T> the type of objects to pool.
 */
public interface BlockingObjectPool<T> extends ObjectPool<T> {

    /**
     * Returns an instance of type T from the pool,
     * waiting up to the
     * specified wait time if necessary
     * for an object to become available..
     * <p>
     * The call is a blocking call,
     * and client threads are made to wait
     * for time until an object is available
     * or until the timeout occurs.
     * The call implements a fairness algorithm
     * that ensures that an FCFS service is implemented.
     * <p>
     * Clients are advised to react to {@link InterruptedException}.
     * If the thread is interrupted while waiting
     * for an object to become available,
     * most implementations
     * set the interrupted state of the thread
     * to <code>true</code> and returns null.
     * However this is subject to change
     * from implementation to implementation.
     *
     * @param time amount of time to wait before giving up,
     *   in units of {@code unit}
     * @param unit a {@code TimeUnit} determining
     *   how to interpret the {@code timeout} parameter
     * @return T an instance of the Object
     * of type T from the pool.
     * @throws InterruptedException
     * if interrupted while waiting
     */
    T get(long time, TimeUnit unit) throws InterruptedException;
}
