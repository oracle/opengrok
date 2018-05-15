/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import com.google.gson.Gson;
import org.opensolaris.opengrok.configuration.messages.util.MessageReader;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.util.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageListener {

    public static final String MESSAGES_MAIN_PAGE_TAG = "main";

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageListener.class);

    private static final int DEFAULT_MESSAGE_LIMIT = 100;

    private final Gson gson = new Gson();

    private final Map<Class<? extends Message>, Set<MessageHandler>> messageHandlers = new HashMap<>();

    private ServerSocket configServerSocket;

    /*
    initial capacity - default 16
    initial load factor - default 0.75f
    initial concurrency level - number of concurrently updating threads (default 16)
        - just two (the timer, configuration listener) so set it to small value
    */
    private final ConcurrentMap<String, SortedSet<Message>> tagMessages = new ConcurrentHashMap<>(16, 0.75f, 5);

    private int messagesInTheSystem = 0;

    private int messageLimit = DEFAULT_MESSAGE_LIMIT;

    private Timer expirationTimer;

    public MessageListener() {
    }

    public void addHandler(final Class<? extends Message> messageType, final MessageHandler handler) {
        if (messageType == null) {
            throw new IllegalArgumentException("Cannot add handler for null message type");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Cannot add null handler");
        }

        synchronized (messageHandlers) {
            messageHandlers.computeIfAbsent(messageType, key -> new HashSet<>()).add(handler);
        }
    }

    public void removeHandler(final Class<? extends Message> messageType, final MessageHandler handler) {
        if (messageType == null) {
            throw new IllegalArgumentException("Cannot remove handler for null message type");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Cannot remove null handler");
        }

        synchronized (messageHandlers) {
            Set<MessageHandler> listeners = messageHandlers.get(messageType);
            if (listeners == null || !listeners.contains(handler)) {
                LOGGER.log(Level.WARNING, "{0} is not registered as a handler", handler);
                return;
            }
            listeners.remove(handler);
            if (listeners.isEmpty()) {
                messageHandlers.remove(messageType);
            }
        }
    }

    /**
     * Try to stop the configuration listener thread
     */
    public void stopConfigurationListenerThread() {
        IOUtils.close(configServerSocket);
    }

    /**
     * Start a thread to listen on a socket to receive new messages.
     * The messages can contain various commands for the webapp, including
     * upload of new configuration.
     * @param endpoint The socket address to listen on
     * @throws IOException if the endpoint is not available
     */
    public void start(SocketAddress endpoint) throws IOException {
        if (isListening()) {
            throw new IllegalStateException("Cannot start when already listening for messages");
        }
        bindToAddress(endpoint);
        new Thread(() -> {
            while (!configServerSocket.isClosed()) {
                acceptConnection();
            }
        }, "configurationListener").start();
    }

    private boolean isListening() {
        return configServerSocket != null && !configServerSocket.isClosed();
    }

    private void bindToAddress(final SocketAddress address) throws IOException {
        configServerSocket = new ServerSocket();
        configServerSocket.bind(address);
    }

    private void acceptConnection() {
        try (Socket s = configServerSocket.accept();
             MessageReader in = new MessageReader(s.getInputStream());
             OutputStream output = s.getOutputStream()) {

            LOGGER.log(Level.FINE, "OpenGrok: Got request from {0}", s.getInetAddress().getHostAddress());

            Message m = in.readMessage();
            handleMessage(m, output);

        } catch (SocketException e) {
            // if closed then it is okay (socket was closed while blocked in accept())
            if (!configServerSocket.isClosed()) {
                LOGGER.log(Level.SEVERE, "Socket error", e);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading config file: ", e);
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error parsing config file: ", e);
        } catch (ValidationException e) {
            LOGGER.log(Level.WARNING, "Received invalid message", e);
        }
    }

    /**
     * Handle incoming message.
     *
     * @param m message
     * @param output output stream for errors or success
     */
    private void handleMessage(final Message m, final OutputStream output) throws IOException, ValidationException {
        m.validate();

        if (!canAcceptMessage(m)) {
            LOGGER.log(Level.WARNING, "Message dropped: {0} - too many messages in the system", m.getTags());
            output.write(Message.MESSAGE_LIMIT);
            return;
        }

        Response response;
        try {
            response = processMessage(m);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, String.format("Message dropped: %s - message error", m), ex);
            output.write(Message.MESSAGE_ERROR);
            output.write(ex.getMessage().getBytes());
            return;
        }

        LOGGER.log(Level.FINER, "Message received: {0}", m.getTags());
        LOGGER.log(Level.FINER, "Messages in the system: {0}", getMessagesInTheSystem());

        output.write(Message.MESSAGE_OK);
        output.write(gson.toJson(response).getBytes());
        output.flush();
    }

    private Response processMessage(final Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Cannot notify listeners for null message");
        }

        Class<? extends Message> messageType = message.getClass();

        Response finalResponse = Response.empty();
        synchronized (messageHandlers) {
            Set<MessageHandler> listeners = messageHandlers.get(messageType);
            if (listeners != null) {
                for (MessageHandler listener : listeners) {
                    try {
                        Response response = listener.handle(message);
                        finalResponse = finalResponse.combine(response);
                    } catch (Exception e) { // exception in handler should not stop notifying other handlers
                        LOGGER.log(Level.WARNING, "Exception while processing message", e);
                    }
                }
            }
        }
        return finalResponse;
    }

    private static SortedSet<Message> emptyMessageSet(SortedSet<Message> toRet) {
        return toRet == null ? new TreeSet<>() : toRet;
    }

    /**
     * Get the default set of messages for the main tag.
     *
     * @return set of messages
     */
    public SortedSet<Message> getMessages() {
        if (expirationTimer == null) {
            expireMessages();
        }
        return emptyMessageSet(tagMessages.get(MESSAGES_MAIN_PAGE_TAG));
    }

    /**
     * Get the set of messages for the arbitrary tag
     *
     * @param tag the message tag
     * @return set of messages
     */
    public SortedSet<Message> getMessages(String tag) {
        if (expirationTimer == null) {
            expireMessages();
        }
        return emptyMessageSet(tagMessages.get(tag));
    }

    /**
     * Add a message to the application.
     * Also schedules a expiration timer to remove this message after its expiration.
     *
     * @param m the message
     */
    public void addMessage(Message m) {
        if (!canAcceptMessage(m)) {
            return;
        }

        if (expirationTimer == null) {
            expireMessages();
        }

        boolean added = false;
        for (String tag : m.getTags()) {
            if (!tagMessages.containsKey(tag)) {
                tagMessages.put(tag, new ConcurrentSkipListSet<>());
            }
            if (tagMessages.get(tag).add(m)) {
                messagesInTheSystem++;
                added = true;
            }
        }

        if (added) {
            if (expirationTimer != null) {
                expirationTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        expireMessages();
                    }
                }, Date.from(m.getExpiration().plusMillis(10)));
            }
        }
    }

    /**
     * Immediately remove all messages in the application.
     */
    public void removeAllMessages() {
        tagMessages.clear();
        messagesInTheSystem = 0;
    }

    /**
     * Remove all messages containing at least on of the tags.
     *
     * @param tags set of tags
     */
    public void removeAnyMessage(Set<String> tags) {
        removeAnyMessage(t -> t.hasAny(tags));
    }

    /**
     * Remove messages which have expired.
     */
    private void expireMessages() {
        removeAnyMessage(Message::isExpired);
    }

    /**
     * Generic function to remove any message according to the result of the
     * predicate.
     *
     * @param predicate the testing predicate
     */
    private void removeAnyMessage(Predicate<Message> predicate) {
        int size;
        for (Map.Entry<String, SortedSet<Message>> set : tagMessages.entrySet()) {
            size = set.getValue().size();
            set.getValue().removeIf(predicate);
            messagesInTheSystem -= size - set.getValue().size();
        }

        tagMessages.entrySet().removeIf(t -> t.getValue().isEmpty());
    }

    /**
     * Test if the application can receive this messages.
     *
     * @param m the message
     * @return true if it can
     */
    public boolean canAcceptMessage(Message m) {
        return messagesInTheSystem < getMessageLimit() && !m.isExpired();
    }

    /**
     * Get the maximum number of messages in the application
     *
     * @see #getMessagesInTheSystem()
     * @return the number
     */
    public int getMessageLimit() {
        return messageLimit;
    }

    /**
     * Set the maximum number of messages in the application
     *
     * @see #getMessagesInTheSystem()
     * @param limit the new limit
     */
    public void setMessageLimit(int limit) {
        messageLimit = limit;
    }

    /**
     * Return number of messages present in the hash map.
     *
     * DISCLAIMER: This is not the real number of unique messages in the
     * application because the same message is duplicated for all of the tags in
     * the map.
     *
     * This is just a cheap counter to indicate how many messages are stored in
     * total under different tags.
     *
     * Also one can bypass the counter by not calling
     * {@link #addMessage(Message)}
     *
     * @return number of messages
     */
    public int getMessagesInTheSystem() {
        if (expirationTimer == null) {
            expireMessages();
        }
        return messagesInTheSystem;
    }

    public void startExpirationTimer() {
        if (expirationTimer != null) {
            stopExpirationTimer();
        }
        expirationTimer = new Timer("expirationThread");
        expireMessages();
    }

    /**
     * Stops the watch dog service.
     */
    public void stopExpirationTimer() {
        if (expirationTimer != null) {
            expirationTimer.cancel();
            expirationTimer = null;
        }
    }

    public InetAddress getAddress() {
        if (!isListening()) {
            throw new IllegalStateException("Message listener has not started yet");
        }
        return configServerSocket.getInetAddress();
    }

    public int getPort() {
        if (!isListening()) {
            throw new IllegalStateException("Message listener has not started yet");
        }
        return configServerSocket.getLocalPort();
    }

}
