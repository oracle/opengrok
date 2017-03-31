#!/bin/sh

if [ `uname -m` = "x86_64" ]; then
    curl -o bkinst.bin https://www.bitkeeper.com/downloads/7.3.1ce/bk-7.3.1ce-x86_64-glibc219-linux.bin
else
    curl -o bkinst.bin https://www.bitkeeper.com/downloads/7.3.1ce/bk-7.3.1ce-x86-glibc219-linux.bin
fi

chmod +x bkinst.bin
./bkinst.bin /usr/local/bitkeeper
ln -s /usr/local/bitkeeper/bk /usr/local/bin/bk
