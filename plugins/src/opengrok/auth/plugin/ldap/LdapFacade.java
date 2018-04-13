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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.CommunicationException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.SizeLimitExceededException;
import javax.naming.TimeLimitExceededException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import opengrok.auth.plugin.configuration.Configuration;
import opengrok.auth.plugin.entity.User;

public class LdapFacade extends AbstractLdapProvider {

    private static final Logger LOGGER = Logger.getLogger(LdapFacade.class.getName());

    /**
     * default LDAP filter
     */
    private static final String LDAP_FILTER = "objectclass=*";

    /**
     * Timeout for retrieving the results.
     */
    private static final int LDAP_TIMEOUT = 5000; // ms

    /**
     * @see
     * <a href="https://docs.oracle.com/javase/7/docs/api/javax/naming/directory/SearchControls.html#setCountLimit%28long%29">SearchControls</a>
     *
     * basically it does not mean that the server must send at most this number
     * of results, but that the program should not iterate more than this number
     * over the results.
     */
    private static final int LDAP_COUNT_LIMIT = 100;

    /**
     * When there is no active server in the pool, the facade waits this time
     * interval (since the last failure) until it tries the servers again.
     *
     * This should avoid heavy load to the LDAP servers when they are all
     * broken/not responding/down - pool waiting.
     *
     * Also each server uses this same interval since its last failure - per
     * server waiting.
     */
    private int interval = 10 * 1000;

    /**
     * LDAP search base
     */
    private String searchBase;

    /**
     * Server pool.
     */
    private List<LdapServer> servers = new ArrayList<>();

    private SearchControls controls;
    private int actualServer = 0;
    private long errorTimestamp = 0;
    private boolean reported = false;

    /**
     * Interface for converting LDAP results into user defined types.
     *
     * @param <T> the type of the result
     */
    private interface AttributeMapper<T> {

        public T mapFromAttributes(Attributes attr) throws NamingException;
    };

    /**
     * Transforms the attributes to the set of strings used for authorization.
     *
     * Currently this behaves like it get all records stored in
     */
    private static class ContentAttributeMapper implements AttributeMapper<Map<String, Set<String>>> {

        private final String[] values;

        /**
         * Create a new mapper which retrieves the given values in the resulting
         * set.
         *
         * @param values include these values in the result
         */
        public ContentAttributeMapper(String[] values) {
            this.values = values;
        }

        @Override
        public Map<String, Set<String>> mapFromAttributes(Attributes attrs) throws NamingException {
            Map<String, Set<String>> map = new HashMap<>();

            if (values == null) {
                for (NamingEnumeration<? extends Attribute> attrEnum = attrs.getAll();
                        attrEnum.hasMore();) {
                    Attribute attr = attrEnum.next();

                    if (!map.containsKey(attr.getID())) {
                        map.put(attr.getID(), new TreeSet<>());
                    }

                    final Set<String> valueSet = map.get(attr.getID());

                    for (NamingEnumeration<?> values = attr.getAll(); values.hasMore();) {
                        valueSet.add((String) values.next());
                    }
                }
            } else {
                for (int i = 0; i < values.length; i++) {
                    Attribute attr = attrs.get(values[i]);

                    if (attr == null) {
                        continue;
                    }

                    if (!map.containsKey(attr.getID())) {
                        map.put(attr.getID(), new TreeSet<>());
                    }

                    final Set<String> valueSet = map.get(attr.getID());

                    for (NamingEnumeration<?> values = attr.getAll(); values.hasMore();) {
                        valueSet.add((String) values.next());
                    }
                }

            }

            return map;
        }
    };

    public LdapFacade(Configuration cfg) {
        setServers(cfg.getServers());
        setInterval(cfg.getInterval());
        setSearchBase(cfg.getSearchBase());
        prepareSearchControls();
        prepareServers();
    }

    /**
     * Finds first working server in the pool.
     */
    private void prepareServers() {
        for (int i = 0; i < servers.size(); i++) {
            LdapServer server = servers.get(i);
            if (server.isWorking()) {
                actualServer = i;
                return;
            }
        }
    }

    /**
     * Closes all available servers.
     */
    @Override
    public void close() {
        for (int i = 0; i < servers.size(); i++) {
            LdapServer server = servers.get(i);
            server.close();
        }
    }

    public List<LdapServer> getServers() {
        return servers;
    }

    public LdapFacade setServers(List<LdapServer> servers) {
        this.servers = servers;
        return this;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
        for (int i = 0; i < servers.size(); i++) {
            LdapServer server = servers.get(i);
            server.setInterval(interval);
        }
    }

    public String getSearchBase() {
        return searchBase;
    }

    public void setSearchBase(String base) {
        this.searchBase = base;
    }

    @Override
    public boolean isConfigured() {
        return servers != null && servers.size() > 0 && LDAP_FILTER != null && searchBase != null;
    }

    /**
     * Lookups the authorization values {
     *
     * @param user user information. If @{code null} then search base will be used.
     * @param filter LDAP filter to use. If @{code null} then @{link LDAP_FILTER} will be used.
     * @param values match these LDAP values
     *
     * @return set of strings describing the user's attributes
     */
    @Override
    public Map<String, Set<String>> lookupLdapContent(User user, String filter, String[] values) {

        return lookup(
                user != null ? user.getUsername() : getSearchBase(),
                filter == null ? LDAP_FILTER : filter,
                values,
                new ContentAttributeMapper(values));
    }

    private SearchControls prepareSearchControls() {
        controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setTimeLimit(LDAP_TIMEOUT);
        controls.setCountLimit(LDAP_COUNT_LIMIT);
        return controls;
    }

    /**
     * Lookups the LDAP server for content
     *
     * @param <T> return type
     * @param dn search base for the query
     * @param filter LDAP filter for the query
     * @param attributes returning LDAP attributes
     * @param mapper mapper class implementing @code{AttributeMapper} closed
     *
     * @return results transformed with mapper
     */
    private <T> T lookup(String dn, String filter, String[] attributes, AttributeMapper<T> mapper) {
        return lookup(dn, filter, attributes, mapper, 0);
    }

    /**
     * Lookups the LDAP server for content
     *
     * @param <T> return type
     * @param dn search base for the query
     * @param filter LDAP filter for the query
     * @param attributes returning LDAP attributes
     * @param mapper mapper class implementing @code{AttributeMapper} closed
     * @param fail current count of failures
     *
     * @return results transformed with mapper or {@code null} on failure
     */
    private <T> T lookup(String dn, String filter, String[] attributes, AttributeMapper<T> mapper, int fail) {

        if (errorTimestamp > 0 && errorTimestamp + interval > System.currentTimeMillis()) {
            if (!reported) {
                reported = true;
                LOGGER.log(Level.SEVERE, "Server pool is still broken");
            }
            return null;
        }

        if (fail > servers.size() - 1) {
            // did the whole rotation
            LOGGER.log(Level.SEVERE, "Tried all servers in a pool but no server works");
            errorTimestamp = System.currentTimeMillis();
            reported = false;
            return null;
        }

        if (!isConfigured()) {
            LOGGER.log(Level.SEVERE, "LDAP is not configured");
            return null;
        }

        NamingEnumeration<SearchResult> namingEnum = null;
        try {
            LdapServer server = servers.get(actualServer);
            controls.setReturningAttributes(attributes);
            for (namingEnum = server.search(dn, filter, controls); namingEnum.hasMore();) {
                SearchResult sr = namingEnum.next();
                reported = false;
                return processResult(sr, mapper);
            }
        } catch (NameNotFoundException ex) {
            LOGGER.log(Level.SEVERE, "The LDAP name was not found.", ex);
            return null;
        } catch (SizeLimitExceededException ex) {
            LOGGER.log(Level.SEVERE, "The maximum size of the LDAP result has exceeded.", ex);
            closeActualServer();
            actualServer = getNextServer();
            return lookup(dn, filter, attributes, mapper, fail + 1);
        } catch (TimeLimitExceededException ex) {
            LOGGER.log(Level.SEVERE, "Time limit for LDAP operation has exceeded.", ex);
            closeActualServer();
            actualServer = getNextServer();
            return lookup(dn, filter, attributes, mapper, fail + 1);
        } catch (CommunicationException ex) {
            LOGGER.log(Level.INFO, "Communication error received, reconnecting to next server.", ex);
            closeActualServer();
            actualServer = getNextServer();
            return lookup(dn, filter, attributes, mapper, fail + 1);
        } catch (NamingException ex) {
            LOGGER.log(Level.SEVERE, "An arbitrary LDAP error occurred.", ex);
            closeActualServer();
            actualServer = getNextServer();
            return lookup(dn, filter, attributes, mapper, fail + 1);
        } finally {
            if (namingEnum != null) {
                try {
                    namingEnum.close();
                } catch (NamingException e) {
                    LOGGER.log(Level.WARNING,
                            "failed to close search result enumeration");
                }
            }
        }
        return null;
    }

    private void closeActualServer() {
        servers.get(actualServer).close();
    }

    /**
     * Server take over algorithm behavior.
     *
     * @return the index of the next server to be used
     */
    private int getNextServer() {
        return (actualServer + 1) % servers.size();
    }

    /**
     * Process the incoming LDAP result.
     *
     * @param <T> type of the result
     * @param result LDAP result
     * @param mapper mapper to transform the result into the result type
     * @return transformed result
     *
     * @throws NamingException
     */
    private <T> T processResult(SearchResult result, AttributeMapper<T> mapper) throws NamingException {
        Attributes attrs = result.getAttributes();
        if (attrs != null) {
            return mapper.mapFromAttributes(attrs);
        }
        return null;
    }
}
