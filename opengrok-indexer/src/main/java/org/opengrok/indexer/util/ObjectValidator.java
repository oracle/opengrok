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
  * Represents an API for validation of an object of the pool and for its
  * subsequently cleanup to invalidate it.
  * @author Swaranga
  * @param <T> the type of objects to validate and cleanup
  */
public interface ObjectValidator<T> {

    /**
     * Checks whether the object is valid.
     * @param t the object to check.
     * @return true if the object is valid
     */
    public boolean isValid(T t);

    /**
     * Performs any cleanup activities
     * before discarding the object.
     * For example before discarding
     * database connection objects,
     * the pool will want to close the connections.
     * This is done via the
     * invalidate() method.
     * @param t the object to cleanup
     */
    public void invalidate(T t);
}
