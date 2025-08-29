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
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.opengrok.indexer.Metrics;
import org.opengrok.indexer.logger.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serial;
import java.util.logging.Logger;
import java.util.logging.Level;

@WebServlet("/metrics/prometheus")
public class MetricsServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 0L;

    private static final Logger logger = LoggerFactory.getLogger(MetricsServlet.class);

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) {
        try (PrintWriter pw = resp.getWriter()) {
            pw.print(Metrics.getPrometheusRegistry().scrape());
        } catch (IOException e) {
            try {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        String.format("failed to write the output data: %s", e.getMessage()));
            } catch (IOException ex) {
                logger.log(Level.WARNING, String.format("failed to send error code: %s", e.getMessage()));
            }
        }
    }
}
