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
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.condition;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;
import org.opengrok.indexer.condition.RepositoryInstalled.Type;

import java.util.List;
import java.util.stream.Collectors;

public class EnabledOnRepositoryCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        List<Type> requiredRepositories = List.of(AnnotationUtils
                .findAnnotation(context.getElement(), EnabledForRepository.class)
                .map(EnabledForRepository::value)
                .orElse(new Type[0]));
        List<Type> unsatisfiedRepositories = requiredRepositories.stream()
                .filter(t -> !t.isSatisfied())
                .collect(Collectors.toList());

        if (unsatisfiedRepositories.isEmpty()) {
            return ConditionEvaluationResult.enabled(requiredRepositories + " repositories are working, executing");
        } else {
            return ConditionEvaluationResult.disabled(unsatisfiedRepositories + " repositories are not working, skipping");
        }
    }
}
