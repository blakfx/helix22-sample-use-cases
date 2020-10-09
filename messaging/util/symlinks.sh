#!/bin/bash

# NOTE: the run script calls this script
# Running ./util/run.sh in the /helix/ directory is sufficient

# Need to call this to create the symlinks manually
# For some reason git was unhappy with adding them on Windows
# Seems safer to be able to generate them manually as well

# Must run this file from /helix/ directory within Docker
CUR_DIR=$(pwd)
if [ "$CUR_DIR" != "/helix" ]; then
    echo "You must run this file from /helix directory within Docker"
    exit 1
fi

cd /native_dep

# rename libHelixForJava variants to their appropriate libHelixForJAVA file names
for file in `ls | grep libHelixForJava.so`; do NEWPATH=`echo $file | sed -e 's/Java/JAVA/g'`; mv $file $NEWPATH; done

BASE_NAMES=("libHelix.so" "libHelixForJAVA.so" "libWolfCrypt.so" "libczmq.so" "libentropy.so" "libh_sqlite3.so" "liblibressl_snow3g.so" "libskein3fish.so" "libsnow3g.so" "libsqlcipher.so" "libtomcrypt.so" "libtwofish.so" "libzmq.so")
MSWITCH=(2 1 2 1 2 2 2 2 2 2 2 2 1)

for base in ${!BASE_NAMES[@]}
do
    if [ ${MSWITCH[$base]} -eq 2 ];
    then
        MID=`ls -l | grep -Eo "${BASE_NAMES[$base]}.*" | cut -d'.' -f1,2,3,4 | tail -n 1`
    else
        MID=`ls -l | grep -Eo "${BASE_NAMES[$base]}.*" | cut -d'.' -f1,2,3 | tail -n 1`
    fi
    TOP=`ls -l | grep -Eo "${BASE_NAMES[$base]}.*" | tail -n 1`
    # echo "First" ${BASE_NAMES[$base]} "Second" $MID "Top" $TOP
    ln -sf $MID ${BASE_NAMES[$base]}
    ln -sf $TOP $MID
done


cd /helix