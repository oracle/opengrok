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
 * Copyright (c) 2005, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2011, Jens Elkner.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2019, Krystof Tulinger <k.tulinger@seznam.cz>.
 */
package org.opengrok.indexer.web;

import static org.opengrok.indexer.index.Indexer.PATH_SEPARATOR;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import javax.servlet.http.HttpServletRequest;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.Annotation;
import org.opengrok.indexer.history.HistoryException;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.PlatformUtils;

/**
 * Class for useful functions.
 */
public final class Util {

    private static final Logger LOGGER = LoggerFactory.getLogger(Util.class);

    private static final int BOLD_COUNT_THRESHOLD = 1000;

    private static final String anchorLinkStart = "<a href=\"";
    private static final String anchorClassStart = "<a class=\"";
    private static final String anchorEnd = "</a>";
    private static final String closeQuotedTag = "\">";

    private static final String RE_Q_ESC_AMP_AMP = "\\?|&amp;|&";
    private static final String RE_Q_E_A_A_COUNT_EQ_VAL = "(" + RE_Q_ESC_AMP_AMP + "|\\b)" +
            QueryParameters.COUNT_PARAM_EQ + "\\d+";
    private static final String RE_Q_E_A_A_START_EQ_VAL = "(" + RE_Q_ESC_AMP_AMP + "|\\b)" +
            QueryParameters.START_PARAM_EQ + "\\d+";
    private static final String RE_A_ANCHOR_Q_E_A_A = "^(" + RE_Q_ESC_AMP_AMP + ")";

    /** Private to enforce static. */
    private Util() {
    }

    /**
     * Return a string that represents <code>s</code> in HTML by calling
     * {@link #htmlize(java.lang.CharSequence, java.lang.Appendable, boolean)}
     * with {@code s}, a transient {@link StringBuilder}, and {@code true}.
     * <p>
     * (N.b. if no special characters are present, {@code s} is returned as is,
     * without the expensive call.)
     *
     * @param s a defined string
     * @return a string representing the character sequence in HTML
     */
    public static String prehtmlize(String s) {
        if (!needsHtmlize(s, true)) {
            return s;
        }

        StringBuilder sb = new StringBuilder(s.length() * 2);
        try {
            htmlize(s, sb, true);
        } catch (IOException ioe) {
            // IOException cannot happen when the destination is a
            // StringBuilder. Wrap in an AssertionError so that callers
            // don't have to check for an IOException that should never
            // happen.
            throw new AssertionError("StringBuilder threw IOException", ioe);
        }
        return sb.toString();
    }

    /**
     * Calls
     * {@link #htmlize(java.lang.CharSequence, java.lang.Appendable, boolean)}
     * with {@code q}, a transient {@link StringBuilder}, and {@code true}.
     * @param q a character sequence
     * @return a string representing the character sequence in HTML
     */
    public static String prehtmlize(CharSequence q) {
        StringBuilder sb = new StringBuilder(q.length() * 2);
        try {
            htmlize(q, sb, true);
        } catch (IOException ioe) {
            // IOException cannot happen when the destination is a
            // StringBuilder. Wrap in an AssertionError so that callers
            // don't have to check for an IOException that should never
            // happen.
            throw new AssertionError("StringBuilder threw IOException", ioe);
        }
        return sb.toString();
    }

    /**
     * Append to {@code dest} the UTF-8 URL-encoded representation of the
     * Lucene-escaped version of {@code str}.
     * @param str a defined instance
     * @param dest a defined target
     * @throws IOException I/O exception
     */
    public static void qurlencode(String str, Appendable dest)
            throws IOException {
        URIEncode(QueryParser.escape(str), dest);
    }

    /**
     * Return a string that represents a <code>CharSequence</code> in HTML by
     * calling
     * {@link #htmlize(java.lang.CharSequence, java.lang.Appendable, boolean)}
     * with {@code s}, a transient {@link StringBuilder}, and {@code false}.
     * <p>
     * (N.b. if no special characters are present, {@code s} is returned as is,
     * without the expensive call.)
     *
     * @param s a defined string
     * @return a string representing the character sequence in HTML
     */
    public static String htmlize(String s) {
        if (!needsHtmlize(s, false)) {
            return s;
        }

        StringBuilder sb = new StringBuilder(s.length() * 2);
        try {
            htmlize(s, sb, false);
        } catch (IOException ioe) {
            // IOException cannot happen when the destination is a
            // StringBuilder. Wrap in an AssertionError so that callers
            // don't have to check for an IOException that should never
            // happen.
            throw new AssertionError("StringBuilder threw IOException", ioe);
        }
        return sb.toString();
    }

    /**
     * Return a string which represents a <code>CharSequence</code> in HTML by
     * calling
     * {@link #htmlize(java.lang.CharSequence, java.lang.Appendable, boolean)}
     * with {@code q}, a transient {@link StringBuilder}, and {@code false}.
     *
     * @param q a character sequence
     * @return a string representing the character sequence in HTML
     */
    public static String htmlize(CharSequence q) {
        StringBuilder sb = new StringBuilder(q.length() * 2);
        try {
            htmlize(q, sb, false);
        } catch (IOException ioe) {
            // IOException cannot happen when the destination is a
            // StringBuilder. Wrap in an AssertionError so that callers
            // don't have to check for an IOException that should never
            // happen.
            throw new AssertionError("StringBuilder threw IOException", ioe);
        }
        return sb.toString();
    }

    /**
     * Append a character sequence to the given destination whereby special
     * characters for HTML or characters that are not printable ASCII are
     * escaped accordingly.
     *
     * @param q a character sequence to escape
     * @param dest where to append the character sequence to
     * @param pre a value indicating whether the output is pre-formatted -- if
     * true then LFs will not be converted to &lt;br&gt; elements
     * @throws IOException if an error occurred when writing to {@code dest}
     */
    public static void htmlize(CharSequence q, Appendable dest, boolean pre)
            throws IOException {
        for (int i = 0; i < q.length(); i++) {
            htmlize(q.charAt(i), dest, pre);
        }
    }

    /**
     * Calls
     * {@link #htmlize(java.lang.CharSequence, java.lang.Appendable, boolean)}
     * with {@code q}, {@code dest}, and {@code false}.
     *
     * @param q a character sequence to escape
     * @param dest where to append the character sequence to
     * @throws IOException if an error occurred when writing to {@code dest}
     */
    public static void htmlize(CharSequence q, Appendable dest)
            throws IOException {
        htmlize(q, dest, false);
    }

    /**
     * Append a character array to the given destination whereby special
     * characters for HTML or characters that are not printable ASCII are
     * escaped accordingly.
     *
     * @param cs characters to escape
     * @param length max. number of characters to append, starting from index 0.
     * @param dest where to append the character sequence to
     * @throws IOException if an error occurred when writing to {@code dest}
     */
    public static void htmlize(char[] cs, int length, Appendable dest)
            throws IOException {
        int len = length;
        if (cs.length < length) {
            len = cs.length;
        }
        for (int i = 0; i < len; i++) {
            htmlize(cs[i], dest, false);
        }
    }

    /**
     * Append a character to the given destination whereby special characters
     * special for HTML or characters that are not printable ASCII are
     * escaped accordingly.
     *
     * @param c the character to append
     * @param dest where to append the character to
     * @param pre a value indicating whether the output is pre-formatted -- if
     * true then LFs will not be converted to &lt;br&gt; elements
     * @throws IOException if an error occurred when writing to {@code dest}
     * @see #needsHtmlize(char, boolean)
     */
    private static void htmlize(char c, Appendable dest, boolean pre)
            throws IOException {
        switch (c) {
            case '\'':
                dest.append("&apos;");
                break;
            case '"':
                dest.append("&quot;");
                break;
            case '&':
                dest.append("&amp;");
                break;
            case '>':
                dest.append("&gt;");
                break;
            case '<':
                dest.append("&lt;");
                break;
            case '\n':
                if (pre) {
                    dest.append(c);
                } else {
                    dest.append("<br/>");
                }
                break;
            default:
                if ((c >= ' ' && c <= '~') || (c < ' ' &&
                    Character.isWhitespace(c))) {
                    dest.append(c);
                } else {
                    dest.append("&#").append(Integer.toString(c)).append(';');
                }
                break;
        }
    }

    /**
     * Determine if a character is a special character needing HTML escaping or
     * is a character that is not printable ASCII.
     * @param c the character to examine
     * @param pre a value indicating whether the output is pre-formatted -- if
     * true then LFs will not be converted to &lt;br&gt; elements
     * @see #htmlize(char, java.lang.Appendable, boolean)
     */
    private static boolean needsHtmlize(char c, boolean pre) {
        switch (c) {
            case '\'':
            case '"':
            case '&':
            case '>':
            case '<':
                return true;
            case '\n':
                if (!pre) {
                    return true;
                }
            default:
                if ((c >= ' ' && c <= '~') || (c < ' ' &&
                    Character.isWhitespace(c))) {
                    return false;
                }
                return true;
        }
    }

    private static boolean needsHtmlize(CharSequence q, boolean pre) {
        for (int i = 0; i < q.length(); ++i) {
            if (needsHtmlize(q.charAt(i), pre)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convenience method for {@code breadcrumbPath(urlPrefix, path, PATH_SEPARATOR)}.
     *
     * @param urlPrefix prefix to add to each url
     * @param path path to crack
     * @return HTML markup fro the breadcrumb or the path itself.
     *
     * @see #breadcrumbPath(String, String, char)
     */
    public static String breadcrumbPath(String urlPrefix, String path) {
        return breadcrumbPath(urlPrefix, path, PATH_SEPARATOR);
    }

    /**
     * Convenience method for
     * {@code breadcrumbPath(urlPrefix, path, sep, "", false)}.
     *
     * @param urlPrefix prefix to add to each url
     * @param path path to crack
     * @param sep separator to use to crack the given path
     *
     * @return HTML markup fro the breadcrumb or the path itself.
     * @see #breadcrumbPath(String, String, char, String, boolean, boolean)
     */
    public static String breadcrumbPath(String urlPrefix, String path, char sep) {
        return breadcrumbPath(urlPrefix, path, sep, "", false);
    }

    /**
     * Convenience method for
     * {@code breadcrumbPath(urlPrefix, path, sep, "", false, path.endsWith(sep)}.
     *
     * @param urlPrefix prefix to add to each url
     * @param path path to crack
     * @param sep separator to use to crack the given path
     * @param urlPostfix suffix to add to each url
     * @param compact if {@code true} the given path gets transformed into its
     * canonical form (.i.e. all '.' and '..' and double separators removed, but
     * not always resolves to an absolute path) before processing starts.
     * @return HTML markup fro the breadcrumb or the path itself.
     * @see #breadcrumbPath(String, String, char, String, boolean, boolean)
     * @see #getCanonicalPath(String, char)
     */
    public static String breadcrumbPath(String urlPrefix, String path,
            char sep, String urlPostfix, boolean compact) {
        if (path == null || path.length() == 0) {
            return path;
        }
        return breadcrumbPath(urlPrefix, path, sep, urlPostfix, compact,
                path.charAt(path.length() - 1) == sep);
    }

    /**
     * Create a breadcrumb path to allow navigation to each element of a path.
     * Consecutive separators (<var>sep</var>) in the given <var>path</var> are
     * always collapsed into a single separator automatically. If
     * <var>compact</var> is {@code true} path gets translated into a canonical
     * path similar to {@link File#getCanonicalPath()}, however the current
     * working directory is assumed to be "/" and no checks are done (e.g.
     * neither whether the path [component] exists nor which type it is).
     *
     * @param urlPrefix what should be prepend to the constructed URL
     * @param path the full path from which the breadcrumb path is built.
     * @param sep the character that separates the path components in
     * <var>path</var>
     * @param urlPostfix what should be append to the constructed URL
     * @param compact if {@code true}, a canonical path gets constructed before
     * processing.
     * @param isDir if {@code true} a "/" gets append to the last path
     * component's link and <var>sep</var> to its name
     * @return <var>path</var> if it resolves to an empty or "/" or {@code null}
     * path, the HTML markup for the breadcrumb path otherwise.
     */
    public static String breadcrumbPath(String urlPrefix, String path,
            char sep, String urlPostfix, boolean compact, boolean isDir) {
        if (path == null || path.length() == 0) {
            return path;
        }
        String[] pnames = normalize(path.split(escapeForRegex(sep)), compact);
        if (pnames.length == 0) {
            return path;
        }
        String prefix = urlPrefix == null ? "" : urlPrefix;
        String postfix = urlPostfix == null ? "" : urlPostfix;
        StringBuilder pwd = new StringBuilder(path.length() + pnames.length);
        StringBuilder markup
                = new StringBuilder((pnames.length + 3 >> 1) * path.length()
                        + pnames.length
                        * (17 + prefix.length() + postfix.length()));
        int k = path.indexOf(pnames[0]);
        if (path.lastIndexOf(sep, k) != -1) {
            pwd.append(PATH_SEPARATOR);
            markup.append(sep);
        }
        for (int i = 0; i < pnames.length; i++) {
            pwd.append(URIEncodePath(pnames[i]));
            if (isDir || i < pnames.length - 1) {
                pwd.append(PATH_SEPARATOR);
            }
            markup.append(anchorLinkStart).append(prefix).append(pwd)
                    .append(postfix).append(closeQuotedTag).append(pnames[i])
                    .append(anchorEnd);
            if (isDir || i < pnames.length - 1) {
                markup.append(sep);
            }
        }
        return markup.toString();
    }

    /**
     * Normalize the given <var>path</var> to its canonical form. I.e. all
     * separators (<var>sep</var>) are replaced with a slash ('/'), all double
     * slashes are replaced by a single slash, all single dot path components
     * (".") of the formed path are removed and all double dot path components
     * (".." ) of the formed path are replaced with its parent or '/' if there
     * is no parent.
     * <p>
     * So the difference to {@link File#getCanonicalPath()} is, that this method
     * does not hit the disk (just string manipulation), resolves
     * <var>path</var>
     * always against '/' and thus always returns an absolute path, which may
     * actually not exist, and which has a single trailing '/' if the given
     * <var>path</var> ends with the given <var>sep</var>.
     *
     * @param path path to mangle. If not absolute or {@code null}, the current
     * working directory is assumed to be '/'.
     * @param sep file separator to use to crack <var>path</var> into path
     * components
     * @return always a canonical path which starts with a '/'.
     */
    public static String getCanonicalPath(String path, char sep) {
        if (path == null || path.length() == 0) {
            return "/";
        }
        String[] pnames = normalize(path.split(escapeForRegex(sep)), true);
        if (pnames.length == 0) {
            return "/";
        }
        StringBuilder buf = new StringBuilder(path.length());
        buf.append('/');
        for (int i = 0; i < pnames.length; i++) {
            buf.append(pnames[i]).append('/');
        }
        if (path.charAt(path.length() - 1) != sep) {
            // since is not a general purpose method. So we waive to handle
            // cases like:
            // || path.endsWith("/..") || path.endsWith("/.")
            buf.setLength(buf.length() - 1);
        }
        return buf.toString();
    }

    private static final Pattern EMAIL_PATTERN
            = Pattern.compile("([^<\\s]+@[^>\\s]+)");

    /**
     * Get email address of the author.
     *
     * @param author string containing author and possibly email address.
     * @return email address of the author or full author string if the author
     * string does not contain an email address.
     */
    public static String getEmail(String author) {
        Matcher emailMatcher = EMAIL_PATTERN.matcher(author);
        String email = author;
        if (emailMatcher.find()) {
            email = emailMatcher.group(1).trim();
        }

        return email;
    }

    /**
     * Remove all empty and {@code null} string elements from the given
     * <var>names</var> and optionally all redundant information like "." and
     * "..".
     *
     * @param names names to check
     * @param canonical if {@code true}, remove redundant elements as well.
     * @return a possible empty array of names all with a length &gt; 0.
     */
    private static String[] normalize(String[] names, boolean canonical) {
        LinkedList<String> res = new LinkedList<>();
        if (names == null || names.length == 0) {
            return new String[0];
        }
        for (String name : names) {
            if (name == null || name.length() == 0) {
                continue;
            }
            if (canonical) {
                if (name.equals("..")) {
                    if (!res.isEmpty()) {
                        res.removeLast();
                    }
                } else if (!name.equals(".")) {
                    res.add(name);
                }
            } else {
                res.add(name);
            }
        }
        return res.size() == names.length ? names : res.toArray(new String[res
                .size()]);
    }

    /**
     * Generate a regexp that matches the specified character. Escape it in case
     * it is a character that has a special meaning in a regexp.
     *
     * @param c the character that the regexp should match
     * @return a six-character string in the form of <code>&#92;u</code><i>hhhh</i>
     */
    private static String escapeForRegex(char c) {
        StringBuilder sb = new StringBuilder(6);
        sb.append("\\u");
        String hex = Integer.toHexString(c);
        for (int i = 0; i < 4 - hex.length(); i++) {
            sb.append('0');
        }
        sb.append(hex);
        return sb.toString();
    }

    private static NumberFormat FORMATTER = new DecimalFormat("#,###,###,###.#");

    private static NumberFormat COUNT_FORMATTER =
        new DecimalFormat("#,###,###,###");

    /**
     * Convert the given size into a human readable string.
     *
     * NOTE: when changing the output of this function make sure to adapt the
     * jQuery tablesorter custom parsers in web/httpheader.jspf
     *
     * @param num size to convert.
     * @return a readable string
     */
    public static String readableSize(long num) {
        float l = num;
        NumberFormat formatter = (NumberFormat) FORMATTER.clone();
        if (l < 1024) {
            return formatter.format(l) + ' '; // for none-dirs append 'B'? ...
        } else if (l < 1048576) {
            return (formatter.format(l / 1024) + " KiB");
        } else if (l < 1073741824) {
            return ("<b>" + formatter.format(l / 1048576) + " MiB</b>");
        } else {
            return ("<b>" + formatter.format(l / 1073741824) + " GiB</b>");
        }
    }

    /**
     * Convert the specified {@code count} into a human readable string.
     * @param count value to convert.
     * @return a readable string
     */
    public static String readableCount(long count) {
        NumberFormat formatter = (NumberFormat) COUNT_FORMATTER.clone();
        if (count < BOLD_COUNT_THRESHOLD) {
            return formatter.format(count);
        } else {
            return "<b>" + formatter.format(count) + "</b>";
        }
    }

    /**
     * Converts different HTML special characters into their encodings used in
     * html.
     *
     * @param s input text
     * @return encoded text for use in &lt;a title=""&gt; tag
     */
    public static String encode(String s) {
        /**
         * Make sure that the buffer is long enough to contain the whole string
         * with the expanded special characters. We use 1.5*length as a
         * heuristic.
         */
        StringBuilder sb = new StringBuilder((int) Math.max(10, s.length() * 1.5));
        try {
            encode(s, sb);
        } catch (IOException ex) {
            // IOException cannot happen when the destination is a
            // StringBuilder. Wrap in an AssertionError so that callers
            // don't have to check for an IOException that should never
            // happen.
            throw new AssertionError("StringBuilder threw IOException", ex);
        }
        return sb.toString();
    }

    /**
     * Converts different HTML special characters into their encodings used in
     * html.
     *
     * @param s input text
     * @param dest appendable destination for appending the encoded characters
     * @throws java.io.IOException I/O exception
     */
    public static void encode(String s, Appendable dest) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 127 || c == '"' || c == '<' || c == '>' || c == '&' || c == '\'') {
                // special html characters
                dest.append("&#").append("" + (int) c).append(";");
            } else if (c == ' ') {
                // non breaking space
                dest.append("&nbsp;");
            } else if (c == '\t') {
                dest.append("&nbsp;&nbsp;&nbsp;&nbsp;");
            } else if (c == '\n') {
                // <br/>
                dest.append("&lt;br/&gt;");
            } else {
                dest.append(c);
            }
        }
    }

    /**
     * Encode URL.
     *
     * @param urlStr string URL
     * @return the encoded URL
     * @throws URISyntaxException URI syntax
     * @throws MalformedURLException URL malformed
     */
    public static String encodeURL(String urlStr) throws URISyntaxException, MalformedURLException {
        URL url = new URL(urlStr);
        URI constructed = new URI(url.getProtocol(), url.getUserInfo(),
                url.getHost(), url.getPort(),
                url.getPath(), url.getQuery(), url.getRef());
        return constructed.toString();
    }

    /**
     * Write out line information wrt. to the given annotation in the format:
     * {@code Linenumber Blame Author} incl. appropriate links.
     *
     * @param num linenumber to print
     * @param out print destination
     * @param annotation annotation to use. If {@code null} only the linenumber
     * gets printed.
     * @param userPageLink see {@link RuntimeEnvironment#getUserPage()}
     * @param userPageSuffix see {@link RuntimeEnvironment#getUserPageSuffix()}
     * @param project project that is used
     * @throws IOException depends on the destination (<var>out</var>).
     */
    public static void readableLine(int num, Writer out, Annotation annotation,
            String userPageLink, String userPageSuffix, String project)
            throws IOException {
        readableLine(num, out, annotation, userPageLink, userPageSuffix, project, false);
    }

    public static void readableLine(int num, Writer out, Annotation annotation, String userPageLink,
            String userPageSuffix, String project, boolean skipNewline)
            throws IOException {
        // this method should go to JFlexXref
        String snum = String.valueOf(num);
        if (num > 1 && !skipNewline) {
            out.write("\n");
        }
        out.write(anchorClassStart);
        out.write(num % 10 == 0 ? "hl" : "l");
        out.write("\" name=\"");
        out.write(snum);
        out.write("\" href=\"#");
        out.write(snum);
        out.write(closeQuotedTag);
        out.write(snum);
        out.write(anchorEnd);

        if (annotation != null) {
            writeAnnotation(num, out, annotation, userPageLink, userPageSuffix, project);
        }
    }

    private static void writeAnnotation(int num, Writer out, Annotation annotation, String userPageLink,
                                        String userPageSuffix, String project) throws IOException {
        String r = annotation.getRevision(num);
        boolean enabled = annotation.isEnabled(num);
        out.write("<span class=\"blame\">");
        if (enabled) {
            out.write(anchorClassStart);
            out.write("r");
            out.write("\" style=\"background-color: ");
            out.write(annotation.getColors().getOrDefault(r, "inherit"));
            out.write("\" href=\"");
            out.write(URIEncode(annotation.getFilename()));
            out.write("?");
            out.write(QueryParameters.ANNOTATION_PARAM_EQ_TRUE);
            out.write("&amp;");
            out.write(QueryParameters.REVISION_PARAM_EQ);
            out.write(URIEncode(r));
            String msg = annotation.getDesc(r);
            out.write("\" title=\"");
            if (msg != null) {
                out.write(Util.encode(msg));
            }
            if (annotation.getFileVersion(r) != 0) {
                out.write("&lt;br/&gt;version: " + annotation.getFileVersion(r) + "/"
                        + annotation.getRevisions().size());
            }
            out.write(closeQuotedTag);
        }
        StringBuilder buf = new StringBuilder();
        final boolean most_recent_revision = annotation.getFileVersion(r) == annotation.getRevisions().size();
        // print an asterisk for the most recent revision
        if (most_recent_revision) {
            buf.append("<span class=\"most_recent_revision\">");
            buf.append('*');
        }
        htmlize(r, buf);
        if (most_recent_revision) {
            buf.append("</span>"); // recent revision span
        }
        out.write(buf.toString());
        buf.setLength(0);
        if (enabled) {
            RuntimeEnvironment env = RuntimeEnvironment.getInstance();

            out.write(anchorEnd);

            // Write link to search the revision in current project.
            out.write(anchorClassStart);
            out.write("search\" href=\"" + env.getUrlPrefix());
            out.write(QueryParameters.DEFS_SEARCH_PARAM_EQ);
            out.write("&amp;");
            out.write(QueryParameters.REFS_SEARCH_PARAM_EQ);
            out.write("&amp;");
            out.write(QueryParameters.PATH_SEARCH_PARAM_EQ);
            out.write(project);
            out.write("&amp;");
            out.write(QueryParameters.HIST_SEARCH_PARAM_EQ);
            out.write("&quot;");
            out.write(URIEncode(r));
            out.write("&quot;&amp;");
            out.write(QueryParameters.TYPE_SEARCH_PARAM_EQ);
            out.write("\" title=\"Search history for this revision");
            out.write(closeQuotedTag);
            out.write("S");
            out.write(anchorEnd);
        }
        String a = annotation.getAuthor(num);
        if (userPageLink == null) {
            out.write(HtmlConsts.SPAN_A);
            htmlize(a, buf);
            out.write(buf.toString());
            out.write(HtmlConsts.ZSPAN);
            buf.setLength(0);
        } else {
            out.write(anchorClassStart);
            out.write("a\" href=\"");
            out.write(userPageLink);
            out.write(URIEncode(a));
            if (userPageSuffix != null) {
                out.write(userPageSuffix);
            }
            out.write(closeQuotedTag);
            htmlize(a, buf);
            out.write(buf.toString());
            buf.setLength(0);
            out.write(anchorEnd);
        }
        out.write("</span>");
    }

    /**
     * Generate a string from the given path and date in a way that allows
     * stable lexicographic sorting (i.e. gives always the same results) as a
     * walk of the file hierarchy. Thus null character (\u0000) is used both to
     * separate directory components and to separate the path from the date.
     *
     * @param path path to mangle.
     * @param date date string to use.
     * @return the mangled path.
     */
    public static String path2uid(String path, String date) {
        return path.replace('/', '\u0000') + "\u0000" + date;
    }

    /**
     * The reverse operation for {@link #path2uid(String, String)} - re-creates
     * the unmangled path from the given uid.
     *
     * @param uid uid to unmangle.
     * @return the original path.
     */
    public static String uid2url(String uid) {
        String url = uid.replace('\u0000', PATH_SEPARATOR);
        return url.substring(0, url.lastIndexOf(PATH_SEPARATOR)); // remove date from end
    }

    /**
     * Sanitizes Windows path delimiters (if {@link PlatformUtils#isWindows()}
     * is {@code true}) as
     * {@link org.opengrok.indexer.index.Indexer#PATH_SEPARATOR} in order not
     * to conflict with the Lucene escape character and also so {@code path}
     * appears as a correctly formed URI in search results.
     */
    public static String fixPathIfWindows(String path) {
        if (path != null && PlatformUtils.isWindows()) {
            return path.replace(File.separatorChar, PATH_SEPARATOR);
        }
        return path;
    }

    /**
     * Write the 'H A D' links. This is used for search results and directory
     * listings.
     *
     * @param out writer for producing output
     * @param ctxE URI encoded prefix
     * @param entry file/directory name to write
     * @param is_dir is directory
     * @throws IOException depends on the destination (<var>out</var>).
     */
    public static void writeHAD(Writer out, String ctxE, String entry,
            boolean is_dir) throws IOException {

        String downloadPrefixE = ctxE + Prefix.DOWNLOAD_P;
        String xrefPrefixE = ctxE + Prefix.XREF_P;

        out.write("<td class=\"q\">");
        if (RuntimeEnvironment.getInstance().isHistoryEnabled()) {
            String histPrefixE = ctxE + Prefix.HIST_L;

            out.write("<a href=\"");
            out.write(histPrefixE);
            if (!entry.startsWith("/")) {
                entry = "/" + entry;
            }
            out.write(entry);
            out.write("\" title=\"History\">H</a>");
        }

        if (!is_dir) {
            out.write(" <a href=\"");
            out.write(xrefPrefixE);
            out.write(entry);
            out.write("?");
            out.write(QueryParameters.ANNOTATION_PARAM_EQ_TRUE);
            out.write("\" title=\"Annotate\">A</a> ");
            out.write("<a href=\"");
            out.write(downloadPrefixE);
            out.write(entry);
            out.write("\" title=\"Download\">D</a>");
        }

        out.write("</td>");
    }

    /**
     * Wrapper around UTF-8 URL encoding of a string.
     *
     * @param q query to be encoded. If {@code null}, an empty string will be
     * used instead.
     * @return null if fail, otherwise the encoded string
     * @see URLEncoder#encode(String, String)
     */
    public static String URIEncode(String q) {
        try {
            return q == null ? "" : URLEncoder.encode(q,
                StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // Should not happen. UTF-8 must be supported by JVMs.
            LOGGER.log(
                    Level.WARNING, "Failed to URL-encode UTF-8: ", e);
        }
        return null;
    }

    /**
     * Append to {@code dest} the UTF-8 URL-encoded representation of
     * {@code str}.
     * @param str a defined instance
     * @param dest a defined target
     * @throws IOException I/O
     */
    public static void URIEncode(String str, Appendable dest)
            throws IOException {
        String uenc = URIEncode(str);
        dest.append(uenc);
    }

    /**
     * Append '&amp;name=value" to the given buffer. If the given
     * <var>value</var>
     * is {@code null}, this method does nothing.
     *
     * @param buf where to append the query string
     * @param key the name of the parameter to add. Append as is!
     * @param value the value for the given parameter. Gets automatically UTF-8
     * URL encoded.
     * @throws NullPointerException if the given buffer is {@code null}.
     * @see #URIEncode(String)
     */
    public static void appendQuery(StringBuilder buf, String key,
            String value) {

        if (value != null) {
            buf.append("&amp;").append(key).append('=').append(URIEncode(value));
        }
    }

    /**
     * URI encode the given path.
     *
     * @param path path to encode.
     * @return the encoded path.
     * @throws NullPointerException if a parameter is {@code null}
     */
    public static String URIEncodePath(String path) {
        // Bug #19188: Ideally, we'd like to use the standard class library to
        // encode the paths. We're aware of the following two methods:
        //
        // 1) URLEncoder.encode() - this method however transforms space to +
        // instead of %20 (which is right for HTML form data, but not for
        // paths), and it also does not preserve the separator chars (/).
        //
        // 2) URI.getRawPath() - transforms space and / as expected, but gets
        // confused when the path name contains a colon character (it thinks
        // parts of the path is schema in that case)
        //
        // For now, encode manually the way we want it.
        StringBuilder sb = new StringBuilder(path.length());
        for (byte b : path.getBytes(StandardCharsets.UTF_8)) {
            // URLEncoder's javadoc says a-z, A-Z, ., -, _ and * are safe
            // characters, so we preserve those to make the encoded string
            // shorter and easier to read. We also preserve the separator
            // chars (/). All other characters are encoded (as UTF-8 byte
            // sequences).
            if ((b == '/') || (b >= 'a' && b <= 'z')
                    || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9')
                    || (b == '.') || (b == '-') || (b == '_') || (b == '*')) {
                sb.append((char) b);
            } else {
                sb.append('%');
                int u = b & 0xFF;  // Make the byte value unsigned.
                if (u <= 0x0F) {
                    // Add leading zero if required.
                    sb.append('0');
                }
                sb.append(Integer.toHexString(u).toUpperCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    /**
     * Escape a string for use as in an HTML attribute value. The returned value
     * is not enclosed in double quotes. The caller needs to add those.
     *
     * @param q string to escape.
     * @return an empty string if a parameter is {@code null}, the mangled
     * string otherwise.
     */
    public static String formQuoteEscape(String q) {
        if (q == null || q.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        char c;
        for (int i = 0; i < q.length(); i++) {
            c = q.charAt(i);
            switch (c) {
                case '"':
                    sb.append("&quot;");
                    break;
                case '&':
                    sb.append("&amp;");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Tag changes in the given <var>line1</var> and <var>line2</var>
     * for highlighting. Removed parts are tagged with CSS class {@code d}, new
     * parts are tagged with CSS class {@code a} using a {@code span} element.
     * The input parameters must not have any HTML escapes in them.
     *
     * @param line1 line of the original file
     * @param line2 line of the changed/new file
     * @return the tagged lines (field[0] ~= line1, field[1] ~= line2).
     * @throws NullPointerException if one of the given parameters is
     * {@code null}.
     */
    public static String[] diffline(StringBuilder line1, StringBuilder line2) {
        String[] ret = new String[2];
        int s = 0;
        int m = line1.length() - 1;
        int n = line2.length() - 1;
        while (s <= m && s <= n && (line1.charAt(s) == line2.charAt(s))) {
            s++;
        }

        while (s <= m && s <= n && (line1.charAt(m) == line2.charAt(n))) {
            m--;
            n--;
        }

        // deleted
        if (s <= m) {
            StringBuilder sb = new StringBuilder();
            sb.append(Util.htmlize(line1.substring(0, s)));
            sb.append(HtmlConsts.SPAN_D);
            sb.append(Util.htmlize(line1.substring(s, m + 1)));
            sb.append(HtmlConsts.ZSPAN);
            sb.append(Util.htmlize(line1.substring(m + 1, line1.length())));
            ret[0] = sb.toString();
        } else {
            ret[0] = Util.htmlize(line1.toString()); // no change
        }

        // added
        if (s <= n) {
            StringBuilder sb = new StringBuilder();
            sb.append(Util.htmlize(line2.substring(0, s)));
            sb.append(HtmlConsts.SPAN_A);
            sb.append(Util.htmlize(line2.substring(s, n + 1)));
            sb.append(HtmlConsts.ZSPAN);
            sb.append(Util.htmlize(line2.substring(n + 1, line2.length())));
            ret[1] = sb.toString();
        } else {
            ret[1] = Util.htmlize(line2.toString()); // no change
        }

        return ret;
    }

    /**
     * Dump the configuration as an HTML table.
     *
     * @param out destination for the HTML output
     * @throws IOException if an error happens while writing to {@code out}
     * @throws HistoryException if the history guru cannot be accesses
     */
    @SuppressWarnings("boxing")
    public static void dumpConfiguration(Appendable out) throws IOException,
            HistoryException {
        out.append("<table border=\"1\" width=\"100%\">");
        out.append("<tr><th>Variable</th><th>Value</th></tr>");
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        printTableRow(out, "Source root", env.getSourceRootPath());
        printTableRow(out, "Data root", env.getDataRootPath());
        printTableRow(out, "CTags", env.getCtags());
        printTableRow(out, "Bug page", env.getBugPage());
        printTableRow(out, "Bug pattern", env.getBugPattern());
        printTableRow(out, "User page", env.getUserPage());
        printTableRow(out, "User page suffix", env.getUserPageSuffix());
        printTableRow(out, "Review page", env.getReviewPage());
        printTableRow(out, "Review pattern", env.getReviewPattern());
        printTableRow(out, "Using projects", env.isProjectsEnabled());
        out.append("<tr><td>Ignored files</td><td>");
        printUnorderedList(out, env.getIgnoredNames().getItems());
        out.append("</td></tr>");
        printTableRow(out, "lucene RAM_BUFFER_SIZE_MB", env.getRamBufferSize());
        printTableRow(out, "Allow leading wildcard in search",
                env.isAllowLeadingWildcard());
        printTableRow(out, "History cache", HistoryGuru.getInstance()
                .getCacheInfo());
        printTableRow(out, "Authorization plugin directory", env.getPluginDirectory());
        printTableRow(out, "Authorization watchdog directory", env.getPluginDirectory());
        printTableRow(out, "Authorization watchdog enabled", env.isAuthorizationWatchdog());
        printTableRow(out, "Authorization stack", "<pre>" + env.getAuthorizationFramework().getStack().hierarchyToString() + "</pre>");
        out.append("</table>");
    }

    /**
     * Just read the given source and dump as is to the given destination. Does
     * nothing, if one or more of the parameters is {@code null}.
     *
     * @param out write destination
     * @param in source to read
     * @throws IOException as defined by the given reader/writer
     * @throws NullPointerException if a parameter is {@code null}.
     */
    public static void dump(Writer out, Reader in) throws IOException {
        if (in == null || out == null) {
            return;
        }
        char[] buf = new char[8192];
        int len = 0;
        while ((len = in.read(buf)) >= 0) {
            out.write(buf, 0, len);
        }
    }

    /**
     * Silently dump a file to the given destination. All {@link IOException}s
     * gets caught and logged, but not re-thrown.
     *
     * @param out dump destination
     * @param dir directory, which should contains the file.
     * @param filename the basename of the file to dump.
     * @param compressed if {@code true} the denoted file is assumed to be
     * gzipped.
     * @return {@code true} on success (everything read and written).
     * @throws NullPointerException if a parameter is {@code null}.
     */
    public static boolean dump(Writer out, File dir, String filename,
            boolean compressed) {
        return dump(out, new File(dir, filename), compressed);
    }

    /**
     * Silently dump a file to the given destination. All {@link IOException}s
     * gets caught and logged, but not re-thrown.
     *
     * @param out dump destination
     * @param file file to dump.
     * @param compressed if {@code true} the denoted file is assumed to be
     * gzipped.
     * @return {@code true} on success (everything read and written).
     * @throws NullPointerException if a parameter is {@code null}.
     */
    public static boolean dump(Writer out, File file, boolean compressed) {
        if (!file.exists()) {
            return false;
        }
        /**
         * For backward compatibility, read the OpenGrok-produced document
         * using the system default charset.
         */
        try (InputStream iss = new BufferedInputStream(new FileInputStream(file))) {
            try (Reader in = compressed ? new InputStreamReader(new GZIPInputStream(iss)) : new InputStreamReader(iss)) {
                dump(out, in);
            }
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    "An error occurred while piping file " + file + ": ", e);
        }
        return false;
    }

    /**
     * Silently dump an xref file to the given destination. All
     * {@link IOException}s get caught and logged, but not re-thrown.
     * @param out dump destination
     * @param file file to dump
     * @param compressed if {@code true} the denoted file is assumed to be
     * gzipped
     * @param contextPath an optional override of "/source/" as the context path
     * @return {@code true} on success (everything read and written)
     * @throws NullPointerException if a parameter is {@code null}.
     */
    public static boolean dumpXref(Writer out, File file, boolean compressed,
            String contextPath) {
        if (!file.exists()) {
            return false;
        }
        /**
         * For backward compatibility, read the OpenGrok-produced document
         * using the system default charset.
         */
        try (InputStream iss = new BufferedInputStream(
                new FileInputStream(file))) {
            Reader in = compressed ? new InputStreamReader(new GZIPInputStream(
                iss)) : new InputStreamReader(iss);
            dumpXref(out, in, contextPath);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "An error occured while piping file " +
                    file, e);
        }
        return false;
    }

    /**
     * Silently dump an xref file to the given destination. All
     * {@link IOException}s get caught and logged, but not re-thrown.
     * @param out dump destination
     * @param in source to read
     * @param contextPath an optional override of "/source/" as the context path
     * @throws IOException as defined by the given reader/writer
     * @throws NullPointerException if a parameter is {@code null}.
     */
    public static void dumpXref(Writer out, Reader in, String contextPath)
            throws IOException {
        if (in == null || out == null) {
            return;
        }
        XrefSourceTransformer xform = new XrefSourceTransformer(in);
        xform.setWriter(out);
        xform.setContextPath(contextPath);
        while (xform.yylex()) {
            // Nothing else to do.
        }
    }

    /**
     * Print a row in an HTML table.
     *
     * @param out destination for the HTML output
     * @param cells the values to print in the cells of the row
     * @throws IOException if an error happens while writing to {@code out}
     */
    private static void printTableRow(Appendable out, Object... cells)
            throws IOException {
        out.append("<tr>");
        StringBuilder buf = new StringBuilder(256);
        for (Object cell : cells) {
            out.append("<td>");
            String str = (cell == null) ? "null" : cell.toString();
            htmlize(str, buf);
            out.append(str);
            buf.setLength(0);
            out.append("</td>");
        }
        out.append("</tr>");
    }

    /**
     * Print an unordered list (HTML).
     *
     * @param out destination for the HTML output
     * @param items the list items
     * @throws IOException if an error happens while writing to {@code out}
     */
    private static void printUnorderedList(Appendable out,
            Collection<String> items) throws IOException {
        out.append("<ul>");
        StringBuilder buf = new StringBuilder(256);
        for (String item : items) {
            out.append("<li>");
            htmlize(item, buf);
            out.append(buf);
            buf.setLength(0);
            out.append("</li>");
        }
        out.append("</ul>");
    }

    /**
     * Create a string literal for use in JavaScript functions.
     *
     * @param str the string to be represented by the literal
     * @return a JavaScript string literal
     */
    public static String jsStringLiteral(String str) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Make a path relative by stripping off a prefix. If the path does not have
     * the given prefix, return the full path unchanged.
     *
     * @param prefix the prefix to strip off
     * @param fullPath the path from which to remove the prefix
     * @return a path relative to {@code prefix} if {@code prefix} is a parent
     * directory of {@code fullPath}; otherwise, {@code fullPath}
     */
    public static String stripPathPrefix(String prefix, String fullPath) {
        // Find the length of the prefix to strip off. The prefix should
        // represent a directory, so it could end with a slash. In case it
        // doesn't end with a slash, increase the length by one so that we
        // strip off the leading slash from the relative path.
        int prefixLength = prefix.length();
        if (!prefix.endsWith("/")) {
            prefixLength++;
        }

        // If the full path starts with the prefix, strip off the prefix.
        if (fullPath.length() > prefixLength && fullPath.startsWith(prefix)
                && fullPath.charAt(prefixLength - 1) == '/') {
            return fullPath.substring(prefixLength);
        }

        // Otherwise, return the full path.
        return fullPath;
    }

    /**
     * Creates a HTML slider for pagination. This has the same effect as
     * invoking <code>createSlider(offset, limit, size, null)</code>.
     *
     * @param offset start of the current page
     * @param limit max number of items per page
     * @param size number of total hits to paginate
     * @return string containing slider html
     */
    public static String createSlider(int offset, int limit, int size) {
        return createSlider(offset, limit, size, null);
    }

    /**
     * Creates a HTML slider for pagination.
     *
     * @param offset start of the current page
     * @param limit max number of items per page
     * @param size number of total hits to paginate
     * @param request request containing URL parameters which should be appended
     * to the page URL
     * @return string containing slider html
     */
    public static String createSlider(int offset, int limit, long size, HttpServletRequest request) {
        String slider = "";
        if (limit < size) {
            final StringBuilder buf = new StringBuilder(4096);
            int lastPage = (int) Math.ceil((double) size / limit);
            // startingResult is the number of a first result on the current page
            int startingResult = offset - limit * (offset / limit % 10 + 1);
            int myFirstPage = startingResult < 0 ? 1 : startingResult / limit + 1;
            int myLastPage = Math.min(lastPage, myFirstPage + 10 + (myFirstPage == 1 ? 0 : 1));

            // function taking the page number and appending the desired content into the final buffer
            Function<Integer, Void> generatePageLink = new Function<Integer, Void>() {
                @Override
                public Void apply(Integer page) {
                    int myOffset = Math.max(0, (page - 1) * limit);
                    if (myOffset <= offset && offset < myOffset + limit) {
                        // do not generate anchor for current page
                        buf.append("<span class=\"sel\">").append(page).append("</span>");
                    } else {
                        buf.append("<a class=\"more\" href=\"?");
                        // append request parameters
                        if (request != null && request.getQueryString() != null) {
                            String query = request.getQueryString();
                            query = query.replaceFirst(RE_Q_E_A_A_COUNT_EQ_VAL, "");
                            query = query.replaceFirst(RE_Q_E_A_A_START_EQ_VAL, "");
                            query = query.replaceFirst(RE_A_ANCHOR_Q_E_A_A, "");
                            if (!query.isEmpty()) {
                                buf.append(query);
                                buf.append("&amp;");
                            }
                        }
                        buf.append(QueryParameters.COUNT_PARAM_EQ).append(limit);
                        if (myOffset != 0) {
                            buf.append("&amp;").append(QueryParameters.START_PARAM_EQ).
                                    append(myOffset);
                        }
                        buf.append("\">");
                        // add << or >> if this link would lead to another section
                        if (page == myFirstPage && page != 1) {
                            buf.append("&lt;&lt");
                        } else if (page == myLastPage && myOffset + limit < size) {
                            buf.append("&gt;&gt;");
                        } else {
                            buf.append(page);
                        }
                        buf.append("</a>");
                    }
                    return null;
                }
            };

            // slider composition
            if (myFirstPage != 1) {
                generatePageLink.apply(1);
                buf.append("<span>...</span>");
            }
            for (int page = myFirstPage; page <= myLastPage; page++) {
                generatePageLink.apply(page);
            }
            if (myLastPage != lastPage) {
                buf.append("<span>...</span>");
                generatePageLink.apply(lastPage);
            }
            return buf.toString();
        }
        return slider;
    }

    /**
     * Check if the string is a HTTP URL.
     *
     * @param string the string to check
     * @return true if it is http URL, false otherwise
     */
    public static boolean isHttpUri(String string) {
        URL url;
        try {
            url = new URL(string);
        } catch (MalformedURLException ex) {
            return false;
        }
        return url.getProtocol().equals("http") || url.getProtocol().equals("https");
    }

    protected static final String REDACTED_USER_INFO = "redacted_by_OpenGrok";

    /**
     * If given path is a URL, return the string representation with the user-info part filtered out.
     * @param path path to object
     * @return either the original string or string representation of URL with the user-info part removed
     */
    public static String redactUrl(String path) {
        URL url;
        try {
            url = new URL(path);
        } catch (MalformedURLException e) {
            // not an URL
            return path;
        }
        if (url.getUserInfo() != null) {
            return url.toString().replace(url.getUserInfo(),
                    REDACTED_USER_INFO);
        } else {
            return path;
        }
    }

    /**
     * Build a HTML link to the given HTTP URL. If the URL is not an http URL
     * then it is returned as it was received. This has the same effect as
     * invoking <code>linkify(url, true)</code>.
     *
     * @param url the text to be linkified
     * @return the linkified string
     *
     * @see #linkify(java.lang.String, boolean)
     */
    public static String linkify(String url) {
        return linkify(url, true);
    }

    /**
     * Build a html link to the given http URL. If the URL is not an http URL
     * then it is returned as it was received.
     *
     * @param url the HTTP URL
     * @param newTab if the link should open in a new tab
     * @return HTML code containing the link &lt;a&gt;...&lt;/a&gt;
     */
    public static String linkify(String url, boolean newTab) {
        if (isHttpUri(url)) {
            try {
                Map<String, String> attrs = new TreeMap<>();
                attrs.put("href", url);
                attrs.put("title", String.format("Link to %s", Util.encode(url)));
                if (newTab) {
                    attrs.put("target", "_blank");
                }
                return buildLink(url, attrs);
            } catch (URISyntaxException | MalformedURLException ex) {
                return url;
            }
        }
        return url;
    }

    /**
     * Build an anchor with given name and a pack of attributes. Automatically
     * escapes href attributes and automatically escapes the name into HTML
     * entities.
     *
     * @param name displayed name of the anchor
     * @param attrs map of attributes for the html element
     * @return string containing the result
     *
     * @throws URISyntaxException URI syntax
     * @throws MalformedURLException malformed URL
     */
    public static String buildLink(String name, Map<String, String> attrs)
            throws URISyntaxException, MalformedURLException {
        StringBuilder buffer = new StringBuilder();
        buffer.append("<a");
        for (Entry<String, String> attr : attrs.entrySet()) {
            buffer.append(" ");
            buffer.append(attr.getKey());
            buffer.append("=\"");
            String value = attr.getValue();
            if (attr.getKey().equals("href")) {
                value = Util.encodeURL(value);
            }
            buffer.append(value);
            buffer.append("\"");
        }
        buffer.append(">");
        buffer.append(Util.htmlize(name));
        buffer.append("</a>");
        return buffer.toString();
    }

    /**
     * Build an anchor with given name and a pack of attributes. Automatically
     * escapes href attributes and automatically escapes the name into HTML
     * entities.
     *
     * @param name displayed name of the anchor
     * @param url anchor's URL
     * @return string containing the result
     *
     * @throws URISyntaxException URI syntax
     * @throws MalformedURLException bad URL
     */
    public static String buildLink(String name, String url)
            throws URISyntaxException, MalformedURLException {
        Map<String, String> attrs = new TreeMap<>();
        attrs.put("href", url);
        return buildLink(name, attrs);
    }

    /**
     * Build an anchor with given name and a pack of attributes. Automatically
     * escapes href attributes and automatically escapes the name into HTML
     * entities.
     *
     * @param name displayed name of the anchor
     * @param url anchor's URL
     * @param newTab a flag if the link should be opened in a new tab
     * @return string containing the result
     *
     * @throws URISyntaxException URI syntax
     * @throws MalformedURLException bad URL
     */
    public static String buildLink(String name, String url, boolean newTab)
            throws URISyntaxException, MalformedURLException {
        Map<String, String> attrs = new TreeMap<>();
        attrs.put("href", url);
        if (newTab) {
            attrs.put("target", "_blank");
        }
        return buildLink(name, attrs);
    }

    /**
     * Replace all occurrences of pattern in the incoming text with the link
     * named name pointing to an URL. It is possible to use the regexp pattern
     * groups in name and URL when they are specified in the pattern.
     *
     * @param text text to replace all patterns
     * @param pattern the pattern to match
     * @param name link display name
     * @param url link URL
     * @return the text with replaced links
     */
    public static String linkifyPattern(String text, Pattern pattern, String name, String url) {
        try {
            String buildLink = buildLink(name, url, true);
            return pattern.matcher(text).replaceAll(buildLink);
        } catch (URISyntaxException | MalformedURLException ex) {
            LOGGER.log(Level.WARNING, "The given URL ''{0}'' is not valid", url);
            return text;
        }
    }

    /**
     * Try to complete the given URL part into full URL with server name, port,
     * scheme, ...
     * <dl>
     * <dt>for request http://localhost:8080/source/xref/xxx and part
     * /cgi-bin/user=</dt>
     * <dd>http://localhost:8080/cgi-bin/user=</dd>
     * <dt>for request http://localhost:8080/source/xref/xxx and part
     * cgi-bin/user=</dt>
     * <dd>http://localhost:8080/source/xref/xxx/cgi-bin/user=</dd>
     * <dt>for request http://localhost:8080/source/xref/xxx and part
     * http://users.com/user=</dt>
     * <dd>http://users.com/user=</dd>
     * </dl>
     *
     * @param url the given URL part, may be already full URL
     * @param req the request containing the information about the server
     * @return the converted URL or the input parameter if there was an error
     */
    public static String completeUrl(String url, HttpServletRequest req) {
        try {
            if (!isHttpUri(url)) {
                if (url.startsWith("/")) {
                    return new URI(req.getScheme(), null, req.getServerName(), req.getServerPort(), url, null, null).toString();
                }
                StringBuffer prepUrl = req.getRequestURL();
                if (!url.isEmpty()) {
                    prepUrl.append('/').append(url);
                }
                return new URI(prepUrl.toString()).toString();
            }
            return url;
        } catch (URISyntaxException ex) {
            LOGGER.log(Level.INFO,
                    String.format("Unable to convert given URL part '%s' to complete URL", url),
                    ex);
            return url;
        }
    }

    /**
     * Parses the specified URL and returns its query params.
     * @param url URL to retrieve the query params from
     * @return query params of {@code url}
     */
    public static Map<String, List<String>> getQueryParams(final URL url) {
        if (url == null) {
            throw new IllegalArgumentException("Cannot get query params from the null url");
        }
        Map<String, List<String>> returnValue = new HashMap<>();

        if (url.getQuery() == null) {
            return returnValue;
        }

        String[] pairs = url.getQuery().split("&");

        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }

            int idx = pair.indexOf('=');
            if (idx == -1) {
                returnValue.computeIfAbsent(pair, k -> new LinkedList<>());
                continue;
            }

            String key = pair.substring(0, idx);
            String value = pair.substring(idx + 1);

            try {
                key = URLDecoder.decode(key, StandardCharsets.UTF_8.toString());
                value = URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException("Could not find UTF-8 encoding", e);
            }

            List<String> paramValues = returnValue.computeIfAbsent(key, k -> new LinkedList<>());
            paramValues.add(value);
        }
        return returnValue;
    }

}
