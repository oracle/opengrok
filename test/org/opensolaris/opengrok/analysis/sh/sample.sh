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
#
# Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
# Use is subject to license terms.
#
# xref: build and maintain source cross-reference databases.
#

ONBLDDIR=$(dirname $(whence $0))

PROG=`basename $0`
XREFMK=`dirname $0`/xref.mk
XRMAKEFILE=Makefile export XRMAKEFILE
MAKE="dmake -m serial"

#
# The CSCOPEOPTIONS variable can cause problems if it's set in the environment
# when using cscope; remove it.
#
unset CSCOPEOPTIONS

#
# The CDPATH variable causes ksh's `cd' builtin to emit messages to stdout
# under certain circumstances, which can really screw things up; unset it.
#
unset CDPATH

#
# Print the provided failure message and exit with an error.
#
fail()
{
        echo $PROG: $@ > /dev/stderr
        exit 1
}

#
# Print the provided warning message.
#
warn()
{
        echo $PROG: warning: $@ > /dev/stderr
}

#
# Print the provided informational message.
#
info()
{
        echo $PROG: $@
}

#
# Print the provided informational message, and the current value of $SECONDS
# in a user-friendly format.
#
timeinfo()
{
	typeset -Z2 sec
	typeset -i min seconds

	((seconds = SECONDS))
	((min = seconds / 60))
	((sec = seconds % 60))

	info "$1 in ${min}m${sec}s"
}

which_scm | read SCM_MODE CODEMGR_WS || exit 1

if [[ $SCM_MODE == "unknown" ]];then
	print -u2 "Unable to determine SCM type currently in use."
	exit 1
fi

export CODEMGR_WS
SRC=$CODEMGR_WS/usr/src export SRC
MACH=`uname -p` export MACH

[ -f $XREFMK ] || fail "cannot locate xref.mk"

clobber=
noflg=
xrefs=

while getopts cfm:px: flag; do
	case $flag in
	c)
		clobber=y
		;;
	f)
		noflg=y
		;;
	m)
		XRMAKEFILE=$OPTARG
		;;
	p)
		#
		# The ENVCPPFLAGS* environment variables contain the include
		# paths to our proto areas; clear 'em so that they don't end
		# up in CPPFLAGS, and thus don't end up in XRINCS in xref.mk.
		#
		ENVCPPFLAGS1=
		ENVCPPFLAGS2=
		ENVCPPFLAGS3=
		ENVCPPFLAGS4=
		;;
 	x)
		xrefs=$OPTARG
		;;
	\?)
		echo "usage: $PROG [-cfp] [-m <makefile>]"\
		     "[-x cscope|ctags|etags[,...]] [<subtree> ...]"\
		      > /dev/stderr
		exit 1
		;;
	esac
done

shift $((OPTIND - 1))

#
# Get the list of directories before we reset $@.
#
dirs=$@
[ -z "$dirs" ] && dirs=.

#
# Get the canonical path to the workspace.  This allows xref to work
# even in the presence of lofs(7FS).
#
cd $CODEMGR_WS
CODEMGR_WS=`/bin/pwd`
cd - > /dev/null

#
# Process the xref format list.  For convenience, support common synonyms
# for the xref formats.
#
if [ -z "$xrefs" ]; then
	#
	# Disable etags if we can't find it.
	#
	xrefs="cscope ctags"
	$MAKE -e -f $XREFMK xref.etags.check 2>/dev/null 1>&2 && \
	    xrefs="$xrefs etags"
else
	oldifs=$IFS
	IFS=,
	set -- $xrefs
	IFS=$oldifs

	xrefs=
	for xref; do
		case $xref in
		cscope|cscope.out)
			xrefs="$xrefs cscope"
			;;
		ctags|tags)
			xrefs="$xrefs ctags"
			;;
		etags|TAGS)
			xrefs="$xrefs etags"
			;;
		*)
			warn "ignoring unknown cross-reference \"$xref\""
			;;
 		esac
 	done

	[ -z "$xrefs" ] && fail "no known cross-reference formats specified"
fi

#
# Process the requested list of directories.
#
for dir in $dirs; do
	if [ ! -d $dir ]; then
		warn "directory \"$dir\" does not exist; skipping"
		continue
	fi

	#
	# NOTE: we cannot use $PWD because it will mislead in the presence
	# of lofs(7FS).
	#
	cd $dir || fail "cannot change to directory $dir"
	pwd=`/bin/pwd`
	reldir=${pwd##${CODEMGR_WS}/}
	if [ "$reldir" = "$pwd" ]; then
		warn "directory \"$pwd\" is not beneath \$CODEMGR_WS; skipping"
		cd - > /dev/null
		continue
	fi

	#
	# If we're building cross-references, then run `xref.clean' first
	# to purge any crud that may be lying around from previous aborted runs.
	#
	if [ -z "$clobber" ]; then
		$MAKE -e -f $XREFMK xref.clean > /dev/null
	fi

	#
	# Find flg-related source files, if requested.
	#
	if [ -z "$noflg" -a -z "$clobber" ]; then
		SECONDS=0
    		info "$reldir: finding flg-related source files"
		$MAKE -e -f $XREFMK xref.flg > /dev/null
		if [ $? -ne 0 ]; then
			warn "$reldir: unable to find flg-related source files"
		else
			nfiles=`wc -l < xref.flg`
			if [ "$nfiles" -eq 1 ]; then
				msg="found 1 flg-related source file"
			else
				msg="found $nfiles flg-related source files"
			fi
			timeinfo "$reldir: $msg"
		fi
	fi

	#
	# Build or clobber all of the requested cross-references.
	#
	for xref in $xrefs; do
		if [ -n "$clobber" ]; then
			info "$reldir: clobbering $xref cross-reference"
			$MAKE -e -f $XREFMK xref.${xref}.clobber > /dev/null ||
 			    warn "$reldir: cannot clobber $xref cross-reference"
			continue
		fi

		SECONDS=0
		info "$reldir: building $xref cross-reference"
		$MAKE -e -f $XREFMK xref.${xref} > /dev/null ||
		    fail "$reldir: cannot build $xref cross-reference"
		timeinfo "$reldir: built $xref cross-reference"
 	done

	$MAKE -e -f $XREFMK xref.clean > /dev/null ||
	    warn "$reldir: cannot clean up temporary files"
	cd - > /dev/null
done
exit 0
