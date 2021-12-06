#! /bin/sh
java -Xmx512M -cp target:lib/ECLA.jar:lib/DTNConsoleConnection.jar:lib/opencsv-3.8.jar core.DTNSim $*
