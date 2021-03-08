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
# Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
#

import os
import sys

import pytest
import tempfile

from requests.exceptions import HTTPError

from opengrok_tools.utils.commandsequence import CommandSequence, \
    CommandSequenceBase
from opengrok_tools.utils.patterns import PROJECT_SUBST


def test_str():
    cmds = CommandSequence(CommandSequenceBase("opengrok-master",
                                               [{"command": ['foo']},
                                                {"command": ["bar"]}]))
    assert str(cmds) == "opengrok-master"


@pytest.mark.skipif(not os.path.exists('/bin/sh')
                    or not os.path.exists('/bin/echo'),
                    reason="requires Unix")
def test_run_retcodes():
    cmd_list = [{"command": ["/bin/echo"]},
                {"command": ["/bin/sh", "-c",
                 "echo " + PROJECT_SUBST + "; exit 0"]},
                {"command": ["/bin/sh", "-c",
                 "echo " + PROJECT_SUBST + "; exit 1"]}]
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
    cmd_list = [{"command": ["/bin/sh", "-c",
                 "echo " + PROJECT_SUBST + "; exit 255"]},
                {"command": ["/bin/echo"]}]
    cmds = CommandSequence(CommandSequenceBase("opengrok-master", cmd_list))
    cmds.run()
    assert cmds.retcodes == {
        '/bin/sh -c echo opengrok-master; exit 255': 255
    }


@pytest.mark.skipif(not os.path.exists('/bin/sh')
                    or not os.path.exists('/bin/echo'),
                    reason="requires Unix")
def test_exit_2_handling():
    cmd_list = [{"command": ["/bin/sh", "-c",
                 "echo " + PROJECT_SUBST + "; exit 2"]},
                {"command": ["/bin/echo"]}]
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
    cmd_list = [{"command": ["/bin/sh", "-c",
                 "echo " + PROJECT_SUBST + "; exit 2"]},
                {"command": ["/bin/echo"]},
                {"command": ["/bin/sh", "-c",
                             "echo " + PROJECT_SUBST +
                             "; exit 1"]},
                {"command": ["/bin/sh", "-c",
                             "echo " + PROJECT_SUBST]}]
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
    cmd_list = [{"command": ["/bin/echo", PROJECT_SUBST]}]
    cmds = CommandSequence(CommandSequenceBase("test-subst", cmd_list))
    cmds.run()

    assert cmds.outputs['/bin/echo test-subst'] == ['test-subst\n']


def test_cleanup_exception():
    """
    If cleanup is not a list, Exception should be thrown when initializing
    the CommandSequence object.
    """
    cleanup = {"cleanup": ["foo", PROJECT_SUBST]}
    with pytest.raises(Exception):
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
        cleanup_list = [{"command": ["/usr/bin/touch", file_foo]},
                        {"command": ["/bin/cat", "/totallynonexistent"]},
                        {"command": ["/usr/bin/touch", file_bar]}]
        # Running 'cat' on non-existing entry causes it to return 1.
        cmd = ["/bin/cat", "/foobar"]
        cmd_list = [{"command": cmd}]
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
                            [{'command': ['http://foo', 'PUT', 'data']}]))
    assert commands is not None
    with monkeypatch.context() as m:
        m.setattr("requests.put", mock_response)
        commands.run()
        assert commands.check([]) == 1


def test_headers_init():
    headers = {'foo': 'bar'}

    # First verify that header parameter of a command does not impact class member.
    commands = CommandSequence(CommandSequenceBase("opengrok-master",
                                                   [{'command': ['http://foo', 'PUT', 'data', headers]}]))

    assert commands.http_headers is None

    # Second, verify that init function propagates the headers.
    commands = CommandSequence(CommandSequenceBase("opengrok-master",
                                                   [{'command': ['http://foo', 'PUT', 'data']}],
                                                   http_headers=headers))

    assert commands.http_headers == headers
