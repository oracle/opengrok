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
import os
import command
from command import Command


class CommandsBase:
    """
    Wrap the run of a set of Command instances.

    This class intentionally does not contain any logging
    so that it can be passed through Pool.map().
    """

    def __init__(self, name, commands):
        self.name = name
        self.commands = commands
        self.failed = False
        self.retcodes = {}
        self.outputs = {}

    def __str__(self):
        return str(self.name)

    def get_cmd_output(self, cmd, indent=""):
        str = ""
        if self.outputs[cmd]:
            for line in self.outputs[cmd]:
                str += '{}{}'.format(indent, line)

        return str

    def fill(self, retcodes, outputs, failed):
        self.retcodes = retcodes
        self.outputs = outputs
        self.failed = failed


class Commands(CommandsBase):
    def __init__(self, base):
        super().__init__(base.name, base.commands)

        self.logger = logging.getLogger(__name__)
        logging.basicConfig()

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
            self.retcodes[str(cmd)] = cmd.getretcode()
            self.outputs[str(cmd)] = cmd.getoutput()

            # If a command fails, terminate the sequence of commands.
            if cmd.getretcode() != 0:
                self.logger.debug("command {} failed, breaking".format(cmd))
                self.failed = True
                break

    def check(self, ignore_errors):
        """
	Check the output of the commands and perform logging.
	"""

        self.logger.debug("Output from {}:".format(self.name))
        for cmd in self.outputs.keys():
            if self.outputs[cmd] and len(self.outputs[cmd]) > 0:
                self.logger.debug("{}: {}".
                             format(cmd, self.outputs[cmd]))

        if self.name in ignore_errors:
            return

        if any(rv != 0 for rv in self.retcodes.values()):
            self.logger.error("processing of selfect {} failed".
                         format(self))
            indent = "  "
            self.logger.error("{}failed commands:".format(indent))
            failed_cmds = {k: v for k, v in
                           self.retcodes.items() if v != 0}
            indent = "    "
            for cmd in failed_cmds.keys():
                self.logger.error("{}'{}': {}".
                             format(indent, cmd, failed_cmds[cmd]))
                out = self.get_cmd_output(cmd,
                                          indent=indent + "  ")
                if out:
                    self.logger.error(out)
            self.logger.error("")

        errored_cmds = {k: v for k, v in self.outputs.items()
                        if "error" in str(v).lower()}
        if len(errored_cmds) > 0:
            self.logger.error("Command output in selfect {}"
                         " contains errors:".format(self.name))
            indent = "  "
            for cmd in errored_cmds.keys():
                self.logger.error("{}{}".format(indent, cmd))
                out = self.get_cmd_output(cmd,
                                          indent=indent + "  ")
                if out:
                    self.logger.error(out)
                self.logger.error("")
