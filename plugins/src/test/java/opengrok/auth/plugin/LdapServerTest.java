package opengrok.auth.plugin;

import opengrok.auth.plugin.ldap.LdapServer;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

public class LdapServerTest {

    @Test
    public void testInvalidURI() {
        LdapServer server = new LdapServer("foo:/\\/\\foo.bar");
        assertFalse(server.isReachable());
    }

    @Test
    public void testGetPort() throws URISyntaxException {
        LdapServer server = new LdapServer("ldaps://foo.bar");
        assertEquals(636, server.getPort());

        server = new LdapServer("ldap://foo.bar");
        assertEquals(389, server.getPort());

        server = new LdapServer("crumble://foo.bar");
        assertEquals(-1, server.getPort());
    }

    @Test
    public void testSetGetUsername() {
        LdapServer server = new LdapServer();

        assertNull(server.getUsername());
        assertNull(server.getPassword());

        final String testUsername = "foo";
        server.setUsername(testUsername);
        assertEquals(testUsername, server.getUsername());

        final String testPassword = "bar";
        server.setPassword(testPassword);
        assertEquals(testPassword, server.getPassword());
    }

    @Test
    public void testIsReachable() throws IOException, InterruptedException, URISyntaxException {
        // Start simple TCP server on port 6336. It has to be > 1024 to avoid BindException
        // due to permission denied.
        int testPort = 6336;
        InetAddress localhostAddr = InetAddress.getLocalHost();
        ServerSocket serverSocket = new ServerSocket(testPort, 1, localhostAddr);
        Thread thread = new Thread(() -> {
            try {
                while (true) {
                    Socket client = serverSocket.accept();
                    client.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        thread.start();
        Socket socket = null;
        for (int i = 0; i < 3; i++) {
            try {
                socket = new Socket(localhostAddr, testPort);
            } catch (IOException e) {
                Thread.sleep(1000);
            }
        }

        assertNotNull(socket);
        assertTrue(socket.isConnected());

        // Mock getAddresses() to return single localhost IP address.
        LdapServer server = new LdapServer("ldaps://foo.bar.com");
        LdapServer serverSpy = Mockito.spy(server);
        Mockito.when(serverSpy.getAddresses(any())).thenReturn(new InetAddress[]{localhostAddr});
        doReturn(testPort).when(serverSpy).getPort();

        // Test reachability.
        boolean reachable = serverSpy.isReachable();
        serverSocket.close();
        thread.join(5000);
        thread.interrupt();
        assertTrue(reachable);

        // Test non-reachability.
        reachable = serverSpy.isReachable();
        assertFalse(reachable);
    }

    @Test
    public void testEmptyAddressArray() throws UnknownHostException {
        LdapServer server = new LdapServer("ldaps://foo.bar.com");
        LdapServer serverSpy = Mockito.spy(server);
        Mockito.when(serverSpy.getAddresses(any())).thenReturn(new InetAddress[]{});
        assertFalse(serverSpy.isReachable());
    }
}
