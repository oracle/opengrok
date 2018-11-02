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

from opengrok_tools.utils.commandsequence import CommandSequence, CommandSequenceBase


class TestApp(unittest.TestCase):
    def test_str(self):
        cmds = CommandSequence(CommandSequenceBase("opengrok-master",
                                                   [{"command": ['foo']}, {"command": ["bar"]}]))
        self.assertEqual("opengrok-master", str(cmds))

    @unittest.skipUnless(os.path.exists('/bin/sh') and os.path.exists('/bin/echo'), "requires Unix")
    def test_run_retcodes(self):
        cmds = CommandSequence(CommandSequenceBase("opengrok-master",
                                                   [{"command": ["/bin/echo"]},
                                             {"command": ["/bin/sh", "-c", "echo " + CommandSequence.PROJECT_SUBST + "; exit 0"]},
                                             {"command": ["/bin/sh", "-c", "echo " + CommandSequence.PROJECT_SUBST + "; exit 1"]}]))
        cmds.run()
        self.assertEqual({'/bin/echo opengrok-master': 0,
                          '/bin/sh -c echo opengrok-master; exit 0': 0,
                          '/bin/sh -c echo opengrok-master; exit 1': 1}, cmds.retcodes)

    @unittest.skipUnless(os.path.exists('/bin/sh') and os.path.exists('/bin/echo'), "requires Unix")
    def test_terminate_after_non_zero_code(self):
        cmds = CommandSequence(CommandSequenceBase("opengrok-master",
                                                   [{"command": ["/bin/sh", "-c", "echo " + CommandSequence.PROJECT_SUBST + "; exit 255"]},
                                      {"command": ["/bin/echo"]}]))
        cmds.run()
        self.assertEqual({'/bin/sh -c echo opengrok-master; exit 255': 255}, cmds.retcodes)

    @unittest.skipUnless(os.path.exists('/bin/sh') and os.path.exists('/bin/echo'), "requires Unix")
    def test_exit_2_handling(self):
        cmds = CommandSequence(CommandSequenceBase("opengrok-master",
                                                   [{"command": ["/bin/sh", "-c", "echo " + CommandSequence.PROJECT_SUBST + "; exit 2"]},
                                      {"command": ["/bin/echo"]}]))
        cmds.run()
        self.assertEqual({'/bin/sh -c echo opengrok-master; exit 2': 2}, cmds.retcodes)
        self.assertFalse(cmds.failed)

    @unittest.skipUnless(os.path.exists('/bin/echo'), "requires Unix")
    def test_project_subst(self):
        cmds = CommandSequence(CommandSequenceBase("test-subst",
                                                   [{"command": ["/bin/echo", CommandSequence.PROJECT_SUBST]}]))
        cmds.run()
        self.assertEqual(['test-subst\n'],
                         cmds.outputs['/bin/echo test-subst'])

if __name__ == '__main__':
    unittest.main()
