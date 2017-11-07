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
import threading


class Command:
    """
    wrapper for synchronous execution of commands via subprocess.Popen()
    and getting their output (stderr is redirected to stdout) and return value
    """

    # state definitions
    FINISHED = "finished"
    INTERRUPTED = "interrupted"
    ERRORED = "errored"

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

        class OutputThread(threading.Thread):
            """
            Capture data from subprocess.Popen(). This avoids hangs when
            stdout/stderr buffers fill up.
            """

            def __init__(self):
                super(OutputThread, self).__init__()
                self.read_fd, self.write_fd = os.pipe()
                self.pipe_fobj = os.fdopen(self.read_fd)
                self.out = []
                self.start()

            def run(self):
                """
                It might happen that after the process is gone, the thread
                still has data to read from the pipe. Should probably introduce
                a boolean and set it to True under the 'if not line' block
                below and make the caller wait for it to become True.
                """
                while True:
                    line = self.pipe_fobj.readline()
                    if not line:
                        self.pipe_fobj.close()
                        return

                    self.out.append(line)

            def getoutput(self):
                return self.out

            def fileno(self):
                return self.write_fd

            def close(self):
                os.close(self.write_fd)

        othr = OutputThread()
        try:
            self.logger.debug("command = {}".format(self.cmd))
            p = subprocess.Popen(self.cmd, stderr=subprocess.STDOUT,
                                 stdout=othr)
            p.wait()
        except KeyboardInterrupt as e:
            self.logger.debug("Got KeyboardException while processing ",
                              exc_info=True)
            self.state = Command.INTERRUPTED
        except OSError as e:
            self.logger.debug("Got OS error", exc_info=True)
            self.state = Command.ERRORED
        else:
            self.state = Command.FINISHED
            self.returncode = int(p.returncode)
            self.logger.debug("{} -> {}".format(self.cmd, self.getretcode()))
        finally:
            othr.close()
            self.out = othr.getoutput()

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
        if self.state is not Command.FINISHED:
            return None
        else:
            return self.returncode

    def getoutput(self):
        if self.state is Command.FINISHED:
            return self.out
        else:
            return None

    def getstate(self):
        return self.state
