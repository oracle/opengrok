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
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.util;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.logger.LoggerFactory;

/**
 *
 * @author Krystof Tulinger
 */
public class ClassUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassUtil.class);

    /**
     * Mark all transient fields in {@code targetClass} as @Transient for the
     * XML serialization.
     *
     * Fields marked with java transient keyword do not work becase the
     * XMLEncoder does not take these into account. This helper marks the fields
     * marked with transient keyword as transient also for the XMLDecoder.
     *
     * @param targetClass the class
     */
    public static void remarkTransientFields(Class targetClass) {
        try {
            BeanInfo info;
            info = Introspector.getBeanInfo(targetClass);
            PropertyDescriptor[] propertyDescriptors = info.getPropertyDescriptors();
            for (Field f : targetClass.getDeclaredFields()) {
                if (Modifier.isTransient(f.getModifiers())) {
                    for (int i = 0; i < propertyDescriptors.length; ++i) {
                        if (propertyDescriptors[i].getName().equals(f.getName())) {
                            propertyDescriptors[i].setValue("transient", Boolean.TRUE);
                        }
                    }
                }
            }
        } catch (IntrospectionException ex) {
            LOGGER.log(Level.WARNING, "An exception ocurred during remarking transient fields:", ex);
        }
    }
    
    /**
     * Invokes a setter on an object and passes a value to that setter.
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
     * @param obj the object
     * @param field name of the field which will be changed
     * @param value desired value
     *
     * @throws IOException if any error occurs (no suitable method, bad
     * conversion, ...)
     */
    public static void invokeSetter(Object obj, String field, String value) throws IOException {
        try {
            PropertyDescriptor desc = new PropertyDescriptor(field, obj.getClass());
            Method setter = desc.getWriteMethod();

            if (setter == null) {
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
            String paramClass = c.getName();

            /**
             * Java primitive types as per
             * <a href="https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html">java
             * datatypes</a>.
             */
            if (paramClass.equals("boolean") || paramClass.equals(Boolean.class.getName())) {
                if (!BooleanUtil.isBoolean(value)) {
                    throw new IOException(String.format("Unsupported type conversion from String to a boolean for name \"%s\" -"
                            + " got \"%s\" - allowed values are [false, off, 0, true, on, 1].",
                            field, value));
                }
                Boolean v = Boolean.valueOf(value);
                if (!v) {
                    /**
                     * The Boolean.valueOf() returns true only for "true" case
                     * insensitive so now we have either the false values or
                     * "on" or "1". These are convenient shortcuts for "on", "1"
                     * to be interpreted as booleans.
                     */
                    v = v || value.equalsIgnoreCase("on");
                    v = v || value.equals("1");
                }
                setter.invoke(obj, v);
            } else if (paramClass.equals("short") || paramClass.equals(Short.class.getName())) {
                setter.invoke(obj, Short.valueOf(value));
            } else if (paramClass.equals("int") || paramClass.equals(Integer.class.getName())) {
                setter.invoke(obj, Integer.valueOf(value));
            } else if (paramClass.equals("long") || paramClass.equals(Long.class.getName())) {
                setter.invoke(obj, Long.valueOf(value));
            } else if (paramClass.equals("float") || paramClass.equals(Float.class.getName())) {
                setter.invoke(obj, Float.valueOf(value));
            } else if (paramClass.equals("double") || paramClass.equals(Double.class.getName())) {
                setter.invoke(obj, Double.valueOf(value));
            } else if (paramClass.equals("byte") || paramClass.equals(Byte.class.getName())) {
                setter.invoke(obj, Byte.valueOf(value));
            } else if (paramClass.equals("char") || paramClass.equals(Character.class.getName())) {
                setter.invoke(obj, value.charAt(0));
            } else if (paramClass.equals(String.class.getName())) {
                setter.invoke(obj, value);
            } else {
                // error unknown type
                throw new IOException(
                        String.format("Unsupported type conversion for the name \"%s\". Expecting \"%s\".",
                                field, paramClass));
            }
        } catch (NumberFormatException ex) {
            throw new IOException(
                    String.format("Unsupported type conversion from String to a number for name \"%s\" - %s.",
                            field, ex.getLocalizedMessage()), ex);
        } catch (IndexOutOfBoundsException ex) {
            throw new IOException(
                    String.format("The string is not long enough to extract 1 character for name \"%s\" - %s.",
                            field, ex.getLocalizedMessage()), ex);
        } catch (IntrospectionException
                | IllegalAccessException
                | IllegalArgumentException
                /**
                 * This the case when the invocation failed because the invoked
                 * method failed with an exception. All exceptions are
                 * propagated through this exception.
                 */
                | InvocationTargetException ex) {
            throw new IOException(
                    String.format("Unsupported operation with object of class %s for name \"%s\" - %s.",
                            obj.getClass().toString(),
                            field,
                            ex.getCause() == null
                            ? ex.getLocalizedMessage()
                            : ex.getCause().getLocalizedMessage()),
                    ex);
        }
    }

    /**
     * Invokes a getter of a property on an object.
     * 
     * @param obj the object
     * @param field string with field name
     * @return string representation of the field value
     * @throws java.io.IOException 
     */
    public static String invokeGetter(Object obj, String field) throws IOException {
        String val = null;

        try {
            PropertyDescriptor desc = new PropertyDescriptor(field, obj.getClass());
            Method getter = desc.getReadMethod();

            if (getter == null) {
                throw new IOException(
                        String.format("No getter for the name \"%s\".", field));
            }

            if (getter.getParameterCount() != 0) {
                /**
                 * Actually should not happen as it is not considered as a
                 * read method so an exception would be thrown earlier.
                 */
                throw new IOException(
                        String.format("The getter \"%s\" for the name \"%s\" takes a parameter.",
                                getter.getName(), field));
            }
            
            val = getter.invoke(obj).toString();
        } catch (IntrospectionException
                | IllegalAccessException
                | InvocationTargetException
                | IllegalArgumentException ex) {
            throw new IOException(
                    String.format("Unsupported operation with object of class %s for name \"%s\" - %s.",
                            obj.getClass().toString(),
                            field,
                            ex.getCause() == null
                            ? ex.getLocalizedMessage()
                            : ex.getCause().getLocalizedMessage()),
                    ex);
        }

        return val;
    }
}
