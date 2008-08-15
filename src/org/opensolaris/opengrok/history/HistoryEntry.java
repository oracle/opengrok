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

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
    private List<String> files;
    private List<String> changeRequests;
 
    /* This holds the subversion repository's view of where the file is in a particular revision */
    private File repositoryPath;

    /* This holds the source root's view of where the file is in a particular revision */
    private File sourceRootPath;
    
    /** Creates a new instance of HistoryEntry */
    public HistoryEntry() {
        message = new StringBuffer();
        files = new ArrayList<String>();
        changeRequests = new ArrayList<String>();
    }
    
    public HistoryEntry(String revision, Date date, String author,
            String message, boolean active) {
        this.revision = revision;
        setDate(date);
        this.author = author;
        this.message = new StringBuffer(message);
        this.active = active;
        this.files = new ArrayList<String>();
        this.changeRequests = new ArrayList<String>();
    }
    
    public String getLine() {
        return revision + " " + date + " " + author + " " + message + "\n";
    }

    public void dump() {
        System.err.println("HistoryEntry : revision       = " + revision);
        System.err.println("HistoryEntry : date           = " + date);
        System.err.println("HistoryEntry : author         = " + author);
        System.err.println("HistoryEntry : active         = " + (active ? "True" : "False") );
        String[] lines = message.toString().split("\n");
        String separator = "=";
        for( String line : lines) {
            System.err.println("HistoryEntry : message        " + separator + " " + line);
            separator = ">";
        }
        separator = "=";
        for( String cr : changeRequests) {
            System.err.println("HistoryEntry : changeRequests " + separator + " " + cr);
            separator = ">";
        }
        separator = "=";
        for( String file : files) {
            System.err.println("HistoryEntry : files          " + separator + " " + file);
            separator = ">";
        }
        System.err.println();
   }

    public String getAuthor() {
        return author;
    }
    
    public Date getDate() {
        if (date != null) {
            return (Date) date.clone();
        } else {
            return null;
        }
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
        if (date != null) {
            this.date = (Date) date.clone();
        } else {
            this.date = null;
        }
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
    
    public void addFile(String file) {
        files.add(file);
    }
    
    public List<String> getFiles() {
        return files;
    }
    
    public void setFiles(List<String> files) {
        this.files = files;
    }
    
    @Override
    public String toString() {
        return getLine();
    }
    
    public void addChangeRequest(String changeRequest) {
        changeRequests.add(changeRequest);
    }
    
    public List<String> getChangeRequests() {
        return changeRequests;
    }
    
    public void setChangeRequests(List<String> changeRequests) {
        this.changeRequests = changeRequests;
    }

    /** 
     * Returns the subversion repository's view of where the file is
     * in a particular revision.
     *
     * @return the path
     */
    public File getRepositoryPath() {
        return repositoryPath;
    }

    /** 
     * Sets the subversion repository's view of where the file is
     * in a particular revision.
     *
     * @param path the path
     */
    public void setRepositoryPath(File path) {
        repositoryPath = path;
    }

    /** 
     * Returns the source root's view of where the file is in a particular revision.
     *
     * @return the path
     */
    public File getSourceRootPath() {
        return sourceRootPath;
    }

    /** 
     * Sets the source root's view of where the file is in a particular revision.
     *
     * @param path the path
     */
    public void setSourceRootPath(File path) {
        sourceRootPath = path;
    }
    
    /**
     * Remove "unneeded" info such as multiline history and files list
     */
    public void strip() {
        int idx = message.indexOf("\n");
        if (idx != -1) {
            message.setLength(idx);
        }
        files.clear();
    }
}
