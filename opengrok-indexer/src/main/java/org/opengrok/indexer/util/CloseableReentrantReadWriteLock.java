/*
 * This work is licensed under the Creative Commons Attribution-ShareAlike 4.0
 * International License. To view a copy of this license, visit
 * https://creativecommons.org/licenses/by-sa/4.0/ or send a letter to
 * Creative Commons, PO Box 1866, Mountain View, CA 94042, USA.
 *
 * Copyright (c) 2017, https://stackoverflow.com/users/7583219/skoskav
 * Copyright (c) 2011, https://stackoverflow.com/questions/6965731/are-locks-autocloseable
 * Portions Copyright (c) 2019-2020, Chris Fraire <cfraire@me.com>.
 *
 * Used under CC 4 with modifications noted as follows as required by license:
 * 2019-09-10 -- cfraire@me.com, derived to use for ReentrantReadWriteLock.
 * 2020-04-21 -- cfraire@me.com, updated for proper handling re Serializable.
 */

package org.opengrok.indexer.util;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Represents a subclass of {@link ReentrantReadWriteLock} that can return
 * {@link ResourceLock} instances.
 */
public final class CloseableReentrantReadWriteLock extends ReentrantReadWriteLock {

    private static final long serialVersionUID = 95L;

    private transient ResourceLock readUnlocker = newReadUnlocker();

    private transient ResourceLock writeUnlocker = newWriteUnlocker();

    /**
     * @return a defined {@link ResourceLock} once the {@link #readLock()} has
     * been acquired
     */
    public ResourceLock readLockAsResource() {
        /*
         * A subclass of ReentrantReadWriteLock is forced to be serializable, so
         * we would have to handle where serialization can short-circuit field
         * initialization above and leave the instance's fields null.
         * Consequently, the fields cannot be final. They are encapsulating
         * fixed logic, so we can optimize and choose not to even bother
         * serializing by declaring transient and handling below.
         */
        ResourceLock unlocker = readUnlocker;
        if (unlocker == null) {
            unlocker = newReadUnlocker();
            // No synchronization necessary since overwrite is of no matter.
            readUnlocker = unlocker;
        }
        readLock().lock();
        return unlocker;
    }

    /**
     * @return a defined {@link ResourceLock} once the {@link #writeLock()}
     * has been acquired
     */
    public ResourceLock writeLockAsResource() {
        /*
         * A subclass of ReentrantReadWriteLock is forced to be serializable, so
         * we would have to handle where serialization can short-circuit field
         * initialization above and leave the instance's fields null.
         * Consequently, the fields cannot be final. They are encapsulating
         * fixed logic, so we can optimize and choose not to even bother
         * serializing by declaring transient and handling below.
         */
        ResourceLock unlocker = writeUnlocker;
        if (unlocker == null) {
            unlocker = newWriteUnlocker();
            // No synchronization necessary since overwrite is of no matter.
            writeUnlocker = unlocker;
        }
        writeLock().lock();
        return unlocker;
    }

    private ResourceLock newReadUnlocker() {
        return () -> this.readLock().unlock();
    }

    private ResourceLock newWriteUnlocker() {
        return () -> this.writeLock().unlock();
    }
}
