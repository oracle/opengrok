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
 * Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.util.UriUtils;

/**
 * Represents an abstract base class for subclasses of
 * {@link JFlexStateStacker} that can publish as {@link ScanningSymbolMatcher}.
 */
public abstract class JFlexSymbolMatcher extends JFlexStateStacker
        implements ScanningSymbolMatcher {

    private SymbolMatchedListener symbolListener;
    private NonSymbolMatchedListener nonSymbolListener;
    private String disjointSpanClassName;

    /**
     * Associates the specified listener, replacing the former one.
     * @param l defined instance
     */
    @Override
    public void setSymbolMatchedListener(SymbolMatchedListener l) {
        if (l == null) {
            throw new IllegalArgumentException("`l' is null");
        }
        symbolListener = l;
    }

    /**
     * Clears any association to a listener.
     */
    @Override
    public void clearSymbolMatchedListener() {
        symbolListener = null;
    }

    /**
     * Associates the specified listener, replacing the former one.
     * @param l defined instance
     */
    @Override
    public void setNonSymbolMatchedListener(NonSymbolMatchedListener l) {
        if (l == null) {
            throw new IllegalArgumentException("`l' is null");
        }
        nonSymbolListener = l;
    }

    /**
     * Clears any association to a listener.
     */
    @Override
    public void clearNonSymbolMatchedListener() {
        nonSymbolListener = null;
    }

    /**
     * Gets the class name value from the last call to
     * {@link #onDisjointSpanChanged(String, long)}.
     * @return a defined value or null
     */
    protected String getDisjointSpanClassName() {
        return disjointSpanClassName;
    }

    /**
     * Raises
     * {@link SymbolMatchedListener#symbolMatched(org.opengrok.indexer.analysis.SymbolMatchedEvent)}
     * for a subscribed listener.
     * @param str the symbol string
     * @param start the symbol start position
     */
    protected void onSymbolMatched(String str, long start) {
        SymbolMatchedListener l = symbolListener;
        if (l != null) {
            SymbolMatchedEvent evt = new SymbolMatchedEvent(this, str, start,
                start + str.length());
            l.symbolMatched(evt);
        }
    }

    /**
     * Raises
     * {@link SymbolMatchedListener#sourceCodeSeen(org.opengrok.indexer.analysis.SourceCodeSeenEvent)}
     * for all subscribed listeners in turn.
     * @param start the source code start position
     */
    protected void onSourceCodeSeen(long start) {
        SymbolMatchedListener l = symbolListener;
        if (l != null) {
            SourceCodeSeenEvent evt = new SourceCodeSeenEvent(this, start);
            l.sourceCodeSeen(evt);
        }
    }

    /**
     * Calls {@link #onNonSymbolMatched(String, long)} with the
     * {@link String#valueOf(char)} {@code c} and {@code start}.
     * @param c the text character
     * @param start the text start position
     */
    protected void onNonSymbolMatched(char c, long start) {
        onNonSymbolMatched(String.valueOf(c), start);
    }

    /**
     * Raises
     * {@link NonSymbolMatchedListener#nonSymbolMatched(org.opengrok.indexer.analysis.TextMatchedEvent)}
     * for a subscribed listener.
     * @param str the text string
     * @param start the text start position
     */
    protected void onNonSymbolMatched(String str, long start) {
        NonSymbolMatchedListener l = nonSymbolListener;
        if (l != null) {
            TextMatchedEvent evt = new TextMatchedEvent(this, str, start,
                start + str.length());
            l.nonSymbolMatched(evt);
        }
    }

    /**
     * Raises
     * {@link NonSymbolMatchedListener#nonSymbolMatched(org.opengrok.indexer.analysis.TextMatchedEvent)}
     * for a subscribed listener.
     * @param str the text string
     * @param hint the text hint
     * @param start the text start position
     */
    protected void onNonSymbolMatched(String str, EmphasisHint hint, long start) {
        NonSymbolMatchedListener l = nonSymbolListener;
        if (l != null) {
            TextMatchedEvent evt = new TextMatchedEvent(this, str, hint, start,
                start + str.length());
            l.nonSymbolMatched(evt);
        }
    }

    /**
     * Raises
     * {@link NonSymbolMatchedListener#keywordMatched(org.opengrok.indexer.analysis.TextMatchedEvent)}
     * for a subscribed listener.
     * @param str the text string
     * @param start the text start position
     */
    protected void onKeywordMatched(String str, long start) {
        NonSymbolMatchedListener l = nonSymbolListener;
        if (l != null) {
            TextMatchedEvent evt = new TextMatchedEvent(this, str, start,
                start + str.length());
            l.keywordMatched(evt);
        }
    }

    /**
     * Calls {@link #setLineNumber(int)} with the sum of
     * {@link #getLineNumber()} and the number of LFs in {@code str}, and then
     * raises
     * {@link NonSymbolMatchedListener#endOfLineMatched(org.opengrok.indexer.analysis.TextMatchedEvent)}
     * for a subscribed listener.
     * @param str the text string
     * @param start the text start position
     */
    protected void onEndOfLineMatched(String str, long start) {
        setLineNumber(getLineNumber() + countEOLs(str));
        NonSymbolMatchedListener l = nonSymbolListener;
        if (l != null) {
            TextMatchedEvent evt = new TextMatchedEvent(this, str, start,
                start + str.length());
            l.endOfLineMatched(evt);
        }
    }

    /**
     * Raises
     * {@link NonSymbolMatchedListener#disjointSpanChanged(org.opengrok.indexer.analysis.DisjointSpanChangedEvent)}
     * for a subscribed listener.
     * @param className the text string
     * @param position the text position
     */
    protected void onDisjointSpanChanged(String className, long position) {
        disjointSpanClassName = className;
        NonSymbolMatchedListener l = nonSymbolListener;
        if (l != null) {
            DisjointSpanChangedEvent evt = new DisjointSpanChangedEvent(this,
                className, position);
            l.disjointSpanChanged(evt);
        }
    }

    /**
     * Calls
     * {@link #onUriMatched(String, long, Pattern)}
     * with {@code uri}, {@code start}, and {@code null}.
     * @param uri the URI string
     * @param start the URI start position
     */
    protected void onUriMatched(String uri, long start) {
        onUriMatched(uri, start, null);
    }

    /**
     * Raises
     * {@link NonSymbolMatchedListener#linkageMatched(org.opengrok.indexer.analysis.LinkageMatchedEvent)}
     * of {@link LinkageType#URI} for a subscribed listener.
     * <p>First, the end of {@code uri} is possibly trimmed (with a
     * corresponding call to {@link #yypushback(int)}) based on the result
     * of {@link StringUtils#countURIEndingPushback(java.lang.String)} and
     * optionally
     * {@link StringUtils#countPushback(java.lang.String, java.util.regex.Pattern)}
     * if {@code collateralCapture} is not null.
     * <p>If the pushback count is equal to the length of {@code url}, then it
     * is simply written -- and nothing is pushed back -- in order to avoid a
     * never-ending {@code yylex()} loop.
     *
     * @param uri the URI string
     * @param start the URI start position
     * @param collateralCapture optional pattern to indicate characters which
     * may have been captured as valid URI characters but in a particular
     * context should mark the start of a pushback
     */
    protected void onUriMatched(String uri, long start, Pattern collateralCapture) {
        UriUtils.TrimUriResult result = UriUtils.trimUri(uri, true, collateralCapture);
        if (result.getPushBackCount() > 0) {
            yypushback(result.getPushBackCount());
        }

        NonSymbolMatchedListener l = nonSymbolListener;
        if (l != null) {
            uri = result.getUri();
            LinkageMatchedEvent evt = new LinkageMatchedEvent(this, uri,
                LinkageType.URI, start, start + uri.length());
            l.linkageMatched(evt);
        }
    }

    /**
     * Raises
     * {@link NonSymbolMatchedListener#linkageMatched(org.opengrok.indexer.analysis.LinkageMatchedEvent)}
     * of {@link LinkageType#FILELIKE} for a subscribed listener.
     * @param str the text string
     * @param start the text start position
     */
    protected void onFilelikeMatched(String str, long start) {
        NonSymbolMatchedListener l = nonSymbolListener;
        if (l != null) {
            LinkageMatchedEvent evt = new LinkageMatchedEvent(this, str,
                LinkageType.FILELIKE, start, start + str.length());
            l.linkageMatched(evt);
        }
    }

    /**
     * Raises
     * {@link NonSymbolMatchedListener#pathlikeMatched(org.opengrok.indexer.analysis.PathlikeMatchedEvent)}
     * for a subscribed listener.
     * @param str the path text string
     * @param sep the path separator
     * @param canonicalize a value indicating whether the path should be
     * canonicalized
     * @param start the text start position
     */
    protected void onPathlikeMatched(String str, char sep, boolean canonicalize, long start) {
        NonSymbolMatchedListener l = nonSymbolListener;
        if (l != null) {
            PathlikeMatchedEvent  evt = new PathlikeMatchedEvent(this, str,
                sep, canonicalize, start, start + str.length());
            l.pathlikeMatched(evt);
        }
    }

    /**
     * Raises
     * {@link NonSymbolMatchedListener#linkageMatched(org.opengrok.indexer.analysis.LinkageMatchedEvent)}
     * of {@link LinkageType#EMAIL} for a subscribed listener.
     * @param str the text string
     * @param start the text start position
     */
    protected void onEmailAddressMatched(String str, long start) {
        NonSymbolMatchedListener l = nonSymbolListener;
        if (l != null) {
            LinkageMatchedEvent evt = new LinkageMatchedEvent(this, str,
                LinkageType.EMAIL, start, start + str.length());
            l.linkageMatched(evt);
        }
    }

    /**
     * Raises
     * {@link NonSymbolMatchedListener#linkageMatched(org.opengrok.indexer.analysis.LinkageMatchedEvent)}
     * of {@link LinkageType#LABEL} for a subscribed listener.
     * @param str the text string (literal capture)
     * @param start the text start position
     * @param lstr the text link string
     */
    protected void onLabelMatched(String str, long start, String lstr) {
        NonSymbolMatchedListener l = nonSymbolListener;
        if (l != null) {
            LinkageMatchedEvent evt = new LinkageMatchedEvent(this, str,
                LinkageType.LABEL, start, start + str.length(), lstr);
            l.linkageMatched(evt);
        }
    }

    /**
     * Raises
     * {@link NonSymbolMatchedListener#linkageMatched(org.opengrok.indexer.analysis.LinkageMatchedEvent)}
     * of {@link LinkageType#LABELDEF} for a subscribed listener.
     * @param str the text string (literal capture)
     * @param start the text start position
     */
    protected void onLabelDefMatched(String str, long start) {
        NonSymbolMatchedListener l = nonSymbolListener;
        if (l != null) {
            LinkageMatchedEvent evt = new LinkageMatchedEvent(this, str,
                LinkageType.LABELDEF, start, start + str.length());
            l.linkageMatched(evt);
        }
    }

    /**
     * Raises
     * {@link NonSymbolMatchedListener#linkageMatched(org.opengrok.indexer.analysis.LinkageMatchedEvent)}
     * of {@link LinkageType#QUERY} for a subscribed listener.
     * @param str the text string
     * @param start the text start position
     */
    protected void onQueryTermMatched(String str, long start) {
        NonSymbolMatchedListener l = nonSymbolListener;
        if (l != null) {
            LinkageMatchedEvent evt = new LinkageMatchedEvent(this, str,
                LinkageType.QUERY, start, start + str.length());
            l.linkageMatched(evt);
        }
    }

    /**
     * Raises
     * {@link NonSymbolMatchedListener#linkageMatched(org.opengrok.indexer.analysis.LinkageMatchedEvent)}
     * of {@link LinkageType#REFS} for a subscribed listener.
     * @param str the text string
     * @param start the text start position
     */
    protected void onRefsTermMatched(String str, long start) {
        NonSymbolMatchedListener l = nonSymbolListener;
        if (l != null) {
            LinkageMatchedEvent evt = new LinkageMatchedEvent(this, str,
                LinkageType.REFS, start, start + str.length());
            l.linkageMatched(evt);
        }
    }

    /**
     * Raises
     * {@link NonSymbolMatchedListener#scopeChanged(org.opengrok.indexer.analysis.ScopeChangedEvent)}
     * for a subscribed listener.
     * @param action the scope change action
     * @param str the text string
     * @param start the text start position
     */
    protected void onScopeChanged(ScopeAction action, String str, long start) {
        NonSymbolMatchedListener l = nonSymbolListener;
        if (l != null) {
            ScopeChangedEvent evt = new ScopeChangedEvent(this, action, str,
                start, start + str.length());
            l.scopeChanged(evt);
        }
    }

    /**
     * Calls
     * {@link #onFilteredSymbolMatched(String, long, Set, boolean)}
     * with {@code str}, {@code start}, {@code keywords}, and {@code true}.
     * @param str the text string
     * @param start the text start position
     * @param keywords an optional set to search for {@code str} as a member to
     * indicate a keyword
     * @return true if the {@code str} was not in {@code keywords} or if
     * {@code keywords} was null
     */
    protected boolean onFilteredSymbolMatched(String str, long start, Set<String> keywords) {
        return onFilteredSymbolMatched(str, start, keywords, true);
    }

    /**
     * Raises {@link #onKeywordMatched(String, long)} if
     * {@code keywords} is not null and {@code str} is found as a member (in a
     * case-sensitive or case-less search per {@code caseSensitive}); otherwise
     * raises {@link #onSymbolMatched(String, long)}.
     * @param str the text string
     * @param start the text start position
     * @param keywords an optional set to search for {@code str} as a member to
     * indicate a keyword
     * @param caseSensitive a value indicating if {@code keywords} should be
     * searched for {@code str} as-is ({@code true}) or if the lower-case
     * equivalent of {@code str} should be used ({@code false}).
     * @return true if the {@code str} was not in {@code keywords} or if
     * {@code keywords} was null
     */
    protected boolean onFilteredSymbolMatched(String str, long start, Set<String> keywords,
            boolean caseSensitive) {

        if (keywords != null) {
            String check = caseSensitive ? str : str.toLowerCase(Locale.ROOT);
            if (keywords.contains(check)) {
                onKeywordMatched(str, start);
                return false;
            }
        }
        onSymbolMatched(str, start);
        return true;
    }

    private static int countEOLs(String str) {
        Matcher m = StringUtils.STANDARD_EOL.matcher(str);
        int n = 0;
        while (m.find()) {
            ++n;
        }
        return n;
    }
}
