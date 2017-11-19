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
 * Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.analysis.vb;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("PMD.AvoidThrowingRawExceptionTypes")
public final class Consts {

    private static final Set<String> reservedKeywords;

    static {
        HashSet<String> kwds = new HashSet<String>();
        populateKeywordSet(kwds);
        reservedKeywords = Collections.unmodifiableSet(kwds);
    }

    private Consts() {
        // Util class, can not be constructed.
    }

    private static void populateKeywordSet(Set<String> kwd) {
        kwd.add("AddHandler");
        kwd.add("AddressOf");
        kwd.add("Alias");
        kwd.add("And");
        kwd.add("AndAlso");
        kwd.add("As");
        kwd.add("Boolean");
        kwd.add("ByRef");
        kwd.add("Byte");
        kwd.add("ByVal");
        kwd.add("Call");
        kwd.add("Case");
        kwd.add("Catch");
        kwd.add("CBool");
        kwd.add("CByte");
        kwd.add("CChar");
        kwd.add("CDate");
        kwd.add("CDec");
        kwd.add("CDbl");
        kwd.add("Char");
        kwd.add("CInt");
        kwd.add("Class");
        kwd.add("CLng");
        kwd.add("CObj");
        kwd.add("Const");
        kwd.add("Continue");
        kwd.add("CSByte");
        kwd.add("CShort");
        kwd.add("CSng");
        kwd.add("CStr");
        kwd.add("CType");
        kwd.add("CUInt");
        kwd.add("CULng");
        kwd.add("CUShort");
        kwd.add("Date");
        kwd.add("Decimal");
        kwd.add("Declare");
        kwd.add("Default");
        kwd.add("Delegate");
        kwd.add("Dim");
        kwd.add("DirectCast");
        kwd.add("Do");
        kwd.add("Double");
        kwd.add("Each");
        kwd.add("Else");
        kwd.add("ElseIf");
        kwd.add("End");
        kwd.add("EndIf");
        kwd.add("Enum");
        kwd.add("Erase");
        kwd.add("Error");
        kwd.add("Event");
        kwd.add("Exit");
        kwd.add("False");
        kwd.add("Finally");
        kwd.add("For");
        kwd.add("Friend");
        kwd.add("Function");
        kwd.add("Get");
        kwd.add("GetType");
        kwd.add("Global");
        kwd.add("GoSub");
        kwd.add("GoTo");
        kwd.add("Handles");
        kwd.add("If");
        kwd.add("Implements");
        kwd.add("Imports");
        kwd.add("In");
        kwd.add("Inherits");
        kwd.add("Integer");
        kwd.add("Interface");
        kwd.add("Is");
        kwd.add("IsNot");
        kwd.add("Let");
        kwd.add("Lib");
        kwd.add("Like");
        kwd.add("Long");
        kwd.add("Loop");
        kwd.add("Me");
        kwd.add("Mod");
        kwd.add("Module");
        kwd.add("MustInherit");
        kwd.add("MustOverride");
        kwd.add("MyBase");
        kwd.add("MyClass");
        kwd.add("Namespace");
        kwd.add("Narrowing");
        kwd.add("New");
        kwd.add("Next");
        kwd.add("Not");
        kwd.add("Nothing");
        kwd.add("NotInheritable");
        kwd.add("NotOverridable");
        kwd.add("Object");
        kwd.add("Of");
        kwd.add("On");
        kwd.add("Operator");
        kwd.add("Option");
        kwd.add("Optional");
        kwd.add("Or");
        kwd.add("OrElse");
        kwd.add("Overloads");
        kwd.add("Overridable");
        kwd.add("Overrides");
        kwd.add("ParamArray");
        kwd.add("Partial");
        kwd.add("Private");
        kwd.add("Property");
        kwd.add("Protected");
        kwd.add("Public");
        kwd.add("RaiseEvent");
        kwd.add("ReadOnly");
        kwd.add("ReDim");
        kwd.add("REM");
        kwd.add("RemoveHandler");
        kwd.add("Resume");
        kwd.add("Return");
        kwd.add("SByte");
        kwd.add("Select");
        kwd.add("Set");
        kwd.add("Shadows");
        kwd.add("Shared");
        kwd.add("Short");
        kwd.add("Single");
        kwd.add("Static");
        kwd.add("Step");
        kwd.add("Stop");
        kwd.add("String");
        kwd.add("Structure");
        kwd.add("Sub");
        kwd.add("SyncLock");
        kwd.add("Then");
        kwd.add("Throw");
        kwd.add("To");
        kwd.add("True");
        kwd.add("Try");
        kwd.add("TryCast");
        kwd.add("TypeOf");
        kwd.add("Variant");
        kwd.add("Wend");
        kwd.add("UInteger");
        kwd.add("ULong");
        kwd.add("UShort");
        kwd.add("Using");
        kwd.add("When");
        kwd.add("While");
        kwd.add("Widening");
        kwd.add("With");
        kwd.add("WithEvents");
        kwd.add("WriteOnly");
        kwd.add("Xor");
    }

    static Set<String> getReservedKeywords() {
        return reservedKeywords;
    }
}
