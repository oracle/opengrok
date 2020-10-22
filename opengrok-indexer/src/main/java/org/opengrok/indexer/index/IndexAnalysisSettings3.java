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
 * Copyright (c) 2018, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a serializable gathering of some top-level metadata concerning the
 * operation of {@link IndexDatabase} -- and persisted therein too -- which are
 * re-compared upon each indexing run since changes to them might require
 * re-indexing particular files or in certain cases all files.
 */
public final class IndexAnalysisSettings3 implements Serializable {

    private static final long serialVersionUID = -4700122690707062276L;

    private String projectName;

    /**
     * Nullable to allow easing this object into existing OpenGrok indexes
     * without forcing a re-indexing.
     * @serial
     */
    private Integer tabSize;

    /**
     * Nullable to allow easing this object into existing OpenGrok indexes
     * without forcing a re-indexing.
     * @serial
     */
    private Long analyzerGuruVersion;

    /**
     * Nullable because otherwise custom de-serialization does not work, as a
     * {@code final} initialized value may not actually happen because Java
     * de-serialization circumvents normal construction.
     * @serial
     */
    private Map<String, Long> analyzersVersions = new HashMap<>();

    /**
     * Nullable because otherwise custom de-serialization does not work, as a
     * {@code final} initialized value may not actually happen because Java
     * de-serialization circumvents normal construction. We don't bother with
     * anything but a simple {@link HashMap} here.
     * @serial
     */
    private Map<String, IndexedSymlink> indexedSymlinks = new HashMap<>();

    /**
     * Gets the project name to be used to distinguish different instances of
     * {@link IndexAnalysisSettings3} that might be returned by a Lucene
     * {@code MultiReader} search across projects.
     * @return projectName
     */
    public String getProjectName() {
        return projectName;
    }

    /**
     * Sets the project name to be used to distinguish different instances of
     * {@link IndexAnalysisSettings3} that might be returned by a Lucene
     * {@code MultiReader} search across projects.
     * @param value project name
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
     * Gets the version number for the specified file type name if it exists.
     * @param fileTypeName name of the file type
     * @return a defined value or {@code null} if unknown
     */
    public Long getAnalyzerVersion(String fileTypeName) {
        return analyzersVersions.get(fileTypeName);
    }

    /**
     * Gets an unmodifiable view of the map of file type names to version
     * numbers.
     * @return a defined instance
     */
    public Map<String, Long> getAnalyzersVersions() {
        return Collections.unmodifiableMap(analyzersVersions);
    }

    /**
     * Replaces the contents of the instance's map with the {@code values}.
     * @param values a defined instance
     */
    public void setAnalyzersVersions(Map<String, Long> values) {
        analyzersVersions.clear();
        analyzersVersions.putAll(values);
    }

    /**
     * Gets an unmodifiable view of the map of canonical file names to symlinks.
     * @return a defined instance
     */
    public Map<String, IndexedSymlink> getIndexedSymlinks() {
        return Collections.unmodifiableMap(indexedSymlinks);
    }

    /**
     * Replaces the contents of the instance's map with the {@code values}.
     * @param values a defined instance
     */
    public void setIndexedSymlinks(Map<String, IndexedSymlink> values) {
        indexedSymlinks.clear();
        indexedSymlinks.putAll(values);
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
     * De-serializes a binary representation of an {@link IndexAnalysisSettings3}
     * object.
     * @param bytes a byte array containing the serialization
     * @return a defined instance
     * @throws IOException Any of the usual Input/Output related exceptions.
     * @throws ClassNotFoundException Class of a serialized object cannot be
     * found.
     * @throws ClassCastException if the array contains an object of another
     * type than {@code IndexAnalysisSettings}
     */
    public static IndexAnalysisSettings3 deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        ObjectInputStream in = new ObjectInputStream(
            new ByteArrayInputStream(bytes));
        return (IndexAnalysisSettings3) in.readObject();
    }

    @SuppressWarnings("Duplicates")
    private void readObject(ObjectInputStream in) throws ClassNotFoundException,
            IOException {

        boolean hasValue = in.readBoolean();
        String vString = in.readUTF();
        projectName = hasValue ? vString : null;

        hasValue = in.readBoolean();
        int vInteger = in.readInt();
        tabSize = hasValue ? vInteger : null;

        hasValue = in.readBoolean();
        long vLong = in.readLong();
        analyzerGuruVersion = hasValue ? vLong : null;

        /*
         * De-serialization circumvents normal construction, so the following
         * field could be null.
         */
        if (analyzersVersions == null) {
            analyzersVersions = new HashMap<>();
        }
        int analyzerCount = in.readInt();
        for (int i = 0; i < analyzerCount; ++i) {
            vString = in.readUTF();
            vLong = in.readLong();
            analyzersVersions.put(vString, vLong);
        }

        /*
         * De-serialization circumvents normal construction, so the following
         * field could be null.
         */
        if (indexedSymlinks == null) {
            indexedSymlinks = new HashMap<>();
        }
        int symlinkCount = in.readInt();
        for (int i = 0; i < symlinkCount; ++i) {
            String absolute = in.readUTF();
            String canonical = in.readUTF();
            boolean isLocal = in.readBoolean();
            IndexedSymlink indexed = new IndexedSymlink(absolute, canonical, isLocal);
            indexedSymlinks.put(canonical, indexed);
        }
    }

    @SuppressWarnings("Duplicates")
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeBoolean(projectName != null); // hasValue
        out.writeUTF(projectName == null ? "" : projectName);

        out.writeBoolean(tabSize != null); // hasValue
        out.writeInt(tabSize == null ? 0 : tabSize);

        out.writeBoolean(analyzerGuruVersion != null); // hasValue
        out.writeLong(analyzerGuruVersion == null ? 0 : analyzerGuruVersion);

        int collectionCount = analyzersVersions.size();
        out.writeInt(collectionCount);
        for (Map.Entry<String, Long> entry : analyzersVersions.entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeLong(entry.getValue());
            --collectionCount;
        }
        if (collectionCount != 0) {
            throw new IllegalStateException("analyzersVersions were modified");
        }

        collectionCount = indexedSymlinks.size();
        out.writeInt(collectionCount);
        for (IndexedSymlink entry : indexedSymlinks.values()) {
            out.writeUTF(entry.getAbsolute());
            out.writeUTF(entry.getCanonical());
            out.writeBoolean(entry.isLocal());
            --collectionCount;
        }
        if (collectionCount != 0) {
            throw new IllegalStateException("indexedSymlinks were modified");
        }
    }
}
