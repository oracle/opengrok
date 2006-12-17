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
 * Copyright 2006 Trond Norbye.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.history;

import java.util.Date;

/**
 * Collect all information of a given revision
 *
 * @author Trond Norbye
 */
public class HistoryEntry {
    private String revision;
    private Date date;
    private String author;
    private StringBuffer message;
    private boolean active;
    
    /** Creates a new instance of HistoryEntry */
    public HistoryEntry() {
        message = new StringBuffer();
    }
    
    public HistoryEntry(String revision, Date date, String author,
                        String message, boolean active) {
        this.revision = revision;
        this.date = date;
        this.author = author;
        this.message = new StringBuffer(message);
        this.active = active;
    }
    
    public String getLine() {
        return revision + " " + date + " " + author + " " + message + "\n";
    }

    public String getAuthor() {
        return author;
    }

    public Date getDate() {
        return date;
    }

    public String getMessage() {
        return message.toString().trim();
    }

    public String getRevision() {
        return revision;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setMessage(String message) {
        this.message.setLength(0);
        this.message.append(message);
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }
    
    public void appendMessage(String message) {
        this.message.append(message);
        this.message.append("\n");
    }
    
    public String toString() {
        return getLine();
    }
}
