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
        p = Commands(CommandsBase("opengrok-master",
                     [['foo'], ["bar"]]))
        self.assertEqual("opengrok-master", str(p))

    def test_run_retcodes(self):
        p = Commands(CommandsBase("opengrok-master",
                     [["/bin/echo"], ["/bin/true"], ["/bin/false"]]))
        p.run()
        # print(p.retcodes)
        self.assertEqual({'/bin/echo opengrok-master': 0,
                          '/bin/true opengrok-master': 0,
                          '/bin/false opengrok-master': 1}, p.retcodes)

    # def test_outputs(self):

    # def test_get_cmd_output(self):
        # print p.get_cmd_output('/var/tmp/print-error.ksh opengrok-master')


if __name__ == '__main__':
    unittest.main()
