package org.opengrok.indexer.util;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;

public class ProgressTest {
    @BeforeClass
    public static void setup() {
        // needed to spawn logger thread in Progress
        RuntimeEnvironment.getInstance().setPrintProgress(true);
    }

    @Test
    public void testProgress() throws InterruptedException {
        final Logger logger = Mockito.mock(Logger.class);
        final int totalCount = 100; // use int because of Mockito.calls()/times() only accepts int

        Mockito.when(logger.isLoggable(any())).thenReturn(true);
        try (final Progress progress = new Progress(logger, "foo", totalCount)) {
            assertNotNull(progress.getLoggerThread());

            // Progress does not give any indication that the logger thread reached the point when
            // it can accept notifications from increment() so check the Thread state.
            while (progress.getLoggerThread().getState() != Thread.State.WAITING) {
                System.out.println("Waiting for the logger thread to reach the initial wait()");
                TimeUnit.MILLISECONDS.sleep(10);
            }

            for (int i = 0; i < totalCount; i++) {
                progress.increment();
                // Give the logger thread some time to log.
                while (progress.getLoggerThread().getState() != Thread.State.WAITING) {
                    TimeUnit.MILLISECONDS.sleep(10);
                }
            }
        }

        Mockito.verify(logger, times(totalCount)).log(any(), anyString(), any(Object[].class));
    }

    @Test
    public void testThreads() throws InterruptedException {
        final Logger logger = Mockito.mock(Logger.class);
        final int totalCount = Runtime.getRuntime().availableProcessors();

        Mockito.when(logger.isLoggable(any())).thenReturn(true);
        ExecutorService executor = Executors.newFixedThreadPool(totalCount);
        try (final Progress progress = new Progress(logger, "foo", totalCount)) {
            for (int i = 0; i < totalCount; i++) {
                executor.submit(progress::increment);
            }
        }

        executor.awaitTermination(10, TimeUnit.SECONDS);
        Mockito.verify(logger, atLeast(1)).log(any(), anyString(), any(Object[].class));
    }
}
