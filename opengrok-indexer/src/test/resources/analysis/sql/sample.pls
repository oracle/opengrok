create or replace package ut_utils authid definer is
  /*
  utPLSQL - Version 3
  Copyright 2016 - 2017 utPLSQL Project

  Licensed under the Apache License, Version 2.0 (the "License"):
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
  */

  /**
   * Common utilities and constants used throughout utPLSQL framework
   *
   */

  gc_version                 constant varchar2(50) := 'v3.0.4.1461-develop';

  /* Constants: Event names */
  gc_run                     constant varchar2(12) := 'run';
  gc_suite                   constant varchar2(12) := 'suite';
  gc_before_all              constant varchar2(12) := 'before_all';
  gc_before_each             constant varchar2(12) := 'before_each';
  gc_before_test             constant varchar2(12) := 'before_test';
  gc_test                    constant varchar2(12) := 'test';
  gc_test_execute            constant varchar2(12) := 'test_execute';
  gc_after_test              constant varchar2(10) := 'after_test';
  gc_after_each              constant varchar2(12) := 'after_each';
  gc_after_all               constant varchar2(12) := 'after_all';

  /* Constants: Test Results */
  tr_disabled                constant number(1) := 0; -- test/suite was disabled
  tr_success                 constant number(1) := 1; -- test passed
  tr_failure                 constant number(1) := 2; -- one or more expectations failed
  tr_error                   constant number(1) := 3; -- exception was raised

  tr_disabled_char           constant varchar2(8) := 'Disabled'; -- test/suite was disabled
  tr_success_char            constant varchar2(7) := 'Success'; -- test passed
  tr_failure_char            constant varchar2(7) := 'Failure'; -- one or more expectations failed
  tr_error_char              constant varchar2(5) := 'Error'; -- exception was raised

  /*
    Constants: Rollback type for ut_test_object
  */
  gc_rollback_auto           constant number(1) := 0; -- rollback after each test and suite
  gc_rollback_manual         constant number(1) := 1; -- leave transaction control manual
  --gc_rollback_on_error       constant number(1) := 2; -- rollback tests only on error

  ex_unsupported_rollback_type exception;
  gc_unsupported_rollback_type constant pls_integer := -20200;
  pragma exception_init(ex_unsupported_rollback_type, -20200);

  ex_path_list_is_empty exception;
  gc_path_list_is_empty constant pls_integer := -20201;
  pragma exception_init(ex_path_list_is_empty, -20201);

  ex_invalid_path_format exception;
  gc_invalid_path_format constant pls_integer := -20202;
  pragma exception_init(ex_invalid_path_format, -20202);

  ex_suite_package_not_found exception;
  gc_suite_package_not_found constant pls_integer := -20204;
  pragma exception_init(ex_suite_package_not_found, -20204);

  -- Reporting event time not supported
  ex_invalid_rep_event_time exception;
  gc_invalid_rep_event_time constant pls_integer := -20210;
  pragma exception_init(ex_invalid_rep_event_time, -20210);

  -- Reporting event name not supported
  ex_invalid_rep_event_name exception;
  gc_invalid_rep_event_name constant pls_integer := -20211;
  pragma exception_init(ex_invalid_rep_event_name, -20211);

  -- Any of tests failed
  ex_some_tests_failed exception;
  gc_some_tests_failed constant pls_integer := -20213;
  pragma exception_init(ex_some_tests_failed, -20213);

  -- Any of tests failed
  ex_invalid_version_no exception;
  gc_invalid_version_no constant pls_integer := -20214;
  pragma exception_init(ex_invalid_version_no, -20214);

  gc_max_storage_varchar2_len constant integer := 4000;
  gc_max_output_string_length constant integer := 4000;
  gc_max_input_string_length  constant integer := gc_max_output_string_length - 2; --we need to remove 2 chars for quotes around string
  gc_more_data_string         constant varchar2(5) := '[...]';
  gc_overflow_substr_len      constant integer := gc_max_input_string_length - length(gc_more_data_string);
  gc_number_format            constant varchar2(100) := 'TM9';
  gc_date_format              constant varchar2(100) := 'yyyy-mm-dd"T"hh24:mi:ss';
  gc_timestamp_format         constant varchar2(100) := 'yyyy-mm-dd"T"hh24:mi:ssxff';
  gc_timestamp_tz_format      constant varchar2(100) := 'yyyy-mm-dd"T"hh24:mi:ssxff tzh:tzm';
  gc_null_string              constant varchar2(4) := 'NULL';

  type t_version is record(
    major  natural,
    minor  natural,
    bugfix natural,
    build  natural
  );


  /**
   * Converts test results into strings
   *
   * @param a_test_result numeric representation of test result
   *
   * @return a string representation of a test_result.
   */
  function test_result_to_char(a_test_result integer) return varchar2;

  function to_test_result(a_test boolean) return integer;

  /**
   * Generates a unique name for a savepoint
   * Uses sys_guid, as timestamp gives only miliseconds on Windows and is not unique
   * Issue: #506 for details on the implementation approach
   */
  function gen_savepoint_name return varchar2;

  procedure debug_log(a_message varchar2);

  procedure debug_log(a_message clob);

  function to_string(a_value varchar2, a_qoute_char varchar2 := '''') return varchar2;

  function to_string(a_value clob, a_qoute_char varchar2 := '''') return varchar2;

  function to_string(a_value blob, a_qoute_char varchar2 := '''') return varchar2;

  function to_string(a_value boolean) return varchar2;

  function to_string(a_value number) return varchar2;

  function to_string(a_value date) return varchar2;

  function to_string(a_value timestamp_unconstrained) return varchar2;

  function to_string(a_value timestamp_tz_unconstrained) return varchar2;

  function to_string(a_value timestamp_ltz_unconstrained) return varchar2;

  function to_string(a_value yminterval_unconstrained) return varchar2;

  function to_string(a_value dsinterval_unconstrained) return varchar2;

  function boolean_to_int(a_value boolean) return integer;

  function int_to_boolean(a_value integer) return boolean;

  /**
   * Validates passed value against supported rollback types
   */
  procedure validate_rollback_type(a_rollback_type number);


  /**
   *
   * Splits a given string into table of string by delimiter.
   * The delimiter gets removed.
   * If null passed as any of the parameters, empty table is returned.
   * If no occurence of a_delimiter found in a_text then text is returned as a single row of the table.
   * If no text between delimiters found then an empty row is returned, example:
   *   string_to_table( 'a,,b', ',' ) gives table ut_varchar2_list( 'a', null, 'b' );
   *
   * @param a_string                 the text to be split.
   * @param a_delimiter              the delimiter character or string
   * @param a_skip_leading_delimiter determines if the leading delimiter should be ignored, used by clob_to_table
   *
   * @return table of varchar2 values
   */
  function string_to_table(a_string varchar2, a_delimiter varchar2:= chr(10), a_skip_leading_delimiter varchar2 := 'N') return ut_varchar2_list;

  /**
   * Splits a given string into table of string by delimiter.
   * Default value of a_max_amount is 8191 because of code can contains multibyte character.
   * The delimiter gets removed.
   * If null passed as any of the parameters, empty table is returned.
   * If split text is longer than a_max_amount it gets split into pieces of a_max_amount.
   * If no text between delimiters found then an empty row is returned, example:
   *   string_to_table( 'a,,b', ',' ) gives table ut_varchar2_list( 'a', null, 'b' );
   *
   * @param a_clob       the text to be split.
   * @param a_delimiter  the delimiter character or string (default chr(10) )
   * @param a_max_amount the maximum length of returned string (default 8191)
   * @return table of varchar2 values
   */
  function clob_to_table(a_clob clob, a_max_amount integer := 8191, a_delimiter varchar2:= chr(10)) return ut_varchar2_list;

  function table_to_clob(a_text_table ut_varchar2_list, a_delimiter varchar2:= chr(10)) return clob;

  /**
   * Returns time difference in seconds (with miliseconds) between given timestamps
   */
  function time_diff(a_start_time timestamp with time zone, a_end_time timestamp with time zone) return number;

  /**
   * Returns a text indented with spaces except the first line.
   */
  function indent_lines(a_text varchar2, a_indent_size integer := 4, a_include_first_line boolean := false) return varchar2;


  /**
   * Returns a list of object that are part of utPLSQL framework
   */
  function get_utplsql_objects_list return ut_object_names;

  /**
   * Append a line to the end of ut_varchar2_lst
   */
  procedure append_to_varchar2_list(a_list in out nocopy ut_varchar2_list, a_line varchar2);

  procedure append_to_clob(a_src_clob in out nocopy clob, a_new_data clob);
  procedure append_to_clob(a_src_clob in out nocopy clob, a_new_data varchar2);

  function convert_collection(a_collection ut_varchar2_list) return ut_varchar2_rows;

  /**
   * Set session's action and module using dbms_application_info
   */
  procedure set_action(a_text in varchar2);

  /**
   * Set session's client info using dbms_application_info
   */
  procedure set_client_info(a_text in varchar2);

  function to_xpath(a_list varchar2, a_ancestors varchar2 := '/*/') return varchar2;

  function to_xpath(a_list ut_varchar2_list, a_ancestors varchar2 := '/*/') return varchar2;

  procedure cleanup_temp_tables;

  /**
   * Converts version string into version record
   *
   * @param    a_version_no string representation of version in format vX.X.X.X where X is a positive integer
   * @return   t_version    record with up to four positive numbers containing version
   * @throws   20214        if passed version string is not matching version pattern
   */
  function to_version(a_version_no varchar2) return t_version;


  /**
  * Saves data from dbms_output buffer into a global temporary table (cache)
  *   used to store dbms_output buffer captured before the run
  *
  */
  procedure save_dbms_output_to_cache;

  /**
  * Reads data from global temporary table (cache) abd puts it back into dbms_output
  *   used to recover dbms_output buffer data after a run is complete
  *
  */
  procedure read_cache_to_dbms_output;


  /**
   * Function is used to reference to utPLSQL owned objects in dynamic sql statements executed from packages with invoker rights
   *
   * @return the name of the utPSQL schema owner
   */
  function ut_owner return varchar2;


  /**
   * Used in dynamic sql select statements to maintain balance between
   *   number of hard-parses and optimiser accurancy for cardinality of collections
   *
   *
   * @return 3, for inputs of: 1-9; 33 for input of 10 - 99; 333 for (100 - 999)
   */
  function scale_cardinality(a_cardinality natural) return natural;

end ut_utils;
/

create or replace package body ut_suite_builder is
  /*
  utPLSQL - Version 3
  Copyright 2016 - 2017 utPLSQL Project

  Licensed under the Apache License, Version 2.0 (the "License"):
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
  */

  ------------------

  function create_suite(a_object ut_annotated_object) return ut_logical_suite is
    l_is_suite              boolean := false;
    l_is_test               boolean := false;
    l_suite_disabled        boolean := false;
    l_test_disabled         boolean := false;
    l_suite_items           ut_suite_items := ut_suite_items();
    l_suite_name            varchar2(4000);

    l_default_setup_proc    varchar2(250 char);
    l_default_teardown_proc varchar2(250 char);
    l_suite_setup_proc      varchar2(250 char);
    l_suite_teardown_proc   varchar2(250 char);
    l_suite_path            varchar2(4000 char);

    l_proc_name             varchar2(250 char);

    l_suite                 ut_logical_suite;
    l_test                  ut_test;

    l_suite_rollback        integer;

    l_beforetest_procedure  varchar2(250 char);
    l_aftertest_procedure   varchar2(250 char);
    l_rollback_type         integer;
    l_displayname           varchar2(4000);
    function is_last_annotation_for_proc(a_annotations ut_annotations, a_index binary_integer) return boolean is
    begin
      return a_index = a_annotations.count or a_annotations(a_index).subobject_name != nvl(a_annotations(a_index+1).subobject_name, ' ');
    end;
  begin
    l_suite_rollback := ut_utils.gc_rollback_auto;
    for i in 1 .. a_object.annotations.count loop

      if a_object.annotations(i).subobject_name is null then

        if a_object.annotations(i).name in ('suite','displayname') then
          l_suite_name := a_object.annotations(i).text;
          if a_object.annotations(i).name = 'suite' then
            l_is_suite := true;
          end if;
        elsif a_object.annotations(i).name = 'disabled' then
          l_suite_disabled := true;
        elsif a_object.annotations(i).name = 'suitepath' and  a_object.annotations(i).text is not null then
          l_suite_path := a_object.annotations(i).text || '.' || lower(a_object.object_name);
        elsif a_object.annotations(i).name = 'rollback' then
          if lower(a_object.annotations(i).text) = 'manual' then
            l_suite_rollback := ut_utils.gc_rollback_manual;
          else
            l_suite_rollback := ut_utils.gc_rollback_auto;
          end if;
        end if;

      elsif l_is_suite then

        l_proc_name := a_object.annotations(i).subobject_name;

        if a_object.annotations(i).name = 'beforeeach' and l_default_setup_proc is null then
          l_default_setup_proc := l_proc_name;
        elsif a_object.annotations(i).name = 'aftereach' and l_default_teardown_proc is null then
          l_default_teardown_proc := l_proc_name;
        elsif a_object.annotations(i).name = 'beforeall' and l_suite_setup_proc is null then
          l_suite_setup_proc := l_proc_name;
        elsif a_object.annotations(i).name = 'afterall' and l_suite_teardown_proc is null then
          l_suite_teardown_proc := l_proc_name;


        elsif a_object.annotations(i).name = 'disabled' then
          l_test_disabled := true;
        elsif a_object.annotations(i).name = 'beforetest' then
          l_beforetest_procedure := a_object.annotations(i).text;
        elsif a_object.annotations(i).name = 'aftertest' then
          l_aftertest_procedure := a_object.annotations(i).text;
        elsif a_object.annotations(i).name in ('displayname','test') then
          l_displayname := a_object.annotations(i).text;
          if a_object.annotations(i).name = 'test' then
            l_is_test := true;
          end if;
        elsif a_object.annotations(i).name = 'rollback' then
          if lower(a_object.annotations(i).text) = 'manual' then
            l_rollback_type := ut_utils.gc_rollback_manual;
          elsif lower(a_object.annotations(i).text) = 'auto' then
            l_rollback_type := ut_utils.gc_rollback_auto;
          end if;
        end if;

        if l_is_test and is_last_annotation_for_proc(a_object.annotations, i) then
          l_suite_items.extend;
          l_suite_items(l_suite_items.last) :=
            ut_test(a_object_owner          => a_object.object_owner
                   ,a_object_name           => a_object.object_name
                   ,a_name                  => l_proc_name
                   ,a_description           => l_displayname
                   ,a_rollback_type         => coalesce(l_rollback_type, l_suite_rollback)
                   ,a_disabled_flag         => l_test_disabled
                   ,a_before_test_proc_name => l_beforetest_procedure
                   ,a_after_test_proc_name  => l_aftertest_procedure);

          l_is_test := false;
          l_test_disabled := false;
          l_aftertest_procedure  := null;
          l_beforetest_procedure := null;
          l_rollback_type        := null;
        end if;

      end if;
    end loop;

    if l_is_suite then
      l_suite := ut_suite (
          a_object_owner          => a_object.object_owner,
          a_object_name           => a_object.object_name,
          a_name                  => a_object.object_name, --this could be different for sub-suite (context)
          a_path                  => l_suite_path,  --a patch for this suite (excluding the package name of current suite)
          a_description           => l_suite_name,
          a_rollback_type         => l_suite_rollback,
          a_disabled_flag         => l_suite_disabled,
          a_before_all_proc_name  => l_suite_setup_proc,
          a_after_all_proc_name   => l_suite_teardown_proc
      );
      for i in 1 .. l_suite_items.count loop
        l_test := treat(l_suite_items(i) as ut_test);
        l_test.set_beforeeach(l_default_setup_proc);
        l_test.set_aftereach(l_default_teardown_proc);
        l_test.path := l_suite.path  || '.' ||  l_test.name;
        l_suite.add_item(l_test);
      end loop;
    end if;

    return l_suite;

  end create_suite;

  function build_suites_hierarchy(a_suites_by_path tt_schema_suites) return tt_schema_suites is
    l_result            tt_schema_suites;
    l_suite_path        varchar2(4000 char);
    l_parent_path       varchar2(4000 char);
    l_name              varchar2(4000 char);
    l_suites_by_path    tt_schema_suites;
  begin
    l_suites_by_path := a_suites_by_path;
    --were iterating in reverse order of the index by path table
    -- so the first paths will be the leafs of hierarchy and next will their parents
    l_suite_path  := l_suites_by_path.last;
    ut_utils.debug_log('Input suites to process = '||l_suites_by_path.count);

    while l_suite_path is not null loop
      l_parent_path := substr( l_suite_path, 1, instr(l_suite_path,'.',-1)-1);
      ut_utils.debug_log('Processing l_suite_path = "'||l_suite_path||'", l_parent_path = "'||l_parent_path||'"');
      --no parent => I'm a root element
      if l_parent_path is null then
        ut_utils.debug_log('  suite "'||l_suite_path||'" is a root element - adding to return list.');
        l_result(l_suite_path) := l_suites_by_path(l_suite_path);
      -- not a root suite - need to add it to a parent suite
      else
        --parent does not exist and needs to be added
        if not l_suites_by_path.exists(l_parent_path) then
          l_name  := substr( l_parent_path, instr(l_parent_path,'.',-1)+1);
          ut_utils.debug_log('  Parent suite "'||l_parent_path||'" not found in the list - Adding suite "'||l_name||'"');
          l_suites_by_path(l_parent_path) :=
            ut_logical_suite(
              a_object_owner => l_suites_by_path(l_suite_path).object_owner,
              a_object_name => l_name, a_name => l_name, a_path => l_parent_path
            );
        else
          ut_utils.debug_log('  Parent suite "'||l_parent_path||'" found in list of suites');
        end if;
        ut_utils.debug_log('  adding suite "'||l_suite_path||'" to "'||l_parent_path||'" items');
        l_suites_by_path(l_parent_path).add_item( l_suites_by_path(l_suite_path) );
      end if;
      l_suite_path := l_suites_by_path.prior(l_suite_path);
    end loop;
    ut_utils.debug_log(l_result.count||' root suites created.');
    return l_result;
  end;

  function build_suites(a_annotated_objects sys_refcursor) return t_schema_suites_info is
    l_suite             ut_logical_suite;
    l_annotated_objects ut_annotated_objects;
    l_all_suites        tt_schema_suites;
    l_result            t_schema_suites_info;
  begin
    fetch a_annotated_objects bulk collect into l_annotated_objects;
    close a_annotated_objects;

    for i in 1 .. l_annotated_objects.count loop
      l_suite := create_suite(l_annotated_objects(i));
      if l_suite is not null then
        l_all_suites(l_suite.path) := l_suite;
        l_result.suite_paths(l_suite.object_name) := l_suite.path;
      end if;
    end loop;

    --build hierarchical structure of the suite
    -- Restructure single-dimension list into hierarchy of suites by the value of %suitepath attribute value
    l_result.schema_suites := build_suites_hierarchy(l_all_suites);

    return l_result;
  end;

  function build_schema_suites(a_owner_name varchar2) return t_schema_suites_info is
    l_annotations_cursor sys_refcursor;
  begin
    -- form the single-dimension list of suites constructed from parsed packages
    open l_annotations_cursor for
      q'[select value(x)
          from table(
            ]'||ut_utils.ut_owner||q'[.ut_annotation_manager.get_annotated_objects(:a_owner_name, 'PACKAGE')
          )x ]'
      using a_owner_name;

    return build_suites(l_annotations_cursor);
  end;

end ut_suite_builder;
/
/*http://example.com.*/
/* comment /* comment */
comment
*/
