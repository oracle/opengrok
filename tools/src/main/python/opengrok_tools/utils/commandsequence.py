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
# Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
#

import logging

from requests.exceptions import RequestException

from .command import Command
from .exitvals import (
    CONTINUE_EXITVAL,
    SUCCESS_EXITVAL,
    FAILURE_EXITVAL
)
from .restful import call_rest_api
from .patterns import PROJECT_SUBST, COMMAND_PROPERTY, CALL_PROPERTY, URL_SUBST
import re


API_TIMEOUT_PROPERTY = "api_timeout"
ASYNC_API_TIMEOUT_PROPERTY = "async_api_timeout"
HEADERS_PROPERTY = "headers"
METHOD_PROPERTY = "method"
URI_PROPERTY = "uri"


class CommandConfigurationException(Exception):
    pass


def check_call_config(call):
    """
    :param call: dictionary with API call configuration
    """
    if not isinstance(call, dict):
        raise CommandConfigurationException("call value not a dictionary: {}".
                                            format(call))

    uri = call.get(URI_PROPERTY)
    if not uri:
        raise CommandConfigurationException(f"no '{URI_PROPERTY}' key present in {call}")

    method = call.get(METHOD_PROPERTY)
    if method and method.upper() not in ['GET', 'POST', 'PUT', 'DELETE']:
        raise CommandConfigurationException(f"invalid HTTP method: {method}")

    headers = call.get(HEADERS_PROPERTY)
    if headers and not isinstance(headers, dict):
        raise CommandConfigurationException("headers must be a dictionary")

    call_timeout = call.get(API_TIMEOUT_PROPERTY)
    if call_timeout:
        try:
            int(call_timeout)
        except ValueError as exc:
            raise CommandConfigurationException(f"{API_TIMEOUT_PROPERTY} not an integer", exc)

    call_api_timeout = call.get(ASYNC_API_TIMEOUT_PROPERTY)
    if call_api_timeout:
        try:
            int(call_api_timeout)
        except ValueError as exc:
            raise CommandConfigurationException(f"{ASYNC_API_TIMEOUT_PROPERTY} not an integer", exc)


def check_command_property(command):
    """
    Check if the 'commands' parameter of CommandSequenceBase() has the right structure
    w.r.t. individual commands.
    :param command: command element
    """

    if not isinstance(command, dict):
        raise CommandConfigurationException("command '{}' is not a dictionary".format(command))

    command_value = command.get(COMMAND_PROPERTY)
    call_value = command.get(CALL_PROPERTY)
    if command_value is None and call_value is None:
        raise CommandConfigurationException(f"command dictionary has unknown key: {command}")

    if command_value and not isinstance(command_value, list):
        raise CommandConfigurationException("command value not a list: {}".
                                            format(command_value))
    if call_value:
        check_call_config(call_value)


class ApiCall:
    """
    Container class to store properties of API call.
    """
    def __init__(self, call_dict):
        """
        Initialize the object from a dictionary.
        :param call_dict: dictionary
        """
        if not isinstance(call_dict, dict):
            raise CommandConfigurationException(f"not a dictionary: {call_dict}")

        self.uri = call_dict.get(URI_PROPERTY)
        self.method = call_dict.get(METHOD_PROPERTY)
        if not self.method:
            self.method = "GET"

        self.data = call_dict.get("data")

        self.headers = call_dict.get(HEADERS_PROPERTY)
        if not self.headers:
            self.headers = {}

        self.api_timeout = None
        call_timeout = call_dict.get(API_TIMEOUT_PROPERTY)
        if call_timeout:
            self.api_timeout = call_timeout

        self.async_api_timeout = None
        call_api_timeout = call_dict.get(ASYNC_API_TIMEOUT_PROPERTY)
        if call_api_timeout:
            self.async_api_timeout = call_api_timeout


class CommandSequenceBase:
    """
    Wrap the run of a set of Command instances.

    This class intentionally does not contain any logging
    so that it can be passed through Pool.map().
    """

    def __init__(self, name, commands, loglevel=logging.INFO, cleanup=None,
                 driveon=False, url=None, env=None, http_headers=None,
                 api_timeout=None, async_api_timeout=None, args_subst={}):
        self.name = name

        if commands is None:
            raise CommandConfigurationException("commands is None")
        if not isinstance(commands, list):
            raise CommandConfigurationException("commands is not a list")
        self.commands = commands
        for command in self.commands:
            check_command_property(command)

        self.failed = False
        self.retcodes = {}
        self.outputs = {}

        if cleanup and not isinstance(cleanup, list):
            raise CommandConfigurationException("cleanup is not a list of commands")
        self.cleanup = cleanup
        if self.cleanup:
            for command in self.cleanup:
                check_command_property(command)

        self.loglevel = loglevel
        self.driveon = driveon
        self.env = env
        self.http_headers = http_headers
        self.api_timeout = api_timeout
        self.async_api_timeout = async_api_timeout

        self.url = url

        self.args_subst = {PROJECT_SUBST: self.name,
                           URL_SUBST: self.url}
        self.args_subst.extend(args_subst)

    def __str__(self):
        return str(self.name)

    def get_cmd_output(self, cmd, indent=""):
        """
        :param cmd: command
        :param indent: prefix for each line
        :return: command output as string
        """

        str_out = ""
        for line in self.outputs.get(cmd, []):
            str_out += '{}{}'.format(indent, line)

        return str_out

    def fill(self, retcodes, outputs, failed):
        self.retcodes = retcodes
        self.outputs = outputs
        self.failed = failed


class CommandSequence(CommandSequenceBase):
    re_program = re.compile('ERROR[:]*\\s+')

    def __init__(self, base):
        super().__init__(base.name, base.commands, loglevel=base.loglevel,
                         cleanup=base.cleanup, driveon=base.driveon,
                         url=base.url, env=base.env,
                         http_headers=base.http_headers,
                         api_timeout=base.api_timeout,
                         async_api_timeout=base.async_api_timeout)

        self.logger = logging.getLogger(__name__)
        self.logger.setLevel(base.loglevel)

    def run_command(self, cmd):
        """
        Execute a command and return its return code.
        """
        cmd.execute()
        self.retcodes[str(cmd)] = cmd.getretcode()
        self.outputs[str(cmd)] = cmd.getoutput()

        return cmd.getretcode()

    def run(self):
        """
        Run the sequence of commands and capture their output and return code.
        First command that returns code other than 0 terminates the sequence.
        If the command has return code 2, the sequence will be terminated
        however it will not be treated as error (unless the 'driveon' parameter
        is True).

        If a command contains PROJECT_SUBST pattern, it will be replaced
        by project name, otherwise project name will be appended to the
        argument list of the command.

        Any command entry that is a URI, will be used to submit REST API
        request.
        """

        for command in self.commands:
            if command.get(CALL_PROPERTY):
                try:
                    call_rest_api(ApiCall(command.get(CALL_PROPERTY)),
                                  {PROJECT_SUBST: self.name,
                                   URL_SUBST: self.url},
                                  self.http_headers,
                                  self.api_timeout,
                                  self.async_api_timeout)
                except RequestException as e:
                    self.logger.error("REST API call {} failed: {}".
                                      format(command, e))
                    self.failed = True
                    self.retcodes[str(command)] = FAILURE_EXITVAL

                    break
            elif command.get(COMMAND_PROPERTY):
                command_args = command.get(COMMAND_PROPERTY)
                command = Command(command_args,
                                  env_vars=command.get("env"),
                                  logger=self.logger,
                                  resource_limits=command.get("limits"),
                                  args_subst=self.args_subst,
                                  args_append=[self.name], excl_subst=True)
                ret_code = self.run_command(command)

                # If a command exits with non-zero return code,
                # terminate the sequence of commands.
                if ret_code != SUCCESS_EXITVAL:
                    if ret_code == CONTINUE_EXITVAL:
                        if not self.driveon:
                            self.logger.debug("command '{}' for project {} "
                                              "requested break".
                                              format(command, self.name))
                            self.run_cleanup()
                        else:
                            self.logger.debug("command '{}' for project {} "
                                              "requested break however "
                                              "the 'driveon' option is set "
                                              "so driving on.".
                                              format(command, self.name))
                            continue
                    else:
                        self.logger.error("command '{}' for project {} failed "
                                          "with code {}, breaking".
                                          format(command, self.name, ret_code))
                        self.failed = True
                        self.run_cleanup()

                    break
            else:
                raise Exception(f"unknown command: {command}")

    def run_cleanup(self):
        """
        Call cleanup sequence in case the command sequence failed
        or termination was requested.
        """
        if self.cleanup is None:
            return

        for cleanup_cmd in self.cleanup:
            if cleanup_cmd.get(CALL_PROPERTY):
                try:
                    call_rest_api(ApiCall(cleanup_cmd.get(CALL_PROPERTY)),
                                  {PROJECT_SUBST: self.name,
                                   URL_SUBST: self.url},
                                  self.http_headers,
                                  self.api_timeout,
                                  self.async_api_timeout)
                except RequestException as e:
                    self.logger.error("API call {} failed: {}".
                                      format(cleanup_cmd, e))
            elif cleanup_cmd.get(COMMAND_PROPERTY):
                command_args = cleanup_cmd.get(COMMAND_PROPERTY)
                self.logger.debug("Running cleanup command '{}'".
                                  format(command_args))
                cmd = Command(command_args,
                              logger=self.logger,
                              args_subst={PROJECT_SUBST: self.name,
                                          URL_SUBST: self.url},
                              args_append=[self.name], excl_subst=True)
                cmd.execute()
                if cmd.getretcode() != SUCCESS_EXITVAL:
                    self.logger.error("cleanup command '{}' failed with "
                                      "code {}".
                                      format(cmd.cmd, cmd.getretcode()))
                    self.logger.info('output: {}'.format(cmd.getoutputstr()))
            else:
                raise Exception(f"unknown type of action: {cleanup_cmd}")

    def print_outputs(self, logger, loglevel=logging.INFO, lines=False):
        """
        Print command outputs.
        """

        logger.debug("Output for project '{}':".format(self.name))
        for cmd in self.outputs.keys():
            if self.outputs[cmd] and len(self.outputs[cmd]) > 0:
                if lines:
                    logger.log(loglevel, "Output from '{}':".format(cmd))
                    logger.log(loglevel, '{}'.format(self.get_cmd_output(cmd)))
                else:
                    logger.log(loglevel, "'{}': {}".
                               format(cmd, self.outputs[cmd]))

    def check(self, ignore_errors):
        """
        Check the output of the commands and perform logging.

        Return SUCCESS_EXITVAL on success, 1 if error was detected.
        """

        ret = SUCCESS_EXITVAL
        self.print_outputs(self.logger, loglevel=logging.DEBUG)

        if ignore_errors and self.name in ignore_errors:
            self.logger.debug("errors of project '{}' ignored".
                              format(self.name))
            return

        self.logger.debug("retcodes = {}".format(self.retcodes))
        if any(rv != SUCCESS_EXITVAL and rv != CONTINUE_EXITVAL
               for rv in self.retcodes.values()):
            ret = 1
            self.logger.error("processing of project '{}' failed".
                              format(self))
            indent = "  "
            self.logger.error("{}failed commands:".format(indent))
            failed_cmds = {k: v for k, v in
                           self.retcodes.items() if v != SUCCESS_EXITVAL}
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
                        if self.re_program.match(str(v))}
        if len(errored_cmds) > 0:
            ret = 1
            self.logger.error("Command output in project '{}'"
                              " contains errors:".format(self.name))
            indent = "  "
            for cmd in errored_cmds.keys():
                self.logger.error("{}{}".format(indent, cmd))
                out = self.get_cmd_output(cmd,
                                          indent=indent + "  ")
                if out:
                    self.logger.error(out)
                self.logger.error("")

        return ret
