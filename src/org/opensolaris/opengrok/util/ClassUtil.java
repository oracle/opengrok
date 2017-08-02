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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.util;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.configuration.Project;
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
}
