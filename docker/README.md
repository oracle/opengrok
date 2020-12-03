[![](https://images.microbadger.com/badges/image/opengrok/docker.svg)](https://microbadger.com/images/opengrok/docker "Get your own image badge on microbadger.com")
[![](https://images.microbadger.com/badges/version/opengrok/docker.svg)](https://microbadger.com/images/opengrok/docker "Get your own version badge on microbadger.com")

# A Docker container for OpenGrok

## OpenGrok from official source

Built from official source: https://github.com/oracle/opengrok/releases/

You can learn more about OpenGrok at http://oracle.github.io/opengrok/

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

* Tomcat 9
* JRE 11
* Configurable mirroring/reindexing (default every 10 min)

The mirroring step works by going through all projects and attempting to
synchronize all its repositories (e.g. it will do `git pull` for Git
repositories).

### Indexer logs

The indexer/mirroring is set so that it does not log into files.
Rather, everything goes to standard (error) output. To see how the indexer
is doing, use the `docker logs` command.

### Source Code Management systems supported

- Mercurial
- Git
- Subversion

### Tags and versioning

Each OpenGrok release triggers creation of new Docker image. 

| Tag      | Note                                                    |
| -------- |:--------------------------------------------------------|
| `latest` | tracks the latest version                               |
| `x.y.z`  | if you want to pin against a specific version           |
| `x.y`    | stay on micro versions to avoid reindexing from scratch |

## How to run

### From DockerHub

    docker run -d -v <path/to/your/src>:/opengrok/src -p 8080:8080 opengrok/docker:latest

The container exports ports 8080 for OpenGrok.

The volume mounted to `/opengrok/src` should contain the projects you want to make searchable (in sub directories). You can use common revision control checkouts (git, svn, etc...) and OpenGrok will make history and blame information available.

## Environment Variables

| Docker Environment Var. | Default value | Description |
| ----------------------- | ------------- | ----------- |
`REINDEX` | 10 | Period of automatic mirroring/reindexing in minutes. Setting to `0` will disable automatic indexing. You can manually trigger an reindex using docker exec: `docker exec <container> /scripts/index.sh`
`INDEXER_OPT` | empty | pass extra options to OpenGrok Indexer. For example, `-i d:vendor` will remove all the `*/vendor/*` files from the index. You can check the indexer options on https://github.com/oracle/opengrok/wiki/Python-scripts-transition-guide
`NOMIRROR` | empty | To avoid the mirroring step, set the variable to non-empty value.
`URL_ROOT` | `/` | Override the sub-URL that OpenGrok should run on.

To specify environment variable for `docker run`, use the `-e` option, e.g. `-e REINDEX=30`

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
      REINDEX: '60'
    # Volumes store your data between container upgrades
    volumes:
       - '~/opengrok-src/:/opengrok/src/'  # source code
       - '~/opengrok-etc/:/opengrok/etc/'  # folder contains configuration.xml
       - '~/opengrok-data/:/opengrok/data/'  # index and other things for source code
```

Save the file into `docker-compose.yml` and then simply run

    docker-compose up -d

Equivalent `docker run` command would look like this:

```bash
docker run -d \
    --name opengrok \
    -p 8080:8080/tcp \
    -e REINDEX="60" \
    -v ~/opengrok-src/:/opengrok/src/ \
    -v ~/opengrok-etc/:/opengrok/etc/ \
    -v ~/opengrok-data/:/opengrok/data/ \
    opengrok/docker:latest
```

## Build image locally

If you want to do your own development, you can build the image yourself:

    git clone https://github.com/oracle/opengrok.git
    cd opengrok
    docker build -t opengrok-dev .

Then run the container:

    docker run -d -v <path/to/your/src>:/opengrok/src -p 8080:8080 opengrok-dev

## Inspecting the container

You can get inside a container using the [command below](https://docs.docker.com/engine/reference/commandline/exec/):

```bash
docker exec -it <container> bash
```

Enjoy.
