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
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.search.context;

import org.apache.lucene.search.uhighlight.Passage;
import org.apache.lucene.search.uhighlight.PassageScorer;
import org.opengrok.indexer.logger.LoggerFactory;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Custom {@link PassageScorer} used in {@link OGKUnifiedHighlighter}.
 * The goal is to have ordering of passages based strictly on their start offsets.
 */
public class OGPassageScorer extends PassageScorer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OGPassageScorer.class);

    public OGPassageScorer() {
        // Use non-default values so that the scorer object is easier to identify when debugging.
        super(1, 2, 3);
    }

    @Override
    public float score(Passage passage, int contentLength) {
        LOGGER.log(Level.FINEST, "{0} -> {1}", new Object[]{passage, passage.getStartOffset()});
        return -passage.getStartOffset();
    }
}
