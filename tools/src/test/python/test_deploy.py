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
# Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
#

import filecmp
import logging
import os
import tempfile

from opengrok_tools.deploy import deploy_war


def test_deploy_dirs():
    logger = logging.getLogger(__name__)

    with tempfile.NamedTemporaryFile(suffix=".war", delete=False) as source_war_fp:
        source_war_fp.write(b"foo")
        source_war = source_war_fp.name

    with tempfile.TemporaryDirectory() as tmp_dir:
        assert os.path.isfile(source_war)
        target_war = os.path.join(tmp_dir, "foo", "bar", "my.war")
        target_dir = os.path.dirname(target_war)
        deploy_war(logger, source_war, target_war)
        assert os.path.isdir(target_dir)
        assert os.path.isfile(target_war)
        assert filecmp.cmp(source_war, target_war)

    os.unlink(source_war)
