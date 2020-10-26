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
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.suggest.popular.impl.chronicle;

import net.openhft.chronicle.bytes.HeapBytesStore;
import net.openhft.chronicle.bytes.RandomDataInput;
import net.openhft.chronicle.hash.AbstractData;
import net.openhft.chronicle.hash.Data;
import net.openhft.chronicle.hash.serialization.DataAccess;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;
import org.apache.lucene.util.BytesRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link BytesRef} data serializer for {@link net.openhft.chronicle.map.ChronicleMap}.
 * Modified from https://github.com/OpenHFT/Chronicle-Map/blob/master/docs/CM_Tutorial_DataAccess.adoc
 */
public class BytesRefDataAccess extends AbstractData<BytesRef> implements DataAccess<BytesRef> {

    /** Cache field. */
    private transient HeapBytesStore<byte[]> bs;

    /** State field. */
    private transient byte[] array;

    public BytesRefDataAccess() {
        initTransients();
    }

    private void initTransients() {
        bs = null;
    }

    @Override
    public RandomDataInput bytes() {
        return bs;
    }

    @Override
    public long offset() {
        return bs.start();
    }

    @Override
    public long size() {
        return bs.capacity();
    }

    @Override
    public BytesRef get() {
        return new BytesRef(array);
    }

    @Override
    public BytesRef getUsing(@Nullable BytesRef using) {
        if (using == null) {
            using = new BytesRef(new byte[array.length]);
        } else if (using.bytes.length < array.length) {
            using.bytes = new byte[array.length];
        }
        System.arraycopy(array, 0, using.bytes, 0, array.length);
        using.offset = 0;
        using.length = array.length;
        return using;
    }

    @Override
    public Data<BytesRef> getData(@NotNull BytesRef instance) {
        if (instance.bytes.length == instance.length) {
            array = instance.bytes;
        } else {
            array = new byte[instance.length];
            System.arraycopy(instance.bytes, instance.offset, array, 0, instance.length);
        }
        bs = HeapBytesStore.wrap(array);
        return this;
    }

    @Override
    public void uninit() {
        array = null;
        bs = null;
    }

    @Override
    public DataAccess<BytesRef> copy() {
        return new BytesRefDataAccess();
    }

    @Override
    public void writeMarshallable(@NotNull WireOut wireOut) {
        // no fields to write
    }

    @Override
    public void readMarshallable(@NotNull WireIn wireIn) {
        // no fields to read
        initTransients();
    }

}
