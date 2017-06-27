#!/bin/sh

# Load SMF shell support definitions
. /lib/svc/share/smf_include.sh

#
# Move any content from /var/opengrok/.migrate/ to /var/share/opengrok/
#
# The migrate_shared_files.py is not a public interface and will be eventually
# replaced by different script which will make this service to fail, hopefully
# prompting its removal - see the package definition and the comment above
# refresh_fmri=svc:/system/filesystem/minimal:default
#
/lib/svc/share/migrate_shared_files.py /var/.migrate /var/share opengrok

# After this script runs, the service does not need to remain online.
smf_method_exit $SMF_EXIT_TEMP_DISABLE done "OpenGrok shared files moved"
