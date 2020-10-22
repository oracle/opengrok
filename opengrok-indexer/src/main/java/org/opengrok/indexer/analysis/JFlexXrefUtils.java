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

import java.io.IOException;
import java.io.Writer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.opengrok.indexer.analysis.Definitions.Tag;
import org.opengrok.indexer.analysis.Scopes.Scope;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.util.UriUtils;
import org.opengrok.indexer.web.HtmlConsts;
import org.opengrok.indexer.web.QueryParameters;
import org.opengrok.indexer.web.Util;

/**
 * Represents a container for extracted, shared logic between {@link JFlexXref}
 * and its backwardly-compatible cousin, {@link JFlexNonXref}.
 */
public class JFlexXrefUtils {

    /**
     * Matches an HTML 5 ID or Name.
     */
    private static final Pattern HTML5_ID_NAME = Pattern.compile("(?U)^\\S+$");

    /**
     * Appends the {@code url} to the specified {@code out} {@link Writer}.
     * <p>If {@code doEndingPushback} is true, then
     * {@link StringUtils#countURIEndingPushback(java.lang.String)} is enlisted
     * for use with {@link JFlexLexer#yypushback(int)} -- i.e., {@code url} is
     * only partially written.
     * <p>If {@code collateralCapture} is not null, then its match in
     * {@code url} will alternatively mark the start of a count for pushback --
     * i.e., everything at and beyond the first {@code collateralCapture} match
     * will be considered not to belong to the URI.
     * <p>If the pushback count is equal to the length of {@code url}, then it
     * is simply written -- and nothing is pushed back -- in order to avoid a
     * never-ending {@code yylex()} loop.
     * @param out a defined, target instance
     * @param lexer a defined, associated lexer
     * @param url the URL to append
     * @param doEndingPushback a value indicating whether to test the
     * {@code url} with
     * {@link StringUtils#countURIEndingPushback(java.lang.String)}
     * @param collateralCapture optional pattern to indicate characters which
     * may have been captured as valid URI characters but in a particular
     * context should mark the start of a pushback
     * @throws IOException if an error occurs while appending
     */
    public static void appendLink(Writer out, JFlexLexer lexer, String url,
            boolean doEndingPushback, Pattern collateralCapture) throws IOException {

        UriUtils.TrimUriResult result = UriUtils.trimUri(url, doEndingPushback, collateralCapture);
        if (result.getPushBackCount() > 0) {
            lexer.yypushback(result.getPushBackCount());
        }

        url = result.getUri();
        out.write("<a href=\"");
        Util.htmlize(url, out);
        out.write("\">");
        Util.htmlize(url, out);
        out.write("</a>");
    }

    /**
     * Writes if {@code project} is not null an XML &amp;amp; followed by
     * the {@link Project#getName()} to the specified {@code out}
     * {@link Writer}.
     * @param out a defined instance
     * @param project possibly a defined instance or null
     * @throws IOException if {@link Writer#write(java.lang.String)} fails
     */
    public static void appendProject(Writer out, Project project)
            throws IOException {
        if (project != null) {
            out.write("&amp;");
            out.write(QueryParameters.PROJECT_SEARCH_PARAM_EQ);
            out.write(project.getName());
        }
    }

    /**
     * Writes the closing of an open span tag -- if
     * {@code currentDisjointSpanName} is not null -- and the opening -- if
     * {@code className} is non-null -- of a new span tag.
     * @param out a defined, target instance
     * @param className the class name for the new tag or {@code null} just to
     * close an open tag.
     * @param currentDisjointSpanName possibly a defined instance or null
     * @return {@code className}
     * @throws IOException if an output error occurs
     */
    public static String disjointSpan(Writer out, String className,
        String currentDisjointSpanName)
            throws IOException {
        if (currentDisjointSpanName != null) {
            out.write(HtmlConsts.ZSPAN);
        }
        if (className != null) {
            out.write(String.format(HtmlConsts.SPAN_FMT, className));
        }
        return className;
    }

    /**
     * Generate span id for namespace based on line number, name, and signature
     * (more functions with same name and signature can be defined in single
     * file).
     * @param scope Scope to generate id from
     * @return generated span id
     */
    public static String generateId(Scope scope) {
        String name = Integer.toString(scope.getLineFrom()) + scope.getName()
                + scope.getSignature();
        int hash = name.hashCode();
        return "scope_id_" + Integer.toHexString(hash);
    }

    /**
     * Creates a new {@link Scope} if {@code scopesEnabled} is {@code true},
     * {@code existingScope} is null, {@code defs} is not null, and
     * {@link Definitions#getTags(int)} indicates that a function or method
     * is defined to starting at the {@link JFlexStackingLexer#getLineNumber()}
     * of {@code lexer}.
     * @param scopesEnabled are scopes enabled ?
     * @param existingScope possibly a defined instance or null
     * @param lexer a defined, associated lexer
     * @param defs possibly a defined instance or null
     * @return possibly a defined instance or null if a new scope is not to be
     * set
     */
    public static Scope maybeNewScope(boolean scopesEnabled,
        Scope existingScope, JFlexStackingLexer lexer, Definitions defs) {
        if (scopesEnabled && existingScope == null) {
            int line = lexer.getLineNumber();
            if (defs != null) {
                List<Tag> tags = defs.getTags(line);
                if (tags != null) {
                    for (Tag tag : tags) {
                        if (tag.type.startsWith("function") ||
                            tag.type.startsWith("method")) {
                            Scope scope = new Scope(tag.line, tag.line,
                                tag.symbol, tag.namespace, tag.signature);
                            return scope;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Write an e-mail address. The address will be obfuscated if
     * {@link RuntimeEnvironment#isObfuscatingEMailAddresses()} returns
     * {@code true}.
     * @param out a defined, target instance
     * @param address the address to write
     * @throws IOException if an error occurs while writing to the stream
     */
    public static void writeEMailAddress(Writer out, String address)
            throws IOException {
        if (RuntimeEnvironment.getInstance().isObfuscatingEMailAddresses()) {
            Util.htmlize(address.replace("@", " (at) "), out);
        } else {
            Util.htmlize(address, out);
        }
    }

    /**
     * Writes a symbol and generate links as appropriate.
     *
     * @param out a defined, target instance
     * @param defs a possibly defined instance or null
     * @param urlPrefix a defined instance
     * @param project a possibly defined instance or null
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
    public static boolean writeSymbol(Writer out, Definitions defs,
        String urlPrefix, Project project, String symbol, Set<String> keywords,
        int line, boolean caseSensitive, boolean isKeyword)
            throws IOException {
        String[] strs = new String[1];
        strs[0] = "";

        String check = caseSensitive ? symbol : symbol.toLowerCase(Locale.ROOT);
        if (isKeyword || (keywords != null && keywords.contains( check ))) {
            // This is a keyword, so we don't create a link.
            out.append("<b>");
            Util.htmlize(symbol, out);
            out.append("</b>");
            return false;
        }

        if (defs != null && defs.hasDefinitionAt(symbol, line, strs)) {
            // This is the definition of the symbol.
            String type = strs[0];
            String style_class = "d";

            XrefStyle style = XrefStyle.getStyle(type);
            if (style != null) {
                style_class = style.ssClass;
            }

            // 1) Create an anchor for direct links. (Perhaps we should only
            //    do this when there's exactly one definition of the symbol in
            //    this file? Otherwise, we may end up with multiple anchors with
            //    the same name.)
            //
            //    Skip the anchor if the symbol name is not a valid anchor
            //    name.  Note: In HTML 5, an ID or Name can be any string, with
            //    the following restrictions: must be at least one character
            //    long, must not contain any space characters.
            //    https://www.w3.org/TR/2010/WD-html-markup-20100624/datatypes.html
            //    Formerly for HTML 4, ID and Name were very restricted, as
            //    explained by @tulinkry.
            if (HTML5_ID_NAME.matcher(symbol).matches()) {
                out.append("<a class=\"");
                out.append(style_class);
                out.append("\" name=\"");
                Util.htmlize(symbol, out);
                out.append("\"/>");
            }

            // 2) Create a link that searches for all references to this symbol.
            out.append("<a href=\"");
            out.append(urlPrefix);
            out.append(QueryParameters.REFS_SEARCH_PARAM_EQ);
            Util.qurlencode(symbol, out);
            appendProject(out, project);
            out.append("\" class=\"");
            out.append(style_class);
            out.append(" intelliWindow-symbol\"");
            out.append(" data-definition-place=\"def\"");
            out.append(">");
            Util.htmlize(symbol, out);
            out.append("</a>");
        } else if (defs != null && defs.occurrences(symbol) == 1) {
            writeSameFileLinkSymbol(out, symbol);
        } else {
            // This is a symbol that is not defined in this file, or a symbol
            // that is defined more than once in this file. In either case, we
            // can't generate a direct link to the definition, so generate a
            // link to search for all definitions of that symbol instead.
            out.append("<a href=\"");
            out.append(urlPrefix);
            out.append(QueryParameters.DEFS_SEARCH_PARAM_EQ);
            Util.qurlencode(symbol, out);
            appendProject(out, project);
            out.append("\"");
            out.append(" class=\"intelliWindow-symbol\"");
            out.append(" data-definition-place=\"undefined-in-file\"");
            out.append(">");
            Util.htmlize(symbol, out);
            out.append("</a>");
        }
        return true;
    }

    /**
     * Write a symbol with a link to its definition which is expected at
     * exactly one location in the same file.
     * @param out a defined, target instance
     * @param symbol the symbol to write
     * @throws IOException if {@link Writer#append(java.lang.CharSequence)}
     * fails
     */
    public static void writeSameFileLinkSymbol(Writer out, String symbol)
            throws IOException {
        // This is a reference to a symbol defined exactly once in this file.
        String style_class = "d";

        // Generate a direct link to the symbol definition.
        out.append("<a class=\"");
        out.append(style_class);
        out.append(" intelliWindow-symbol\" href=\"#");
        Util.URIEncode(symbol, out);
        out.append("\"");
        out.append(" data-definition-place=\"defined-in-file\"");
        out.append(">");
        Util.htmlize(symbol, out);
        out.append("</a>");
    }

    /**
     * Write a JavaScript function that returns an array with the definitions to
     * list in the navigation panel. Each element of the array is itself an
     * array containing the name of the definition type, the CSS class name for
     * the type, and an array of (symbol, line) pairs for the definitions of
     * that type.
     * @param out a defined, target instance
     * @param defs a possibly defined instance or null
     * @throws IOException if {@link Writer#append(java.lang.CharSequence)}
     * fails
     */
    public static void writeSymbolTable(Writer out, Definitions defs)
            throws IOException {
        if (defs == null) {
            // No definitions, no symbol table to write
            return;
        }

        // We want the symbol table to be sorted
        Comparator<Tag> cmp = (Tag tag1, Tag tag2) -> {
            // Order by symbol name, and then by line number if multiple
            // definitions use the same symbol name
            int ret = tag1.symbol.compareTo(tag2.symbol);
            if (ret == 0) {
                ret = tag1.line - tag2.line;
            }
            return ret;
        };

        Map<String, SortedSet<Tag>> symbols
                = new HashMap<>();

        for (Tag tag : defs.getTags()) {
            XrefStyle style = XrefStyle.getStyle(tag.type);
            if (style != null && style.title != null) {
                SortedSet<Tag> tags = symbols.get(style.name);
                if (tags == null) {
                    tags = new TreeSet<>(cmp);
                    symbols.put(style.name, tags);
                }
                tags.add(tag);
            }
        }

        // TODO try to get rid of included js scripts generated from here (all
        // js should ideally be in util)
        out.append("<script type=\"text/javascript\">/* <![CDATA[ */\n");
        out.append("function get_sym_list(){return [");

        boolean first = true;
        for (XrefStyle style : XrefStyle.DEFINITION_STYLES) {
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
     * Write HTML escape sequence for the specified Unicode character, unless
     * it's an ISO control character, in which case it is ignored.
     * @param out a defined, target instance
     * @param c the character to write
     * @throws IOException if an error occurs while writing to the stream
     */
    public static void writeUnicodeChar(Writer out, char c)
            throws IOException {
        if (!Character.isISOControl(c)) {
            out.append("&#").append(Integer.toString(c)).append(';');
        }
    }

    /** Private to enforce static. */
    private JFlexXrefUtils() {
    }
}
