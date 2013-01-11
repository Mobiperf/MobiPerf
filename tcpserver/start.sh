#!/bin/bash
#this script needs to be run from the current directory

cd ~/mobiperf

for i in Downlink Uplink ServerConfig KeepAlive Command
do
	echo "running $i"
	sudo java -jar $i.jar mlab &
done

echo "success deploying and running server\n"
