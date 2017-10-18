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
# Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
#

import logging
import command
from command import Command
import os


class Commands:
    """
    Wrap the run of a set of Command instances.
    """

    def __init__(self, name, commands, logger=None):
        self.name = name
        self.commands = commands
        self.failed = False
        self.retcodes = {}
        self.outputs = {}
        # XXX this will not work in Python 2.x since logging contains
        # threading stuff that does not work with multiprocessing
        #self.logger = logger or logging.getLogger(__name__)
        #logging.basicConfig()

    def __str__(self):
        return str(self.name)

    def get_cmd_output(self, cmd, indent=""):
        str = ""
        #logger.debug("getting output for command {}".format(cmd))
        for line in self.outputs[cmd]:
            # logger.error('{}{}'.format(indent, line.rstrip(os.linesep)))
            str += '{}{}'.format(indent, line)

        return str

    def run(self):
        """
        Run the sequence of commands and capture their output and return code.
        First command that returns code other than 0 terminates the sequence.
        """
        for command in self.commands:
            cmd = Command(command,
                          args_subst={"ARG": self.name},
                          args_append=[self.name], excl_subst=True)
            cmd.execute()
            # XXX logger.debug("{} -> {}".format(command, cmd.getretcode()))
            # If a command fails, terminate the sequence of commands.
            self.retcodes[str(cmd)] = cmd.getretcode()
            self.outputs[str(cmd)] = cmd.getoutput()
            if cmd.getretcode() != 0:
                self.failed = True
                break
