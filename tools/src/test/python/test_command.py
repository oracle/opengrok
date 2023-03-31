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
# Portions Copyright (c) 2020, Krystof Tulinger <k.tulinger@seznam.cz>
#

import os
import platform
import tempfile
import time

import pytest

from conftest import posix_only, system_binary
from opengrok_tools.utils.command import Command


def test_subst_append_default():
    cmd = Command(['foo', '=ARG=', 33, 'bar'],
                  args_subst={"ARG": "blah"},
                  args_append=["1", "2"])
    assert cmd.cmd == ['foo', '=blah=', '33', 'bar', '1', '2']


def test_subst_append_exclsubst():
    """
    Exclusive substitution is on and was performed, therefore no arguments
    should be appended.
    """
    cmd = Command(['foo', 'ARG', 'bar'],
                  args_subst={"ARG": "blah"},
                  args_append=["1", "2"],
                  excl_subst=True)
    assert cmd.cmd == ['foo', 'blah', 'bar']


def test_subst_append_exclsubst_nosubst():
    """
    Exclusive substituation is on however no substitution was performed,
    therefore arguments can be appended.
    """
    cmd = Command(['foo', 'bar'],
                  args_subst={"ARG": "blah"},
                  args_append=["1", "2"],
                  excl_subst=True)
    assert cmd.cmd == ['foo', 'bar', '1', '2']


def test_subst_multiple():
    """
    Test multiple substitutions within one argument.
    """
    cmd = Command(['foo', 'aaa%ARG%bbb%XYZ%ccc', 'bar'],
                  args_subst={"%ARG%": "blah", "%XYZ%": "xyz"})
    assert cmd.cmd == ['foo', 'aaablahbbbxyzccc', 'bar']


def test_subst_env():
    """
    Test substitution with value treated as environment variable.
    """
    os.environ["FOO"] = "foo"
    cmd = Command(['foo', 'aaa%ARG%bbb', 'bar'],
                  args_subst={"%ARG%": "$FOO"})
    assert cmd.cmd == ['foo', 'aaafoobbb', 'bar']
    os.environ.pop("FOO")


# On Windows the return code is actually 1.
@pytest.mark.skipif(platform.system() == 'Windows',
                    reason="broken on Windows")
def test_execute_nonexistent():
    cmd = Command(['/baaah', '/etc/passwd'])
    cmd.execute()
    assert cmd.getretcode() is None
    assert cmd.getstate() == Command.ERRORED


@posix_only
def test_getoutput():
    cmd = Command(['/bin/ls', '/etc/passwd'])
    cmd.execute()
    assert cmd.getoutput() == ['/etc/passwd\n']


@posix_only
def test_work_dir():
    os.chdir("/")
    orig_cwd = os.getcwd()
    assert tempfile.gettempdir() != orig_cwd
    cmd = Command(['/bin/ls', '/etc/passwd'],
                  work_dir=tempfile.gettempdir())
    cmd.execute()
    assert os.getcwd() == orig_cwd


@system_binary('env')
def test_env(env_binary):
    cmd = Command([env_binary], env_vars={'FOO': 'BAR', 'A': 'B'})
    cmd.execute()
    assert "FOO=BAR\n" in cmd.getoutput()


@system_binary('true')
@system_binary('false')
def test_retcode(true_binary, false_binary):
    cmd = Command([false_binary])
    cmd.execute()
    assert cmd.getretcode() != 0
    assert cmd.getstate() == Command.FINISHED

    cmd = Command([true_binary])
    cmd.execute()
    assert cmd.getretcode() == 0
    assert cmd.getstate() == Command.FINISHED


def test_command_to_str():
    cmd = Command(["foo", "bar"])
    assert str(cmd) == "foo bar"


@system_binary('sleep')
def test_command_timeout(sleep_binary):
    timeout = 30
    cmd = Command([sleep_binary, str(timeout)], timeout=3)
    start_time = time.time()
    cmd.execute()
    # Check the process is no longer around.
    assert cmd.getpid() is not None
    with pytest.raises(ProcessLookupError):
        os.kill(cmd.getpid(), 0)
    elapsed_time = time.time() - start_time
    assert elapsed_time < timeout
    assert cmd.getstate() == Command.TIMEDOUT
    assert cmd.getretcode() is None


@system_binary('sleep')
def test_command_notimeout(sleep_binary):
    cmd_timeout = 30
    cmd = Command([sleep_binary, "3"], timeout=cmd_timeout)
    cmd.execute()
    assert cmd.getstate() == Command.FINISHED
    assert cmd.getretcode() == 0


@posix_only
def test_stderr():
    cmd = Command(["/bin/cat", "/foo/bar", "/etc/passwd"],
                  redirect_stderr=False)
    cmd.execute()
    assert cmd.getstate() == Command.FINISHED
    assert cmd.getretcode() != 0
    # The error could contain localized output strings so check just
    # for the path itself.
    assert '/foo/bar' in "\n".join(cmd.geterroutput())
    assert '/foo/bar' not in "\n".join(cmd.getoutput())
    assert 'root' in "\n".join(cmd.getoutput())


# This test needs the "/bin/cat" command, therefore it is Unix only.
@posix_only
def test_long_output():
    """
    Test that output thread in the Command class captures all of the output.
    (and also it does not hang the command by filling up the pipe)

    By default stderr is redirected to stdout.
    """
    # in bytes, should be enough to fill a pipe
    num_lines = 5000
    line_length = 1000
    num_bytes = num_lines * (line_length + 1)
    with tempfile.NamedTemporaryFile() as file:
        for _ in range(num_lines):
            file.write(b'A' * line_length)
            file.write(b'\n')
        file.flush()
        assert os.path.getsize(file.name) == num_bytes

        cmd = Command(["/bin/cat", file.name])
        cmd.execute()

        assert cmd.getstate() == Command.FINISHED
        assert cmd.getretcode() == 0
        assert cmd.geterroutput() is None
        assert len("".join(cmd.getoutput())) == num_bytes


@posix_only
def test_resource_limits():
    """
    Simple smoke test for setting resource limits.
    """
    resource_limits = {"RLIMIT_NOFILE": 1024}
    cmd = Command(['/bin/cat', '/etc/passwd'],
                  resource_limits=resource_limits)
    cmd.set_resource_limits(resource_limits)
    cmd.execute()
