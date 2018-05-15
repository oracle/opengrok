This is a list of the most common configuration options with their defaults which are not available as an indexer switch. These options are particularly handy when working with https://github.com/oracle/opengrok/wiki/Read-only-configuration

 - `pluginDirectory`
 - `fetchHistoryWhenNotInCache` - false - avoid generating history for individual files when indexing. This prevents excess commands if the history log for top-level directory of a project cannot be retrieved for some reason (e.g. the command to get the history log fails) at the cost of not having history for given project available (if projects are enabled). This is specific to SCMs that can generate history for directories (e.g. Mercurial, Git, ...).
 - `authorizationWatchdogEnabled`
 - `pluginConfiguration`
 - `groups`
 - ...