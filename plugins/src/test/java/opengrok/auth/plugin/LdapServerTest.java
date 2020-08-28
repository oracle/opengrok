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
import java.util.concurrent.CountDownLatch;

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
    }

    @Test
    public void testIsReachable() throws UnknownHostException, InterruptedException, URISyntaxException {
        // Start simple TCP server on port 6336. It has to be > 1024 to avoid BindException
        // due to permission denied.
        int testPort = 6336;
        InetAddress localhostAddr = InetAddress.getLocalHost();
        final CountDownLatch startLatch = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            try {
                ServerSocket socket = new ServerSocket(testPort, 1, localhostAddr);
                startLatch.countDown();
                Socket client = socket.accept();
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // TODO:
        //  There is still a tiny window between when the latch unblocks and the server actually starts accept().
        //  We can make the server loop for connections and connect here with a timeout to make sure it is up.
        thread.start();
        startLatch.await();

        // Mock getAddresses() to return single localhost IP address.
        LdapServer server = new LdapServer("ldaps://foo.bar.com");
        LdapServer serverSpy = Mockito.spy(server);
        Mockito.when(serverSpy.getAddresses(any())).thenReturn(new InetAddress[]{localhostAddr});
        doReturn(testPort).when(serverSpy).getPort();

        // Test reachability.
        boolean reachable = serverSpy.isReachable();
        thread.join(5000);
        thread.interrupt();
        assertTrue(reachable);
    }
}
