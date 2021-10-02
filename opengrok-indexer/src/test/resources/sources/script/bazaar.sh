#! /bin/ksh
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
# Dummy wrapper-script to set up the PYTHONPATH and start bzr..
PYTHONPATH=${HOME}/bin/bazaar/lib/python2.5/site-packages
LC_ALL="C"
export PYTHONPATH LC_ALL
${HOME}/bin/bazaar/bin/bzr "$@"
exit $?
