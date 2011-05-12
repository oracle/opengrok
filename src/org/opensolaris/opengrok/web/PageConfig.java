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
 * Copyright (c) 2011 Jens Elkner.
 */
package org.opensolaris.opengrok.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.jrcs.diff.Diff;
import org.apache.commons.jrcs.diff.DifferentiationFailedException;
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.analysis.ExpandTabsReader;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.Annotation;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.index.IgnoredNames;
import org.opensolaris.opengrok.search.QueryBuilder;

/**
 * A simple container to lazy initialize common vars wrt. a single request. 
 * It MUST NOT be shared between several requests and {@link #cleanup()} should 
 * be called before the page context gets destroyed (e.g. by overwriting
 * {@code jspDestroy()} or when leaving the {@code service} method. 
 * <p>
 * Purpose is to decouple implementation details from web design, so that the 
 * JSP developer does not need to know every implementation detail and normally 
 * has to deal with this class/wrapper, only (so some people may like to call 
 * this class a bean with request scope ;-)). Furthermore it helps to keep the
 * pages (how content gets generated) consistent and to document the request 
 * parameters used.
 * <p>
 * General contract for this class (i.e. if not explicitly documented):
 * no method of this class changes neither the request nor the response.
 * 
 * @author Jens Elkner
 * @version $Revision$
 */
public class PageConfig {
    // TODO if still used, get it from the app context

    boolean check4on = true;
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
    private static final EnumSet<Genre> txtGenres =
            EnumSet.of(Genre.DATA, Genre.PLAIN, Genre.HTML);
    private TreeSet<String> requestedProjects;
    private String requestedProjectsString;
    private String[] dirFileList;
    private QueryBuilder queryBuilder;
    private File dataRoot;
    private StringBuilder headLines;

    /**
     * Add the given data to the &lt;head&gt; section of the html page to 
     * generate.
     * @param data  data to add. It is copied as is, so remember to escape 
     *  special characters ...
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
     * @return an empty string if nothing to add, the data otherwise.
     */
    public String getHeaderData() {
        return headLines == null ? "" : headLines.toString();
    }

    /**
     * Get all data required to create a diff view wrt. to this request in one go.
     * @return an instance with just enough information to render a sufficient
     *  view. If not all required parameters were given either they are 
     *  supplemented with reasonable defaults if possible, otherwise the 
     *  related field(s) are {@code null}. {@link DiffData#errorMsg} 
     *  {@code != null} indicates, that an error occured and one should not
     *  try to render a view.
     */
    public DiffData getDiffData() {
        DiffData data = new DiffData();
        data.path = getPath().substring(0, path.lastIndexOf('/'));
        data.filename = Util.htmlize(getResourceFile().getName());

        String srcRoot = getSourceRootPath();
        String context = req.getContextPath();

        String[] path = new String[2];
        data.rev = new String[2];
        data.file = new String[2][];
        data.param = new String[2];
        for (int i = 1; i <= 2; i++) {
            String[] tmp = null;
            String p = req.getParameter("r" + i);
            if (p != null) {
                tmp = p.split("@");
            }
            if (tmp != null && tmp.length == 2) {
                path[i - 1] = tmp[0];
                data.rev[i - 1] = tmp[1];
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
        if (data.genre == Genre.IMAGE) {
            return data; // no more info needed
        }
        if (data.genre == null || txtGenres.contains(data.genre)) {
            InputStream[] in = new InputStream[2];
            BufferedReader br = null;
            try {
                for (int i = 0; i < 2; i++) {
                    File f = new File(srcRoot + path[i]);
                    in[i] = HistoryGuru.getInstance().getRevision(f.getParent(), f.getName(), data.rev[i]);
                }
                if (data.genre == null) {
                    try {
                        data.genre = AnalyzerGuru.getGenre(in[0]);
                    } catch (IOException e) {
                        data.errorMsg = "Unable to determine the file type: "
                                + Util.htmlize(e.getMessage());
                    }
                    if (data.genre == Genre.IMAGE
                            || (data.genre != Genre.PLAIN
                            && data.genre != Genre.HTML)) {
                        return data;
                    }
                }
                ArrayList<String> lines = new ArrayList<String>();
                Project p = getProject();
                for (int i = 0; i < 2; i++) {
                    br = new BufferedReader(ExpandTabsReader.wrap(new InputStreamReader(in[i]), p));
                    String line;
                    while ((line = br.readLine()) != null) {
                        lines.add(line);
                    }
                    data.file[i] = lines.toArray(new String[lines.size()]);
                    lines.clear();
                    br.close();
                    in[i] = null;
                    br = null;
                }
            } catch (Exception e) {
                data.errorMsg = "Error reading revisions: "
                        + Util.htmlize(e.getMessage());
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (Exception e) {
                        /** ignore */
                    }
                }
                for (int i = 0; i < 2; i++) {
                    if (in[i] != null) {
                        try {
                            in[i].close();
                        } catch (Exception e) {
                            /** ignore */
                        }
                    }
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
                            path[i] + "@" + data.rev[i], null);
                    data.param[i] = u.getRawQuery();
                } catch (URISyntaxException e) {
                    // should not happen
                }
            }
            data.full = fullDiff();
            data.type = getDiffType();
        }
        return data;
    }

    /**
     * Get the diff display type to use wrt. the request parameter {@code format}.
     * @return {@link DiffType#SIDEBYSIDE} if the request contains no such parameter
     *  or one with an unknown value, the recognized diff type otherwise.
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
     * @return {@code true} if a request parameter {@code full} with the 
     *  literal value {@code 1} was found. 
     */
    public boolean fullDiff() {
        String val = req.getParameter("full");
        return val != null && val.equals("1");
    }

    /**
     * Check, whether the request contains minial information required to
     * produce a valid page. If this method returns an empty string, the
     * referred file or directory actually exists below the source root 
     * directory and is readable.
     * 
     * @return {@code null} if the referred src file, directory or history is not 
     *  available, an empty String if further processing is ok and a none-empty
     *  string which contains the URI encoded redirect path if the request 
     *  should be redirected.
     * @see #resourceNotAvailable()
     * @see #getOnRedirect()
     * @see #getDirectoryRedirect()
     */
    public String canProcess() {
        if (resourceNotAvailable()) {
            return getOnRedirect();
        }
        String redir = getDirectoryRedirect();
        if (redir == null && getPrefix() == Prefix.HIST_L && !hasHistory()) {
            return null;
        }
        // jel: outfactored from list.jsp - seems to be bogus
        if (isDir()) {
            if (getPrefix() == Prefix.XREF_P) {
                String[] list = getResourceFileList();
                if (list.length == 0) {
                    String rev = getRequestedRevision();
                    if (rev.length() != 0 && !hasHistory()) {
                        return null;
                    }
                }
            } else if (getPrefix() == Prefix.RAW_P) {
                return null;
            }
        }
        return redir == null ? "" : redir;
    }

    /**
     * Get a list of filenames in the requested path.
     * @return an empty array, if the resource does not exist, is not a 
     *  directory or an error occured when reading it, otherwise a list of
     *  filenames in that directory.
     * @see #getResourceFile()
     * @see #isDir()
     */
    public String[] getResourceFileList() {
        if (dirFileList == null) {
            if (isDir() && getResourcePath().length() > 1) {
                dirFileList = getResourceFile().list();
            }
            if (dirFileList == null) {
                dirFileList = new String[0];
            }
        }
        return dirFileList;
    }

    /**
     * Get the time of last modification of the related file or directory.
     * @return the last modification time of the related file or directory.
     * @see File#lastModified()
     */
    public long getLastModified() {
        return getResourceFile().lastModified();
    }

    /**
     * Get all RSS related directories from the request using its {@code also} 
     * parameter.
     * @return an empty string if the requested resource is not a directory, a
     *  space (' ') separated list of unchecked directory names otherwise.
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
        for (int i = 0; i < val.length; i++) {
            paths.append(' ').append(val[i]);
        }
        return paths.toString();
    }

    /**
     * Get the int value of the given request parameter.
     * @param name  name of the parameter to lookup.
     * @param defaultValue  value to return, if the parameter is not set, is not
     *  a number, or is &lt; 0.
     * @return the parsed int value on success, the given default value otherwise.
     */
    public int getIntParam(String name, int defaultValue) {
        String s = req.getParameter(name);
        if (s != null && s.length() != 0) {
            try {
                int x = Integer.parseInt(s, 10);
                if (x >= 0) {
                    defaultValue = x;
                }
            } catch (Exception e) {
                // fallback to default
            }
        }
        return defaultValue;
    }

    /**
     * Get the <b>start</b> index for a search result to return by looking up
     * the {@code start} request parameter.
     * @return 0 if the corresponding start parameter is not set or not a number,
     *  the number found otherwise.
     */
    public int getSearchStart() {
        return getIntParam("start", 0);
    }

    /**
     * Get the number of search results to max. return by looking up the 
     * {@code n} request parameter.
     * 
     * @return the default number of hits if the corresponding start parameter 
     *  is not set or not a number, the number found otherwise.
     */
    public int getSearchMaxItems() {
        return getIntParam("n", getEnv().getHitsPerPage());
    }

    /**
     * Get sort orders from the request parameter {@code sort} and if this list
     * would be empty from the cookie {@code OpenGrokorting}.
     * @return a possible empty list which contains the sort order values in 
     *  the same order supplied by the request parameter or cookie(s).
     */
    public List<SortOrder> getSortOrder() {
        List<SortOrder> sort = new ArrayList<SortOrder>();
        ArrayList<String> vals = getParamVals("sort");
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
     * parameters:
     * <dl>
     *      <dt>q</dt>
     *      <dd>freetext lookup rules</dd>
     *      <dt>defs</dt>
     *      <dd>definitions lookup rules</dd>
     *      <dt>path</dt>
     *      <dd>path related rules</dd>
     *      <dt>hist</dt>
     *      <dd>history related rules</dd>
     * </dl>
     * @return a query builder with all relevant fields populated.
     */
    public QueryBuilder getQueryBuilder() {
        if (queryBuilder == null) {
            queryBuilder = new QueryBuilder().setFreetext(req.getParameter("q")).setDefs(req.getParameter("defs")).setRefs(req.getParameter("refs")).setPath(req.getParameter("path")).setHist(req.getParameter("hist"));

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
     * Get the eftar reader for the opengrok data directory. If it has been
     * already opened and not closed, this instance gets returned. One should
     * not close it once used: {@link #cleanup()} takes care to close it.
     * 
     * @return {@code null} if a reader can't be established, the reader 
     *  otherwise.
     */
    public EftarFileReader getEftarReader() {
        if (eftarReader == null || eftarReader.isClosed()) {
            try {
                eftarReader = new EftarFileReader(getEnv().getDataRootPath()
                        + "/index/dtags.eftar");
            } catch (Exception e) {
                /* ignore */
            }
        }
        return eftarReader;
    }

    /**
     * Get the definition tag for the request related file or directory. 
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
                /* ignore */
            }
        }
        if (dtag == null) {
            dtag = "";
        }
        return dtag;
    }

    /**
     * Get the revision parameter {@code r} from the request.
     * @return {@code "r=<i>revision</i>"} if found, an empty string otherwise.
     */
    public String getRequestedRevision() {
        if (rev == null) {
            String tmp = req.getParameter("r");
            rev = (tmp != null && tmp.length() > 0) ? "r=" + tmp : "";
        }
        return rev;
    }

    /**
     * Check, whether the request related resource has history information.
     * @return {@code true} if history is available.
     * @see HistoryGuru#hasHistory(File)
     */
    public boolean hasHistory() {
        if (hasHistory == null) {
            hasHistory = Boolean.valueOf(HistoryGuru.getInstance().hasHistory(getResourceFile()));
        }
        return hasHistory.booleanValue();
    }

    /**
     * Check, whether annotations are available for the related resource.
     * @return {@code true} if annotions are available.
     */
    public boolean hasAnnotations() {
        if (hasAnnotation == null) {
            hasAnnotation = Boolean.valueOf(!isDir()
                    && HistoryGuru.getInstance().hasHistory(getResourceFile()));
        }
        return hasAnnotation.booleanValue();
    }

    /**
     * Check, whether the resource to show should be annotated.
     * @return {@code true} if annotation is desired and available.
     */
    public boolean annotate() {
        if (annotate == null) {
            annotate = Boolean.valueOf(hasAnnotations()
                    && Boolean.parseBoolean(req.getParameter("a")));
        }
        return annotate.booleanValue();
    }

    /**
     * Get the annotation for the reqested resource. 
     * @return {@code null} if not available or annotation was not requested, 
     *  the cached annotation otherwise.
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
            annotation = HistoryGuru.getInstance().annotate(resourceFile, rev.isEmpty() ? null : rev.substring(2));
        } catch (IOException e) {
            /* ignore */
        }
        return annotation;
    }

    /**
     * Get the name which should be show as "Crossfile"
     * @return the name of the related file or directory.
     */
    public String getCrossFilename() {
        return getResourceFile().getName();
    }

    /**
     * Get the {@code path} parameter and display value for "Search only in"
     * option. 
     * @return always an array of 3 fields, whereby field[0] contains the
     *  path value to use (starts and ends always with a '/'). Field[1] the 
     *  contains string to show in the UI. field[2] is set to 
     *  {@code disabled=""} if the current path is the "/" directory, 
     *  otherwise set to an empty string.
     */
    public String[] getSearchOnlyIn() {
        if (isDir()) {
            return path.length() == 0
                    ? new String[]{"/", "/", "disabled=\"\""}
                    : new String[]{path, path, ""};
        }
        String[] res = new String[3];
        res[0] = path.substring(0, path.lastIndexOf('/') + 1);
        res[1] = path.substring(res[0].length());
        res[2] = "";
        return res;
    }

    /**
     * Get the project {@link #getPath()} refers to.
     * @return  {@code null} if not available, the project otherwise.
     */
    public Project getProject() {
        return Project.getProject(getResourceFile());
    }

    /**
     * Same as {@link #getRequestedProjects()} but returns the project names as
     * a coma separated String.
     * @return a possible empty String but never {@code null}.
     */
    public String getRequestedProjectsAsString() {
        if (requestedProjectsString == null) {
            TreeSet<String> projects = getRequestedProjects();
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
     * Get the document hash provided by the request parameter {@code h}.
     * @return {@code null} if the request does not contain such a parameter, 
     *  its value otherwise.
     */
    public String getDocumentHash() {
        return req.getParameter("h");
    }

    /**
     * Get a reference to a set of requested projects via request parameter 
     * {@code project} or cookies or defaults.
     * <p>
     * NOTE: This method assumes, that project names do <b>not</b> contain
     *  a comma (','), since this character is used as name separator!
     * 
     * @return a possible empty set of project names aka descriptions but never 
     *  {@code null}. It is determined as 
     * follows:
     * <ol>
     *  <li>If there is no project in the runtime environment (RTE) an empty
     *      set is returned. Otherwise:</li>
     *  <li>If there is only one project in the RTE, this one gets returned (no 
     *      matter, what the request actually says). Otherwise</li> 
     *  <li>If the request parameter {@code project} contains any available
     *      project, the set with invalid projects removed gets returned.
     *      Otherwise:</li>
     *  <li>If the request has a cookie with the name {@code OpenGrokProject} 
     *      and it contains any available project, the set with invalid 
     *      projects removed gets returned. Otherwise:</li>
     *  <li>If a default project is set in the RTE, this project gets returned.
     *      Otherwise:</li>
     *  <li>an empty set</li>
     * </ol>
     */
    public TreeSet<String> getRequestedProjects() {
        if (requestedProjects == null) {
            requestedProjects =
                    getRequestedProjects("project", "OpenGrokProject");
        }
        return requestedProjects;
    }
    private static Pattern COMMA_PATTERN = Pattern.compile("'");

    private static final void splitByComma(String value, List<String> result) {
        if (value == null || value.length() == 0) {
            return;
        }
        String p[] = COMMA_PATTERN.split(value);
        for (int k = 0; k < p.length; k++) {
            if (p[k].length() != 0) {
                result.add(p[k]);
            }
        }
    }

    /**
     * Get the cookie values for the given name. Splits comma separated values
     * automatically into a list of Strings.
     * @param cookieName    name of the cookie.
     * @return a possible empty list.
     */
    public ArrayList<String> getCookieVals(String cookieName) {
        Cookie[] cookies = req.getCookies();
        ArrayList<String> res = new ArrayList<String>();
        if (cookies != null) {
            for (int i = cookies.length - 1; i >= 0; i--) {
                if (cookies[i].getName().equals(cookieName)) {
                    splitByComma(cookies[i].getValue(), res);
                }
            }
        }
        return res;
    }

    /**
     * Get the parameter values for the given name. Splits comma separated 
     * values automatically into a list of Strings.
     * @param name  name of the parameter.
     * @return a possible empty list.
     */
    private ArrayList<String> getParamVals(String paramName) {
        String vals[] = req.getParameterValues(paramName);
        ArrayList<String> res = new ArrayList<String>();
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
     * @param paramName the name of the request parameter, which possibly 
     *  contains the project list in question.
     * @param cookieName    name of the cookie which possible contains project 
     *  lists used as fallback
     * @return a possible empty set but never {@code null}.
     */
    protected TreeSet<String> getRequestedProjects(String paramName,
            String cookieName) {
        TreeSet<String> set = new TreeSet<String>();
        List<Project> projects = getEnv().getProjects();
        if (projects == null) {
            return set;
        }
        if (projects.size() == 1) {
            set.add(projects.get(0).getDescription());
            return set;
        }
        ArrayList<String> vals = getParamVals(paramName);
        for (String s : vals) {
            if (Project.getByDescription(s) != null) {
                set.add(s);
            }
        }
        if (set.isEmpty()) {
            List<String> cookies = getCookieVals(cookieName);
            for (String s : cookies) {
                if (Project.getByDescription(s) != null) {
                    set.add(s);
                }
            }
        }
        if (set.isEmpty()) {
            Project defaultProject = env.getDefaultProject();
            if (defaultProject != null) {
                set.add(defaultProject.getDescription());
            }
        }
        return set;
    }

    /**
     * Set the page title to use.
     * @param title title to set (might be {@code null}).
     */
    public void setTitle(String title) {
        pageTitle = title;
    }

    /**
     * Get the page title to use.
     * @return {@code null} if not set, the page title otherwise.
     */
    public String getTitle() {
        return pageTitle;
    }

    /**
     * Get the base path to use to refer to CSS stylesheets and related 
     * resources. Usually used to create links.
     * 
     * @return  the appropriate application directory prefixed with the 
     *  application's context path (e.g. "/source/default").
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
            sourceRootPath = File.separatorChar != '/'
                    ? getEnv().getSourceRootPath().replace(File.separatorChar, '/')
                    : getEnv().getSourceRootPath();
        }
        return sourceRootPath;
    }

    /**
     * Get the prefix for the related request.
     * @return {@link Prefix#UNKNOWN} if the servlet path matches any known
     *  prefix, the prefix otherwise.
     */
    public Prefix getPrefix() {
        if (prefix == null) {
            prefix = Prefix.get(req.getServletPath());
        }
        return prefix;
    }

    /**
     * Get the canonical path of the related resource relative to the 
     * source root directory (used file separators are all '/'). No check is
     * made, whether the obtained path is really an accessable resource on disk. 
     * 
     * @see HttpServletRequest#getPathInfo()
     * @return a possible empty String (denotes the source root directory) but 
     *  not {@code null}.
     */
    public String getPath() {
        if (path == null) {
            path = Util.getCanonicalPath(req.getPathInfo(), '/');
            if (path.equals("/")) {
                path = "";
            }
        }
        return path;
    }

    /**
     * If a requested resource is not available, append "/on/" to
     * the source root directory and try again to resolve it.
     * 
     * @return on success a none-{@code null} gets returned, which should be
     *         used to redirect the client to the propper path.
     */
    public String getOnRedirect() {
        if (check4on) {
            File newFile = new File(getSourceRootPath() + "/on/" + getPath());
            if (newFile.canRead()) {
                return req.getContextPath() + req.getServletPath() + "/on"
                        + getUriEncodedPath()
                        + (newFile.isDirectory() ? trailingSlash(path) : "");
            }
        }
        return null;
    }

    /**
     * Get the on disk file to the request related file or directory.
     * 
     * NOTE: If a repository contains hard or symbolic links, the returned
     * file may finally point to a file outside of the source root directory. 
     * 
     * @return {@code new File("/")} if the related file or directory is not
     *         available (can not be find below the source root directory), 
     *         the readable file or directory otherwise.
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
     *         directory), otherwise the path to the readable file or directory.
     * @see #getResourceFile()
     */
    public String getResourcePath() {
        if (resourcePath == null) {
            resourcePath = File.separatorChar != '/'
                    ? getResourceFile().getPath()
                    : getResourceFile().getPath().replace(File.separatorChar, '/');
        }
        return resourceFile.getPath();
    }

    /**
     * Check, whether the related request resource matches a valid file or
     * directory below the source root directory and wether it matches an
     * ignored pattern.
     * 
     * @return {@code true} if the related resource does not exists or should be
     *         ignored.
     * @see #getIgnoredNames()
     * @see #getResourcePath()
     */
    public boolean resourceNotAvailable() {
        getIgnoredNames();
        return getResourcePath().equals("/") || ignoredNames.ignore(getPath())
                || ignoredNames.ignore(resourceFile.getParentFile().getName());
    }

    /**
     * Check, whether the request related path represents a directory.
     * 
     * @return {@code true} if directory related request
     */
    public boolean isDir() {
        if (isDir == null) {
            isDir = Boolean.valueOf(getResourceFile().isDirectory());
        }
        return isDir.booleanValue();
    }

    private static String trailingSlash(String path) {
        return path.length() == 0 || path.charAt(path.length() - 1) != '/'
                ? "/"
                : "";
    }

    private final File checkFile(File dir, String name, boolean compressed) {
        File f = null;
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

    /**
     * Find the files with the given names in the {@link #getPath()} directory
     * relative to the crossfile directory of the opengrok data directory. It
     * is tried to find the compressed file first by appending the file extension
     * ".gz" to the filename. If that fails or an uncompressed version of the
     * file is younger than its compressed version, the uncompressed file gets
     * used. 
     * 
     * @param filenames filenames to lookup.
     * @return an empty array if the related directory does not exist or the
     *  given list is {@code null} or empty, otherwise an array, which may 
     *  contain {@code null} entries (when the related file could not be found)
     *  having the same order as the given list.
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
                res[i] = checkFile(dir, filenames.get(i), compressed);
            }
        }
        return res;
    }

    /**
     * Lookup the file {@link #getPath()} relative to the crossfile directory 
     * of the opengrok data directory. It is tried to find the compressed file 
     * first by appending the file extension ".gz" to the filename. If that 
     * fails or an uncompressed version of the file is younger than its 
     * compressed version, the uncompressed file gets used. 
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
     *  redirect path to use otherwise.
     */
    public String getDirectoryRedirect() {
        if (isDir()) {
            if (path.length() == 0) {
                // => /
                return null;
            }
            getPrefix();
            if (prefix != Prefix.XREF_P && prefix != Prefix.HIST_P) {
                //if it is an existing dir perhaps people wanted dir xref
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
     * Get the URI encoded canonical path to the related file or directory 
     * (the URI part between the servlet path and the start of the query string). 
     * @return an URI encoded path which might be an empty string but not 
     *  {@code null}.
     * @see #getPath()
     */
    public String getUriEncodedPath() {
        if (uriEncodedPath == null) {
            uriEncodedPath = Util.URIEncodePath(getPath());
        }
        return uriEncodedPath;
    }

    /**
     * Get opengrok's configured dataroot directory.
     * It is veriefied, that the used environment has a valid opengrok data root
     * set and that it is an accessable directory.
     * @return the opengrok data directory.
     * @throws InvalidParameterException if inaccessable or not set.
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
                        + "' refers to a none-exsting or unreadable directory!");
            }
        }
        return dataRoot;
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
        return sh;
    }

    /**
     * Get the config wrt. the given request. If there is none yet, a new config
     * gets created, attached to the request and returned.
     * <p>
     * 
     * @param request   the request to use to initialize the config parameters.
     * @return always the same none-{@code null} config for a given request.
     * @throws NullPointerException
     *             if the given parameter is {@code null}.
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
    private static final String ATTR_NAME = PageConfig.class.getCanonicalName();
    private HttpServletRequest req;

    private PageConfig(HttpServletRequest req) {
        this.req = req;
    }

    /**
     * Cleanup all allocated resources. Should always be called right before
     * leaving the _jspService / service.
     */
    public void cleanup() {
        if (req != null) {
            req.removeAttribute(ATTR_NAME);
            req = null;
        }
        env = null;
        if (eftarReader != null) {
            try {
                eftarReader.close();
            } catch (Exception e) {
                /** ignore */
            }
        }
    }
}
