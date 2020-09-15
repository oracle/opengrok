package opengrok.auth.plugin.ldap;

import opengrok.auth.plugin.configuration.Configuration;
import opengrok.auth.plugin.ldap.LdapFacade;
import opengrok.auth.plugin.ldap.LdapServer;
import org.junit.Test;
import org.mockito.Mockito;

import javax.naming.directory.SearchControls;
import javax.naming.ldap.LdapContext;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;

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
    public void testToStringNegative() throws UnknownHostException {
        Configuration config = new Configuration();
        LdapServer server1 = new LdapServer("ldap://foo.com");
        LdapServer serverSpy1 = Mockito.spy(server1);
        Mockito.when(serverSpy1.getAddresses(any())).thenReturn(new InetAddress[]{InetAddress.getLocalHost()});
        Mockito.when(serverSpy1.isReachable()).thenReturn(false);

        config.setServers(Collections.singletonList(serverSpy1));
        config.setSearchBase("dc=foo,dc=com");
        int timeoutValue = 3;
        config.setConnectTimeout(timeoutValue);
        LdapFacade facade = new LdapFacade(config);
        assertEquals("{server=no active server, searchBase=dc=foo,dc=com}",
                facade.toString());
    }

    @Test
    public void testToStringPositive() throws UnknownHostException {
        Configuration config = new Configuration();
        LdapServer server1 = new LdapServer("ldap://foo.com");
        LdapServer serverSpy1 = Mockito.spy(server1);
        Mockito.doReturn(true).when(serverSpy1).isWorking();

        config.setServers(Collections.singletonList(serverSpy1));
        config.setSearchBase("dc=foo,dc=com");
        int timeoutValue = 3;
        config.setConnectTimeout(timeoutValue);
        LdapFacade facade = new LdapFacade(config);
        assertEquals("{server=ldap://foo.com timeout: 3, searchBase=dc=foo,dc=com}",
                facade.toString());
    }

    @Test
    public void testPrepareServersNegative() throws UnknownHostException {
        Configuration config = new Configuration();

        LdapServer server1 = new LdapServer("ldap://foo.com");
        LdapServer serverSpy1 = Mockito.spy(server1);
        Mockito.when(serverSpy1.getAddresses(any())).thenReturn(new InetAddress[]{InetAddress.getLocalHost()});
        Mockito.when(serverSpy1.isReachable()).thenReturn(false);

        LdapServer server2 = new LdapServer("ldap://bar.com");
        LdapServer serverSpy2 = Mockito.spy(server2);
        Mockito.when(serverSpy2.getAddresses(any())).thenReturn(new InetAddress[]{});

        config.setServers(Arrays.asList(serverSpy1, serverSpy2));
        LdapFacade facade = new LdapFacade(config);
        facade.prepareServers();
        assertFalse(facade.isConfigured());
    }
}
