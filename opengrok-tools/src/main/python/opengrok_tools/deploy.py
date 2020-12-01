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
# Copyright (c) 2008, 2019, Oracle and/or its affiliates. All rights reserved.
# Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
#

import argparse
import os
import tempfile
from shutil import copyfile
from zipfile import ZipFile

from .utils.log import get_console_logger, get_class_basename, \
    fatal
from .utils.parsers import get_base_parser
from .utils.xml import insert_file, XMLProcessingException

WEB_XML = 'WEB-INF/web.xml'
DEFAULT_CONFIG_FILE = '/var/opengrok/etc/configuration.xml'

"""
 deploy war file

"""


def repack_war(logger, source_war, target_war, default_config_file,
               config_file=None, insert_path=None):
    """
    Repack source_war into target_war, performing substitution of config_file
    and/or inserting XML snippet to the 'web.xml' file in the process.

    :param logger: logger object
    :param source_war: path to the original WAR file
    :param target_war: path to the destination WAR file
    :param default_config_file: path to default configuration file
    :param config_file: path to new configuration file
    :param insert_path: path to XML file to insert
    """

    with ZipFile(source_war, 'r') as infile, \
            ZipFile(target_war, 'w') as outfile:
        for item in infile.infolist():
            data = infile.read(item.filename)

            if item.filename == WEB_XML:
                if config_file:
                    logger.debug("Performing substitution of '{}' with '{}'".
                                 format(default_config_file, config_file))
                    default_config_file = default_config_file.encode()
                    config_file = config_file.encode()
                    data = data.replace(default_config_file, config_file)

                if insert_path:
                    logger.debug("Inserting contents of file '{}'".
                                 format(insert_path))
                    data = insert_file(data, insert_path)

            outfile.writestr(item, data)


def deploy_war(logger, source_war, target_war, config_file=None,
               insert_path=None):
    """
    Copy warSource to warTarget (checking existence of both), optionally
    repacking the warTarget archive if configuration file resides in
    non-default location.
    """

    if not os.path.isfile(source_war):
        logger.error("{} is not a file".format(source_war))

    if os.path.isdir(target_war):
        orig = target_war
        target_war = os.path.join(target_war, os.path.basename(source_war))
        logger.debug("Target {} is directory, will use {}".
                     format(orig, target_war))

    # If user does not use default configuration file location then attempt to
    # extract WEB-INF/web.xml from the war file using jar or zip utility,
    # update the hardcoded values and then update source.war with the new
    # WEB-INF/web.xml.
    tmp_war = None

    if (config_file and config_file != DEFAULT_CONFIG_FILE) or insert_path:

        # Resolve the path to be absolute so that webapp can find the file.
        if config_file:
            config_file = os.path.abspath(config_file)

        with tempfile.NamedTemporaryFile(prefix='OpenGroktmpWar',
                                         suffix='.war',
                                         delete=False) as tmp_war:
            logger.debug('Repacking {} with custom configuration path to {}'.
                         format(source_war, tmp_war.name))
            repack_war(logger, source_war, tmp_war.name, DEFAULT_CONFIG_FILE,
                       config_file, insert_path)
            source_war = tmp_war.name

    logger.debug("Installing {} to {}".format(source_war, target_war))
    copyfile(source_war, target_war)

    if tmp_war:
        os.remove(tmp_war.name)


def main():
    parser = argparse.ArgumentParser(description='Deploy WAR file',
                                     parents=[get_base_parser()])

    parser.add_argument('-c', '--config',
                        help='Path to OpenGrok configuration file')
    parser.add_argument('-i', '--insert',
                        help='Path to XML file to insert into the {} file'.
                        format(WEB_XML))
    parser.add_argument('source_war', nargs=1,
                        help='Path to war file to deploy')
    parser.add_argument('target_war', nargs=1,
                        help='Path where to deploy source war file to')

    try:
        args = parser.parse_args()
    except ValueError as e:
        fatal(e)

    logger = get_console_logger(get_class_basename(), args.loglevel)

    if args.insert and not os.path.isfile(args.insert):
        fatal("File '{}' does not exist".format(args.insert))

    try:
        deploy_war(logger, args.source_war[0], args.target_war[0], args.config,
                   args.insert)
    except XMLProcessingException as e:
        fatal(e)


if __name__ == '__main__':
    main()
