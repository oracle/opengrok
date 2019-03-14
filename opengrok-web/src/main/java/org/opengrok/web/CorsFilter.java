package org.opengrok.web;

import javax.ws.rs.container.*;
import javax.ws.rs.ext.Provider;

@Provider
@CorsEnable
public class CorsFilter implements ContainerResponseFilter {
    /**
     * Method for ContainerResponseFilter.
     */
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {

        // if there is no Origin header, then it is not a
        // cross origin request. We don't do anything.
        if (request.getHeaderString("Origin") == null) {
            return;
        }
        response.getHeaders().add("Access-Control-Allow-Origin", "*");
    }
}