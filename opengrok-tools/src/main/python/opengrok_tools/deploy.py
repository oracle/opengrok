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

import argparse
import logging
import os
import tempfile
from zipfile import ZipFile
from shutil import copyfile
from .utils.log import get_console_logger

"""
 deploy war file

"""


def repack_war(logger, sourceWar, targetWar, configFile, defaultConfigFile):
    """
    Repack sourceWar into targetWar, performing substitution of configFile
    in the process.
    """

    WEB_XML = 'WEB-INF/web.xml'

    with ZipFile(sourceWar, 'r') as infile, ZipFile(targetWar, 'w') as outfile:
        for item in infile.infolist():
            data = infile.read(item.filename)
            if item.filename == WEB_XML:
                logger.debug("Performing substitution of '{}' with '{}'".
                             format(defaultConfigFile, configFile))
                defaultConfigFile = defaultConfigFile.encode()
                configFile = configFile.encode()
                data = data.replace(defaultConfigFile, configFile)
            outfile.writestr(item, data)


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


def main():
    parser = argparse.ArgumentParser(description='Deploy WAR file')

    parser.add_argument('-D', '--debug', action='store_true',
                        help='Enable debug prints')
    parser.add_argument('-c', '--config',
                        help='Path to OpenGrok configuration file')
    parser.add_argument('source_war', nargs=1,
                        help='Path to war file to deploy')
    parser.add_argument('target_war', nargs=1,
                        help='Path where to deploy source war file to')

    args = parser.parse_args()

    loglevel = logging.INFO
    if args.debug:
        loglevel = logging.DEBUG
    logger = get_console_logger(__name__, loglevel)

    deploy_war(logger, args.source_war[0], args.target_war[0], args.config)

    print("Start your application server (if it is not already running) "
          "or wait until it loads the just installed web application.\n"
          "OpenGrok should be available on <HOST>:<PORT>/{APP_CONTEXT}")


if __name__ == '__main__':
    main()
