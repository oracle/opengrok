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
# Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
#

import unittest
import sys
import os
import time


sys.path.insert(0, os.path.abspath(
                os.path.join(os.path.dirname(__file__), '..', '..',
                'main', 'python')))

from opengrok_tools.utils.command import Command
import tempfile

class TestApp(unittest.TestCase):
    #def __init__(self):
    #    logging.basicConfig(level=logging.DEBUG)

    def test_subst_append_default(self):
        cmd = Command(['foo', '=ARG=', 'bar'],
                      args_subst={"ARG": "blah"},
                      args_append=["1", "2"])
        self.assertEqual(['foo', '=blah=', 'bar', '1', '2'], cmd.cmd)

    def test_subst_append_exclsubst(self):
        """
        Exclusive substitution is on and was performed, therefore no arguments
        should be appended.
        """
        cmd = Command(['foo', 'ARG', 'bar'],
                      args_subst={"ARG": "blah"},
                      args_append=["1", "2"],
                      excl_subst=True)
        self.assertEqual(['foo', 'blah', 'bar'], cmd.cmd)

    def test_subst_append_exclsubst_nosubst(self):
        """
        Exclusive substituation is on however no substitution was performed,
        therefore arguments can be appended.
        """
        cmd = Command(['foo', 'bar'],
                      args_subst={"ARG": "blah"},
                      args_append=["1", "2"],
                      excl_subst=True)
        self.assertEqual(['foo', 'bar', '1', '2'], cmd.cmd)

    def test_execute_nonexistent(self):
        cmd = Command(['/baaah', '/etc/passwd'])
        cmd.execute()
        self.assertEqual(None, cmd.getretcode())
        self.assertEqual(Command.ERRORED, cmd.getstate())

    @unittest.skipUnless(os.name.startswith("posix"), "requires Unix")
    def test_getoutput(self):
        cmd = Command(['/bin/ls', '/etc/passwd'])
        cmd.execute()
        self.assertEqual(['/etc/passwd\n'], cmd.getoutput())

    @unittest.skipUnless(os.name.startswith("posix"), "requires Unix")
    def test_work_dir(self):
        os.chdir("/")
        orig_cwd = os.getcwd()
        self.assertNotEqual(orig_cwd, tempfile.gettempdir())
        cmd = Command(['/bin/ls', '/etc/passwd'],
                      work_dir=tempfile.gettempdir())
        cmd.execute()
        self.assertEqual(orig_cwd, os.getcwd())

    @unittest.skipUnless(os.name.startswith("posix"), "requires Unix")
    def test_env(self):
        cmd = Command(['/usr/bin/env'],
                      env_vars={'FOO': 'BAR', 'A': 'B'})
        cmd.execute()
        self.assertTrue("FOO=BAR\n" in cmd.getoutput())

    @unittest.skipUnless(os.path.exists('/bin/true') and os.path.exists('/bin/false'), "requires Unix")
    def test_retcode(self):
        cmd = Command(["/bin/false"])
        cmd.execute()
        self.assertNotEqual(0, cmd.getretcode())
        self.assertEqual(Command.FINISHED, cmd.getstate())

        cmd = Command(["/bin/true"])
        cmd.execute()
        self.assertEqual(0, cmd.getretcode())
        self.assertEqual(Command.FINISHED, cmd.getstate())

    @unittest.skipUnless(os.path.exists('/usr/bin/true') and os.path.exists('/usr/bin/false'), "requires Unix")
    def test_retcode_usr(self):
        cmd = Command(["/usr/bin/false"])
        cmd.execute()
        self.assertNotEqual(0, cmd.getretcode())
        self.assertEqual(Command.FINISHED, cmd.getstate())

        cmd = Command(["/usr/bin/true"])
        cmd.execute()
        self.assertEqual(0, cmd.getretcode())
        self.assertEqual(Command.FINISHED, cmd.getstate())

    def test_str(self):
        cmd = Command(["foo", "bar"])
        self.assertEqual("foo bar", str(cmd))

    @unittest.skipUnless(os.name.startswith("posix"), "requires Unix")
    def test_timeout(self):
        timeout = 30
        cmd = Command(["/bin/sleep", str(timeout)], timeout=3)
        start_time = time.time()
        cmd.execute()
        # Check the process is no longer around.
        self.assertIsNotNone(cmd.getpid())
        self.assertRaises(ProcessLookupError, os.kill, cmd.getpid(), 0)
        elapsed_time = time.time() - start_time
        self.assertTrue(elapsed_time < timeout)
        self.assertEqual(Command.TIMEDOUT, cmd.getstate())
        self.assertEqual(None, cmd.getretcode())

    @unittest.skipUnless(os.name.startswith("posix"), "requires Unix")
    def test_notimeout(self):
        cmd_timeout = 30
        cmd = Command(["/bin/sleep", "3"], timeout=cmd_timeout)
        cmd.execute()
        self.assertEqual(Command.FINISHED, cmd.getstate())
        self.assertEqual(0, cmd.getretcode())

    @unittest.skipUnless(os.name.startswith("posix"), "requires Unix")
    def test_stderr(self):
        cmd = Command(["/bin/cat", "/foo/bar", "/etc/passwd"],
                      redirect_stderr=False)
        cmd.execute()
        self.assertEqual(Command.FINISHED, cmd.getstate())
        self.assertNotEqual(0, cmd.getretcode())
        # The error could contain localized output strings so check just
        # for the path itself.
        self.assertTrue("/foo/bar" in "\n".join(cmd.geterroutput()))
        self.assertFalse("/foo/bar" in "\n".join(cmd.getoutput()))
        self.assertTrue("root" in "\n".join(cmd.getoutput()))

    @unittest.skipUnless(os.name.startswith("posix"), "requires Unix")
    def test_resource_limits(self):
        """
        Simple smoke test for setting resource limits.
        """
        resource_limits = {"RLIMIT_NOFILE": 1024}
        cmd = Command(['/bin/cat', '/etc/passwd'],
                      resource_limits=resource_limits)
        cmd.set_resource_limits(resource_limits)
        cmd.execute()

if __name__ == '__main__':
    unittest.main()
