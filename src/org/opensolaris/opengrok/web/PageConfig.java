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
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions copyright (c) 2011 Jens Elkner.
 */
package org.opensolaris.opengrok.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.ServletRequest;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.jrcs.diff.Diff;
import org.apache.commons.jrcs.diff.DifferentiationFailedException;
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.analysis.ExpandTabsReader;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.authorization.AuthorizationFramework;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.Annotation;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.index.IgnoredNames;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.opensolaris.opengrok.util.IOUtils;

/**
 * A simple container to lazy initialize common vars wrt. a single request. It
 * MUST NOT be shared between several requests and
 * {@link #cleanup(ServletRequest)} should be called before the page context
 * gets destroyed (e.g.when leaving the {@code service} method).
 * <p>
 * Purpose is to decouple implementation details from web design, so that the
 * JSP developer does not need to know every implementation detail and normally
 * has to deal with this class/wrapper, only (so some people may like to call
 * this class a bean with request scope ;-)). Furthermore it helps to keep the
 * pages (how content gets generated) consistent and to document the request
 * parameters used.
 * <p>
 * General contract for this class (i.e. if not explicitly documented): no
 * method of this class changes neither the request nor the response.
 *
 * @author Jens Elkner
 * @version $Revision$
 */
public final class PageConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(PageConfig.class);

    public static final String OPEN_GROK_PROJECT = "OpenGrokProject";
    
    // TODO if still used, get it from the app context

    private final AuthorizationFramework authFramework;
    private RuntimeEnvironment env;
    private IgnoredNames ignoredNames;
    private String path;
    private File resourceFile;
    private String resourcePath;
    private EftarFileReader eftarReader;
    private String sourceRootPath;
    private Boolean isDir;
    private String uriEncodedPath;
    private Prefix prefix;
    private String pageTitle;
    private String dtag;
    private String rev;
    private Boolean hasAnnotation;
    private Boolean annotate;
    private Annotation annotation;
    private Boolean hasHistory;
    private static final EnumSet<Genre> txtGenres
            = EnumSet.of(Genre.DATA, Genre.PLAIN, Genre.HTML);
    private SortedSet<String> requestedProjects;
    private String requestedProjectsString;
    private List<String> dirFileList;
    private QueryBuilder queryBuilder;
    private File dataRoot;
    private StringBuilder headLines;
    private boolean lastEditedDisplayMode = true;

    private static final String ATTR_NAME = PageConfig.class.getCanonicalName();
    private HttpServletRequest req;

    /**
     * Sets current request's attribute.
     * 
     * @param attr attribute
     * @param val value
     */
    public void setRequestAttribute(String attr, Object val) {
        this.req.setAttribute(attr, val);
    }
    
    /**
     * Gets current request's attribute.
     * @param attr attribute
     * @return Object attribute value or null if attribute does not exist
     */
    public Object getRequestAttribute(String attr) {
        return this.req.getAttribute(attr);
    }    
    
    /**
     * Add the given data to the &lt;head&gt; section of the html page to
     * generate.
     *
     * @param data data to add. It is copied as is, so remember to escape
     * special characters ...
     */
    public void addHeaderData(String data) {
        if (data == null || data.length() == 0) {
            return;
        }
        if (headLines == null) {
            headLines = new StringBuilder();
        }
        headLines.append(data);
    }

    /**
     * Get addition data, which should be added as is to the &lt;head&gt;
     * section of the html page.
     *
     * @return an empty string if nothing to add, the data otherwise.
     */
    public String getHeaderData() {
        return headLines == null ? "" : headLines.toString();
    }

    /**
     * Get all data required to create a diff view wrt. to this request in one
     * go.
     *
     * @return an instance with just enough information to render a sufficient
     * view. If not all required parameters were given either they are
     * supplemented with reasonable defaults if possible, otherwise the related
     * field(s) are {@code null}. {@link DiffData#errorMsg}
     *  {@code != null} indicates, that an error occured and one should not try
     * to render a view.
     */
    public DiffData getDiffData() {
        DiffData data = new DiffData();
        data.path = getPath().substring(0, path.lastIndexOf('/'));
        data.filename = Util.htmlize(getResourceFile().getName());

        String srcRoot = getSourceRootPath();
        String context = req.getContextPath();

        String[] filepath = new String[2];
        data.rev = new String[2];
        data.file = new String[2][];
        data.param = new String[2];

        /*
         * Basically the request URI looks like this:
         * http://$site/$webapp/diff/$resourceFile?r1=$fileA@$revA&r2=$fileB@$revB
         * The code below extracts file path and revision from the URI.
         */
        for (int i = 1; i <= 2; i++) {
            String p = req.getParameter("r" + i);
            if (p != null) {
                int j = p.lastIndexOf("@");
                if (j != -1) {
                    filepath[i - 1] = p.substring(0, j);
                    data.rev[i - 1] = p.substring(j + 1);
                }
            }
        }
        if (data.rev[0] == null || data.rev[1] == null
                || data.rev[0].length() == 0 || data.rev[1].length() == 0
                || data.rev[0].equals(data.rev[1])) {
            data.errorMsg = "Please pick two revisions to compare the changed "
                    + "from the <a href=\"" + context + Prefix.HIST_L
                    + getUriEncodedPath() + "\">history</a>";
            return data;
        }
        data.genre = AnalyzerGuru.getGenre(getResourceFile().getName());

        if (data.genre == null || txtGenres.contains(data.genre)) {
            InputStream[] in = new InputStream[2];
            try {
                // Get input stream for both older and newer file.
                for (int i = 0; i < 2; i++) {
                    File f = new File(srcRoot + filepath[i]);
                    in[i] = HistoryGuru.getInstance().getRevision(f.getParent(), f.getName(), data.rev[i]);
                    if (in[i] == null) {
                        data.errorMsg = "Unable to get revision "
                                + Util.htmlize(data.rev[i]) + " for file: "
                                + Util.htmlize(getPath());
                        return data;
                    }
                }

                /*
                 * If the genre of the older revision cannot be determined,
                 * (this can happen if the file was empty), try with newer
                 * version.
                 */
                for (int i = 0; i < 2 && data.genre == null; i++) {
                    try {
                        data.genre = AnalyzerGuru.getGenre(in[i]);
                    } catch (IOException e) {
                        data.errorMsg = "Unable to determine the file type: "
                                + Util.htmlize(e.getMessage());
                    }
                }

                if (data.genre != Genre.PLAIN && data.genre != Genre.HTML) {
                    return data;
                }

                ArrayList<String> lines = new ArrayList<>();
                Project p = getProject();
                for (int i = 0; i < 2; i++) {
                    try (BufferedReader br = new BufferedReader(
                            ExpandTabsReader.wrap(new InputStreamReader(in[i]), p))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            lines.add(line);
                        }
                        data.file[i] = lines.toArray(new String[lines.size()]);
                        lines.clear();
                    }
                    in[i] = null;
                }
            } catch (Exception e) {
                data.errorMsg = "Error reading revisions: "
                        + Util.htmlize(e.getMessage());
            } finally {
                for (int i = 0; i < 2; i++) {
                    IOUtils.close(in[i]);
                }
            }
            if (data.errorMsg != null) {
                return data;
            }
            try {
                data.revision = Diff.diff(data.file[0], data.file[1]);
            } catch (DifferentiationFailedException e) {
                data.errorMsg = "Unable to get diffs: "
                        + Util.htmlize(e.getMessage());
            }
            for (int i = 0; i < 2; i++) {
                try {
                    URI u = new URI(null, null, null,
                            filepath[i] + "@" + data.rev[i], null);
                    data.param[i] = u.getRawQuery();
                } catch (URISyntaxException e) {
                    LOGGER.log(Level.WARNING, "Failed to create URI: ", e);
                }
            }
            data.full = fullDiff();
            data.type = getDiffType();
        }
        return data;
    }

    /**
     * Get the diff display type to use wrt. the request parameter
     * {@code format}.
     *
     * @return {@link DiffType#SIDEBYSIDE} if the request contains no such
     * parameter or one with an unknown value, the recognized diff type
     * otherwise.
     * @see DiffType#get(String)
     * @see DiffType#getAbbrev()
     * @see DiffType#toString()
     */
    public DiffType getDiffType() {
        DiffType d = DiffType.get(req.getParameter("format"));
        return d == null ? DiffType.SIDEBYSIDE : d;
    }

    /**
     * Check, whether a full diff should be displayed.
     *
     * @return {@code true} if a request parameter {@code full} with the literal
     * value {@code 1} was found.
     */
    public boolean fullDiff() {
        String val = req.getParameter("full");
        return val != null && val.equals("1");
    }

    /**
     * Check, whether the request contains minimal information required to
     * produce a valid page. If this method returns an empty string, the
     * referred file or directory actually exists below the source root
     * directory and is readable.
     *
     * @return {@code null} if the referred src file, directory or history is
     * not available, an empty String if further processing is ok and a
     * non-empty string which contains the URI encoded redirect path if the
     * request should be redirected.
     * @see #resourceNotAvailable()
     * @see #getDirectoryRedirect()
     */
    public String canProcess() {
        if (resourceNotAvailable()) {
            return null;
        }
        String redir = getDirectoryRedirect();
        if (redir == null && getPrefix() == Prefix.HIST_L && !hasHistory()) {
            return null;
        }
        // jel: outfactored from list.jsp - seems to be bogus
        if (isDir()) {
            if (getPrefix() == Prefix.XREF_P) {
                if (getResourceFileList().isEmpty()
                        && !getRequestedRevision().isEmpty() && !hasHistory()) {
                    return null;
                }
            } else if ((getPrefix() == Prefix.RAW_P)
                    || (getPrefix() == Prefix.DOWNLOAD_P)) {
                return null;
            }
        }
        return redir == null ? "" : redir;
    }

    /**
     * Get a list of filenames in the requested path.
     *
     * @return an empty list, if the resource does not exist, is not a directory
     * or an error occurred when reading it, otherwise a list of filenames in
     * that directory, sorted alphabetically
     * @see #getResourceFile()
     * @see #isDir()
     */
    public List<String> getResourceFileList() {
        if (dirFileList == null) {
            String[] files = null;
            if (isDir() && getResourcePath().length() > 1) {
                files = getResourceFile().list();
            }
            if (files == null) {
                dirFileList = Collections.emptyList();
            } else {
                Arrays.sort(files, String.CASE_INSENSITIVE_ORDER);
                dirFileList
                        = Collections.unmodifiableList(Arrays.asList(files));
            }
        }
        return dirFileList;
    }

    /**
     * Get the time of last modification of the related file or directory.
     *
     * @return the last modification time of the related file or directory.
     * @see File#lastModified()
     */
    public long getLastModified() {
        return getResourceFile().lastModified();
    }

    /**
     * Get all RSS related directories from the request using its {@code also}
     * parameter.
     *
     * @return an empty string if the requested resource is not a directory, a
     * space (' ') separated list of unchecked directory names otherwise.
     */
    public String getHistoryDirs() {
        if (!isDir()) {
            return "";
        }
        String[] val = req.getParameterValues("also");
        if (val == null || val.length == 0) {
            return path;
        }
        StringBuilder paths = new StringBuilder(path);
        for (String val1 : val) {
            paths.append(' ').append(val1);
        }
        return paths.toString();
    }

    /**
     * Get the int value of the given request parameter.
     *
     * @param name name of the parameter to lookup.
     * @param defaultValue value to return, if the parameter is not set, is not
     * a number, or is &lt; 0.
     * @return the parsed int value on success, the given default value
     * otherwise.
     */
    public int getIntParam(String name, int defaultValue) {
        int ret = defaultValue;
        String s = req.getParameter(name);
        if (s != null && s.length() != 0) {
            try {
                int x = Integer.parseInt(s, 10);
                if (x >= 0) {
                    ret = x;
                }
            } catch (NumberFormatException e) {
                LOGGER.log(Level.INFO, "Failed to parse integer " + s, e);
            }
        }
        return ret;
    }
    
    /**
     * Get the <b>start</b> index for a search result to return by looking up
     * the {@code start} request parameter.
     *
     * @return 0 if the corresponding start parameter is not set or not a
     * number, the number found otherwise.
     */
    public int getSearchStart() {
        return getIntParam("start", 0);
    }

    /**
     * Get the number of search results to max. return by looking up the
     * {@code n} request parameter.
     *
     * @return the default number of hits if the corresponding start parameter
     * is not set or not a number, the number found otherwise.
     */
    public int getSearchMaxItems() {
        return getIntParam("n", getEnv().getHitsPerPage());
    }

    public int getRevisionMessageCollapseThreshold() {
        return getEnv().getRevisionMessageCollapseThreshold();
    }

    /**
     * Get sort orders from the request parameter {@code sort} and if this list
     * would be empty from the cookie {@code OpenGrokorting}.
     *
     * @return a possible empty list which contains the sort order values in the
     * same order supplied by the request parameter or cookie(s).
     */
    public List<SortOrder> getSortOrder() {
        List<SortOrder> sort = new ArrayList<>();
        List<String> vals = getParamVals("sort");
        for (String s : vals) {
            SortOrder so = SortOrder.get(s);
            if (so != null) {
                sort.add(so);
            }
        }
        if (sort.isEmpty()) {
            vals = getCookieVals("OpenGrokSorting");
            for (String s : vals) {
                SortOrder so = SortOrder.get(s);
                if (so != null) {
                    sort.add(so);
                }
            }
        }
        return sort;
    }

    /**
     * Get a reference to the {@code QueryBuilder} wrt. to the current request
     * parameters: <dl> <dt>q</dt> <dd>freetext lookup rules</dd> <dt>defs</dt>
     * <dd>definitions lookup rules</dd> <dt>path</dt> <dd>path related
     * rules</dd> <dt>hist</dt> <dd>history related rules</dd> </dl>
     *
     * @return a query builder with all relevant fields populated.
     */
    public QueryBuilder getQueryBuilder() {
        if (queryBuilder == null) {
            queryBuilder = new QueryBuilder().setFreetext(req.getParameter("q"))
                    .setDefs(req.getParameter(QueryBuilder.DEFS))
                    .setRefs(req.getParameter(QueryBuilder.REFS))
                    .setPath(req.getParameter(QueryBuilder.PATH))
                    .setHist(req.getParameter(QueryBuilder.HIST))
                    .setType(req.getParameter(QueryBuilder.TYPE));

            // This is for backward compatibility with links created by OpenGrok
            // 0.8.x and earlier. We used to concatenate the entire query into a
            // single string and send it in the t parameter. If we get such a
            // link, just add it to the freetext field, and we'll get the old
            // behaviour. We can probably remove this code in the first feature
            // release after 0.9.
            String t = req.getParameter("t");
            if (t != null) {
                queryBuilder.setFreetext(t);
            }
        }
        return queryBuilder;
    }

    /**
     * Get the eftar reader for the data directory. If it has been already
     * opened and not closed, this instance gets returned. One should not close
     * it once used: {@link #cleanup(ServletRequest)} takes care to close it.
     *
     * @return {@code null} if a reader can't be established, the reader
     * otherwise.
     */
    public EftarFileReader getEftarReader() {
        if (eftarReader == null || eftarReader.isClosed()) {
            File f = getEnv().getConfiguration().getDtagsEftar();
            if (f == null) {
                eftarReader = null;
            } else {
                try {
                    eftarReader = new EftarFileReader(f);
                } catch (FileNotFoundException e) {
                    LOGGER.log(Level.FINE, "Failed to create EftarFileReader: ", e);
                }
            }
        }
        return eftarReader;
    }

    /**
     * Get the definition tag for the request related file or directory.
     *
     * @return an empty string if not found, the tag otherwise.
     */
    public String getDefineTagsIndex() {
        if (dtag != null) {
            return dtag;
        }
        getEftarReader();
        if (eftarReader != null) {
            try {
                dtag = eftarReader.get(getPath());
                // cfg.getPrefix() != Prefix.XREF_S) {
            } catch (IOException e) {
                LOGGER.log(Level.INFO, "Failed to get entry from eftar reader: ", e);
            }
        }
        if (dtag == null) {
            dtag = "";
        }
        return dtag;
    }

    /**
     * Get the revision parameter {@code r} from the request.
     *
     * @return revision if found, an empty string otherwise.
     */
    public String getRequestedRevision() {
        if (rev == null) {
            String tmp = req.getParameter("r");
            rev = (tmp != null && tmp.length() > 0) ? tmp : "";
        }
        return rev;
    }

    /**
     * Check, whether the request related resource has history information.
     *
     * @return {@code true} if history is available.
     * @see HistoryGuru#hasHistory(File)
     */
    public boolean hasHistory() {
        if (hasHistory == null) {
            hasHistory = HistoryGuru.getInstance().hasHistory(getResourceFile());
        }
        return hasHistory;
    }

    /**
     * Check, whether annotations are available for the related resource.
     *
     * @return {@code true} if annotations are available.
     */
    public boolean hasAnnotations() {
        if (hasAnnotation == null) {
            hasAnnotation = !isDir()
                    && HistoryGuru.getInstance().hasHistory(getResourceFile());
        }
        return hasAnnotation;
    }

    /**
     * Check, whether the resource to show should be annotated.
     *
     * @return {@code true} if annotation is desired and available.
     */
    public boolean annotate() {
        if (annotate == null) {
            annotate = hasAnnotations()
                    && Boolean.parseBoolean(req.getParameter("a"));
        }
        return annotate;
    }

    /**
     * Get the annotation for the requested resource.
     *
     * @return {@code null} if not available or annotation was not requested,
     * the cached annotation otherwise.
     */
    public Annotation getAnnotation() {
        if (isDir() || getResourcePath().equals("/") || !annotate()) {
            return null;
        }
        if (annotation != null) {
            return annotation;
        }
        getRequestedRevision();
        try {
            annotation = HistoryGuru.getInstance().annotate(resourceFile, rev.isEmpty() ? null : rev);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to get annotations: ", e);
            /* ignore */
        }
        return annotation;
    }

    /**
     * Get the name which should be show as "Crossfile"
     *
     * @return the name of the related file or directory.
     */
    public String getCrossFilename() {
        return getResourceFile().getName();
    }

    /**
     * Get the {@code path} parameter and display value for "Search only in"
     * option.
     *
     * @return always an array of 3 fields, whereby field[0] contains the path
     * value to use (starts and ends always with a '/'). Field[1] the contains
     * string to show in the UI. field[2] is set to {@code disabled=""} if the
     * current path is the "/" directory, otherwise set to an empty string.
     */
    public String[] getSearchOnlyIn() {
        if (isDir()) {
            return path.length() == 0
                    ? new String[]{"/", "this directory", "disabled=\"\""}
                    : new String[]{path, "this directory", ""};
        }
        String[] res = new String[3];
        res[0] = path.substring(0, path.lastIndexOf('/') + 1);
        res[1] = res[0];
        res[2] = "";
        return res;
    }

    /**
     * Get the project {@link #getPath()} refers to.
     *
     * @return {@code null} if not available, the project otherwise.
     */
    public Project getProject() {
        return Project.getProject(getResourceFile());
    }

    /**
     * Same as {@link #getRequestedProjects()} but returns the project names as
     * a coma separated String.
     *
     * @return a possible empty String but never {@code null}.
     */
    public String getRequestedProjectsAsString() {
        if (requestedProjectsString == null) {
            Set<String> projects = getRequestedProjects();
            if (projects.isEmpty()) {
                requestedProjectsString = "";
            } else {
                StringBuilder buf = new StringBuilder();
                for (String name : projects) {
                    buf.append(name).append(',');
                }
                buf.setLength(buf.length() - 1);
                requestedProjectsString = buf.toString();
            }
        }
        return requestedProjectsString;
    }

    /**
     * Get a reference to a set of requested projects via request parameter
     * {@code project} or cookies or defaults.
     * <p>
     * NOTE: This method assumes, that project names do <b>not</b> contain a
     * comma (','), since this character is used as name separator!
     *
     * @return a possible empty set of project names aka descriptions but never
     * {@code null}. It is determined as follows: <ol> <li>If there is no
     * project in the runtime environment (RTE) an empty set is returned.
     * Otherwise:</li> <li>If there is only one project in the RTE, this one
     * gets returned (no matter, what the request actually says). Otherwise</li>
     * <li>If the request parameter {@code project} contains any available
     * project, the set with invalid projects removed gets returned.
     * Otherwise:</li> <li>If the request has a cookie with the name
     * {@code OpenGrokProject} and it contains any available project, the set
     * with invalid projects removed gets returned. Otherwise:</li> <li>If a
     * default project is set in the RTE, this project gets returned.
     * Otherwise:</li> <li>an empty set</li> </ol>
     */
    public SortedSet<String> getRequestedProjects() {
        if (requestedProjects == null) {
            requestedProjects
                    = getRequestedProjects("project", OPEN_GROK_PROJECT);
        }
        return requestedProjects;
    }
    
    private static final Pattern COMMA_PATTERN = Pattern.compile(",");

    private static void splitByComma(String value, List<String> result) {
        if (value == null || value.length() == 0) {
            return;
        }
        String p[] = COMMA_PATTERN.split(value);
        for (String p1 : p) {
            if (p1.length() != 0) {
                result.add(p1);
            }
        }
    }

    /**
     * Get the cookie values for the given name. Splits comma separated values
     * automatically into a list of Strings.
     *
     * @param cookieName name of the cookie.
     * @return a possible empty list.
     */
    public List<String> getCookieVals(String cookieName) {
        Cookie[] cookies = req.getCookies();
        ArrayList<String> res = new ArrayList<>();
        if (cookies != null) {
            for (int i = cookies.length - 1; i >= 0; i--) {
                if (cookies[i].getName().equals(cookieName)) {
                    try {
                        String value = URLDecoder.decode(cookies[i].getValue(), "utf-8");
                        splitByComma(value, res);
                    } catch (UnsupportedEncodingException ex) {
                        LOGGER.log(Level.INFO, "decoding cookie failed", ex);
                    }
                }
            }
        }
        return res;
    }

    /**
     * Get the parameter values for the given name. Splits comma separated
     * values automatically into a list of Strings.
     *
     * @param name name of the parameter.
     * @return a possible empty list.
     */
    private List<String> getParamVals(String paramName) {
        String vals[] = req.getParameterValues(paramName);
        List<String> res = new ArrayList<>();
        if (vals != null) {
            for (int i = vals.length - 1; i >= 0; i--) {
                splitByComma(vals[i], res);
            }
        }
        return res;
    }

    /**
     * Same as {@link #getRequestedProjects()}, but with a variable cookieName
     * and parameter name. This way it is trivial to implement a project filter
     * ...
     *
     * @param paramName the name of the request parameter, which possibly
     * contains the project list in question.
     * @param cookieName name of the cookie which possible contains project
     * lists used as fallback
     * @return a possible empty set but never {@code null}.
     */
    protected SortedSet<String> getRequestedProjects(String paramName,
            String cookieName) {
        TreeSet<String> set = new TreeSet<>();
        List<Project> projects = getEnv().getProjects();
        if (projects == null) {
            return set;
        }
        if (projects.size() == 1 && authFramework.isAllowed(req, projects.get(0))) {
            set.add(projects.get(0).getDescription());
            return set;
        }
        List<String> vals = getParamVals(paramName);
        for (String s : vals) {
            Project x = Project.getByDescription(s);
            if (x != null && authFramework.isAllowed(req, x)) {
                set.add(s);
            }
        }
        if (set.isEmpty()) {
            List<String> cookies = getCookieVals(cookieName);
            for (String s : cookies) {
                Project x = Project.getByDescription(s);
                if (x != null && authFramework.isAllowed(req, x)) {
                    set.add(s);
                }
            }
        }
        if (set.isEmpty()) {
            Project defaultProject = env.getDefaultProject();
            if (defaultProject != null && authFramework.isAllowed(req, defaultProject)) {
                set.add(defaultProject.getDescription());
            }
        }
        return set;
    }
    
    public ProjectHelper getProjectHelper() {
        return ProjectHelper.getInstance(this);
    }

    /**
     * Set the page title to use.
     *
     * @param title title to set (might be {@code null}).
     */
    public void setTitle(String title) {
        pageTitle = title;
    }

    /**
     * Get the page title to use.
     *
     * @return {@code null} if not set, the page title otherwise.
     */
    public String getTitle() {
        return pageTitle;
    }

    /**
     * Get the base path to use to refer to CSS stylesheets and related
     * resources. Usually used to create links.
     *
     * @return the appropriate application directory prefixed with the
     * application's context path (e.g. "/source/default").
     * @see HttpServletRequest#getContextPath()
     * @see RuntimeEnvironment#getWebappLAF()
     */
    public String getCssDir() {
        return req.getContextPath() + '/' + getEnv().getWebappLAF();
    }

    /**
     * Get the current runtime environment.
     *
     * @return the runtime env.
     * @see RuntimeEnvironment#getInstance()
     * @see RuntimeEnvironment#register()
     */
    public RuntimeEnvironment getEnv() {
        if (env == null) {
            env = RuntimeEnvironment.getInstance().register();
        }
        return env;
    }

    /**
     * Get the name patterns used to determine, whether a file should be
     * ignored.
     *
     * @return the corresponding value from the current runtime config..
     */
    public IgnoredNames getIgnoredNames() {
        if (ignoredNames == null) {
            ignoredNames = getEnv().getIgnoredNames();
        }
        return ignoredNames;
    }

    /**
     * Get the canonical path to root of the source tree. File separators are
     * replaced with a '/'.
     *
     * @return The on disk source root directory.
     * @see RuntimeEnvironment#getSourceRootPath()
     */
    public String getSourceRootPath() {
        if (sourceRootPath == null) {
            String srcpath = getEnv().getSourceRootPath();
            if (srcpath != null) {
                sourceRootPath = srcpath.replace(File.separatorChar, '/');
            }
        }
        return sourceRootPath;
    }

    /**
     * Get the prefix for the related request.
     *
     * @return {@link Prefix#UNKNOWN} if the servlet path matches any known
     * prefix, the prefix otherwise.
     */
    public Prefix getPrefix() {
        if (prefix == null) {
            prefix = Prefix.get(req.getServletPath());
        }
        return prefix;
    }

    /**
     * Get the canonical path of the related resource relative to the source
     * root directory (used file separators are all '/'). No check is made,
     * whether the obtained path is really an accessible resource on disk.
     *
     * @see HttpServletRequest#getPathInfo()
     * @return a possible empty String (denotes the source root directory) but
     * not {@code null}.
     */
    public String getPath() {
        if (path == null) {
            path = Util.getCanonicalPath(req.getPathInfo(), '/');
            if ("/".equals(path)) {
                path = "";
            }
        }
        return path;
    }

    /**
     * Get the on disk file to the request related file or directory.
     *
     * NOTE: If a repository contains hard or symbolic links, the returned file
     * may finally point to a file outside of the source root directory.
     *
     * @return {@code new File("/")} if the related file or directory is not
     * available (can not be find below the source root directory), the readable
     * file or directory otherwise.
     * @see #getSourceRootPath()
     * @see #getPath()
     */
    public File getResourceFile() {
        if (resourceFile == null) {
            resourceFile = new File(getSourceRootPath(), getPath());
            if (!resourceFile.canRead()) {
                resourceFile = new File("/");
            }
        }
        return resourceFile;
    }

    /**
     * Get the canonical on disk path to the request related file or directory
     * with all file separators replaced by a '/'.
     *
     * @return "/" if the evaluated path is invalid or outside the source root
     * directory), otherwise the path to the readable file or directory.
     * @see #getResourceFile()
     */
    public String getResourcePath() {
        if (resourcePath == null) {
            resourcePath = getResourceFile().getPath().replace(File.separatorChar, '/');
        }
        return resourcePath;
    }

    /**
     * Check, whether the related request resource matches a valid file or
     * directory below the source root directory and whether it matches an
     * ignored pattern.
     *
     * @return {@code true} if the related resource does not exists or should be
     * ignored.
     * @see #getIgnoredNames()
     * @see #getResourcePath()
     */
    public boolean resourceNotAvailable() {
        getIgnoredNames();
        return getResourcePath().equals("/") || ignoredNames.ignore(getPath())
                || ignoredNames.ignore(resourceFile.getParentFile())
                || ignoredNames.ignore(resourceFile);
    }

    /**
     * Check, whether the request related path represents a directory.
     *
     * @return {@code true} if directory related request
     */
    public boolean isDir() {
        if (isDir == null) {
            isDir = getResourceFile().isDirectory();
        }
        return isDir;
    }

    private static String trailingSlash(String path) {
        return path.length() == 0 || path.charAt(path.length() - 1) != '/'
                ? "/"
                : "";
    }

    private File checkFile(File dir, String name, boolean compressed) {
        File f;
        if (compressed) {
            f = new File(dir, name + ".gz");
            if (f.exists() && f.isFile()
                    && f.lastModified() >= resourceFile.lastModified()) {
                return f;
            }
        }
        f = new File(dir, name);
        if (f.exists() && f.isFile()
                && f.lastModified() >= resourceFile.lastModified()) {
            return f;
        }
        return null;
    }

    private File checkFileResolve(File dir, String name, boolean compressed) {
        File lresourceFile = new File(getSourceRootPath() + getPath(), name);
        if (!lresourceFile.canRead()) {
            lresourceFile = new File("/");
        }
        File f;
        if (compressed) {
            f = new File(dir, name + ".gz");
            if (f.exists() && f.isFile()
                    && f.lastModified() >= lresourceFile.lastModified()) {
                return f;
            }
        }
        f = new File(dir, name);
        if (f.exists() && f.isFile()
                && f.lastModified() >= lresourceFile.lastModified()) {
            return f;
        }
        return null;
    }

    /**
     * Find the files with the given names in the {@link #getPath()} directory
     * relative to the crossfile directory of the opengrok data directory. It is
     * tried to find the compressed file first by appending the file extension
     * ".gz" to the filename. If that fails or an uncompressed version of the
     * file is younger than its compressed version, the uncompressed file gets
     * used.
     *
     * @param filenames filenames to lookup.
     * @return an empty array if the related directory does not exist or the
     * given list is {@code null} or empty, otherwise an array, which may
     * contain {@code null} entries (when the related file could not be found)
     * having the same order as the given list.
     */
    public File[] findDataFiles(List<String> filenames) {
        if (filenames == null || filenames.isEmpty()) {
            return new File[0];
        }
        File[] res = new File[filenames.size()];
        File dir = new File(getEnv().getDataRootPath() + Prefix.XREF_P + path);
        if (dir.exists() && dir.isDirectory()) {
            getResourceFile();
            boolean compressed = getEnv().isCompressXref();
            for (int i = res.length - 1; i >= 0; i--) {
                res[i] = checkFileResolve(dir, filenames.get(i), compressed);
            }
        }
        return res;
    }

    /**
     * Lookup the file {@link #getPath()} relative to the crossfile directory of
     * the opengrok data directory. It is tried to find the compressed file
     * first by appending the file extension ".gz" to the filename. If that
     * fails or an uncompressed version of the file is younger than its
     * compressed version, the uncompressed file gets used.
     *
     * @return {@code null} if not found, the file otherwise.
     */
    public File findDataFile() {
        return checkFile(new File(getEnv().getDataRootPath() + Prefix.XREF_P),
                path, env.isCompressXref());
    }

    /**
     * Get the path the request should be redirected (if any).
     *
     * @return {@code null} if there is no reason to redirect, the URI encoded
     * redirect path to use otherwise.
     */
    public String getDirectoryRedirect() {
        if (isDir()) {
            if (path.length() == 0) {
                // => /
                return null;
            }
            getPrefix();
            if (prefix != Prefix.XREF_P && prefix != Prefix.HIST_L
                    && prefix != Prefix.RSS_P) {
                // if it is an existing dir perhaps people wanted dir xref
                return req.getContextPath() + Prefix.XREF_P
                        + getUriEncodedPath() + trailingSlash(path);
            }
            String ts = trailingSlash(path);
            if (ts.length() != 0) {
                return req.getContextPath() + prefix + getUriEncodedPath() + ts;
            }
        }
        return null;
    }

    /**
     * Get the URI encoded canonical path to the related file or directory (the
     * URI part between the servlet path and the start of the query string).
     *
     * @return an URI encoded path which might be an empty string but not
     * {@code null}.
     * @see #getPath()
     */
    public String getUriEncodedPath() {
        if (uriEncodedPath == null) {
            uriEncodedPath = Util.URIEncodePath(getPath());
        }
        return uriEncodedPath;
    }

    /**
     * Get opengrok's configured dataroot directory. It is verified, that the
     * used environment has a valid opengrok data root set and that it is an
     * accessible directory.
     *
     * @return the opengrok data directory.
     * @throws InvalidParameterException if inaccessible or not set.
     */
    public File getDataRoot() {
        if (dataRoot == null) {
            String tmp = getEnv().getDataRootPath();
            if (tmp == null || tmp.length() == 0) {
                throw new InvalidParameterException("dataRoot parameter is not "
                        + "set in configuration.xml!");
            }
            dataRoot = new File(tmp);
            if (!(dataRoot.isDirectory() && dataRoot.canRead())) {
                throw new InvalidParameterException("The configured dataRoot '"
                        + tmp
                        + "' refers to a none-existing or unreadable directory!");
            }
        }
        return dataRoot;
    }
    
    public boolean isLastEditedDisplayMode() {
        return lastEditedDisplayMode;
    }

    public void setLastEditedDisplayMode(boolean lastEditedDisplayMode) {
        this.lastEditedDisplayMode = lastEditedDisplayMode;
    }

    /**
     * Prepare a search helper with all required information, ready to execute
     * the query implied by the related request parameters and cookies.
     * <p>
     * NOTE: One should check the {@link SearchHelper#errorMsg} as well as
     * {@link SearchHelper#redirect} and take the appropriate action before
     * executing the prepared query or continue processing.
     * <p>
     * This method stops populating fields as soon as an error occurs.
     *
     * @return a search helper.
     */
    public SearchHelper prepareSearch() {
        SearchHelper sh = new SearchHelper();
        sh.dataRoot = getDataRoot(); // throws Exception if none-existent
        List<SortOrder> sortOrders = getSortOrder();
        sh.order = sortOrders.isEmpty() ? SortOrder.RELEVANCY : sortOrders.get(0);
        if (getRequestedProjects().isEmpty() && getEnv().hasProjects()) {
            sh.errorMsg = "You must select a project!";
            return sh;
        }
        sh.builder = getQueryBuilder();
        if (sh.builder.getSize() == 0) {
            // Entry page show the map
            sh.redirect = req.getContextPath() + '/';
            return sh;
        }
        sh.start = getSearchStart();
        sh.maxItems = getSearchMaxItems();
        sh.contextPath = req.getContextPath();
        // jel: this should be IMHO a config param since not only core dependend
        sh.parallel = Runtime.getRuntime().availableProcessors() > 1;
        sh.isCrossRefSearch = getPrefix() == Prefix.SEARCH_R;
        sh.compressed = env.isCompressXref();
        sh.desc = getEftarReader();
        sh.sourceRoot = new File(getSourceRootPath());
        sh.lastEditedDisplayMode = isLastEditedDisplayMode();
        return sh;
    }

    /**
     * Get the config wrt. the given request. If there is none yet, a new config
     * gets created, attached to the request and returned.
     * <p>
     *
     * @param request the request to use to initialize the config parameters.
     * @return always the same none-{@code null} config for a given request.
     * @throws NullPointerException if the given parameter is {@code null}.
     */
    public static PageConfig get(HttpServletRequest request) {
        Object cfg = request.getAttribute(ATTR_NAME);
        if (cfg != null) {
            return (PageConfig) cfg;
        }
        PageConfig pcfg = new PageConfig(request);
        request.setAttribute(ATTR_NAME, pcfg);
        return pcfg;
    }

    private PageConfig(HttpServletRequest req) {
        this.req = req;
        this.authFramework = AuthorizationFramework.getInstance();
    }

    /**
     * Cleanup all allocated resources (if any) from the instance attached to
     * the given request.
     *
     * @param sr request to check, cleanup. Ignored if {@code null}.
     * @see PageConfig#get(HttpServletRequest)
     */
    public static void cleanup(ServletRequest sr) {
        if (sr == null) {
            return;
        }
        PageConfig cfg = (PageConfig) sr.getAttribute(ATTR_NAME);
        if (cfg == null) {
            return;
        }
        sr.removeAttribute(ATTR_NAME);
        cfg.env = null;
        cfg.req = null;
        if (cfg.eftarReader != null) {
            cfg.eftarReader.close();
        }
        ProjectHelper.cleanup();
    }
    
    /**
     * Checks if current request is allowed to access project.
     * @param t project
     * @return true if yes
     */
    public boolean isAllowed(Project t) {
        return this.authFramework.isAllowed(this.req, t);
    }
    
    /**
     * Checks if current request is allowed to access group.
     * @param g group
     * @return true if yes
     */
    public boolean isAllowed(Group g) {
        return this.authFramework.isAllowed(this.req, g);
    }
    

}
