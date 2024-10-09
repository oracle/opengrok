#!/usr/bin/env python3

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
# Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
# Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
#

import os
import platform
import shutil

from .command import Command
from .utils import is_exe


class Java(Command):
    """
    java executable wrapper class
    """

    def __init__(self, command, logger=None, main_class=None, java=None,
                 jar=None, java_opts=None, classpath=None, env_vars=None,
                 redirect_stderr=True, doprint=False,
                 max_line_length=None, max_lines=None):

        if not java:
            java = self.FindJava(logger)
            if not java:
                raise Exception("Cannot find Java")

        if not is_exe(java):
            raise Exception("{} is not executable file".format(java))

        logger.debug("Java = {}".format(java))

        java_command = [java]
        if java_opts:
            java_command.extend(java_opts)
        if classpath:
            java_command.append('-classpath')
            java_command.append(classpath)
        if jar:
            java_command.append('-jar')
            java_command.append(jar)
        if main_class:
            java_command.append(main_class)
        env = None
        if env_vars:
            env = {}
            for spec in env_vars:
                if spec.find('=') != -1:
                    name, value = spec.split('=')
                    env[name] = value

        java_command.extend(command)
        logger.debug("Java command: {}".format(java_command))

        super().__init__(java_command, logger=logger, env_vars=env,
                         redirect_stderr=redirect_stderr, doprint=doprint,
                         max_line_length=max_line_length, max_lines=max_lines)

    def FindJava(self, logger):
        """
        Determine Java binary based on platform.
        """
        java = None
        system_name = platform.system()
        if system_name == 'SunOS':
            rel = platform.release()
            java_home = None
            if rel == '5.10':
                java_home = "/usr/jdk/instances/jdk1.7.0"
            elif rel == '5.11':
                java_home = "/usr/jdk/latest"

            if java_home and os.path.isdir(java_home):
                java = os.path.join(java_home, 'bin', 'java')
        elif system_name == 'Darwin':
            cmd = Command(['/usr/libexec/java_home'])
            cmd.execute()
            java = os.path.join(cmd.getoutputstr(), 'bin', 'java')
        elif system_name == 'Linux':
            link_path = '/etc/alternatives/java'
            if os.path.exists(link_path):
                # Resolve the symlink.
                java = os.path.realpath(link_path)

        if not java:
            java_home = os.environ.get('JAVA_HOME')
            if java_home:
                logger.debug("Could not detemine Java home using standard "
                             "means, trying JAVA_HOME: {}".format(java_home))
                if os.path.isdir(java_home):
                    java = os.path.join(java_home, 'bin', 'java')

        if not java:
            logger.debug("Could not detemine java executable using standard "
                         "means, trying system path")
            java = shutil.which('java')

        return java
