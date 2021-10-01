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
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.util;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.BooleanUtils;
import org.opengrok.indexer.logger.LoggerFactory;

/**
 *
 * @author Krystof Tulinger
 */
public class ClassUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassUtil.class);

    private ClassUtil() {
    }

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
    public static void remarkTransientFields(Class<?> targetClass) {
        try {
            BeanInfo info = Introspector.getBeanInfo(targetClass);
            PropertyDescriptor[] propertyDescriptors = info.getPropertyDescriptors();
            for (Field f : targetClass.getDeclaredFields()) {
                if (Modifier.isTransient(f.getModifiers())) {
                    for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
                        if (propertyDescriptor.getName().equals(f.getName())) {
                            propertyDescriptor.setValue("transient", Boolean.TRUE);
                        }
                    }
                }
            }
        } catch (IntrospectionException ex) {
            LOGGER.log(Level.WARNING, "An exception ocurred during remarking transient fields:", ex);
        }
    }

    private static Object stringToObject(String fieldName, Class<?> c, String value) throws IOException {
        Object v;
        String paramClass = c.getName();

        try {
            /*
             * Java primitive types as per
             * <a href="https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html">java
             * datatypes</a>.
             */
            if (paramClass.equals("boolean") || paramClass.equals(Boolean.class.getName())) {
                Boolean parsedValue = BooleanUtils.toBooleanObject(value);
                if (parsedValue == null) {
                    throw new IOException(String.format("Unsupported type conversion from String to a boolean for name \"%s\" -"
                                    + " got \"%s\" - allowed values are [false, off, 0, true, on, 1].",
                            paramClass, value));
                }
                v = parsedValue;
            } else if (paramClass.equals("short") || paramClass.equals(Short.class.getName())) {
                v = Short.valueOf(value);
            } else if (paramClass.equals("int") || paramClass.equals(Integer.class.getName())) {
                v = Integer.valueOf(value);
            } else if (paramClass.equals("long") || paramClass.equals(Long.class.getName())) {
                v = Long.valueOf(value);
            } else if (paramClass.equals("float") || paramClass.equals(Float.class.getName())) {
                v = Float.valueOf(value);
            } else if (paramClass.equals("double") || paramClass.equals(Double.class.getName())) {
                v = Double.valueOf(value);
            } else if (paramClass.equals("byte") || paramClass.equals(Byte.class.getName())) {
                v = Byte.valueOf(value);
            } else if (paramClass.equals("char") || paramClass.equals(Character.class.getName())) {
                v = value.charAt(0);
            } else if (paramClass.equals(String.class.getName())) {
                v = value;
            } else {
                ObjectMapper mapper = new ObjectMapper();
                v = mapper.readValue(value, c);
            }
        }  catch (NumberFormatException ex) {
            throw new IOException(
                    String.format("Unsupported type conversion from String to a number for name \"%s\" - %s.",
                            fieldName, ex.getLocalizedMessage()), ex);
        } catch (IndexOutOfBoundsException ex) {
            throw new IOException(
                    String.format("The string is not long enough to extract 1 character for name \"%s\" - %s.",
                            fieldName, ex.getLocalizedMessage()), ex);
        }

        return v;
    }

    private static Method getSetter(Object obj, String fieldName) throws IOException {
        PropertyDescriptor desc;
        try {
            desc = new PropertyDescriptor(fieldName, obj.getClass());
        } catch (IntrospectionException e) {
            throw new IOException(e);
        }
        Method setter = desc.getWriteMethod();

        if (setter == null) {
            throw new IOException(
                    String.format("No setter for the name \"%s\".", fieldName));
        }

        if (setter.getParameterCount() != 1) {
            // not a setter
            /*
             * Actually should not happen as it is not considered as a
             * writer method so an exception would be thrown earlier.
             */
            throw new IOException(
                    String.format("The setter \"%s\" for the name \"%s\" does not take exactly 1 parameter.",
                            setter.getName(), fieldName));
        }

        return setter;
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
     * Any other parameter type will cause an exception. The size/value itself is checked elsewhere.
     *
     * @param obj the object
     * @param fieldName name of the field which will be changed
     * @param value desired value represented as string
     *
     * @throws IOException if any error occurs (no suitable method, bad conversion, ...)
     */
    public static void setFieldValue(Object obj, String fieldName, String value) throws IOException {
        Method setter = getSetter(obj, fieldName);
        Class<?> c = setter.getParameterTypes()[0];
        Object objValue = stringToObject(fieldName, c, value);
        invokeSetter(setter, obj, fieldName, objValue);
    }

    /**
     * Invokes a setter on an object and passes a value to that setter.
     *
     * @param obj the object
     * @param fieldName name of the field which will be changed
     * @param value desired value
     * @throws IOException all exceptions from the reflection
     */
    public static void setFieldValue(Object obj, String fieldName, Object value) throws IOException {
        Method setter = getSetter(obj, fieldName);
        invokeSetter(setter, obj, fieldName, value);
    }

    private static void invokeSetter(Method setter, Object obj, String fieldName, Object value) throws IOException {
        try {
            setter.invoke(obj, value);
        } catch (IllegalAccessException
                | IllegalArgumentException
                /*
                 * This the case when the invocation failed because the invoked
                 * method failed with an exception. All exceptions are
                 * propagated through this exception.
                 */
                | InvocationTargetException ex) {
            throw new IOException(
                    String.format("Unsupported operation with object of class %s for name \"%s\" - %s.",
                            obj.getClass().toString(),
                            fieldName,
                            ex.getCause() == null
                                    ? ex.getLocalizedMessage()
                                    : ex.getCause().getLocalizedMessage()), ex);
        }
    }

    /**
     * @param obj object
     * @param fieldName field name
     * @return true if field is present in the object (not recursively) or false
     */
    public static boolean hasField(Object obj, String fieldName) {
        try {
            PropertyDescriptor desc = new PropertyDescriptor(fieldName, obj.getClass());
        } catch (IntrospectionException e) {
            return false;
        }
        return true;
    }

    /**
     * Invokes a getter of a property on an object.
     *
     * @param obj the object
     * @param field string with field name
     * @return string representation of the field value
     * @throws java.io.IOException exception
     */
    public static Object getFieldValue(Object obj, String field) throws IOException {

        try {
            PropertyDescriptor desc = new PropertyDescriptor(field, obj.getClass());
            Method getter = desc.getReadMethod();

            if (getter == null) {
                throw new IOException(
                        String.format("No getter for the name \"%s\".", field));
            }

            if (getter.getParameterCount() != 0) {
                /*
                 * Actually should not happen as it is not considered as a
                 * read method so an exception would be thrown earlier.
                 */
                throw new IOException(
                        String.format("The getter \"%s\" for the name \"%s\" takes a parameter.",
                                getter.getName(), field));
            }

            return getter.invoke(obj);
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
    }
}
