#! /bin/ksh
# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License (the "License").
# You may not use this file except in compliance with the License.
#
# You can obtain a copy of the license at usr/src/OPENSOLARIS.LICENSE
# or http://www.opensolaris.org/os/licensing.
# See the License for the specific language governing permissions
# and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at usr/src/OPENSOLARIS.LICENSE.
# If applicable, add the following below this CDDL HEADER, with the
# fields enclosed by brackets "[]" replaced with your own identifying
# information: Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END

# Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
# Use is subject to license terms.


# The root direction of this opengrok installation
ROOT=/opengrok; export ROOT
# How often should the source be updated (in seconds)
SLEEPTIME=1800; export SLEEPTIME

# The Java version to use
JAVA_HOME=/usr/jdk/latest; export JAVA_HOME

# The path needed
PATH=/usr/bin; export PATH

# The name of the various SCM tools to use 
# CVS=/usr/bin/cvs; export CVS
# SUBVERSION=/usr/bin/svn; export SUBVERSION
# MERCURIAL=/usr/bin/hg; export MERCURIAL

# The name of the web servers that should be notified with the information
# of the active configuration to use. Specify them with hostname:port and 
# separate multiple hosts by space.
# WEBSERVERS="server1:2424 server2:2424"
# WEBSERVERS="localhost:2424"

# The email address to send an email if the source update fails, or a fatal
# error occurs while the index database is beeing updated.
ADMINISTRATOR=root@localhost; export ADMINISTRATOR

# The current version of the Mercurial log parser expects C date format.
LC_ALL=C; export LC_ALL

# Additional directories to look for libraries in. 
# (Subversions java binding etc)
LD_LIBRARY_PATH=/opt/csw/lib/svn; export LD_LIBRARY_PATH

case "$1" in
    start)
        su opengrok -c "${ROOT}/smf/indexer.sh" &
        ;;

    stop)
        pkill -u opengrok -x indexer.sh    
        ;;
 
    *)
        echo "Usage: $0 {start|stop}"
        exit 1
        ;;
esac

exit 0
