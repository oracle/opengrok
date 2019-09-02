package opengrok.auth.plugin;

import opengrok.auth.plugin.configuration.Configuration;
import opengrok.auth.plugin.ldap.LdapFacade;
import opengrok.auth.plugin.ldap.LdapServer;
import org.junit.Test;

import javax.naming.directory.SearchControls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LdapFacadeTest {
    @Test
    public void testSearchControlsConfig() {
        Configuration config = new Configuration();
        int searchTimeout = 1234;
        config.setSearchTimeout(searchTimeout);
        int countLimit = 32;
        config.setCountLimit(countLimit);

        LdapFacade facade = new LdapFacade(config);
        SearchControls controls = facade.getSearchControls();
        assertEquals(searchTimeout, controls.getTimeLimit());
        assertEquals(countLimit, controls.getCountLimit());
    }

    @Test
    public void testConnectTimeoutInheritance() {
        Configuration config = new Configuration();
        config.setServers(Collections.singletonList(new LdapServer("http://foo.bar")));
        int timeoutValue = 42;
        config.setConnectTimeout(timeoutValue);
        LdapFacade facade = new LdapFacade(config);
        assertTrue(facade.getServers().stream().anyMatch(s -> s.getConnectTimeout() == timeoutValue));
    }

    @Test
    public void testToString() {
        Configuration config = new Configuration();
        config.setServers(Arrays.asList(new LdapServer("http://foo.foo"),
                new LdapServer("http://bar.bar")));
        config.setSearchBase("dc=foo,dc=com");
        int timeoutValue = 42;
        config.setConnectTimeout(timeoutValue);
        LdapFacade facade = new LdapFacade(config);
        assertEquals("{servers=http://foo.foo,http://bar.bar, searchBase=dc=foo,dc=com}",
                facade.toString());
    }
}
