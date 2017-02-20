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
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.io.IOException;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 *
 * @author Vladimir Kotal
 */
public class ConfigMessage extends Message {

    @Override
    protected byte[] applyMessage(RuntimeEnvironment env) throws IOException {
        if (hasTag("getconf")) {
            return env.getConfiguration().getXMLRepresentationAsString().getBytes();
        } else if (hasTag("setconf")) {
            env.applyConfig(this, hasTag("reindex"));
        }

        return null;
    }

    @Override
    public void validate() throws Exception {
        if (hasTag("setconf")) {
            if (getText() == null) {
                throw new Exception("The setconf message must contain a text.");
            }
        } else if (hasTag("getconf")) {
            if (getText() !=  null) {
                throw new Exception("The getconf message should not contain a text.");
            }
            if (getTags().size() != 1) {
                throw new Exception("The getconf message should be the only tag.");
            }
        } else {
            throw new Exception("The message tag must be either setconf or getconf");
        }

        if (hasTag("setconf") && hasTag("getconf")) {
            throw new Exception("The message tag must be either setconf or getconf");
        }

        super.validate();
    }
}
