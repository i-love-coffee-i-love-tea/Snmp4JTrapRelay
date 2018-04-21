#!/bin/sh

OLDPWD=$(pwd)

cd scripts
java -jar ../trap-relay-daemon/target/trap-relay-daemon-1.0-SNAPSHOT-jar-with-dependencies.jar
cd "$OLDPWD"
