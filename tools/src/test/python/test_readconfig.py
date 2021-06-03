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
# Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
#
import os
import tempfile
from mockito import mock
import logging

from opengrok_tools.utils.readconfig import read_config


def test_read_config_empty_yaml():
    with tempfile.NamedTemporaryFile() as tmpf:
        tmpf.file.write(b'#foo\n')
        tmpf.flush()
        res = read_config(mock(spec=logging.Logger), tmpf.name)
        assert res is not None
        assert type(res) == dict
        assert len(res) == 0
