#!/bin/bash

git clone https://github.com/universal-ctags/ctags.git
cd ctags
./autogen.sh
./configure && make && make install
