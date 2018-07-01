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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.suggest.util;

import net.openhft.chronicle.bytes.BytesStore;
import net.openhft.chronicle.core.io.Closeable;
import net.openhft.chronicle.core.util.SerializableFunction;
import net.openhft.chronicle.hash.Data;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ExternalMapQueryContext;
import net.openhft.chronicle.map.MapEntry;
import net.openhft.chronicle.map.MapSegmentContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ChronicleMapUtils {

    private ChronicleMapUtils() {
    }

    public static <K, V> ChronicleMap<K, V> empty(Class<K> keyClass, Class<V> valueClass) {
        return new ChronicleMap<K, V>() {

            @Override
            public V get(Object key) {
                return null;
            }

            @Override
            public V getUsing(K key, V usingValue) {
                return null;
            }

            @Override
            public V acquireUsing(@NotNull K key, V usingValue) {
                return null;
            }

            @NotNull
            @Override
            public Closeable acquireContext(@NotNull K key, @NotNull V usingValue) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <R> R getMapped(K key, @NotNull SerializableFunction<? super V, R> function) {
                return null;
            }

            @Override
            public void getAll(File toFile) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void putAll(File fromFile) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Class<V> valueClass() {
                return valueClass;
            }

            @Override
            public V putIfAbsent(@NotNull K key, V value) {
                return null;
            }

            @Override
            public boolean remove(@NotNull Object key, Object value) {
                return false;
            }

            @Override
            public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
                throw new UnsupportedOperationException();
            }

            @Override
            public V replace(@NotNull K key, @NotNull V value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public boolean isEmpty() {
                return true;
            }

            @Override
            public boolean containsKey(Object key) {
                return false;
            }

            @Override
            public boolean containsValue(Object value) {
                return false;
            }

            @Nullable
            @Override
            public V put(K key, V value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public V remove(Object key) {
                return null;
            }

            @Override
            public void putAll(@NotNull Map<? extends K, ? extends V> m) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void clear() {

            }

            @NotNull
            @Override
            public Set<K> keySet() {
                return Collections.emptySet();
            }

            @NotNull
            @Override
            public Collection<V> values() {
                return Collections.emptySet();
            }

            @NotNull
            @Override
            public Set<Entry<K, V>> entrySet() {
                return Collections.emptySet();
            }

            @Override
            public File file() {
                return null;
            }

            @Override
            public String name() {
                return null;
            }

            @Override
            public String toIdentityString() {
                return null;
            }

            @Override
            public long longSize() {
                return 0;
            }

            @Override
            public long offHeapMemoryUsed() {
                return 0;
            }

            @Override
            public Class<K> keyClass() {
                return keyClass;
            }

            @NotNull
            @Override
            public ExternalMapQueryContext<K, V, ?> queryContext(K key) {
                throw new UnsupportedOperationException();
            }

            @NotNull
            @Override
            public ExternalMapQueryContext<K, V, ?> queryContext(Data<K> key) {
                throw new UnsupportedOperationException();
            }

            @NotNull
            @Override
            public ExternalMapQueryContext<K, V, ?> queryContext(BytesStore keyBytes, long offset, long size) {
                throw new UnsupportedOperationException();
            }

            @Override
            public MapSegmentContext<K, V, ?> segmentContext(int segmentIndex) {
                return null;
            }

            @Override
            public int segments() {
                return 0;
            }

            @Override
            public boolean forEachEntryWhile(Predicate<? super MapEntry<K, V>> predicate) {
                return false;
            }

            @Override
            public void forEachEntry(Consumer<? super MapEntry<K, V>> action) {

            }

            @Override
            public void close() {

            }

            @Override
            public boolean isOpen() {
                return true;
            }
        };
    }

}
