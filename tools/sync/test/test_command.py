#!/usr/bin/env python

import unittest
import logging
import sys
import os

sys.path.insert(0, os.path.abspath(
                os.path.join(os.path.dirname(__file__), '..')))

import command
from command import Command


class TestApp(unittest.TestCase):
    #def __init__(self):
    #    logging.basicConfig(level=logging.DEBUG)

    def test_subst_append_default(self):
        cmd = Command(['foo', 'ARG', 'bar'],
                        args_subst={"ARG": "blah"},
                        args_append=["1", "2"])
        self.assertEqual(['foo', 'blah', 'bar', '1', '2'], cmd.cmd)

    def test_subst_append_exclsubst(self):
        cmd = Command(['foo', 'ARG', 'bar'],
                        args_subst={"ARG": "blah"},
                        args_append=["1", "2"],
                        excl_subst=True)
        self.assertEqual(['foo', 'blah', 'bar'], cmd.cmd)

    def test_execute_nonexistent(self):
        cmd = Command(['/baaah', '/etc/passwd'])
        cmd.execute()
        self.assertEqual(None, cmd.getretcode())

    def test_getoutput(self):
        """
        XXX this only works on Unix
        """
        cmd = Command(['/bin/ls', '/etc/passwd'])
        cmd.execute()
        self.assertEqual(['/etc/passwd\n'], cmd.getoutput())

    def test_retcode(self):
        """
        XXX this only works on Unix
        """

        cmd = Command(["/bin/false"])
        cmd.execute()
        self.assertNotEqual(0, cmd.getretcode())

        cmd = Command(["/bin/true"])
        cmd.execute()
        self.assertEqual(0, cmd.getretcode())

    def test_str(self):
        cmd = Command(["foo", "bar"])
        self.assertEqual("foo bar", str(cmd))


if __name__ == '__main__':
    unittest.main()
