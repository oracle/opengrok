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

package org.opensolaris.opengrok.management.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import org.opensolaris.opengrok.management.OGAgent;

/**
 *
 * @author Jan S Berg
 */
public class SettingsPersistence {

    public final static String HOST = "org.opensolaris.opengrok.management.connection.host";
    public final static String JMXPORT = "org.opensolaris.opengrok.management.connection.jmxmp.port";
    public final static String INDEXTIMEOUTKEY = "org.opensolaris.opengrok.management.indextimeout";
    public final static String CONNECTIONTIMEOUTKEY = "org.opensolaris.opengrok.management.connectiontimeout";
    public final static String LOGGINGPATHKEY = "org.opensolaris.opengrok.management.logging.path";
    public final static String FILELOGLEVELKEY = "org.opensolaris.opengrok.management.logging.filelevel";
    public final static String CONSOLELOGLEVELKEY = "org.opensolaris.opengrok.management.logging.consolelevel";
    private Properties ogcProperties = new Properties();
    private File propertyFile;
    private boolean existingSettings = false;

    /**
     * Get settings for the OpenGrok Management Agent Client
     * Try if a config file has been given
     * if not set, try default settings (oga.properties in management directory)
     * @throws java.io.IOException
     */
    public SettingsPersistence(String cfgfile) throws IOException {
        InputStream in = OGAgent.class.getResourceAsStream("oga.properties");
        if (in != null) {
            ogcProperties.load(in);
            in.close();
        }
        if (cfgfile != null) {
            propertyFile = new File(cfgfile);
            FileInputStream is = new FileInputStream(propertyFile);
            ogcProperties.load(is);
            is.close();
            existingSettings = true;
        }
    }

    public String getAgentUrl() {
        return ogcProperties.getProperty(HOST) + ":" + ogcProperties.getProperty(JMXPORT);
    }

    public boolean hasExistingSettings() {
        return existingSettings;
    }

    public String getProperty(String key) {
        return ogcProperties.getProperty(key);
    }

    public void setProperty(String key, String val) {
        ogcProperties.setProperty(key, val);
    }

    public Level getFileLogLevel() {
        return Level.parse(ogcProperties.getProperty(FILELOGLEVELKEY));
    }

    public Level getConsoleLogLevel() {
        return Level.parse(ogcProperties.getProperty(CONSOLELOGLEVELKEY));
    }
}
