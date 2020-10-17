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
 * Copyright (c) 2018-2019, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.search;

import org.apache.lucene.search.uhighlight.Passage;
import org.apache.lucene.search.uhighlight.PassageFormatter;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.search.context.ContextArgs;
import org.opengrok.indexer.search.context.LineHighlight;
import org.opengrok.indexer.search.context.PassageConverter;
import org.opengrok.indexer.search.context.PhraseHighlight;
import org.opengrok.indexer.util.SourceSplitter;
import org.opengrok.indexer.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.regex.Matcher;

/**
 * Represents a subclass of {@link PassageFormatter} that uses
 * {@link PassageConverter} to produce {@link Hit} instances.
 */
public class HitFormatter extends SearchFormatterBase {

    private String filename;

    /**
     * Initializes a formatter for the specified arguments.
     */
    public HitFormatter() {
        super(new PassageConverter(new ContextArgs((short) 0, Short.MAX_VALUE)));
    }

    /**
     * Gets the source code file name, including optional path.
     * @return the full path or {@code null}
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Sets the source code file name.
     * @param value the file name to use
     */
    public void setFilename(String value) {
        this.filename = value;
    }

    /**
     * Splits {@code originalText} using {@link SourceSplitter}, converts
     * passages using {@link PassageConverter}, and formats for returning hits
     * through the search API.
     * @param passages a required instance
     * @param originalText a required instance
     * @return a defined list of {@link Hit} instances, which might be empty
     */
    @Override
    public Object format(Passage[] passages, String originalText) {

        updateOriginalText(originalText);

        SortedMap<Integer, LineHighlight> lines = cvt.convert(passages, splitter);
        List<Hit> res = new ArrayList<>();
        for (LineHighlight lhi : lines.values()) {
            final int lineOffset = lhi.getLineno();

            String line = splitter.getLine(lineOffset);
            Matcher eolMatcher = StringUtils.STANDARD_EOL.matcher(line);
            if (eolMatcher.find()) {
                line = line.substring(0, eolMatcher.start());
            }

            for (int i = 0; i < lhi.countMarkups(); ++i) {
                marks.clear();
                PhraseHighlight phi = lhi.getMarkup(i);
                checkIfMark(line, phi);

                Hit hit = new Hit(filename);
                // `binary' is false
                hit.setLine(line);
                hit.setLineno(String.valueOf(lineOffset + 1)); // to 1-offset
                hit.setLeft(phi.getLineStart());
                hit.setRight(phi.getLineEnd());

                if (defs != null) {
                    // N.b. use ctags 1-offset vs 0-offset.
                    List<Definitions.Tag> lineTags =  defs.getTags(lineOffset + 1);
                    if (lineTags != null) {
                        Definitions.Tag pickedTag = findTagForMark(lineTags, marks);
                        if (pickedTag != null) {
                            hit.setTag(pickedTag.type);
                        }
                    }
                }

                res.add(hit);
            }
        }

        return res;
    }
}
