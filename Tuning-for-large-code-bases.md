# Almost atomic index flip using ZFS

While indexing big source repos you might consider using ZFS filesystem to give
you advantage of datasets which can be flipped over or cloned when needed.
If the machine is strong enough it will also give you an option to
incrementally index in parallel to having the current sources and index in sync.
(So Tomcat sees certain zfs datasets, then you just stop it, flip datasets to
the ones that were updated by SCM/index and start tomcat again - outage is
minimal, sources+indexes are **always** in sync, users see the truth)

# JVM tuning

OpenGrok script by default uses 2GB of heap and 16MB per thread for flush size of
lucene docs indexing(when to flush to disk).
It also uses default 32bit JRE.
This **might not** be enough. You might need to consider this:
Lucene 4.x sets indexer defaults:

```
DEFAULT_RAM_PER_THREAD_HARD_LIMIT_MB = 1945;
DEFAULT_MAX_THREAD_STATES = 8;
DEFAULT_RAM_BUFFER_SIZE_MB = 16.0;
```

* which might grow as big as 16GB (though `DEFAULT_RAM_BUFFER_SIZE_MB` shouldn't
 really allow it, but keep it around 1-2GB)

* the lucenes `RAM_BUFFER_SIZE_MB` can be tuned now using the parameter `-m`, so
running a 8GB 64 bit server JDK indexer with tuned docs flushing(on Solaris 11):

  ```
  # export JAVA=/usr/java/bin/`isainfo -k`/java
  (or use /usr/java/bin/amd64/java )
  # export JAVA_OPTS="-Xmx8192m -server"
  # OPENGROK_FLUSH_RAM_BUFFER_SIZE="-m 256" ./OpenGrok index /source
  ```

Tomcat by default also supports only small deployments. For bigger ones you
**might** need to increase its heap which might necessitate the switch to 64-bit
Java. It will most probably be the same for other containers as well.
For tomcat you can easily get this done by creating `conf/setenv.sh`:

```bash
# cat conf/setenv.sh
# 64-bit Java
JAVA_OPTS="$JAVA_OPTS -d64 -server"

# OpenGrok memory boost to cover all-project searches
# (7 MB * 247 projects + 300 MB for cache should be enough)
# 64-bit Java allows for more so let's use 8GB to be on the safe side.
# We might need to allow more for concurrent all-project searches.
JAVA_OPTS="$JAVA_OPTS -Xmx8g"

export JAVA_OPTS
```

# Tomcat/Apache tuning

For tomcat you might also hit a limit for http header size (we use it to send
the project list when requesting search results):

* increase(add) in `conf/server.xml`

  ```xml
  maxHttpHeaderSize
  connectionTimeout="20000"
  maxHttpHeaderSize="65536"
  redirectPort="8443" />
  ```

  Refer to docs of other containers for more info on how to achieve the same.

The same tuning to Apache can be done with the `LimitRequestLine` directive:

```
LimitRequestLine 65536
LimitRequestFieldSize 65536
```

# Open File and processes hard and soft limits

The initial index creation process is resource intensive and often the error
`java.io.IOException: error=24, Too many open files` appears in the logs. To
avoid this increase the `ulimit` value to a higher number.

It is noted that the hard and soft limit for open files of 10240 works for mid
sized repositories and so the recommendation is to start with 10240.

If you get a similar error, but for threads:
`java.lang.OutOfMemoryError: unable to create new native thread `
it might be due to strict security limits and you need to increase the limits.

# Multi-project search speed tip

If multi-project search is performed frequently, it might be good to warm
up file system cache after each reindex. This can be done e.g. with
<https://github.com/hoytech/vmtouch>