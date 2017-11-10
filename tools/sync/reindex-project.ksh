#!/usr/bin/ksh -p
#
# OpenGrok reindexing script for one project
#

#
# Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
#

#
# Reindex just one project. For that special logging.properties file will be
# created to have per-project logs. Also, to avoid configuration file being
# changed while the indexer is reading it, get the configuration from
# the webapp.
#
function reindex_one
{
	# path relative to source root
	typeset project_path=$1
	typeset -r project_name=$( basename $1 )
	typeset -r template="$OPENGROK_INSTANCE_BASE/etc/logging.properties.template"

	if [[ ! -r $template ]]; then
		print -u2 "cannot read template $template"
		return 1
	fi

	print "Refreshing OpenGrok index for $1"

	#
	# Make sure there is separate logging configuration for this project.
	#
	typeset -r log_conf_dir=$OPENGROK_INSTANCE_BASE/log/configs
	if [[ ! -d $log_conf_dir ]]; then
		mkdir "$log_conf_dir"
		if (( $? != 0 )); then
			print -u2 "cannot create directory $log_conf_dir"
			return 1
		fi
	fi

	log_conf="$log_conf_dir/$project_name"
	if [[ ! -f $log_conf || $template -nt $log_conf ]]; then
		typeset project_log_dir=$OPENGROK_INSTANCE_BASE/log/$project_name
		if [[ ! -d $project_log_dir ]]; then
			mkdir "$project_log_dir"
			if (( $? != 0 )); then
				print -u2 "cannot create directory $project_log_dir"
				return 1
			fi
		fi

		typeset tmp=$( mktemp /tmp/logfile.XXXXXX )
		if [[ -z $tmp ]]; then
			print -u2 "cannot create temporary file"
			return 1
		fi

		cat "$template" | sed "s%DIR_TO_BE%$project_log_dir%" > "$tmp"
		mv "$tmp" "$log_conf"
	fi

	# Get fresh configuration from the webapp.
	config_xml=$( mktemp /tmp/opengrok-conf.XXXXXX )
	if [[ -z $config_xml ]]; then
		print -u2 "cannot create temporary file for configuration"
		exit 1
	fi

	$binary_base/Messages -n config -t getconf > "$config_xml"
	if (( $? != 0 )); then
		print -u2 "failed to get configuration from webapp"
		rm -f "$config_xml"
		exit 1
	fi

	OPENGROK_CONFIGURATION=$opengrok_conf		\
	    OPENGROK_LOGGER_CONFIG_PATH=$log_conf	\
	    OPENGROK_READ_XML_CONFIGURATION=$config_xml	\
	    $binary_base/OpenGrok			\
	    indexpart "$project_name"
	ret=$?

	rm -f "$config_xml"

	return $ret
}

if (( $# != 3 )); then
	print -u2 "$0 opengrok_conf_file binary_base project_name"
	exit 1
fi

typeset -r opengrok_conf=$1
if [[ ! -f $opengrok_conf ]]; then
	print -u2 "file $opengrok_conf does not exist"
	exit 1
fi

. $opengrok_conf

if [[ -z $OPENGROK_INSTANCE_BASE ]]; then
	print -u2 "cannot get OPENGROK_INSTANCE_BASE from " \
	    "$opengrok_conf"
	return 1
fi

typeset -r binary_base=$2
if [[ ! -d $binary_base ]]; then
	print -u2 "not a directory: $binary_base"
	exit 1
fi

reindex_one "$3"
