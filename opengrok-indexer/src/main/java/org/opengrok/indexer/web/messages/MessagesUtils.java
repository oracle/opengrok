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
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.web.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.web.Util;

import java.io.IOException;
import java.io.Writer;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class MessagesUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessagesUtils.class);

    private void MessageUtils() {
        // private to ensure static
    }

    static final class TaggedMessagesContainer implements JSONable {

        private final String tag;
        private final SortedSet<MessagesContainer.AcceptedMessage> messages;

        TaggedMessagesContainer(String tag, SortedSet<MessagesContainer.AcceptedMessage> messages) {
            this.tag = tag;
            this.messages = messages;
        }

        public String getTag() {
            return tag;
        }

        public SortedSet<MessagesContainer.AcceptedMessage> getMessages() {
            return messages;
        }
    }

    /**
     * Print list of messages into output.
     *
     * @param out output
     * @param set set of messages
     */
    public static void printMessages(Writer out, SortedSet<MessagesContainer.AcceptedMessage> set) {
        printMessages(out, set, false);
    }

    /**
     * Print set of messages into output.
     * @param out output
     * @param set set of messages
     * @param limited if the container should be limited
     */
    private static void printMessages(Writer out, SortedSet<MessagesContainer.AcceptedMessage> set, boolean limited) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        if (!set.isEmpty()) {
            try {
                out.write("<ul class=\"message-group");
                if (limited) {
                    out.write(" limited");
                }
                out.write("\">\n");
                for (MessagesContainer.AcceptedMessage m : set) {
                    out.write("<li class=\"message-group-item ");
                    out.write(Util.encode(m.getMessage().getMessageLevel().toString()));
                    out.write("\" title=\"Expires on ");
                    out.write(Util.encode(df.format(Date.from(m.getExpirationTime()))));
                    out.write("\">");
                    out.write(Util.encode(df.format(Date.from(m.getAcceptedTime()))));
                    out.write(": ");
                    out.write(m.getMessage().getText());
                    out.write("</li>");
                }
                out.write("</ul>");
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING,
                        "An error occurred for a group of messages", ex);
            }
        }
    }

    /**
     * Print set of tagged messages into JSON object.
     *
     * @return JSON string or empty string
     */
    private static String taggedMessagesToJson(Set<TaggedMessagesContainer> messages) {
        if (messages.isEmpty()) {
            return JSONable.EMPTY;
        }

        ObjectMapper mapper = new ObjectMapper();

        try {
            return mapper.writeValueAsString(messages);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.WARNING, String.format("failed to encode '%s' to JSON: ", messages), e);
            return JSONable.EMPTY;
        }
    }

    /**
     * Print messages for given tags into JSON array.
     *
     * @param tags list of tags
     * @return JSON array of the messages (the same as the parameter)
     */
    public static String messagesToJson(String... tags) {
        Set<TaggedMessagesContainer> messages = new HashSet<>();

        for (String tag : tags) {
            SortedSet<MessagesContainer.AcceptedMessage> messagesWithTag = RuntimeEnvironment.getInstance().getMessages(tag);
            if (messagesWithTag.isEmpty()) {
                continue;
            }

            TaggedMessagesContainer container = new TaggedMessagesContainer(tag, messagesWithTag);
            messages.add(container);
        }

        return taggedMessagesToJson(messages);
    }

    /**
     * Print messages for given tags into JSON array.
     *
     * @param tags list of tags
     * @return json array of the messages
     * @see #messagesToJson(String...)
     */
    private static String messagesToJson(List<String> tags) {
        return messagesToJson(tags.toArray(new String[0]));
    }

    /**
     * Print messages for given project into JSON. These messages are
     * tagged by project description or tagged by any of the project's group name.
     *
     * @param project the project
     * @param additionalTags additional list of tags
     * @return JSON string
     * @see #messagesToJson(String...)
     */
    public static String messagesToJson(Project project, String... additionalTags) {
        if (project == null) {
            return JSONable.EMPTY;
        }

        List<String> tags = new ArrayList<>();
        tags.addAll(Arrays.asList(additionalTags));
        tags.add(project.getName());
        project.getGroups().stream().map(Group::getName).forEach(tags::add);

        return messagesToJson(tags);
    }

    /**
     * Print messages for given project into JSON array. These messages are
     * tagged by project description or tagged by any of the project's group
     * name.
     *
     * @param project the project
     * @return the json array
     * @see #messagesToJson(Project, String...)
     */
    public static String messagesToJson(Project project) {
        return messagesToJson(project, new String[0]);
    }

    /**
     * Print messages for given group into JSON.
     *
     * @param group the group
     * @param additionalTags additional list of tags
     * @return JSON string
     * @see #messagesToJson(java.util.List)
     */
    private static String messagesToJson(Group group, String... additionalTags) {
        List<String> tags = new ArrayList<>();

        tags.add(group.getName());
        tags.addAll(Arrays.asList(additionalTags));

        return messagesToJson(tags);
    }

    /**
     * Convert messages for given group into JSON.
     *
     * @param group the group
     * @return JSON string
     * @see #messagesToJson(Group, String...)
     */
    public static String messagesToJson(Group group) {
        return messagesToJson(group, new String[0]);
    }

    /**
     * @return name of highest cssClass of messages present in the system or null.
     */
    static String getHighestMessageLevel(Collection<MessagesContainer.AcceptedMessage> messages) {
        return messages.
                stream().
                map(MessagesContainer.AcceptedMessage::getMessageLevel).
                max(Message.MessageLevel.VALUE_COMPARATOR).
                map(Message.MessageLevel::toString).
                orElse(null);
    }

    /**
     * @param tags message tags
     * @return name of highest cssClass of messages present in the system or null.
     */
    public static String getMessageLevel(String... tags) {
        Set<MessagesContainer.AcceptedMessage> messages;
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        messages = Arrays.stream(tags).
                map(env::getMessages).
                flatMap(Collection::stream).
                collect(Collectors.toSet());

        return getHighestMessageLevel(messages);
    }
}
