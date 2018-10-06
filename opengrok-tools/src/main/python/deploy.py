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
# Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
# Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
#

import os
import sys
import argparse
from utils import get_command
from command import Command
import tempfile
from shutil import copyfile
import logging


"""
 deploy war file

"""


def repack_war(logger, sourceWar, targetWar, configFile, defaultConfigFile):
    """
    Repack sourceWar into targetWar, performing substitution of configFile
    in the process.
    """

    jar_cmd = get_command(logger, None, 'jar')
    if jar_cmd:
        extract_cmd = [jar_cmd, '-xf']
        compress_cmd = [jar_cmd, '-uf']
    else:
        zip_cmd = get_command(logger, None, 'zip')
        unzip_cmd = get_command(logger, None, 'unzip')
        if not zip_cmd:
            raise Exception("zip not found")
        if not unzip_cmd:
            raise Exception("unzip not found")

        extract_cmd = [unzip_cmd]
        compress_cmd = [zip_cmd, '-rf']

    # Resolve the full path. This is needed if sourceWar is specified as
    # relative path because the process will switch working directory below.
    sourceWar = os.path.realpath(sourceWar)
    logger.debug('source war path = {}'.format(sourceWar))

    # Create temporary directory and switch to it.
    with tempfile.TemporaryDirectory(prefix='OpenGrokWarRepack') as tmpDirName:
        logger.debug('Changing working directory to {}'.format(tmpDirName))
        origWorkingDir = os.getcwd()
        os.chdir(tmpDirName)

        # Extract the web.xml file from the source archive.
        WEB_INF = 'WEB-INF'
        WEB_XML = os.path.join(WEB_INF, 'web.xml')
        logger.debug('Decompressing {} from {} into {}'.
                     format(WEB_XML, sourceWar, tmpDirName))
        extract_cmd.append(sourceWar)
        extract_cmd.append(WEB_XML)
        cmd = Command(extract_cmd, logger=logger)
        cmd.execute()
        ret = cmd.getretcode()
        if ret is None or ret != 0:
            raise Exception("Cannot decompress war file {}, command '{}' "
                            "ended with {}".format(sourceWar, cmd, ret))

        # Substitute the configuration path in the web.xml file.
        logger.debug("Performing substitution of '{}' with '{}'".
                     format(defaultConfigFile, configFile))
        with open(WEB_XML, 'r') as f:
            data = f.read().replace(defaultConfigFile, configFile)
        with open(WEB_XML, 'w') as f:
            f.write(data)

        # Refresh the target archive with the modified file.
        logger.debug('Copying {} to {}'.format(sourceWar, targetWar))
        copyfile(sourceWar, targetWar)
        logger.debug('Refreshing target archive {}'.format(targetWar))
        compress_cmd.append(targetWar)
        compress_cmd.append(WEB_XML)
        cmd = Command(compress_cmd, logger=logger)
        cmd.execute()
        ret = cmd.getretcode()
        if ret is None or ret != 0:
            raise Exception("Cannot re-compress war file {}, command '{}' "
                            "ended with {}".format(targetWar, cmd, ret))

        # Need to switch back to original working directory, so that the
        # temporary directory can be deleted.
        os.chdir(origWorkingDir)


def deploy_war(logger, sourceWar, targetWar, configFile=None):
    """
    Copy warSource to warTarget (checking existence of both), optionally
    repacking the warTarget archive if configuration file resides in
    non-default location.
    """

    if not os.path.isfile(sourceWar):
        logger.error("{} is not a file".format(sourceWar))

    if os.path.isdir(targetWar):
        orig = targetWar
        targetWar = os.path.join(targetWar, os.path.basename(sourceWar))
        logger.debug("Target {} is directory, will use {}".
                     format(orig, targetWar))

    # If user does not use default configuration file location then attempt to
    # extract WEB-INF/web.xml from the war file using jar or zip utility,
    # update the hardcoded values and then update source.war with the new
    # WEB-INF/web.xml.
    tmpWar = None
    DEFAULT_CONFIG_FILE = '/var/opengrok/etc/configuration.xml'
    if configFile and configFile != DEFAULT_CONFIG_FILE:
        with tempfile.NamedTemporaryFile(prefix='OpenGroktmpWar',
                                         suffix='.war',
                                         delete=False) as tmpWar:
            logger.info('Repacking {} with custom configuration path to {}'.
                        format(sourceWar, tmpWar.name))
            repack_war(logger, sourceWar, tmpWar.name, configFile,
                       DEFAULT_CONFIG_FILE)
            sourceWar = tmpWar.name

    logger.info("Installing {} to {}".format(sourceWar, targetWar))
    copyfile(sourceWar, targetWar)

    if tmpWar:
        os.remove(tmpWar.name)


if __name__ == '__main__':

    parser = argparse.ArgumentParser(description='Manage parallel workers.')

    parser.add_argument('-D', '--debug', action='store_true',
                        help='Enable debug prints')
    parser.add_argument('-c', '--config',
                        help='Path to OpenGrok configuration file')
    parser.add_argument('source_war', nargs=1,
                        help='Path to war file to deploy')
    parser.add_argument('target_war', nargs=1,
                        help='Path where to deploy source war file to')

    args = parser.parse_args()

    if args.debug:
        logging.basicConfig(level=logging.DEBUG)
    else:
        logging.basicConfig()

    logger = logging.getLogger(os.path.basename(sys.argv[0]))

    deploy_war(logger, args.source_war[0], args.target_war[0], args.config)

    print("Start your application server (if it is not already running) "
          "or wait until it loads the just installed web application.\n"
          "OpenGrok should be available on <HOST>:<PORT>/{APP_CONTEXT}")
