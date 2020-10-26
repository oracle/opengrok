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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.web.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.opengrok.indexer.logger.LoggerFactory;

import javax.validation.constraints.NotBlank;

import java.io.IOException;
import java.text.DateFormat;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MessagesContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessagesContainer.class);

    public static final String MESSAGES_MAIN_PAGE_TAG = "main";

    private static final int DEFAULT_MESSAGE_LIMIT = 100;

    private final Map<String, SortedSet<AcceptedMessage>> tagMessages = new HashMap<>();

    private int messagesInTheSystem = 0;

    private int messageLimit = DEFAULT_MESSAGE_LIMIT;

    private Timer expirationTimer;

    private final Object lock = new Object();

    /**
     * @return all messages regardless their tag
     */
    public Set<AcceptedMessage> getAllMessages() {
        synchronized (lock) {
            if (expirationTimer == null) {
                expireMessages();
            }
            return tagMessages.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
        }
    }

    /**
     * Get the default set of messages for the main tag.
     *
     * @return set of messages
     */
    public SortedSet<AcceptedMessage> getMessages() {
        synchronized (lock) {
            if (expirationTimer == null) {
                expireMessages();
            }
            return emptyMessageSet(tagMessages.get(MESSAGES_MAIN_PAGE_TAG));
        }
    }

    /**
     * Get the set of messages for the arbitrary tag.
     *
     * @param tag the message tag
     * @return set of messages
     */
    public SortedSet<AcceptedMessage> getMessages(final String tag) {
        if (tag == null) {
            throw new IllegalArgumentException("Cannot get messages for null tag");
        }

        synchronized (lock) {
            if (expirationTimer == null) {
                expireMessages();
            }
            return emptyMessageSet(tagMessages.get(tag));
        }
    }

    /**
     * Add a message to the application.
     * Also schedules a expiration timer to remove this message after its expiration.
     *
     * @param m the message
     */
    public void addMessage(final Message m) {
        synchronized (lock) {
            if (m == null) {
                throw new IllegalArgumentException("Cannot add null message");
            }

            if (isMessageLimitExceeded()) {
                LOGGER.log(Level.WARNING, "cannot add message to the system, " +
                                "exceeded Configuration messageLimit of {0}", messageLimit);
                throw new IllegalStateException("Cannot add message - message limit exceeded");
            }

            if (expirationTimer == null) {
                expireMessages();
            }

            AcceptedMessage acceptedMessage = new AcceptedMessage(m);
            addMessage(acceptedMessage);
        }
    }

    private void addMessage(final AcceptedMessage acceptedMessage) {
        boolean added = false;
        for (String tag : acceptedMessage.getMessage().getTags()) {
            if (!tagMessages.containsKey(tag)) {
                tagMessages.put(tag, new TreeSet<>());
            }
            if (tagMessages.get(tag).add(acceptedMessage)) {
                messagesInTheSystem++;
                added = true;
            }
        }

        if (added && expirationTimer != null) {
            expirationTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    expireMessages();
                }
            }, Date.from(acceptedMessage.getExpirationTime().plusMillis(10)));
        }
    }

    /**
     * Remove all messages containing at least one of the tags.
     *
     * @param tags set of tags
     */
    public void removeAnyMessage(Set<String> tags) {
        if (tags == null) {
            return;
        }
        removeAnyMessage(t -> t.getMessage().hasAny(tags));
    }

    public void removeAnyMessage(Set<String> tags, String text) {
        if (tags == null) {
            return;
        }
        removeAnyMessage(t -> t.getMessage().hasTagsAndText(tags, text));
    }

    /**
     * Remove messages which have expired.
     */
    private void expireMessages() {
        removeAnyMessage(AcceptedMessage::isExpired);
    }

    /**
     * Generic function to remove any message according to the result of the
     * predicate.
     *
     * @param predicate the testing predicate
     */
    private void removeAnyMessage(Predicate<AcceptedMessage> predicate) {
        synchronized (lock) {
            int size;
            for (Map.Entry<String, SortedSet<AcceptedMessage>> set : tagMessages.entrySet()) {
                size = set.getValue().size();
                set.getValue().removeIf(predicate);
                messagesInTheSystem -= size - set.getValue().size();
            }

            tagMessages.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
    }

    private boolean isMessageLimitExceeded() {
        return messagesInTheSystem >= messageLimit;
    }

    /**
     * Set the maximum number of messages in the application.
     *
     * @param limit the new limit
     */
    public void setMessageLimit(int limit) {
        messageLimit = limit;
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

    private static SortedSet<AcceptedMessage> emptyMessageSet(SortedSet<AcceptedMessage> toRet) {
        return toRet == null ? new TreeSet<>() : toRet;
    }

    public static class AcceptedMessage implements Comparable<AcceptedMessage>, JSONable {

        private final Instant acceptedTime = Instant.now();

        // The message member is ignored so that it can be flattened using the getters specified below.
        @JsonIgnore
        private final Message message;

        @JsonProperty("text")
        @NotBlank(message = "text cannot be empty")
        @JsonSerialize(using = Message.HTMLSerializer.class)
        public String getText() {
            return message.getText();
        }

        @JsonProperty("messageLevel")
        @JsonSerialize(using = Message.MessageLevelSerializer.class)
        public Message.MessageLevel getMessageLevel() {
            return message.getMessageLevel();
        }

        private AcceptedMessage(final Message message) {
            this.message = message;
        }

        @JsonProperty("created")
        @JsonSerialize(using = InstantSerializer.class)
        public Instant getAcceptedTime() {
            return acceptedTime;
        }

        @JsonProperty("tags")
        public Set<String> getTags() {
            return message.getTags();
        }

        public Message getMessage() {
            return message;
        }

        public boolean isExpired() {
            return getExpirationTime().isBefore(Instant.now());
        }

        @JsonProperty("expiration")
        @JsonSerialize(using = InstantSerializer.class)
        public Instant getExpirationTime() {
            return acceptedTime.plus(message.getDuration());
        }

        @Override
        public int compareTo(final AcceptedMessage o) {
            int cmpRes = acceptedTime.compareTo(o.acceptedTime);
            if (cmpRes == 0) {
                return message.compareTo(o.message);
            }
            return cmpRes;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AcceptedMessage that = (AcceptedMessage) o;
            return Objects.equals(acceptedTime, that.acceptedTime)
                    && Objects.equals(message, that.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(acceptedTime, message);
        }

        private static class InstantSerializer extends StdSerializer<Instant> {

            private static final long serialVersionUID = -369908820170764793L;

            InstantSerializer() {
                this(null);
            }

            InstantSerializer(final Class<Instant> cl) {
                super(cl);
            }

            @Override
            public void serialize(
                    final Instant instant,
                    final JsonGenerator jsonGenerator,
                    final SerializerProvider serializerProvider
            ) throws IOException {
                if (instant != null) {
                    DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG, Locale.ROOT);
                    jsonGenerator.writeString(formatter.format(Date.from(instant)));
                } else {
                    jsonGenerator.writeNull();
                }
            }
        }
    }

}
