## Debugging

### Indexer

When running the indexer, add the 

```
export JAVA_DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,address=8010,suspend=y"
```

This will make the indexer to listen on the port 8010 until a debugger connects. 

In Netbeans, select the Debug -> Attach Debugger from the menu and fill in the port number in the dialog window and click Attach.

Simply insert a breakpoint either in the Indexer code or the webapp.

### Web application

To debug the web application the most generic way would be to add debug parameters to the application server.

For Tomcat, create the `bin/setenv.sh` file with the following contents:

```shell
CATALINA_OPTS="-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n"
```

Then restart Tomcat and then you can simply use remote debugging from your IDE.

## Profiling

For profiling an indexing run, JWDP is not required for a local `ProcessAttach`. It is convenient though to pause the run until the profiler is attached.

### Example in NetBeans

1. Start an indexing run in a terminal for profiling:
```
$ OPENGROK_PROFILER=1 OpenGrok index --profiler
Loading the default instance configuration ...
Start profiler. Continue (Y/N)? 
```

2. Attach the NetBeans profiler from the menu: Profile -> Attach to External Process ... -> ... Already running local Java process.
3. Continue the indexing run in the terminal by entering `Y`.
4. After the run, NetBeans will present the analysis.