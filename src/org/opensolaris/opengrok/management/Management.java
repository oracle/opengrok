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

import java.util.Date;
import java.util.Properties;
import java.util.logging.Logger;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.opensolaris.opengrok.Info;

public final class Management implements ManagementMBean, MBeanRegistration {

    private static Management managementInstance = null;
    private final static Logger log = Logger.getLogger("org.opensolaris.opengrok");
    private Properties ogaProperties = null;
    private final long startTime; // Stores the time this bean is created
    private Boolean update = Boolean.FALSE;
    private Integer noThreads = Integer.valueOf(1);
    private String[] subFiles = new String[]{};
    private String configurationFile = null;
    private String publishHost = null;

    /**
     * The only constructor is private, so other classes will only get an
     * instance through the static factory method getInstance().
     */
    private Management(Properties ogaProperties) {
        startTime = System.currentTimeMillis();
        this.ogaProperties = ogaProperties;
            updateProperties();
    }

    private void updateProperties() {
        update = Boolean.parseBoolean(ogaProperties.getProperty("org.opensolaris.opengrok.indexer.updatedatabase"));
        noThreads = Integer.parseInt(ogaProperties.getProperty("org.opensolaris.opengrok.indexer.numberofthreads"));
        configurationFile = ogaProperties.getProperty("org.opensolaris.opengrok.configuration.file");
        String subfiles = ogaProperties.getProperty("org.opensolaris.opengrok.indexer.subfiles");
        if (subfiles != null) {
            subFiles = subfiles.split(",");
        }
        publishHost = ogaProperties.getProperty("org.opensolaris.opengrok.indexer.publishserver.url");

    }

    /**
     * Static factory method to get an instance of Management.
     * @param ogaProperties The properties to use
     * @return A management instance to use
     */
    public static synchronized Management getInstance(Properties ogaProperties) {
        if (managementInstance == null) {
            managementInstance = new Management(ogaProperties);
        }
        return managementInstance;
    }

    /**
     * Static factory method to get an instance of management.
     * Returns null if Management has not been initialized yet.
     */
    public static Management getInstance() {
        return managementInstance;
    }

    /**
     * Get a selected property from  configuration.
     * @return String with property value
     */
    public String getProperty(String key) {
        return ogaProperties.getProperty(key);
    }

    /**
     * Set a selected property in the  configuration.
     * @param key the String key for the property to be set.
     * $param value the String value for the property to be set.
     */
    public void setProperty(String key, String value) {
        if (key == null) {
            log.severe("Trying to set property with key == null");
            return;
        }
        ogaProperties.setProperty(key, value);
        saveProperties();
    }

    private void saveProperties() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public ObjectName preRegister(MBeanServer server, ObjectName name) {
        return name;
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() {
    }

    public void postDeregister() {
    }

    /**
     * Stops the  agent, so it is not restarted.
     */
    public void stop() {
        log.warning("STOPPING AGENT!");
    //WrapperManager.stop(0);
    }

    public String getSystemProperty(String key) {
        return System.getProperty(key);
    }

    public void setSystemProperty(String key, String value) {
        if (key == null) {
            log.severe("Trying to set property with key == null");
            return;
        }
        System.setProperty(key, value);
    }

    public String getAllSystemProperties() {
        return System.getProperties().toString();
    }

    public String getSystemEnvProperty(String key) {
        return System.getenv(key);
    }

    public String getAllSystemEnvProperties() {
        return System.getenv().toString();
    }

    /**
     * Get the time (in milliseconds since 1970) when the agent was started
     * @return long time when the agent was started, in milliseconds.
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Get a Date object with the time the agent was started.
     * @return Date with the starting date
     */
    public Date getStartDate() {
        return new Date(startTime);
    }

    /**
     * Get the version tag for the agent
     * @return String the version tag for this agent
     */
    public String getVersion() {
        return Info.getFullVersion();
    }

    public void setUpdateIndexDatabase(Boolean val) {
        this.update = val;
    }

    public Boolean getUpdateIndexDatabase() {
        return update;
    }

    public void setNumberOfThreads(Integer val) {
        this.noThreads = val;
    }

    public Integer getNumberOfThreads() {
        return noThreads;
    }

    public void setSubFiles(String[] sublist) {
        this.subFiles = (sublist == null) ? null : (String[]) sublist.clone();
    }

    public String[] getSubFiles() {
        return (subFiles == null) ? null : (String[]) subFiles.clone();
    }

    public String getConfigurationFile() {
        return configurationFile;
    }

    public String getPublishServerURL() {
        return publishHost;
    }
}
