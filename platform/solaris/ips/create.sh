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
# Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
# Use is subject to license terms.

version=`grep 'name="version"' build.xml | cut -f 4 -d \"`
revision=`hg log --template '{rev}' -r tip`

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

for dir in /etc/opengrok /usr/opengrok /usr/opengrok/bin /usr/opengrok/lib \
           /usr/opengrok/man /usr/opengrok/man/sman1
do
   PKGSEND add dir mode=0755 owner=root group=sys path=${dir}
done

for dir in /var/opengrok /var/opengrok/data /var/opengrok/etc \
           /var/opengrok/log /var/opengrok/source
do
   PKGSEND add dir mode=0755 owner=noaccess group=noaccess path=${dir}
done

PKGSEND add file platform/solaris/smf/opengrok.xml mode=0444 owner=root group=sys path=/var/svc/manifest/application/opengrok.xml restart_fmri=svc:/system/manifest-import:default
PKGSEND add file platform/solaris/smf/opengrok mode=0555 owner=bin group=bin path=/lib/svc/method/opengrok
PKGSEND add file dist/opengrok.jar mode=0555 owner=bin group=bin path=/usr/opengrok/bin/opengrok.jar
PKGSEND add link path=/usr/opengrok/bin/lib target=../lib

# install libs
for file in bcel-5.1.jar jakarta-oro-2.0.8.jar \
            lucene-core-2.2.0.jar lucene-spellchecker-2.2.0.jar \
            org.apache.commons.jrcs.diff.jar org.apache.commons.jrcs.rcs.jar \
            swing-layout-0.9.jar
do
   PKGSEND add file dist/lib/${file} mode=0444 owner=bin group=bin path=/usr/opengrok/lib/${file}
done


# install man page
PKGSEND add file dist/opengrok.1 mode=0444 owner=bin group=bin path=/usr/opengrok/man/sman1/opengrok.1

# install default configuration
PKGSEND add file platform/solaris/default/opengrok.properties mode=0444 owner=root group=sys path=/etc/opengrok/opengrok.properties preserve=renameold
PKGSEND add link path=/usr/opengrok/bin/lib/ant.jar target=/usr/share/lib/ant/ant.jar
PKGSEND add link path=/usr/opengrok/bin/lib/jmxremote_optional.jar target=/usr/share/lib/jdmk/jmxremote_optional.jar
PKGSEND add depend fmri=pkg:/SUNWjdmk-base type=require
PKGSEND add depend fmri=pkg:/SUNWant type=require
PKGSEND add depend fmri=pkg:/SUNWj6rt type=require
PKGSEND add depend fmri=pkg:/SUNWtcat type=require
PKGSEND add file dist/source.war mode=0444 owner=root group=bin path=/var/apache/tomcat/webapps/source.war

PKGSEND add set name=description value="OpenGrok - Wicked fast source browser"
PKGSEND close
