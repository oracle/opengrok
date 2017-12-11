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
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright 2011 Jens Elkner.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.analysis;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.analysis.Definitions.Tag;
import org.opensolaris.opengrok.analysis.Scopes.Scope;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.Annotation;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;

/**
 * Base class for Xref lexers.
 *
 * @author Lubos Kosco
 */
public abstract class JFlexXref extends JFlexStateStacker {

    public Writer out;
    public String urlPrefix = RuntimeEnvironment.getInstance().getUrlPrefix();
    public Annotation annotation;
    public Project project;
    protected Definitions defs;
    private boolean scopesEnabled = false;
    private boolean foldingEnabled = false;

    private boolean scopeOpen = false;
    protected Scopes scopes = new Scopes();
    protected Scope scope;
    private int scopeLevel = 0;

    /**
     * See {@link RuntimeEnvironment#getUserPage()}. Per default initialized in
     * the constructor and here to be consistent and avoid lot of unnecessary
     * lookups.
     *
     * @see #startNewLine()
     */
    protected String userPageLink;
    /**
     * See {@link RuntimeEnvironment#getUserPageSuffix()}. Per default
     * initialized in the constructor and here to be consistent and avoid lot of
     * unnecessary lookups.
     *
     * @see #startNewLine()
     */
    protected String userPageSuffix;

    /**
     * The span class name from the last call to
     * {@link #disjointSpan(java.lang.String)}.
     */
    private String disjointSpanClassName;

    /**
     * Description of the style to use for a type of definitions.
     */
    private static class Style {

        /**
         * Name of the style definition as given by CTags.
         */
        final String name;
        /**
         * Class name used by the style sheets when rendering the xref.
         */
        final String ssClass;
        /**
         * The title of the section to which this type belongs, or {@code null}
         * if this type should not be listed in the navigation panel.
         */
        final String title;

        /**
         * Construct a style description.
         */
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
        new Style("macro", "xm", "Macro"),
        new Style("argument", "xa", null),
        new Style("local", "xl", null),
        new Style("variable", "xv", "Variable"),
        new Style("class", "xc", "Class"),
        new Style("package", "xp", "Package"),
        new Style("interface", "xi", "Interface"),
        new Style("namespace", "xn", "Namespace"),
        new Style("enumerator", "xer", null),
        new Style("enum", "xe", "Enum"),
        new Style("struct", "xs", "Struct"),
        new Style("typedefs", "xts", null),
        new Style("typedef", "xt", "Typedef"),
        new Style("union", "xu", null),
        new Style("field", "xfld", null),
        new Style("member", "xmb", null),
        new Style("function", "xf", "Function"),
        new Style("method", "xmt", "Method"),
        new Style("subroutine", "xsr", "Subroutine"),};

    protected JFlexXref() {
        userPageLink = RuntimeEnvironment.getInstance().getUserPage();
        if (userPageLink != null && userPageLink.length() == 0) {
            userPageLink = null;
        }
        userPageSuffix = RuntimeEnvironment.getInstance().getUserPageSuffix();
        if (userPageSuffix != null && userPageSuffix.length() == 0) {
            userPageSuffix = null;
        }
    }

    /**
     * Reinitialize the xref with new contents.
     *
     * @param contents a char buffer with text to analyze
     * @param length the number of characters to use from the char buffer
     */
    public void reInit(char[] contents, int length) {
        reInit(new CharArrayReader(contents, 0, length));
    }

    /**
     * Reinitialize the lexer with new reader.
     *
     * @param reader new reader for this lexer
     */
    public void reInit(Reader reader) {
        this.yyreset(reader);
        reset();
    }

    /**
     * Resets the xref tracked state after {@link #reset()}.
     */
    @Override
    public void reset() {
        super.reset();

        annotation = null;
        disjointSpanClassName = null;
        scopes = new Scopes();
        scope = null;
        scopeLevel = 0;
        scopeOpen = false;
    }

    /**
     * set definitions
     * @param defs definitions
     */
    public void setDefs(Definitions defs) {
        this.defs = defs;
    }

    /**
     * set scopes
     * @param scopesEnabled if they should be enabled or disabled
     */
    public void setScopesEnabled(boolean scopesEnabled) {
        this.scopesEnabled = scopesEnabled;
    }

    /**
     * set folding of code
     * @param foldingEnabled whether to fold or not
     */
    public void setFoldingEnabled(boolean foldingEnabled) {
        this.foldingEnabled = foldingEnabled;
    }

    protected void appendProject() throws IOException {
        if (project != null) {
            out.write("&amp;project=");
            out.write(project.getName());
        }
    }

    /**
     * Calls {@link #appendLink(java.lang.String, boolean)} with {@code url}
     * and false.
     * @param url the URL to append
     * @throws IOException if an error occurs while appending
     */
    protected void appendLink(String url) throws IOException {
        appendLink(url, false);
    }

    /**
     * Calls
     * {@link #appendLink(java.lang.String, boolean, java.util.regex.Pattern)}
     * with {@code url}, {@code doEndingPushback}, and null.
     * @param url the URL to append
     * @param doEndingPushback a value indicating whether to test the
     * {@code url} with
     * {@link StringUtils#countURIEndingPushback(java.lang.String)}
     * @throws IOException if an error occurs while appending
     */
    protected void appendLink(String url, boolean doEndingPushback)
        throws IOException {

        appendLink(url, doEndingPushback, null);
    }

    /**
     * Appends the {@code url} to the active {@link Writer}.
     * <p>If {@code doEndingPushback} is true, then
     * {@link StringUtils#countURIEndingPushback(java.lang.String)} is enlisted
     * for use with {@link #yypushback(int)} -- i.e., {@code url} is only
     * partially written.
     * <p>If {@code collateralCapture} is not null, then its match in
     * {@code url} will alternatively mark the start of a count for pushback --
     * i.e., everything at and beyond the first {@code collateralCapture} match
     * will be considered not to belong to the URI.
     * <p>If the pushback count is equal to the length of {@code url}, then it
     * is simply written -- and nothing is pushed back -- in order to avoid a
     * never-ending {@code yylex()} loop.
     * @param url the URL to append
     * @param doEndingPushback a value indicating whether to test the
     * {@code url} with
     * {@link StringUtils#countURIEndingPushback(java.lang.String)}
     * @param collateralCapture optional pattern to indicate characters which
     * may have been captured as valid URI characters but in a particular
     * context should mark the start of a pushback
     * @throws IOException if an error occurs while appending
     */
    protected void appendLink(String url, boolean doEndingPushback,
        Pattern collateralCapture)
            throws IOException {

        int n = 0;
        int subn;
        do {
            // An ending-pushback could be present before a collateral capture,
            // so detect both in a loop (on a shrinking `url') until no more
            // shrinking should occur.

            subn = 0;
            if (doEndingPushback) {
                subn = StringUtils.countURIEndingPushback(url);
            }
            int ccn = StringUtils.countPushback(url, collateralCapture);
            if (ccn > subn) subn = ccn;

            // Push back if positive, but not if equal to the current length.
            if (subn > 0 && subn < url.length()) {
                url = url.substring(0, url.length() - subn);
                n += subn;
            } else {
                subn = 0;
            }
        } while (subn != 0);
        if (n > 0) yypushback(n);

        out.write("<a href=\"");
        out.write(Util.formQuoteEscape(url));
        out.write("\">");
        Util.htmlize(url, out);
        out.write("</a>");
    }

    protected String getProjectPostfix(boolean encoded) {
        String amp = encoded ? "&amp;" : "&";
        return project == null ? "" : (amp + "project=" + project.getName());
    }

    protected void startScope() {
        if (scopesEnabled && scope == null) {
            int line = getLineNumber();
            if (defs != null) {
                List<Tag> tags = defs.getTags(line);
                if (tags != null) {
                    for (Tag tag : tags) {
                        if (tag.type.startsWith("function") || tag.type.startsWith("method")) {
                            scope = new Scope(tag.line, tag.line, tag.symbol, tag.namespace, tag.signature);
                            scopeLevel = 0;
                            break;
                        }
                    }
                }
            }
        }
    }

    protected void incScope() {
        if (scope != null) {
            scopeLevel++;
        }
    }

    protected void decScope() {
        if (scope != null && scopeLevel > 0) {
            scopeLevel--;
            if (scopeLevel == 0) {
                scope.setLineTo(getLineNumber());
                scopes.addScope(scope);
                scope = null;
            }
        }
    }

    protected void endScope() {
        if (scope != null && scopeLevel == 0) {
            scope.setLineTo(getLineNumber());
            scopes.addScope(scope);
            scope = null;
        }
    }

    /**
     * Get generated scopes.
     * @return scopes for current line
     */
    public Scopes getScopes() {
        return scopes;
    }

    /**
     * Get the next token from the scanner.
     * @return state number (e.g. YYEOF)
     * @throws java.io.IOException in case of any I/O prob
     */
    public abstract int yylex() throws IOException;

    /**
     * Reset the scanner.
     * @param reader new reader to reinit this 
     */
    public abstract void yyreset(Reader reader);

    /**
     * Get the value of {@code yyline}.
     * @return line number
     */
    protected abstract int getLineNumber();

    /**
     * Set the value of {@code yyline}.
     * @param x line number
     */
    protected abstract void setLineNumber(int x);

    /**
     * start new analysis
     * @param newState state to begin from
     */
    public abstract void yybegin(int newState);

    /**
     * Pushes {@code number} characters of the matched text back into the
     * input stream per the documented JFlex behavior.
     * @param number a value greater than or equal to zero and less than or
     * equal to the length of the matched text.
     */
    public abstract void yypushback(int number);

    /**
     * returns current state of analysis
     * @return id of state
     */
    public abstract int yystate();

    /**
     * Gets the YYEOF value.
     * @return YYEOF
     */
    public abstract int getYYEOF();

    /**
     * Writes the closing of an open span tag previously opened by this method
     * and the opening -- if {@code className} is non-null -- of a new span
     * tag.
     * <p>This method's disjoint spans are independent of any span used for
     * scopes.
     * <p>Any open span is closed at the end of {@link #write(java.io.Writer)}
     * just before any open scope is closed.
     * @param className the class name for the new tag or {@code null} just to
     * close an open tag.
     * @throws IOException if an output error occurs
     */
    public void disjointSpan(String className) throws IOException {
        if (disjointSpanClassName != null) out.write(HtmlConsts.ZSPAN);
        if (className != null) {
            out.write(String.format(HtmlConsts.SPAN_FMT, className));
        }
        disjointSpanClassName = className;
    }

    /**
     * Gets the argument from the last call to
     * {@link #disjointSpan(java.lang.String)}.
     * @return a defined value or null
     */
    public String getDisjointSpanClassName() {
        return disjointSpanClassName;
    }

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
        while (yylex() != getYYEOF()) { // NOPMD while statement intentionally empty
            // nothing to do here, yylex() will do the work
        }

        disjointSpan(null);

        // terminate scopes
        if (scopeOpen) {
            out.write("</span>");
            scopeOpen = false;
        }

        while (!stack.empty()) {
            yypop();
        }
    }

    /**
     * Write a JavaScript function that returns an array with the definitions to
     * list in the navigation panel. Each element of the array is itself an
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

        Map<String, SortedSet<Tag>> symbols
                = new HashMap<>();

        for (Tag tag : defs.getTags()) {
            Style style = getStyle(tag.type);
            if (style != null && style.title != null) {
                SortedSet<Tag> tags = symbols.get(style.name);
                if (tags == null) {
                    tags = new TreeSet<>(cmp);
                    symbols.put(style.name, tags);
                }
                tags.add(tag);
            }
        }

        //TODO try to get rid of included js scripts generated from here (all js should ideally be in util)
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
     * Generate span id for namespace based on line number, name, and signature
     * (more functions with same name and signature can be defined in single
     * file)
     *
     * @param scope Scope to generate id from
     * @return generated span id
     */
    private String generateId(Scope scope) {
        String name = Integer.toString(scope.getLineFrom()) + scope.getName()
                + scope.getSignature();
        int hash = name.hashCode();
        return "scope_id_" + Integer.toHexString(hash);
    }

    /**
     * Simple escape of html characters in raw string.
     *
     * @param raw Raw string
     * @return String with escaped html characters
     */
    protected String htmlize(String raw) {
        return Util.prehtmlize(raw);
    }

    /**
     * Terminate the current line and insert preamble for the next line. The
     * line count will be incremented.
     *
     * @throws IOException on error when writing the xref
     */
    public void startNewLine() throws IOException {
        String iconId = null;
        int line = getLineNumber() + 1;
        boolean skipNl = false;
        setLineNumber(line);

        if (scopesEnabled) {
            startScope();

            if (scopeOpen && scope == null) {
                scopeOpen = false;
                out.write("\n</span>");
                skipNl = true;
            } else if (scope != null) {
                String scopeId = generateId(scope);
                if (scope.getLineFrom() == line) {
                    out.write("\n<span id='");
                    out.write(scopeId);
                    out.write("' class='scope-head'><span class='scope-signature'>");
                    out.write(htmlize(scope.getName() + scope.getSignature()));
                    out.write("</span>");
                    iconId = scopeId + "_fold_icon";
                    skipNl = true;
                } else if (scope.getLineFrom() == line - 1) {
                    if (scopeOpen) {
                        out.write("</span>");
                    }

                    out.write("\n<span id='");
                    out.write(scopeId);
                    out.write("_fold' class='scope-body'>");
                    skipNl = true;
                }
                scopeOpen = true;
            }
        }

        Util.readableLine(line, out, annotation, userPageLink, userPageSuffix,
                getProjectPostfix(true), skipNl);

        if (foldingEnabled && scopesEnabled) {
            if (iconId != null) {
                out.write("<a style='cursor:pointer;' onclick='fold(this.parentNode.id)' id='");
                out.write(iconId);
                /* space inside span for IE support */
                out.write("'><span class='fold-icon'>&nbsp;</span></a>");
            } else {
                out.write("<span class='fold-space'>&nbsp;</span>");
            }
        }
    }

    /**
     * Write a symbol and generate links as appropriate.
     *
     * @param symbol the symbol to write
     * @param keywords a set of keywords recognized by this analyzer (no links
     * will be generated if the symbol is a keyword)
     * @param line the line number on which the symbol appears
     * @return true if the {@code symbol} was not in {@code keywords} or if
     * {@code keywords} was null
     * @throws IOException if an error occurs while writing to the stream
     */
    protected boolean writeSymbol(String symbol, Set<String> keywords, int line)
            throws IOException {
        return writeSymbol(symbol, keywords, line, true, false);
    }

    /**
     * Write a symbol and generate links as appropriate.
     *
     * @param symbol the symbol to write
     * @param keywords a set of keywords recognized by this analyzer (no links
     * will be generated if the symbol is a keyword)
     * @param line the line number on which the symbol appears
     * @param caseSensitive Whether the keyword list is case sensitive
     * @return true if the {@code symbol} was not in {@code keywords} or if
     * {@code keywords} was null
     * @throws IOException if an error occurs while writing to the stream
     */
    protected boolean writeSymbol(
            String symbol, Set<String> keywords, int line, boolean caseSensitive)
            throws IOException {
        return writeSymbol(symbol, keywords, line, caseSensitive, false);
    }
    
    /**
     * Write a symbol and generate links as appropriate.
     *
     * @param symbol the symbol to write
     * @param keywords a set of keywords recognized by this analyzer (no links
     * will be generated if the symbol is a keyword)
     * @param line the line number on which the symbol appears
     * @param caseSensitive Whether the keyword list is case sensitive
     * @param quote Whether the symbol gets quoted in links or not
     * @return true if the {@code symbol} was not in {@code keywords} or if
     * {@code keywords} was null
     * @throws IOException if an error occurs while writing to the stream
     */
    protected boolean writeSymbol(String symbol, Set<String> keywords,
        int line, boolean caseSensitive, boolean quote)
            throws IOException {
        return writeSymbol(symbol, keywords, line, caseSensitive, quote, false);
    }

    /**
     * Write a symbol and generate links as appropriate.
     *
     * @param symbol the symbol to write
     * @param keywords a set of keywords recognized by this analyzer (no links
     * will be generated if the symbol is a keyword)
     * @param line the line number on which the symbol appears
     * @param caseSensitive Whether the keyword list is case sensitive
     * @param quote Whether the symbol gets quoted in links or not
     * @param isKeyword Whether the symbol is certainly a keyword without
     * bothering to look up in a defined {@code keywords}
     * @return true if the {@code symbol} was not in {@code keywords} or if
     * {@code keywords} was null and if-and-only-if {@code isKeyword} is false
     * @throws IOException if an error occurs while writing to the stream
     */
    protected boolean writeSymbol(
            String symbol, Set<String> keywords, int line, boolean caseSensitive,
            boolean quote, boolean isKeyword)
            throws IOException {
        String[] strs = new String[1];
        strs[0] = "";
        String jsEscapedSymbol = symbol.replace("'", "\\'");
        String qt = (quote) ? "&quot;" : "";

        String check = caseSensitive ? symbol : symbol.toLowerCase();
        if (isKeyword || (keywords != null && keywords.contains( check ))) {
            // This is a keyword, so we don't create a link.
            out.append("<b>").append(symbol).append("</b>");
            return false;
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
            //
            //    Note: In HTML 4, the name must start with a letter, and can
            //    only contain letters, digits, hyphens, underscores, colons,
            //    and periods. https://www.w3.org/TR/html4/types.html#type-name
            //    Skip the anchor if the symbol name is not a valid anchor
            //    name. This restriction is lifted in HTML 5.
            if (symbol.matches("[a-zA-Z][a-zA-Z0-9_:.-]*")) {
                out.append("<a class=\"");
                out.append(style_class);
                out.append("\" name=\"");
                out.append(symbol);
                out.append("\"/>");
            }

            // 2) Create a link that searches for all references to this symbol.
            out.append("<a href=\"");
            out.append(urlPrefix);
            out.append("refs=");
            out.append(qt+symbol+qt);
            appendProject();
            out.append("\" class=\"");
            out.append(style_class);
            out.append(" intelliWindow-symbol\"");
            out.append(" data-definition-place=\"def\"");
            out.append(">");
            out.append(symbol);
            out.append("</a>");

        } else if (defs != null && defs.occurrences(symbol) == 1) {
            // This is a reference to a symbol defined exactly once in this file.
            String style_class = "d";

            // Generate a direct link to the symbol definition.
            out.append("<a class=\"");
            out.append(style_class);
            out.append(" intelliWindow-symbol\" href=\"#");
            out.append(symbol);
            out.append("\"");
            out.append(" data-definition-place=\"defined-in-file\"");
            out.append(">");
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
            out.append(qt+symbol+qt);
            appendProject();
            out.append("\"");
            out.append(" class=\"intelliWindow-symbol\"");
            out.append(" data-definition-place=\"undefined-in-file\"");
            out.append(">");
            out.append(symbol);
            out.append("</a>");
        }
        return true;
    }

    /**
     * Write an {@code htmlize()}d keyword symbol
     *
     * @param symbol the symbol to write
     * @param line the line number on which the symbol appears
     * @throws IOException if an error occurs while writing to the stream
     */
    protected void writeKeyword(String symbol, int line) throws IOException {
        writeSymbol(htmlize(symbol), null, line, false, false, true);
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
}
