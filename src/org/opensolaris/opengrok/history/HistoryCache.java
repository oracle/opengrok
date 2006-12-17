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

class HistoryCache {
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
        throws Exception
    {
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

        // TODO need a global property to turn off caching
        if (cache.exists() || (parser.isCacheable() && time > 300)) {
            // retrieving the history takes too long, cache it!
            try {
                writeCache(entries, cache);
            } catch (Exception e) {
                System.err.println("Error when writing cache file '" +
                                   cache + "':");
                e.printStackTrace();
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
        // TODO we should put the cache directory under the data root
        File parent = file.getParentFile();
        File cacheDir = new File(parent, ".ogcache");
        return new File(cacheDir, file.getName());
    }

    /**
     * Write history entries to a file.
     */
    private static void writeCache(List<HistoryEntry> entries, File file)
        throws IOException
    {
        File dir = file.getParentFile();
        if (!dir.isDirectory()) {
            if (!dir.mkdirs()) {
                System.err.println("Unable to create directory '" + dir + "'.");
            }
        }

        XMLEncoder e = new XMLEncoder(
                          new BufferedOutputStream(new FileOutputStream(file)));
        e.writeObject(entries);
        e.close();
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
