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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.RepositoryInfo;

/**
 * repository specific message
 *
 * @author Vladimir Kotal
 */
public class RepositoryMessage extends Message {
    
    @Override
    protected byte[] applyMessage(RuntimeEnvironment env) throws Exception {
        String ret = null;
        String msgtext = getText();
        
        switch (msgtext) {
            case "get-repo-type":
                List<String> types = new ArrayList<>(16);
                
                for (String tag: getTags()) {
                    boolean found = false;
                    for (RepositoryInfo ri : env.getRepositories()) {
                        if (ri.getDirectoryNameRelative().equals(tag)) {
                            types.add(tag + ":" + ri.getType());
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        types.add(tag + ":N/A");
                    }
                }
                ret = types.stream().collect(Collectors.joining("\n"));
                break;
        }
        
        return ret.getBytes();
    }

    /**
     * Validate the message.
     * Tag is repository path, text is command.
     * @throws Exception exception
     */
    @Override
    public void validate() throws Exception {
        String command = getText();
        Set<String> allowedTexts = new TreeSet<>(Arrays.asList("get-repo-type"));

        // The text field carries the command.
        if (command == null) {
            throw new Exception("The message text must contain one of '" + allowedTexts.toString() + "'");
        }
        if (!allowedTexts.contains(command)) {
            throw new Exception("The message text must contain one of '" + allowedTexts.toString() + "'");
        }

        if (getTags().size() == 0) {
            throw new Exception("All repository messages must have at least one tag");
        }
        
        super.validate();
    }
}
