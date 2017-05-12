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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import org.opensolaris.opengrok.util.Getopt;

/**
 * Merge 2 config files together. More precisely, take the 1st as a base and
 * set all properties from the 2nd in it.
 *
 * @author Vladimir Kotal
 */
public class ConfigMerge {

    private static final String name = "ConfigMerge";

    public static void main(String[] argv) {

        Getopt getopt = new Getopt(argv, "h?");

        try {
            getopt.parse();
        } catch (ParseException ex) {
            System.err.println(name + ": " + ex.getMessage());
            b_usage();
            System.exit(1);
        }

        int cmd;
        File f;
        getopt.reset();
        while ((cmd = getopt.getOpt()) != -1) {
            switch (cmd) {
                case '?':
                case 'h':
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

        int optind = getopt.getOptind();
        if (optind < 0 || argv.length - optind != 2) {
            a_usage();
            System.exit(1);
        }

        Configuration cfgDefault = new Configuration();

        Configuration cfgBase = null;
        try {
            cfgBase = Configuration.read(new File(argv[optind]));
        } catch (IOException ex) {
            System.err.println("cannot read base file " + argv[optind] + ":" + ex);
            System.exit(1);
        }

        Configuration cfgNew = null;
        try {
            cfgNew = Configuration.read(new File(argv[optind + 1]));
        } catch (IOException ex) {
            System.err.println("cannot read file " + argv[optind + 1]);
            System.exit(1);
        }

        // Basic strategy: take all non-static/transient fields that have a setter
        // from cfgNew that are not of default value and set them to cfgBase.
        for (Field field : cfgNew.getClass().getDeclaredFields()) {
            String fieldName = field.getName();
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) ||
                Modifier.isFinal(modifiers)) {
                continue;
            }
            PropertyDescriptor desc = null;
            try {
                desc = new PropertyDescriptor(fieldName, Configuration.class);
            } catch (IntrospectionException ex) {
                System.err.println("cannot get property descriptor for '" + fieldName + "'");
                System.exit(1);
            }

            Method setter = desc.getWriteMethod();
            if (setter == null) {
                System.err.println("no setter for '" + fieldName + "'");
                System.exit(1);
            }

            Method getter = desc.getReadMethod();
            if (getter == null) {
                System.err.println("no getter for '" + fieldName + "'");
                System.exit(1);
            }

            try {
                Object obj = getter.invoke(cfgNew);
                if ((obj == null && getter.invoke(cfgDefault) == null) ||
                    obj.equals(getter.invoke(cfgDefault))) {
                        continue;
                }
            } catch (Exception ex) {
                System.err.println("failed to invoke getter for " + fieldName + ": " + ex);
                System.exit(1);
            }

            try {
                setter.invoke(cfgBase, getter.invoke(cfgNew));
            } catch (Exception ex) {
                System.err.println("failed to invoke setter for '" + fieldName + "'");
                System.exit(1);
            }
        }

        // Write the resulting XML representation to standard output.
        OutputStream os = System.out;
        cfgBase.encodeObject(os);
    }

    private static final void a_usage() {
        System.err.println("Usage:");
        System.err.println(name + " [-h] <config_file_base> <config_file_new>");
        System.err.println();
        System.err.println("OPTIONS:");
        System.err.println("Help");
        System.err.println("-?                   print this help message");
        System.err.println("-h                   print this help message");
        System.err.println();
    }

    private static final void b_usage() {
        System.err.println("Maybe try to run " + name + " -h");
    }
}
