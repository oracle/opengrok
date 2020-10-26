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
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.History;
import org.opengrok.indexer.history.HistoryEntry;
import org.opengrok.indexer.history.HistoryException;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.web.messages.JSONable;
import org.opengrok.web.api.v1.filter.CorsEnable;
import org.opengrok.web.api.v1.filter.PathAuthorized;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;

@Path(HistoryController.PATH)
public final class HistoryController {

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private static final int MAX_RESULTS = 1000;

    public static final String PATH = "/history";

    static class HistoryEntryDTO implements JSONable {
        @JsonProperty
        private String revision;
        @JsonProperty
        private Date date;
        @JsonProperty
        private String author;
        @JsonProperty
        private String tags;
        @JsonProperty
        private String message;
        @JsonProperty
        private SortedSet<String> files;

        // for testing
        HistoryEntryDTO() {
        }

        HistoryEntryDTO(HistoryEntry entry) {
            this.revision = entry.getRevision();
            this.date = entry.getDate();
            this.author = entry.getAuthor();
            this.tags = entry.getTags();
            this.message = entry.getMessage();
            this.files = entry.getFiles();
        }

        // for testing
        public String getAuthor() {
            return author;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final HistoryEntryDTO other = (HistoryEntryDTO) obj;
            if (!Objects.equals(this.revision, other.revision)) {
                return false;
            }
            if (!Objects.equals(this.date, other.date)) {
                return false;
            }
            if (!Objects.equals(this.author, other.author)) {
                return false;
            }
            if (!Objects.equals(this.tags, other.tags)) {
                return false;
            }
            if (!Objects.equals(this.message, other.message)) {
                return false;
            }
            if (!Objects.equals(this.files, other.files)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(revision, date, author, tags, message, files);
        }
    }

    static class HistoryDTO implements JSONable {
        @JsonProperty
        private final List<HistoryEntryDTO> entries;
        @JsonProperty
        private int start;
        @JsonProperty
        private int count;
        @JsonProperty
        private int total;

        // for testing
        HistoryDTO() {
            this.entries = new ArrayList<>();
        }

        HistoryDTO(List<HistoryEntryDTO> entries, int start, int count, int total) {
            this.entries = entries;
            this.start = start;
            this.count = count;
            this.total = total;
        }

        // for testing
        public List<HistoryEntryDTO> getEntries() {
            return entries;
        }

        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final HistoryDTO other = (HistoryDTO) obj;
            if (!Objects.equals(this.entries, other.entries)) {
                return false;
            }
            if (!Objects.equals(this.start, other.start)) {
                return false;
            }
            if (!Objects.equals(this.count, other.count)) {
                return false;
            }
            if (!Objects.equals(this.total, other.total)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(entries, start, count, total);
        }
    }

    static HistoryDTO getHistoryDTO(List<HistoryEntry> historyEntries, int start, int count, int total) {
        List<HistoryEntryDTO> entries = new ArrayList<>();
        historyEntries.stream().map(HistoryEntryDTO::new).forEach(entries::add);
        return new HistoryDTO(entries, start, count, total);
    }

    @GET
    @CorsEnable
    @PathAuthorized
    @Produces(MediaType.APPLICATION_JSON)
    public HistoryDTO get(@Context HttpServletRequest request,
                          @Context HttpServletResponse response,
                          @QueryParam("path") final String path,
                          @QueryParam("withFiles") final boolean withFiles,
                          @QueryParam("max") @DefaultValue(MAX_RESULTS + "") final int maxEntries,
                          @QueryParam("start") @DefaultValue(0 + "") final int startIndex)
            throws HistoryException {

        History history = HistoryGuru.getInstance().getHistory(new File(env.getSourceRootFile(), path),
                withFiles, true);
        if (history == null) {
            return null;
        }

        return getHistoryDTO(history.getHistoryEntries(maxEntries, startIndex),
                startIndex, maxEntries, history.getHistoryEntries().size());
    }
}
