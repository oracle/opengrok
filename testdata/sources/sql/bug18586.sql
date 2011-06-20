-- test case for bug #18586 - ArrayIndexOutOfBoundsException when indexing SQL file

create table abcdef(
  x int,
  y int);

insert into abcdef values (1,2), (3,4);
