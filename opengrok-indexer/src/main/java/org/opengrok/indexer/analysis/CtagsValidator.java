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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.ObjectValidator;

public final class CtagsValidator implements ObjectValidator<Ctags> {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        CtagsValidator.class);

    @Override
    public boolean isValid(Ctags ctags) {
        return ctags != null && !ctags.isClosed();
    }

    @Override
    public void invalidate(Ctags ctags) {
        try {
            if (ctags != null) {
                ctags.close();
            }
        } catch (IOException ex) {
            LOGGER.log(Level.FINE, "Error closing ctags", ex);
        }
    }
}
