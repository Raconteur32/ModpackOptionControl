#!/bin/sh
if [ -n "$1" ]; then
    ./gradlew :gui:run "-Pmoc.gameDir=$1"
else
    ./gradlew :gui:run
fi
