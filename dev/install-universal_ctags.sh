#!/bin/bash

git clone https://github.com/universal-ctags/ctags.git
cd ctags
# need to use last working version until issue #2977 is resolved.
git reset --hard 1295de210df49c979f27e185106a7ebc55146c57
./autogen.sh
./configure && make && make install
/usr/local/bin/ctags --version
