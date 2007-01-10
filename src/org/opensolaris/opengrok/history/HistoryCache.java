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
 * Copyright 2006 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.history;

import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.beans.XMLDecoder;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

class HistoryCache {
    private static Object lock = new Object();
    
    /**
     * Retrieve the history for the given file, either from the cache or by
     * parsing the history information in the repository.
     *
     * @param file The file to retrieve history for
     * @param parserClass The class that implements the parser to use
     * @param repository The external repository to read the history from (can
     * be <code>null</code>)
     */
    static List<HistoryEntry> get(File file,
            Class<? extends HistoryParser> parserClass,
            ExternalRepository repository)
            throws Exception {
        File cache = getCachedFile(file);
        if (cache.exists() && file.lastModified() < cache.lastModified()) {
            try {
                return readCache(cache);
            } catch (Exception e) {
                System.err.println("Error when reading cache file '" +
                        cache + "':");
                e.printStackTrace();
            }
        }
        
        long time = System.currentTimeMillis();
        
        HistoryParser parser = parserClass.newInstance();
        List<HistoryEntry> entries = parser.parse(file, repository);
        
        time = System.currentTimeMillis() - time;
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (env.useHistoryCache()) {
            if (cache.exists() || (parser.isCacheable() && time > env.getHistoryReaderTimeLimit())) {
                // retrieving the history takes too long, cache it!
                try {
                    writeCache(entries, cache);
                } catch (Exception e) {
                    System.err.println("Error when writing cache file '" +
                            cache + "':");
                    e.printStackTrace();
                }
            }
        }
        
        return entries;
    }
    
    /**
     * Get a <code>File</code> object describing the cache file.
     *
     * @param file the file to find the cache for
     * @return file that might contain cached history for <code>file</code>
     */
    private static File getCachedFile(File file) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        
        StringBuilder sb = new StringBuilder();
        sb.append(env.getDataRootPath());
        sb.append(File.separatorChar);
        sb.append(".ogcache");
        
        try {
            String add = file.getCanonicalPath().substring(env.getSourceRootPath().length());
            if (add.length() == 0) {
                add = File.separator;
            }
            sb.append(add);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        
        if (file.isDirectory()) {
            sb.append(".log");
        }
        
        return new File(sb.toString());
    }
    
    /**
     * Write history entries to a file.
     */
    private static void writeCache(List<HistoryEntry> entries, File file)
    throws IOException {
        File dir = file.getParentFile();
        if (!dir.isDirectory()) {
            if (!dir.mkdirs()) {
                throw new IOException(
                    "Unable to create directory '" + dir + "'.");
            }
        }
        
        // We have a problem that multiple threads may access the cache layer
        // at the same time. Since I would like to avoid read-locking, I just
        // serialize the write access to the cache file. The generation of the
        // cache file would most likely be executed during index generation, and
        // that happens sequencial anyway....
        // Generate the file with a temporary name and move it into place when
        // I'm done so I don't have to protect the readers for partially updated
        // files...
        synchronized (lock) {
            File output = File.createTempFile("oghist", null, dir);
            XMLEncoder e = new XMLEncoder(
                    new BufferedOutputStream(new FileOutputStream(output)));
            e.writeObject(entries);
            e.close();
            if (!file.delete() && file.exists()) {
                output.delete();
                throw new IOException(
                    "Cachefile exists, and I could not delete it.");
            }
            if (!output.renameTo(file)) {
                output.delete();
                throw new IOException("Failed to rename cache tmpfile.");
            }
        }
    }
    
    /**
     * Read history entries from a file.
     */
    private static List<HistoryEntry> readCache(File file) throws IOException {
        XMLDecoder d = new XMLDecoder(
                new BufferedInputStream(new FileInputStream(file)));
        Object obj = d.readObject();
        d.close();
        // We could cast obj directly to List<HistoryEntry>, but that would
        // generate an unchecked cast warning. Copy the list instead.
        ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        for (Object o : (List) obj) {
            entries.add((HistoryEntry) o);
        }
        return entries;
    }
}
