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
 * Represents an API for the mechanism to create
 * new objects to be used in an object pool.
 * @author Swaranga
 * @param <T> the type of object to create.
 */
public interface ObjectFactory<T> {

    /**
     * Returns a new instance of an object of type T.
     * @return T an new instance of the object of type T
     */
    public abstract T createNew();
}
