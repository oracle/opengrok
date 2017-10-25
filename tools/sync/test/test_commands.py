#!/usr/bin/python

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
                     [["/bin/echo"], ["foo"]]))
        p.run()
        self.assertEqual({'/bin/echo opengrok-master': 0,
	                  'foo opengrok-master': None}, p.retcodes)

    # def test_outputs(self):

    # def test_get_cmd_output(self):
        # print p.get_cmd_output('/var/tmp/print-error.ksh opengrok-master')


if __name__ == '__main__':
    unittest.main()
