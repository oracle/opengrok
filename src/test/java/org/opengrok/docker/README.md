# Docker Image Integration Tests

Integration tests for the OpenGrok Docker image using Testcontainers.

## Running

```bash
# Run integration tests
mvn verify

# Skip if Docker not available
mvn verify -DskipITs=true
```

## What's tested

- Container startup with volume mounts
- File ownership (appuser:appgroup) - catches chown bugs
- Web interface on port 8080
- REST API on port 5000
- Indexer operation
- Non-root process execution
- Startup error detection

## Notes

- Requires Docker to be running
- Takes about 3-5 minutes
- Tests run automatically in CI

Related: issue #4912
