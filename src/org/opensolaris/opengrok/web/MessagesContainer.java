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
package org.opensolaris.opengrok.web;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Predicate;

public class MessagesContainer {

    public static final String MESSAGES_MAIN_PAGE_TAG = "main";

    private static final int DEFAULT_MESSAGE_LIMIT = 100;

    private final Map<String, SortedSet<AcceptedMessage>> tagMessages = new HashMap<>();

    private int messagesInTheSystem = 0;

    private int messageLimit = DEFAULT_MESSAGE_LIMIT;

    private Timer expirationTimer;

    private final Object lock = new Object();

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
     * Get the set of messages for the arbitrary tag
     *
     * @param tag the message tag
     * @return set of messages
     */
    public SortedSet<AcceptedMessage> getMessages(String tag) {
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
                return;
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
                tagMessages.put(tag, new ConcurrentSkipListSet<>());
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
     * Set the maximum number of messages in the application
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

    public static class Message implements Comparable<Message> {

        private Set<String> tags;

        private String cssClass;

        private String text;

        private Duration duration;

        public Message(
                final String text,
                final Set<String> tags,
                final String cssClass,
                final Duration duration
        ) {
            if (text == null || text.isEmpty()) {
                throw new IllegalArgumentException("text cannot be null or empty");
            }
            if (tags == null || tags.isEmpty()) {
                throw new IllegalArgumentException("tags cannot be null or empty");
            }
            if (duration == null || duration.isNegative()) {
                throw new IllegalArgumentException("duration cannot be null or negative");
            }

            Set<String> finalTags = new HashSet<>();
            for (String tag : tags) {
                if (!tag.trim().isEmpty()) {
                    finalTags.add(tag);
                }
            }

            if (finalTags.isEmpty()) {
                throw new IllegalArgumentException("tags contain only blank values");
            }

            this.text = text;
            this.tags = finalTags;
            this.cssClass = cssClass;
            this.duration = duration;
        }

        public Set<String> getTags() {
            return tags;
        }

        public String getCssClass() {
            return cssClass;
        }

        public String getText() {
            return text;
        }

        public Duration getDuration() {
            return duration;
        }

        /**
         * @param t set of tags
         * @return true if message has at least one of the tags
         */
        public boolean hasAny(Set<String> t) {
            Set<String> tmp = new TreeSet<>(t);
            tmp.retainAll(tags);
            return !tmp.isEmpty();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Message message = (Message) o;
            return Objects.equals(tags, message.tags) &&
                    Objects.equals(cssClass, message.cssClass) &&
                    Objects.equals(text, message.text) &&
                    Objects.equals(duration, message.duration);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tags, cssClass, text, duration);
        }

        @Override
        public int compareTo(final Message o) {
            int i;
            if (text != null && (i = text.compareTo(o.text)) != 0) {
                return i;
            }
            if (duration != null && (i = duration.compareTo(o.duration)) != 0) {
                return i;
            }
            return tags.size() - o.tags.size();
        }
    }

    public static class AcceptedMessage implements Comparable<AcceptedMessage> {

        private final Instant acceptedTime = Instant.now();

        private final Message message;

        private AcceptedMessage(final Message message) {
            this.message = message;
        }

        public Instant getAcceptedTime() {
            return acceptedTime;
        }

        public Message getMessage() {
            return message;
        }

        public boolean isExpired() {
            return getExpirationTime().isBefore(Instant.now());
        }

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
    }

}
