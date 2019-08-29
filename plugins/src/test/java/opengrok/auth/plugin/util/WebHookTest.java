package opengrok.auth.plugin.util;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;

public class WebHookTest extends JerseyTest {
    private static final String PREFIX = "service";
    private static int requests;

    @Path(PREFIX)
    public static class Service {
        @POST
        public String handlePost() { requests++; return "posted"; }
    }

    @Override
    protected Application configure() {
        return new ResourceConfig(Service.class);
    }

    @Test
    public void testPost() throws ExecutionException, InterruptedException {
        assertEquals(0, requests);
        WebHook hook = new WebHook(getBaseUri() + PREFIX, "{}");
        Future<String> future = hook.post();
        future.get();
        assertEquals(1, requests);
    }
}
