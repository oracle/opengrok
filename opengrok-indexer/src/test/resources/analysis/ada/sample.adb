--
-- CDDL HEADER START
--
-- The contents of this file are subject to the terms of the
-- Common Development and Distribution License (the "License").
-- You may not use this file except in compliance with the License.
--
-- See LICENSE.txt included in this distribution for the specific
-- language governing permissions and limitations under the License.
--
-- When distributing Covered Code, include this CDDL HEADER in each
-- file and include the License file at LICENSE.txt.
-- If applicable, add the following below this CDDL HEADER, with the
-- fields enclosed by brackets "[]" replaced with your own identifying
-- information: Portions Copyright [yyyy] [name of copyright owner]
--
-- CDDL HEADER END
--

--
-- Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
--

with Ada.Text_IO; use Ada.Text_IO;
procedure Hello is
begin
	Put_Line("Hello, world!");
	Put_Line("""
		Hello?""");
	Put_Line('?');
	Put_Line('
');
	Put(0);
	Put(12);
	Put(123_456);
	Put(3.14159_26);
	Put(2#1111_1111#);
	Put(16#E#E1);
	Put(16#F.FF#E+2);
	Put_Line();
	Put_Line("Archimedes said ""Εύρηκα""");
end Hello;

-- Test a URL that is not matched fully by a rule using just {URIChar} and
-- {FnameChar}:
-- https://msdn.microsoft.com/en-us/library/windows/desktop/ms633591(v=vs.85).aspx
