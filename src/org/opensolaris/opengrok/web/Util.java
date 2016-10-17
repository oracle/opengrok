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
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright 2011 Jens Elkner.
 */
package org.opensolaris.opengrok.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.opensolaris.opengrok.Info;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.messages.Message;
import org.opensolaris.opengrok.history.Annotation;
import org.opensolaris.opengrok.history.HistoryException;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.logger.LoggerFactory;

/**
 * Class for useful functions.
 */
public final class Util {

    private static final Logger LOGGER = LoggerFactory.getLogger(Util.class);

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final String SPAN_D = "<span class=\"d\">";
    private static final String SPAN_A = "<span class=\"a\">";
    private static final String SPAN_E = "</span>";

    private Util() {
        // singleton
    }

    /**
     * Return a string which represents a <code>CharSequence</code> in HTML.
     *
     * @param q a character sequence
     * @return a string representing the character sequence in HTML
     */
    public static String htmlize(CharSequence q) {
        StringBuilder sb = new StringBuilder(q.length() * 2);
        try {
            htmlize(q, sb);
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
     * characters for HTML are escaped accordingly.
     *
     * @param q a character sequence to escape
     * @param dest where to append the character sequence to
     * @throws IOException if an error occurred when writing to {@code dest}
     */
    public static void htmlize(CharSequence q, Appendable dest)
            throws IOException {
        for (int i = 0; i < q.length(); i++) {
            htmlize(q.charAt(i), dest);
        }
    }

    /**
     * Append a character array to the given destination whereby special
     * characters for HTML are escaped accordingly.
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
            htmlize(cs[i], dest);
        }
    }

    /**
     * Append a character to the given destination whereby special characters
     * special for HTML are escaped accordingly.
     *
     * @param c the character to append
     * @param dest where to append the character to
     * @throws IOException if an error occurred when writing to {@code dest}
     */
    private static void htmlize(char c, Appendable dest) throws IOException {
        switch (c) {
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
                dest.append("<br/>");
                break;
            default:
                dest.append(c);
        }
    }

    private static final String versionP = htmlize(Info.getRevision());

    /**
     * used by BUI - CSS needs this parameter for proper cache refresh (per
     * changeset) in client browser TODO jel: but useless, since the page cached
     * anyway.
     *
     * @return html escaped version (hg changeset)
     */
    public static String versionParameter() {
        return versionP;
    }

    /**
     * Convenience method for {@code breadcrumbPath(urlPrefix, path, '/')}.
     *
     * @param urlPrefix prefix to add to each url
     * @param path path to crack
     * @return HTML markup fro the breadcrumb or the path itself.
     *
     * @see #breadcrumbPath(String, String, char)
     */
    public static String breadcrumbPath(String urlPrefix, String path) {
        return breadcrumbPath(urlPrefix, path, '/');
    }

    private static final String anchorLinkStart = "<a href=\"";
    private static final String anchorClassStart = "<a class=\"";
    private static final String anchorEnd = "</a>";
    private static final String closeQuotedTag = "\">";

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
            pwd.append('/');
            markup.append(sep);
        }
        for (int i = 0; i < pnames.length; i++) {
            pwd.append(URIEncodePath(pnames[i]));
            if (isDir || i < pnames.length - 1) {
                pwd.append('/');
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

    private final static Pattern EMAIL_PATTERN
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
                } else if (name.equals(".")) {
                } else {
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
     * @return a six-character string on the form <tt>&#92;u</tt><i>hhhh</i>
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
     * Converts different html special characters into their encodings used in
     * html. Currently used only for tooltips of annotation revision number view
     *
     * @param s input text
     * @return encoded text for use in &lt;a title=""&gt; tag
     */
    public static String encode(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\'':
                    sb.append("&#39;");
                    break;
                case '"':
                    sb.append("&#39;");
                    break; // \\\
                case '&':
                    sb.append("&amp;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case ' ':
                    sb.append("&nbsp;");
                    break;
                case '\t':
                    sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
                    break;
                case '\n':
                    sb.append("&lt;br/&gt;");
                    break;
                case '\r':
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
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
            throws IOException
    {    
        readableLine(num, out, annotation, userPageLink, userPageSuffix, project, false);
    }
    
    public static void readableLine(int num, Writer out, Annotation annotation,
            String userPageLink, String userPageSuffix, String project, boolean skipNewline)
            throws IOException
    {
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
            String r = annotation.getRevision(num);
            boolean enabled = annotation.isEnabled(num);
            out.write("<span class=\"blame\">");
            if (enabled) {
                out.write(anchorClassStart);
                out.write("r");
                if (annotation.getFileVersion(r) != 0) {
                    /*
                        version number, 1 is the most recent
                        generates css classes version_color_n
                     */
                    int versionNumber = Math.max(1,
                            annotation.getFileVersionsCount()
                            - annotation.getFileVersion(r) + 1);
                    out.write(" version_color_" + versionNumber);
                }
                out.write("\" href=\"");
                out.write(URIEncode(annotation.getFilename()));
                out.write("?a=true&amp;r=");
                out.write(URIEncode(r));
                String msg = annotation.getDesc(r);
                out.write("\" title=\"");
                if (msg != null) {
                    out.write(msg);
                }
                if (annotation.getFileVersion(r) != 0) {
                    out.write("&lt;br/&gt;version: " + annotation.getFileVersion(r) + "/"
                            + annotation.getFileVersionsCount());
                }
                out.write(closeQuotedTag);
            }
            StringBuilder buf = new StringBuilder();
            htmlize(r, buf);
            out.write(buf.toString());
            buf.setLength(0);
            if (enabled) {
                RuntimeEnvironment env = RuntimeEnvironment.getInstance();

                out.write(anchorEnd);

                // Write link to search the revision in current project.
                out.write(anchorClassStart);
                out.write("search\" href=\"" + env.getUrlPrefix());
                out.write("defs=&amp;refs=&amp;path=");
                out.write(project);
                out.write("&amp;hist=" + URIEncode(r));
                out.write("&amp;type=\" title=\"Search history for this changeset");
                out.write(closeQuotedTag);
                out.write("S");
                out.write(anchorEnd);
            }
            String a = annotation.getAuthor(num);
            if (userPageLink == null) {
                out.write("<span class=\"a\">");
                htmlize(a, buf);
                out.write(buf.toString());
                out.write("</span>");
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
        String url = uid.replace('\u0000', '/');
        return url.substring(0, url.lastIndexOf('/')); // remove date from end
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

        String histPrefixE = ctxE + Prefix.HIST_L;
        String downloadPrefixE = ctxE + Prefix.DOWNLOAD_P;
        String xrefPrefixE = ctxE + Prefix.XREF_P;

        out.write("<td class=\"q\"><a href=\"");
        out.write(histPrefixE);
        if (!entry.startsWith("/")) {
            entry = "/" + entry;
        }
        out.write(entry);
        out.write("\" title=\"History\">H</a>");

        if (!is_dir) {
            out.write(" <a href=\"");
            out.write(xrefPrefixE);
            out.write(entry);
            out.write("?a=true\" title=\"Annotate\">A</a> ");
            out.write("<a href=\"");
            out.write(downloadPrefixE);
            out.write(entry);
            out.write("\" title=\"Download\">D</a>");
        }

        out.write("</td>");
    }

    /**
     * wrapper around UTF-8 URL encoding of a string
     *
     * @param q query to be encoded. If {@code null}, an empty string will be
     * used instead.
     * @return null if fail, otherwise the encoded string
     * @see URLEncoder#encode(String, String)
     */
    public static String URIEncode(String q) {
        try {
            return q == null ? "" : URLEncoder.encode(q, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // Should not happen. UTF-8 must be supported by JVMs.
            LOGGER.log(
                    Level.WARNING, "Failed to URL-encode UTF-8: ", e);
        }
        return null;
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
        for (byte b : path.getBytes(UTF8)) {
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
                sb.append(
                        Integer.toHexString(u).toUpperCase(Locale.ENGLISH));
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
            sb.append(SPAN_D);
            sb.append(Util.htmlize(line1.substring(s, m + 1)));
            sb.append(SPAN_E);
            sb.append(Util.htmlize(line1.substring(m + 1, line1.length())));
            ret[0] = sb.toString();
        } else {
            ret[0] = line1.toString(); // no change
        }

        // added
        if (s <= n) {
            StringBuilder sb = new StringBuilder();
            sb.append(Util.htmlize(line2.substring(0, s)));
            sb.append(SPAN_A);
            sb.append(Util.htmlize(line2.substring(s, n + 1)));
            sb.append(SPAN_E);
            sb.append(Util.htmlize(line2.substring(n + 1, line2.length())));
            ret[1] = sb.toString();
        } else {
            ret[1] = line2.toString(); // no change
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
        printTableRow(out, "Using projects", env.hasProjects());
        out.append("<tr><td>Ignored files</td><td>");
        printUnorderedList(out, env.getIgnoredNames().getItems());
        out.append("</td></tr>");
        printTableRow(out, "lucene RAM_BUFFER_SIZE_MB", env.getRamBufferSize());
        printTableRow(out, "Allow leading wildcard in search",
                env.isAllowLeadingWildcard());
        printTableRow(out, "History cache", HistoryGuru.getInstance()
                .getCacheInfo());
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
        try (Reader in = compressed
                ? new InputStreamReader(new GZIPInputStream(
                        new FileInputStream(file)))
                : new FileReader(file)) {
            dump(out, in);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    "An error occured while piping file " + file + ": ", e);
        }
        return false;
    }
    
    /**
     * Print list of messages into output
     *
     * @param out output
     * @param set set of messages
     */
    public static void printMessages(Writer out, SortedSet<Message> set) {
        printMessages(out, set, false);
    }

    /**
     * Print set of messages into output
     *
     * @param out output
     * @param set set of messages
     * @param limited if the container should be limited
     */
    public static void printMessages(Writer out, SortedSet<Message> set, boolean limited) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        if (!set.isEmpty()) {
            try {
                out.write("<ul class=\"message-group");
                if (limited) {
                    out.write(" limited");
                }
                out.write("\">\n");
                for (Message m : set) {
                    out.write("<li class=\"message-group-item ");
                    out.write(Util.encode(m.getClassName()));
                    out.write("\" title=\"Expires on ");
                    out.write(Util.encode(df.format(m.getExpiration())));
                    out.write("\">");
                    out.write(Util.encode(df.format(m.getCreated())));
                    out.write(": ");
                    out.write(m.getText());
                    out.write("</li>");
                }
                out.write("</ul>");
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING,
                        "An error occured for a group of messages", ex);
            }
        }
    }

    /**
     * Print set of messages into json array
     *
     * @param set set of messages
     * @return json array containing the set of messages
     */
    @SuppressWarnings("unchecked")
    public static JSONArray messagesToJson(SortedSet<Message> set) {
        JSONArray array = new JSONArray();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        for (Message m : set) {
            JSONObject message = new JSONObject();
            message.put("class", Util.encode(m.getClassName()));
            message.put("expiration", Util.encode(df.format(m.getExpiration())));
            message.put("created", Util.encode(df.format(m.getCreated())));
            message.put("text", Util.encode(m.getText()));
            JSONArray tags = new JSONArray();
            for (String t : m.getTags()) {
                tags.add(Util.encode(t));
            }
            message.put("tags", tags);
            array.add(message);
        }
        return array;
    }

    /**
     * Print set of messages into json object for given tag.
     *
     * @param tag return messages in json format for the given tag
     * @return json object with 'tag' and 'messages' attribute or null
     */
    @SuppressWarnings("unchecked")
    public static JSONObject messagesToJsonObject(String tag) {
        SortedSet<Message> messages = RuntimeEnvironment.getInstance().getMessages(tag);
        if (messages.isEmpty()) {
            return null;
        }
        JSONObject toRet = new JSONObject();
        toRet.put("tag", tag);
        toRet.put("messages", messagesToJson(messages));
        return toRet;
    }

    /**
     * Print messages for given tags into json array
     *
     * @param array the array where the result should be stored
     * @param tags list of tags
     * @return json array of the messages (the same as the parameter)
     * @see #messagesToJsonObject(String)
     */
    @SuppressWarnings("unchecked")
    public static JSONArray messagesToJson(JSONArray array, String... tags) {
        array = array == null ? new JSONArray() : array;
        for (String tag : tags) {
            JSONObject messages = messagesToJsonObject(tag);
            if (messages == null || messages.isEmpty()) {
                continue;
            }
            array.add(messages);
        }
        return array;
    }

    /**
     * Print messages for given tags into json array
     *
     * @param tags list of tags
     * @return json array of the messages
     * @see #messagesToJson(JSONArray, String...)
     * @see #messagesToJsonObject(String)
     */
    public static JSONArray messagesToJson(String... tags) {
        return messagesToJson((JSONArray) null, tags);
    }

    /**
     * Print messages for given project into json array. These messages are
     * tagged by project description or tagged by any of the project's group
     * name.
     *
     * @param project the project
     * @param additionalTags additional list of tags
     * @return the json array
     * @see #messagesToJson(String...)
     */
    public static JSONArray messagesToJson(Project project, String... additionalTags) {
        if (project == null) {
            return new JSONArray();
        }
        List<String> tags = new ArrayList<>();
        tags.addAll(Arrays.asList(additionalTags));
        tags.add(project.getDescription());
        project.getGroups().stream().forEach((Group t) -> {
            tags.add(t.getName());
        });
        return messagesToJson((String[]) tags.toArray());
    }

    /**
     * Print messages for given project into json array. These messages are
     * tagged by project description or tagged by any of the project's group
     * name
     *
     * @param project the project
     * @return the json array
     * @see #messagesToJson(Project, String...)
     */
    public static JSONArray messagesToJson(Project project) {
        return messagesToJson(project, new String[0]);
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
}
