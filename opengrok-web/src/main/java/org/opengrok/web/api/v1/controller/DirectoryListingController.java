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
package org.opengrok.web.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.jetbrains.annotations.VisibleForTesting;
import org.opengrok.indexer.analysis.NullableNumLinesLOC;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.CacheException;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.DirectoryEntry;
import org.opengrok.indexer.util.FileExtraZipper;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.web.messages.JSONable;
import org.opengrok.web.DirectoryListing;
import org.opengrok.web.PageConfig;
import org.opengrok.web.api.v1.filter.CorsEnable;
import org.opengrok.web.api.v1.filter.PathAuthorized;
import org.opengrok.web.util.NoPathParameterException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.opengrok.web.util.FileUtil.toFile;

@Path(DirectoryListingController.PATH)
public class DirectoryListingController {

    public static final String PATH = "/list";

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryListingController.class);

    static class DirectoryEntryDTO implements JSONable {
        @JsonProperty
        String path;
        @JsonProperty
        Long numLines;
        @JsonProperty
        Long loc;
        @JsonProperty
        Date date;
        @JsonProperty
        String description;
        @JsonProperty
        String pathDescription;
        @JsonProperty
        boolean isDirectory;
        @JsonProperty
        Long size;

        // Needed for deserialization when testing.
        DirectoryEntryDTO() {
        }

        DirectoryEntryDTO(DirectoryEntry entry) throws ForbiddenSymlinkException, IOException {
            path = RuntimeEnvironment.getInstance().getPathRelativeToSourceRoot(entry.getFile());
            NullableNumLinesLOC extra = entry.getExtra();
            if (extra != null) {
                loc = entry.getExtra().getLOC();
                numLines = entry.getExtra().getNumLines();
            }
            date = entry.getDate();
            description = entry.getDescription();
            pathDescription = entry.getPathDescription();
            isDirectory = entry.getFile().isDirectory();
            if (isDirectory) {
                size = null;
            } else {
                size = entry.getFile().length();
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final DirectoryEntryDTO other = (DirectoryEntryDTO) obj;

            if (!Objects.equals(this.path, other.path)) {
                return false;
            }
            if (!Objects.equals(this.date, other.date)) {
                return false;
            }
            if (!Objects.equals(this.numLines, other.numLines)) {
                return false;
            }
            if (!Objects.equals(this.loc, other.loc)) {
                return false;
            }
            if (!Objects.equals(this.description, other.description)) {
                return false;
            }
            if (!Objects.equals(this.pathDescription, other.pathDescription)) {
                return false;
            }
            if (this.isDirectory != other.isDirectory) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, date, numLines, loc, description, pathDescription, isDirectory);
        }

        @Override
        public String toString() {
            return "{" + "path=" + path + ",date=" + date + ",numLines=" + numLines + ",loc=" + loc +
                    ",description=" + description + ",pathDescription=" + pathDescription +
                    ",isDirectory=" + isDirectory + "}";
        }
    }

    @VisibleForTesting
    static List<DirectoryEntryDTO> getDirectoryEntriesDTO(List<DirectoryEntry> entries) {
        List<DirectoryEntryDTO> result = new ArrayList<>(entries.size());
        for (DirectoryEntry entry : entries) {
            DirectoryEntryDTO directoryEntryDTO = null;
            try {
                directoryEntryDTO = new DirectoryEntryDTO(entry);
            } catch (IOException | ForbiddenSymlinkException e) {
                LOGGER.log(Level.WARNING, "TODO");
            }
            result.add(directoryEntryDTO);
        }
        return result;
    }

    @GET
    @CorsEnable
    @PathAuthorized
    @Produces(MediaType.APPLICATION_JSON)
    public List<DirectoryEntryDTO> getDirectoryListing(@Context HttpServletRequest request,
                                             @Context HttpServletResponse response,
                                             @QueryParam("path") final String path)
            throws IOException, NoPathParameterException, CacheException {

        File file = toFile(path);
        PageConfig cfg = PageConfig.get(path, request);
        DirectoryListing dl = new DirectoryListing();
        List<String> files = cfg.getResourceFileList();

        List<DirectoryEntry> entries = dl.createDirectoryEntries(file, path, files);

        Project project = Project.getProject(path);
        List<NullableNumLinesLOC> extras = cfg.getExtras(project, request);
        FileExtraZipper zipper = new FileExtraZipper();
        zipper.zip(entries, extras);

        return getDirectoryEntriesDTO(entries);
    }
}
