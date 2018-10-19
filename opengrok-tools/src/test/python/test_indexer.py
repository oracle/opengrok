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
# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
#

import unittest
import sys
import os


sys.path.insert(0, os.path.abspath(
                os.path.join(os.path.dirname(__file__), '..', '..',
                'main', 'python', 'opengrok_tools')))

from opengrok_tools.utils.indexer import merge_properties


class TestApp(unittest.TestCase):
    def test_merge_properties(self):
        merged = merge_properties(['foo', '-Dfoo=1'],
                                  ['-Dfoo=2', '-Dbar=bar'])
        self.assertEqual(['-Dbar=bar', '-Dfoo=1', 'foo'], sorted(merged))


if __name__ == '__main__':
    unittest.main()
