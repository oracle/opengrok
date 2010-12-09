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
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.analysis;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.Set;
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

    protected JFlexXref() {
        try {
            // TODO when bug #16053 is fixed, we should add a getter to a file
            // that's included from all the Xref classes so that we avoid the
            // reflection.
            Field f = getClass().getField("YYEOF");
            yyeof = f.getInt(null);
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

    /**
     * Write xref to the specified {@code Writer}.
     *
     * @param out xref destination
     * @throws IOException on error when writing the xref
     */
    public void write(Writer out) throws IOException {
        this.out = out;
        setLineNumber(0);
        startNewLine();
        while (yylex() != yyeof) { // NOPMD while statement intentionally empty
            // nothing to do here, yylex() will do the work
        }
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
        Util.readableLine(line, out, annotation);
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
            throws IOException {
        String[] strs = new String[1];
        strs[0] = "";

        if (keywords != null && keywords.contains(symbol)) {
            // This is a keyword, so we don't create a link.
            out.append("<b>").append(symbol).append("</b>");

        } else if (defs != null && defs.hasDefinitionAt(symbol, line, strs)) {
            // This is the definition of the symbol.
            String type = strs[0];
            String style_class = "d";

            if (type.startsWith("macro")) {
                style_class = "xm";
            } else if (type.startsWith("argument")) {
                style_class = "xa";
            } else if (type.startsWith("local")) {
                style_class = "xl";
            } else if (type.startsWith("variable")) {
                style_class = "xv";
            } else if (type.startsWith("class")) {
                style_class = "xc";
            } else if (type.startsWith("package")) {
                style_class = "xp";
            } else if (type.startsWith("interface")) {
                style_class = "xi";
            } else if (type.startsWith("namespace")) {
                style_class = "xn";
            } else if (type.startsWith("enum")) {
                style_class = "xe";
            } else if (type.startsWith("enumerator")) {
                style_class = "xer";
            } else if (type.startsWith("struct")) {
                style_class = "xs";
            } else if (type.startsWith("typedef")) {
                style_class = "xt";
            } else if (type.startsWith("typedefs")) {
                style_class = "xts";
            } else if (type.startsWith("union")) {
                style_class = "xu";
            } else if (type.startsWith("field")) {
                style_class = "xfld";
            } else if (type.startsWith("member")) {
                style_class = "xmb";
            } else if (type.startsWith("function")) {
                style_class = "xf";
            } else if (type.startsWith("method")) {
                style_class = "xmt";
            } else if (type.startsWith("subroutine")) {
                style_class = "xsr";
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
            out.append("\" ");
            // May have multiple anchors with the same function name,
            // store line number for accurate location used in list.jsp.
            out.append("ln=\"");
            out.append(Integer.toString(line));
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
            out.append("&#").append(Integer.toString((int) c)).append(';');
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
}
