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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.suggest.query.data;

import java.util.BitSet;

/**
 * {@link IntsHolder} implementation by using the {@link BitSet}.
 */
public class BitIntsHolder extends BitSet implements IntsHolder {

    private static final long serialVersionUID = 6943964922850699553L;

    public BitIntsHolder() {
    }

    public BitIntsHolder(final int nbits) {
        super(nbits);
    }

    /** {@inheritDoc} */
    @Override
    public boolean has(final int i) {
        return get(i);
    }

    /** {@inheritDoc} */
    @Override
    public int numberOfElements() {
        return cardinality();
    }

}
