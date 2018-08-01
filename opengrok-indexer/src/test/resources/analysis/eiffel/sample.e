-- All source code and binary programs included in Gobo Eiffel
-- are distributed under the terms and conditions of the MIT
-- License:
-- 
--     The MIT License
-- 
--     Copyright (c) <year> <copyright holders>
-- 
--     Permission is hereby granted, free of charge, to any person obtaining
--     a copy of this software and associated documentation files (the
--     "Software"), to deal in the Software without restriction, including
--     without limitation the rights to use, copy, modify, merge, publish,
--     distribute, sublicense, and/or sell copies of the Software, and to
--     permit persons to whom the Software is furnished to do so, subject to
--     the following conditions:
-- 
--     The above copyright notice and this permission notice shall be included
--     in all copies or substantial portions of the Software.
-- 
--     THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
--     EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
--     MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
--     IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
--     CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
--     TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
--     SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
-- 
-- This license is an OSI Approved License:
-- 
--     http://opensource.org/licenses/mit-license.html
-- 
-- and a GPL-Compatible Free Software License:
-- 
--     http://www.gnu.org/licenses/license-list.html#X11License
-- 
-- --
-- Copyright (c) 1997-2007, Eric Bezault and others

note

	description:

		"Scanners for config files made up of name/value pairs and preprocessor instructions"

	copyright: "Copyright (c) 2007-2013, Eric Bezault and others"
	license: "MIT License"
	date: "$Date$"
	revision: "$Revision$"
	note0: "http://example.com"
	note1: "[http://example.com]"
	note2: "{http://example.com}"
	note3: "xx{http://example.com}xx"
	note4: "xx[http://example.com]xx"

deferred class UT_CONFIG_SCANNER

inherit

	YY_COMPRESSED_SCANNER_SKELETON
		rename
			make as make_compressed_scanner_skeleton,
			reset as reset_compressed_scanner_skeleton
		redefine
			wrap, output
		end

	UT_CONFIG_TOKENS
		export {NONE} all end


feature -- Status report

	valid_start_condition (sc: INTEGER): BOOLEAN
			-- Is `sc' a valid start condition?
		do
			Result := (INITIAL <= sc and sc <= S_EMPTY_LINE)
		end

feature {NONE} -- Implementation

	yy_build_tables
			-- Build scanner tables.
		do
			yy_nxt := yy_nxt_template
			yy_chk := yy_chk_template
			yy_base := yy_base_template
			yy_def := yy_def_template
			yy_ec := yy_ec_template
			yy_meta := yy_meta_template
			yy_accept := yy_accept_template
		end

	yy_execute_action (yy_act: INTEGER)
			-- Execute semantic action.
		do
			inspect yy_act
when 1, 2 then
--|#line 40 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 40")
end

						-- Comment.
						set_start_condition (S_SKIP_EOL)
					
when 3 then
--|#line 45 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 45")
end

						last_token := P_IFDEF
						set_start_condition (S_PREPROC)
					
when 4 then
--|#line 49 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 49")
end

						last_token := P_IFNDEF
						set_start_condition (S_PREPROC)
					
when 5 then
--|#line 53 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 53")
end

						last_token := P_ELSE
						set_start_condition (S_PREPROC)
					
when 6 then
--|#line 57 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 57")
end

						last_token := P_ENDIF
						set_start_condition (S_PREPROC)
					
when 7 then
--|#line 61 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 61")
end

						last_token := P_INCLUDE
						set_start_condition (S_PREPROC)
					
when 8 then
--|#line 65 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 65")
end

						last_token := P_DEFINE
						set_start_condition (S_PREPROC)
					
when 9 then
--|#line 69 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 69")
end

						last_token := P_UNDEF
						set_start_condition (S_PREPROC)
					
when 10 then
--|#line 73 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 73")
end

						last_token := P_NAME
						last_string_value := text
						check attached last_string_value as l_last_string_value then
							STRING_.left_adjust (l_last_string_value)
						end
						set_start_condition (S_NAME)
					
when 11 then
--|#line 81 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 81")
end

						last_token := P_EOL
						line_nb := line_nb + 1
					
when 12 then
--|#line 85 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 85")
end

						set_start_condition (S_EMPTY_LINE)
					
when 13 then
--|#line 91 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 91")
end

						line_nb := line_nb + 1
						set_start_condition (INITIAL)
					
when 14 then
--|#line 95 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 95")
end

						set_start_condition (INITIAL)
					
when 15 then
--|#line 101 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 101")
end
-- Separator.
when 16 then
--|#line 102 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 102")
end

						last_token := P_STRING
						last_string_value := text_substring (2, text_count - 1)
					
when 17 then
--|#line 106 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 106")
end

						last_token := P_NAME
						last_string_value := text
					
when 18 then
--|#line 110 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 110")
end
last_token := P_AND
when 19 then
--|#line 111 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 111")
end
last_token := P_OR
when 20 then
--|#line 112 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 112")
end

						last_token := P_EOL
						line_nb := line_nb + 1
						set_start_condition (INITIAL)
					
when 21 then
--|#line 124 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 124")
end
-- Separator.
when 22 then
--|#line 125 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 125")
end

						last_token := P_COLON
						set_start_condition (S_VALUE)
					
when 23 then
--|#line 132 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 132")
end
-- Separator.
when 24 then
--|#line 133 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 133")
end

						last_token := P_VALUE
						last_string_value := text
					
when 25 then
--|#line 137 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 137")
end

						last_token := P_EOL
						line_nb := line_nb + 1
						set_start_condition (INITIAL)
					
when 26 then
--|#line 149 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 149")
end

						last_token := P_EOL
						line_nb := line_nb + 1
						set_start_condition (INITIAL)
					
when 27 then
--|#line 160 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 160")
end

						last_token := text_item (1).code
						set_start_condition (INITIAL)
					
when 28 then
--|#line 0 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 0")
end
last_token := yyError_token
fatal_error ("scanner jammed")
			else
				last_token := yyError_token
				fatal_error ("fatal scanner internal error: no action found")
			end
			yy_set_beginning_of_line
		end

	yy_execute_eof_action (yy_sc: INTEGER)
			-- Execute EOF semantic action.
		do
			inspect yy_sc
when 1 then
--|#line 117 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 117")
end

						last_token := P_EOL
						set_start_condition (INITIAL)
					
when 5 then
--|#line 142 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("Executing scanner user-code from file 'ut_config_scanner.l' at line 142")
end

						last_token := P_EOL
						set_start_condition (INITIAL)
					
when 6 then
--|#line 154 "ut_config_scanner.l"
debug ("GELEX")
	std.error.put_line ("xx[Executing scanner user-code from file
    "ut_config_scanner.l"]x" at line 154]xx")
end

						last_token := P_EOL
						set_start_condition (INITIAL)
					
			else
				terminate
			end
		end

feature {NONE} -- Table templates

	yy_nxt_template: SPECIAL [INTEGER]
			-- Template for `yy_nxt'
		local
			an_array: ARRAY [INTEGER]
		once
			create an_array.make_filled (0, 0, 220)
			yy_nxt_template_1 (an_array)
			yy_nxt_template_2 (an_array)
			Result := yy_fixed_array (an_array)
		end

	yy_nxt_template_1 (an_array: ARRAY [INTEGER])
			-- Fill chunk #1 of template for `yy_nxt'.
		do
			yy_array_subcopy (an_array, <<
			    0,   16,   17,   18,   16,   19,   16,   20,   21,   16,
			   21,   21,   21,   21,   21,   21,   21,   21,   21,   16,
			   16,   22,   23,   24,   16,   25,   26,   26,   16,   26,
			   26,   26,   26,   26,   26,   26,   26,   26,   27,   30,
			   30,   33,   34,   33,   34,   87,   31,   31,   36,   57,
			   58,   37,   59,   38,   40,   60,   86,   67,   85,   84,
			   41,   42,   68,   43,   83,   82,   81,   44,   88,   80,
			   79,   88,   78,   45,   36,   77,   76,   37,   75,   38,
			   62,   62,   74,   62,   62,   62,   73,   72,   62,   71,
			   70,   69,   66,   65,   64,   55,   53,   52,   62,   16,

			   16,   16,   16,   16,   16,   16,   16,   16,   28,   28,
			   28,   28,   28,   28,   28,   28,   28,   32,   32,   32,
			   32,   32,   32,   32,   32,   32,   39,   63,   46,   39,
			   39,   39,   39,   39,   47,   47,   61,   56,   47,   47,
			   47,   47,   47,   49,   49,   49,   49,   51,   51,   51,
			   51,   51,   51,   51,   51,   51,   54,   54,   39,   54,
			   54,   54,   54,   54,   54,   40,   40,   55,   40,   40,
			   40,   40,   40,   40,   45,   45,   53,   45,   45,   45,
			   45,   45,   45,   62,   62,   52,   62,   62,   62,   62,
			   62,   62,   50,   48,   46,   39,   88,   35,   35,   29, yy_Dummy>>,
			1, 200, 0)
		end

	yy_nxt_template_2 (an_array: ARRAY [INTEGER])
			-- Fill chunk #2 of template for `yy_nxt'.
		do
			yy_array_subcopy (an_array, <<
			   29,   15,   88,   88,   88,   88,   88,   88,   88,   88,
			   88,   88,   88,   88,   88,   88,   88,   88,   88,   88,
			   88, yy_Dummy>>,
			1, 21, 200)
		end

	yy_chk_template: SPECIAL [INTEGER]
			-- Template for `yy_chk'
		local
			an_array: ARRAY [INTEGER]
		once
			create an_array.make_filled (0, 0, 220)
			yy_chk_template_1 (an_array)
			yy_chk_template_2 (an_array)
			Result := yy_fixed_array (an_array)
		end

	yy_chk_template_1 (an_array: ARRAY [INTEGER])
			-- Fill chunk #1 of template for `yy_chk'.
		do
			yy_array_subcopy (an_array, <<
			    0,    2,    2,    2,    2,    2,    2,    2,    2,    2,
			    2,    2,    2,    2,    2,    2,    2,    2,    2,    2,
			    3,    3,    3,    3,    3,    3,    3,    3,    3,    3,
			    3,    3,    3,    3,    3,    3,    3,    3,    3,    9,
			   10,   11,   11,   12,   12,   86,    9,   10,   17,   42,
			   42,   17,   43,   17,   19,   43,   82,   59,   81,   78,
			   19,   19,   59,   19,   77,   76,   75,   19,   20,   74,
			   73,   20,   71,   20,   36,   70,   69,   36,   68,   36,
			   45,   45,   67,   45,   45,   45,   66,   65,   45,   64,
			   61,   60,   58,   57,   56,   55,   53,   51,   45,   89,

			   89,   89,   89,   89,   89,   89,   89,   89,   90,   90,
			   90,   90,   90,   90,   90,   90,   90,   91,   91,   91,
			   91,   91,   91,   91,   91,   91,   92,   47,   46,   92,
			   92,   92,   92,   92,   93,   93,   44,   41,   93,   93,
			   93,   93,   93,   94,   94,   94,   94,   95,   95,   95,
			   95,   95,   95,   95,   95,   95,   96,   96,   39,   96,
			   96,   96,   96,   96,   96,   97,   97,   33,   97,   97,
			   97,   97,   97,   97,   98,   98,   30,   98,   98,   98,
			   98,   98,   98,   99,   99,   28,   99,   99,   99,   99,
			   99,   99,   27,   25,   22,   21,   15,   14,   13,    8, yy_Dummy>>,
			1, 200, 0)
		end

	yy_chk_template_2 (an_array: ARRAY [INTEGER])
			-- Fill chunk #2 of template for `yy_chk'.
		do
			yy_array_subcopy (an_array, <<
			    7,   88,   88,   88,   88,   88,   88,   88,   88,   88,
			   88,   88,   88,   88,   88,   88,   88,   88,   88,   88,
			   88, yy_Dummy>>,
			1, 21, 200)
		end

	yy_base_template: SPECIAL [INTEGER]
			-- Template for `yy_base'
		once
			Result := yy_fixed_array (<<
			    0,    0,    0,   19,    0,    0,    0,  197,  196,   37,
			   38,   39,   41,  195,  194,  196,  201,   46,  201,   49,
			   66,  188,  192,  201,    0,  187,    0,  173,  182,  201,
			  174,  201,    0,  165,  201,  201,   72,    0,    0,  151,
			    0,  125,   34,   39,  120,   79,  126,  123,  201,    0,
			  201,   94,  201,   94,    0,   93,   81,   76,   81,   46,
			   81,   79,    0,  201,   75,   75,   72,   70,   67,   61,
			   63,   56,  201,   57,   56,   54,   47,   51,   47,  201,
			  201,   45,   45,  201,  201,  201,   33,  201,  201,   98,
			  107,  116,  124,  133,  137,  146,  155,  164,  173,  182, yy_Dummy>>)
		end

	yy_def_template: SPECIAL [INTEGER]
			-- Template for `yy_def'
		once
			Result := yy_fixed_array (<<
			    0,   89,   88,   88,    3,   89,   89,   90,   90,   89,
			   89,   91,   91,   89,   89,   88,   88,   92,   88,   88,
			   92,   20,   88,   88,   93,   88,   94,   88,   95,   88,
			   88,   88,   96,   88,   88,   88,   20,   19,   20,   20,
			   97,   88,   88,   88,   88,   98,   88,   93,   88,   94,
			   88,   95,   88,   88,   96,   88,   88,   88,   88,   88,
			   88,   88,   99,   88,   88,   88,   88,   88,   88,   88,
			   88,   88,   88,   88,   88,   88,   88,   88,   88,   88,
			   88,   88,   88,   88,   88,   88,   88,   88,    0,   88,
			   88,   88,   88,   88,   88,   88,   88,   88,   88,   88, yy_Dummy>>)
		end

	yy_ec_template: SPECIAL [INTEGER]
			-- Template for `yy_ec'
		local
			an_array: ARRAY [INTEGER]
		once
			create an_array.make_filled (0, 0, 256)
			yy_ec_template_1 (an_array)
			yy_ec_template_2 (an_array)
			Result := yy_fixed_array (an_array)
		end

	yy_ec_template_1 (an_array: ARRAY [INTEGER])
			-- Fill chunk #1 of template for `yy_ec'.
		do
			yy_array_subcopy (an_array, <<
			    0,    1,    1,    1,    1,    1,    1,    1,    1,    2,
			    3,    1,    1,    2,    1,    1,    1,    1,    1,    1,
			    1,    1,    1,    1,    1,    1,    1,    1,    1,    1,
			    1,    1,    2,    1,    4,    5,    1,    1,    6,    1,
			    1,    1,    1,    1,    1,    7,    8,    1,    8,    8,
			    8,    8,    8,    8,    8,    8,    8,    8,    9,    1,
			    1,    1,    1,    1,    1,    8,    8,   10,   11,   12,
			   13,    8,    8,   14,    8,    8,   15,    8,   16,    8,
			    8,    8,    8,   17,    8,   18,    8,    8,    8,    8,
			    8,    1,    1,    1,    1,    8,    1,    8,    8,   10,

			   11,   12,   13,    8,    8,   14,    8,    8,   15,    8,
			   16,    8,    8,    8,    8,   17,    8,   18,    8,    8,
			    8,    8,    8,    1,   19,    1,    1,    1,    1,    1,
			    1,    1,    1,    1,    1,    1,    1,    1,    1,    1,
			    1,    1,    1,    1,    1,    1,    1,    1,    1,    1,
			    1,    1,    1,    1,    1,    1,    1,    1,    1,    1,
			    1,    1,    1,    1,    1,    1,    1,    1,    1,    1,
			    1,    1,    1,    1,    1,    1,    1,    1,    1,    1,
			    1,    1,    1,    1,    1,    1,    1,    1,    1,    1,
			    1,    1,    1,    1,    1,    1,    1,    1,    1,    1, yy_Dummy>>,
			1, 200, 0)
		end

	yy_ec_template_2 (an_array: ARRAY [INTEGER])
			-- Fill chunk #2 of template for `yy_ec'.
		do
			yy_array_subcopy (an_array, <<
			    1,    1,    1,    1,    1,    1,    1,    1,    1,    1,
			    1,    1,    1,    1,    1,    1,    1,    1,    1,    1,
			    1,    1,    1,    1,    1,    1,    1,    1,    1,    1,
			    1,    1,    1,    1,    1,    1,    1,    1,    1,    1,
			    1,    1,    1,    1,    1,    1,    1,    1,    1,    1,
			    1,    1,    1,    1,    1,    1,    1, yy_Dummy>>,
			1, 57, 200)
		end

	yy_meta_template: SPECIAL [INTEGER]
			-- Template for `yy_meta'
		once
			Result := yy_fixed_array (<<
			    0,    1,    2,    3,    4,    5,    1,    6,    7,    1,
			    7,    7,    7,    7,    7,    7,    7,    8,    9,    1, yy_Dummy>>)
		end

	yy_accept_template: SPECIAL [INTEGER]
			-- Template for `yy_accept'
		once
			Result := yy_fixed_array (<<
			    0,    0,    0,    0,    0,    0,    0,   14,   14,    0,
			    0,    0,    0,    0,    0,   29,   27,   12,   11,   27,
			   10,   10,   15,   20,   27,   27,   17,   27,   14,   13,
			   21,   22,   24,   23,   25,   26,   12,    0,   10,   10,
			    2,    0,    0,    0,    0,    1,   15,    0,   18,   17,
			   19,   14,   13,   21,   24,   23,    0,    0,    0,    0,
			    0,    0,    1,   16,    0,    0,    0,    0,    0,    0,
			    0,    0,    5,    0,    0,    0,    0,    0,    0,    6,
			    3,    0,    0,    9,    8,    4,    0,    7,    0, yy_Dummy>>)
		end

feature {NONE} -- Constants

	yyJam_base: INTEGER = 201
			-- Position in `yy_nxt'/`yy_chk' tables
			-- where default jam table starts

	yyJam_state: INTEGER = 88
			-- State id corresponding to jam state

	yyTemplate_mark: INTEGER = 89
			-- Mark between normal states and templates

	yyNull_equiv_class: INTEGER = 1
			-- Equivalence code for NULL character

	yyReject_used: BOOLEAN = false
			-- Is `reject' called?

	yyVariable_trail_context: BOOLEAN = false
			-- Is there a regular expression with
			-- both leading and trailing parts having
			-- variable length?

	yyReject_or_variable_trail_context: BOOLEAN = false
			-- Is `reject' called or is there a
			-- regular expression with both leading
			-- and trailing parts having variable length?

	yyNb_rules: INTEGER = 28
			-- Number of rules

	yyEnd_of_buffer: INTEGER = 29
			-- End of buffer rule code

	yyLine_used: BOOLEAN = false
			-- Are line and column numbers used?

	yyPosition_used: BOOLEAN = false
			-- Is `position' used?

	INITIAL: INTEGER = 0b0
	S_PREPROC: INTEGER = 0B1
	S_READLINE: INTEGER = 0x2
	S_SKIP_EOL: INTEGER = 0X3
	S_NAME: INTEGER = 0c4
	S_VALUE: INTEGER = 0C5
	S_EMPTY_LINE: INTEGER = 6
			-- Start condition codes

feature -- User-defined features



feature {NONE} -- Initialization

	make
			-- Create a new scanner.
		do
			make_with_buffer (Empty_buffer)
			last_string_value := ""
			line_nb := 1
		end

feature -- Initialization

	reset
			-- Reset scanner before scanning next input.
		do
			reset_compressed_scanner_skeleton
			last_string_value := ""
			line_nb := 1
		end

feature -- Access

	line_nb: INTEGER
			-- Current line number

	include_stack: DS_STACK [YY_BUFFER]
			-- Input buffers not completely parsed yet
		deferred
		ensure
			include_stack_not_void: Result /= Void
			no_void_buffer: not Result.has_void
		end

	line_nb_stack: DS_STACK [INTEGER]
			-- Line numbers in the corresponding input buffers in `include_stack'
		deferred
		ensure
			line_nb_stack_not_void: Result /= Void
			same_count: Result.count = include_stack.count
		end

feature -- Status report

	ignored: BOOLEAN
			-- Is current line ignored?
		deferred
		end

feature -- Element change

	wrap: BOOLEAN
			-- Should current scanner terminate when end of file is reached?
			-- True unless an include file was being processed.
		local
			l_old_buffer: YY_BUFFER
			a_file: KI_CHARACTER_INPUT_STREAM
		do
			if not include_stack.is_empty then
				l_old_buffer := input_buffer
				set_input_buffer (include_stack.item)
				line_nb := line_nb_stack.item
				line_nb_stack.remove
				include_stack.remove
				if attached {YY_FILE_BUFFER} l_old_buffer as l_old_file_buffer then
					a_file := l_old_file_buffer.file
					if a_file.is_closable then
						a_file.close
					end
				end
				set_start_condition (INITIAL)
			else
				Result := True
			end
		end

feature -- Output

	output (a_text: like text)
			-- Silently ignore `a_text'.
		do
		end

end
