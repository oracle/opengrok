#!/usr/bin/env python3

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
# Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
#

import os
import pytest
import platform

import xml.etree.ElementTree as ET

from opengrok_tools.utils.xml import insert_file, XMLProcessingException

DIR_PATH = os.path.dirname(os.path.realpath(__file__))


@pytest.mark.skipif(platform.system() == 'Windows',
                    reason="broken on Windows")
@pytest.mark.skipif(not hasattr(ET, "canonicalize"),
                    reason="need ElementTree with canonicalize()")
def test_xml_insert():
    with open(os.path.join(DIR_PATH, "web.xml")) as base_xml:
        out = insert_file(base_xml.read(),
                          os.path.join(DIR_PATH, "insert.xml"))
        with open(os.path.join(DIR_PATH, "new.xml")) as expected_xml_fp:
            out_xml_canonical = ET.canonicalize(out, strip_text=True)
            expected_xml_canonical = ET.canonicalize(from_file=expected_xml_fp,
                                                     strip_text=True)

            assert out_xml_canonical == expected_xml_canonical


def test_invalid_xml():
    with open(os.path.join(DIR_PATH, "web.xml")) as base_xml:
        with pytest.raises(XMLProcessingException):
            insert_file(base_xml.read(),
                        os.path.join(DIR_PATH, "invalid.xml"))
