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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.management;

import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;

/**
 * @author Jan S Berg
 */
public interface ManagementMBean {

    /**
     * Stops the agent, so it is not restarted.
     */
    public void stop();

    /**
     * Get the xml based configuration file
     * @return String with the file path and name for xml configuration
     */
    public String getConfigurationFile();

    /**
     * Set a new name for the xml based configurationfile
     * @param filename String name with full path of configurationfile
     */
     public void setConfigurationFile(String filename);

    /**
     * Get the URL to the Publish Server we want to
     * publish the indexed data
     * @return String URL to the server (hostname:port)
     */
    public String getPublishServerURL();

    /**
     * Set the URL to where to publish opengrok indexed data
     * @param url String to server for publishing the opengrok.war file
     */
    public void setPublishServerURL(String url);

    /**
     * Set update index database property
     * @param val Boolean true to also run update index database
     */
    public void setUpdateIndexDatabase(Boolean val);

    /**
     * Get the udate database property
     * @return Boolean returns true if update index database is switched on
     */
    public Boolean getUpdateIndexDatabase();

    /**
     * Set number of Threads to use for indexing
     */
    public void setNumberOfThreads(Integer val);

    /**
     * Get number of Threads to use for indexing
     */
    public Integer getNumberOfThreads();

    /**
     * Set subfiles
     * @param sublist
     */
    public void setSubFiles(String[] sublist);

    /**
     * Get subfiles
     * @return get StringArray of subfiles to run through
     */
    public String[] getSubFiles();

    /**
     * Get a selected property from JAG configuration.
     * @return String with property value
     */
    public String getProperty(String key);

    /**
     * Set a selected property in the JAG configuration.
     * JAG will immediately save its configuration to file when a
     * property is set.
     * @param key the String key for the property to be set.
     * $param value the String value for the property to be set.
     */
    public void setProperty(String key, String value);

    /**
     * Get the selected System property
     * @return String with property value
     */
    public String getSystemProperty(String key);

    /**
     * Set a selected System property
     * @param key the String key for the property to be set.
     * $param value the String value for the property to be set.
     */
    public void setSystemProperty(String key, String value);

    /**
     * Get the selected Environment property
     * @return String with Environment property value
     */
    public String getSystemEnvProperty(String key);

    /**
     * Get the time (in milliseconds since 1970) when the agent was started
     * @return long time when the agent was started, in milliseconds.
     */
    public long getStartTime();

    /**
     * Get a Date object with the time the agent was started.
     * @return Date with the starting date
     */
    public Date getStartDate();

    /**
     * Get the version tag for the agent
     * @return String the version tag for this agent
     */
    public String getVersion();

    /**
     * Set the loglevel for the console
     * @param level the level for console logging
     */
    public void setConsoleLogLevel(Level level);

    /**
     * Get the current loglevel for the console
     * @return Level current console loglevel
     */
    public Level getConsoleLogLevel();

    /**
     * set the file log level
     * @param level the level for file logging
     */
    public void setFileLogLevel(Level level);

    /**
     * get the loglevel on the agent logfile
     * @return Level for file logging
     */
    public Level getFileLogLevel();

    /**
     * Set the path for the file logging
     * @param path String path to where to log
     * @throws IOException if path does not exist or could be created on server
     */
    public void setFileLogPath(String path) throws IOException;

    /**
     * Get the file logpath
     * @return String file log path
     */
    public String getFileLogPath();
}
