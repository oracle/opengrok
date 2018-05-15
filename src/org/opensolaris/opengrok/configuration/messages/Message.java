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
import java.time.Instant;
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
            .create();

    public static final int MESSAGE_OK = 0x1;
    public static final int MESSAGE_LIMIT = 0x2;
    public static final int MESSAGE_ERROR = 0x4;

    protected static final long DEFAULT_EXPIRATION_IN_MINUTES = 10;

    private String text;
    private String cssClass;
    private Set<String> tags = new TreeSet<>();
    private Instant created = Instant.now();
    private Instant expiration = created.plus(DEFAULT_EXPIRATION_IN_MINUTES, ChronoUnit.MINUTES);

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
        if (getCreated() == null) {
            throw new ValidationException("The message must contain a creation date.");
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

    public Instant getExpiration() {
        return expiration;
    }

    public Instant getCreated() {
        return created;
    }

    /**
     * @return true if the message is expired
     */
    public boolean isExpired() {
        return expiration.isBefore(Instant.now());
    }

    protected Set<String> getDefaultTags() {
        return Collections.emptySet();
    }

    protected String getDefaultCssClass() {
        return null;
    }

    @Override
    public int compareTo(Message m) {
        int i;
        if (created != null && (i = getCreated().compareTo(m.getCreated())) != 0) {
            return i;
        }
        if (text != null && (i = getText().compareTo(m.getText())) != 0) {
            return i;
        }
        if (expiration != null && (i = getExpiration().compareTo(m.getExpiration())) != 0) {
            return i;
        }
        return getTags().size() - m.getTags().size();
    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = 41 * hash + (this.created == null ? 0 : this.created.hashCode());
        hash = 29 * hash + (this.text == null ? 0 : this.text.hashCode());
        hash = 17 * hash + (this.tags == null ? 0 : this.tags.hashCode());
        hash = 13 * hash + (this.expiration == null ? 0 : this.expiration.hashCode());
        return hash;
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

        if (!Objects.equals(this.created, other.created)) {
            return false;
        }
        if (!Objects.equals(this.expiration, other.expiration)) {
            return false;
        }
        if (!Objects.equals(this.text, other.text)) {
            return false;
        }
        if (!Objects.equals(this.tags, other.tags)) {
            return false;
        }
        return true;
    }

    /**
     * Serialize the message as XML and send it into the socket.
     *
     * @param host host
     * @param port port number
     * @throws IOException if I/O exception occurred
     *
     * @see #throwIfError(int c, String message)
     *
     * @return possible output for this application, null if no output
     */
    public Response write(final String host, final int port) throws IOException {
        try (Socket sock = new Socket(host, port)) {
            try (MessageWriter mos = new MessageWriter(sock.getOutputStream());
                 InputStream input = sock.getInputStream()) {

                mos.writeMessage(this);

                int deliveryStatus = input.read();
                if (deliveryStatus != Message.MESSAGE_OK) {
                    throwIfError(deliveryStatus, IOUtils.toString(input));
                }

                return gson.fromJson(new InputStreamReader(input), Response.class);
            }
        }
    }

    public String getEncoded() {
        return gson.toJson(this);
    }

    public static Message decodeObject(final String s) {
        return gson.fromJson(s, Message.class);
    }

    /**
     * Decode the return code from the remote server.
     *
     * @param c the code
     * @param message error message stored in string
     * @throws IOException if the return code meant an error
     */
    protected void throwIfError(int c, String message) throws IOException {
        switch (c) {
            case MESSAGE_OK:
                break;
            case MESSAGE_LIMIT:
                throw new IOException(
                        String.format(
                                "Message was not accepted by the remote server - "
                                + "%s (error %#x).",
                                message.length() > 0 ? message : "too many messages in the system",
                                c));
            default:
                throw new IOException(
                        String.format(
                                "Message was not accepted by the remote server %s%s%s(error %#x).",
                                message.length() > 0 ? "- " : "",
                                message.length() > 0 ? message : "",
                                message.length() > 0 ? " " : "",
                                c));

        }
    }

    private static class MessageDeserializer implements JsonDeserializer<Message> {

        @Override
        public Message deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) {
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

    public static class Builder<T extends Message> {

        private final Class<T> messageClass;

        private final Set<String> tags = new TreeSet<>();

        private String text;

        private String cssClass;

        private Instant expiration;

        public Builder(final Class<T> cl) {
            messageClass = cl;
        }

        public T build() {
            T m = createMessage(messageClass);

            Message castToParent = m;

            castToParent.tags.addAll(tags);

            if (castToParent.tags.isEmpty()) {
                Set<String> defaultTags = m.getDefaultTags();
                if (defaultTags != null) {
                    castToParent.tags.addAll(defaultTags);
                }
            }

            if (text != null) {
                castToParent.text = text;
            }

            if (cssClass != null) {
                castToParent.cssClass = cssClass;
            } else {
                castToParent.cssClass = m.getDefaultCssClass();
            }
            if (expiration != null) {
                castToParent.expiration = expiration;
            }
            return m;
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

        public Builder<T> setExpiration(final Instant expirationDate) {
            this.expiration = expirationDate;
            return this;
        }

    }
}
