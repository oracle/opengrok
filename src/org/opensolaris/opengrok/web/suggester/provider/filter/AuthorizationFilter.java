package org.opensolaris.opengrok.web.suggester.provider.filter;

import org.opensolaris.opengrok.authorization.AuthorizationFramework;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.web.suggester.model.SuggesterQueryData;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

@Provider
@Authorized
public class AuthorizationFilter implements ContainerRequestFilter {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Context
    private HttpServletRequest request;

    @Override
    public void filter(final ContainerRequestContext context) {
        AuthorizationFramework auth = env.getAuthorizationFramework();
        if (auth != null) {
            String[] projects = request.getParameterValues(SuggesterQueryData.PROJECTS_PARAM);
            if (projects != null) {
                for (String project : projects) {
                    Project p = Project.getByName(project);
                    if (!auth.isAllowed(request, p)) {
                        context.abortWith(Response.status(Response.Status.FORBIDDEN).build());
                    }
                }
            }
        }
    }

}
