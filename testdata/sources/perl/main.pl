#!/usr/bin/perl
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

my x;
$x=12345              # integer
$x=-54321             # negative integer
$x=12345.67            # floating point 
$x=6.02E23             # scientific notation 
$x=0xffff              # hexadecimal 
$x=0377                # octal 
$x=4_294_967_296       # underline for legibility

