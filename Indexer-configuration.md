# Indexer tunables

This is a list of the most common configuration options with their defaults which are not available as an indexer switch. These options are particularly handy when working with https://github.com/oracle/opengrok/wiki/Read-only-configuration

 - `fetchHistoryWhenNotInCache` - false - avoid generating history for individual files when indexing. This prevents excess commands if the history log for top-level directory of a project cannot be retrieved for some reason (e.g. the command to get the history log fails) at the cost of not having history for given project available (if projects are enabled). This is specific to SCMs that can generate history for directories (e.g. Mercurial, Git, ...).
 - `authorizationWatchdogEnabled` - XXX
 - `pluginDirectory` - XXX
 - `pluginConfiguration` - XXX
 - ...

# Other Indexer configuration

## Custom ctags configuration

To make ctags recognize additional symbols/definitions/etc. it is possible to
specify configuration file with extra configuration options for ctags.

This can be done by using the `-o` option for `opengrok.jar`.

Sample configuration file for Solaris code base:

```
--regex-asm=/^[ \t]*(ENTRY_NP|ENTRY|RTENTRY)+\(([a-zA-Z0-9_]+)\)/\2/f,function/
--regex-asm=/^[ \t]*ENTRY2\(([a-zA-Z0-9_]+),[ ]*([a-zA-Z0-9_]+)\)/\1/f,function/
--regex-asm=/^[ \t]*ENTRY2\(([a-zA-Z0-9_]+),[ ]*([a-zA-Z0-9_]+)\)/\2/f,function/
--regex-asm=/^[ \t]*ENTRY_NP2\(([a-zA-Z0-9_]+),[ ]*([a-zA-Z0-9_]+)\)/\1/f,function/
--regex-asm=/^[ \t]*ENTRY_NP2\(([a-zA-Z0-9_]+),[ ]*([a-zA-Z0-9_]+)\)/\2/f,function/
```

## Introduce own mapping for an extension to analyzer

Use the `-A` Indexer option, e.g. like this:

```
-A .cs:org.opengrok.indexer.analysis.plain.PlainAnalyzerFactory
```

This will map extension `.cs` to the analyzer created by the `PlainAnalyzerFactory `. You should even be able to override OpenGroks analyzers using this option.
