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
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.opensolaris.opengrok.configuration.messages.util.MessageWriter;
import org.opensolaris.opengrok.util.IOUtils;

/**
 * If you extend this file, don't forget to add an information into the root
 * README.
 *
 * @author Kryštof Tulinger
 */
public abstract class Message implements Comparable<Message> {

    public static final String DELIMITER = "\0";

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Message.class, new MessageDeserializer())
            .registerTypeAdapter(Duration.class, new DurationAdapter())
            .create();

    private String text;
    private String cssClass;
    private Set<String> tags = new TreeSet<>();

    private Duration duration = Duration.of(10, ChronoUnit.MINUTES);

    private final String type;

    protected Message() {
        type = getClass().getName();
    }

    /**
     * Validate the current message and throw an exception if the message is not
     * valid. Further implementation can override this method to get the message
     * into a state they would expect.
     *
     * @throws ValidationException if message has invalid format
     */
    public void validate() throws ValidationException {
        if (duration == null || duration.isNegative()) {
            throw new ValidationException("The message must contain a duration.");
        }
    }

    public String getText() {
        return text;
    }

    public String getCssClass() {
        return cssClass;
    }

    public String getType() {
        return type;
    }

    /**
     * @param t set of tags
     * @return true if message has at least on of the tags
     */
    public boolean hasAny(Set<String> t) {
        Set<String> tmp = new TreeSet<>(t);
        tmp.retainAll(tags);
        return !tmp.isEmpty();
    }

    /**
     *
     * @param t set of tags
     * @return true if message has all of the tags
     */
    public boolean hasAll(Set<String> t) {
        return tags.containsAll(t);
    }

    /**
     *
     * @param tag the tag
     * @return true if message has this tag
     */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public Set<String> getTags() {
        return new TreeSet<>(tags);
    }

    public Duration getDuration() {
        return duration;
    }

    protected Set<String> getDefaultTags() {
        return Collections.emptySet();
    }

    protected String getDefaultCssClass() {
        return null;
    }

    @Override
    public int compareTo(Message m) {
        int cmpRes;
        if (text != null && (cmpRes = getText().compareTo(m.getText())) != 0) {
            return cmpRes;
        }
        if ((cmpRes = duration.compareTo(m.duration)) != 0) {
            return cmpRes;
        }

        return getTags().size() - m.getTags().size();
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, tags, duration);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Message other = (Message) obj;

        return Objects.equals(duration, other.duration)
                && Objects.equals(this.tags, other.tags)
                && Objects.equals(this.text, other.text);
    }

    @Override
    public String toString() {
        return "Message{" +
                "text='" + text + '\'' +
                ", tags=" + tags +
                ", type='" + type + '\'' +
                '}';
    }

    /**
     * Serialize the message and send it into the socket.
     *
     * @param host host
     * @param port port number
     * @throws IOException if I/O exception occurred
     *
     * @see #throwIfError(DeliveryStatus, String message)
     *
     * @return possible output for this application, null if no output
     */
    public Response write(final String host, final int port) throws IOException {
        try (Socket sock = new Socket(host, port)) {
            try (MessageWriter mos = new MessageWriter(sock.getOutputStream());
                 InputStream input = sock.getInputStream()) {

                mos.writeMessage(this);

                DeliveryStatus deliveryStatus = DeliveryStatus.fromStatusCode(input.read());
                if (deliveryStatus != DeliveryStatus.OK) {
                    throwIfError(deliveryStatus, IOUtils.toString(input));
                }

                try (InputStreamReader reader = new InputStreamReader(input)) {
                    return gson.fromJson(reader, Response.class);
                }
            }
        }
    }

    public String getEncoded() {
        return gson.toJson(this);
    }

    public static Message decode(final String s) {
        return gson.fromJson(s, Message.class);
    }

    /**
     * Decode the return code from the remote server.
     *
     * @param deliveryStatus status of the message delivery
     * @param message error message stored in string
     * @throws IOException if the return code meant an error
     */
    protected void throwIfError(final DeliveryStatus deliveryStatus, final String message) throws IOException {
        switch (deliveryStatus) {
            case OK:
                break;
            case OVER_LIMIT:
                throw new IOException(
                        String.format(
                                "Message was not accepted by the remote server - "
                                + "%s (error %#x).",
                                message.length() > 0 ? message : "too many messages in the system",
                                deliveryStatus.statusCode));
            default:
                throw new IOException(
                        String.format(
                                "Message was not accepted by the remote server %s%s%s(error %#x).",
                                message.length() > 0 ? "- " : "",
                                message.length() > 0 ? message : "",
                                message.length() > 0 ? " " : "",
                                deliveryStatus.statusCode));
        }
    }

    public enum DeliveryStatus {

        OK(0x1), OVER_LIMIT(0x2), ERROR(0x4);

        private final int statusCode;

        DeliveryStatus(final int statusCode) {
            this.statusCode = statusCode;
        }

        public static DeliveryStatus fromStatusCode(final int statusCode) {
            for (DeliveryStatus ds : values()) {
                if (ds.statusCode == statusCode) {
                    return ds;
                }
            }
            throw new IllegalArgumentException("Unknown status code " + statusCode);
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    private static class MessageDeserializer implements JsonDeserializer<Message> {

        @Override
        public Message deserialize(
                final JsonElement jsonElement,
                final Type type,
                final JsonDeserializationContext context
        ) {
            String classStr = jsonElement.getAsJsonObject().get("type").getAsString();
            Class<?> cl;
            try {
                cl = Class.forName(classStr);
            } catch (ClassNotFoundException e) {
                throw new JsonParseException(e);
            }
            return context.deserialize(jsonElement, cl);
        }
    }

    private static class DurationAdapter extends TypeAdapter<Duration> {

        @Override
        public void write(final JsonWriter writer, final Duration duration) throws IOException {
            if (duration == null) {
                writer.nullValue();
                return;
            }
            writer.value(duration.toString());
        }

        @Override
        public Duration read(final JsonReader reader) throws IOException {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull();
                return null;
            }
            return Duration.parse(reader.nextString());
        }
    }

    public static class Builder<T extends Message> {

        private final Class<T> messageClass;

        private final Set<String> tags = new TreeSet<>();

        private String text;

        private String cssClass;

        private Duration duration;

        public Builder(final Class<T> cl) {
            messageClass = cl;
        }

        public T build() {
            T result = createMessage(messageClass);
            fillData(result);
            return result;
        }

        private void fillData(final Message message) {
            message.tags.addAll(tags);

            if (message.tags.isEmpty()) {
                Set<String> defaultTags = message.getDefaultTags();
                if (defaultTags != null) {
                    message.tags.addAll(defaultTags);
                }
            }

            if (text != null) {
                message.text = text;
            }

            if (cssClass != null) {
                message.cssClass = cssClass;
            } else {
                message.cssClass = message.getDefaultCssClass();
            }
            if (duration != null) {
                message.duration = duration;
            }
        }

        private static <T> T createMessage(final Class<T> messageClass) {
            try {
                return messageClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Cannot create message", e);
            }
        }

        public Builder<T> addTag(final String tag) {
            if (tag == null) {
                throw new IllegalArgumentException("Tag cannot be null");
            }
            tags.add(tag);
            return this;
        }

        public Builder<T> clearTags() {
            tags.clear();
            return this;
        }

        public Builder<T> setText(final String text) {
            this.text = text;
            return this;
        }

        public Builder<T> setTextFromFile(final String filepath) throws IOException {
            byte[] encoded = Files.readAllBytes(Paths.get(filepath));
            this.text = new String(encoded);
            return this;
        }

        public Builder<T> setCssClass(final String cssClass) {
            this.cssClass = cssClass;
            return this;
        }

        public Builder<T> setDuration(final Duration duration) {
            this.duration = duration;
            return this;
        }

    }
}
