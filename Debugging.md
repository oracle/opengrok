The `OpenGrok` shell script allows to specify Java debug options using the `JAVA_DEBUG` environment variable. This is especially handy for enabling remote debugger, like so:

```
JAVA_DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,address=8010,suspend=y"
```
