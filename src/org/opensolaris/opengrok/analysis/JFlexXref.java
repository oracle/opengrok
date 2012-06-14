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
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright 2011 Jens Elkner.
 */

package org.opensolaris.opengrok.analysis;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;
import org.opensolaris.opengrok.analysis.Definitions.Tag;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.Annotation;
import org.opensolaris.opengrok.web.Util;

/**
 * Base class for Xref lexers.
 *
 * @author Lubos Kosco
 */
public abstract class JFlexXref {
    public Writer out;
    public String urlPrefix = RuntimeEnvironment.getInstance().getUrlPrefix();
    public Annotation annotation;
    public Project project;
    protected Definitions defs;
    /** EOF value returned by yylex(). */
    private final int yyeof;
    /** See {@link RuntimeEnvironment#getUserPage()}. Per default initialized
     * in the constructor and here to be consistent and avoid lot of
     * unnecessary lookups.
     * @see #startNewLine() */
    protected String userPageLink;
    /** See {@link RuntimeEnvironment#getUserPageSuffix()}. Per default
     * initialized in the constructor and here to be consistent and avoid lot of
     * unnecessary lookups.
     * @see #startNewLine() */
    protected String userPageSuffix;

    protected Stack<Integer> stack = new Stack<Integer>();
    protected Stack<String> stackPopString = new Stack<String>();

    /**
     * Description of the style to use for a type of definitions.
     */
    private static class Style {
        /** Name of the style definition as given by CTags. */
        final String name;

        /** Class name used by the style sheets when rendering the xref. */
        final String ssClass;

        /**
         * The title of the section to which this type belongs, or {@code null}
         * if this type should not be listed in the navigation panel.
         */
        final String title;

        /** Construct a style description. */
        Style(String name, String ssClass, String title) {
            this.name = name;
            this.ssClass = ssClass;
            this.title = title;
        }
    }

    /**
     * Description of styles to use for different types of definitions.
     */
    private static final Style[] DEFINITION_STYLES = {
        new Style("macro",      "xm",   "Macro"),
        new Style("argument",   "xa",   null),
        new Style("local",      "xl",   null),
        new Style("variable",   "xv",   "Variable"),
        new Style("class",      "xc",   "Class"),
        new Style("package",    "xp",   "Package"),
        new Style("interface",  "xi",   "Interface"),
        new Style("namespace",  "xn",   "Namespace"),
        new Style("enumerator", "xer",  null),
        new Style("enum",       "xe",   "Enum"),
        new Style("struct",     "xs",   "Struct"),
        new Style("typedefs",   "xts",  null),
        new Style("typedef",    "xt",   "Typedef"),
        new Style("union",      "xu",   null),
        new Style("field",      "xfld", null),
        new Style("member",     "xmb",  null),
        new Style("function",   "xf",   "Function"),
        new Style("method",     "xmt",  "Method"),
        new Style("subroutine", "xsr",  "Subroutine"),
    };

    protected JFlexXref() {
        try {
            // TODO when bug #16053 is fixed, we should add a getter to a file
            // that's included from all the Xref classes so that we avoid the
            // reflection.
            Field f = getClass().getField("YYEOF");
            yyeof = f.getInt(null);
            userPageLink = RuntimeEnvironment.getInstance().getUserPage();
            if (userPageLink != null && userPageLink.length() == 0) {
                userPageLink = null;
            }
            userPageSuffix = RuntimeEnvironment.getInstance().getUserPageSuffix();
            if (userPageSuffix != null && userPageSuffix.length() == 0) {
                userPageSuffix = null;
            }
        } catch (Exception e) {
            // The auto-generated constructors for the Xref classes don't
            // expect a checked exception, so wrap it in an AssertionError.
            // This should never happen, since all the Xref classes will get
            // a public static YYEOF field from JFlex.
            AssertionError ae = new AssertionError("Couldn't initialize yyeof");
            ae.initCause(e);
            throw ae; // NOPMD (stack trace is preserved by initCause(), but
                      // PMD thinks it's lost)
        }
    }

    /**
     * Reinitialize the xref with new contents.
     *
     * @param contents a char buffer with text to analyze
     * @param length the number of characters to use from the char buffer
     */
    public void reInit(char[] contents, int length) {
        yyreset(new CharArrayReader(contents, 0, length));
        annotation = null;
    }

    public void setDefs(Definitions defs) {
        this.defs = defs;
    }

    protected void appendProject() throws IOException {
        if (project != null) {
            out.write("&amp;project=");
            out.write(project.getDescription());
        }
    }

    protected String getProjectPostfix() {
        return project == null ? "" : ("&amp;project=" + project.getDescription());
    }

    /** Get the next token from the scanner. */
    public abstract int yylex() throws IOException;

    /** Reset the scanner. */
    public abstract void yyreset(Reader reader);

    /** Get the value of {@code yyline}. */
    protected abstract int getLineNumber();

    /** Set the value of {@code yyline}. */
    protected abstract void setLineNumber(int x);

    public abstract void yybegin(int newState);

    public abstract int yystate();

    /**
     * Write xref to the specified {@code Writer}.
     *
     * @param out xref destination
     * @throws IOException on error when writing the xref
     */
    public void write(Writer out) throws IOException {
        this.out = out;
        writeSymbolTable();
        setLineNumber(0);
        startNewLine();
        while (yylex() != yyeof) { // NOPMD while statement intentionally empty
            // nothing to do here, yylex() will do the work
        }
    }

    /**
     * Write a JavaScript function that returns an array with the definitions
     * to list in the navigation panel. Each element of the array is itself an
     * array containing the name of the definition type, the CSS class name for
     * the type, and an array of (symbol, line) pairs for the definitions of
     * that type.
     */
    private void writeSymbolTable() throws IOException {
        if (defs == null) {
            // No definitions, no symbol table to write
            return;
        }

        // We want the symbol table to be sorted
        Comparator<Tag> cmp = new Comparator<Tag>() {
            @Override
            public int compare(Tag tag1, Tag tag2) {
                // Order by symbol name, and then by line number if multiple
                // definitions use the same symbol name
                int ret = tag1.symbol.compareTo(tag2.symbol);
                if (ret == 0) {
                    ret = tag1.line - tag2.line;
                }
                return ret;
            }
        };

        Map<String, SortedSet<Tag>> symbols =
                new HashMap<String, SortedSet<Tag>>();

        for (Tag tag : defs.getTags()) {
            Style style = getStyle(tag.type);
            if (style != null && style.title != null) {
                SortedSet<Tag> tags = symbols.get(style.name);
                if (tags == null) {
                    tags = new TreeSet<Tag>(cmp);
                    symbols.put(style.name, tags);
                }
                tags.add(tag);
            }
        }

        out.append("<script type=\"text/javascript\">/* <![CDATA[ */\n");
        out.append("function get_sym_list(){return [");

        boolean first = true;
        for (Style style : DEFINITION_STYLES) {
            SortedSet<Tag> tags = symbols.get(style.name);
            if (tags != null) {
                if (!first) {
                    out.append(',');
                }
                out.append("[\"");
                out.append(style.title);
                out.append("\",\"");
                out.append(style.ssClass);
                out.append("\",[");

                boolean firstTag = true;
                for (Tag tag : tags) {
                    if (!firstTag) {
                        out.append(',');
                    }
                    out.append('[');
                    out.append(Util.jsStringLiteral(tag.symbol));
                    out.append(',');
                    out.append(Integer.toString(tag.line));
                    out.append(']');
                    firstTag = false;
                }
                out.append("]]");
                first = false;
            }
        }
        /* no LF intentionally - xml is whitespace aware ... */
        out.append("];} /* ]]> */</script>");
    }

    /**
     * Get the style description for a definition type.
     *
     * @param type the definition type
     * @return the style of a definition type, or {@code null} if no style is
     * defined for the type
     * @see #DEFINITION_STYLES
     */
    private Style getStyle(String type) {
        for (Style style : DEFINITION_STYLES) {
            if (type.startsWith(style.name)) {
                return style;
            }
        }
        return null;
    }

    /**
     * Terminate the current line and insert preamble for the next line. The
     * line count will be incremented.
     *
     * @throws IOException on error when writing the xref
     */
    protected void startNewLine() throws IOException {
        int line = getLineNumber() + 1;
        setLineNumber(line);
        Util.readableLine(line, out, annotation, userPageLink, userPageSuffix);
    }

    /**
     * Write a symbol and generate links as appropriate.
     *
     * @param symbol the symbol to write
     * @param keywords a set of keywords recognized by this analyzer (no links
     * will be generated if the symbol is a keyword)
     * @param line the line number on which the symbol appears
     * @throws IOException if an error occurs while writing to the stream
     */
    protected void writeSymbol(String symbol, Set<String> keywords, int line)
            throws IOException{
        writeSymbol(symbol, keywords, line, true);
    }

    /**
     * Write a symbol and generate links as appropriate.
     *
     * @param symbol the symbol to write
     * @param keywords a set of keywords recognized by this analyzer (no links
     * will be generated if the symbol is a keyword)
     * @param line the line number on which the symbol appears
     * @param caseSensitive Whether the keyword list is case sensitive
     * @throws IOException if an error occurs while writing to the stream
     */
    protected void writeSymbol(
            String symbol, Set<String> keywords, int line, boolean caseSensitive)
            throws IOException {
        String[] strs = new String[1];
        strs[0] = "";

        if (keywords != null && keywords.contains(
                caseSensitive ? symbol : symbol.toLowerCase())) {
            // This is a keyword, so we don't create a link.
            out.append("<b>").append(symbol).append("</b>");

        } else if (defs != null && defs.hasDefinitionAt(symbol, line, strs)) {
            // This is the definition of the symbol.
            String type = strs[0];
            String style_class = "d";

            Style style = getStyle(type);
            if (style != null) {
                style_class = style.ssClass;
            }

            // 1) Create an anchor for direct links. (Perhaps we should only
            //    do this when there's exactly one definition of the symbol in
            //    this file? Otherwise, we may end up with multiple anchors with
            //    the same name.)
            out.append("<a class=\"");
            out.append(style_class);
            out.append("\" name=\"");
            out.append(symbol);
            out.append("\"/>");

            // 2) Create a link that searches for all references to this symbol.
            out.append("<a href=\"");
            out.append(urlPrefix);
            out.append("refs=");
            out.append(symbol);
            appendProject();
            out.append("\" class=\"");
            out.append(style_class);
            out.append("\">");
            out.append(symbol);
            out.append("</a>");

        } else if (defs != null && defs.occurrences(symbol) == 1) {
            // This is a reference to a symbol defined exactly once in this file.
            String style_class = "d";

            // Generate a direct link to the symbol definition.
            out.append("<a class=\"");
            out.append(style_class);
            out.append("\" href=\"#");
            out.append(symbol);
            out.append("\">");
            out.append(symbol);
            out.append("</a>");

        } else {
            // This is a symbol that is not defined in this file, or a symbol
            // that is defined more than once in this file. In either case, we
            // can't generate a direct link to the definition, so generate a
            // link to search for all definitions of that symbol instead.
            out.append("<a href=\"");
            out.append(urlPrefix);
            out.append("defs=");
            out.append(symbol);
            appendProject();
            out.append("\">");
            out.append(symbol);
            out.append("</a>");
        }
    }

    /**
     * Write HTML escape sequence for the specified Unicode character, unless
     * it's an ISO control character, in which case it is ignored.
     *
     * @param c the character to write
     * @throws IOException if an error occurs while writing to the stream
     */
    protected void writeUnicodeChar(char c) throws IOException {
        if (!Character.isISOControl(c)) {
            out.append("&#").append(Integer.toString(c)).append(';');
        }
    }

    /**
     * Write an e-mail address. The address will be obfuscated if
     * {@link RuntimeEnvironment#isObfuscatingEMailAddresses()} returns
     * {@code true}.
     *
     * @param address the address to write
     * @throws IOException if an error occurs while writing to the stream
     */
    protected void writeEMailAddress(String address) throws IOException {
        if (RuntimeEnvironment.getInstance().isObfuscatingEMailAddresses()) {
            out.write(address.replace("@", " (at) "));
        } else {
            out.write(address);
        }
    }

    public void yypush(int newState, String popString) {
        this.stack.push(yystate());
        this.stackPopString.push(popString);
        yybegin(newState);
    }

    public void yypop() throws IOException {
        yybegin(this.stack.pop());
        String popString = this.stackPopString.pop();
        if (popString != null) {
            out.write(popString);
        }
    }
}
