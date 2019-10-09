package org.opengrok.indexer.util;

import org.junit.Test;
import org.mockito.Mockito;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;

public class ProgressTest {
    @Test
    public void testProgress() throws InterruptedException {
        final Logger logger = Mockito.mock(Logger.class);
        final int totalCount = 100; // use int because of Mockito.calls()/times() only accepts int

        // needed to spawn logger thread in Progress
        RuntimeEnvironment.getInstance().setPrintProgress(true);

        Mockito.when(logger.isLoggable(any())).thenReturn(true);
        boolean first = true;
        try (final Progress progress = new Progress(logger, "foo", totalCount)) {
            assertNotNull(progress.getLoggerThread());

            // Progress does not give any indication that the logger thread reached the point when
            // it can accept notifications from increment() so check the Thread state.
            if (first) {
                while (progress.getLoggerThread().getState() != Thread.State.WAITING) {
                    System.out.println("Waiting for the logger thread to reach the initial wait()");
                    TimeUnit.SECONDS.sleep(1);
                }
                first = false;
            }

            for (int i = 0; i < totalCount; i++) {
                progress.increment();
                // Give the logger thread some time to log.
                TimeUnit.MILLISECONDS.sleep(10);
            }
        }

        Mockito.verify(logger, times(totalCount)).log(any(), anyString(), any(Object[].class));
    }
}
