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
# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
#

"""
    This script is wrapper of commands to add/remove project or refresh
    configuration using read-only configuration.
"""

import argparse
import io
import shutil
import sys
import tempfile
from os import path
from hashlib import sha1

from filelock import Timeout, FileLock

from .utils.command import Command
from .utils.log import get_console_logger, get_class_basename, \
    fatal
from .utils.opengrok import get_configuration, set_configuration, \
    add_project, delete_project, get_config_value, get_repos
from .utils.parsers import get_base_parser, get_headers, add_http_headers
from .utils.utils import get_command
from .utils.webutil import is_web_uri
from .utils.exitvals import (
    FAILURE_EXITVAL,
    SUCCESS_EXITVAL
)

MAJOR_VERSION = sys.version_info[0]
if (MAJOR_VERSION < 3):
    print("Need Python 3, you are running {}".format(MAJOR_VERSION))
    sys.exit(1)

__version__ = "0.7"


def exec_command(doit, logger, cmd, msg):
    """
    Execute given command and return its output.
    Exit the program on failure.
    """
    cmd = Command(cmd, logger=logger, redirect_stderr=False)
    if not doit:
        logger.info(cmd)
        return
    cmd.execute()
    if cmd.getstate() != Command.FINISHED \
            or cmd.getretcode() != SUCCESS_EXITVAL:
        logger.error(msg)
        logger.error("Standard output: {}".format(cmd.getoutput()))
        logger.error("Error output: {}".format(cmd.geterroutput()))
        sys.exit(FAILURE_EXITVAL)

    logger.debug(cmd.geterroutputstr())

    return cmd.getoutput()


def get_config_file(basedir):
    """
    Return configuration file in basedir
    """

    return path.join(basedir, "etc", "configuration.xml")


def install_config(doit, logger, src, dst):
    """
    Copy the data of src to dst. Exit on failure.
    """
    if not doit:
        logger.debug("Not copying {} to {}".format(src, dst))
        return

    #
    # Copy the file so that close() triggered unlink()
    # does not fail.
    #
    logger.debug("Copying {} to {}".format(src, dst))
    try:
        shutil.copyfile(src, dst)
    except PermissionError:
        logger.error('Failed to copy {} to {} (permissions)'.
                     format(src, dst))
        sys.exit(FAILURE_EXITVAL)
    except OSError:
        logger.error('Failed to copy {} to {} (I/O)'.
                     format(src, dst))
        sys.exit(FAILURE_EXITVAL)


def config_refresh(doit, logger, basedir, uri, configmerge, jar_file,
                   roconfig, java, headers=None, timeout=None):
    """
    Refresh current configuration file with configuration retrieved
    from webapp. If roconfig is not None, the current config is merged with
    readonly configuration first.

    The merge of the current config from the webapp with the read-only config
    is done as a workaround for https://github.com/oracle/opengrok/issues/2002
    """

    main_config = get_config_file(basedir)
    if not path.isfile(main_config):
        logger.error("file {} does not exist".format(main_config))
        sys.exit(FAILURE_EXITVAL)

    if doit:
        current_config = get_configuration(logger, uri,
                                           headers=headers, timeout=timeout)
        if not current_config:
            sys.exit(FAILURE_EXITVAL)
    else:
        current_config = None

    with tempfile.NamedTemporaryFile() as fcur:
        logger.debug("Temporary file for current config: {}".format(fcur.name))
        if doit:
            fcur.write(bytearray(''.join(current_config), "UTF-8"))
            fcur.flush()

        if not roconfig:
            logger.info('Refreshing configuration')
            install_config(doit, logger, fcur.name, main_config)
        else:
            logger.info('Refreshing configuration '
                        '(merging with read-only config)')
            with tempfile.NamedTemporaryFile() as fmerged:
                logger.debug("Temporary file for merged config: {}".
                             format(fmerged.name))
                configmerge_cmd = configmerge
                configmerge_cmd.extend(['-a', jar_file, roconfig, fcur.name, fmerged.name])
                if java:
                    configmerge_cmd.append('-j')
                    configmerge_cmd.append(java)
                exec_command(doit, logger,
                             configmerge_cmd,
                             "cannot merge configuration")
                if doit:
                    install_config(doit, logger, fmerged.name, main_config)


def project_add(doit, logger, project, uri, headers=None, timeout=None, api_timeout=None):
    """
    Adds a project to configuration.
    """

    logger.info("Adding project {}".format(project))

    if doit:
        if add_project(logger, project, uri, headers=headers, timeout=timeout,
                       api_timeout=api_timeout):
            repos = get_repos(logger, project, uri,
                              headers=headers, timeout=timeout)
            if repos:
                logger.info("Added project {} with repositories: {}".
                            format(project, repos))


def project_delete(logger, project, uri, doit=True, deletesource=False,
                   headers=None, timeout=None, api_timeout=None):
    """
    Delete the project for configuration and all its data.
    Works in multiple steps:

      1. delete the project from configuration and its indexed data
      2. refresh on disk configuration
      3. delete the source code for the project
    """

    # Be extra careful as we will be recursively removing directory structure.
    if not project or len(project) == 0:
        raise Exception("invalid call to project_delete(): missing project")

    logger.info("Deleting project {} and its index data".format(project))

    if doit:
        delete_project(logger, project, uri, headers=headers, timeout=timeout,
                       api_timeout=api_timeout)

    if deletesource:
        src_root = get_config_value(logger, 'sourceRoot', uri, headers=headers,
                                    timeout=timeout)
        if not src_root or len(src_root) == 0:
            raise Exception("source root empty")
        logger.debug("Source root = {}".format(src_root))
        sourcedir = path.join(src_root, project)
        logger.debug("Removing directory tree {}".format(sourcedir))
        if doit:
            logger.info("Removing source code under {}".format(sourcedir))
            try:
                shutil.rmtree(sourcedir)
            except FileNotFoundError:
                pass
            except Exception as e:
                logger.error("Failed to remove {}: {}".format(sourcedir, e))


def get_lock_file(args):
    project_list = list()
    if args.add is not None:
        project_list.extend(args.add)
    if args.delete is not None:
        project_list.extend(args.delete)
    if len(project_list) == 0:
        name = "refresh"
    else:
        name = sha1("".join(project_list).encode()).hexdigest()

    return path.join(tempfile.gettempdir(),
                     path.basename(sys.argv[0]) + "-" + name + ".lock")


def main():
    parser = argparse.ArgumentParser(description='project management.',
                                     formatter_class=argparse.
                                     ArgumentDefaultsHelpFormatter,
                                     parents=[get_base_parser(
                                         tool_version=__version__)
                                     ])

    parser.add_argument('-b', '--base', default="/var/opengrok",
                        help='OpenGrok instance base directory')
    parser.add_argument('-R', '--roconfig',
                        help='OpenGrok read-only configuration file')
    parser.add_argument('-U', '--uri', default='http://localhost:8080/source',
                        help='URI of the webapp with context path')
    parser.add_argument('-c', '--configmerge',
                        help='path to the ConfigMerge program')
    parser.add_argument('--java', help='Path to java binary '
                                       '(needed for config merge program)')
    parser.add_argument('-j', '--jar', help='Path to jar archive to run')
    parser.add_argument('-u', '--upload', action='store_true',
                        help='Upload configuration at the end')
    parser.add_argument('-n', '--noop', action='store_true', default=False,
                        help='Do not run any commands or modify any config'
                             ', just report. Usually implies '
                             'the --debug option.')
    parser.add_argument('-N', '--nosourcedelete', action='store_true',
                        default=False, help='Do not delete source code when '
                                            'deleting a project')
    add_http_headers(parser)
    parser.add_argument('--api_timeout', type=int, default=3,
                        help='Set response timeout in seconds for RESTful API calls')
    parser.add_argument('--async_api_timeout', type=int,
                        help='Set timeout in seconds for asynchronous RESTful API calls')

    group = parser.add_mutually_exclusive_group()
    group.add_argument('-a', '--add', metavar='project', nargs='+',
                       help='Add project (assumes its source is available '
                            'under source root')
    group.add_argument('-d', '--delete', metavar='project', nargs='+',
                       help='Delete project and its data and source code')
    group.add_argument('-r', '--refresh', action='store_true',
                       help='Refresh configuration. If read-only '
                            'configuration is supplied, it is merged '
                            'with current '
                            'configuration.')

    try:
        args = parser.parse_args()
    except ValueError as e:
        fatal(e)

    doit = not args.noop
    configmerge = None

    #
    # Setup logger as a first thing after parsing arguments so that it can be
    # used through the rest of the program.
    #
    logger = get_console_logger(get_class_basename(), args.loglevel)

    headers = get_headers(args.header)

    if args.nosourcedelete and not args.delete:
        logger.error("The no source delete option is only valid for delete")
        sys.exit(FAILURE_EXITVAL)

    # Set the base directory
    if args.base:
        if path.isdir(args.base):
            logger.debug("Using {} as instance base".
                         format(args.base))
        else:
            logger.error("Not a directory: {}\n"
                         "Set the base directory with the --base option."
                         .format(args.base))
            sys.exit(FAILURE_EXITVAL)

    # If read-only configuration file is specified, this means read-only
    # configuration will need to be merged with active webapp configuration.
    # This requires config merge tool to be run so a couple of other things
    # need to be checked.
    if args.roconfig:
        if path.isfile(args.roconfig):
            logger.debug("Using {} as read-only config".format(args.roconfig))
        else:
            logger.error("File {} does not exist".format(args.roconfig))
            sys.exit(FAILURE_EXITVAL)

        configmerge_file = get_command(logger, args.configmerge,
                                       "opengrok-config-merge")
        if configmerge_file is None:
            logger.error("Use the --configmerge option to specify the path to"
                         "the config merge script")
            sys.exit(FAILURE_EXITVAL)

        configmerge = [configmerge_file]
        if args.loglevel:
            configmerge.append('-l')
            configmerge.append(str(args.loglevel))

        if args.jar is None:
            logger.error('jar file needed for config merge tool, '
                         'use --jar to specify one')
            sys.exit(FAILURE_EXITVAL)

    uri = args.uri
    if not is_web_uri(uri):
        logger.error("Not a URI: {}".format(uri))
        sys.exit(FAILURE_EXITVAL)
    logger.debug("web application URI = {}".format(uri))

    lock = FileLock(get_lock_file(args))
    try:
        with lock.acquire(timeout=0):
            if args.add:
                for proj in args.add:
                    project_add(doit=doit, logger=logger,
                                project=proj,
                                uri=uri, headers=headers,
                                timeout=args.api_timeout,
                                api_timeout=args.async_api_timeout)

                config_refresh(doit=doit, logger=logger,
                               basedir=args.base,
                               uri=uri,
                               configmerge=configmerge,
                               jar_file=args.jar,
                               roconfig=args.roconfig,
                               java=args.java,
                               headers=headers,
                               timeout=args.api_timeout)
            elif args.delete:
                for proj in args.delete:
                    project_delete(logger=logger,
                                   project=proj,
                                   uri=uri, doit=doit,
                                   deletesource=not args.nosourcedelete,
                                   headers=headers,
                                   timeout=args.api_timeout,
                                   api_timeout=args.async_api_timeout)

                config_refresh(doit=doit, logger=logger,
                               basedir=args.base,
                               uri=uri,
                               configmerge=configmerge,
                               jar_file=args.jar,
                               roconfig=args.roconfig,
                               java=args.java,
                               headers=headers,
                               timeout=args.api_timeout)
            elif args.refresh:
                config_refresh(doit=doit, logger=logger,
                               basedir=args.base,
                               uri=uri,
                               configmerge=configmerge,
                               jar_file=args.jar,
                               roconfig=args.roconfig,
                               java=args.java,
                               headers=headers,
                               timeout=args.api_timeout)
            else:
                parser.print_help()
                sys.exit(FAILURE_EXITVAL)

            if args.upload:
                main_config = get_config_file(basedir=args.base)
                if path.isfile(main_config):
                    if doit:
                        with io.open(main_config, mode='r',
                                     encoding="utf-8") as config_file:
                            config_data = config_file.read().encode("utf-8")
                            if not set_configuration(logger,
                                                     config_data, uri,
                                                     headers=headers,
                                                     timeout=args.api_timeout,
                                                     api_timeout=args.async_api_timeout):
                                sys.exit(FAILURE_EXITVAL)
                else:
                    logger.error("file {} does not exist".format(main_config))
                    sys.exit(FAILURE_EXITVAL)
    except Timeout:
        logger.warning("Already running, exiting.")
        sys.exit(FAILURE_EXITVAL)


if __name__ == '__main__':
    main()
