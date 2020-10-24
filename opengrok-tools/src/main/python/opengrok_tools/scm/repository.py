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
# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
# Portions Copyright (c) 2020, Krystof Tulinger <k.tulinger@seznam.cz>
#

import abc

from ..utils.command import Command


class RepositoryException(Exception):
    """
    Exception returned when repository operation failed.
    """
    pass


class Repository:
    """
    abstract class wrapper for Source Code Management repository
    """

    __metaclass__ = abc.ABCMeta

    SYNC_COMMAND_SECTION = 'sync'
    INCOMING_COMMAND_SECTION = 'incoming'

    def __init__(self, logger, path, project, configured_commands, env, hooks,
                 timeout):
        self.logger = logger
        self.path = path
        self.project = project
        self.timeout = timeout
        self.configured_commands = configured_commands
        if env:
            self.env = env
        else:
            self.env = {}

    def __str__(self):
        return self.path

    def getCommand(self, cmd, **kwargs):
        kwargs['timeout'] = self.timeout
        return Command(cmd, **kwargs)

    def sync(self):
        # Eventually, there might be per-repository hooks added here.
        if self.is_command_overridden(self.configured_commands, self.SYNC_COMMAND_SECTION):
            return self._run_custom_sync_command(
                self.listify(self.configured_commands[self.SYNC_COMMAND_SECTION])
            )
        return self.reposync()

    @abc.abstractmethod
    def reposync(self):
        """
        Synchronize the repository by running sync command specific for
        given repository type.

        Return 1 on failure, 0 on success.
        """
        raise NotImplementedError()

    def incoming(self):
        """
        Check if there are any incoming changes.

        Return True if so, False otherwise.
        """
        if self.is_command_overridden(self.configured_commands, self.INCOMING_COMMAND_SECTION):
            return self._run_custom_incoming_command(
                self.listify(self.configured_commands[self.INCOMING_COMMAND_SECTION])
            )
        return self.incoming_check()

    def incoming_check(self):
        """
        Check if there are any incoming changes.

        Return True if so, False otherwise.
        """
        return True

    def _run_custom_sync_command(self, command):
        """
        Execute the custom sync command.

        :param command: the command
        :return: 0 on success execution, 1 otherwise
        """
        status, output = self._run_command(command)
        log_handler = self.logger.info if status == 0 else self.logger.warning
        log_handler("output of '{}':".format(command))
        log_handler(output)
        return status

    def _run_custom_incoming_command(self, command):
        """
        Execute the custom incoming command.

        :param command: the command
        :return: true when there are changes, false otherwise
        """
        status, output = self._run_command(command)
        if status != 0:
            self.logger.error("output of '{}':".format(command))
            self.logger.error(output)
            raise RepositoryException('failed to check for incoming in repository {}'.format(self))
        return len(output.strip()) > 0

    def _run_command(self, command):
        """
        Execute the command.

        :param command: the command
        :return: tuple of (status, output)
                    - status: 0 on success execution, non-zero otherwise
                    - output: command output as string
        """
        cmd = self.getCommand(command, work_dir=self.path,
                              env_vars=self.env, logger=self.logger)
        cmd.execute()
        if cmd.getretcode() != 0 or cmd.getstate() != Command.FINISHED:
            cmd.log_error("failed to perform command")
            status = cmd.getretcode()
            if status == 0 and cmd.getstate() != Command.FINISHED:
                status = 1
            return status, '\n'.join(filter(None, [
                cmd.getoutputstr(),
                cmd.geterroutputstr()
            ]))
        return 0, cmd.getoutputstr()

    @staticmethod
    def _repository_command(configured_commands, default=lambda: None):
        """
        Get the repository command, or use default supplier.

        :param configured_commands: commands section from configuration
                                    for this repository type
        :param default: the supplier of default command
        :return: the repository command
        """
        if isinstance(configured_commands, str):
            return configured_commands
        elif isinstance(configured_commands, dict) and \
                configured_commands.get('command'):
            return configured_commands['command']

        return default()

    @staticmethod
    def listify(object):
        if isinstance(object, list) or isinstance(object, tuple):
            return object
        return [object]

    @staticmethod
    def is_command_overridden(config, command):
        """
        Determine if command key is overridden in the configuration.

        :param config: configuration
        :param command: the command
        :return: true if overridden, false otherwise
        """
        return isinstance(config, dict) and config.get(command) is not None
