#!/bin/sh

## Create a word list from an aspell file
#
# 1. ftp://ftp.gnu.org/gnu/aspell/dict/0index.html
# 2. tar jxfv aspell-<lang>-<ver>.tar.bz2
# 3. cd aspell-<lang>-<ver>
# 4. ./configure && make && make install
# 5. invoke this script (no args needed from in the dir)

LANG=$1

if [ -z "$LANG" ] ; then
    LANG=$(ls *.dat | grep -E '^[a-z]{2}.dat$' | sed 's/\.dat$//')
fi

if [ -z "$LANG" ] ; then
    echo "usage: $0 <language code>"
    echo "example: $0 en"
    exit 1
fi

echo "dumping words for language: $LANG"

aspell -d $LANG dump master | aspell -l $LANG expand > $LANG-words.txt
echo "wrote $LANG-words.txt"
