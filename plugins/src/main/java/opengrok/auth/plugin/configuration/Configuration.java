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
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.configuration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import opengrok.auth.plugin.ldap.LdapServer;
import opengrok.auth.plugin.util.WebHooks;

/**
 * Encapsulates configuration for LDAP plugins.
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        creatorVisibility = JsonAutoDetect.Visibility.NONE
)
public class Configuration implements Serializable {

    private static final long serialVersionUID = -1;

    @JsonProperty
    private List<LdapServer> servers = new ArrayList<>();
    @JsonProperty
    private int interval;
    @JsonProperty
    private String searchBase;
    @JsonProperty
    private WebHooks webHooks;
    @JsonProperty
    private int searchTimeout;
    @JsonProperty
    private int connectTimeout;
    @JsonProperty
    private int readTimeout;
    @JsonProperty
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

    String getObjectRepresentationAsString() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        this.encodeObject(bos);
        return bos.toString();
    }

    void encodeObject(OutputStream out) throws IOException {
        var mapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        mapper.writeValue(out, this);
    }

    /**
     * Read a configuration from a file.
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

    static Configuration decodeObject(InputStream in) throws IOException {
        var mapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        return mapper.readValue(in, Configuration.class);
    }
}
