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
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.AnalyzerGuru;
import org.opengrok.indexer.analysis.TextAnalyzer;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.web.api.v1.filter.CorsEnable;
import org.opengrok.web.api.v1.filter.PathAuthorized;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Path(FileController.PATH)
public class FileController {

    public static final String PATH = "/file";

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private ArrayList<String> lines = new ArrayList<>();

    static class LineDTO {
        @JsonProperty
        private String line;
        @JsonProperty
        private int number;

        // for testing
        LineDTO() {
        }

        LineDTO(String line, int num) {
            this.line = line;
            this.number = num;
        }

        public String getLine() {
            return this.line;
        }

        public int getNumber() {
            return this.number;
        }
    }

    private static File getFile(String path, HttpServletResponse response) throws IOException {
        if (path == null) {
            if (response != null) {
                response.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Missing path parameter");
            }
            return null;
        }

        File file = new File(env.getSourceRootFile(), path);
        if (!file.isFile()) {
            if (response != null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
            }
            return null;
        }

        return file;
    }

    @GET
    @CorsEnable
    @PathAuthorized
    @Path("/content")
    @Produces(MediaType.APPLICATION_JSON)
    public Object getContent(@Context HttpServletRequest request,
                             @Context HttpServletResponse response,
                             @QueryParam("path") final String path) throws IOException {

        File file = getFile(path, response);
        if (file == null) {
            // error already set in the response
            return null;
        }

        try (InputStream in = new BufferedInputStream(
                new FileInputStream(file))) {
            AbstractAnalyzer fa = AnalyzerGuru.getAnalyzer(in, path);
            if (!(fa instanceof TextAnalyzer)) {
                response.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Not a text file");
                return null;
            }
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }

        int count = 1;
        List<LineDTO> linesDTO = new ArrayList<>();
        for (String line: lines) {
            LineDTO l = new LineDTO(line, count++);
            linesDTO.add(l);
        }
        return linesDTO;
    }

    @GET
    @CorsEnable
    @PathAuthorized
    @Path("/genre")
    @Produces(MediaType.TEXT_PLAIN)
    public String getGenre(@Context HttpServletRequest request,
                           @Context HttpServletResponse response,
                           @QueryParam("path") final String path) throws IOException {

        File file = getFile(path, response);
        if (file == null) {
            return null;
        }

        try (InputStream in = new BufferedInputStream(
                new FileInputStream(file))) {
            return AnalyzerGuru.getGenre(in).toString();
        }
    }
}
