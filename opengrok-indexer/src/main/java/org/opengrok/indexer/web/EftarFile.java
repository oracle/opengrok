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
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.web;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * An Extremely Fast Tagged Attribute Read-only File System.
 * Created on October 12, 2005
 *
 * <i>Eftar</i> File has the following format
 * <code>
 * FILE --&gt; Record  ( Record | tagString ) *
 * <br>
 * Record --&gt; 64bit:Hash 16bit:childrenOffset  16bit:(numberChildren|lenthOfTag) 16bit:tagOffset
 * </code>
 *
 * It is a tree of tagged names, doing binary search in sorted list of children
 *
 * @author Chandan
 */
public class EftarFile {

    public static final int RECORD_LENGTH = 14;
    private long offset;
    private Node root;

    static class Node {

        private final long hash;
        private String tag;
        private final Map<Long, Node> children;
        private long tagOffset;
        private long childOffset;

        Node(long hash, String tag) {
            this.hash = hash;
            this.tag = tag;
            children = new TreeMap<>();
        }

        public Node put(long hash, String desc) {
            return children.computeIfAbsent(hash, newNode -> new Node(hash, desc));
        }

        public Node get(long hash) {
            return children.get(hash);
        }
    }

    public static long myHash(String name) {
        if (name == null || name.length() == 0) {
            return 0;
        }
        long hash = 2861;
        int n = name.length();
        if (n > 100) {
            n = 100;
        }
        for (int i = 0; i < n; i++) {
            hash = (hash * 641L + name.charAt(i) * 2969 + hash << 6) % 9322397;
        }
        return hash;
    }

    private void write(Node n, DataOutputStream out) throws IOException {
        if (n.tag != null) {
            out.write(n.tag.getBytes());
            offset += n.tag.length();
        }
        for (Node childNode : n.children.values()) {
            out.writeLong(childNode.hash);
            if (childNode.children.size() > 0) {
                out.writeShort((short) (childNode.childOffset - offset));
                out.writeShort((short) childNode.children.size());
            } else {
                out.writeShort(0);
                if (childNode.tag == null) {
                    out.writeShort((short) 0);
                } else {
                    out.writeShort((short) childNode.tag.length());
                }
            }
            if (childNode.tag == null) {
                out.writeShort(0);
            } else {
                out.writeShort((short) (childNode.tagOffset - offset));
            }
            offset += RECORD_LENGTH;
        }
        for (Node childNode : n.children.values()) {
            write(childNode, out);
        }
    }

    private void traverse(Node n) {
        if (n.tag == null) {
            n.tagOffset = 0;
        } else {
            n.tagOffset = offset;
            offset += n.tag.length();
        }
        if (n.children.size() > 0) {
            n.childOffset = offset;
            offset += ((long) RECORD_LENGTH * n.children.size());
        } else {
            n.childOffset = 0;
        }
        for (Node childnode : n.children.values()) {
            traverse(childnode);
        }
    }

    /**
     * Reads the input into interim representation. Can be called multiple times.
     * @param descriptions set of PathDescription
     */
    private void readInput(Set<PathDescription> descriptions) {
        if (root == null) {
            root = new Node(1, null);
        }

        for (PathDescription desc : descriptions) {
            StringTokenizer toks = new StringTokenizer(desc.getPath(), "\\/");
            Node n = root;
            while (toks.hasMoreTokens()) {
                n = n.put(myHash(toks.nextToken()), null);
            }
            n.tag = desc.getDescription();
        }
    }

    public void write(String outPath) throws IOException {
        offset = RECORD_LENGTH;
        traverse(root);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outPath)))) {
            out.writeLong(0x5e33);
            out.writeShort(RECORD_LENGTH);
            out.writeShort(root.children.size());
            out.writeShort(0);
            offset = RECORD_LENGTH;
            write(root, out);
        }
    }

    public void create(Set<PathDescription> descriptions, String outputPath) throws IOException {
        readInput(descriptions);
        write(outputPath);
    }
}
