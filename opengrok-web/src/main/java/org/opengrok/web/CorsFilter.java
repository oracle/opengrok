package org.opengrok.web;

import javax.ws.rs.container.*;
import javax.ws.rs.ext.Provider;

@Provider
@CorsEnable
public class CorsFilter implements ContainerResponseFilter {

    public static final String ALLOW_CORS_HEADER = "Access-Control-Allow-Origin";
    public static final String CORS_REQUEST_HEADER = "Origin";

    /**
     * Method for ContainerResponseFilter.
     */
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        // if there is no Origin header, then it is not a
        // cross origin request. We don't do anything.
        if (request.getHeaderString(CORS_REQUEST_HEADER) == null) {
            return;
        }

        response.getHeaders().add(ALLOW_CORS_HEADER, "*");
    }
}