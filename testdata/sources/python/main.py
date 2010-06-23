#!/usr/bin/env python
# 

# testing comment

import getopt
import os
#import stat
import string
import sys

import datetime
import time
import math
import random 

SCRIPTHOME=os.path.dirname(sys.argv[0]) 

version_string = "0.4"

def banner():
    print """This is a multi
 string with mu
 ltiple lines
""", 


def version():
    print "version %s " % version_string, 

class MyClass:
    """A simple example class"""
    i = 12345
    x = 0xdeadbeef
    l= 456L
    def f(self):
        return 'hello world'

def main():

    # supress RuntimeWarning for tempnam being insecure
#    warnings.filterwarnings( "ignore" )

    # go ahead
    x = MyClass()
    print "hello world",

    version()

# ---------------------------------------------------------------------------
if  __name__ == "__main__":
    main()


