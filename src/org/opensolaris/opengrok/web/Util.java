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
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import org.opensolaris.opengrok.Info;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.Annotation;
import org.opensolaris.opengrok.history.HistoryException;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.util.IOUtils;

/**
 * Class for useful functions.
 */
public final class Util {
    private Util() {
        // singleton
    }

    /**
     * Return a string which represents a <code>CharSequence</code> in HTML.
     *
     * @param q
     *            a character sequence
     * @return a string representing the character sequence in HTML
     */
    public static String htmlize(CharSequence q) {
        StringBuilder sb = new StringBuilder(q.length() * 2);
        htmlize(q, sb);
        return sb.toString();
    }

    /**
     * Append a character sequence to the given destination whereby
     * special characters for HTML are escaped accordingly.
     *
     * @param q     a character sequence to esacpe
     * @param dest  where to append the character sequence to
     */
    public static void htmlize(CharSequence q, StringBuilder dest) {
        for (int i = 0; i < q.length(); i++ ) {
            htmlize(q.charAt(i), dest);
        }
    }

    /**
     * Append a character array to the given destination whereby
     * special characters for HTML are escaped accordingly.
     *
     * @param cs    characters to esacpe
     * @param length max. number of characters to append, starting from index 0.
     * @param dest  where to append the character sequence to
     */
    public static void htmlize(char[] cs, int length, StringBuilder dest) {
        int len = length;
        if (cs.length < length) {
            len = cs.length;
        }
        for (int i = 0; i < len; i++ ) {
            htmlize(cs[i], dest);
        }
    }

    /**
     * Append a character to the given destination whereby special characters
     * special for HTML are escaped accordingly.
     *
     * @param c the character to append
     * @param dest where to append the character to
     */
    private static void htmlize(char c, StringBuilder dest) {
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

    private static String versionP = htmlize(Info.getRevision());

    /**
     * used by BUI - CSS needs this parameter for proper cache refresh (per
     * changeset) in client browser jel: but useless, since the page cached
     * anyway.
     *
     * @return html escaped version (hg changeset)
     */
    public static String versionParameter() {
        return versionP;
    }

    /**
     * Convinience method for {@code breadcrumbPath(urlPrefix, path, '/')}.
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
    public static String breadcrumbPath(String urlPrefix, String path, char sep)
    {
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
     * @param compact if {@code true} the given path gets transformed into
     *  its canonical form (.i.e. all '.' and '..' and double separators
     *  removed, but not always resolves to an absolute path) before processing
     *  starts.
     * @return HTML markup fro the breadcrumb or the path itself.
     * @see #breadcrumbPath(String, String, char, String, boolean, boolean)
     * @see #getCanonicalPath(String, char)
     */
    public static String breadcrumbPath(String urlPrefix, String path,
        char sep, String urlPostfix, boolean compact)
    {
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
     * @param urlPrefix
     *            what should be prepend to the constructed URL
     * @param path
     *            the full path from which the breadcrumb path is built.
     * @param sep
     *            the character that separates the path components in
     *            <var>path</var>
     * @param urlPostfix
     *            what should be append to the constructed URL
     * @param compact
     *            if {@code true}, a canonical path gets constructed before
     *            processing.
     * @param isDir
     *            if {@code true} a "/" gets append to the last path component's
     *            link and <var>sep</var> to its name
     * @return <var>path</var> if it resolves to an empty or "/" or
     *      {@code null} path, the HTML markup for the breadcrumb path otherwise.
     */
    public static String breadcrumbPath(String urlPrefix, String path,
        char sep, String urlPostfix, boolean compact, boolean isDir)
    {
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
        StringBuilder markup =
            new StringBuilder( (pnames.length + 3 >> 1) * path.length()
                + pnames.length
                * (17 + prefix.length() + postfix.length()));
        int k = path.indexOf(pnames[0]);
        if (path.lastIndexOf(sep, k) != -1) {
            pwd.append('/');
            markup.append(sep);
        }
        for (int i = 0; i < pnames.length; i++ ) {
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
     * separators (<var>sep</var>) are replaced with a slash ('/'), all
     * double slashes are replaced by a single slash, all single dot path
     * components (".") of the formed path are removed and all double dot path
     * components (".." ) of the formed path are replaced with its parent or
     * '/' if there is no parent.
     * <p>
     * So the difference to {@link File#getCanonicalPath()} is, that this method
     * does not hit the disk (just string manipulation), resolves <var>path</var>
     * always against '/' and thus always returns an absolute path, which may
     * actually not exist, and which has a single trailing '/' if the given
     * <var>path</var> ends with the given <var>sep</var>.
     *
     * @param path  path to mangle. If not absolute or {@code null}, the
     *      current working directory is assumed to be '/'.
     * @param sep   file separator to use to crack <var>path</var> into path
     *      components
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
        for (int i=0; i < pnames.length; i++) {
            buf.append(pnames[i]).append('/');
        }
        if (path.charAt(path.length()-1) != sep) {
            // since is not a general purpose method. So we waive to handle
            // cases like:
            // || path.endsWith("/..") || path.endsWith("/.")
            buf.setLength(buf.length()-1);
        }
        return buf.toString();
    }

    /**
     * Remove all empty and {@code null} string elements from the given
     * <var>names</var> and optionally all redundant information like "." and
     * "..".
     *
     * @param names
     *            names to check
     * @param canonical
     *            if {@code true}, remove redundant elements as well.
     * @return a possible empty array of names all with a length &gt; 0.
     */
    private static String[] normalize(String[] names, boolean canonical) {
        LinkedList<String> res = new LinkedList<String>();
        if (names == null || names.length == 0) {
            return new String[0];
        }
        for (int i = 0; i < names.length; i++ ) {
            if (names[i] == null || names[i].length() == 0) {
                continue;
            }
            if (canonical) {
                if (names[i].equals("..")) {
                    if (res.size() > 0) {
                        res.removeLast();
                    }
                } else if (names[i].equals(".")) {
                    continue;
                } else {
                    res.add(names[i]);
                }
            } else {
                res.add(names[i]);
            }
        }
        return res.size() == names.length ? names : res.toArray(new String[res
            .size()]);
    }

    /**
     * Generate a regex that matches the specified character. Escape it in case
     * it is a character that has a special meaning in a regex.
     *
     * @param c
     *            the character that the regex should match
     * @return a six-character string on the form <tt>&#92;u</tt><i>hhhh</i>
     */
    private static String escapeForRegex(char c) {
        StringBuilder sb = new StringBuilder(6);
        sb.append("\\u");
        String hex = Integer.toHexString(c);
        for (int i = 0; i < 4 - hex.length(); i++ ) {
            sb.append('0');
        }
        sb.append(hex);
        return sb.toString();
    }

    private static NumberFormat FORMATTER = new DecimalFormat("#,###,###,###.#");

    /**
     * Convert the given size into a human readable string.
     * @param num   size to convert.
     * @return a readable string
     */
    public static String readableSize(long num) {
        float l = num;
        NumberFormat formatter = (NumberFormat) FORMATTER.clone();
        if (l < 1024) {
            return formatter.format(l) + ' '; // for none-dirs append 'B'? ...
        } else if (l < 1048576) {
            return (formatter.format(l / 1024) + " KiB");
        } else {
            return ("<b>" + formatter.format(l / 1048576) + " MiB</b>");
        }
    }

    /**
     * Converts different html special characters into their encodings used in
     * html. Currently used only for tooltips of annotation revision number view
     *
     * @param s
     *            input text
     * @return encoded text for use in <a title=""> tag
     */
    public static String encode(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++ ) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append('\'');
                    break; // \\\"
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
     * @param num   linenumber to print
     * @param out   print destination
     * @param annotation    annotation to use. If {@code null} only the
     *  linenumber gets printed.
     * @param userPageLink see {@link RuntimeEnvironment#getUserPage()}
     * @param userPageSuffix see {@link RuntimeEnvironment#getUserPageSuffix()}
     * @throws IOException depends on the destination (<var>out</var>).
     */
    public static void readableLine(int num, Writer out, Annotation annotation,
        String userPageLink, String userPageSuffix)
    throws IOException
    {
        // this method should go to JFlexXref
        String snum = String.valueOf(num);
        if (num > 1) {
            out.write("\n");
        }
        out.write(anchorClassStart);
        out.write( (num % 10 == 0 ? "hl" : "l"));
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
                out.write("r\" href=\"" );
                out.write(URIEncode(annotation.getFilename()));
                out.write("?a=true&amp;r=");
                out.write(URIEncode(r));
                String msg = annotation.getDesc(r);
                if (msg != null) {
                    out.write("\" title=\"");
                    out.write(msg);
                }
                out.write(closeQuotedTag);
            }
            StringBuilder buf = new StringBuilder();
            htmlize(r, buf);
            out.write(buf.toString());
            buf.setLength(0);
            if (enabled) {
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
     * walk of the file hierarchy. Thus null character (\u0000) is used both
     * to separate directory components and to separate the path from the date.
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
     * @param uid   uid to unmangle.
     * @return the original path.
     */
    public static String uid2url(String uid) {
        String url = uid.replace('\u0000', '/');
        return url.substring(0, url.lastIndexOf('/')); // remove date from end
    }

    /**
     * wrapper arround UTF-8 URL encoding of a string
     *
     * @param q     query to be encoded. If {@code null}, an empty string will
     *  be used instead.
     * @return null if fail, otherwise the encoded string
     * @see URLEncoder#encode(String, String)
     */
    public static String URIEncode(String q) {
        try {
            return q == null ? "" : URLEncoder.encode(q, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // Should not happen. UTF-8 must be supported by JVMs.
            Logger.getLogger(EftarFileReader.class.getName()).log(Level.WARNING, "Failed to URL-encode UTF-8: ", e);            
        }
        return null;
    }

    /**
     * Append '&amp;name=value" to the given buffer. If the given <var>value</var>
     * is {@code null}, this method does nothing.
     *
     * @param buf   where to append the query string
     * @param key   the name of the parameter to add. Append as is!
     * @param value the value for the given parameter. Gets automatically UTF-8
     *  URL encoded.
     * @throws NullPointerException if the given buffer is {@code null}.
     * @see #URIEncode(String)
     */
    public static void appendQuery(StringBuilder buf, String key,
        String value)
    {
        if (value != null) {
            buf.append("&amp;").append(key).append('=').append(URIEncode(value));
        }
    }

    /**
     * URI encode the given path.
     * @param path  path to encode.
     * @return the encoded path.
     * @see URI#getRawPath()
     * @throws NullPointerException if a parameter is {@code null}
     */
    public static String URIEncodePath(String path) {
        try {
            URI uri = new URI(null, null, path, null);
            return uri.getRawPath();
        } catch (URISyntaxException ex) {
            OpenGrokLogger.getLogger().log(Level.WARNING,
                "Could not encode path " + path, ex);
        }
        return "";
    }

    /**
     * Replace all quote characters (ASCI 0x22) with the corresponding html
     * entity (&amp;quot;).
     * @param q string to escape.
     * @return an empty string if a parameter is {@code null}, the mangled
     *  string otherwise.
     */
    public static String formQuoteEscape(String q) {
        if (q == null || q.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        char c;
        for (int i = 0; i < q.length(); i++ ) {
            c = q.charAt(i);
            if (c == '"') {
                sb.append("&quot;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final String SPAN_D = "<span class=\"d\">";
    private static final String SPAN_A = "<span class=\"a\">";
    private static final String SPAN_E = "</span>";
    private static final int SPAN_LEN = SPAN_D.length() + SPAN_E.length();

    /**
     * Tag changes in the given <var>line1</var> and <var>line2</var>
     * for highlighting. Removed parts are tagged with CSS class {@code d},
     * new parts are tagged with CSS class {@code a} using a {@code span}
     * element.
     *
     * @param line1 line of the original file
     * @param line2 line of the changed/new file
     * @return the tagged lines (field[0] ~= line1, field[1] ~= line2).
     * @throws NullPointerException if one of the given parameters is {@code null}.
     */
    public static String[] diffline(StringBuilder line1, StringBuilder line2) {
        int m = line1.length();
        int n = line2.length();
        if (n == 0 || m == 0) {
            return new String[] { line1.toString(), line2.toString() };
        }

        int s = 0;
        char[] csl1 = new char[m + SPAN_LEN];
        line1.getChars(0, m--, csl1, 0);
        char[] csl2 = new char[n + SPAN_LEN];
        line2.getChars(0, n--, csl2, 0);
        while (s <= m && s <= n && csl1[s] == csl2[s]) {
            s++ ;
        }
        while (s <= m && s <= n && csl1[m] == csl2[n]) {
            m-- ;
            n-- ;
        }

        String[] ret = new String[2];
        // deleted
        if (s <= m) {
            m++;
            System.arraycopy(csl1, m, csl1, m + SPAN_LEN, line1.length() - m);
            System.arraycopy(csl1, s, csl1, s + SPAN_D.length(), m - s);
            SPAN_E.getChars(0, SPAN_E.length(), csl1, m + SPAN_D.length());
            SPAN_D.getChars(0, SPAN_D.length(), csl1, s);
            ret[0] = new String(csl1);
        } else {
            ret[0] = line1.toString();
        }
        // added
        if (s <= n) {
            n++;
            System.arraycopy(csl2, n, csl2, n + SPAN_LEN, line2.length() - n);
            System.arraycopy(csl2, s, csl2, s + SPAN_A.length(), n - s);
            SPAN_E.getChars(0, SPAN_E.length(), csl2, n + SPAN_A.length());
            SPAN_A.getChars(0, SPAN_A.length(), csl2, s);
            ret[1] = new String(csl2);
        } else {
            ret[1] = line2.toString();
        }
        return ret;
    }

    /**
     * Dump the configuration as an HTML table.
     *
     * @param out
     *            destination for the HTML output
     * @throws IOException
     *             if an error happens while writing to {@code out}
     * @throws HistoryException
     *             if the history guru cannot be accesses
     */
    @SuppressWarnings("boxing")
    public static void dumpConfiguration(Appendable out) throws IOException,
        HistoryException
    {
        out.append("<table border=\"1\" width=\"100%\">");
        out.append("<tr><th>Variable</th><th>Value</th></tr>");
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        printTableRow(out, "Source root", env.getSourceRootPath());
        printTableRow(out, "Data root", env.getDataRootPath());
        printTableRow(out, "CTags", env.getCtags());
        printTableRow(out, "Bug page", env.getBugPage());
        printTableRow(out, "Bug pattern", env.getBugPattern());
        printTableRow(out, "User page", env.getUserPage());
        printTableRow(out, "Review page", env.getReviewPage());
        printTableRow(out, "Review pattern", env.getReviewPattern());
        printTableRow(out, "Using projects", env.hasProjects());
        out.append("<tr><td>Ignored files</td><td>");
        printUnorderedList(out, env.getIgnoredNames().getItems());
        out.append("</td></tr>");
        printTableRow(out, "Index word limit", env.getIndexWordLimit());
        printTableRow(out, "Allow leading wildcard in search",
            env.isAllowLeadingWildcard());
        printTableRow(out, "History cache", HistoryGuru.getInstance()
            .getCacheInfo());
        out.append("</table>");
    }

    /**
     * Just read the given source and dump as is to the given destionation.
     * Does nothing, if one or more of the parameters is {@code null}.
     * @param out   write destination
     * @param in    source to read
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
     * Silently dump a file to the given destionation. All {@link IOException}s
     * gets caught and logged, but not re-thrown.
     *
     * @param out   dump destination
     * @param dir   directory, which should contains the file.
     * @param filename  the basename of the file to dump.
     * @param compressed if {@code true} the denoted file is assumed to be
     *  gzipped.
     * @return {@code true} on success (everything read and written).
     * @throws NullPointerException if a parameter is {@code null}.
     */
    public static boolean dump(Writer out, File dir, String filename,
        boolean compressed)
    {
        return dump(out, new File(dir, filename), compressed);
    }

    /**
     * Silently dump a file to the given destionation. All {@link IOException}s
     * gets caught and logged, but not re-thrown.
     *
     * @param out   dump destination
     * @param file  file to dump.
     * @param compressed if {@code true} the denoted file is assumed to be
     *  gzipped.
     * @return {@code true} on success (everything read and written).
     * @throws NullPointerException if a parameter is {@code null}.
     */
    public static boolean dump(Writer out, File file, boolean compressed) {
        if (!file.exists()) {
            return false;
        }
        FileInputStream fis = null;
        GZIPInputStream gis = null;
        Reader in = null;
        try {
            if (compressed) {
                fis = new FileInputStream(file);
                gis = new GZIPInputStream(fis);
                in = new InputStreamReader(gis);
            } else {
                in = new FileReader(file);
            }
            dump(out, in);
            return true;
        } catch(IOException e) {
            OpenGrokLogger.getLogger().log(Level.WARNING,
                "An error occured while piping file " + file + ": ", e);
        } finally {
            IOUtils.close(in);
            IOUtils.close(gis);
            IOUtils.close(fis);
        }
        return false;
    }

    /**
     * Print a row in an HTML table.
     *
     * @param out
     *            destination for the HTML output
     * @param cells
     *            the values to print in the cells of the row
     * @throws IOException
     *             if an error happens while writing to {@code out}
     */
    private static void printTableRow(Appendable out, Object... cells)
        throws IOException
    {
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
     * @param out
     *            destination for the HTML output
     * @param items
     *            the list items
     * @throws IOException
     *             if an error happens while writing to {@code out}
     */
    private static void printUnorderedList(Appendable out,
        Collection<String> items) throws IOException
    {
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
     * @param str
     *            the string to be represented by the literal
     * @return a JavaScript string literal
     */
    public static String jsStringLiteral(String str) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < str.length(); i++ ) {
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
}
