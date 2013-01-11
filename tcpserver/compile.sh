#!/bin/bash

cd src
# javac -d ../bin servers/*.java
javac -target 1.5 -source 1.5 -d ../bin servers/*.java # satisfy server version
cd ..

mkdir mlab 2> /dev/null

cd bin
for i in Uplink Downlink ServerConfig
do
	echo "Main-Class: servers.$i" > manifest
	jar cvfm $i.jar manifest servers/$i*.class servers/Definition.class servers/Utilities.class
	mv $i.jar ../mlab
done

rm manifest

cd ..
