#!/bin/bash
#this script needs to be run from the current directory

for i in Downlink Uplink ServerConfig
do
	echo "stopping $i"
	ps aux | grep "$i.jar" | awk '{system("kill -9 " $2);}'
done
