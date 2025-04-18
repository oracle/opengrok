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
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import org.jetbrains.annotations.Nullable;
import org.opengrok.indexer.util.DTOElement;
import org.opengrok.indexer.util.WhitelistObjectInputFilter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Definitions implements Serializable {

    private static final long serialVersionUID = 1191703801007779489L;

    private static final ObjectInputFilter serialFilter = new WhitelistObjectInputFilter(
            Definitions.class,
            HashMap.class,
            Map.Entry[].class,
            Integer.class,
            Number.class,
            LineTagMap.class,
            HashSet.class,
            Tag.class,
            ArrayList.class,
            Object[].class
    );

    // Per line sym -> tags mapping
    public static class LineTagMap implements Serializable {

        private static final long serialVersionUID = 1191703801007779481L;
        @SuppressWarnings("java:S116")
        private final HashMap<String, Set<Tag>> sym_tags; //NOPMD

        protected LineTagMap() {
            this.sym_tags = new HashMap<>();
        }
    }
    // line number -> tag map
    @SuppressWarnings("java:S116")
    private final HashMap<Integer, LineTagMap> line_maps;

    /**
     * Map from symbol to the line numbers on which the symbol is defined.
     */
    private final HashMap<String, Set<Integer>> symbols;
    /**
     * List of all the tags.
     */
    private final ArrayList<Tag> tags;

    public Definitions() {
        symbols = new HashMap<>();
        line_maps = new HashMap<>();
        tags = new ArrayList<>();
    }

    /**
     * Reset all {@link Tag#used} values to {@code false}.
     */
    public void resetUnused() {
        for (Tag tag : tags) {
            tag.used = false;
        }
    }

    /**
     * Get all symbols used in definitions.
     *
     * @return a set containing all the symbols
     */
    public Set<String> getSymbols() {
        return symbols.keySet();
    }

    /**
     * Check if there is a tag for a symbol.
     *
     * @param symbol the symbol to check
     * @return {@code true} if there is a tag for {@code symbol}
     */
    public boolean hasSymbol(String symbol) {
        return symbols.containsKey(symbol);
    }

    /**
     * Check whether the specified symbol is defined on the given line.
     *
     * @param symbol the symbol to look for
     * @param lineNumber the line to check
     * @param strs type of definition(to be passed back to caller)
     * @return {@code true} if {@code symbol} is defined on the specified line
     */
    public boolean hasDefinitionAt(String symbol, int lineNumber, String[] strs) {
        if (strs.length > 0) {
            strs[0] = "none";
        }

        // Get tag info
        boolean isDefinitionPresent = Optional.ofNullable(symbols.get(symbol))
                .filter(lines -> lines.contains(lineNumber))
                .isPresent();
        if (isDefinitionPresent) {
            LineTagMap lineMap = line_maps.get(lineNumber);
            if (lineMap != null) {
                for (Tag tag : lineMap.sym_tags.get(symbol)) {
                    if (tag.used) {
                        continue;
                    }
                    if (strs.length > 0) { //NOPMD
                        strs[0] = tag.type;
                    }
                    tag.used = true;
                    // Assume the first one
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Return the number of occurrences of definitions with the specified
     * symbol.
     *
     * @param symbol the symbol to count the occurrences of
     * @return the number of times the specified symbol is defined
     */
    public int occurrences(String symbol) {
        Set<Integer> lines = symbols.get(symbol);
        return lines == null ? 0 : lines.size();
    }

    /**
     * Return the number of distinct symbols.
     *
     * @return number of distinct symbols
     */
    public int numberOfSymbols() {
        return symbols.size();
    }

    /**
     * Get a list of all tags.
     *
     * @return all tags
     */
    public List<Tag> getTags() {
        return tags;
    }

    /**
     * Get a list of all tags on given line.
     *
     * @param line line number
     * @return list of tags or null
     */
    public @Nullable List<Tag> getTags(int line) {
        return Optional.ofNullable(line_maps.get(line))
                .map(lineMap -> lineMap.sym_tags.values().stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList())
                )
                .orElse(null);
    }

    /**
     * Class that represents a single tag.
     */
    public static class Tag implements Serializable {

        private static final long serialVersionUID = 1217869075425651465L;

        public int getLine() {
            return line;
        }

        public String getSymbol() {
            return symbol;
        }

        /**
         * Line number of the tag.
         */
        @DTOElement
        public final int line;
        /**
         * The symbol used in the definition.
         */
        @DTOElement
        public final String symbol;

        public String getType() {
            return type;
        }

        public String getText() {
            return text;
        }

        public String getNamespace() {
            return namespace;
        }

        public String getSignature() {
            return signature;
        }

        public int getLineStart() {
            return lineStart;
        }

        public int getLineEnd() {
            return lineEnd;
        }

        /**
         * The type of the tag.
         */
        @DTOElement
        public final String type;
        /**
         * The full line on which the definition occurs.
         */
        @DTOElement
        public final String text;
        /**
         * Namespace/class of tag definition.
         */
        @DTOElement
        public final String namespace;
        /**
         * Scope of tag definition.
         */
        @DTOElement
        public final String signature;
        /**
         * The starting offset (possibly approximate) of {@link #symbol} from
         * the start of the line.
         */
        @DTOElement
        public final int lineStart;
        /**
         * The ending offset (possibly approximate) of {@link #symbol} from
         * the start of the line.
         */
        @DTOElement
        public final int lineEnd;

        /**
         * A non-serialized marker for marking a tag to avoid its reuse.
         */
        private transient boolean used;

        protected Tag() {
            this(0, null, null, null, null, null, 0, 0);
        }

        protected Tag(int line, String symbol, String type, String text,
                String namespace, String signature, int lineStart,
                int lineEnd) {
            this.line = line;
            this.symbol = symbol;
            this.type = type;
            this.text = text;
            this.namespace = namespace;
            this.signature = signature;
            this.lineStart = lineStart;
            this.lineEnd = lineEnd;
        }
    }

    public void addTag(int line, String symbol, String type, String text,
            int lineStart, int lineEnd) {
        addTag(line, symbol, type, text, null, null, lineStart, lineEnd);
    }

    public void addTag(int line, String symbol, String type, String text,
            String namespace, String signature, int lineStart, int lineEnd) {
        Tag newTag = new Tag(line, symbol, type, text, namespace, signature,
            lineStart, lineEnd);
        tags.add(newTag);
        Set<Integer> lines = symbols.computeIfAbsent(symbol,
                k -> new HashSet<>());
        Integer aLine = line;
        lines.add(aLine);

        // Get per line map
        LineTagMap lineMap = line_maps.computeIfAbsent(aLine,
                key -> new LineTagMap());

        // Insert sym->tag map for this line
        Set<Tag> ltags = lineMap.sym_tags.get(symbol);
        if (ltags == null) {
            ltags = new HashSet<>();
            lineMap.sym_tags.put(symbol, ltags);
        }
        ltags.add(newTag);
    }

    /**
     * Create a binary representation of this object.
     *
     * @return a byte array representing this object
     * @throws IOException if an error happens when writing to the array
     */
    public byte[] serialize() throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); var oos = new ObjectOutputStream(bytes)) {
            oos.writeObject(this);
            return bytes.toByteArray();
        }
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
    public static Definitions deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(serialFilter);
            return (Definitions) in.readObject();
        }
    }
}
