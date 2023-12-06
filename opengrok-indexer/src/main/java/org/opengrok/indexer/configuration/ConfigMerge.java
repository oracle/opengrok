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
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.configuration;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import org.opengrok.indexer.util.Getopt;

/**
 * Merge 2 config files together. More precisely, take the 1st as a base and
 * set all properties from the 2nd in it.
 *
 * @author Vladimir Kotal
 */
@SuppressWarnings("java:S106")
public class ConfigMerge {

    private static final String NAME = "ConfigMerge";

    private ConfigMerge() {
    }

    /**
     * Merge base and new configuration.
     * @param cfgBase base configuration
     * @param cfgNew new configuration, will receive properties from the base configuration
     * @throws Exception exception
     */
    public static void merge(Configuration cfgBase, Configuration cfgNew) throws Exception {
        Configuration cfgDefault = new Configuration();

        // Basic strategy: take all non-static/transient fields that have a setter
        // from cfgBase that are not of default value and set them to cfgNew.
        var fields = Arrays.stream(cfgBase.getClass().getDeclaredFields())
                .filter(ConfigMerge::isCopyable)
                .collect(Collectors.toList());

        for (Field field : fields) {
            String fieldName = field.getName();
            PropertyDescriptor desc;
            try {
                desc = new PropertyDescriptor(fieldName, Configuration.class);
            } catch (IntrospectionException ex) {
                throw new Exception("cannot get property descriptor for '" + fieldName + "'");
            }

            Method setter = desc.getWriteMethod();
            if (setter == null) {
                throw new Exception("no setter for '" + fieldName + "'");
            }

            Method getter = desc.getReadMethod();
            if (getter == null) {
                throw new Exception("no getter for '" + fieldName + "'");
            }

            try {
                Object obj = getter.invoke(cfgBase);
                if (Objects.equals(obj, getter.invoke(cfgDefault))) {
                        continue;
                }
            } catch (Exception ex) {
                throw new Exception("failed to invoke getter for " + fieldName + ": " + ex);
            }

            try {
                setter.invoke(cfgNew, getter.invoke(cfgBase));
            } catch (Exception ex) {
                throw new Exception("failed to invoke setter for '" + fieldName + "'");
            }
        }
    }

    private static boolean isCopyable(Field field) {

        int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers) &&
                !Modifier.isFinal(modifiers);
    }

    public static void main(String[] argv) {

        Getopt getopt = new Getopt(argv, "h?");

        try {
            getopt.parse();
        } catch (ParseException ex) {
            System.err.println(NAME + ": " + ex.getMessage());
            bUsage(System.err);
            System.exit(1);
        }

        int cmd;
        getopt.reset();
        while ((cmd = getopt.getOpt()) != -1) {
            switch (cmd) {
                case '?':
                case 'h':
                    aUsage(System.out);
                    System.exit(0);
                    break;
                default:
                    System.err.println("Internal Error - Not implemented option: " + (char) cmd);
                    bUsage(System.err);
                    System.exit(1);
                    break;
            }
        }

        int optind = getopt.getOptind();
        if (optind < 0 || argv.length - optind != 3) {
            aUsage(System.err);
            System.exit(1);
        }

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
            System.err.println("cannot read file " + argv[optind + 1] + ":" + ex);
            System.exit(1);
        }

        try {
            merge(cfgBase, cfgNew);
        } catch (Exception ex) {
            System.err.print(ex);
            System.exit(1);
        }

        // Write the resulting XML representation to the output file.
        try (OutputStream os = new FileOutputStream(argv[optind + 2])) {
            cfgNew.encodeObject(os);
        } catch (IOException ex) {
            System.err.print(ex);
            System.exit(1);
        }
    }

    private static void aUsage(PrintStream out) {
        out.println("Usage:");
        out.println(NAME + " [-h] <config_file_base> <config_file_new> <output_file>");
        out.println();
        out.println("OPTIONS:");
        out.println("Help");
        out.println("-?                   print this help message");
        out.println("-h                   print this help message");
        out.println();
    }

    private static void bUsage(PrintStream out) {
        out.println("Maybe try to run " + NAME + " -h");
    }
}
