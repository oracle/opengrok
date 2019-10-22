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

package org.opengrok.indexer.util;

import net.sf.cglib.beans.BeanGenerator;
import org.opengrok.indexer.logger.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BeanBuilder {
    private BeanGenerator beanGenerator;
    private Map<String, Object> valueMap;

    private static final Logger LOGGER =
            LoggerFactory.getLogger(BeanBuilder.class);

    public BeanBuilder() {
        beanGenerator = new BeanGenerator();
        valueMap = new HashMap<>();
    }

    public BeanBuilder add(String name, Class<?> type, Object value) {
        beanGenerator.addProperty(name, type);
        valueMap.put(name, value);

        return this;
    }

    public Object build() {
        Object myBean = beanGenerator.create();

        for (String name : valueMap.keySet()) {
            try {
                ClassUtil.setFieldValue(myBean, name, valueMap.get(name));
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "cannot generate RepositoryInfo bean", e);
                return null;
            }
        }

        return myBean;
    }
}
