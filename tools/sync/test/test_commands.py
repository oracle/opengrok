#!/usr/bin/env python3

import unittest
import os
import sys

sys.path.insert(0, os.path.abspath(
                os.path.join(os.path.dirname(__file__), '..')))

import commands
from commands import Commands, CommandsBase


class TestApp(unittest.TestCase):
    def test_str(self):
        cmds = Commands(CommandsBase("opengrok-master",
                        [['foo'], ["bar"]]))
        self.assertEqual("opengrok-master", str(cmds))

    @unittest.skipUnless(os.name.startswith("posix"), "requires Unix")
    def test_run_retcodes(self):
        cmds = Commands(CommandsBase("opengrok-master",
                        [["/bin/echo"], ["/bin/true"], ["/bin/false"]]))
        cmds.run()
        # print(p.retcodes)
        self.assertEqual({'/bin/echo opengrok-master': 0,
                          '/bin/true opengrok-master': 0,
                          '/bin/false opengrok-master': 1}, cmds.retcodes)

    @unittest.skipUnless(os.name.startswith("posix"), "requires Unix")
    def test_project_subst(self):
        cmds = Commands(CommandsBase("test-subst",
                        [["/bin/echo", '%PROJECT%']]))
        cmds.run()
        self.assertEqual(['test-subst\n'],
                         cmds.outputs['/bin/echo test-subst'])

if __name__ == '__main__':
    unittest.main()
