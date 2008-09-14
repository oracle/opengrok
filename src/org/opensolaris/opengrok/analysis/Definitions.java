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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.analysis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Definitions implements Serializable {
    /** Map from symbol to the line numbers on which the symbol is defined. */
    private final Map<String, Set<Integer>> symbols;
    /** List of all the tags. */
    private final List<Tag> tags;

    Definitions() {
        symbols = new HashMap<String, Set<Integer>>();
        tags = new ArrayList<Tag>();
    }

    /**
     * Get all symbols used in definitions.
     * @return a set containing all the symbols
     */
    public Set<String> getSymbols() {
        return symbols.keySet();
    }

    /**
     * Check if there is a tag for a symbol.
     * @param symbol the symbol to check
     * @return {@code true} iff there is a tag for {@code symbol}
     */
    public boolean hasSymbol(String symbol) {
        return symbols.containsKey(symbol);
    }

    /**
     * Check whether the specified symbol is defined on the given line.
     * @param symbol the symbol to look for
     * @param lineNumber the line to check
     * @return {@code true} iff {@code symbol} is defined on the specified line
     */
    public boolean hasDefinitionAt(String symbol, int lineNumber) {
        Set<Integer> lines = symbols.get(symbol);
        return lines != null && lines.contains(lineNumber);
    }

    /**
     * Return the number of occurrences of definitions with the specified
     * symbol.
     * @param symbol the symbol to count the occurrences of
     * @return the number of times the specified symbol is defined
     */
    public int occurrences(String symbol) {
        Set<Integer> lines = symbols.get(symbol);
        return lines == null ? 0 : lines.size();
    }

    /**
     * Return the number of distinct symbols.
     * @return number of distinct symbols
     */
    public int numberOfSymbols() {
        return symbols.size();
    }

    /**
     * Get a list of all tags.
     * @return all tags
     */
    public List<Tag> getTags() {
        return tags;
    }

    /**
     * Class that represents a single tag.
     */
    public static class Tag implements Serializable {
        /** Line number of the tag. */
        public final int line;
        /** The symbol used in the definition. */
        public final String symbol;
        /** The type of the tag. */
        public final String type;
        /** The full line on which the definition occurs. */
        public final String text;

        protected Tag(int line, String symbol, String type, String text) {
            this.line = line;
            this.symbol = symbol;
            this.type = type;
            this.text = text;
        }
    }

    void addTag(int line, String symbol, String type, String text) {
        // The strings are frequently repeated (a symbol can be used in
        // multiple definitions, multiple definitions can have the same type,
        // one line can contain multiple definitions). Intern them to minimize
        // the space consumed by them (see bug #809).
        final String internedSymbol = symbol.intern();
        final String internedType = type.intern();
        final String internedText = text.intern();
        tags.add(new Tag(line, internedSymbol, internedType, internedText));
        Set<Integer> lines = symbols.get(internedSymbol);
        if (lines == null) {
            lines = new HashSet<Integer>();
            symbols.put(internedSymbol, lines);
        }
        lines.add(line);
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
    public static Definitions deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        ObjectInputStream in =
                new ObjectInputStream(new ByteArrayInputStream(bytes));
        return (Definitions) in.readObject();
    }
}
