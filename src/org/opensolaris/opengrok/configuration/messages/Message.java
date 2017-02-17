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
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.util.XmlEofOutputStream;

/**
 * If you extend this file, don't forget to add an information into the root
 * README.
 *
 * @author Kryštof Tulinger
 */
public abstract class Message implements Comparable<Message> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Message.class);

    public static final int MESSAGE_OK = 0x1;
    public static final int MESSAGE_LIMIT = 0x2;
    public static final int MESSAGE_ERROR = 0x4;

    protected static final int SECOND = 1000;
    protected static final int MINUTE = 60 * SECOND;
    protected static final long DEFAULT_EXPIRATION = 10 * MINUTE;

    protected String text;
    protected String className;
    protected Set<String> tags = new TreeSet<>();
    protected Date created = new Date();
    protected Date expiration = new Date(System.currentTimeMillis() + DEFAULT_EXPIRATION);

    /**
     * Validate and apply the message to the current runtime environment.
     *
     * @param env the runtime environment
     * @return possible output for this application, null if no output
     * @throws java.lang.Exception
     */
    final public byte[] apply(RuntimeEnvironment env) throws Exception {
        validate();
        return applyMessage(env);
    }

    /**
     * Apply the message to the current runtime environment.
     *
     * @param env the runtime environment
     * @return possible output for this application, null if no output
     * @throws java.lang.Exception
     */
    protected abstract byte[] applyMessage(RuntimeEnvironment env) throws Exception;

    /**
     * Validate the current message and throw an exception if the message is not
     * valid. Further implementation can override this method to get the message
     * into a state they would expect.
     *
     * @throws Exception
     */
    public void validate() throws Exception {
        if (getCreated() == null) {
            throw new Exception("The message must contain a creation date.");
        }
    }

    /**
     * Factory method for particular message types.
     *
     * @param type the message type
     * @return specific message instance for the given type or null
     */
    public static Message createMessage(String type) {
        String classname = Message.class.getPackage().getName();
        classname += "." + type.substring(0, 1).toUpperCase(Locale.getDefault());
        classname += type.substring(1) + "Message";

        try {
            Class concreteClass = Class.forName(classname);
            return (Message) concreteClass.newInstance();
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, "Couldn't create message object of type \"{0}\".", type);
        }
        return null;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setTextFromFile(String filepath) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(filepath));
        this.text = new String(encoded);
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
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
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public Message addTag(String tag) {
        this.tags.add(tag);
        return this;
    }

    public Date getExpiration() {
        return expiration;
    }

    public void setExpiration(Date expiration) {
        this.expiration = expiration;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    /**
     * @return true if the message is expired
     */
    public boolean isExpired() {
        return expiration.before(new Date());
    }

    /**
     * Time left to the expiration.
     *
     * @return the time in milliseconds
     */
    public long timeLeft() {
        long ret = expiration.getTime() - new Date().getTime();
        return ret < 0 ? 0 : ret;
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
     * @throws IOException
     *
     * @see #throwIfError(int c, String message)
     *
     * @return possible output for this application, null if no output
     */
    public byte[] write(String host, int port) throws IOException {
        try (Socket sock = new Socket(host, port)) {
            try (XMLEncoder e = new XMLEncoder(new XmlEofOutputStream(sock.getOutputStream()))) {
                e.writeObject(this);
            }

            try (InputStream input = sock.getInputStream();
                    ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                int ret, r;
                if ((ret = input.read()) < 0) {
                    throwIfError(ret, "unexpected end of socket while waiting for ack");
                }

                byte[] buffer = new byte[4096];
                while ((r = input.read(buffer)) >= 0) {
                    out.write(buffer, 0, r);
                }

                throwIfError(ret, out.toString());

                if (out.size() > 0) {
                    return out.toByteArray();
                }
            }
        }
        return null;
    }

    public void write(File file) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            this.encodeObject(out);
        }
    }

    public String getXMLRepresentationAsString() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        this.encodeObject(bos);
        return bos.toString();
    }

    private void encodeObject(OutputStream out) {
        try (XMLEncoder e = new XMLEncoder(new BufferedOutputStream(out))) {
            e.writeObject(this);
        }
    }

    public static Message read(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            return decodeObject(in);
        }
    }

    public static Message makeXMLStringAsMessage(String xmlconfig) throws IOException {
        final Message ret;
        final ByteArrayInputStream in = new ByteArrayInputStream(xmlconfig.getBytes());
        ret = decodeObject(in);
        return ret;
    }

    private static Message decodeObject(InputStream in) throws IOException {
        final Object ret;
        final LinkedList<Exception> exceptions = new LinkedList<>();

        try (XMLDecoder d = new XMLDecoder(new BufferedInputStream(in))) {
            ret = d.readObject();
        }

        if (!(ret instanceof Message)) {
            throw new IOException("Not a valid message file");
        }

        Message conf = ((Message) ret);

        return conf;
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
}
