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

proc printHelloWorld {} {
   puts "Hello world"
}

proc viewSource { f } {
   global filesVisited EB
   set EB(curFile) $f
   lappend filesVisited $f

   # change window title to show the current file
   set wt [wm title .eb]
   if { [string first : $wt] != -1 } {
	set idx [string first : $wt]
	set base [string range $wt 0 $idx]
	set wtn [concat $base $f]
	 } else {
		  set wtn [concat ${wt}: $f]
	 }
    wm title .eb $wtn
    .eb.f.t config -state normal
    .eb.f.t delete 1.0 end
    if [catch {open $f} in] {
	.eb.f.t insert end $in
     } else {
	.eb.f.t insert end [read $in]
	close $in
     }
    .eb.f.t config -state normal
    .eb.buttons.apply config -command [list applySource $f]
}