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
# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
#

import xml.etree.ElementTree as ET


class XMLProcessingException(Exception):
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
    try:
        insert_tree = ET.parse(insert_xml_file)
    except ET.ParseError as e:
        raise XMLProcessingException("Cannot parse file '{}' as XML".
                                     format(insert_xml_file)) from e
    except (PermissionError, IOError) as e:
        raise XMLProcessingException("Cannot read file '{}'".
                                     format(insert_xml_file)) from e

    insert_root = insert_tree.getroot()

    for elem in list(insert_root.findall('.')):
        root.extend(list(elem))

    return ET.tostring(root, encoding="utf8", method='xml').decode("utf-8")
