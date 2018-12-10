package org.opengrok.web;

import org.junit.Test;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequestEvent;
import javax.servlet.http.HttpServletRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WebappListenerTest {
    /**
     * simple smoke test for WebappListener request handling
     */
    @Test
    public void testRequest() {
        WebappListener wl = new WebappListener();
        final HttpServletRequest req = mock(HttpServletRequest.class);
        final ServletContext servletContext = mock(ServletContext.class);
        when(req.getServletContext()).thenReturn(servletContext);
        ServletRequestEvent event = new ServletRequestEvent(servletContext, req);

        wl.requestInitialized(event);
        wl.requestDestroyed(event);
    }
}
