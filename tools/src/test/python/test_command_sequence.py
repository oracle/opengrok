#!/usr/bin/env python3

#
# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License (the "License").
# You may not use this file except in compliance with the License.
#
# See LICENSE.txt included in this distribution for the specific
# language governing permissions and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at LICENSE.txt.
# If applicable, add the following below this CDDL HEADER, with the
# fields enclosed by brackets "[]" replaced with your own identifying
# information: Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#

#
# Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
#

import os
import sys

import pytest
import tempfile

from requests.exceptions import HTTPError

from opengrok_tools.utils.commandsequence import CommandSequence, \
    CommandSequenceBase, CommandConfigurationException
from opengrok_tools.utils.patterns import PROJECT_SUBST, CALL_PROPERTY, COMMAND_PROPERTY


def test_str():
    cmds = CommandSequence(CommandSequenceBase("opengrok-master",
                                               [{"command": {"args": ['foo']}},
                                                {"command": {"args": ["bar"]}}]))
    assert str(cmds) == "opengrok-master"


def test_invalid_configuration_commands_none():
    with pytest.raises(CommandConfigurationException) as exc_info:
        CommandSequence(CommandSequenceBase("foo", None))

    assert str(exc_info.value) == "commands is None"


def test_invalid_configuration_commands_no_command():
    with pytest.raises(CommandConfigurationException) as exc_info:
        CommandSequence(CommandSequenceBase("foo", [{"command": {"args": ['foo']}},
                                                    {"foo": "bar"}]))

    assert str(exc_info.value).startswith("command dictionary has unknown key")


def test_invalid_configuration_commands_no_dict1():
    with pytest.raises(CommandConfigurationException) as exc_info:
        CommandSequence(CommandSequenceBase("foo", [{"command": {"args": ['foo']}},
                                                    {"command": ["bar"]}]))

    assert str(exc_info.value).startswith("command value not a dictionary")


def test_invalid_configuration_commands_no_dict2():
    with pytest.raises(CommandConfigurationException) as exc_info:
        CommandSequence(CommandSequenceBase("foo", [{"command": {"args": ['foo']}},
                                                    "command"]))

    assert str(exc_info.value).find("is not a dictionary") != -1


@pytest.mark.parametrize('type', [COMMAND_PROPERTY, CALL_PROPERTY])
def test_invalid_configuration_commands_none_value(type):
    with pytest.raises(CommandConfigurationException) as exc_info:
        CommandSequence(CommandSequenceBase("foo", [{type: None}]))

    assert str(exc_info.value).find("empty") != -1


def test_timeout_propagation():
    """
    Make sure the timeouts propagate from CommandSequenceBase to CommandSequence.
    """
    expected_timeout = 11
    expected_api_timeout = 22
    cmd_seq_base = CommandSequenceBase("foo", [{"command": {"args": ['foo']}}],
                                       api_timeout=expected_timeout,
                                       async_api_timeout=expected_api_timeout)
    cmd_seq = CommandSequence(cmd_seq_base)
    assert cmd_seq.api_timeout == expected_timeout
    assert cmd_seq.async_api_timeout == expected_api_timeout


@pytest.mark.skipif(not os.path.exists('/bin/sh')
                    or not os.path.exists('/bin/echo'),
                    reason="requires Unix")
def test_run_retcodes():
    cmd_list = [{"command": {"args": ["/bin/echo"]}},
                {"command": {"args": ["/bin/sh", "-c",
                 "echo " + PROJECT_SUBST + "; exit 0"]}},
                {"command": {"args": ["/bin/sh", "-c",
                 "echo " + PROJECT_SUBST + "; exit 1"]}}]
    cmds = CommandSequence(CommandSequenceBase("opengrok-master", cmd_list))
    cmds.run()
    assert cmds.retcodes == {
        '/bin/echo opengrok-master': 0,
        '/bin/sh -c echo opengrok-master; exit 0': 0,
        '/bin/sh -c echo opengrok-master; exit 1': 1
    }


@pytest.mark.skipif(not os.path.exists('/bin/sh')
                    or not os.path.exists('/bin/echo'),
                    reason="requires Unix")
def test_terminate_after_non_zero_code():
    cmd_list = [{"command": {"args": ["/bin/sh", "-c",
                 "echo " + PROJECT_SUBST + "; exit 255"]}},
                {"command": {"args": ["/bin/echo"]}}]
    cmds = CommandSequence(CommandSequenceBase("opengrok-master", cmd_list))
    cmds.run()
    assert cmds.retcodes == {
        '/bin/sh -c echo opengrok-master; exit 255': 255
    }


@pytest.mark.skipif(not os.path.exists('/bin/sh')
                    or not os.path.exists('/bin/echo'),
                    reason="requires Unix")
def test_exit_2_handling():
    cmd_list = [{"command": {"args": ["/bin/sh", "-c",
                 "echo " + PROJECT_SUBST + "; exit 2"]}},
                {"command": {"args": ["/bin/echo"]}}]
    cmds = CommandSequence(CommandSequenceBase("opengrok-master", cmd_list))
    cmds.run()
    assert cmds.retcodes == {
        '/bin/sh -c echo opengrok-master; exit 2': 2
    }
    assert not cmds.failed


@pytest.mark.skipif(not os.path.exists('/bin/sh')
                    or not os.path.exists('/bin/echo'),
                    reason="requires Unix")
def test_driveon_flag():
    cmd_list = [{"command": {"args": ["/bin/sh", "-c",
                 "echo " + PROJECT_SUBST + "; exit 2"]}},
                {"command": {"args": ["/bin/echo"]}},
                {"command": {"args": ["/bin/sh", "-c",
                                      "echo " + PROJECT_SUBST +
                                      "; exit 1"]}},
                {"command": {"args": ["/bin/sh", "-c",
                             "echo " + PROJECT_SUBST]}}]
    cmds = CommandSequence(CommandSequenceBase("opengrok-master",
                                               cmd_list, driveon=True))
    cmds.run()
    assert cmds.retcodes == {
        '/bin/sh -c echo opengrok-master; exit 2': 2,
        '/bin/echo opengrok-master': 0,
        '/bin/sh -c echo opengrok-master; exit 1': 1
    }
    assert cmds.failed


@pytest.mark.skipif(not os.path.exists('/bin/echo'),
                    reason="requires Unix")
def test_project_subst():
    cmd_list = [{"command": {"args": ["/bin/echo", PROJECT_SUBST]}}]
    cmds = CommandSequence(CommandSequenceBase("test-subst", cmd_list))
    cmds.run()

    assert cmds.outputs['/bin/echo test-subst'] == ['test-subst']


@pytest.mark.skipif(not os.path.exists('/bin/echo'),
                    reason="requires Unix")
def test_args_subst():
    cmd_list = [{"command": {"args": ["/bin/echo", "%PATTERN%"],
                             "args_subst": {"%PATTERN%": "foo"}}}]
    cmds = CommandSequence(CommandSequenceBase("test-subst", cmd_list))
    cmds.run()

    assert cmds.outputs['/bin/echo foo'] == ['foo']


@pytest.mark.skipif(not os.path.exists('/bin/echo'),
                    reason="requires Unix")
def test_args_subst_env():
    cmd_list = [{"command": {"args": ["/bin/echo", "%PATTERN%"],
                             "args_subst": {"%PATTERN%": "$FOO"}}}]
    os.environ["FOO"] = "bar"
    cmds = CommandSequence(CommandSequenceBase("test-subst", cmd_list))
    cmds.run()
    os.environ.pop("FOO")

    assert cmds.outputs['/bin/echo bar'] == ['bar']


def test_cleanup_exception():
    """
    If cleanup is not a list, exception should be thrown when initializing
    the CommandSequence object.
    """
    cleanup = {"cleanup": ["foo", PROJECT_SUBST]}
    with pytest.raises(CommandConfigurationException):
        CommandSequence(CommandSequenceBase("test-cleanup-list", None,
                                            cleanup=cleanup))


# /bin/cat returns 2 on Solaris
@pytest.mark.skipif(not os.path.exists('/usr/bin/touch') or
                    not os.path.exists('/bin/cat') or
                    sys.platform == "sunos5",
                    reason="requires Unix")
def test_cleanup():
    with tempfile.TemporaryDirectory() as tmpdir:
        file_foo = os.path.join(tmpdir, "foo")
        file_bar = os.path.join(tmpdir, "bar")
        cleanup_list = [{"command": {"args": ["/usr/bin/touch", file_foo]}},
                        {"command": {"args": ["/bin/cat", "/totallynonexistent"]}},
                        {"command": {"args": ["/usr/bin/touch", file_bar]}}]
        # Running 'cat' on non-existing entry causes it to return 1.
        cmd = ["/bin/cat", "/foobar"]
        cmd_list = [{"command": {"args": cmd}}]
        commands = CommandSequence(CommandSequenceBase("test-cleanup-list",
                                                       cmd_list,
                                                       cleanup=cleanup_list))
        assert commands is not None
        commands.run()
        assert list(commands.retcodes.values()) == [1]
        assert os.path.isfile(file_foo)
        assert os.path.isfile(file_bar)


def test_restful_fail(monkeypatch):
    class MockResponse:
        def p(self):
            raise HTTPError("foo")

        def __init__(self):
            self.status_code = 500
            self.raise_for_status = self.p

    def mock_response(uri, headers, data, params, proxies, timeout):
        return MockResponse()

    commands = CommandSequence(
        CommandSequenceBase("test-cleanup-list",
                            [{CALL_PROPERTY: {"uri": 'http://foo', "method": 'PUT', "data": 'data'}}]))
    assert commands is not None
    with monkeypatch.context() as m:
        m.setattr("requests.put", mock_response)
        commands.run()
        assert commands.check([]) == 1


def test_headers_init():
    headers = {'foo': 'bar'}

    # First verify that header parameter of a command does not impact class member.
    commands = CommandSequence(CommandSequenceBase("opengrok-master",
                                                   [{CALL_PROPERTY: {"uri": 'http://foo', "method": 'PUT',
                                                                     "data": 'data', "headers": headers}}]))

    assert commands.http_headers is None

    # Second, verify that init function propagates the headers.
    commands = CommandSequence(CommandSequenceBase("opengrok-master",
                                                   [{CALL_PROPERTY: {"uri": 'http://foo',
                                                                     "method": 'PUT', "data": 'data'}}],
                                                   http_headers=headers))

    assert commands.http_headers == headers
