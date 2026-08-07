#!/bin/sh
./gradlew :gui_web:shadowJar
if [ -n "$1" ]; then
    java "-Dmoc.gameDir=$1" -jar gui_web/build/libs/moc-web.jar
else
    java -jar gui_web/build/libs/moc-web.jar
fi
