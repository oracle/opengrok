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
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2011, Jens Elkner.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Set;
import java.util.regex.Pattern;
import org.opengrok.indexer.analysis.Scopes.Scope;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.Annotation;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.web.QueryParameters;
import org.opengrok.indexer.web.Util;

/**
 * Represents a base class for non-traditional xref lexers whose
 * cross-references are not straight-forward representations of source code but
 * instead are attempts to show derived presentations (e.g. a visual
 * representation of troff or mandoc(5)).
 * <p>
 * This approach is controversial (see
 * <a href="https://github.com/oracle/opengrok/issues/33">
 * "man pages are not cross-referenced"</a>,
 * <a href="https://github.com/oracle/opengrok/issues/393">
 * "man2html rendering of sh.1 shows garbage"</a>,
 * <a href="https://github.com/oracle/opengrok/issues/433">
 * "Annotate for man pages does not work"</a>,
 * <a href="https://github.com/oracle/opengrok/issues/553">
 * "man page garbled"</a>) and probably should be considered deprecated as a
 * candidate for future removal in favor of a feature described by
 * <a href="https://github.com/kahatlen">kahatlen</a>:
 * <p>
 * "The [troff or mandoc(5)] xref could show the markup, and then there could
 * be a button you could click to see the rendered output."
 */
public abstract class JFlexNonXref extends JFlexStateStacker
        implements Xrefer {

    protected Writer out;
    protected String urlPrefix = RuntimeEnvironment.getInstance().getUrlPrefix();
    protected Annotation annotation;
    protected Project project;
    protected Definitions defs;
    private boolean scopesEnabled;
    private boolean foldingEnabled;

    private boolean scopeOpen;
    protected Scopes scopes = new Scopes();
    protected Scope scope;
    private int scopeLevel;

    /**
     * The following field is set to {@code true} (via {@link #phLOC()}) when
     * applicable during lexing before a call to {@link #startNewLine()} so
     * that the lines-of-code count is also incremented.
     */
    protected boolean didSeePhysicalLOC;

    protected int loc;

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

    protected JFlexNonXref() {
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
     * Resets the xref tracked state.
     */
    @Override
    public void reset() {
        super.reset();

        annotation = null;
        didSeePhysicalLOC = false;
        disjointSpanClassName = null;
        loc = 0;
        scopes = new Scopes();
        scope = null;
        scopeLevel = 0;
        scopeOpen = false;
        setLineNumber(1);
    }

    /**
     * Gets the document physical lines-of-code count.
     * @return a number greater than or equal to 0
     */
    @Override
    public int getLOC() {
        return loc;
    }

    @Override
    public void setAnnotation(Annotation annotation) {
        this.annotation = annotation;
    }

    /**
     * Set definitions.
     * @param defs definitions
     */
    @Override
    public void setDefs(Definitions defs) {
        this.defs = defs;
    }

    @Override
    public void setProject(Project project) {
        this.project = project;
    }

    /**
     * Set scopes.
     * @param scopesEnabled if they should be enabled or disabled
     */
    @Override
    public void setScopesEnabled(boolean scopesEnabled) {
        this.scopesEnabled = scopesEnabled;
    }

    /**
     * Set folding of code.
     * @param foldingEnabled whether to fold or not
     */
    @Override
    public void setFoldingEnabled(boolean foldingEnabled) {
        this.foldingEnabled = foldingEnabled;
    }

    /**
     * Sets a value indicating that a physical line-of-code was encountered.
     */
    protected void phLOC() {
        didSeePhysicalLOC = true;
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
     * Calls
     * {@link JFlexXrefUtils#appendLink(java.io.Writer, org.opengrok.indexer.analysis.JFlexLexer, java.lang.String, boolean, java.util.regex.Pattern)}
     * with the active {@link Writer}, the field {@code matcher}, {@code url},
     * {@code doEndingPushback}, and {@code collateralCapture}.
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
        JFlexXrefUtils.appendLink(out, this, url, doEndingPushback,
            collateralCapture);
    }

    protected String getProjectPostfix(boolean encoded) {
        String amp = encoded ? "&amp;" : "&";
        return project == null ? "" : (amp + QueryParameters.PROJECT_SEARCH_PARAM_EQ +
                project.getName());
    }

    protected void startScope() {
        Scope newScope = JFlexXrefUtils.maybeNewScope(scopesEnabled, scope,
            this, defs);
        if (newScope != null) {
            scope = newScope;
            scopeLevel = 0;
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
        disjointSpanClassName = JFlexXrefUtils.disjointSpan(out, className,
            disjointSpanClassName);
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
        if (defs != null) {
            defs.resetUnused();
        }
        JFlexXrefUtils.writeSymbolTable(out, defs);
        setLineNumber(1);
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
     * Calls {@link Util#prehtmlize(java.lang.String)}.
     * @param raw Raw string
     * @return String with escaped characters
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
        int line = getLineNumber();
        boolean skipNl = false;
        setLineNumber(line + 1);

        if (didSeePhysicalLOC) {
            ++loc;
            didSeePhysicalLOC = false;
        }

        if (scopesEnabled) {
            startScope();

            if (scopeOpen && scope == null) {
                scopeOpen = false;
                out.write("\n</span>");
                skipNl = true;
            } else if (scope != null) {
                String scopeId = JFlexXrefUtils.generateId(scope);
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
    protected boolean writeSymbol(String symbol, Set<String> keywords, int line,
        boolean caseSensitive) throws IOException {
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
     * @param isKeyword Whether the symbol is certainly a keyword without
     * bothering to look up in a defined {@code keywords}
     * @return true if the {@code symbol} was not in {@code keywords} or if
     * {@code keywords} was null and if-and-only-if {@code isKeyword} is false
     * @throws IOException if an error occurs while writing to the stream
     */
    protected boolean writeSymbol(String symbol, Set<String> keywords, int line,
        boolean caseSensitive, boolean isKeyword) throws IOException {
        return JFlexXrefUtils.writeSymbol(out, defs, urlPrefix, project,
            symbol, keywords, line, caseSensitive, isKeyword);
    }

    /**
     * Writes a keyword symbol.
     *
     * @param symbol the symbol to write
     * @param line the line number on which the symbol appears
     * @throws IOException if an error occurs while writing to the stream
     */
    protected void writeKeyword(String symbol, int line) throws IOException {
        writeSymbol(symbol, null, line, false, true);
    }

    /**
     * Calls {@link JFlexXrefUtils#writeUnicodeChar(java.io.Writer, char)} with
     * the active {@link Writer} and {@code c}.
     * @param c the character to write
     * @throws IOException if an error occurs while writing to the stream
     */
    protected void writeUnicodeChar(char c) throws IOException {
        JFlexXrefUtils.writeUnicodeChar(out, c);
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
        JFlexXrefUtils.writeEMailAddress(out, address);
    }
}
