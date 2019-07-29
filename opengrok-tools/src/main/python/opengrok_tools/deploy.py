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
import xml.etree.ElementTree as ET

from .utils.log import get_console_logger, get_class_basename, \
    fatal
from .utils.parsers import get_baseparser

WEB_XML = 'WEB-INF/web.xml'

"""
 deploy war file

"""


class ProcessingException(Exception):
    pass


def insert_file(input_xml, insert_xml_file):
    """
    inserts sub-root elements of XML file under root of input XML
    :param input_xml: input XML string
    :param insert_xml_file: path to file to insert
    :return: string with resulting XML
    """
    # This avoids resulting XML to have namespace prefixes in elements.
    ET.register_namespace('', "http://xmlns.jcp.org/xml/ns/javaee")

    root = ET.fromstring(input_xml)
    insert_tree = ET.parse(insert_xml_file)
    insert_root = insert_tree.getroot()
    index = len(root)

    for elem in list(insert_root.findall('.')):
        for e in list(elem):
            root.insert(index, e)
            index = index + 1

    return '<?xml version="1.0" encoding="UTF-8"?>\n' + \
           ET.tostring(root, encoding="unicode")


def repack_war(logger, sourceWar, targetWar, defaultConfigFile,
               configFile=None, insert_path=None):
    """
    Repack sourceWar into targetWar, performing substitution of configFile
    and/or inserting XML snippet to the 'web.xml' file in the process.

    :param logger: logger object
    :param sourceWar: path to the original WAR file
    :param targetWar: path to the destination WAR file
    :param defaultConfigFile: path to default configuration file
    :param configFile: path to new configuration file
    :param insert_path: path to XML file to insert
    """

    with ZipFile(sourceWar, 'r') as infile, ZipFile(targetWar, 'w') as outfile:
        for item in infile.infolist():
            data = infile.read(item.filename)

            if item.filename == WEB_XML:
                if configFile:
                    logger.debug("Performing substitution of '{}' with '{}'".
                                 format(defaultConfigFile, configFile))
                    defaultConfigFile = defaultConfigFile.encode()
                    configFile = configFile.encode()
                    data = data.replace(defaultConfigFile, configFile)

                if insert_path:
                    logger.debug("Inserting contents of file '{}'".
                                 format(insert_path))
                    try:
                        data = insert_file(data, insert_path)
                    except ET.ParseError as e:
                        raise ProcessingException(
                            "Cannot parse file '{}' as XML".
                            format(insert_path)) from e
                    except (PermissionError, IOError) as e:
                        raise ProcessingException("Cannot read file '{}'".
                                                  format(insert_path)) from e

            outfile.writestr(item, data)


def deploy_war(logger, sourceWar, targetWar, configFile=None,
               insert_file=None):
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
    if (configFile and configFile != DEFAULT_CONFIG_FILE) or insert_file:

        # Resolve the path to be absolute so that webapp can find the file.
        if configFile:
            configFile = os.path.abspath(configFile)

        with tempfile.NamedTemporaryFile(prefix='OpenGroktmpWar',
                                         suffix='.war',
                                         delete=False) as tmpWar:
            logger.info('Repacking {} with custom configuration path to {}'.
                        format(sourceWar, tmpWar.name))
            repack_war(logger, sourceWar, tmpWar.name, DEFAULT_CONFIG_FILE,
                       configFile, insert_file)
            sourceWar = tmpWar.name

    logger.info("Installing {} to {}".format(sourceWar, targetWar))
    copyfile(sourceWar, targetWar)

    if tmpWar:
        os.remove(tmpWar.name)


def main():
    parser = argparse.ArgumentParser(description='Deploy WAR file',
                                     parents=[get_baseparser()])

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
    except ProcessingException as e:
        fatal(e)

    print("Start your application server (if it is not already running) "
          "or wait until it loads the just installed web application.\n"
          "OpenGrok should be available on <HOST>:<PORT>/{APP_CONTEXT}")


if __name__ == '__main__':
    main()
