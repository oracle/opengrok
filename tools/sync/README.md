
Set of scripts to facilitate parallel project synchronization and mirroring

Use e.g. like this:

  # sync.py -c /scripts/sync.conf -d /ws-local/ -p

where the sync.conf file contents might look like this:

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
                  ["/scripts/check-indexer-logs.ksh"]]
  }
```

The commands got appended project name unless one of their arguments is equal
to 'ARG', in which case it is substituted with project name and no append is
done.

For per-project reindexing to work properly, reindex-project.ksh uses
the logging.properties.template to make sure each project has its own
log directory.

The mirror-config.yml can look e.g. like this:

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
    disable: true
  userland:
    proxy: true
    hooks:
      pre: userland-pre.ksh
      post: userland-post.ksh
```

See https://github.com/OpenGrok/OpenGrok/wiki/Per-project-management
for more details on per-project management.
