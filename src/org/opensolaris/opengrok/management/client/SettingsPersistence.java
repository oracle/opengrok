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
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.management.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import org.opensolaris.opengrok.management.Constants;
import org.opensolaris.opengrok.management.OGAgent;

/**
 *
 * @author Jan S Berg
 */
public class SettingsPersistence {

    public final static String HOST = Constants.JMX_HOST;
    public final static String JMXPORT = Constants.JMX_PORT;
    public final static String RMIPORT = Constants.RMI_PORT;
    public final static String JMXURL = Constants.JMX_URL;
    public final static String INDEXTIMEOUTKEY = "org.opensolaris.opengrok.management.indextimeout";
    public final static String CONNECTIONTIMEOUTKEY = "org.opensolaris.opengrok.management.connectiontimeout";
    public final static String LOGGINGPATHKEY = Constants.LOG_PATH;
    public final static String FILELOGLEVELKEY = "org.opensolaris.opengrok.management.logging.filelevel";
    public final static String CONSOLELOGLEVELKEY = "org.opensolaris.opengrok.management.logging.consolelevel";
    private final Properties ogcProperties = new Properties();
    private File propertyFile;
    private boolean existingSettings = false; //NOPMD we do change it, but in deep finally block

    /**
     * Get settings for the OpenGrok Management Agent Client
     * Try if a config file has been given
     * if not set, try default settings (oga.properties in management directory)
     * @throws java.io.IOException
     */
    public SettingsPersistence(String cfgfile) throws IOException {
        InputStream in=null;
        try {
        in = OGAgent.class.getResourceAsStream("oga.properties");
        if (in != null) {
            ogcProperties.load(in);
            in.close();
            }
        } catch (IOException ioe) { //NOPMD
          throw ioe; //do we need to propagate this up ?
        } finally {
            if (in != null) {
                in.close(); // this will be just thrown up
            }
        }

        if (cfgfile != null) {
            propertyFile = new File(cfgfile);
            FileInputStream is=null;
            try {
            is = new FileInputStream(propertyFile);
            ogcProperties.load(is);
            } catch (IOException ioe) { //NOPMD
              throw ioe; //do we need to propagate this up ?
            } finally { //NOPMD
              try {
              if (is != null) {
                  is.close();
              }
              } catch (IOException ioe) { //NOPMD
                throw ioe; 
              } finally {
              existingSettings = true;
              }
            }
        }
    }

    /**
     * Get the JMX URL to the agent. Generate a URL from host and port
     * properties if no URL has been specified.
     *
     * @return the URL to the agent
     */
    public String getAgentUrl() {
        String url = ogcProperties.getProperty(JMXURL);
        if (url == null) {
            String host = ogcProperties.getProperty(HOST, "localhost");
            int jmxport = Integer.valueOf(
                    ogcProperties.getProperty(JMXPORT, "9292"));
            int rmiport = Integer.valueOf(ogcProperties.getProperty(
                    RMIPORT, String.valueOf(jmxport + 1)));
            url = "service:jmx:rmi://" + host + ":" + jmxport +
                    "/jndi/rmi://" + host + ":" + rmiport + "/opengrok";
        }
        return url;
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
