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
# Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
#

import os
import pytest
import platform

from opengrok_tools.utils.xml import insert_file, XMLProcessingException

DIR_PATH = os.path.dirname(os.path.realpath(__file__))


@pytest.mark.skipif(platform.system() == 'Windows',
                    reason="broken on Windows")
def test_xml_insert():
    with open(os.path.join(DIR_PATH, "web.xml")) as base_xml:
        out = insert_file(base_xml.read(),
                          os.path.join(DIR_PATH, "insert.xml"))
        with open(os.path.join(DIR_PATH, "new.xml")) as expected_xml:
            # TODO: this should really be comparing XML trees
            assert out.strip() == expected_xml.read().strip()


def test_invalid_xml():
    with open(os.path.join(DIR_PATH, "web.xml")) as base_xml:
        with pytest.raises(XMLProcessingException):
            insert_file(base_xml.read(),
                        os.path.join(DIR_PATH, "invalid.xml"))
