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
package org.opengrok.indexer.web.messages;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;
import org.opengrok.indexer.web.api.constraints.PositiveDuration;

import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static org.opengrok.indexer.web.messages.MessagesContainer.MESSAGES_MAIN_PAGE_TAG;

public class Message implements Comparable<Message> {

    @NotEmpty(message = "tags cannot be empty")
    private Set<String> tags = Collections.singleton(MESSAGES_MAIN_PAGE_TAG);

    private String cssClass = "info";

    @NotBlank(message = "text cannot be empty")
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

        this.text = text;
        this.tags = tags;
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

    private static class DurationSerializer extends StdSerializer<Duration> {

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

}
