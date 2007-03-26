#! /bin/sh
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
# Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
# Use is subject to license terms.

cd ${SRC_ROOT}
for f in *
do
   if [ -d ${f}/.svn -a -n "${SUBVERSION}" ]
   then
      echo "Update source in ${f} (Subversion)"
      cd ${f}
      ${SUBVERSION} -q up
      if [ $? -ne 0 ]
      then
         error="true"
      fi
      cd ..
   elif [ -d ${f}/CVS -a -n "${CVS}" ]
   then
      echo "Update source in ${f} (CVS)"
      cd ${f}
      ${CVS} -q up 
      if [ $? -ne 0 ]
      then
         error="true"
      fi
      cd ..
   elif [ -d ${f}/.hg -a -n "${MERCURIAL}" ]
   then
      echo "Update source in ${f} (Mercurial)"
      cd ${f}
      ${MERCURIAL} pull
      if [ $? -ne 0 ]
      then
         error="true"
      else
         ${MERCURIAL} update
         if [ $? -ne 0 ]
         then
            error="true"
         fi
      fi     
      cd ..
   fi
done

if [ "${error}" = "true" ]
then
   exit 1
fi

exit 0
