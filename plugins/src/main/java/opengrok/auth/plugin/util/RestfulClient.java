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
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package opengrok.auth.plugin.util;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple RESTful client.
 */
public class RestfulClient {
    private static final Logger LOGGER = Logger.getLogger(RestfulClient.class.getName());

    private RestfulClient() {
        // private to ensure static
    }

    /**
     * Perform HTTP PUT request.
     * @param URI URI
     * @param input JSON string contents
     * @return HTTP status or -1
     */
    public static int postIt(String URI, String input) {
        try {
            Client client = ClientBuilder.newClient();

            LOGGER.log(Level.FINEST, "sending REST POST request to {0}: {1}",
                    new Object[]{URI, input});
            Response response = client.target(URI)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(input, MediaType.APPLICATION_JSON));

            int status = response.getStatus();
            if (status != HttpServletResponse.SC_CREATED) {
                LOGGER.log(Level.WARNING, "REST request failed: HTTP error code : {0}", status);
            }

            return status;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "REST request failed", e);
            return -1;
        }
    }
}
