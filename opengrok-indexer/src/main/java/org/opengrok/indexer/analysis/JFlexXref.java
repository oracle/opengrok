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
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2011, Jens Elkner.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import org.opengrok.indexer.analysis.Scopes.Scope;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.Annotation;
import org.opengrok.indexer.web.QueryParameters;
import org.opengrok.indexer.web.Util;

/**
 * @author Lubos Kosco
 */
public class JFlexXref implements Xrefer, SymbolMatchedListener,
        NonSymbolMatchedListener {

    /**
     * Used to indicate pre-formatted output with
     * {@link Util#htmlize(java.lang.CharSequence, java.lang.Appendable, boolean)}.
     */
    private static final boolean PRE = true;

    private final ScanningSymbolMatcher matcher;
    private Writer out;
    private final String urlPrefix =
        RuntimeEnvironment.getInstance().getUrlPrefix();
    private Annotation annotation;
    private Project project;
    private Definitions defs;
    private boolean scopesEnabled;
    private boolean foldingEnabled;

    private boolean scopeOpen;
    private Scopes scopes = new Scopes();
    private Scope scope;
    private int scopeLevel;

    /**
     * The following field is set to {@code true} (via
     * {@link #sourceCodeSeen(org.opengrok.indexer.analysis.SourceCodeSeenEvent)})
     * when applicable during lexing before a call to {@link #startNewLine()}
     * so that the lines-of-code count is also incremented.
     */
    private boolean didSeePhysicalLOC;

    private int loc;

    /**
     * See {@link RuntimeEnvironment#getUserPage()}. Per default initialized in
     * the constructor and here to be consistent and avoid lot of unnecessary
     * lookups.
     *
     * @see #startNewLine()
     */
    private String userPageLink;
    /**
     * See {@link RuntimeEnvironment#getUserPageSuffix()}. Per default
     * initialized in the constructor and here to be consistent and avoid lot of
     * unnecessary lookups.
     *
     * @see #startNewLine()
     */
    private String userPageSuffix;

    /**
     * The span class name from the last call to
     * {@link #disjointSpan(java.lang.String)}.
     */
    private String disjointSpanClassName;

    /**
     * Initialize an instance, passing a {@link ScanningSymbolMatcher} which
     * will be owned by the {@link JFlexXref}.
     * @param matcher a defined instance
     */
    public JFlexXref(ScanningSymbolMatcher matcher) {
        if (matcher == null) {
            throw new IllegalArgumentException("`matcher' is null");
        }
        this.matcher = matcher;
        matcher.setSymbolMatchedListener(this);
        matcher.setNonSymbolMatchedListener(this);
        // The xrefer will own the matcher, so we won't have to unsubscribe.

        userPageLink = RuntimeEnvironment.getInstance().getUserPage();
        if (userPageLink != null && userPageLink.length() == 0) {
            userPageLink = null;
        }
        userPageSuffix = RuntimeEnvironment.getInstance().getUserPageSuffix();
        if (userPageSuffix != null && userPageSuffix.length() == 0) {
            userPageSuffix = null;
        }
    }

    public void setReader(Reader input) {
        if (input == null) {
            throw new IllegalArgumentException("`input' is null");
        }
        matcher.yyreset(input);
        matcher.reset();
    }

    /**
     * Resets the instance.
     * Normally, it only makes sense to call this method after calling
     * {@link #setReader(java.io.Reader)} to reset the instance's
     * {@link ScanningSymbolMatcher} first as well.
     */
    @Override
    public void reset() {
        annotation = null;
        didSeePhysicalLOC = false;
        disjointSpanClassName = null;
        loc = 0;
        scopes = new Scopes();
        scope = null;
        scopeLevel = 0;
        scopeOpen = false;
    }

    /**
     * Gets the line number from {@link ScanningSymbolMatcher#getLineNumber()}
     * of the instance's {@link ScanningSymbolMatcher}.
     * @return yyline
     */
    @Override
    public int getLineNumber() {
        return matcher.getLineNumber();
    }

    /**
     * Gets the determined count of physical lines-of-code.
     * @return lines-of-code count
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

    @Override
    public void symbolMatched(SymbolMatchedEvent evt) {
        try {
            JFlexXrefUtils.writeSymbol(out, defs, urlPrefix, project,
                evt.getStr(), null, matcher.getLineNumber(), false, false);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Sets a value indicating that source code was encountered such that
     * {@link #getLOC()} will be incremented upon the next
     * {@link #startNewLine()} or at end-of-file.
     * @param evt ignored
     */
    @Override
    public void sourceCodeSeen(SourceCodeSeenEvent evt) {
        didSeePhysicalLOC = true;
    }

    @Override
    public void nonSymbolMatched(TextMatchedEvent evt) {
        String str = evt.getStr();
        try {
            switch (evt.getHint()) {
                case EM:
                    out.write("<em>");
                    Util.htmlize(str, out, PRE);
                    out.write("</em>");
                    break;
                case STRONG:
                    out.write("<strong>");
                    Util.htmlize(str, out, PRE);
                    out.write("</strong>");
                    break;
                case NONE:
                default:
                    Util.htmlize(str, out, PRE);
                    break;
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void keywordMatched(TextMatchedEvent evt) {
        try {
            writeKeyword(evt.getStr(), matcher.getLineNumber());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void endOfLineMatched(TextMatchedEvent evt) {
        try {
            startNewLine();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void disjointSpanChanged(DisjointSpanChangedEvent evt) {
        try {
            disjointSpan(evt.getDisjointSpanClassName());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void linkageMatched(LinkageMatchedEvent evt) {
        String str = evt.getStr();
        String lstr = evt.getLinkStr();
        LinkageType linkageType = evt.getLinkageType();
        try {
            switch (linkageType) {
                case EMAIL:
                    writeEMailAddress(str);
                    break;
                case LABEL:
                    // Only PowerShell seems to be using this.
                    out.write("<a class=\"xlbl\" name=\"");
                    Util.URIEncode(lstr, out);
                    out.write("\">");
                    Util.htmlize(str, out, PRE);
                    out.write("</a>");
                    break;
                case LABELDEF:
                    // Only PowerShell seems to be using this.
                    JFlexXrefUtils.writeSameFileLinkSymbol(out, str);
                    break;
                case FILELIKE:
                    out.write("<a href=\"");
                    out.write(urlPrefix);
                    out.write(QueryParameters.PATH_SEARCH_PARAM_EQ);
                    /*
                     * Maybe in the future the following should properly be
                     * qurlencode(), but just htmlize() it for now.
                     */
                    Util.htmlize(lstr, out);
                    JFlexXrefUtils.appendProject(out, project);
                    out.write("\">");
                    Util.htmlize(str, out, PRE);
                    out.write("</a>");
                    break;
                case PATHLIKE:
                    out.write(Util.breadcrumbPath(urlPrefix +
                            QueryParameters.PATH_SEARCH_PARAM_EQ, str, '/'));
                    break;
                case QUERY:
                    out.write("<a href=\"");
                    out.write(urlPrefix);
                    out.write(QueryParameters.FULL_SEARCH_PARAM_EQ);
                    Util.qurlencode(lstr, out);
                    JFlexXrefUtils.appendProject(out, project);
                    out.write("\">");
                    Util.htmlize(str, out, PRE);
                    out.write("</a>");
                    break;
                case REFS:
                    out.write("<a href=\"");
                    out.write(urlPrefix);
                    out.write(QueryParameters.REFS_SEARCH_PARAM_EQ);
                    Util.qurlencode(lstr, out);
                    JFlexXrefUtils.appendProject(out, project);
                    out.write("\">");
                    Util.htmlize(str, out, PRE);
                    out.write("</a>");
                    break;
                case URI:
                    appendLink(str);
                    break;
                default:
                    throw new UnsupportedOperationException("LinkageType" +
                        linkageType);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void pathlikeMatched(PathlikeMatchedEvent evt) {
        String str = evt.getStr();
        String breadcrumbPath = Util.breadcrumbPath(urlPrefix +
                QueryParameters.PATH_SEARCH_PARAM_EQ, str, evt.getSep(),
                getProjectPostfix(true), evt.getCanonicalize());
        try {
            out.write(breadcrumbPath);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void scopeChanged(ScopeChangedEvent evt) {
        ScopeAction action = evt.getAction();
        switch (action) {
            case INC:
                incScope();
                break;
            case DEC:
                decScope();
                break;
            case END:
                endScope();
                break;
            default:
                throw new UnsupportedOperationException("ScopeAction " +
                    action);
        }

        //
        // TODO: maybe retire this odd convention which I've kept to minimize
        // test differences.
        //
        String str = evt.getStr();
        try {
            if (str.length() > 1) {
                out.write(str);
            } else if (str.length() > 0) {
                writeUnicodeChar(str.charAt(0));
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Calls
     * {@link JFlexXrefUtils#appendLink(java.io.Writer, org.opengrok.indexer.analysis.JFlexLexer, java.lang.String, boolean, java.util.regex.Pattern)}
     * with the active {@link Writer}, the instance's
     * {@link ScanningSymbolMatcher}, {@code url}, {@code false}, and
     * {@code null}.
     * @param url the URL to append
     * @throws IOException if an error occurs while appending
     */
    protected void appendLink(String url)
            throws IOException {
        JFlexXrefUtils.appendLink(out, matcher, url, false, null);
    }

    protected String getProjectPostfix(boolean encoded) {
        String amp = encoded ? "&amp;" : "&";
        return project == null ? "" : (amp + QueryParameters.PROJECT_SEARCH_PARAM_EQ +
                project.getName());
    }

    protected void startScope() {
        Scope newScope = JFlexXrefUtils.maybeNewScope(scopesEnabled, scope,
            matcher, defs);
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
                scope.setLineTo(matcher.getLineNumber());
                scopes.addScope(scope);
                scope = null;
            }
        }
    }

    protected void endScope() {
        if (scope != null && scopeLevel == 0) {
            scope.setLineTo(matcher.getLineNumber());
            scopes.addScope(scope);
            scope = null;
        }
    }

    /**
     * Get generated scopes.
     * @return scopes for current line
     */
    @Override
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
    private void disjointSpan(String className) throws IOException {
        disjointSpanClassName = JFlexXrefUtils.disjointSpan(out, className,
            disjointSpanClassName);
    }

    /**
     * Write xref to the specified {@code Writer}.
     *
     * @param out xref destination
     * @throws IOException on error when writing the xref
     */
    @Override
    public void write(Writer out) throws IOException {
        this.out = out;
        if (defs != null) {
            defs.resetUnused();
        }
        JFlexXrefUtils.writeSymbolTable(out, defs);
        startNewLine();
        while (matcher.yylex() != matcher.getYYEOF()) {
            // NOPMD while statement intentionally empty
            // nothing to do here, yylex() will do the work
        }

        disjointSpan(null);

        // terminate scopes
        if (scopeOpen) {
            out.write("</span>");
            scopeOpen = false;
        }

        while (!matcher.emptyStack()) {
            matcher.yypop();
        }

        // Account for dangling line of code. Unlike line number, LOC is
        // incremented post- rather than pre-.
        if (didSeePhysicalLOC) {
            ++loc;
            didSeePhysicalLOC = false;
        }
    }

    /**
     * Terminate the current line and insert preamble for the next line. The
     * line count is taken from {@link ScanningSymbolMatcher#getLineNumber()}.
     * The lines-of-code count may be incremented if {@link #didSeePhysicalLOC}
     * has been set to {@code true} (after which it will be reset to
     * {@code false}).
     * @throws IOException on error when writing the xref
     */
    public void startNewLine() throws IOException {
        String iconId = null;
        int line = matcher.getLineNumber();
        boolean skipNl = false;

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
                    /*
                     * It seems scope.getSignature() is sometimes null, so the
                     * following can create values with "null" in the
                     * concatenated string -- and tests are expecting that.
                     */
                    Util.htmlize(scope.getName() + scope.getSignature(), out,
                        PRE);
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
     * Writes a keyword symbol.
     *
     * @param symbol the symbol to write
     * @param line the line number on which the symbol appears
     * @throws IOException if an error occurs while writing to the stream
     */
    protected void writeKeyword(String symbol, int line) throws IOException {
        JFlexXrefUtils.writeSymbol(out, defs, urlPrefix, project,
            symbol, null, line, false, true);
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
