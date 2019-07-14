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
 * Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.history;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.primitives.Longs;
import com.oath.halodb.HaloDB;
import com.oath.halodb.HaloDBException;
import com.oath.halodb.HaloDBOptions;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Represents a logger of batches of file {@link HistoryEntry} arrays for
 * transient storage to be pieced together later file by file.
 */
class FileHistoryTemp implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileHistoryTemp.class);

    private static final ObjectMapper MAPPER;
    private static final TypeReference<HistoryEntryFixed[]> TYPE_H_E_F_ARRAY;

    private final AtomicLong counter = new AtomicLong(0);
    private Path tempDir;
    private Map<String, PointedMeta> batchPointers;
    private HaloDB batchDb;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        TYPE_H_E_F_ARRAY = new TypeReference<HistoryEntryFixed[]>() { };
    }

    /**
     * Gets the number of appended files. (Not thread safe.)
     * @return a non-negative number
     */
    int fileCount() {
        return batchDb == null ? 0 : batchPointers.size();
    }

    /**
     * Creates a temporary directory, and opens a HashDb for temporary storage
     * of history. (Not thread safe.)
     */
    public void open() throws IOException {
        if (tempDir != null) {
            throw new IllegalStateException("already open");
        }

        tempDir = Files.createTempDirectory("org_opengrok-file_history_log");
        Path batchDbPath = tempDir.resolve("batch.db");

        HaloDBOptions options = new HaloDBOptions();
        options.setMaxFileSize(64 * 1024 * 1024); // 64 MB
        options.setFlushDataSizeBytes(8 * 1024 * 1024); // 8 MB
        options.setCompactionThresholdPerFile(1.0);
        options.setCompactionJobRate(64 * 1024 * 1024); // 64 MB
        options.setNumberOfRecords(10_000_000);
        options.setCleanUpTombstonesDuringOpen(false);
        options.setCleanUpInMemoryIndexOnClose(true);
        options.setUseMemoryPool(true);
        options.setMemoryPoolChunkSize(2 * 1024 * 1024); // 2 MB
        options.setFixedKeySize(Longs.BYTES);

        try {
            batchDb = HaloDB.open(batchDbPath.toString(), options);
        } catch (HaloDBException e) {
            throw new IOException("opening temp db", e);
        }

        counter.set(0);
        batchPointers = new ConcurrentHashMap<>();
    }

    /**
     * Cleans up temporary directory. (Not thread safe.)
     */
    @Override
    public void close() throws IOException {
        if (batchPointers != null) {
            batchPointers.clear();
        }

        try {
            if (batchDb != null) {
                batchDb.close();
            }
        } catch (HaloDBException e) {
            throw new IOException("closing temp db", e);
        } finally {
            batchDb = null;
            if (tempDir != null) {
                IOUtils.removeRecursive(tempDir);
            }
        }
    }

    /**
     * Sets the specified {@code entries} as the list of entries for
     * {@code file}, which will indicate a full overwrite later. (Thread safe
     * but not guaranteeing batch order for simultaneous calls for the same
     * {@code file}.)
     * @throws IOException if an I/O error occurs writing to temp
     */
    public void set(String file, List<HistoryEntry> entries) throws IOException {
        incorporate(file, entries, true);
    }

    /**
     * Appends the specified batch of {@code entries} to the previous list of
     * entries for {@code file}. (Thread safe but not guaranteeing batch order
     * for simultaneous calls for the same {@code file}.)
     * @throws IOException if an I/O error occurs writing to temp
     */
    public void append(String file, List<HistoryEntry> entries) throws IOException {
        incorporate(file, entries, false);
    }

    private void incorporate(String file, List<HistoryEntry> entries, boolean forceOverwrite)
            throws IOException {

        if (batchDb == null) {
            throw new IllegalStateException("not open");
        }

        HistoryEntryFixed[] fixedEntries = fix(entries);
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        MAPPER.writeValue(bytesOut, fixedEntries);
        byte[] serialized = bytesOut.toByteArray();
        byte[] packed = BinPacker.pack(serialized);

        long i = counter.incrementAndGet();
        try {
            batchDb.put(Longs.toByteArray(i), packed);
        } catch (HaloDBException e) {
            throw new IOException("writing temp db", e);
        }

        final PointedMeta pointed;
        if (forceOverwrite) {
            pointed = new PointedMeta(true);
            batchPointers.put(file, pointed);
        } else {
            pointed = batchPointers.computeIfAbsent(file, k -> new PointedMeta(false));
        }

        synchronized (pointed.lock) {
            pointed.counters.add(i);
        }
    }

    /**
     * Gets an enumerator to recompose batches of entries by file into
     * {@link KeyedHistory} instances. (Not thread safe.)
     * @return a defined enumeration
     */
    Enumeration<KeyedHistory> getEnumerator() {

        if (batchDb == null) {
            throw new IllegalStateException("not open");
        }

        final Iterator<String> iterator = batchPointers.keySet().iterator();

        return new Enumeration<KeyedHistory>() {
            @Override
            public boolean hasMoreElements() {
                return iterator.hasNext();
            }

            @Override
            public KeyedHistory nextElement() {
                String file = iterator.next();
                PointedMeta pointed = batchPointers.get(file);
                List<HistoryEntry> entries;
                try {
                    entries = readEntries(pointed.counters);
                } catch (IOException e) {
                    throw new RuntimeException("readEntries()", e);
                }
                return new KeyHistoryImpl(file, entries, pointed.forceOverwrite);
            }
        };
    }

    private List<HistoryEntry> readEntries(List<Long> pointers) throws IOException {

        List<HistoryEntry> res = new ArrayList<>();
        for (long nextCounter : pointers) {
            byte[] packed;
            try {
                packed = batchDb.get(Longs.toByteArray(nextCounter));
            } catch (HaloDBException e) {
                throw new IOException("reading temp db", e);
            }
            byte[] serialized = BinPacker.unpack(packed);
            Object obj = MAPPER.readValue(serialized, TYPE_H_E_F_ARRAY);

            if (obj == null) {
                LOGGER.log(Level.SEVERE, "Unexpected null");
            } else if (obj instanceof HistoryEntryFixed[]) {
                for (HistoryEntryFixed element : (HistoryEntryFixed[]) obj) {
                    res.add(element.toEntry());
                }
            } else {
                LOGGER.log(Level.SEVERE, "Unexpected serialized type, {0}", obj.getClass());
            }
        }
        return res;
    }

    private static HistoryEntryFixed[] fix(List<HistoryEntry> entries) {
        HistoryEntryFixed[] res = new HistoryEntryFixed[entries.size()];

        int i = 0;
        for (HistoryEntry entry : entries) {
            res[i++] = new HistoryEntryFixed(entry);
        }
        return res;
    }

    private static class PointedMeta {
        final Object lock = new Object();
        final List<Long> counters = new ArrayList<>();
        final boolean forceOverwrite;

        PointedMeta(boolean forceOverwrite) {
            this.forceOverwrite = forceOverwrite;
        }
    }

    private static class KeyHistoryImpl implements KeyedHistory {
        private final String file;
        private final List<HistoryEntry> entries;
        private final boolean forceOverwrite;

        KeyHistoryImpl(String file, List<HistoryEntry> entries, boolean forceOverwrite) {
            this.file = file;
            this.entries = entries;
            this.forceOverwrite = forceOverwrite;
        }

        @Override
        public String getFile() {
            return file;
        }

        @Override
        public List<HistoryEntry> getEntries() {
            return Collections.unmodifiableList(entries);
        }

        @Override
        public boolean isForceOverwrite() {
            return forceOverwrite;
        }
    }

    private static class BinPacker {
        static final int MINIMUM_LENGTH_TO_COMPRESS = 100;

        static byte[] pack(byte[] value) {
            if (value.length < MINIMUM_LENGTH_TO_COMPRESS) {
                final byte[] packed = new byte[value.length + 1];
                packed[0] = 0; // Set 0 to indicate uncompressed.
                System.arraycopy(value, 0, packed, 1, value.length);
                return packed;
            } else {
                ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
                bytesOut.write(1); // Set 1 to indicate compressed.

                try (GZIPOutputStream gz = new GZIPOutputStream(bytesOut)) {
                    gz.write(value);
                } catch (IOException e) {
                    // Not expected to happen
                    throw new RuntimeException(e);
                }
                return bytesOut.toByteArray();
            }
        }

        static byte[] unpack(byte[] packed) {
            if (packed[0] == 0) {
                byte[] res = new byte[packed.length - 1];
                System.arraycopy(packed, 1, res, 0, packed.length - 1);
                return res;
            } else {
                ByteArrayInputStream bytesIn = new ByteArrayInputStream(packed);
                //noinspection ResultOfMethodCallIgnored
                bytesIn.read();

                ByteArrayOutputStream res = new ByteArrayOutputStream();
                try (GZIPInputStream gz = new GZIPInputStream(bytesIn)) {
                    int b;
                    while ((b = gz.read()) != -1) {
                        res.write(b);
                    }
                } catch (IOException e) {
                    // Not expected to happen
                    throw new RuntimeException(e);
                }
                return res.toByteArray();
            }
        }
    }
}
