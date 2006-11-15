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
 * Copyright 2005 Trond Norbye.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"@(#)Editor.java 1.1     06/02/22 SMI"
 */

package org.opensolaris.opengrok.search.scope;

/**
 * The Editor class is used as an interface to hide the details on how to
 * display a file.
 *
 * @author Trond Norbye
 *
 * @todo The class should be refactored to hide the command field. It should
 * @todo have a method to load its config attributes (the caller provides the
 * @todo preference object. It should also get it's own getConfigPanel()
 */
public abstract class Editor {
    /**
     * Holds value of property name.
     */
    private String name;

    /**
     * Holds value of property command.
     */
    private String command;

    /**
     * Creates a new instance of Editor
     */
    public Editor() {
        name = null;
        command = null;
    }

    /**
     * Get the textual representation of this object
     *
     * @return The display-name of the editor
     */
    public String toString() {
        return name;
    }

    /**
     * Getter for property name.
     *
     * @return Value of property name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Setter for property name.
     *
     * @param name New value of property name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Getter for property command.
     *
     * @return Value of property command.
     */
    public String getCommand() {
        return this.command;
    }

    /**
     * Setter for property command.
     *
     * @param command New value of property command.
     */
    public void setCommand(String command) {
        this.command = command;
    }

    /**
     * Is it possible to modify the command on this editor or not
     *
     * @return true if the user is able to modify the command used to start
     *         this editor or not
     */
    public abstract boolean isEditable();

    /**
     * Display a named file in the editor
     *
     * @param filename The name of the file to display
     * @param lineno The line number to position the caret at (null if unknown)
     *
     * @throws java.lang.Exception if an error occurs while starting the editor
     *         or reading the file
     */
    public abstract void displayFile(String filename, Integer lineno)
        throws Exception;
}
