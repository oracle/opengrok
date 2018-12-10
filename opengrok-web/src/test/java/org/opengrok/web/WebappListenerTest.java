package org.opengrok.web;

import org.junit.Test;
import org.opengrok.indexer.web.DummyHttpServletRequest;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequestEvent;

import static org.mockito.Mockito.mock;

public class WebappListenerTest {
    /**
     * simple smoke test for WebappListener request handling
     */
    @Test
    public void testRequest() {
        WebappListener wl = new WebappListener();
        DummyHttpServletRequest req = new DummyHttpServletRequest();
        final ServletContext servletContext = mock(ServletContext.class);
        ServletRequestEvent event = new ServletRequestEvent(servletContext, req);

        wl.requestInitialized(event);
        wl.requestDestroyed(event);
    }
}
