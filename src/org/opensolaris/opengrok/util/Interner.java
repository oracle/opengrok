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
 * Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.util;

import java.util.HashMap;

/**
 * <p>
 * Helper class that interns objects, that is, returns a canonical
 * representation of the objects. This works similar to
 * {@link java.lang.String#intern}, but it stores the canonical objects on
 * the heap instead of in the permgen space to address bug #15956.
 * </p>
 *
 * <p>
 * Instances of this class are not thread safe.
 * </p>
 *
 * <p>
 * In contrast to {@link java.lang.String#intern}, this class does not attempt
 * to make objects that are not referenced anymore eligible for garbage
 * collection. Hence, references to instances of this class should not be
 * held longer than necessary.
 * </p>
 *
 * @param <T> the type of the objects being interned by the instance
 */
public class Interner<T> {

    /** Map of interned objects. Key and value contain the same object. */
    private final HashMap<T, T> map = new HashMap<T, T>();

    /**
     * <p>
     * Intern an object and return a canonical instance of it. For two objects
     * {@code o1} and {@code o2}, the following always evaluates to
     * {@code true}:
     * </p>
     *
     * <pre>
     *     ( o1 == null ) ?
     *         ( intern(o1) == null ) :
     *         o1.equals(o2) == ( intern(o1) == intern(o2) )
     * </pre>
     *
     * @param instance the object to intern
     * @return a canonical representation of {@code instance}
     */
    public T intern(T instance) {
        if (instance == null) {
            return null;
        }

        T interned = map.get(instance);

        if (interned == null) {
            interned = instance;
            map.put(interned, interned);
        }

        return interned;
    }
}
