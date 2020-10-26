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
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.configuration;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import opengrok.auth.plugin.ldap.LdapServer;
import opengrok.auth.plugin.util.WebHooks;

/**
 * Encapsulates configuration for LDAP plugins.
 */
public class Configuration implements Serializable {

    private static final long serialVersionUID = -1;

    private List<LdapServer> servers = new ArrayList<>();
    private int interval;
    private String searchBase;
    private WebHooks webHooks;
    private int searchTimeout;
    private int connectTimeout;
    private int readTimeout;
    private int countLimit;

    public void setServers(List<LdapServer> servers) {
        this.servers = servers;
    }

    public List<LdapServer> getServers() {
        return servers;
    }

    public void setWebHooks(WebHooks webHooks) {
        this.webHooks = webHooks;
    }

    public WebHooks getWebHooks() {
        return webHooks;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public int getSearchTimeout() {
        return this.searchTimeout;
    }

    public void setSearchTimeout(int timeout) {
        this.searchTimeout = timeout;
    }

    public int getConnectTimeout() {
        return this.connectTimeout;
    }

    public void setConnectTimeout(int timeout) {
        this.connectTimeout = timeout;
    }

    public int getReadTimeout() {
        return this.readTimeout;
    }

    public void setReadTimeout(int timeout) {
        this.readTimeout = timeout;
    }

    public int getCountLimit() {
        return this.countLimit;
    }

    public void setCountLimit(int limit) {
        this.countLimit = limit;
    }

    public String getSearchBase() {
        return searchBase;
    }
    
    public void setSearchBase(String base) {
        this.searchBase = base;
    }
    
    public String getXMLRepresentationAsString() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        this.encodeObject(bos);
        return bos.toString();
    }

    private void encodeObject(OutputStream out) {
        try (XMLEncoder e = new XMLEncoder(new BufferedOutputStream(out))) {
            e.writeObject(this);
        }
    }

    /**
     * Read a configuration from a file in XML format.
     *
     * @param file input file
     * @return the new configuration object
     * @throws IOException if any error occurs
     */
    public static Configuration read(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            return decodeObject(in);
        }
    }

    /**
     * Read a configuration from a string in xml format.
     *
     * @param xmlconfig input string
     * @return the new configuration object
     * @throws IOException if any error occurs
     */
    public static Configuration makeXMLStringAsConfiguration(String xmlconfig) throws IOException {
        final Configuration ret;
        final ByteArrayInputStream in = new ByteArrayInputStream(xmlconfig.getBytes());
        ret = decodeObject(in);
        return ret;
    }

    private static Configuration decodeObject(InputStream in) throws IOException {
        final Object ret;

        try (XMLDecoder d = new XMLDecoder(new BufferedInputStream(in), null, null, Configuration.class.getClassLoader())) {
            ret = d.readObject();
        }

        if (!(ret instanceof Configuration)) {
            throw new IOException("Not a valid configuration file");
        }

        return (Configuration) ret;
    }
}
