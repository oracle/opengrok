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
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.ldap;

import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

public class LdapServer {

    private static final Logger LOGGER = Logger.getLogger(LdapServer.class.getName());

    private static final String LDAP_TIMEOUT_PARAMETER = "com.sun.jndi.ldap.connect.timeout";
    private static final String LDAP_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    /**
     * Timeout for connecting.
     */
    private static final int LDAP_TIMEOUT = 5000; // ms

    private String url;
    private String username;
    private String password;
    private int timeout;
    private int interval = 10 * 1000;

    private Hashtable<String, String> env;
    private LdapContext ctx;
    private long errorTimestamp = 0;

    public LdapServer() {
        this(prepareEnv());
    }

    public LdapServer(String server) {
        this(prepareEnv());
        this.url = server;
    }

    public LdapServer(Hashtable<String, String> env) {
        this.env = env;
    }

    public String getUrl() {
        return url;
    }

    public LdapServer setName(String name) {
        this.url = name;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public LdapServer setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public LdapServer setPassword(String password) {
        this.password = password;
        return this;
    }

    public int getTimeout() {
        return timeout;
    }

    public LdapServer setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    /**
     * The LDAP server is working only when its connection is not null. This
     * tries to establish the connection if it is not established already.
     *
     * @return true if it is working
     */
    public synchronized boolean isWorking() {
        if (ctx == null) {
            ctx = connect();
        }
        return ctx != null;
    }

    /**
     * Connects to the LDAP server.
     *
     * @return the new connection or null
     */
    private synchronized LdapContext connect() {
        LOGGER.log(Level.INFO, "Server {0} connecting", this.url);

        if (errorTimestamp > 0 && errorTimestamp + interval > System.currentTimeMillis()) {
            LOGGER.log(Level.INFO, "Server {0} is down", this.url);
            close();
            return null;
        }

        if (ctx == null) {
            try {
                env.put(Context.PROVIDER_URL, this.url);

                if (this.username != null) {
                    env.put(Context.SECURITY_PRINCIPAL, this.username);
                }
                if (this.password != null) {
                    env.put(Context.SECURITY_CREDENTIALS, this.password);
                }
                if (this.timeout > 0) {
                    env.put(LDAP_TIMEOUT_PARAMETER, Integer.toString(this.timeout));
                }

                ctx = new InitialLdapContext(env, null);
                ctx.reconnect(null);
                ctx.setRequestControls(null);
                LOGGER.log(Level.INFO, "Connected to server {0}", env.get(Context.PROVIDER_URL));
                errorTimestamp = 0;
            } catch (NamingException ex) {
                LOGGER.log(Level.INFO, "Server {0} is not responding", env.get(Context.PROVIDER_URL));
                errorTimestamp = System.currentTimeMillis();
                close();
                return ctx = null;
            }
        }
        return ctx;
    }

    /**
     * Lookups the LDAP server.
     *
     * @param name base dn for the search
     * @param filter LDAP filter
     * @param cons controls for the LDAP request
     * @return LDAP enumeration with the results
     *
     * @throws NamingException naming exception
     */
    public NamingEnumeration<SearchResult> search(String name, String filter, SearchControls cons) throws NamingException {
        return search(name, filter, cons, false);
    }

    /**
     * Lookups the LDAP server.
     *
     * @param name base dn for the search
     * @param filter LDAP filter
     * @param controls controls for the LDAP request
     * @param reconnected flag if the request has failed previously
     * @return LDAP enumeration with the results
     *
     * @throws NamingException naming exception
     */
    public NamingEnumeration<SearchResult> search(String name, String filter, SearchControls controls, boolean reconnected)
            throws NamingException {

        if (!isWorking()) {
            close();
            throw new CommunicationException(String.format("Server \"%s\" is down", env.get(Context.PROVIDER_URL)));
        }

        if (reconnected) {
            LOGGER.log(Level.INFO, "Server {0} reconnect", env.get(Context.PROVIDER_URL));
            close();
            if ((ctx = connect()) == null) {
                throw new CommunicationException(String.format("Server \"%s\" cannot reconnect", env.get(Context.PROVIDER_URL)));
            }
        }

        try {
            synchronized (this) {
                return ctx.search(name, filter, controls);
            }
        } catch (CommunicationException ex) {
            if (reconnected) {
                throw ex;
            }
            return search(name, filter, controls, true);
        }
    }

    /**
     * Closes the server context.
     */
    public synchronized void close() {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (NamingException ex) {
            }
            ctx = null;
        }
    }

    private static Hashtable<String, String> prepareEnv() {
        Hashtable<String, String> e = new Hashtable<String, String>();
        e.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CONTEXT_FACTORY);
        e.put(LDAP_TIMEOUT_PARAMETER, Integer.toString(LDAP_TIMEOUT));
        return e;
    }

}
