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
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.json.simple.parser.ParseException;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.web.Statistics;
import org.opensolaris.opengrok.web.Util;

/**
 *
 * @author Krystof Tulinger
 */
public class StatsMessage extends Message {

    static final List<String> ALLOWED_OPTIONS = Arrays.asList(new String[]{"get", "reload", "clean"});

    @Override
    protected byte[] applyMessage(RuntimeEnvironment env) throws IOException, ParseException {
        if (getText().equalsIgnoreCase("reload")) {
            env.loadStatistics();
        } else if (getText().equalsIgnoreCase("clean")) {
            env.setStatistics(new Statistics());
        }
        return Util.statisticToJson(env.getStatistics()).toJSONString().getBytes();
    }

    @Override
    public void validate() throws Exception {
        if (getText() == null) {
            throw new Exception("The message must contain a text.");
        }
        if (!ALLOWED_OPTIONS.contains(getText())) {
            throw new Exception(
                    String.format("The message text must be one of [%s] - '%s' given",
                            ALLOWED_OPTIONS.stream().collect(Collectors.joining(",")), getText()));
        }
        super.validate();
    }
}
