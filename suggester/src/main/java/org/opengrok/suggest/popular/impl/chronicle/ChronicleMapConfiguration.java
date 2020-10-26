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
package org.opengrok.suggest.popular.impl.chronicle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link net.openhft.chronicle.map.ChronicleMap} does not have the configuration stored in the file. Therefore,
 * other means were necessary to remember the configuration.
 */
public class ChronicleMapConfiguration implements Serializable {

    private static final long serialVersionUID = -18408710536443827L;

    private static final Logger logger = Logger.getLogger(ChronicleMapConfiguration.class.getName());

    private static final String FILE_NAME_SUFFIX = "_map.cfg";

    private int entries;

    private double averageKeySize;

    public ChronicleMapConfiguration(final int entries, final double averageKeySize) {
        this.entries = entries;
        this.averageKeySize = averageKeySize;
    }

    public int getEntries() {
        return entries;
    }

    public double getAverageKeySize() {
        return averageKeySize;
    }

    public void setEntries(int entries) {
        this.entries = entries;
    }

    public void setAverageKeySize(double averageKeySize) {
        this.averageKeySize = averageKeySize;
    }

    /**
     * Stores this into a file.
     * @param dir directory where to store the file
     * @param field field this configuration is for
     */
    public void save(final Path dir, final String field) {
        try (FileOutputStream fos = new FileOutputStream(getFile(dir, field));
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(this);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save chronicle map configuration", e);
        }
    }

    /**
     * Loads configuration from file.
     * @param dir directory from which to load the configuration
     * @param field field this configuration is for
     * @return loaded configuration or {@code null} if this configuration could not be loaded
     */
    public static ChronicleMapConfiguration load(final Path dir, final String field) {
        File f = getFile(dir, field);
        if (!f.exists()) {
            return null;
        }
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            return (ChronicleMapConfiguration) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.log(Level.SEVERE, "Could not load chronicle map configuration", e);
        }
        return null;
    }

    private static File getFile(final Path dir, final String field) {
        return dir.resolve(field + FILE_NAME_SUFFIX).toFile();
    }

}
