#!/usr/bin/ksh -p
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
# file and include the License file at usr/src/OPENSOLARIS.LICENSE.
# If applicable, add the following below this CDDL HEADER, with the
# fields enclosed by brackets "[]" replaced with your own identifying
# information: Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#

#
# Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
#


PKG_REPO_NAME=myrepo
PKG_PUBLISHER=opengrok
PKG_NAME=opengrok

case "$1" in
   -v)
      shift
      if [ $# -gt 0 ]
      then
      	 human_readable_version=$1
      else
	 echo "Usage: $0 -v <version>"
	 exit 1
      fi
      shift
    ;;
    *)
      echo "Usage: $0 -v <version>"
      exit 1
    ;;
esac


if [ ! "$human_readable_version" ]
then
   echo "Cannot identify the version"
   exit 1
fi


# Transform OpenGrok version to the IPS version standard
# As following:
#
# 0.12		~	0.12.0.1.0
# 0.<n>		~	0.<n>.0.1.0
# ...
#
# 0.12-rc1	~	0.12.0.0.1
# 0.12-rc<i>	~	0.12.0.0.<i>
# ...
#
# Note that the release candidate must follow imediately after the basic version number (0.12)
#
#
# 0.12.1	~	0.12.1.0.0
# 0.12.<j>	~	0.12.<j>.0.0
# ...
#
# 0.12.0.1	~	0.12.0.1.1
# 0.12.0.<k>	~	0.12.0.1.<k>
# ...
#
#
# This is done due to IPS restriction on the version's names
# This also keep the order of picking the versions from repository



version=$(echo "$human_readable_version" | nawk -F"." '
function parse ( version )
{
   if ( version ~ /^[0-9]+\.[0-9]+-rc[0-9]+/ )
   {
      FS="-rc"
      ret = $1".0.0."$2;
      FS="."
      return ret;
   }
   else if ( version ~ /^[0-9]+\.[0-9]+$/ )
   {
      return version".0.1.0";
   }
   else if ( version ~ /^[0-9]+\.[0-9]+\.[0-9]+$/ )
   {
      ret = 0;
      if ( $3 == "0" )
        ret = 1;
      return version"."ret".0";
   }
   else if ( version ~ /^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$/ )
   {
      ret = $1;
      for ( i = 2; i < 4; i ++ )
        ret = ret"."$i;
      return ret".1."$NF;
   }
   else if ( version ~ /rc/ )
   {
      return -1;
   }
   else
   {
      return version;
   }

}
{
   print parse($0);
}
')

if [ $? != 0 ]
then
   echo "Command failed: nawk ..."
   exit 1
fi

if [ "x$version" = "x-1" ]
then
   echo "Not supported naming scheme $version"
   exit 1
fi


# create local repo
mkdir -p "$PKG_REPO_NAME"

if [ $? != 0 ]
then
   echo Command failed: mkdir -p "$PKG_REPO_NAME"
   exit 1
fi

PKG()
{
   "$@"
   if [ $? != 0 ]
   then
      echo Command failed: "$@"
      rm -rf "$PKG_REPO_NAME"
      exit 1
   fi
}


PKG pkgrepo create "$PKG_REPO_NAME"
PKG pkgrepo -s "$PKG_REPO_NAME" set publisher/prefix=${PKG_PUBLISHER}

export PKG_REPO="$PKG_REPO_NAME"

eval `pkgsend open ${PKG_NAME}@${version}`


if [ $? != 0 ]
then
    echo "Fatal: could not open ${PKG_NAME}@${version}"
    rm -rf "$PKR_REPO_NAME"
    exit 1
fi

for dir in /etc/opengrok /usr/opengrok /usr/opengrok/man /usr/opengrok/man/man1\
	   /usr/opengrok/doc
do
   PKG pkgsend add dir mode=0755 owner=root group=sys path=${dir}
done


for dir in /usr/opengrok/bin /usr/opengrok/lib
do
   PKG pkgsend add dir mode=0755 owner=root group=bin path=${dir}
done

for dir in /var/opengrok /var/opengrok/data /var/opengrok/etc \
           /var/opengrok/log /var/opengrok/src
do
   PKG pkgsend add dir mode=0755 owner=webservd group=webservd path=${dir}
done

PKG pkgsend add link path=/usr/opengrok/lib/lib target=../lib

PKG pkgsend add file platform/solaris/smf/opengrok.xml mode=0444 owner=root group=sys path=/var/svc/manifest/application/opengrok.xml restart_fmri=svc:/system/manifest-import:default
PKG pkgsend add file platform/solaris/smf/svc-opengrok mode=0555 owner=root group=bin path=/lib/svc/method/svc-opengrok
PKG pkgsend add file platform/solaris/smf/ogindexd mode=0555 owner=root group=bin path=/usr/opengrok/lib/ogindexd

PKG pkgsend add file OpenGrok mode=0555 owner=root group=bin path=/usr/opengrok/bin/OpenGrok
PKG pkgsend add file tools/Groups mode=0555 owner=root group=bin path=/usr/opengrok/bin/Groups
PKG pkgsend add file tools/Messages mode=0555 owner=root group=bin path=/usr/opengrok/bin/Messages

PKG pkgsend add file dist/opengrok.jar mode=0444 owner=root group=bin path=/usr/opengrok/lib/opengrok.jar

PKG pkgsend add file logging.properties mode=0444 owner=root group=sys path=/usr/opengrok/doc/logging.properties
PKG pkgsend add file README.txt mode=0444 owner=root group=sys path=/usr/opengrok/doc/README.txt
PKG pkgsend add file CHANGES.txt mode=0444 owner=root group=sys path=/usr/opengrok/doc/CHANGES.txt
PKG pkgsend add file LICENSE.txt mode=0444 owner=root group=sys path=/usr/opengrok/doc/LICENSE.txt
PKG pkgsend add file NOTICE.txt mode=0444 owner=root group=sys path=/usr/opengrok/doc/NOTICE.txt
PKG pkgsend add file doc/EXAMPLE.txt mode=0444 owner=root group=sys path=/usr/opengrok/doc/EXAMPLE.txt
PKG pkgsend add file doc/ctags.config mode=0444 owner=root group=sys path=/usr/opengrok/doc/ctags.config



# install libs
LV=6.2.1
for file in ant.jar \
    bcel-6.0.jar \
    lucene-analyzers-common-${LV}.jar \
    lucene-core-${LV}.jar \
    lucene-queryparser-${LV}.jar \
    lucene-suggest-${LV}.jar \
    jrcs.jar \
    swing-layout-0.9.jar \
    json-simple-1.1.1.jar
do
   PKG pkgsend add file dist/lib/${file} mode=0444 owner=root group=bin \
       path=/usr/opengrok/lib/${file}
done


# install man page
PKG pkgsend add file dist/opengrok.1 mode=0444 owner=root group=bin path=/usr/opengrok/man/man1/opengrok.1

# install default configuration
PKG pkgsend add depend fmri=pkg:/runtime/java/jre-8 type=require
PKG pkgsend add depend fmri=pkg:/web/java-servlet/tomcat-8 type=require

# Following line gets commented by that the developer/tool/exuberant-ctags has been removed from IPS
# This has to stay commented until the next release of Solaris will contain the exhuberant ctags package
#PKG pkgsend add depend fmri=pkg:/developer/tool/exuberant-ctags type=require

PKG pkgsend add file dist/source.war mode=0444 owner=webservd group=webservd path=/usr/opengrok/lib/source.war

PKG pkgsend add set name=description value="OpenGrok - wicked fast source browser"
PKG pkgsend add set name=pkg.human-version value="${human_readable_version}"
PKG pkgsend close


# checks whether the same file exists and updates it
if [ -f "${PKG_NAME}-${human_readable_version}.p5p" ]
then
   rm -f ${PKG_NAME}-${human_readable_version}.p5p
fi

outfile="$PWD/dist/${PKG_NAME}-${human_readable_version}.p5p"
rm -f "${outfile}"
PKG pkgrecv -s "$PKG_REPO_NAME" -a -d "${outfile}" ${PKG_NAME}

# cleanup
if [ -d "$PKG_REPO_NAME" ]
then
   rm -rf "$PKG_REPO_NAME"
fi

unset PKG_REPO

echo "SUCCESSFULLY COMPLETED"
echo "OpenGrok has been packaged into $outfile"
echo "For more information about installing OpenGrok visit pkg man page."
