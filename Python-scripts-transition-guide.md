In OpenGrok 1.1 the shell scripts were all rewritten to Python. The main change is mainly to the `OpenGrok` shell script that is now a thin layer atop running the main class from `opengrok.jar`. This means users will have to supply `Indexer` options directly. This is a transition guide for those who had been using the shell scripts.

# `OpenGrok` shell script subcommand replacements:

| `OpenGrok` subcomand | Replacement |
| ----- | ---- |
bootstrap | use `indexer.py` with `--updateConfig` and the `-U` options
index | use `indexer.py` with options as per the sections below
indexpart | use `reindex-project.py` with options as per the sections below
updateDesc | use [RESTful API](/OpenGrok/OpenGrok/wiki/Web-services#rest-api) for updating path descriptions
deploy | use the `deploy.py` script

# Environment variables replaceable with `Indexer` options:

| `OpenGrok` environment variable | `Indexer` option |
| ----- | ---- |
OPENGROK_VERBOSE | --verbose
OPENGROK_PROGRESS | --progress
OPENGROK_TAG | -G
OPENGROK_PARALLELISM | --threads
OPENGROK_PROFILER | --profiler
OPENGROK_SCAN_DEPTH | --depth
OPENGROK_DEFAULT_PROJECTS | -p
OPENGROK_ENABLE_PROJECTS | -P
OPENGROK_SCAN_REPOS | -S
OPENGROK_GENERATE_HISTORY | -r / -H
OPENGROK_RENAMED_FILES_HISTORY | --renamedHistory
OPENGROK_WPREFIX | --leadingWildCards on
OPENGROK_WEBAPP_CFGADDR | -U
OPENGROK_FLUSH_RAM_BUFFER_SIZE | -m
OPENGROK_MANDOC | --mandoc
OPENGROK_LOCKING | --lock
OPENGROK_IGNORE_PATTERNS | -i
OPENGROK_ASSIGNMENTS | -A
OPENGROK_CTAGS_OPTIONS_FILE | -o
OPENGROK_CTAGS | -c (the indexer.py script will try to check if Exuberant/Universal ctags is present however will not set the location)
OPENGROK_READ_XML_CONFIGURATION | -R
OPENGROK_SRC_ROOT | -s
OPENGROK_DATA_ROOT | -d

# No replacement:

| `OpenGrok` environment variable | Comment |
| ----- | ---- |
OPENGROK_WEBAPP_CONTEXT | war file destination is supplied as option in deploy.py
OPENGROK_CONFIGURATION | supply opengrok.jar options directly to indexer.py and other scripts
OPENGROK_NON_INTERACTIVE | 
OPENGROK_DISTRIBUTION_BASE | use `--jar` for full path to the jar
OPENGROK_INSTANCE_BASE | ditto
JAVA_HOME | use -j for full path to java binary
OPENGROK_APP_SERVER, OPENGROK_WAR_TARGET_TOMCAT*, OPENGROK_TOMCAT*, OPENGROK_RESIN*, OPENGROK_GLASSFISH* | supply path to the war location in deploy.py

# Replacement with indexer.py options:

| `OpenGrok` environment variable | `indexer.py` option |
| ----- | ---- |
JAVA_OPTS | -J (can be specified multiple times, will be cumulative)
JAVA | -j
OPENGROK_LOGGER_CONFIG_PATH | add `-Djava.util.logging.config.file=...` to -J

# Examples:

- running full indexer:
  ```
  indexer.py -C -J=-Djava.util.logging.config.file=/var/opengrok/logging.properties \
      -a ../dist/opengrok.jar -- \
      -s /var/opengrok/src -d /var/opengrok/data -H -P -S -G \
      -W /var/opengrok/etc/configuration.xml` -U http://localhost:8080
  ```
- deploy:
  ```
   deploy.py -c /opengrok/etc/configuration.xml -D /opengrok/dist/lib/source.war \
       /var/tomcat8/webapps
  ```
- new `sync.py` config example (together with `reindex-project.py` example): see https://github.com/oracle/opengrok/wiki/Repository-synchronization
