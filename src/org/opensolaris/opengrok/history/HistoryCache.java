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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.history;

import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.beans.XMLDecoder;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
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
    static History get(File file,
            Class<? extends HistoryParser> parserClass,
            ExternalRepository repository)
            throws Exception {
        File cache = getCachedFile(file);
        boolean hasCache = (cache != null) && cache.exists();
        if (hasCache && file.lastModified() < cache.lastModified()) {
            try {
                return readCache(cache);
            } catch (Exception e) {
                System.err.println("Error when reading cache file '" +
                        cache + "':");
                e.printStackTrace();
            }
        }
        
        
        HistoryParser parser = parserClass.newInstance();
        History history = null;
        long time;
        try {
            time = System.currentTimeMillis();
            history = parser.parse(file, repository);
            time = System.currentTimeMillis() - time;
        } catch (Exception e) {
            System.err.println("Failed to parse " + file.getAbsolutePath());
            e.printStackTrace();
            throw e;
        }
        
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (env.useHistoryCache()) {
            if ((cache != null) &&
                    (cache.exists() ||
                         (parser.isCacheable() &&
                              (time > env.getHistoryReaderTimeLimit())))) {
                // retrieving the history takes too long, cache it!
                try {
                    writeCache(history, cache);
                } catch (Exception e) {
                    System.err.println("Error when writing cache file '" +
                            cache + "':");
                    e.printStackTrace();
                }
            }
        }
        
        return history;
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
        sb.append("historycache");
        
        String sourceRoot = env.getSourceRootPath();
        if (sourceRoot == null) {
            return null;
        }

        try {
            String add = file.getCanonicalPath().substring(sourceRoot.length());
            if (add.length() == 0) {
                add = File.separator;
            }
            sb.append(add);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        
        sb.append(".log");
        
        return new File(sb.toString());
    }
    
    /**
     * Write history to a file.
     */
    private static void writeCache(History history, File file)
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
            e.writeObject(history);
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
     * Read history from a file.
     */
    private static History readCache(File file) throws IOException {
        XMLDecoder d = new XMLDecoder(
                new BufferedInputStream(new FileInputStream(file)));
        Object obj = d.readObject();
        d.close();
        return (History) obj;
    }
    
    /**
     * Store a history object to file in the same format as writeCache, but
     * this method does not try to synchonize access (so this function must
     * not be called on the same file from multiple threads). It's intended
     * usage is from the ExternalRepository's createCache.
     * @param name the name of the file (relative from source root)
     * @param history the history for this file
     * @throws IOException if an error occurs
     */
    protected static void writeCacheFile(String name, History history) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(RuntimeEnvironment.getInstance().getDataRootPath());
        sb.append(File.separatorChar);
        sb.append("historycache");
        sb.append(File.separatorChar);
        sb.append(name);
        sb.append(".log");
        
        File file = new File(sb.toString());
        File dir = file.getParentFile();
        if (!dir.isDirectory()) {
            if (!dir.mkdirs()) {
                throw new IOException(
                        "Unable to create directory '" + dir + "'.");
            }
        }

        XMLEncoder e = new XMLEncoder(
                new BufferedOutputStream(new FileOutputStream(file)));
        e.writeObject(history);
        e.close();
    }
}
