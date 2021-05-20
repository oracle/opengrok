#!/bin/bash

#
# Clone Universal ctags Github repository and compile from source.
#
cd ctags
./autogen.sh
./configure && make && make install
/usr/local/bin/ctags --version
