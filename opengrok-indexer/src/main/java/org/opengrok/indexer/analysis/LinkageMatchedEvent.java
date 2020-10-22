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
 * Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

/**
 * Represents an event raised when a symbol matcher matches a string that
 * would not be published as a symbol and that can be linked within OpenGrok or
 * linked externally.
 */
public class LinkageMatchedEvent {

    private final Object source;
    private final String str;
    private final LinkageType linkageType;
    private final long start;
    private final long end;
    private final String lstr;

    /**
     * Initializes an immutable instance of {@link LinkageMatchedEvent} with
     * {@link #getLinkStr()} equal to {@link #getStr()}.
     * @param source the event source
     * @param str the text string (literal capture)
     * @param linkageType the text linkage type
     * @param start the text start position
     * @param end the text end position
     */
    public LinkageMatchedEvent(Object source, String str, LinkageType linkageType,
            long start, long end) {
        this.source = source;
        this.str = str;
        this.linkageType = linkageType;
        this.start = start;
        this.end = end;
        this.lstr = str;
    }

    /**
     * Initializes an immutable instance of {@link LinkageMatchedEvent}.
     * @param source the event source
     * @param str the text string (literal capture)
     * @param linkageType the text linkage type
     * @param start the text start position
     * @param end the text end position
     * @param lstr the text link string
     */
    public LinkageMatchedEvent(Object source, String str, LinkageType linkageType,
            long start, long end, String lstr) {
        this.source = source;
        this.str = str;
        this.linkageType = linkageType;
        this.start = start;
        this.end = end;
        this.lstr = lstr;
    }

    /**
     * Gets the event source.
     * @return the event source
     */
    public Object getSource() {
        return source;
    }

    /**
     * Gets the text string (literal capture).
     * @return the initial value
     */
    public String getStr() {
        return str;
    }

    /**
     * Gets the text end position, likely equal to {@link #getStr()} but
     * depending on {@link #getLinkageType()}.
     * @return the initial value
     */
    public String getLinkStr() {
        return lstr;
    }

    /**
     * Gets the text linkage type.
     * @return the initial value
     */
    public LinkageType getLinkageType() {
        return linkageType;
    }

    /**
     * Gets the text start position.
     * @return the initial value
     */
    public long getStart() {
        return start;
    }

    /**
     * Gets the text end position.
     * @return the initial value
     */
    public long getEnd() {
        return end;
    }
}
