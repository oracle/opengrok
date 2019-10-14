package org.opengrok.indexer.util;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
        Thread loggerThread = null;

        Mockito.when(logger.isLoggable(any())).thenReturn(true);
        try (final Progress progress = new Progress(logger, "foo", totalCount)) {
            assertNotNull(progress.getLoggerThread());
            loggerThread = progress.getLoggerThread();

            // Progress does not give any indication that the logger thread reached the point when
            // it can accept notifications from increment() so check the Thread state.
            while (loggerThread.getState() != Thread.State.WAITING) {
                System.out.println("Waiting for the logger thread to reach the initial wait()");
                TimeUnit.MILLISECONDS.sleep(10);
            }

            for (int i = 0; i < totalCount; i++) {
                progress.increment();
                // Give the logger thread some time to log.
                while (loggerThread.getState() != Thread.State.WAITING) {
                    TimeUnit.MILLISECONDS.sleep(10);
                }
            }
        }

        System.out.println("Waiting for the logger thread to terminate");
        int i = 0;
        while (i < 3 && loggerThread.getState() != Thread.State.TERMINATED) {
            TimeUnit.MILLISECONDS.sleep(10);
            i++;
        }
        Assert.assertSame(loggerThread.getState(), Thread.State.TERMINATED);

        Mockito.verify(logger, times(totalCount)).log(any(), anyString(), any(Object[].class));
    }

    @Test
    public void testThreads() throws InterruptedException {
        final Logger logger = Mockito.mock(Logger.class);
        final int totalCount = Runtime.getRuntime().availableProcessors();
        List<Future<?>> futures = new ArrayList<>();

        Mockito.when(logger.isLoggable(any())).thenReturn(true);
        ExecutorService executor = Executors.newFixedThreadPool(totalCount);
        System.out.println(String.format("Will run %d threads", totalCount));
        try (final Progress progress = new Progress(logger, "foo", totalCount)) {
            while (progress.getLoggerThread().getState() != Thread.State.WAITING) {
                System.out.println("Waiting for the logger thread to reach the initial wait()");
                TimeUnit.MILLISECONDS.sleep(10);
            }

            for (int i = 0; i < totalCount; i++) {
                futures.add(executor.submit(progress::increment));
            }

            System.out.println("Waiting for threads to finish");
            futures.forEach(future -> {
                try {
                    future.get();
                } catch (Exception e) {
                    Assert.fail();
                }
            });
        }

        executor.shutdown();
        System.out.println("Verifying");
        Mockito.verify(logger, atLeast(1)).log(any(), anyString(), any(Object[].class));
    }
}
