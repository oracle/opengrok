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
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.suggest.popular.impl.chronicle;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.io.IORuntimeException;
import net.openhft.chronicle.core.util.ReadResolvable;
import net.openhft.chronicle.hash.serialization.SizedReader;
import net.openhft.chronicle.wire.Marshallable;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;
import org.apache.lucene.util.BytesRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link BytesRef} data serializer for {@link net.openhft.chronicle.map.ChronicleMap}.
 * Modified from https://github.com/OpenHFT/Chronicle-Map/blob/master/docs/CM_Tutorial_DataAccess.adoc
 */
public class BytesRefSizedReader implements SizedReader<BytesRef>, Marshallable, ReadResolvable<BytesRefSizedReader> {

    public static final BytesRefSizedReader INSTANCE = new BytesRefSizedReader();

    private BytesRefSizedReader() {
    }

    @NotNull
    @Override
    @SuppressWarnings("rawtypes")
    public BytesRef read(Bytes in, long size, @Nullable BytesRef using) {
        if (size < 0L || size > (long) Integer.MAX_VALUE) {
            throw new IORuntimeException("byte[] size should be non-negative int, " +
                    size + " given. Memory corruption?");
        }
        int arrayLength = (int) size;
        if (using == null) {
            using = new BytesRef(new byte[arrayLength]);
        } else if (using.bytes.length < arrayLength) {
            using.bytes = new byte[arrayLength];
        }
        in.read(using.bytes, 0, arrayLength);
        using.offset = 0;
        using.length = arrayLength;
        return using;
    }

    @Override
    public void writeMarshallable(@NotNull WireOut wireOut) {
        // no fields to write
    }

    @Override
    public void readMarshallable(@NotNull WireIn wireIn) {
        // no fields to read
    }

    @NotNull
    @Override
    public BytesRefSizedReader readResolve() {
        return INSTANCE;
    }

}
