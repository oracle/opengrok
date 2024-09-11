# A Docker container for OpenGrok

## OpenGrok from official source

Built from official source: https://github.com/oracle/opengrok/releases/

You can learn more about OpenGrok at https://oracle.github.io/opengrok/

The container is available from DockerHub at https://hub.docker.com/r/opengrok/docker/

## When not to use it

This image is simple wrapper around OpenGrok environment. It is basically a small appliance. The indexer and the web container are **not** tuned for large workloads.

If you happen to have one of the following:

  - large source data (e.g. [AOSP](https://en.wikipedia.org/wiki/Android_Open_Source_Project) or the like)
  - stable service
  - Source Code Management systems not supported in the image (e.g. Perforce,
    Clearcase, etc.)
  - need for authentication/authorization

then it is advisable to run OpenGrok standalone or construct your own Docker
image based on the official one.

## Additional info about the image

* Tomcat 10
* JRE 17
* Configurable mirroring/reindexing (default every 10 min)

The mirroring step works by going through all projects and attempting to
synchronize all its repositories (e.g. it will do `git pull --ff-only` for Git
repositories).

Projects are enabled in this setup by default. See environment variables
below on how to change that.

### Indexer logs

The indexer/mirroring is set so that it does not log into files.
Rather, everything goes to standard (error) output. To see how the indexer
is doing, use the `docker logs` command.

### Source Code Management systems supported

- Bazaar
- CVS
- Git
- Mercurial
- Perforce
- RCS
- SCCS
- Subversion

### Tags and versioning

Each OpenGrok release triggers creation of new Docker image.

| Tag      | Note                                                    |
| -------- |:--------------------------------------------------------|
| `master` | corresponds to the latest commit in the OpenGrok repo   |
| `latest` | tracks the latest [released version](https://github.com/oracle/opengrok/releases) |
| `x.y.z`  | if you want to pin against a specific version           |
| `x.y`    | stay on micro versions to avoid reindexing from scratch |

If you want to stay on the bleeding edge, use the `opengrok/docker:master` image which is automatically refreshed whenever a commit is made to the OpenGrok source code repository. This allows to track the development. After all, this is what http://demo.opengrok.org/ is running.

For other use cases, stick to the other image tags.

## How to run

### From DockerHub

    docker run -d -v <path/to/your/src>:/opengrok/src -p 8080:8080 opengrok/docker:latest

The container exports ports 8080 for OpenGrok.

The volume mounted to `/opengrok/src` should contain the projects you want to make searchable (in sub directories). You can use common revision control checkouts (git, svn, etc...) and OpenGrok will make history and blame information available.

## Directories

The image contains these directories:

| Directory | Description |
| --------- | ----------- |
`/opengrok/etc` | stores the configuration for both web app and indexer
`/opengrok/data` | data root - index data
`/opengrok/src` | source root - input data
`/scripts` | startup script and top level configuration. Do not override unless debugging.

## Environment Variables

| Docker Environment Var. | Default value | Description |
| ----------------------- | -------- | ----------- |
`SYNC_PERIOD_MINUTES` | 10 | Period of automatic synchronization (i.e. mirroring + reindexing) in minutes. Setting to `0` will disable periodic syncing (the sync after container startup will still be done).
`INDEXER_OPT` | empty | pass **extra** options to OpenGrok Indexer. For example, `-i d:vendor` will remove all the `*/vendor/*` files from the index. You can check the indexer options on https://github.com/oracle/opengrok/wiki/Python-scripts-transition-guide. The default set of indexer options is: `--remote on -P -H -W`. Do not add `-R` as it is used internally. Rather, see below for the `READONLY_CONFIG_FILE` environment variable.
`INDEXER_JAVA_OPTS` | empty | pass **extra** Java options to OpenGrok Indexer.
`NOMIRROR` | empty | To avoid the mirroring step, set the variable to non-empty value.
`URL_ROOT` | `/` | Override the sub-URL that OpenGrok should run on.
`WORKERS` | number of CPUs in the container | number of workers to use for syncing (applies only to setup with projects enabled)
`AVOID_PROJECTS` | empty | run in project less configuration. Set to non empty value disables projects. Also disables repository synchronization.
`REST_PORT` | 5000 | TCP port where simple REST app listens for GET requests on `/reindex` to trigger manual reindex.
`REST_TOKEN` | None | if set, the REST app will require this token as Bearer token in order to trigger reindex.
`READONLY_CONFIG_FILE` | None | if set, this [read-only configuration](https://github.com/oracle/opengrok/wiki/Read-only-configuration) file will be merged with configuration from this file. This is done when the container starts. This file has to be distinct from the default configuration file (`/opengrok/etc/configuration.xml`), e.g. `/opengrok/etc/read-only-config.xml`.
`CHECK_INDEX` | None | if set, the format of the index will be checked first. **If the index is not compatible with the currently running version, the data root will be wiped out and reindex from scratch will be performed.**
`API_TIMEOUT` | 8 | Timeout for synchronous API requests. In seconds.

To specify environment variable for `docker run`, use the `-e` option, e.g. `-e SYNC_PERIOD_MINUTES=30`

## Repository synchronization

To get more control over repository synchronization (enabled only when projects
are enabled), the `/opengrok/etc/mirror.yml` configuration file can be modified
as per the https://github.com/oracle/opengrok/wiki/Repository-synchronization
wiki.

## OpenGrok Web-Interface

The container has OpenGrok as default web app installed (accessible directly from `/`). With the above container setup, you can find it running on

http://localhost:8080/

The first reindex will take some time to finish. Subsequent reindex will be incremental so will take signigicantly less time.

## Using Docker compose

[Docker-compose](https://docs.docker.com/compose/install/) example:

```yaml
version: "3"

# More info at https://github.com/oracle/opengrok/docker/
services:
  opengrok:
    container_name: opengrok
    image: opengrok/docker:latest
    ports:
      - "8080:8080/tcp"
    environment:
      SYNC_PERIOD_MINUTES: '60'
    # Volumes store your data between container upgrades
    volumes:
       - '~/opengrok/src/:/opengrok/src/'  # source code
       - '~/opengrok/etc/:/opengrok/etc/'  # folder contains configuration.xml
       - '~/opengrok/data/:/opengrok/data/'  # index and other things for source code
```

Save the file into `docker-compose.yml` and then simply run

    docker-compose up -d

Equivalent `docker run` command would look like this:

```bash
docker run -d \
    --name opengrok \
    -p 8080:8080/tcp \
    -e SYNC_PERIOD_MINUTES="60" \
    -v ~/opengrok-src/:/opengrok/src/ \
    -v ~/opengrok-etc/:/opengrok/etc/ \
    -v ~/opengrok-data/:/opengrok/data/ \
    opengrok/docker:latest
```

## Build image locally

If you want to do your own development, you can build the image yourself:

    git clone https://github.com/oracle/opengrok.git
    cd opengrok
    docker buildx build -t opengrok-dev .

Then run the container:

    docker run -d -v <path/to/your/src>:/opengrok/src -p 8080:8080 opengrok-dev

## Inspecting the container

You can get inside a container using the [command below](https://docs.docker.com/engine/reference/commandline/exec/):

```bash
docker exec -it <container> bash
```

Enjoy.
