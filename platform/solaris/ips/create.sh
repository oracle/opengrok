#! /bin/ksh -p
#
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
#
# Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
# Use is subject to license terms.

version=`grep 'name="version"' build.xml | cut -f 4 -d \"`
revision=0.`uname -v | perl -ne 's/(\d+)/print "$1\n"/e'`

PKGSEND() {
   pkgsend "$@"
   if [ $? != 0 ]
   then
      echo Command failed: pkgsend "$@"
      pkgsend close -A
      exit 1
   fi
}

eval `pkgsend open OSOLopengrok@${version}-${revision}`
if [ $? != 0 ]
then
    echo "Fatal: could not open OSOLopengrok@${version}-${revision}"
    exit 1
fi

for dir in /etc/opengrok /usr/opengrok /usr/opengrok/man /usr/opengrok/man/man1\
	   /usr/opengrok/doc
do
   PKGSEND add dir mode=0755 owner=root group=sys path=${dir}
done

for dir in /usr/opengrok/bin /usr/opengrok/lib
do
   PKGSEND add dir mode=0755 owner=root group=bin path=${dir}
done

for dir in /var/opengrok /var/opengrok/data /var/opengrok/etc \
           /var/opengrok/log /var/opengrok/source
do
   PKGSEND add dir mode=0755 owner=webservd group=webservd path=${dir}
done

PKGSEND add link path=/usr/opengrok/lib/lib target=../lib

PKGSEND add file platform/solaris/smf/opengrok.xml mode=0444 owner=root group=sys path=/var/svc/manifest/application/opengrok.xml restart_fmri=svc:/system/manifest-import:default
PKGSEND add file platform/solaris/smf/svc-opengrok mode=0555 owner=root group=bin path=/lib/svc/method/svc-opengrok
PKGSEND add file platform/solaris/smf/ogindexd mode=0555 owner=root group=bin path=/usr/opengrok/lib/ogindexd
PKGSEND add file OpenGrok mode=0555 owner=root group=bin path=/usr/opengrok/bin/OpenGrok
PKGSEND add file dist/opengrok.jar mode=0444 owner=root group=bin path=/usr/opengrok/lib/opengrok.jar

PKGSEND add file logging.properties mode=0444 owner=root group=sys path=/usr/opengrok/doc/logging.properties
PKGSEND add file README.txt mode=0444 owner=root group=sys path=/usr/opengrok/doc/README.txt
PKGSEND add file CHANGES.txt mode=0444 owner=root group=sys path=/usr/opengrok/doc/CHANGES.txt
PKGSEND add file LICENSE.txt mode=0444 owner=root group=sys path=/usr/opengrok/doc/LICENSE.txt
PKGSEND add file NOTICE.txt mode=0444 owner=root group=sys path=/usr/opengrok/doc/NOTICE.txt
PKGSEND add file doc/EXAMPLE.txt mode=0444 owner=root group=sys path=/usr/opengrok/doc/EXAMPLE.txt


# install libs
for file in ant.jar bcel-5.2.jar \
            lucene-core-3.5.0.jar lucene-spellchecker-3.5.0.jar \
            jrcs.jar \
            swing-layout-0.9.jar
do
   PKGSEND add file dist/lib/${file} mode=0444 owner=root group=bin path=/usr/opengrok/lib/${file}
done


# install man page
PKGSEND add file dist/opengrok.1 mode=0444 owner=root group=bin path=/usr/opengrok/man/man1/opengrok.1

# install default configuration
PKGSEND add depend fmri=pkg:/runtime/java type=require
PKGSEND add depend fmri=pkg:/web/java-servlet/tomcat type=require
PKGSEND add depend fmri=pkg:/developer/tool/exuberant-ctags type=require
PKGSEND add file dist/source.war mode=0444 owner=webservd group=webservd path=/var/tomcat6/webapps/source.war

PKGSEND add set name=description value="OpenGrok - Wicked fast source browser"
PKGSEND close
