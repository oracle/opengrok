#!/usr/bin/perl

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

#
# Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
# Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
#

use warnings;

   use DBI;
   
   my $database='dbi:DB2:sample';
   my $user='';
   my $password='';

   my $dbh = DBI->connect($database, $user, $password)
      or die "Can't connect to $database: $DBI::errstr";

   my $sth = $dbh->prepare( 
      q{ SELECT firstnme, lastname 
         FROM employee }
      )
      or die "Can't prepare statement: $DBI::errstr";

   my $rc = $sth->execute
      or die "Can't execute statement: $DBI::errstr";

   print "Query will return $sth->{NUM_OF_FIELDS} fields.\n\n";
   print "$sth->{NAME}->[0]: $sth->{NAME}->[1]\n";

   while (($firstnme, $lastname) = $sth->fetchrow()) {
      print "$firstnme: $lastname\n";
   }

   # check for problems which may have terminated the fetch early
   warn $DBI::errstr if $DBI::err;

   $sth->finish;
   $dbh->disconnect; 

=item snazzle($)

The snazzle() function will behave in the most spectacular
form that you can possibly imagine, not even excepting
cybernetic pyrotechnics.

=cut back to the compiler, nuff of this pod stuff!

sub snazzle($) {
my $thingie = shift;
}

my $x;
$x=12345;              # integer
$x=-54321;             # negative integer
$x=12345.67;           # floating point
$x=6.02E23;            # scientific notation
$x=0xffff;             # hexadecimal
$x=0377;               # octal
$x=4_294_967_296;      # underline for legibility

#
# The following should be marked-up in the same manner as for all sigiled
# identifiers.
#
$s = $var;
$s = \$var;
$s = ${var};

#
# include "<<EOF" examples from
# https://perldoc.perl.org/perlop.html#Quote-and-Quote-like-Operators
#

print <<EOF;
The price is $Price.
EOF
print << "EOF"; # same as above
The price is $Price.
EOF

my $cost = <<'VISTA';  # hasta la ...
That'll be $10 please, ma'am.
VISTA
$cost = <<\VISTA;   # Same thing!
That'll be $10 please, ma'am.
VISTA

print << `EOC`; # execute command and get results
echo hi there
EOC

if ($some_var) {
	print <<~EOF;
	This is a here-doc
	EOF
}

print <<~EOF;
	This text is not indented
	This text is indented with two spaces
		This text is indented with two tabs
EOF

print <<~ 'EOF';
	This text is not indented
	This text is indented with two spaces
		This text is indented with two tabs
EOF

print <<"foo", <<"bar"; # you can stack them
I said foo.
foo
I said bar.
bar

myfunc(<< "THIS", 23, <<'THAT');
Here's a line
or two.
THIS
and here's another.
THAT

#
# Include some samples for the shortcut // syntax of m//
#

$var =~ /pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./ && print;
$var !~/pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./ && print;
/pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./ && print;
(/pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./) && print;
if (/pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./) { print; }
if (1 && /pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./) { print; }
if (0 || /pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./) { print; }
print or/pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./;
print if /pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./;
print unless


	/pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./;

my @o = $contents =~


    /^(?>\S+) \s* := \s* LINKSRC \s* = \s* \S+/mxg;

foreach my $v (@o) { # This loop shouldn't mistakenly be inside the previous m//
	print $v;
}

#
# The following table is from
# https://perldoc.perl.org/perlop.html#Quote-and-Quote-like-Operators .
# The samples following are generated per the table.
#
#	Customary Generic     Meaning	     Interpolates
#	''	 q{}	      Literal		  no
#	""	qq{}	      Literal		  yes
#	``	qx{}	      Command		  yes*
#		qw{}	     Word list		  no
#	//	 m{}	   Pattern match	  yes*
#		qr{}	      Pattern		  yes*
#		 s{}{}	    Substitution	  yes*
#		tr{}{}	  Transliteration	  no (but see below)
#		 y{}{}
#

$s = 'pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.';
$s = q{pP {{nest}}\"\'\(\)\<\>\[\]\/\# et $var.};
$s = q[pP [[nest]]\"\'\(\)\<\>\{\}\/\# et $var.];
$s = q(pP ((nest))\"\'\<\>\{\}\[\]\/\# et $var.);
$s = q<pP <<nest>>\"\'\(\)\{\}\[\]\/\# et $var.>;
$s = q/pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./;
$s = q zpP \"\'\(\)\<\>\{\}\[\]\/\# et $var.z;
$s = q#pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.#;
$s = q'pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.';
$s = q"pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.";
$s = "pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.";
$s = qq{pP {{nest}}\"\'\(\)\<\>\[\]\/\# et $var.};
$s = qq[pP [[nest]]\"\'\(\)\<\>\{\}\/\# et $var.];
$s = qq(pP ((nest))\"\'\<\>\{\}\[\]\/\# et $var.);
$s = qq<pP <<nest>>\"\'\(\)\{\}\[\]\/\# et $var.>;
$s = qq/pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./;
$s = qq zpP \"\'\(\)\<\>\{\}\[\]\/\# et $var.z;
$s = qq#pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.#;
$s = qq'pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.';
$s = qq"pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.";
$s = `pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.`;
$s = qx{pP {{nest}}\"\'\(\)\<\>\[\]\/\# et $var.};
$s = qx[pP [[nest]]\"\'\(\)\<\>\{\}\/\# et $var.];
$s = qx(pP ((nest))\"\'\<\>\{\}\[\]\/\# et $var.);
$s = qx<pP <<nest>>\"\'\(\)\{\}\[\]\/\# et $var.>;
$s = qx/pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./;
$s = qx zpP \"\'\(\)\<\>\{\}\[\]\/\# et $var.z;
$s = qx#pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.#;
$s = qx'pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.';
$s = qx"pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.";
use vars qw{$Cannot %embed &punctuation *here @except $sigils};
use vars qw[$Cannot %embed &punctuation *here @except $sigils];
use vars qw($Cannot %embed &punctuation *here @except $sigils);
use vars qw<$Cannot %embed &punctuation *here @except $sigils>;
use vars qw/$Cannot %embed &punctuation *here @except $sigils/;
use vars qw z$Cannot %embed &punctuation *here @except $sigilsz;
use vars qw#$Cannot %embed &punctuation *here @except $sigils#;
use vars qw'$Cannot %embed &punctuation *here @except $sigils';
use vars qw"$Cannot %embed &punctuation *here @except $sigils";
$s = /pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./;
$s = m{pP {{nest}}\"\'\(\)\<\>\[\]\/\# et $var.};
$s = m[pP [[nest]]\"\'\(\)\<\>\{\}\/\# et $var.];
$s = m(pP ((nest))\"\'\<\>\{\}\[\]\/\# et $var.);
$s = m<pP <<nest>>\"\'\(\)\{\}\[\]\/\# et $var.>;
$s = m/pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./;
$s = m zpP \"\'\(\)\<\>\{\}\[\]\/\# et $var.z;
$s = m#pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.#;
$s = m'pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.';
$s = m"pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.";
$s = qr{pP {{nest}}\"\'\(\)\<\>\[\]\/\# et $var.};
$s = qr[pP [[nest]]\"\'\(\)\<\>\{\}\/\# et $var.];
$s = qr(pP ((nest))\"\'\<\>\{\}\[\]\/\# et $var.);
$s = qr<pP <<nest>>\"\'\(\)\{\}\[\]\/\# et $var.>;
$s = qr/pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./;
$s = qr zpP \"\'\(\)\<\>\{\}\[\]\/\# et $var.z;
$s = qr#pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.#;
$s = qr'pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.';
$s = qr"pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.";
$s = s{pP {{nest}}\"\'\(\)\<\>\[\]\/\# et $var.
    }{pP {{nest}}\"\'\(\)\<\>\[\]\/\# et $var.}x;
$s = s[pP [[nest]]\"\'\(\)\<\>\{\}\/\# et $var.
    ][pP [[nest]]\"\'\(\)\<\>\{\}\/\# et $var.]x;
$s = s(pP ((nest))\"\'\<\>\{\}\[\]\/\# et $var.
    )(pP ((nest))\"\'\<\>\{\}\[\]\/\# et $var.)x;
$s = s<pP <<nest>>\"\'\(\)\{\}\[\]\/\# et $var.
    ><pP <<nest>>\"\'\(\)\{\}\[\]\/\# et $var.>x;
$s = s/pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./
    pP \"\'\(\)\<\>\{\}\[\]\/\# et $var./x;
$s = s zpP \"\'\(\)\<\>\{\}\[\]\/\# et $var.z
    pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.zx;
$s = s#pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.#
    pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.#x;
$s = s'pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.'
    pP \"\'\(\)\<\>\{\}\[\]\/\# et $var.'x;
$s = s"pP \"\'\(\)\<\>\{\}\[\]\/\# et $var."
    pP \"\'\(\)\<\>\{\}\[\]\/\# et $var."x;
$s = tr{pP \"\'\(\)\<\>\[\]\/\# fin.}{pP \"\'\(\)\<\>\[\]\/\# fin.};
$s = tr[pP \"\'\(\)\<\>\{\}\/\# fin.][pP \"\'\(\)\<\>\{\}\/\# fin.];
$s = tr(pP \"\'\<\>\{\}\[\]\/\# fin.)(pP \"\'\<\>\{\}\[\]\/\# fin.);
$s = tr<pP \"\'\(\)\{\}\[\]\/\# fin.><pP \"\'\(\)\{\}\[\]\/\# fin.>;
$s = tr/pP \"\'\(\)\<\>\{\}\[\]\/\# fin./pP \"\'\(\)\<\>\{\}\[\]\/\# fin./;
$s = tr zpP \"\'\(\)\<\>\{\}\[\]\/\# fin.zpP \"\'\(\)\<\>\{\}\[\]\/\# fin.z;
$s = tr#pP \"\'\(\)\<\>\{\}\[\]\/\# fin.#pP \"\'\(\)\<\>\{\}\[\]\/\# fin.#;
$s = tr'pP \"\'\(\)\<\>\{\}\[\]\/\# fin.'pP \"\'\(\)\<\>\{\}\[\]\/\# fin.';
$s = tr"pP \"\'\(\)\<\>\{\}\[\]\/\# fin."pP \"\'\(\)\<\>\{\}\[\]\/\# fin.";
$s = y{pP \"\'\(\)\<\>\[\]\/\# fin.}{pP \"\'\(\)\<\>\[\]\/\# fin.};
$s = y[pP \"\'\(\)\<\>\{\}\/\# fin.][pP \"\'\(\)\<\>\{\}\/\# fin.];
$s = y(pP \"\'\<\>\{\}\[\]\/\# fin.)(pP \"\'\<\>\{\}\[\]\/\# fin.);
$s = y<pP \"\'\(\)\{\}\[\]\/\# fin.><pP \"\'\(\)\{\}\[\]\/\# fin.>;
$s = y/pP \"\'\(\)\<\>\{\}\[\]\/\# fin./pP \"\'\(\)\<\>\{\}\[\]\/\# fin./;
$s = y zpP \"\'\(\)\<\>\{\}\[\]\/\# fin.zpP \"\'\(\)\<\>\{\}\[\]\/\# fin.z;
$s = y#pP \"\'\(\)\<\>\{\}\[\]\/\# fin.#pP \"\'\(\)\<\>\{\}\[\]\/\# fin.#;
$s = y'pP \"\'\(\)\<\>\{\}\[\]\/\# fin.'pP \"\'\(\)\<\>\{\}\[\]\/\# fin.';
$s = y"pP \"\'\(\)\<\>\{\}\[\]\/\# fin."pP \"\'\(\)\<\>\{\}\[\]\/\# fin.";
