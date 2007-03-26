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

cd ${ROOT}
if [ ! -f ${ROOT}/smf/next ]
then
   echo 1 > smf/next
fi

read stage < smf/next

output=/tmp/opengrok.$$

send_report()
{
   if [ -n "${ADMINISTRATOR}" ]
   then
      mailx -s "{OpenGrok update failed" ${ADMINISTRATOR} < ${output}
   fi   
}

PROGDIR=${ROOT}/bin
export PROGDIR

while true
do
   SRC_ROOT=${ROOT}/stage${stage}/source; export SRC_ROOT
   DATA_ROOT=${ROOT}/stage${stage}/data; export DATA_ROOT

   # update source code
   rm -f ${output}
   ./smf/update_source.sh > ${output} 2>&1
   if [ $? -ne 0 ]
   then
      send_report
   else
      # (re)generate index database
      echo "Update index database"
      ${JAVA_HOME}/bin/java -Xmx1524m -jar ${PROGDIR}/opengrok.jar \
                    -H -R ${DATA_ROOT}/../configuration.xml >> ${output} 2>&1

      if [ $? -ne 0 ]
      then
         send_report
      else
         rm -f configuration.xml
         ln -s stage${stage}/configuration.xml

         # notify web-servers
         if [ -n "$WEBSERVERS" ]
         then
            for f in $WEBSERVERS
            do
               # Use rsync to populate the files out to the web server
               # rsync -a configuration.xml stage${stage} \
               #          /net/`echo $f|cut -f1 -d:`/opengrok/
               # if [ $? -eq 0 ]
               # then 

                  # Tell the web server to use the new configuration
                 ${JAVA_HOME}/bin/java -Xmx1524m \
                                       -jar ${PROGDIR}/opengrok.jar \
                                       -U ${f} \
                                       -R ${DATA_ROOT}/../configuration.xml 
               # fi

            done
         fi
  
         # update running configuration
         if [ "${stage}" = 1 ]
         then
            stage=2
         else
            stage=1
         fi
         echo ${stage} > smf/next
      fi
   fi
   sleep ${SLEEPTIME}
done
