package org.opensolaris.opengrok.web.suggester.provider.filter;

import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

@Provider
@PreMatching
public class SuggestionsEnabledFilter implements ContainerRequestFilter {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Override
    public void filter(final ContainerRequestContext context) {
        if (env.getConfiguration() == null || !env.getConfiguration().getSuggester().isEnabled()) {
            context.abortWith(Response.status(Response.Status.NOT_FOUND).build());
        }
    }

}
