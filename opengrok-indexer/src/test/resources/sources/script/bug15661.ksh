#!/bin/ksh93

#
# Examples to verify bug #15661 - support for new ksh command
# substitution x=${ cmd ; }
#

# This should print "hello}"
echo ${ echo hello};}

# This should print "a b"
echo ${
  echo a
  echo b;}

# This should print "d e f"
echo ${ echo d; echo e;
echo f
}

# This should print "g h"
x="${
  echo g
  echo h;}"
echo $x

# This should print "hi hello"
x="${ echo hi; echo hello;}"
echo $x

# This should print "hi hello"
x="${ echo hi
echo hello
}"
echo $x

# This should print "hello world"
x="$(echo ${ echo $(echo ${ echo hello ;});}) world"
echo $x

# This should print "hello world (again)"
x="${ echo $(echo ${ echo $(echo ${ echo hello ;});}) "world";} (again)"
echo $x

# This should print "test1 test2 test3"
x="${ echo test1
{
  echo test2
}
echo test3;}"
echo $x
