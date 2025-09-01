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
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2011, Jens Elkner.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2023, Gino Augustine <gino.augustine@oracle.com>.
 */
package org.opengrok.web;

import static org.opengrok.indexer.history.LatestRevisionUtil.getLatestRevision;
import static org.opengrok.indexer.index.Indexer.PATH_SEPARATOR;
import static org.opengrok.indexer.index.Indexer.PATH_SEPARATOR_STRING;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.opengrok.indexer.Info;
import org.opengrok.indexer.Metrics;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.AnalyzerGuru;
import org.opengrok.indexer.analysis.ExpandTabsReader;
import org.opengrok.indexer.analysis.NullableNumLinesLOC;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.authorization.AuthorizationFramework;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.IgnoredNames;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.Annotation;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.search.DirectoryExtraReader;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.util.LineBreaker;
import org.opengrok.indexer.util.TandemPath;
import org.opengrok.indexer.web.EftarFileReader;
import org.opengrok.indexer.web.Laundromat;
import org.opengrok.indexer.web.Prefix;
import org.opengrok.indexer.web.QueryParameters;
import org.opengrok.indexer.web.SearchHelper;
import org.opengrok.indexer.web.SortOrder;
import org.opengrok.indexer.web.Util;
import org.opengrok.indexer.web.messages.MessagesContainer.AcceptedMessage;
import org.suigeneris.jrcs.diff.Diff;
import org.suigeneris.jrcs.diff.DifferentiationFailedException;

/**
 * A simple container to lazy initialize common vars wrt. a single request. It
 * MUST NOT be shared between several requests and
 * {@link #cleanup(ServletRequest)} should be called before the page context
 * gets destroyed (e.g.when leaving the {@code service} method).
 * <p>
 * Purpose is to decouple implementation details from web design, so that the
 * JSP developer does not need to know every implementation detail and normally
 * has to deal with this class/wrapper, only (so some people may like to call
 * this class a bean with request scope ;-)). Furthermore, it helps to keep the
 * pages (how content gets generated) consistent and to document the request
 * parameters used.
 * <p>
 * General contract for this class (i.e. if not explicitly documented):
 * no method of this class changes neither the request nor the response.
 *
 * @author Jens Elkner
 */
public class PageConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(PageConfig.class);

    // cookie name
    public static final String OPEN_GROK_PROJECT = "OpenGrokProject";

    public static final String DUMMY_REVISION = "unknown";

    private static final String HISTORY_JSP_ATTR_NAME = "history.jsp-hist";

    // query parameters
    static final String PROJECT_PARAM_NAME = "project";
    static final String GROUP_PARAM_NAME = "group";
    private static final String DEBUG_PARAM_NAME = "debug";

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
    private String fragmentIdentifier; // Also settable via match offset translation
    private Boolean hasAnnotation;
    private Boolean annotate;
    private Annotation annotation;
    private Boolean hasHistory;
    private static final EnumSet<AbstractAnalyzer.Genre> txtGenres
            = EnumSet.of(AbstractAnalyzer.Genre.DATA, AbstractAnalyzer.Genre.PLAIN, AbstractAnalyzer.Genre.HTML);
    private SortedSet<String> requestedProjects;
    private String requestedProjectsString;
    private List<String> dirFileList;
    private QueryBuilder queryBuilder;
    private File dataRoot;
    private StringBuilder headLines;
    /**
     * Page java scripts.
     */
    private final Scripts scripts = new Scripts();

    static final String ATTR_NAME = PageConfig.class.getCanonicalName();
    private HttpServletRequest req;

    private final ExecutorService executor;

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
     * @return name of the JSP attribute to store the {@link org.opengrok.indexer.history.History} object.
     */
    public String getHistoryAttrName() {
        return HISTORY_JSP_ATTR_NAME;
    }

    /**
     * Removes an attribute from the current request.
     * @param string the attribute
     */
    public void removeAttribute(String string) {
        req.removeAttribute(string);
    }

    /**
     * Add the given data to the &lt;head&gt; section of the html page to generate.
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
     * Get addition data, which should be added as is to the &lt;head&gt; section of the HTML page.
     *
     * @return an empty string if nothing to add, the data otherwise.
     */
    public String getHeaderData() {
        return headLines == null ? "" : headLines.toString();
    }

    /**
     * Extract file path and revision strings from the URI. Basically the request URI looks like this:
     * {@code http://$site/$webapp/diff/$resourceFile?r1=$fileA@$revA&r2=$fileB@$revB}
     * The code extracts file path and revision from the URI.
     * @param data DiffData object (output parameter)
     * @param context context path
     * @param filepath file path array (output parameter)
     * @return true if the extraction was successful, false otherwise
     * (in which case {@link DiffData#errorMsg} will be set)
     */
    private boolean getFileRevision(DiffData data, String context, String[] filepath) {
        for (int i = 1; i <= 2; i++) {
            String p = req.getParameter(QueryParameters.REVISION_PARAM + i);
            if (p != null) {
                int j = p.lastIndexOf("@");
                if (j != -1) {
                    filepath[i - 1] = Laundromat.launderUriPath(p.substring(0, j));
                    data.rev[i - 1] = Laundromat.launderRevision(p.substring(j + 1));
                }
            }
        }

        if (data.rev[0] == null || data.rev[1] == null
                || data.rev[0].isEmpty() || data.rev[1].isEmpty()
                || data.rev[0].equals(data.rev[1])) {
            data.errorMsg = "Please pick two revisions to compare the changes "
                    + "from the <a href=\"" + context + Prefix.HIST_L
                    + getUriEncodedPath() + "\">history</a>";
            return false;
        }

        return true;
    }

    /**
     * Get all data required to create a diff view w.r.t. to this request in one go.
     *
     * @return an instance with just enough information to render a sufficient view.
     * If not all required parameters were given either they are supplemented
     * with reasonable defaults if possible, otherwise the related field(s) are {@code null}.
     * {@link DiffData#errorMsg}
     * {@code != null} indicates that an error occurred and one should not try to render a view.
     */
    public DiffData getDiffData() {
        DiffData data = new DiffData(getPath().substring(0, getPath().lastIndexOf(PATH_SEPARATOR)),
                getResourceFile().getName());

        String context = req.getContextPath();
        String[] filepath = new String[2];

        if (!getFileRevision(data, context, filepath)) {
            return data;
        }

        data.genre = AnalyzerGuru.getGenre(getResourceFile().getName());
        Optional.of(data)
                .filter(this::isTextGenre)
                .ifPresent(textData -> generatePlainTextDiffData(textData, filepath));
       return data;
    }

    private boolean isTextGenre(DiffData data) {
        return data.genre == null || txtGenres.contains(data.genre);
    }

    private void generatePlainTextDiffData(DiffData data, String[] filepath) {
        String srcRoot = getSourceRootPath();
        InputStream[] in = new InputStream[2];
        try {
            // Get input stream for both older and newer file.
            Future<?>[] future = new Future<?>[2];
            for (int i = 0; i < 2; i++) {
                File f = new File(srcRoot + filepath[i]);
                final String revision = data.rev[i];
                future[i] = executor.submit(() -> HistoryGuru.getInstance().
                        getRevision(f.getParent(), f.getName(), revision));
            }

            for (int i = 0; i < 2; i++) {
                // The Executor used by given repository will enforce the timeout.
                in[i] = (InputStream) future[i].get();
                if (in[i] == null) {
                    data.errorMsg = "Unable to get revision "
                            + Util.htmlize(data.rev[i]) + " for file: "
                            + Util.htmlize(getPath());
                    return;
                }
            }

            /*
             * If the genre of the older revision cannot be determined,
             * (this can happen if the file was empty), try with newer
             * version.
             */
            populateGenreIfEmpty(data, in);

            if (data.genre != AbstractAnalyzer.Genre.PLAIN && data.genre != AbstractAnalyzer.Genre.HTML) {
                return;
            }

            Project p = getProject();
            for (int i = 0; i < 2; i++) {
                // All files under source root are read with UTF-8 as a default.
                try (BufferedReader br = new BufferedReader(
                        ExpandTabsReader.wrap(IOUtils.createBOMStrippedReader(
                                in[i], StandardCharsets.UTF_8.name()), p))) {
                    data.file[i] = br.lines().toArray(String[]::new);
                }
            }
        } catch (Exception e) {
            data.errorMsg = "Error reading revisions: "
                    + Util.htmlize(e.getMessage());
        } finally {
            Arrays.stream(in)
                    .forEach(IOUtils::close);
        }
        if (Objects.isNull(data.errorMsg)) {
            populateRevisionData(data);
            populateRevisionURLDetails(data, filepath);
            data.full = fullDiff();
            data.type = getDiffType();
        }
    }

    private void populateGenreIfEmpty(DiffData data, InputStream[] inArray) {
        for (int i = 0; i < 2 && data.genre == null; i++) {
            try {
                data.genre = AnalyzerGuru.getGenre(inArray[i]);
                if (data.genre == null) {
                    data.errorMsg = "Unable to determine the file type";
                }
            } catch (IOException e) {
                data.errorMsg = "Unable to determine the file type: "
                        + Util.htmlize(e.getMessage());
            }
        }
    }

    private void populateRevisionData(DiffData data) {
        try {
            data.revision = Diff.diff(data.file[0], data.file[1]);
        } catch (DifferentiationFailedException e) {
            data.errorMsg = "Unable to get diffs: " + e.getMessage();
        }
    }

    private void populateRevisionURLDetails(DiffData data, String[] filePath) {
        IntStream.range(0, 2)
                .forEach(i -> {
                    try {
                        URI u = new URI(null, null, null,
                                filePath[i] + "@" + data.rev[i], null);
                        data.param[i] = u.getRawQuery();
                    } catch (URISyntaxException e) {
                        LOGGER.log(Level.WARNING, "Failed to create URI: ", Laundromat.launderLog(e.toString()));
                    }
                });
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
        DiffType d = DiffType.get(req.getParameter(QueryParameters.FORMAT_PARAM));
        return d == null ? DiffType.SIDEBYSIDE : d;
    }

    /**
     * Check, whether a full diff should be displayed.
     *
     * @return {@code true} if a request parameter {@code full} with the literal
     * value {@code 1} was found.
     */
    public boolean fullDiff() {
        String val = req.getParameter(QueryParameters.DIFF_LEVEL_PARAM);
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
     * <p>
     * For the root directory (/xref/), authorization check is performed for each
     * project in case that projects are used.
     * </p>
     *
     * @see #getResourceFile()
     * @see #isDir()
     */
    public List<String> getResourceFileList() {
        if (dirFileList == null) {
            File[] files = null;
            if (isDir() && getResourcePath().length() > 1) {
                files = getResourceFile().listFiles();
            }

            if (files == null) {
                dirFileList = Collections.emptyList();
            } else {
                List<String> listOfFiles = getSortedFiles(files);

                if (env.hasProjects() && getPath().isEmpty()) {
                    /*
                     * This denotes the source root directory, we need to filter
                     * projects which aren't allowed by the authorization
                     * because otherwise the main xref page expose the names of
                     * all projects in OpenGrok even those which aren't allowed
                     * for the particular user. E.g. remove all which aren't
                     * among the filtered set of projects.
                     *
                     * The authorization check is made in
                     * {@link ProjectHelper#getAllProjects()} as a part of all
                     * projects filtering.
                     */
                    List<String> modifiableListOfFiles = new ArrayList<>(listOfFiles);
                    modifiableListOfFiles.removeIf(t -> getProjectHelper().getAllProjects().stream()
                            .noneMatch(p -> p.getName().equalsIgnoreCase(t)));
                    dirFileList = Collections.unmodifiableList(modifiableListOfFiles);
                    return dirFileList;
                }

                dirFileList = Collections.unmodifiableList(listOfFiles);
            }
        }
        return dirFileList;
    }

    private static Comparator<File> getFileComparator() {
        if (RuntimeEnvironment.getInstance().getListDirsFirst()) {
            return (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) {
                    return -1;
                } else if (!f1.isDirectory() && f2.isDirectory()) {
                    return 1;
                } else {
                    return f1.getName().compareTo(f2.getName());
                }
            };
        } else {
            return Comparator.comparing(File::getName);
        }
    }

    @VisibleForTesting
    public static List<String> getSortedFiles(File[] files) {
        return Arrays.stream(files).sorted(getFileComparator()).map(File::getName).collect(Collectors.toList());
    }

    /**
     * @return the last modification time of the related file or directory.
     * @see File#lastModified()
     */
    public long getLastModified() {
        return getResourceFile().lastModified();
    }

    /**
     * Get all RSS related directories from the request using its {@code also} parameter.
     *
     * @return an empty string if the requested resource is not a directory, a
     * space (<code>' '</code>) separated list of unchecked directory names otherwise.
     */
    public String getHistoryDirs() {
        if (!isDir()) {
            return "";
        }
        String[] val = req.getParameterValues("also");
        if (val == null || val.length == 0) {
            return getPath();
        }
        StringBuilder paths = new StringBuilder(getPath());
        for (String val1 : val) {
            paths.append(' ').append(val1);
        }
        return paths.toString();
    }

    /**
     * Get the integer value of the given request parameter.
     *
     * @param name name of the parameter to lookup.
     * @param defaultValue value to return, if the parameter is not set, is not a number, or is &lt; 0.
     * @return the parsed integer value on success, the given default value otherwise.
     */
    public int getIntParam(String name, int defaultValue) {
        int ret = defaultValue;
        String s = req.getParameter(name);
        if (s != null && !s.isEmpty()) {
            try {
                int x = Integer.parseInt(s, 10);
                if (x >= 0) {
                    ret = x;
                }
            } catch (NumberFormatException e) {
                LOGGER.log(Level.INFO, String.format("Failed to parse %s integer %s", name, s), e);
            }
        }
        return ret;
    }

    /**
     * Get the <b>start</b> index for a search result or history listing to return by looking up
     * the {@code start} request parameter.
     *
     * @return 0 if the corresponding start parameter is not set or not a number, the number found otherwise.
     */
    public int getStartIndex() {
        return getIntParam(QueryParameters.START_PARAM, 0);
    }

    /**
     * Get the number of search results or history entries to display by looking up the
     * {@code n} request parameter.
     *
     * @return the default number of items if the corresponding start parameter
     * is not set or not a number, the number found otherwise.
     */
    public int getMaxItems() {
        return getIntParam(QueryParameters.COUNT_PARAM, getEnv().getHitsPerPage());
    }

    public int getRevisionMessageCollapseThreshold() {
        return getEnv().getRevisionMessageCollapseThreshold();
    }

    public int getCurrentIndexedCollapseThreshold() {
        return getEnv().getCurrentIndexedCollapseThreshold();
    }

    public int getGroupsCollapseThreshold() {
        return getEnv().getGroupsCollapseThreshold();
    }

    /**
     * Get sort orders from the request parameter {@code sort} and if this list
     * would be empty from the cookie {@code OpenGrokSorting}.
     *
     * @return a possible empty list which contains the sort order values in the
     * same order supplied by the request parameter or cookie(s).
     */
    public List<SortOrder> getSortOrder() {
        List<SortOrder> sort = new ArrayList<>();
        List<String> vals = getParameterValues(QueryParameters.SORT_PARAM);
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
            queryBuilder = new QueryBuilder().
                    setFreetext(Laundromat.launderQuery(req.getParameter(QueryBuilder.FULL)))
                    .setDefs(Laundromat.launderQuery(req.getParameter(QueryBuilder.DEFS)))
                    .setRefs(Laundromat.launderQuery(req.getParameter(QueryBuilder.REFS)))
                    .setPath(Laundromat.launderQuery(req.getParameter(QueryBuilder.PATH)))
                    .setHist(Laundromat.launderQuery(req.getParameter(QueryBuilder.HIST)))
                    .setType(Laundromat.launderQuery(req.getParameter(QueryBuilder.TYPE)));
        }

        return queryBuilder;
    }

    /**
     * Get the <i>Eftar</i> reader for the data directory. If it has been already
     * opened and not closed, this instance gets returned. One should not close
     * it once used: {@link #cleanup(ServletRequest)} takes care to close it.
     *
     * @return {@code null} if a reader can't be established, the reader
     * otherwise.
     */
    public EftarFileReader getEftarReader() {
        if (eftarReader == null || eftarReader.isClosed()) {
            File f = getEnv().getDtagsEftar();
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
    @NotNull
    public String getDefineTagsIndex() {
        if (dtag != null) {
            return dtag;
        }
        getEftarReader();
        if (eftarReader != null) {
            try {
                dtag = eftarReader.get(getPath());
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
     * Anything besides alphanumeric and {@code :} is removed via {@link Laundromat#launderInput(String)}.
     *
     * @return revision if found, an empty string otherwise.
     */
    public String getRequestedRevision() {
        if (rev == null) {
            String tmp = Laundromat.launderRevision(req.getParameter(QueryParameters.REVISION_PARAM));
            rev = Optional.ofNullable(tmp)
                    .filter(s -> !s.isEmpty())
                    .orElse("");

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
     * @return whether annotations are available for the related resource.
     */
    public boolean hasAnnotations() {
        if (hasAnnotation == null) {
            hasAnnotation = HistoryGuru.getInstance().hasAnnotation(getResourceFile());
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
            annotate = Boolean.parseBoolean(req.getParameter(QueryParameters.ANNOTATION_PARAM)) && hasAnnotations();
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
     * Get the {@code path} parameter and display value for "Search only in" option.
     *
     * @return always an array of 3 fields, whereby field[0] contains the path
     * value to use (starts and ends always with a {@link org.opengrok.indexer.index.Indexer#PATH_SEPARATOR}).
     * Field[1] the contains string to show in the UI. field[2] is set to {@code disabled=""} if the
     * current path is the "/" directory, otherwise set to an empty string.
     */
    public String[] getSearchOnlyIn() {
        if (isDir()) {
            return getPath().isEmpty()
                    ? new String[]{"/", "this directory", "disabled=\"\""}
                    : new String[]{getPath(), "this directory", ""};
        }
        String[] res = new String[3];
        res[0] = getPath().substring(0, getPath().lastIndexOf(PATH_SEPARATOR) + 1);
        res[1] = res[0];
        res[2] = "";
        return res;
    }

    /**
     * Get the project {@link #getPath()} refers to.
     *
     * @return {@code null} if not available, the project otherwise.
     */
    @Nullable
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
            requestedProjectsString = String.join(",", getRequestedProjects());
        }
        return requestedProjectsString;
    }

    /**
     * Get a reference to a set of requested projects via request parameter
     * {@code project} or cookies or defaults.
     * <p>
     * NOTE: This method assumes, that project names do <b>not</b> contain a
     * comma (','), since this character is used as name separator!
     * <p>
     * It is determined as follows:
     * <ol>
     * <li>If there is no project in the configuration an empty set is returned. Otherwise:</li>
     * <li>If there is only one project in the configuration,
     * this one gets returned (no matter, what the request actually says). Otherwise</li>
     * <li>If the request parameter {@code ALL_PROJECT_SEARCH} contains a true value,
     * all projects are added to searching. Otherwise:</li>
     * <li>If the request parameter {@code PROJECT_PARAM_NAME} contains any available project,
     * the set with invalid projects removed gets returned. Otherwise:</li>
     * <li>If the request parameter {@code GROUP_PARAM_NAME} contains any available group,
     * then all projects from that group will be added to the result set. Otherwise:</li>
     * <li>If the request has a cookie with the name {@code OPEN_GROK_PROJECT}
     * and it contains any available project,
     * the set with invalid projects removed gets returned. Otherwise:</li>
     * <li>If a default project is set in the configuration,
     * this project gets returned. Otherwise:</li>
     * <li>an empty set</li>
     * </ol>
     *
     * @return a possible empty set of project names but never {@code null}.
     * @see QueryParameters#ALL_PROJECT_SEARCH
     * @see #PROJECT_PARAM_NAME
     * @see #GROUP_PARAM_NAME
     * @see #OPEN_GROK_PROJECT
     */
    public SortedSet<String> getRequestedProjects() {
        requestedProjects = Optional.ofNullable(requestedProjects)
                .orElseGet( () -> getRequestedProjects(
                        PROJECT_PARAM_NAME, GROUP_PARAM_NAME, OPEN_GROK_PROJECT)
                );
        return requestedProjects;
    }

    private static final Pattern COMMA_PATTERN = Pattern.compile(",");

    private static void splitByComma(String value, List<String> result) {
        if (value == null || value.isEmpty()) {
            return;
        }
        String[] p = COMMA_PATTERN.split(value);
        for (String p1 : p) {
            if (!p1.isEmpty()) {
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
                    String value = URLDecoder.decode(cookies[i].getValue(), StandardCharsets.UTF_8);
                    splitByComma(value, res);
                }
            }
        }
        return res;
    }

    /**
     * Get the parameter values for the given name. Splits comma separated
     * values automatically into a list of Strings.
     *
     * @param paramName name of the parameter.
     * @return a possible empty list.
     */
    private List<String> getParameterValues(String paramName) {
        String[] parameterValues = req.getParameterValues(paramName);
        List<String> res = new ArrayList<>();
        if (parameterValues != null) {
            for (int i = parameterValues.length - 1; i >= 0; i--) {
                splitByComma(parameterValues[i], res);
            }
        }
        return res;
    }

    /**
     * Same as {@link #getRequestedProjects()}, but with a variable cookieName
     * and parameter name.
     *
     * @param projectParamName the name of the request parameter corresponding to a project name.
     * @param groupParamName   the name of the request parameter corresponding to a group name
     * @param cookieName       name of the cookie which possible contains project
     *                         names used as fallback
     * @return set of project names. Possibly empty set but never {@code null}.
     */
    private SortedSet<String> getRequestedProjects(
            String projectParamName,
            String groupParamName,
            String cookieName
    ) {

        TreeSet<String> projectNames = new TreeSet<>();
        if (Boolean.parseBoolean(req.getParameter(QueryParameters.ALL_PROJECT_SEARCH))) {
            return getProjectHelper()
                    .getAllProjects()
                    .stream()
                    .map(Project::getName)
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        // Use a project determined directly from the URL
        boolean isProjectIndexed = Optional.ofNullable(getProject())
                .map(Project::isIndexed)
                .orElse(false);
        if (isProjectIndexed) {
            projectNames.add(getProject().getName());
            return projectNames;
        }

        List<Project> projects = getEnv().getProjectList();
        if (projects.size() == 1) {
            Project p = projects.get(0);
            if (isIndexedAndAllowed(p)) {
                projectNames.add(p.getName());
            }
            return projectNames;
        }

        // Add all projects which match the project parameter name values/
        getParameterValues(projectParamName)
                .stream()
                .map(Project::getByName)
                .filter(Objects::nonNull)
                .filter(this::isIndexedAndAllowed)
                .map(Project::getName)
                .forEach(projectNames::add);
        var groupNames = getParameterValues(groupParamName)
                .stream()
                .map(Group::getByName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        groupNames.stream()
                .map(group -> getProjectHelper().getAllGrouped(group))
                .flatMap(Collection::stream)
                .filter(Project::isIndexed)
                .map(Project::getName)
                .forEach(projectNames::add);

        // Add projects based on cookie.
        if (projectNames.isEmpty() && getIntParam(QueryParameters.NUM_SELECTED_PARAM, -1) != 0) {
            getCookieVals(cookieName)
                    .stream()
                    .map(Project::getByName)
                    .filter(Objects::nonNull)
                    .filter(this::isIndexedAndAllowed)
                    .map(Project::getName)
                    .forEach(projectNames::add);
        }

        // Add default projects.
        if (projectNames.isEmpty()) {
            Optional.ofNullable(env.getDefaultProjects())
                    .stream().flatMap(Collection::stream)
                    .filter(this::isIndexedAndAllowed)
                    .map(Project::getName)
                    .forEach(projectNames::add);
        }

        return projectNames;
    }

    private boolean isIndexedAndAllowed(Project project) {
        return project.isIndexed() && this.isAllowed(project);
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
        return req.getContextPath() + PATH_SEPARATOR + getEnv().getWebappLAF();
    }

    /**
     * Get the current runtime environment.
     *
     * @return the runtime env.
     * @see RuntimeEnvironment#getInstance()
     */
    public RuntimeEnvironment getEnv() {
        if (env == null) {
            env = RuntimeEnvironment.getInstance();
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
     * replaced with a {@link org.opengrok.indexer.index.Indexer#PATH_SEPARATOR}.
     *
     * @return The on disk source root directory.
     * @see RuntimeEnvironment#getSourceRootPath()
     */
    public String getSourceRootPath() {
        if (sourceRootPath == null) {
            String srcPathEnv = getEnv().getSourceRootPath();
            if (srcPathEnv != null) {
                sourceRootPath = srcPathEnv.replace(File.separatorChar, PATH_SEPARATOR);
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
     * root directory (used file separators are all {@link org.opengrok.indexer.index.Indexer#PATH_SEPARATOR}).
     * No check is made, whether the obtained path is really an accessible resource on disk.
     * <p>
     * Also, care needs to be taken when embedding the result in a page. Consider using {@link Util#htmlize(String)}
     * or {@link Util#uriEncodePath(String)}.
     * </p>
     * @return a possible empty String (denotes the source root directory) but not {@code null}.
     * @see HttpServletRequest#getPathInfo()
     */
    public String getPath() {
        if (path == null) {
            path = Util.getCanonicalPath(Laundromat.launderInput(req.getPathInfo()), PATH_SEPARATOR);
            if (PATH_SEPARATOR_STRING.equals(path)) {
                path = "";
            }
        }
        return path;
    }

    private void setPath(String path) {
        this.path = Util.getCanonicalPath(Laundromat.launderInput(path), PATH_SEPARATOR);
    }

    /**
     * @return true if file/directory corresponding to the request path exists however is unreadable, false otherwise
     */
    public boolean isUnreadable() {
        File file = new File(getSourceRootPath(), getPath());
        return file.exists() && !file.canRead();
    }

    /**
     * Get the file object for the given path.
     * <p>
     * NOTE: If a repository contains hard or symbolic links, the returned file
     * may finally point to a file outside the source root directory.
     * </p>
     * @param path the path to the file relatively to the source root
     * @return null if the related file or directory is not
     * available (can not be found below the source root directory), the readable file or directory otherwise.
     * @see #getSourceRootPath()
     */
    public File getResourceFile(String path) {
        File file;
        file = new File(getSourceRootPath(), path);
        if (!file.canRead()) {
            return null;
        }
        return file;
    }

    /**
     * Get the on disk file to the request related file or directory.
     * <p>
     * NOTE: If a repository contains hard or symbolic links, the returned file
     * may finally point to a file outside the source root directory.
     * </p>
     * @return {@code new File({@link org.opengrok.indexer.index.Indexer#PATH_SEPARATOR_STRING })}
     * if the related file or directory is not available (can not be find below the source root directory),
     * the readable file or directory otherwise.
     * @see #getSourceRootPath()
     * @see #getPath()
     */
    public File getResourceFile() {
        if (resourceFile == null) {
            resourceFile = getResourceFile(getPath());
            if (resourceFile == null) {
                resourceFile = new File(PATH_SEPARATOR_STRING);
            }
        }
        return resourceFile;
    }

    /**
     * Get the canonical on disk path to the request related file or directory
     * with all file separators replaced by a {@link org.opengrok.indexer.index.Indexer#PATH_SEPARATOR}.
     *
     * @return {@link org.opengrok.indexer.index.Indexer#PATH_SEPARATOR_STRING} if the evaluated path is invalid
     * or outside the source root directory, otherwise the path to the readable file or directory.
     * @see #getResourceFile()
     */
    public String getResourcePath() {
        if (resourcePath == null) {
            resourcePath = Util.fixPathIfWindows(getResourceFile().getPath());
        }
        return resourcePath;
    }

    /**
     * Check, whether the related request resource matches a valid file or
     * directory below the source root directory and whether it matches an
     * ignored pattern.
     *
     * @return {@code true} if the related resource does not exist or should be ignored.
     * @see #getIgnoredNames()
     * @see #getResourcePath()
     */
    public boolean resourceNotAvailable() {
        getIgnoredNames();
        return getResourcePath().equals(PATH_SEPARATOR_STRING) || ignoredNames.ignore(getPath())
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
        return path.length() == 0 || path.charAt(path.length() - 1) != PATH_SEPARATOR
                ? PATH_SEPARATOR_STRING
                : "";
    }

    @Nullable
    private File checkFileInner(File file, File dir, String name) {

        MeterRegistry meterRegistry = Metrics.getRegistry();

        File xrefFile = new File(dir, name);
        if (xrefFile.exists() && xrefFile.isFile()) {
            if (xrefFile.lastModified() >= file.lastModified()) {
                if (meterRegistry != null) {
                    Counter.builder("xref.file.get").
                            description("xref file get hits").
                            tag("what", "hits").
                            register(meterRegistry).
                            increment();
                }
                return xrefFile;
            } else {
                LOGGER.log(Level.WARNING, "file ''{0}'' is newer than ''{1}''", new Object[]{file, xrefFile});
            }
        }

        if (meterRegistry != null) {
            Counter.builder("xref.file.get").
                    description("xref file get misses").
                    tag("what", "miss").
                    register(meterRegistry).
                    increment();
        }

        return null;
    }

    @Nullable
    private File checkFile(File file, File dir, String name, boolean compressed) {
        File f;
        if (compressed) {
            f = checkFileInner(file, dir, TandemPath.join(name, ".gz"));
            if (f != null) {
                return f;
            }
        }

        return checkFileInner(file, dir, name);
    }

    @Nullable
    private File checkFileResolve(File dir, String name, boolean compressed) {
        File lresourceFile = new File(getSourceRootPath() + getPath(), name);
        if (!lresourceFile.canRead()) {
            lresourceFile = new File(PATH_SEPARATOR_STRING);
        }

        return checkFile(lresourceFile, dir, name, compressed);
    }

    /**
     * Find the files with the given names in the {@link #getPath()} directory
     * relative to the cross-file directory of the opengrok data directory. It will
     * try to find the compressed file first by appending the file extension
     * {@code ".gz"} to the filename. If that fails or an uncompressed version of the
     * file is younger than its compressed version, the uncompressed file gets used.
     *
     * @param filenames filenames to lookup.
     * @return an empty array if the related directory does not exist or the
     * given list is {@code null} or empty, otherwise an array, which may
     * contain {@code null} entries (when the related file could not be found)
     * having the same order as the given list.
     */
    public File[] findDataFiles(@Nullable List<String> filenames) {
        if (filenames == null || filenames.isEmpty()) {
            return new File[0];
        }
        File[] res = new File[filenames.size()];
        File dir = new File(getEnv().getDataRootPath() + Prefix.XREF_P + getPath());
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
     * Lookup the file {@link #getPath()} relative to the cross-file directory of
     * the opengrok data directory. It is tried to find the compressed file
     * first by appending the file extension ".gz" to the filename. If that
     * fails or an uncompressed version of the file is younger than its
     * compressed version, the uncompressed file gets used.
     *
     * @return {@code null} if not found, the file otherwise.
     */
    public File findDataFile() {
        return checkFile(resourceFile, new File(getEnv().getDataRootPath() + Prefix.XREF_P),
                getPath(), env.isCompressXref());
    }

    /**
     * Is revision the latest revision ?
     * @param rev revision string
     * @return true if latest revision, false otherwise
     */
    public boolean isLatestRevision(String rev) {
        return rev.equals(getLatestRevision(getResourceFile()));
    }

    /**
     * Get the location of cross-reference for given file containing the given revision.
     * @param revStr defined revision string
     * @return location to redirect to
     */
    public String getRevisionLocation(String revStr) {
        StringBuilder sb = new StringBuilder();

        sb.append(req.getContextPath());
        sb.append(Prefix.XREF_P);
        sb.append(Util.uriEncodePath(getPath()));
        sb.append("?");
        sb.append(QueryParameters.REVISION_PARAM_EQ);
        sb.append(Util.uriEncode(revStr));

        if (req.getQueryString() != null) {
            sb.append("&");
            sb.append(req.getQueryString());
        }
        if (fragmentIdentifier != null) {
            String anchor = Util.uriEncode(fragmentIdentifier);

            String reqFrag = req.getParameter(QueryParameters.FRAGMENT_IDENTIFIER_PARAM);
            if (reqFrag == null || reqFrag.isEmpty()) {
                /*
                 * We've determined that the fragmentIdentifier field must have
                 * been set to augment request parameters. Now include it
                 * explicitly in the next request parameters.
                 */
                sb.append("&");
                sb.append(QueryParameters.FRAGMENT_IDENTIFIER_PARAM_EQ);
                sb.append(anchor);
            }
            sb.append("#");
            sb.append(anchor);
        }

        return sb.toString();
    }

    /**
     * Get the path the request should be redirected (if any).
     *
     * @return {@code null} if there is no reason to redirect, the URI encoded
     * redirect path to use otherwise.
     */
    public String getDirectoryRedirect() {
        if (isDir()) {
            getPrefix();
            // Redirect /xref -> /xref/
            if (prefix == Prefix.XREF_P
                    && getUriEncodedPath().isEmpty()
                    && !req.getRequestURI().endsWith("/")) {
                return req.getContextPath() + Prefix.XREF_P + '/';
            }

            if (getPath().isEmpty()) {
                // => /
                return null;
            }

            if (prefix != Prefix.XREF_P && prefix != Prefix.HIST_L && prefix != Prefix.RSS_P) {
                // if it is an existing dir perhaps people wanted dir xref
                return req.getContextPath() + Prefix.XREF_P + getUriEncodedPath() + trailingSlash(getPath());
            }
            String ts = trailingSlash(getPath());
            if (!ts.isEmpty()) {
                return req.getContextPath() + prefix + getUriEncodedPath() + ts;
            }
        }
        return null;
    }

    /**
     * Get the URI encoded canonical path to the related file or directory (the
     * URI part between the servlet path and the start of the query string).
     *
     * @return a URI encoded path which might be an empty string but not {@code null}.
     * @see #getPath()
     */
    public String getUriEncodedPath() {
        if (uriEncodedPath == null) {
            uriEncodedPath = Util.uriEncodePath(getPath());
        }
        return uriEncodedPath;
    }

    /**
     * Add a new file script to the page by the name.
     *
     * @param name name of the script to search for
     * @return this
     *
     * @see Scripts#addScript(String, String, Scripts.Type)
     */
    public PageConfig addScript(String name) {
        this.scripts.addScript(this.req.getContextPath(), name, isDebug() ? Scripts.Type.DEBUG : Scripts.Type.MINIFIED);
        return this;
    }

    private boolean isDebug() {
        return Boolean.parseBoolean(req.getParameter(DEBUG_PARAM_NAME));
    }

    /**
     * Return the page scripts.
     *
     * @return the scripts
     *
     * @see Scripts
     */
    public Scripts getScripts() {
        return this.scripts;
    }

    /**
     * Get opengrok's configured data root directory. It is verified, that the
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

    /**
     * Play nice in reverse proxy environment by using pre-configured hostname request to construct the URLs.
     * Will not work well if the scheme or port is different for proxied server and original server.
     * @return server name
     */
    public String getServerName() {
        if (env.getServerName() != null) {
            return env.getServerName();
        } else {
            return Laundromat.launderServerName(req.getServerName());
        }
    }

    /**
     * Prepare a search helper with all required information, ready to execute
     * the query implied by the related request parameters and cookies.
     * <p>
     * NOTE: One should check the {@link SearchHelper#getErrorMsg()} as well as
     * {@link SearchHelper#getRedirect()} and take the appropriate action before
     * executing the prepared query or continue processing.
     * <p>
     * This method stops populating fields as soon as an error occurs.
     *
     * @return a search helper.
     */
    public SearchHelper prepareSearch() {
        List<SortOrder> sortOrders = getSortOrder();
        SearchHelper sh = prepareInternalSearch(sortOrders.isEmpty() ? SortOrder.RELEVANCY : sortOrders.get(0));

        if (getRequestedProjects().isEmpty() && getEnv().hasProjects()) {
            sh.setErrorMsg("You must select a project!");
            return sh;
        }

        if (sh.getBuilder().getSize() == 0) {
            // Entry page show the map
            sh.setRedirect(req.getContextPath() + '/');
            return sh;
        }

        return sh;
    }

    /**
     * Prepare a search helper with required settings for an internal search.
     * <p>
     * NOTE: One should check the {@link SearchHelper#getErrorMsg()} as well as
     * {@link SearchHelper#getRedirect()} and take the appropriate action before
     * executing the prepared query or continue processing.
     * <p>
     * This method stops populating fields as soon as an error occurs.
     * @param sortOrder instance of {@link SortOrder}
     * @return a search helper.
     */
    public SearchHelper prepareInternalSearch(SortOrder sortOrder) {
        String xrValue = req.getParameter(QueryParameters.NO_REDIRECT_PARAM);
        return new SearchHelper.Builder(getDataRoot(), new File(getSourceRootPath()), getEftarReader(),
                getQueryBuilder(), req.getContextPath())
                .start(getStartIndex())
                .order(sortOrder)
                .maxItems(getMaxItems())
                .crossRefSearch(getPrefix() == Prefix.SEARCH_R)
                .guiSearch(getPrefix() == Prefix.SEARCH_R || getPrefix() == Prefix.SEARCH_P)
                .noRedirect(xrValue != null && !xrValue.isEmpty())
                .build();
    }

    /**
     * @param project {@link Project} instance or {@code null}
     * @param request HTTP request
     * @return list of {@link NullableNumLinesLOC} instances related to {@link #path}
     * @throws IOException on I/O error
     */
    @Nullable
    public List<NullableNumLinesLOC> getExtras(@Nullable Project project, HttpServletRequest request)
            throws IOException {

        SearchHelper searchHelper = prepareInternalSearch(SortOrder.RELEVANCY);
        /*
         * N.b. searchHelper.destroy() is called via
         * WebappListener.requestDestroyed() on presence of the following
         * REQUEST_ATTR.
         */
        request.setAttribute(SearchHelper.REQUEST_ATTR, searchHelper);
        Optional.ofNullable(project)
                .ifPresentOrElse(searchHelper::prepareExec,
                        () -> searchHelper.prepareExec(new TreeSet<>())
                );
        return getNullableNumLinesLOCS(project, searchHelper);
    }

    @Nullable
    @VisibleForTesting
    public List<NullableNumLinesLOC> getNullableNumLinesLOCS(@Nullable Project project, SearchHelper searchHelper)
            throws IOException {

        if (searchHelper.getSearcher() != null) {
            DirectoryExtraReader extraReader = new DirectoryExtraReader();
            String primePath = path;
            try {
                primePath = searchHelper.getPrimeRelativePath(project != null ? project.getName() : "", path);
            } catch (IOException | ForbiddenSymlinkException ex) {
                LOGGER.log(Level.WARNING, String.format(
                        "Error getting prime relative for %s", path), ex);
            }
            return extraReader.search(searchHelper.getSearcher(), primePath);
        }

        return null;
    }

    /**
     * Get the config w.r.t. the given request. If there is none yet, a new config
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

    public static PageConfig get(String path, HttpServletRequest request) {
        PageConfig pcfg = new PageConfig(request);
        pcfg.setPath(path);
        return pcfg;
    }

    private PageConfig() {
        this.authFramework = RuntimeEnvironment.getInstance().getAuthorizationFramework();
        this.executor = RuntimeEnvironment.getInstance().getRevisionExecutor();
    }

    private PageConfig(HttpServletRequest req) {
        this();

        this.req = req;
        this.fragmentIdentifier = req.getParameter(QueryParameters.FRAGMENT_IDENTIFIER_PARAM);
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
        ProjectHelper.cleanup(cfg);
        sr.removeAttribute(ATTR_NAME);
        cfg.env = null;
        cfg.req = null;
        if (cfg.eftarReader != null) {
            cfg.eftarReader.close();
        }
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


    public SortedSet<AcceptedMessage> getMessages() {
        return env.getMessages();
    }

    public SortedSet<AcceptedMessage> getMessages(String tag) {
        return env.getMessages(tag);
    }

    /**
     * Get basename of the path or "/" if the path is empty.
     * This is used for setting title of various pages.
     * @param path path
     * @return short version of the path
     */
    public String getShortPath(String path) {
        File file = new File(path);

        if (path.isEmpty()) {
            return "/";
        }

        return file.getName();
    }

    private String addTitleDelimiter(String title) {
        if (!title.isEmpty()) {
            return title + ", ";
        }

        return title;
    }

    /**
     * The search page title string should progressively reflect the search terms
     * so that if only small portion of the string is seen, it describes
     * the action as closely as possible while remaining readable.
     * @return string used for setting page title of search results page
     */
    public String getSearchTitle() {
        String title = "";

        if (req.getParameter(QueryBuilder.FULL) != null && !req.getParameter(QueryBuilder.FULL).isEmpty()) {
            title += req.getParameter(QueryBuilder.FULL) + " (full)";
        }
        if (req.getParameter(QueryBuilder.DEFS) != null && !req.getParameter(QueryBuilder.DEFS).isEmpty()) {
            title = addTitleDelimiter(title);
            title += req.getParameter(QueryBuilder.DEFS) + " (definition)";
        }
        if (req.getParameter(QueryBuilder.REFS) != null && !req.getParameter(QueryBuilder.REFS).isEmpty()) {
            title = addTitleDelimiter(title);
            title += req.getParameter(QueryBuilder.REFS) + " (reference)";
        }
        if (req.getParameter(QueryBuilder.PATH) != null && !req.getParameter(QueryBuilder.PATH).isEmpty()) {
            title = addTitleDelimiter(title);
            title += req.getParameter(QueryBuilder.PATH) + " (path)";
        }
        if (req.getParameter(QueryBuilder.HIST) != null && !req.getParameter(QueryBuilder.HIST).isEmpty()) {
            title = addTitleDelimiter(title);
            title += req.getParameter(QueryBuilder.HIST) + " (history)";
        }

        if (req.getParameterValues(QueryBuilder.PROJECT) != null && req.getParameterValues(QueryBuilder.PROJECT).length != 0) {
            if (!title.isEmpty()) {
                title += " ";
            }
            title += "in projects: ";
            String[] projects = req.getParameterValues(QueryBuilder.PROJECT);
            title += String.join(",", projects);
        }

        return Util.htmlize(title + " - OpenGrok search results");
    }

    /**
     * Similar as {@link #getSearchTitle()}.
     * @return string used for setting page title of search view
     */
    public String getHistoryTitle() {
        String strPath = getPath();
        return Util.htmlize(getShortPath(strPath) + " - OpenGrok history log for " + strPath);
    }

    /**
     * @return page title string with base name of the path and optionally revision ID, HTML encoded
     */
    public String getPathTitle() {
        String strPath = getPath();
        String title = getShortPath(strPath);
        if (!getRequestedRevision().isEmpty()) {
            title += " (revision " + getRequestedRevision() + ")";
        }
        title += " - OpenGrok cross reference for " + (strPath.isEmpty() ? "/" : strPath);

        return Util.htmlize(title);
    }

    public void checkSourceRootExistence() throws IOException {
        if (getSourceRootPath() == null || getSourceRootPath().isEmpty()) {
            throw new FileNotFoundException("Unable to determine source root path. Missing configuration?");
        }
        File sourceRootPathFile = RuntimeEnvironment.getInstance().getSourceRootFile();
        if (!sourceRootPathFile.exists()) {
            throw new FileNotFoundException(String.format("Source root path \"%s\" does not exist",
                    sourceRootPathFile.getAbsolutePath()));
        }
        if (!sourceRootPathFile.isDirectory()) {
            throw new FileNotFoundException(String.format("Source root path \"%s\" is not a directory",
                    sourceRootPathFile.getAbsolutePath()));
        }
        if (!sourceRootPathFile.canRead()) {
            throw new IOException(String.format("Source root path \"%s\" is not readable",
                    sourceRootPathFile.getAbsolutePath()));
        }
    }

    /**
     * Get all project related messages. These include
     * <ol>
     * <li>Main messages</li>
     * <li>Messages with tag = project name</li>
     * <li>Messages with tag = project's groups names</li>
     * </ol>
     *
     * @return the sorted set of messages according to accept time
     * @see org.opengrok.indexer.web.messages.MessagesContainer#MESSAGES_MAIN_PAGE_TAG
     */
    private SortedSet<AcceptedMessage> getProjectMessages() {
        SortedSet<AcceptedMessage> messages = getMessages();

        if (getProject() != null) {
            messages.addAll(getMessages(getProject().getName()));
            getProject().getGroups()
                    .stream()
                    .map(Group::getName)
                    .map(this::getMessages)
                    .forEach(messages::addAll);
        }

        return messages;
    }

    /**
     * Decide if this resource has been modified since the header value in the request.
     * <p>
     * The resource is modified since the weak ETag value in the request, the ETag is
     * computed using:
     * </p>
     * <ul>
     * <li>the source file modification</li>
     * <li>project messages</li>
     * <li>last timestamp for index</li>
     * <li>OpenGrok current deployed version</li>
     * </ul>
     *
     * <p>
     * If the resource was modified, appropriate headers in the response are filled.
     * </p>
     *
     * @param request the http request containing the headers
     * @param response the http response for setting the headers
     * @return true if resource was not modified; false otherwise
     * @see <a href="https://tools.ietf.org/html/rfc7232#section-2.3">HTTP ETag</a>
     * @see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html">HTTP Caching</a>
     */
    public boolean isNotModified(HttpServletRequest request, HttpServletResponse response) {
        String currentEtag = String.format("W/\"%s\"",
                Objects.hash(
                        // last modified time as UTC timestamp in millis
                        getLastModified(),
                        // all project related messages which changes the view
                        getProjectMessages(),
                        // last timestamp value
                        getEnv().getDateForLastIndexRun() != null ? getEnv().getDateForLastIndexRun().getTime() : 0,
                        // OpenGrok version has changed since the last time
                        Info.getVersion()
                )
        );

        String headerEtag = request.getHeader(HttpHeaders.IF_NONE_MATCH);

        if (headerEtag != null && headerEtag.equals(currentEtag)) {
            // weak ETag has not changed, return 304 NOT MODIFIED
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return true;
        }

        // return 200 OK
        response.setHeader(HttpHeaders.ETAG, currentEtag);
        return false;
    }

    /**
     * @param root root path
     * @param path path
     * @return path relative to root
     */
    public static String getRelativePath(String root, String path) {
        return Paths.get(root).relativize(Paths.get(path)).toString();
    }

    /**
     * Determines whether a match offset from a search result has been indicated,
     * and if so tries to calculate a translated xref fragment identifier.
     * @return {@code true} if a xref fragment identifier was calculated by the call to this method
     */
    public boolean evaluateMatchOffset() {
        if (fragmentIdentifier == null) {
            int matchOffset = getIntParam(QueryParameters.MATCH_OFFSET_PARAM, -1);
            if (matchOffset >= 0) {
                File objResourceFile = getResourceFile();
                if (objResourceFile.isFile()) {
                    LineBreaker breaker = new LineBreaker();
                    StreamSource streamSource = StreamSource.fromFile(objResourceFile);
                    try {
                        breaker.reset(streamSource, in -> ExpandTabsReader.wrap(in, getProject()));
                        int matchLine = breaker.findLineIndex(matchOffset);
                        if (matchLine >= 0) {
                            // Convert to 1-based offset to accord with OpenGrok line number.
                            fragmentIdentifier = String.valueOf(matchLine + 1);
                            return true;
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, String.format("Failed to evaluate match offset for %s",
                                objResourceFile), e);
                    }
                }
            }
        }
        return false;
    }
}
