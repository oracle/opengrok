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
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Comparator;
import java.util.TreeSet;

/**
 *
 * @author kotal
 */
public class Scopes implements Serializable {
    private static final long serialVersionUID = 1191703801007779489L;
    
    public static class Scope implements Serializable {
        private static final long serialVersionUID = 1191703801007779489L;

        public int lineFrom;
        public int lineTo;
        public String name;
        public String scope;

        public Scope(int lineFrom, int lineTo, String name, String scope) {
            this.lineFrom = lineFrom;
            this.lineTo = lineTo;
            this.name = name;
            this.scope = scope;
        }
        
        public Scope(int lineFrom) {
            this.lineFrom = lineFrom;
        }
        
        public String getName() {
            return name; //(scope == null ? name : scope + "::" + name) + "()";
        }
    }
    
    public static class ScopeComparator implements Comparator<Scope> {
        @Override
        public int compare(Scope o1, Scope o2) {
            return o1.lineFrom < o2.lineFrom ? -1 : o1.lineFrom > o2.lineFrom ? 1 : 0;
        }
    }
    
    // default global scope
    private static Scope globalScope = new Scope(0, 0, "global", "");
    
    // tree of scopes sorted by starting line
    private TreeSet<Scope> scopes = new TreeSet<>(new ScopeComparator());
    
    public Scopes() {        
    }
    
    public void addScope(Scope scope) {
        scopes.add(scope);
    }
    
    public Scope getScope(int line) {
        // find closest scope that starts before or on given line
        Scope s = scopes.lower(new Scope(line+1));        
        return (s != null && s.lineTo >= line) ? s : globalScope;
    }
    
    /**
     * Create a binary representation of this object.
     * @return a byte array representing this object
     * @throws IOException if an error happens when writing to the array
     */
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(this);
        return bytes.toByteArray();
    }

    /**
     * Deserialize a binary representation of a {@code Definitions} object.
     * @param bytes a byte array containing the {@code Definitions} object
     * @return a {@code Definitions} object
     * @throws IOException if an I/O error happens when reading the array
     * @throws ClassNotFoundException if the class definition for an object
     * stored in the byte array cannot be found
     * @throws ClassCastException if the array contains an object of another
     * type than {@code Definitions}
     */
    public static Scopes deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        ObjectInputStream in =
                new ObjectInputStream(new ByteArrayInputStream(bytes));
        return (Scopes) in.readObject();
    }
}
