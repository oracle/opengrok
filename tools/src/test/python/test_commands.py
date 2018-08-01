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
import os
import sys


sys.path.insert(0, os.path.abspath(
                os.path.join(os.path.dirname(__file__), '..', '..',
                'main', 'python')))

from commands import Commands, CommandsBase


class TestApp(unittest.TestCase):
    def test_str(self):
        cmds = Commands(CommandsBase("opengrok-master",
                        [{"command": ['foo']}, {"command": ["bar"]}]))
        self.assertEqual("opengrok-master", str(cmds))

    @unittest.skipUnless(os.name.startswith("posix"), "requires Unix")
    def test_run_retcodes(self):
        cmds = Commands(CommandsBase("opengrok-master",
                        [{"command": ["/bin/echo"]},
                         {"command": ["/bin/true"]},
                         {"command": ["/bin/false"]}]))
        cmds.run()
        # print(p.retcodes)
        self.assertEqual({'/bin/echo opengrok-master': 0,
                          '/bin/true opengrok-master': 0,
                          '/bin/false opengrok-master': 1}, cmds.retcodes)

    @unittest.skipUnless(os.name.startswith("posix"), "requires Unix")
    def test_project_subst(self):
        cmds = Commands(CommandsBase("test-subst",
                        [{"command": ["/bin/echo", '%PROJECT%']}]))
        cmds.run()
        self.assertEqual(['test-subst\n'],
                         cmds.outputs['/bin/echo test-subst'])

if __name__ == '__main__':
    unittest.main()
