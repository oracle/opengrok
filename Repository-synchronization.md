While by itself OpenGrok does not provide a way how to synchronize repositories (yet, there are some stubs for that in the Repository handling code) it is shipped with a set of Python scripts that make it easy to both synchronize and reindex.

These scripts assume that OpenGrok is setup with projects. See https://github.com/OpenGrok/OpenGrok/wiki/Per-project-management for more details on per-project management.

There are 2 main scripts:
  - `sync.py` - provides a way how to run a sequence of commands for a set of projects (in parallel).
  - `mirror.py` - performs synchronization of given Source Code Management repository with its upstream.

In the source these scripts live under the [tools/sync](https://github.com/OpenGrok/OpenGrok/tree/master/tools/sync) directory.

Both scripts take configuration either in JSON or YAML.

Use e.g. like this:

  `# sync.py -c /scripts/sync.conf -d /ws-local/ -p`

where the `sync.conf` file contents might look like this:

```
  {
     "commands": [["/usr/opengrok/bin/Messages", "-c", "info", "-e", "+1 hour",
                   "-n", "normal", "-t", "ARG", "resync + reindex in progress"],
                  ["sudo", "-u", "wsmirror", "/usr/opengrok/bin/mirror.py",
                    "-c", "/opengrok/etc/mirror-config.yml", "-b",
                    "--messages", "/usr/opengrok/bin/Messages"],
                  ["sudo", "-u", "webservd", "/usr/opengrok/bin/reindex-project.ksh",
                   "/opengrok/etc/opengrok.conf", "/usr/opengrok/bin"],
                  ["/usr/opengrok/bin/Messages", "-n", "abort", "-t"],
                  ["/scripts/check-indexer-logs.ksh"]],
     "ignore_errors": ["NetBSD-current", "linux-mainline-next"],
     "cleanup": ["/usr/opengrok/bin/Messages", "-n", "abort", "-t"]
  }
```

The above `sync.py` command will basically take all directories under `/ws-local` and for each it will run the sequence of commands specified in the `sync.conf` file. This will be done in parallel - on project level. The level of parallelism can be specified using the the `--workers` option (is 4 by default).

Another variant of how to specify the list of projects to be synchronized is to use the `--indexed` option of `sync.py` that will query the webapp configuration for list of indexed projects and will use that list. Otherwise, the `--projects` option can be specified to process just specified projects.

The commands above will basically:
  - mark the project with alert (to let the users know it is being synchronized/indexed) using the first `Messages` command
  - pull the changes from all the upstream repositories that belong to the project using the `mirror.py` command
  - reindex the project using `reindex-project.ksh`
  - clear the alert using the second `Messages` command
  - execute the "/scripts/check-indexer-logs.ksh" script to perform some pattern matching in the indexer logs to see if there were any serious failures there

The `sync.py` script will print any errors to the console and uses file level locking to provide exclusivity of run so it is handy to run from `crontab` periodically.

If any of the commands in `"commands"` fail, the `"cleanup"` command will be run. This is handy in this case since the first `Messages` command will mark the project with alert in the WEB UI so if any of the commands that follow fails, the cleanup `Messages` command will be run to clear the alert.

Some project can be notorious for producing spurious errors so their errors are ignored via the `"ignore_errors"` section.

The `sync.conf` configuration can be also represented as YAML.

In the above example it is assumed that `sync.py` is run as `root` and synchronization and reindexing are done under different users. This is done so that the web application cannot tamper with source code even if compromised.

The commands got appended project name unless one of their arguments is equal
to 'ARG', in which case it is substituted with project name and no append is
done.

For per-project reindexing to work properly, `reindex-project.ksh` uses
the `logging.properties.template` to make sure each project has its own
log directory.

The `mirror-config.yml` configuration file contents can look e.g. like this:

```
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
    hooks:
      pre: userland-pre.ksh
      post: userland-post.ksh
  opengrok-master:
    - ignored_repos:
      /opengrok-master/testdata/repositories/rcs_test
```

In the above config, the `userland` project will be run with environment variables in the `proxy` section, plus it will also run scripts specified in the `hook` section before and after all its repositories are synchronized.

The `opengrok-master` project contains a RCS repository that would make the mirroring fail (since `mirror.py` does not support RCS yet) so it is marked as ignored.