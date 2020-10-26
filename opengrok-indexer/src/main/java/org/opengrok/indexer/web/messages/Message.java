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
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.web.messages;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import org.opengrok.indexer.web.Util;
import org.opengrok.indexer.web.api.constraints.PositiveDuration;

import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static org.opengrok.indexer.web.messages.MessagesContainer.MESSAGES_MAIN_PAGE_TAG;

public class Message implements Comparable<Message>, JSONable {

    @NotEmpty(message = "tags cannot be empty")
    private Set<String> tags = Collections.singleton(MESSAGES_MAIN_PAGE_TAG);

    public enum MessageLevel {
        /**
         * Known values: {@code SUCCESS}, {@code INFO}, {@code WARNING}, {@code ERROR}.
         * The values are sorted according to their level. Higher numeric value of the level (i.e. the enum ordinal)
         * means higher priority.
         */
        SUCCESS("success"), INFO("info"), WARNING("warning"), ERROR("error");

        private final String messageLevelString;

        MessageLevel(String str) {
            messageLevelString = str;
        }

        public static MessageLevel fromString(String val) throws IllegalArgumentException {
            for (MessageLevel v : MessageLevel.values()) {
                if (v.toString().equals(val.toLowerCase(Locale.ROOT))) {
                    return v;
                }
            }
            throw new IllegalArgumentException("class type does not match any known value");
        }

        @Override
        public String toString() {
            return messageLevelString;
        }

        @SuppressWarnings("rawtypes")
        public static final Comparator<MessageLevel> VALUE_COMPARATOR = Comparator.comparingInt(Enum::ordinal);
    }

    @JsonDeserialize(using = MessageLevelDeserializer.class)
    @JsonSerialize(using = MessageLevelSerializer.class)
    private MessageLevel messageLevel = MessageLevel.INFO;

    @NotBlank(message = "text cannot be empty")
    @JsonSerialize(using = HTMLSerializer.class)
    private String text;

    @PositiveDuration(message = "duration must be positive")
    @JsonSerialize(using = DurationSerializer.class)
    @JsonDeserialize(using = DurationDeserializer.class)
    private Duration duration = Duration.ofMinutes(10);

    private Message() { // needed for deserialization
    }

    public Message(
            final String text,
            final Set<String> tags,
            final MessageLevel messageLevel,
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

        this.text = text;
        this.tags = tags;
        this.messageLevel = messageLevel;
        this.duration = duration;
    }

    public Set<String> getTags() {
        return tags;
    }

    public MessageLevel getMessageLevel() {
        return messageLevel;
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

    /**
     * @param tags set of tags to check
     * @param text message text
     * @return true if a mesage has at least one of the tags and text
     */
    public boolean hasTagsAndText(Set<String> tags, String text) {
        if (text == null || text.isEmpty()) {
            return hasAny(tags);
        }

        return hasAny(tags) && getText().equals(text);
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
                Objects.equals(messageLevel, message.messageLevel) &&
                Objects.equals(text, message.text) &&
                Objects.equals(duration, message.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tags, messageLevel, text, duration);
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

    static class MessageLevelSerializer extends StdSerializer<MessageLevel> {
        private static final long serialVersionUID = 928540953227342817L;

        MessageLevelSerializer() {
            this(null);
        }

        MessageLevelSerializer(Class<MessageLevel> vc) {
            super(vc);
        }

        @Override
        public void serialize(final MessageLevel messageLevel,
                                final JsonGenerator jsonGenerator,
                                final SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeString(messageLevel.toString().toLowerCase(Locale.ROOT));
        }
    }

    private static class MessageLevelDeserializer extends StdDeserializer<MessageLevel> {
        private static final long serialVersionUID = 928540953227342817L;

        MessageLevelDeserializer() {
            this(null);
        }

        MessageLevelDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public MessageLevel deserialize(final JsonParser parser, final DeserializationContext context)
                throws IOException {
            try {
                return MessageLevel.fromString(context.readValue(parser, String.class));
            } catch (DateTimeParseException e) {
                throw new IOException(e);
            }
        }
    }

    private static class DurationSerializer extends StdSerializer<Duration> {

        private static final long serialVersionUID = 5275434375701446542L;

        DurationSerializer() {
            this(null);
        }

        DurationSerializer(final Class<Duration> cl) {
            super(cl);
        }

        @Override
        public void serialize(
                final Duration duration,
                final JsonGenerator jsonGenerator,
                final SerializerProvider serializerProvider
        ) throws IOException {
            if (duration != null) {
                jsonGenerator.writeString(duration.toString());
            } else {
                jsonGenerator.writeNull();
            }
        }
    }

    private static class DurationDeserializer extends StdDeserializer<Duration> {
        private static final long serialVersionUID = 5513386447457242809L;

        DurationDeserializer() {
            this(null);
        }

        DurationDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public Duration deserialize(final JsonParser parser, final DeserializationContext context)
                throws IOException {
            try {
                return Duration.parse(context.readValue(parser, String.class));
            } catch (DateTimeParseException e) {
                throw new IOException(e);
            }
        }
    }

    protected static class HTMLSerializer extends StdSerializer<String> {

        private static final long serialVersionUID = -2843900664165513923L;

        HTMLSerializer() {
            this(null);
        }

        HTMLSerializer(final Class<String> cl) {
            super(cl);
        }

        @Override
        public void serialize(
                final String string,
                final JsonGenerator jsonGenerator,
                final SerializerProvider serializerProvider
        ) throws IOException {
            if (string != null) {
                jsonGenerator.writeString(Util.encode(string));
            } else {
                jsonGenerator.writeNull();
            }
        }
    }
}
