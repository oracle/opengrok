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
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.opensolaris.opengrok.util.Getopt;

public final class Messages {

    public static void main(String[] argv) {

        String type = null;
        String text = null;
        List<String> tags = new ArrayList<>();
        String className = null;
        long expire = -1;
        String server = null;
        int port = -1;

        String x;

        Getopt getopt = new Getopt(argv, "m:i:t:g:c:e:s:p:h?");

        try {
            getopt.parse();
        } catch (ParseException ex) {
            System.err.println("Messages: " + ex.getMessage());
            b_usage();
            System.exit(1);
        }

        int cmd;
        File f;
        getopt.reset();
        while ((cmd = getopt.getOpt()) != -1) {
            switch (cmd) {
                case 'm':
                    type = getopt.getOptarg();
                    break;
                case 't':
                    text = getopt.getOptarg();
                    break;
                case 'g':
                    tags.add(getopt.getOptarg());
                    break;
                case 'c':
                    className = getopt.getOptarg();
                    break;
                case 'e':
                    x = getopt.getOptarg();
                    try {
                        expire = Long.parseLong(x) * 1000;
                    } catch (NumberFormatException e) {
                        System.err.println("Cannot parse " + x + " into long");
                        b_usage();
                        System.exit(1);
                    }
                    break;
                case 's':
                    server = getopt.getOptarg();
                    break;
                case 'p':
                    x = getopt.getOptarg();
                    try {
                        port = Integer.parseInt(x);
                    } catch (NumberFormatException e) {
                        System.err.println("Cannot parse " + x + " into integer");
                        b_usage();
                        System.exit(1);
                    }
                    break;
                case 'h':
                    a_usage();
                    System.exit(0);
                    break;
                case '?':
                    a_usage();
                    System.exit(0);
                    break;
                default:
                    System.err.println("Internal Error - Not implemented option: " + (char) cmd);
                    b_usage();
                    System.exit(1);
                    break;
            }
        }

        if (type == null) {
            type = "normal";
        }

        if (className == null) {
            className = "info";
        }

        if (server == null) {
            server = "localhost";
        }

        if (port == -1) {
            port = 2424;
        }

        if (text == null) {
            System.err.println("No text given");
            b_usage();
            System.exit(3);
        }

        Message m = Message.createMessage(type);

        if (m == null) {
            System.err.println("Uknown message type " + type);
            b_usage();
            System.exit(4);
        }

        m.setText(text);
        m.setClassName(className);
        for (String tag : tags) {
            m.addTag(tag);
        }

        if (expire != -1 && expire > System.currentTimeMillis()) {
            m.setExpiration(new Date(expire));
        }

        try {
            m.write(server, port);
        } catch (IOException ex) {
            System.err.println("Cannot contact the target server");
            ex.printStackTrace(System.err);
            System.exit(5);
        }
    }

    private static final void a_usage() {
        System.err.println("Usage:");
        System.err.println("Messages.java" + " [OPTIONS] -t <text>");
        System.err.println();
        System.err.println("OPTIONS:");
        System.err.println("Help");
        System.err.println("-?                   print this help message");
        System.err.println("-h                   print this help message");
        System.err.println();
        System.err.println("Message type");
        System.err.println("-m <type>            message type (default is 'normal')");
        System.err.println();
        System.err.println("Message text");
        System.err.println("-t <text>            text of the message");
        System.err.println();
        System.err.println("Tags");
        System.err.println("-g <tag>             add a tag to the message (can be specified multiple times)");
        System.err.println();
        System.err.println("Class name");
        System.err.println("-c <class>           set the css class for the message (default is 'info')");
        System.err.println();
        System.err.println("Id");
        System.err.println("-i <id>              set the id of the message (default is auto-generated)");
        System.err.println();
        System.err.println("Expiration");
        System.err.println("-e <expire>          set the expire date int UTC timestamp (s)");
        System.err.println();
        System.err.println("Remote address");
        System.err.println("-s <remote address>  set the remote address where to send the message (default is 'localhost')");
        System.err.println("-p <port num>        set the remote port (default is '2424')");
        System.err.println();
        System.err.println();
        System.err.println("Examples");
        System.err.println("-t \"ahoj\"                                        # => send normal message without any tag");
        System.err.println("-t \"ahoj\" -g \"main\"                              # => send normal message with tag 'main'");
        System.err.println("-t \"ahoj\" -g \"main\" -c \"list-group-item-success\" # => send normal message with tag and css class name");
    }

    private static final void b_usage() {
        System.err.println("Maybe try to run Messages -h");
    }
}
