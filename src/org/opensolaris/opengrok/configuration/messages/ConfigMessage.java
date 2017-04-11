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
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 *
 * @author Vladimir Kotal
 * @author Krystof Tulinger
 */
public class ConfigMessage extends Message {

    /**
     * Pattern describes the java variable name and the assigned value.
     * Examples:
     * <ul>
     * <li>variable = true</li>
     * <li>stopOnClose = 10</li>
     * </ul>
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("([a-z_]\\w*) = (.*)");

    @Override
    protected byte[] applyMessage(RuntimeEnvironment env) throws IOException {
        if (hasTag("getconf")) {
            return env.getConfiguration().getXMLRepresentationAsString().getBytes();
        } else if (hasTag("set")) {
            Matcher matcher = VARIABLE_PATTERN.matcher(getText());
            if (matcher.find()) {
                // set the property
                invokeSetter(
                        env.getConfiguration(),
                        matcher.group(1), // field
                        matcher.group(2) // value
                );
                // apply the configuration - let the environment reload the configuration if necessary
                env.applyConfig(env.getConfiguration(), false);
                return String.format("Variable \"%s\" set to \"%s\".", matcher.group(1), matcher.group(2)).getBytes();
            } else {
                // invalid pattern
                throw new IOException(
                        String.format("The pattern \"%s\" does not match \"%s\".",
                                VARIABLE_PATTERN.toString(),
                                getText()));
            }
        } else if (hasTag("setconf")) {
            env.applyConfig(this, hasTag("reindex"));
        }

        return null;
    }

    /**
     * Invokes a setter on the configuration object and passes a value to that
     * setter.
     *
     * The value is passed as string and the function will automatically try to
     * convert it to the parameter type in the setter. These conversion are
     * available only for
     * <a href="https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html">java
     * primitive datatypes</a>:
     * <ul>
     * <li>Boolean or boolean</li>
     * <li>Short or short</li>
     * <li>Integer or integer</li>
     * <li>Long or long</li>
     * <li>Float or float</li>
     * <li>Double or double</li>
     * <li>Byte or byte</li>
     * <li>Character or char</li>
     * <li>String</li>
     * </ul>
     * Any other parameter type will cause an exception.
     *
     * @param config the configuration object
     * @param field name of the field which will be changed
     * @param value desired value
     * @throws IOException if any error occurs (no suitable method, bad
     * conversion, ...)
     */
    protected void invokeSetter(Configuration config, String field, String value) throws IOException {
        try {
            PropertyDescriptor desc = new PropertyDescriptor(field, Configuration.class);
            Method setter = desc.getWriteMethod();

            if (setter == null) {
                // no setter
                throw new IOException(
                        String.format("No setter for the name \"%s\".",
                                field));
            }

            if (setter.getParameterCount() != 1) {
                // not a setter
                /**
                 * Actually should not happen as it is not considered as a
                 * writer method so an exception would be thrown earlier.
                 */
                throw new IOException(
                        String.format("The setter \"%s\" for the name \"%s\" does not take exactly 1 parameter.",
                                setter.getName(), field));
            }

            Class c = setter.getParameterTypes()[0];

            String name = c.getName();
            /**
             * Java primitive types as per
             * <a href="https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html">java
             * datatypes</a>.
             */
            if (c.getName().equals("boolean") || c.getName().equals(Boolean.class.getName())) {
                Boolean v = Boolean.valueOf(value);
                if (!v) {
                    /**
                     * The Boolean.valueOf() returns true only for "true" case
                     * insensitive. These are convenient shortcuts for "on", "1"
                     * to be interpreted as booleans.
                     */
                    v = v || value.equalsIgnoreCase("on");
                    v = v || value.equals("1");
                }
                setter.invoke(config, v);
            } else if (c.getName().equals("short") || c.getName().equals(Short.class.getName())) {
                setter.invoke(config, Short.valueOf(value));
            } else if (c.getName().equals("int") || c.getName().equals(Integer.class.getName())) {
                setter.invoke(config, Integer.valueOf(value));
            } else if (c.getName().equals("long") || c.getName().equals(Long.class.getName())) {
                setter.invoke(config, Long.valueOf(value));
            } else if (c.getName().equals("float") || c.getName().equals(Float.class.getName())) {
                setter.invoke(config, Float.valueOf(value));
            } else if (c.getName().equals("double") || c.getName().equals(Double.class.getName())) {
                setter.invoke(config, Double.valueOf(value));
            } else if (c.getName().equals("byte") || c.getName().equals(Byte.class.getName())) {
                setter.invoke(config, Byte.valueOf(value));
            } else if (c.getName().equals("char") || c.getName().equals(Character.class.getName())) {
                setter.invoke(config, value.charAt(0));
            } else if (c.getName().equals(String.class.getName())) {
                setter.invoke(config, value);
            } else {
                // error uknown type
                throw new IOException(
                        String.format("Unsupported type conversion for the name \"%s\". Expecting \"%s\".",
                                field, c.getName()));
            }
        } catch (NumberFormatException ex) {
            throw new IOException(
                    String.format("Unsupported type conversion from String to Integer for name \"%s\" - %s.",
                            field, ex.getLocalizedMessage()), ex);
        } catch (IntrospectionException
                | IllegalAccessException
                | InvocationTargetException
                | IllegalArgumentException ex) {
            throw new IOException(
                    String.format("Unsupported operation with the configuration for name \"%s\" - %s.",
                            field,
                            ex.getCause() == null
                            ? ex.getLocalizedMessage()
                            : ex.getCause().getLocalizedMessage()),
                    ex);
        }
    }

    /**
     * Cast a boolean value to integer.
     *
     * @param b boolean value
     * @return 0 for false and 1 for true
     */
    protected int toInteger(boolean b) {
        return b ? 1 : 0;
    }

    @Override
    public void validate() throws Exception {
        if (toInteger(hasTag("setconf"))
                + toInteger(hasTag("getconf"))
                + toInteger(hasTag("set")) > 1) {
            throw new Exception("The message tag must be either setconf, getconf or set");
        }

        if (hasTag("setconf")) {
            if (getText() == null) {
                throw new Exception("The setconf message must contain a text.");
            }
        } else if (hasTag("getconf")) {
            if (getText() != null) {
                throw new Exception("The getconf message should not contain a text.");
            }
            if (getTags().size() != 1) {
                throw new Exception("The getconf message should be the only tag.");
            }
        } else if (hasTag("set")) {
            if (getText() == null) {
                throw new Exception("The set message must contain a text.");
            }
            if (getTags().size() != 1) {
                throw new Exception("The set message should be the only tag.");
            }
        } else {
            throw new Exception("The message tag must be either setconf, getconf or set");
        }

        super.validate();
    }
}
