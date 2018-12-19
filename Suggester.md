# Suggester
OpenGrok has a suggester support which tries to autocomplete the user queries.

## Java 9+ problems
The `ChronicleMap` dependency does not work out of the box with Java 9+; as a result, promoting terms based on the previous searches won't work. To solve this issue, following parameters are needed to add to `java` invocation:
```
--add-exports java.base/jdk.internal.ref=ALL-UNNAMED
--add-exports java.base/jdk.internal.misc=ALL-UNNAMED
--add-exports java.base/sun.nio.ch=ALL-UNNAMED
```

If you use Tomcat as a servlet container, these options can be specified by adding the following to the `setenv.sh` file:
```bash
JDK_JAVA_OPTIONS="--add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-exports java.base/jdk.internal.misc=ALL-UNNAMED --add-exports java.base/sun.nio.ch=ALL-UNNAMED"
```

## Queries that suggester supports
**Note:** `|` specifies the caret position in the input field.

### Prefix Query
If the user types a prefix, then suggester suggests the terms with the same prefix. Example for the prefix `t|`:
* the
* this
* to
* …

### Fuzzy Query
If the user specifies a term with a specified fuzzy factor, then the suggester suggests similar terms. Example for the query `car|~1`:
* can
* bar
* char 
* …

### Phrase Query
If the user types a phrase query, then the suggester tries to suggest the terms that follow. Example for the query `"public c|"`:
* class
* com
* collection
* …

**Note:** it is possible to use a sloppy factor `~` as well.

### Range Query
If the user types a range query, then the suggester tries to suggest the terms that exist and satisfy the query. Example for the query `[a TO b|]`:
* by
* basis
* boolean
* …

**Note:** suggestions work also for the lower term.

### Regexp Query
If the user types a regexp query, then the suggester suggests the terms that would be accepted by this query. Example for the query `/[aA].*|/`:
* a
* and
* apache
* …

### Wildcard Query
If the user types a wilcard query, then the suggester suggests the terms that would be accepted by this query.
Example for the query `*e`:
* the
* file
* use
* …

## Configuration
Suggester configuration is bundled in `suggesterConfig` property of the configuration. In [read-only configuration](https://github.com/oracle/opengrok/wiki/Read-only-configuration) it looks for example like this:

```xml
  <void property="suggesterConfig">
     <void property="minChars">
       <int>3</int>
     </void>
  </void>
```

Configurable properties of the suggester configuration:

### Enabled 
Specifies if the suggester is enabled. Some users might not want the suggester functionality.
- Property: `enabled`
- Default value: `true`

### Maximum results
Specifies how many results the suggester should return at maximum. 
- Property: `maxResults`. 
- Default value: `10`

### Minimum characters 
Specifies the minimum number of characters that are needed for the suggester to start looking for suggestions. More characters mean less possible candidates. It can significantly improve performance of the suggester.
- Property: `minChars`
- Default value: `0`

### Allowed projects
Specifies a set of projects for which the suggester should be enabled.
- Property: `allowedProjects`
- Default value: `null` (all allowed)

### Maximum projects
Specifies how many projects can be selected at the same time and the suggestions will work.
- Property: `maxProjects`
- Default value: `Integer.MAX_VALUE` (unlimited)

### Allowed fields
Specifies the fields for which the suggester should be enabled. For instance, it might be
desired that the suggestions should only work for the \textit{full} field. OpenGrok uses some fields to store
private data and it is not desired to create suggester data structures for those fields. Therefore, only the main
search fields are specified by default.
- Property: `allowedFields`
- Default value: `[full, defs, refs, path, hist, type]`

### Allow complex queries 
Specifies if the suggester should support complex queries. If set to `false` then only simple prefix lookups by using the WFST data structure will be performed.
- Property: `allowComplexQueries`
- Default value: `true`
    
### Allow most popular
Specifies if the most popular completion should be enabled.
If set to `false` then it slightly increases the performance and there is no need for WFST rebuilds.
- Property: `allowMostPopular`
- Default value: `true`

### Show scores
Specifies if the scores should be displayed next to the suggestions
- Property: `showScores`
- Default value: `false`

### Show projects 
Specifies if the suggestions should show in which project the term was found. If there are multiple projects then showing all the names is not feasible; therefore, only the number of projects will be shown.
- Property: `showProjects`
- Default value: `true`

### Show time 
Specifies if the time it took the suggester to find the suggestions should be displayed.
- Property: `showTime`
- Default value: `false`

### Rebuild cron config 
Specifies how often should the suggester rebuild the WFST data structures.
The value must be in the Unix cron format
- Property: `rebuildCronConfig`
- Default value: `0 0 * * *` (every day at midnight)
    
### Build Termination Time in Seconds 
Specifies after how much time the suggester should kill the threads that build the suggester data structures. Slow machines should specify more time. This option is mainly here to prevent the suggester to hang in the initialization.
- Property: `buildTerminationTime`
- Default value: `1800` (30 minutes)
    
### Time threshold
Specifies a time threshold for suggestions in milliseconds.
If the computation exceeds this time, it will be stopped and partial results will be returned.
- Property: `timeThreshold`
- Default value: `2000` (2 seconds)
