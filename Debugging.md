The `OpenGrok` shell script allows to specify Java debug options using the `JAVA_DEBUG` environment variable. This is especially handy for enabling remote debugger, like so:

```
export JAVA_DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,address=8010,suspend=y"
```

This will make the indexer to listen on the port 8010 until a debugger connects. In Netbeans, select the Debug -> Attach Debugger from the menu and fill in the port number in the dialog window and click Attach.

Profiling
----
For profiling an indexing run, JWDP is not required for a local `ProcessAttach`. It is convenient though to pause the run until the profiler is attached.

### Example in NetBeans

1. Start an indexing run for profiling:
```
$ OPENGROK_PROFILER=1 OpenGrok index --profiler
Loading the default instance configuration ...
Start profiler. Continue (Y/N)? 
```

2. Attach the NetBeans profiler from the menu: Profile => Attach to External Process ... => ... Already running local Java process.
3. Continue the indexing run in the terminal by entering `Y`.
4. After the run, NetBeans will present the analysis.