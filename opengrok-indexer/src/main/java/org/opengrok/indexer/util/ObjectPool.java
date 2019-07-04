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
 * Represents an API for a cached pool of objects.
 * @author Swaranga
 * @param <T> the type of object to pool.
 */
public interface ObjectPool<T> {

    /**
     * Gets an instance from the pool.
     * The call may be a blocking one or a non-blocking one
     * and that is determined by the implementation.
     * <p>
     * If the call is a blocking call,
     * the call returns immediately with a valid object
     * if available, else the thread is made to wait
     * until an object becomes available.
     * In case of a blocking call,
     * it is advised that clients react
     * to {@link InterruptedException} which might be thrown
     * when the thread waits for an object to become available.
     * <p>
     * If the call is a non-blocking one,
     * the call returns immediately irrespective of
     * whether an object is available or not.
     * If any object is available the call returns it
     * else the call returns <code>null</code>.
     * <p>
     * The validity of the objects are determined using the
     * {@link ObjectValidator} interface, such that
     * an object <code>o</code> is valid if
     * <code> ObjectValidator.isValid(o) == true </code>.
     * @return T one of the pooled objects.
     */
    T get();

    /**
     * Releases the object and puts it back to the pool.
     * The mechanism of putting the object back to the pool is
     * generally asynchronous,
     * however future implementations might differ.
     * @param t the object to return to the pool
     */
    void release(T t);

    /**
     * Shuts down the pool. In essence this call will not
     * accept any more requests
     * and will release all resources.
     * Releasing resources are done
     * via the {@link ObjectValidator#invalidate(java.lang.Object)}
     * method.
     */
    void shutdown();
}
