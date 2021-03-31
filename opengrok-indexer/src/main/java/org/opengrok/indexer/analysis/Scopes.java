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
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.analysis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.TreeSet;

/**
 *
 * @author Tomas Kotal
 */
public class Scopes implements Serializable {

    private static final long serialVersionUID = 1191703801007779489L;

    /**
     * Note: this class has a natural ordering that is inconsistent with equals.
     */
    public static class Scope implements Serializable, Comparable<Scope> {

        private static final long serialVersionUID = 1191703801007779489L;

        private int lineFrom;
        private int lineTo;
        private String name;
        private String namespace;
        private String signature;

        public Scope(int lineFrom, int lineTo, String name, String namespace) {
            this(lineFrom, lineTo, name, namespace, "");
        }

        public Scope(int lineFrom, int lineTo, String name, String namespace, String signature) {
            this.lineFrom = lineFrom;
            this.lineTo = lineTo;
            this.name = name;
            this.namespace = namespace;
            this.signature = signature;
        }

        public Scope(int lineFrom) {
            this.lineFrom = lineFrom;
        }

        public boolean matches(int line) {
            return line >= lineFrom && line <= lineTo;
        }

        @Override
        public int compareTo(Scope o) {
            return Integer.compare(lineFrom, o.lineFrom);
        }

        public int getLineFrom() {
            return lineFrom;
        }

        public void setLineFrom(int lineFrom) {
            this.lineFrom = lineFrom;
        }

        public int getLineTo() {
            return lineTo;
        }

        public void setLineTo(int lineTo) {
            this.lineTo = lineTo;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }
    }

    // default global scope
    public static final Scope GLOBAL_SCOPE = new Scope(0, 0, "global", null, null);

    // tree of scopes sorted by starting line
    private final TreeSet<Scope> scopes = new TreeSet<>();

    public Scopes() {
        // nothing to do here
    }

    public int size() {
        return scopes.size();
    }

    public void addScope(Scope scope) {
        scopes.add(scope);
    }

    public Scope getScope(int line) {
        // find closest scope that starts before or on given line
        Scope s = scopes.floor(new Scope(line));
        return (s != null && s.matches(line)) ? s : GLOBAL_SCOPE;
    }

    /**
     * Create a binary representation of this object.
     *
     * @return a byte array representing this object
     * @throws IOException if an error happens when writing to the array
     */
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(this);
        return bytes.toByteArray();
    }

    /**
     * De-serialize a binary representation of a {@code Definitions} object.
     *
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
        ObjectInputStream in
                = new ObjectInputStream(new ByteArrayInputStream(bytes));
        return (Scopes) in.readObject();
    }
}
