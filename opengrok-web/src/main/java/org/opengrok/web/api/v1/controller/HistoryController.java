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
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 */

package org.opengrok.web.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.opengrok.indexer.authorization.AuthorizationFramework;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.History;
import org.opengrok.indexer.history.HistoryEntry;
import org.opengrok.indexer.history.HistoryException;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.web.messages.JSONable;
import org.opengrok.web.api.v1.filter.CorsEnable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;

@Path(HistoryController.PATH)
public final class HistoryController {

    private RuntimeEnvironment env = RuntimeEnvironment.getInstance();

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

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            final HistoryEntryDTO other = (HistoryEntryDTO) obj;
            if (!Objects.equals(this.revision, other.revision)) return false;
            if (!Objects.equals(this.date, other.date)) return false;
            if (!Objects.equals(this.author, other.author)) return false;
            if (!Objects.equals(this.tags, other.tags)) return false;
            if (!Objects.equals(this.message, other.message)) return false;
            if (!Objects.equals(this.files, other.files)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(revision, date, author, tags, message, files);
        }
    }

    static class HistoryDTO implements JSONable {
        @JsonProperty
        private List<HistoryEntryDTO> entries;

        // for testing
        HistoryDTO() {
            this.entries = new ArrayList<>();
        }

        HistoryDTO(List<HistoryEntryDTO> entries) {
            this.entries = entries;
        }

        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            final HistoryDTO other = (HistoryDTO) obj;
            return Objects.equals(this.entries, other.entries);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entries);
        }
    }

    static HistoryDTO getHistoryDTO(List<HistoryEntry> historyEntries) {
        List<HistoryEntryDTO> entries = new ArrayList<>();
        for (HistoryEntry entry : historyEntries) {
            entries.add(new HistoryEntryDTO(entry));
        }
        HistoryDTO res = new HistoryDTO(entries);
        return res;
    }

    @GET
    @CorsEnable
    @Produces(MediaType.APPLICATION_JSON)
    public HistoryDTO get(@Context HttpServletRequest request,
                          @Context HttpServletResponse response,
                          @QueryParam("path") final String path,
                          @QueryParam("withFiles") final boolean withFiles,
                          @QueryParam("max") @DefaultValue(MAX_RESULTS + "") final int maxEntries,
                          @QueryParam("start") @DefaultValue(0 + "") final int startIndex)
            throws HistoryException, IOException {

        if (request != null) {
            AuthorizationFramework auth = env.getAuthorizationFramework();
            if (auth != null) {
                Project p = Project.getProject(path.startsWith("/") ? path : "/" + path);
                if (p != null && !auth.isAllowed(request, p)) {
                    response.sendError(Response.status(Response.Status.FORBIDDEN).build().getStatus(),
                            "not authorized");
                    return null;
                }
            }
        }

        History history = HistoryGuru.getInstance().getHistory(new File(env.getSourceRootFile(), path),
                withFiles, true);
        if (history == null) {
            return null;
        }

        HistoryDTO res = getHistoryDTO(history.getHistoryEntries(maxEntries, startIndex));

        return res;
    }
}
