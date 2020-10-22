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
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.util.List;
import java.util.Locale;

/**
 * Represents a utility to show the user details of supported {@link Repository}
 * types for -h,--help.
 */
public class RepositoriesHelp {

    public static String getText() {
        StringBuilder builder = new StringBuilder();
        builder.append("Enabled repositories:");
        builder.append(System.lineSeparator());
        builder.append(System.lineSeparator());
        List<Class<? extends Repository>> clazzes = RepositoryFactory.getRepositoryClasses();
        appendClassesHelp(builder, clazzes);

        List<Class<? extends Repository>> disabledClazzes =
                RepositoryFactory.getDisabledRepositoryClasses();
        if (!disabledClazzes.isEmpty()) {
            if (!clazzes.isEmpty()) {
                builder.append(System.lineSeparator());
            }
            builder.append("Disabled repositories:");
            builder.append(System.lineSeparator());
            builder.append(System.lineSeparator());
            appendClassesHelp(builder, disabledClazzes);
        }

        return builder.toString();
    }

    private static void appendClassesHelp(
            StringBuilder builder, List<Class<? extends Repository>> clazzes) {

        clazzes.sort((o1, o2) -> o1.getSimpleName().compareToIgnoreCase(o2.getSimpleName()));
        for (Class<?> clazz : clazzes) {
            String simpleName = clazz.getSimpleName();
            if (toAka(builder, simpleName)) {
                builder.append(" (");
                builder.append(simpleName);
                builder.append(")");
            }
            builder.append(System.lineSeparator());
        }
    }

    private static boolean toAka(StringBuilder builder, String repoSimpleName) {
        final String REPOSITORY = "Repository";
        if (!repoSimpleName.endsWith(REPOSITORY)) {
            builder.append(repoSimpleName);
            return false;
        } else {
            String aka = repoSimpleName.substring(0,
                    repoSimpleName.length() - REPOSITORY.length());
            builder.append(aka.toLowerCase(Locale.ROOT));
            return true;
        }
    }

    /* private to enforce static */
    private RepositoriesHelp() {
    }
}
