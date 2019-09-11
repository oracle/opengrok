/*
 * This work is licensed under the Creative Commons Attribution-ShareAlike 4.0
 * International License. To view a copy of this license, visit
 * https://creativecommons.org/licenses/by-sa/4.0/ or send a letter to
 * Creative Commons, PO Box 1866, Mountain View, CA 94042, USA.
 *
 * Copyright (c) 2017, https://stackoverflow.com/users/7583219/skoskav
 * Copyright (c) 2011, https://stackoverflow.com/questions/6965731/are-locks-autocloseable
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 *
 * Used under CC 4 with modifications noted as follows as required by license:
 * 2019-09-10 -- cfraire@me.com, solely Javadoc changes.
 */

package org.opengrok.indexer.util;

import java.util.concurrent.locks.Lock;

/**
 * Represents an API for try-with-resources management of a {@link Lock}.
 */
public interface ResourceLock extends AutoCloseable {

    /**
     * Unlocking doesn't throw any checked exception.
     */
    @Override
    void close();
}
