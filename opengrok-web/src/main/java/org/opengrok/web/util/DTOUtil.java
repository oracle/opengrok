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
package org.opengrok.web.util;

import net.sf.cglib.beans.BeanGenerator;
import org.modelmapper.ModelMapper;
import org.opengrok.indexer.util.DTOElement;

import java.lang.reflect.Field;

public class DTOUtil {
    private DTOUtil() {
        // private to ensure static
    }

    // ModelMapper is thread-safe and we only need to convert different object types for now
    // so it should be safe to reuse its instance.
    private static final ModelMapper modelMapper = new ModelMapper();

    /**
     * Generate Data Transfer Object from an object. Any field in the input object
     * that is annotated with <code>DTOElement</code> will be brought along.
     * @param object object to use as input
     * @return DTO with values and generated getters/setters from input object
     */
    public static Object createDTO(Object object) {
        // ModelMapper assumes getters/setters so use BeanGenerator to provide them.
        BeanGenerator beanGenerator = new BeanGenerator();
        for (Field field : object.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(DTOElement.class)) {
                beanGenerator.addProperty(field.getName(), field.getType());
            }
        }
        Object bean = beanGenerator.create();

        return modelMapper.map(object, bean.getClass());
    }
}
