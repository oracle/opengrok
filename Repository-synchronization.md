While by itself OpenGrok does not provide a way how to synchronize repositories (yet, there are some stubs for that in the Repository handling code) it is shipped with a set of Python scripts that make it easy to both synchronize and reindex.

These scripts assume that OpenGrok is setup with projects. See https://github.com/OpenGrok/OpenGrok/wiki/Per-project-management for more details on per-project management.

There are 2 main scripts:
  - `opengrok-sync` - provides a way how to run a sequence of commands for a set of projects (in parallel).
  - `opengrok-mirror` - performs synchronization of given Source Code Management repository with its upstream.

In the source these scripts live under the [tools](https://github.com/OpenGrok/OpenGrok/tree/master/tools) directory.

Both scripts take configuration either in JSON or YAML.

# opengrok-sync

Use e.g. like this:

```bash
  $ opengrok-sync -c /scripts/sync.conf -d /ws-local/ -p
```

where the `sync.conf` file contents might look like this:

```YML
commands:
- command:
  - http://localhost:8080/source/api/v1/messages
  - POST
  - cssClass: info
    duration: PT1H
    tags: ['%PROJECT%']
    text: resync + reindex in progress
- command: [sudo, -u, wsmirror, /opengrok/dist/bin/opengrok-mirror, -c, /opengrok/etc/mirror-config.yml]
- command: [sudo, -u, webservd, /opengrok/dist/bin/opengrok-reindex-project, -D, -J=-d64,
    '-J=-XX:-UseGCOverheadLimit', -J=-Xmx16g, -J=-server, --jar, /opengrok/dist/lib/opengrok.jar,
    -t, /opengrok/etc/logging.properties.template, -p, '%PROJ%', -d, /opengrok/log/%PROJECT%,
    -P, '%PROJECT%', --, --renamedHistory, 'on', -r, dirbased, -G, -m, '256', -c,
    /usr/local/bin/ctags, -U, 'http://localhost:8080/source', -o, /opengrok/etc/ctags.config,
    -H, '%PROJECT%']
  env: {LC_ALL: en_US.UTF-8}
  limits: {RLIMIT_NOFILE: 1024}
- command: ['http://localhost:8080/source/api/v1/messages?tag=%PROJECT%', DELETE,
    '']
- command: [/scripts/check-indexer-logs.ksh]
cleanup:
  command: ['http://localhost:8080/source/api/v1/messages?tag=%PROJECT%', DELETE, '']
```

The above `opengrok-sync` command will basically take all directories under `/ws-local` and for each it will run the sequence of commands specified in the `sync.conf` file. This will be done in parallel - on project level. The level of parallelism can be specified using the the `--workers` option (by default it will use as many workers as there are CPUs in the system).

Another variant of how to specify the list of projects to be synchronized is to use the `--indexed` option of `opengrok-sync` that will query the webapp configuration for list of indexed projects and will use that list. Otherwise, the `--projects` option can be specified to process just specified projects.

The commands above will basically:
  - mark the project with alert (to let the users know it is being synchronized/indexed) using the [RESTful API](https://github.com/oracle/opengrok/wiki/Web-services) call (the `%PROJECT%` string is replaced with current project name)
  - pull the changes from all the upstream repositories that belong to the project using the `opengrok-mirror` command
  - reindex the project using `opengrok-reindex-project`
  - clear the alert using the second [RESTful API](https://github.com/oracle/opengrok/wiki/Web-services) call
  - execute the `/scripts/check-indexer-logs.ksh` script to perform some pattern matching in the indexer logs to see if there were any serious failures there. The script can look e.g. like this:
```shell
#!/usr/bin/ksh
#
# Check OpenGrok indexer logs in the last 24 hours for any signs of serious
# trouble.
#

if (( $# != 1 )); then
        print -u2 "usage: $0 <project_name>"
        exit 1
fi

project_name=$1

typeset -r log_dir="/opengrok/log/$project_name/"
if [[ ! -d $log_dir ]]; then
        print -u2 "cannot open log directory $log_dir"
        exit 1
fi

# Check the last log file.
if grep SEVERE "$log_dir/opengrok0.0.log"; then
        exit 1
fi
```

The `opengrok-sync` script will print any errors to the console and uses file level locking to provide exclusivity of run so it is handy to run from `crontab` periodically.

Each "command" can be either normal command execution (supplying the list of program arguments) or RESTful API call (supplying the HTTP verb and optional payload).

Note that if the web application is listening on non-standard host or port (`localhost` and 8080 is the default), the URI has to be used everywhere where it matters. Given that `opengrok-sync` performs RESTful API queries itself, one has to specify the location using the -U option of this script and then again it is necessary to specify it in the configuration file - for any RESTful API calls or for `opengrok-indexer` command (which also uses the -U option).

## Cleanup

If any of the commands in `"commands"` fail, the `"cleanup"` command will be executed. This is handy in this case since the first [RESTful API](https://github.com/oracle/opengrok/wiki/Web-services) call will mark the project with alert in the WEB UI so if any of the commands that follow fails, the cleanup call will be made to clear the alert.

Normal command execution can be also performed in the `cleanup` section.

## Ignoring repositories

Some project can be notorious for producing spurious errors so their errors are ignored via the `"ignore_errors"` section.

## Run

In the above example it is assumed that `opengrok-sync` is run as `root` and synchronization and reindexing are done under different users. This is done so that the web application cannot tamper with source code even if compromised.

## Pattern replacement and logging

The commands got appended project name unless one of their arguments contains
`%PROJECT%`, in which case it is substituted with project name and no append is
done.

For per-project reindexing to work properly, `opengrok-reindex-project` uses
the `logging.properties.template` to make sure each project has its own
log directory. The file can look e.g. like this:

```
handlers= java.util.logging.FileHandler

.level= FINE

java.util.logging.FileHandler.pattern = /opengrok/log/%PROJ%/opengrok%g.%u.log
# Create one file per indexer run. This makes indexer log easy to check.
java.util.logging.FileHandler.limit = 0
java.util.logging.FileHandler.append = false
java.util.logging.FileHandler.count = 30
java.util.logging.FileHandler.formatter = org.opengrok.indexer.logger.formatter.SimpleFileLogFormatter

java.util.logging.ConsoleHandler.level = WARNING
java.util.logging.ConsoleHandler.formatter = org.opengrok.indexer.logger.formatter.SimpleFileLogFormatter
```

The `%PROJ%` template is passed to the script for substitution in the
logging template. This pattern must differ from the `%PROJECT%` pattern, otherwise the `sync.py`
script would substitute it in the command arguments and the substitution in the template file
would not happen.

# opengrok-mirror

The script synchronized the repositories of projects by running appropriate commands (e.g. `git pull` for Git). While it can run perfectly fine standalone, it is meant to be run from within `opengrok-sync` (see above).

## Configuration example

The configuration file contents in YML can look e.g. like this:

```YML
#
# Commands (or paths - for specific repository types only)
#
commands:
  hg: /usr/bin/hg
  svn: /usr/bin/svn
  teamware: /ontools/onnv-tools-i386/teamware/bin
#
# The proxy environment variables will be set for a project's repositories
# if the 'proxy' property is True.
#
proxy:
  http_proxy: proxy.example.com:80
  https_proxy: proxy.example.com:80
  ftp_proxy: proxy.example.com:80
  no_proxy: example.com,foo.example.com
hookdir: /tmp/hooks
# per-project hooks relative to 'hookdir' above
logdir: /tmp/logs
command_timeout: 300
hook_timeout: 1200
#
# Per project configuration.
#
projects:
  http:
    proxy: true
  history:
    disabled: true
  userland:
    proxy: true
    hook_timeout: 3600
    hooks:
      pre: userland-pre.ksh
      post: userland-post.ksh
  opengrok-master:
    ignored_repos:
      - /opengrok-master/testdata/repositories/rcs_test
  jdk.*:
    proxy: true
    hooks:
      post: jdk_post.sh
```

In the above config, the `userland` project will be run with environment variables in the `proxy` section, plus it will also run scripts specified in the `hook` section before and after all its repositories are synchronized. The hook scripts will be run with the current working directory set to that of the project.

The `opengrok-master` project contains a RCS repository that would make the mirroring fail (since `opengrok-mirror` does not support RCS yet) so it is marked as ignored.

## URI specifications

Just like `opengrok-sync`, `opengrok-mirror` also queries the web app for various properties, so if the web application is not listening on default host/port, the URI location has to be specified using the -U option.

## Project matching

Multiple projects can share the same configuration using regular expressions as demonstrated with the `jdk.*` pattern in the above configuration. The patterns are matched from top to the bottom of the configuration file, first match wins.

## Disabling project mirroring

The `history` project is marked as disabled. This means that the `opengrok-mirror` script will exit with special value of 2 that is interpreted by the `opengrok-sync` script to avoid any reindex. It is not treated as an error.

## Batch mode

In batch mode, log messages will be written to a log file under the `logdir` directory specified in the configuration and rotated for each run, up to default count (8) or count specified using the `--backupcount` option.

## Hooks

If pre and post mirroring hooks are specified, they are run before and after project synchronization. If any of the hooks fail, the program is immediately terminated. However, if the synchronization (that is run in between the hook scripts) fails, the post hook will be executed anyway. This is done so that the project is in sane state - usually the post hook which is used to apply extract source archives and apply patches. If the pre hook is used to clean up the extracted work and project synchronization failed, the project would be left barebone.

## Timeouts

Both repository synchronization commands and hooks can have a timeout. By default there is no timeout, unless specified in the configuration file. There are global and per project timeouts, the latter overriding the former. For instance, in the above configuration file, the `userland` project overrides global hook timeout to 1 hour while inheriting the command timeout.