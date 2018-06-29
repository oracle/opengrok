package org.opensolaris.opengrok.web.suggester;

import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.ApplicationPath;

@ApplicationPath("/suggest")
public class SuggesterApp extends ResourceConfig {

    public SuggesterApp() {
        register(new SuggesterAppBinder());
        packages(true, "org.opensolaris.opengrok.web.suggester.controller",
                "org.opensolaris.opengrok.web.suggester.provider");
    }

}
