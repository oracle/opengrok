#!/bin/bash

#
# Clone Universal ctags Github repository and compile from source.
#
git clone https://github.com/universal-ctags/ctags.git
cd ctags
./autogen.sh
./configure && make && make install
/usr/local/bin/ctags --version
