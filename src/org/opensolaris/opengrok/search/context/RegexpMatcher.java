package org.opensolaris.opengrok.search.context;

import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.opensolaris.opengrok.OpenGrokLogger;

/**
 *
 * @author dobrou@gmail.com
 */
class RegexpMatcher extends LineMatcher {
    private final Pattern termRegexp;

    public RegexpMatcher(String term, boolean caseInsensitive) {
        super(caseInsensitive); 
        Pattern regexp;
        try {
            regexp = Pattern.compile(term, caseInsensitive ? Pattern.CASE_INSENSITIVE : 0 );
        } catch ( PatternSyntaxException  e) {
            regexp = null;
            OpenGrokLogger.getLogger().log(Level.WARNING, "RegexpMatcher: {0}", e.getMessage() );
        }
        this.termRegexp = regexp;
    }

    @Override
    public int match(String line) {
        return (termRegexp != null && termRegexp.matcher(line).matches() )
            ? MATCHED 
            : NOT_MATCHED
        ;
    }
    
}
