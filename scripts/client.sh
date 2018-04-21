#!/bin/sh

OLDPWD=$(pwd)
cd scripts
java -jar ../trap-relay-client/target/trap-relay-client-1.0-SNAPSHOT-jar-with-dependencies.jar
cd "$OLDPWD"

