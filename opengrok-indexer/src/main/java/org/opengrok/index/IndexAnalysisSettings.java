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
 * Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.index;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Represents a serializable gathering of some top-level metadata concerning the
 * operation of {@link IndexDatabase} -- and persisted therein too -- which are
 * re-compared upon each indexing run since changes to them might require
 * re-indexing particular files or in certain cases all files.
 */
public final class IndexAnalysisSettings implements Serializable {

    private static final long serialVersionUID = 1005610724146719938L;

    private String projectName;

    /**
     * (nullable to allow easing this object into existing OpenGrok indexes
     * without forcing a re-indexing)
     * @serial
     */
    private Integer tabSize;

    /**
     * (nullable to allow easing this object into existing OpenGrok indexes
     * without forcing a re-indexing)
     * @serial
     */
    private Long analyzerGuruVersion;

    /**
     * Gets the project name to be used to distinguish different instances of
     * {@link IndexAnalysisSettings} that might be returned by a Lucene
     * {@code MultiReader} search across projects.
     * @return projectName
     */
    public String getProjectName() {
        return projectName;
    }

    /**
     * Sets the project name to be used to distinguish different instances of
     * {@link IndexAnalysisSettings} that might be returned by a Lucene
     * {@code MultiReader} search across projects.
     * @param value
     */
    public void setProjectName(String value) {
        this.projectName = value;
    }

    public Integer getTabSize() {
        return tabSize;
    }

    public void setTabSize(Integer value) {
        this.tabSize = value;
    }

    public Long getAnalyzerGuruVersion() {
        return analyzerGuruVersion;
    }

    public void setAnalyzerGuruVersion(Long value) {
        this.analyzerGuruVersion = value;
    }

    /**
     * Creates a binary representation of this object.
     * @return a byte array representing this object
     * @throws  IOException Any exception thrown by the underlying
     * OutputStream.
     */
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(this);
        return bytes.toByteArray();
    }

    /**
     * De-serializes a binary representation of an {@link IndexAnalysisSettings}
     * object.
     * @param bytes a byte array containing the serialization
     * @return a defined instance
     * @throws IOException Any of the usual Input/Output related exceptions.
     * @throws ClassNotFoundException Class of a serialized object cannot be
     * found.
     * @throws ClassCastException if the array contains an object of another
     * type than {@code IndexAnalysisSettings}
     */
    public static IndexAnalysisSettings deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        ObjectInputStream in = new ObjectInputStream(
            new ByteArrayInputStream(bytes));
        return (IndexAnalysisSettings)in.readObject();
    }

    private void readObject(ObjectInputStream in) throws ClassNotFoundException,
            IOException {

        boolean hasValue = in.readBoolean();
        String vstring = in.readUTF();
        projectName = hasValue ? vstring : null;

        hasValue = in.readBoolean();
        int vint = in.readInt();
        tabSize = hasValue ? vint : null;

        hasValue = in.readBoolean();
        long vlong = in.readLong();
        analyzerGuruVersion = hasValue ? vlong : null;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeBoolean(projectName != null); // hasValue
        out.writeUTF(projectName == null ? "" : projectName);

        out.writeBoolean(tabSize != null); // hasValue
        out.writeInt(tabSize == null ? 0 : tabSize);

        out.writeBoolean(analyzerGuruVersion != null); // hasValue
        out.writeLong(analyzerGuruVersion == null ? 0 : analyzerGuruVersion);
    }
}
