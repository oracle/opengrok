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
 * Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search.context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.lucene.search.uhighlight.Passage;
import org.apache.lucene.search.uhighlight.PassageFormatter;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.analysis.Definitions.Tag;
import org.opengrok.indexer.analysis.Scopes;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.SourceSplitter;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.web.HtmlConsts;
import org.opengrok.indexer.web.Util;

/**
 * Represents a subclass of {@link PassageFormatter} that uses
 * {@link PassageConverter}.
 */
public class ContextFormatter extends PassageFormatter {

    private static final String MORE_LABEL = "[all " + HtmlConsts.HELLIP + "]";

    private static final Logger LOGGER = LoggerFactory.getLogger(
        ContextFormatter.class);

    /**
     * Matches a non-word character.
     */
    private static final Pattern NONWORD_CHAR = Pattern.compile("(?U)\\W");

    private final PassageConverter cvt;
    private final List<String> marks = new ArrayList<>();
    private final ContextArgs contextArgs;
    private final HashSet<Integer> reportedScopes = new HashSet<>();
    private String url;
    private Definitions defs;
    private Scopes scopes;
    private boolean showingContext;
    private boolean showOverline;

    /**
     * An optional URL for linking when the "moreLimit" (if positive) is
     * reached.
     */
    private String moreUrl;

    /**
     * Cached splitter, keyed by {@link #originalText}.
     */
    private SourceSplitter splitter;
    private String originalText;

    /**
     * Initializes a formatter for the specified arguments.
     * @param args required instance
     */
    public ContextFormatter(ContextArgs args) {
        this.cvt = new PassageConverter(args);
        this.contextArgs = args;
    }

    /**
     * Gets the initialized value.
     * @return a defined instance
     */
    public ContextArgs getArgs() {
        return cvt.getArgs();
    }

    /**
     * Gets the required URL to use for linking lines.
     * @return the URL or {@code null}
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the required URL to use for linking lines.
     * @param value the URL to use
     */
    public void setUrl(String value) {
        this.url = value;
    }

    /**
     * Gets the optional URL to use if the "more" limit is reached.
     * @return the URL or {@code null}
     */
    public String getMoreUrl() {
        return moreUrl;
    }

    /**
     * Sets the optional URL to use if the "more" limit is reached.
     * @param value the URL to use
     */
    public void setMoreUrl(String value) {
        this.moreUrl = value;
    }

    /**
     * Gets the optional definitions.
     * @return the defs
     */
    public Definitions getDefs() {
        return defs;
    }

    /**
     * Sets the optional definitions.
     * @param value definitions
     */
    public void setDefs(Definitions value) {
        this.defs = value;
    }

    /**
     * Gets the optional scopes to use.
     * @return the scopes
     */
    public Scopes getScopes() {
        return scopes;
    }

    /**
     * Sets the optional scopes to use.
     * @param value scopes
     */
    public void setScopes(Scopes value) {
        this.scopes = value;
    }

    /**
     * Splits {@code originalText} using {@link SourceSplitter}, converts
     * passages using {@link PassageConverter}, and formats for presentation in
     * OpenGrok UI using the instance's properties (e.g., {@link #getUrl()} and
     * {@link #getDefs()}).
     * @param passages a required instance
     * @param originalText a required instance
     * @return a defined {@link FormattedLines} instance, which might be empty
     * @throws IllegalStateException if {@link #getUrl()} is null
     */
    @Override
    public Object format(Passage[] passages, String originalText) {
        String lineUrl = url;
        if (lineUrl == null) {
            throw new IllegalStateException("Url property is null");
        }

        if (this.originalText == null || !this.originalText.equals(
                originalText)) {
            splitter = new SourceSplitter();
            splitter.reset(originalText);
            this.originalText = originalText;
        }

        FormattedLines res = new FormattedLines();
        StringBuilder bld = new StringBuilder();
        SortedMap<Integer, LineHighlight> lines = cvt.convert(passages, splitter);
        int numl = 0;
        int contextLimit = calculateContextLimit(lines);
        boolean limited = false;
        int lastLineno = lines.isEmpty() ? 0 : lines.firstKey();
        for (Map.Entry<Integer, LineHighlight> entry : lines.entrySet()) {
            if (++numl > contextLimit) {
                limited = true;
                break;
            }

            LineHighlight lhi = entry.getValue();
            String line = splitter.getLine(lhi.getLineno());
            Matcher eolMatcher = StringUtils.STANDARD_EOL.matcher(line);
            if (eolMatcher.find()) {
                line = line.substring(0, eolMatcher.start());
            }

            /*
             * When showing context, determine if the current line is non-
             * consecutive in order to show an overline.
             */
            showingContext = cvt.getArgs().getContextSurround() > 0;
            if (!showingContext) {
                showOverline = false;
            } else {
                showOverline = lhi.getLineno() - lastLineno > 1;
                lastLineno = lhi.getLineno();
            }

            try {
                marks.clear();
                startLine(bld, lineUrl, lhi.getLineno());
                int loff = 0;
                int hioff = 0;
                while (loff < line.length()) {
                    // If there are no more markups, use all remaining text.
                    if (hioff >= lhi.countMarkups() ||
                            lhi.getMarkup(hioff).getLineStart() >=
                            line.length()) {
                        lhi.hsub(bld, line, loff);
                        break;
                    }

                    PhraseHighlight phi = lhi.getMarkup(hioff++);

                    /*
                     * If the highlight is a sub-string wholly within the
                     * line, add it to the `marks' list.
                     */
                    if (phi.getLineStart() >= 0 &&
                            phi.getLineEnd() <= line.length()) {
                        marks.add(line.substring(phi.getLineStart(),
                                phi.getLineEnd()));
                    }

                    // Append any line text preceding the phrase highlight ...
                    if (phi.getLineStart() >= 0) {
                        lhi.hsub(bld, line, loff, phi.getLineStart());
                        loff += phi.getLineStart() - loff;
                    }
                    // ... then start the BOLD.
                    bld.append(HtmlConsts.B);

                    // Include the text of the highlight ...
                    if (phi.getLineEnd() >= line.length()) {
                        lhi.hsub(bld, line, loff);
                        loff = line.length();
                    } else {
                        lhi.hsub(bld, line, loff, phi.getLineEnd());
                        loff += phi.getLineEnd() - loff;
                    }
                    // ... then end the BOLD.
                    bld.append(HtmlConsts.ZB);
                }

                finishLine(bld, lhi.getLineno(), lhi.countMarkups() > 0);
                /*
                 * Appending a LF here would hurt the more.jsp view, while
                 * search.jsp (where getContext() does it) is indifferent -- so
                 * skip it.
                 */
                res.put(lhi.getLineno(), bld.toString());
                bld.setLength(0);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Could not format()", e);
                return res;
            }
        }

        res.setLimited(limited);
        if (moreUrl != null) {
            bld.append("<a href=\"");
            bld.append(moreUrl);
            bld.append("\">");
            bld.append(MORE_LABEL);
            bld.append("</a>");
            bld.append(HtmlConsts.BR);
            bld.append("\n");
            res.setFooter(bld.toString());
            bld.setLength(0);
        }
        return res;
    }

    private void startLine(Appendable dest, String lineUrl, int lineOffset) throws IOException {
        if (showingContext) {
            if (showOverline) {
                dest.append("<span class=\"ovl\">");
                reportedScopes.clear();
            } else {
                dest.append("<span class=\"xovl\">");
            }
        }
        dest.append("<a class=\"s\" href=\"");
        dest.append(lineUrl);
        String num = String.valueOf(lineOffset + 1);
        dest.append("#");
        dest.append(num);
        dest.append("\"><span class=\"l\">");
        dest.append(num);
        dest.append("</span> ");
    }

    private void finishLine(Appendable dest, int lineOffset, boolean hasHighlights)
            throws IOException {
        dest.append("</a>");
        if (hasHighlights) {
            writeTag(lineOffset, dest);
            writeScope(lineOffset, dest);
        }

        // Regardless of true EOL, write a <br/>.
        dest.append(HtmlConsts.BR);
        if (showingContext) {
            dest.append(HtmlConsts.ZSPAN);
        }
    }

    private void writeScope(int lineOffset, Appendable dest) throws IOException {
        Scopes.Scope scope = null;
        if (scopes != null) {
            // N.b. use ctags 1-based indexing vs 0-based.
            scope = scopes.getScope(lineOffset + 1);
        }
        if (scope != null && isOkToReportScope(scope)) {
            dest.append("  <a class=\"scope\" href=\"");
            dest.append(url);
            dest.append("#");
            dest.append(String.valueOf(scope.getLineFrom()));
            dest.append("\">in ");
            Util.htmlize(scope.getName(), dest);
            dest.append("()</a>");
        }
    }

    private boolean isOkToReportScope(Scopes.Scope scope) {
        if (scope != scopes.getScope(-1)) {
            if (!showingContext) {
                return true;
            }
            if (!reportedScopes.contains(scope.getLineFrom())) {
                reportedScopes.add(scope.getLineFrom());
                return true;
            }
        }
        return false;
    }

    private void writeTag(int lineOffset, Appendable dest) throws IOException {
        if (defs != null) {
            // N.b. use ctags 1-based indexing vs 0-based.
            List<Tag> linetags =  defs.getTags(lineOffset + 1);
            if (linetags != null) {
                Tag pickedTag = findTagForMark(linetags);
                if (pickedTag != null) {
                    dest.append("  <i>");
                    Util.htmlize(pickedTag.type, dest);
                    dest.append("</i>");
                }
            }
        }
    }

    /**
     * Search the cross product of {@code linetags} and {@code marks} for any
     * mark that starts with a {@link Tag#symbol} and where any subsequent
     * character is a non-word ({@code (?U)\W}) character.
     * @return a defined instance or {@code null}
     */
    private Tag findTagForMark(List<Tag> linetags) {
        for (Tag tag : linetags) {
            if (tag.type != null) {
                for (String mark : marks) {
                    if (mark.startsWith(tag.symbol) && (mark.length() ==
                            tag.symbol.length() || isNonWord(
                                mark.charAt(tag.symbol.length())))) {
                        return tag;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isNonWord(char c) {
        String cword = String.valueOf(c);
        return NONWORD_CHAR.matcher(cword).matches();
    }

    /**
     * Calculates a context limit when surrounding context is enabled to
     * possibly reduce below moreLimit so that dangling surrounding context is
     * not displayed.
     */
    private int calculateContextLimit(SortedMap<Integer, LineHighlight> lines) {
        int moreLimit = contextArgs.getContextLimit();
        // If moreLimit is not applicable, then leave unbounded.
        if (moreLimit < 1) {
            return Integer.MAX_VALUE;
        }
        /*
         * If no surrounding context is specified or if the lines are already
         * within moreLimit, then just use moreLimit.
         */
        if (cvt.getArgs().getContextSurround() < 1 || lines.size() <= moreLimit) {
            return moreLimit;
        }
        /*
         * Walk back from moreLimit to ensure not to leave any dangling
         * surrounding context by making sure there is a phrase highlight seen
         * before a non-contiguous lineno.
         */
        LineHighlight[] lineHighlights = lines.values().toArray(new LineHighlight[0]);
        int i = moreLimit - 1;
        int lastLineno = lineHighlights[i].getLineno();
        int newMoreLimit = moreLimit;
        int trailingCount = 0;
        for (; i >= 0; --i) {
            LineHighlight lhi = lineHighlights[i];
            if (lhi.countMarkups() > 0) {
                break;
            }
            if (lastLineno - lhi.getLineno() > 1) {
                // Found non-contiguity without having seen a highlight.
                newMoreLimit = i + 1;
                trailingCount = 1;
            } else if (trailingCount + 1 > contextArgs.getContextSurround()) {
                // Found superfluous surrounding context.
                --newMoreLimit;
            } else {
                ++trailingCount;
            }
            lastLineno = lhi.getLineno();
        }
        return newMoreLimit;
    }
}
