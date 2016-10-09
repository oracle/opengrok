/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

public class ExpirationNormalMessageTest {

    RuntimeEnvironment env;

    private Message[] makeArray(Message... messages) {
        return messages;
    }

    protected void sleep(long milis) {
        try {
            Thread.sleep(milis);
        } catch (InterruptedException ex) {
        }
    }

    @Before
    public void setUp() {
        env = RuntimeEnvironment.getInstance();
        env.removeAllMessages();
    }

    @After
    public void tearDown() {
        env.removeAllMessages();
    }

    @Test
    public void testExpirationSingle() {
        runSingle();
    }

    @Test
    public void testExpirationSingleTimer() {
        env.startExpirationTimer();
        runSingle();
        env.stopExpirationTimer();
    }

    @Test
    public void testExpirationMultiple() {
        runMultiple();
    }

    @Test
    public void testExpirationMultipleTimer() {
        env.startExpirationTimer();
        runMultiple();
        env.stopExpirationTimer();
    }

    /**
     * This doesn't make sense since we're testing the behaviour of the timer
     * thread.
     */
    @Test
    public void testExpirationConcurrent() {
        for (int i = 0; i < 10; i++) {
            runConcurrentModification();
        }
    }

    @Test
    public void testExpirationConcurrentTimer() {
        env.startExpirationTimer();
        for (int i = 0; i < 10; i++) {
            runConcurrentModification();
        }
        env.stopExpirationTimer();
    }

    protected void runSingle() {
        Assert.assertEquals(0, env.getMessagesInTheSystem());
        NormalMessage m1 = new NormalMessage();
        m1.addTag("main")
                .setExpiration(new Date(System.currentTimeMillis() + 500));
        env.addMessage(m1);
        Assert.assertEquals(1, env.getMessagesInTheSystem());

        for (int i = 0; i < 5; i++) {
            Assert.assertEquals(1, env.getMessagesInTheSystem());
            Assert.assertNotNull(env.getMessages());
            Assert.assertEquals(new TreeSet<Message>(Arrays.asList(makeArray(m1))), env.getMessages());
            sleep(100);
        }
        sleep(30);
        Assert.assertEquals(0, env.getMessagesInTheSystem());
    }

    protected void runMultiple() {
        Assert.assertEquals(0, env.getMessagesInTheSystem());
        NormalMessage m1 = new NormalMessage();
        m1.addTag("main")
                .setExpiration(new Date(System.currentTimeMillis() + 300));
        env.addMessage(m1);

        NormalMessage m2 = new NormalMessage();
        m2.addTag("main")
                .setExpiration(new Date(System.currentTimeMillis() + 600));
        env.addMessage(m2);

        Assert.assertEquals(2, env.getMessagesInTheSystem());
        Assert.assertNotNull(env.getMessages());
        Assert.assertEquals(new TreeSet<Message>(Arrays.asList(makeArray(m1, m2))), env.getMessages());

        // expire first
        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(2, env.getMessagesInTheSystem());
            Assert.assertNotNull(env.getMessages());
            Assert.assertEquals(new TreeSet<Message>(Arrays.asList(makeArray(m1, m2))), env.getMessages());
            sleep(100);
        }
        sleep(30);
        // expire second
        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(1, env.getMessagesInTheSystem());
            Assert.assertNotNull(env.getMessages());
            Assert.assertEquals(new TreeSet<Message>(Arrays.asList(makeArray(m2))), env.getMessages());
            sleep(100);
        }
        sleep(30);
        Assert.assertEquals(0, env.getMessagesInTheSystem());
    }

    protected void runConcurrentModification() {
        long current = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            NormalMessage m = new NormalMessage();
            m.addTag("main");
            m.setExpiration(new Date(current + 50000));
            m.setCreated(new Date(current - 2000 - i));
            m.apply(env);
        }

        Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread th, Throwable ex) {
                if (ex instanceof ConcurrentModificationException) {
                    Assert.fail("The messages shouldn't throw an concurrent modification exception");
                } else {
                    Assert.fail("The messages shouldn't throw any other exception, too");
                }
            }
        };

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                invokeExpireMessages();
            }
        });
        t.setUncaughtExceptionHandler(h);

        Assert.assertEquals(500, env.getMessagesInTheSystem());
        Assert.assertEquals(500, env.getMessages("main").size());

        for (Message m : env.getMessages("main")) {
            m.setExpiration(new Date(current - 2000));
        }

        for (int i = 0; i < 500; i++) {
            if (i == 100) {
                t.start();
            }
            try {
                for (Message m : env.getMessages("main")) {
                    m.setText("Hello message");
                    Assert.assertNotNull(m.getText());
                }
            } catch (ConcurrentModificationException ex) {
                Assert.fail("The messages shouldn't throw an concurrent modification exception");
            } catch (Throwable ex) {
                Assert.fail("The messages shouldn't throw any other exception, too");
            }
        }
        try {
            t.join();
        } catch (InterruptedException ex) {
        }
        Assert.assertEquals(0, env.getMessagesInTheSystem());
        Assert.assertEquals(0, env.getMessages("main").size());
    }

    private void invokeExpireMessages() {
        try {
            Method method = RuntimeEnvironment.class.getDeclaredMethod("expireMessages");
            method.setAccessible(true);
            method.invoke(env);
        } catch (Exception ex) {
            Assert.fail("invokeRemoveAll should not throw an exception");
        }
    }

    @SuppressWarnings("unchecked")
    protected Map<String, SortedSet<Message>> getTagMessages() {
        try {
            Field field = RuntimeEnvironment.class.getDeclaredField("tagMessages");
            field.setAccessible(true);
            return (Map<String, SortedSet<Message>>) field.get(env);
        } catch (Throwable ex) {
            Assert.fail("invoking getTagMessages should not throw an exception");
        }
        return null;
    }
}
