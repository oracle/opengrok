The web application has to have writable access to the data as it employs RESTful APIs that need to be able modify the data such as configuration, index data etc.

For security conscious setup, it is desirable to enable HTTPS handling in the application server. However, the indexer needs to be able to send RESTful API rrequests to the web application during indexing. These requests need to pass through localhost. This means that the application server has to be setup to allow plain HTTP communication on certain port (say 8080) and expose the port to localhost.

Also see https://github.com/oracle/opengrok/wiki/Authorization

If you find what you think is a security problem, report it via email to one of the core contributors.