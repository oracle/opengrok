import javax.servlet.http.HttpServletRequest;
import org.opensolaris.opengrok.authorization.IAuthorizationPlugin;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
/**
 * Sample authorization plugin.
 * 
 * Always just bypass all authorization requests.
 */
public class SamplePlugin implements IAuthorizationPlugin {

    @Override
    public void reload() {
    }
    
    @Override
    public boolean isAllowed(HttpServletRequest request, Project project) {
        return true;
    }

    @Override
    public boolean isAllowed(HttpServletRequest request, Group group) {
        return true;
    }
}

