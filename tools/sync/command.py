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

import os
import logging
import subprocess
import string


class Command:
    """
    wrapper for synchronous execution of commands via subprocess.Popen()
    and getting their output (stderr is redirected to stdout) and return value
    """

    def __init__(self, cmd, args_subst=None, args_append=None, logger=None,
                 excl_subst=False):
        self.cmd = cmd
        self.state = "notrun"
        self.excl_subst = excl_subst

        self.logger = logger or logging.getLogger(__name__)
        logging.basicConfig()

        if args_subst or args_append:
            self.fill_arg(args_append, args_subst)

    def __str__(self):
        return " ".join(self.cmd)

    def execute(self):
        """
        Execute the command and capture its output and return code.
        """

        out = []
        try:
            self.logger.debug("command = {}".format(self.cmd))
            p = subprocess.Popen(self.cmd, stderr=subprocess.STDOUT,
                                 stdout=subprocess.PIPE)
            p.wait()
        except KeyboardInterrupt as e:
            self.logger.debug("Got KeyboardException while processing ",
                              exc_info=True)
            self.state = "interrupted"
        except OSError as e:
            self.logger.debug("Got OS error", exc_info=True)
            self.state = "errored"
        else:
            if p.stdout is not None:
                self.logger.debug("Program output:")
                for line in p.stdout:
                    self.logger.debug(line.rstrip(os.linesep.encode("ascii")))
                    out.append(line.decode())

            self.state = "finished"
            self.returncode = int(p.returncode)
            self.logger.debug("{} -> {}".format(self.cmd, self.getretcode()))
            self.out = out
            p.stdout.close()

    def fill_arg(self, args_append=None, args_subst=None):
        """
        Replace argument names with actual values or append arguments
        to the command vector.
        """

        newcmd = []
        subst_done = False
        for i, cmdarg in enumerate(self.cmd):
            if args_subst:
                if cmdarg in args_subst.keys():
                    self.logger.debug("replacing cmdarg with {}".
                                      format(args_subst[cmdarg]))
                    newcmd.append(args_subst[cmdarg])
                    subst_done = True
                else:
                    newcmd.append(self.cmd[i])
            else:
                newcmd.append(self.cmd[i])

        if args_append and (not self.excl_subst or not subst_done):
            self.logger.debug("appending {}".format(args_append))
            newcmd.extend(args_append)

        self.cmd = newcmd

    def getretcode(self):
        if self.state is not "finished":
            return None
        else:
            return self.returncode

    def getoutput(self):
        if self.state is "finished":
            return self.out
        else:
            return None
