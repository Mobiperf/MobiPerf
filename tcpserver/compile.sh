#!/bin/bash

cd src
#javac -d ../bin servers/*.java
mkdir ../bin 2> /dev/null
javac -target 1.7 -source 1.7 -d ../bin servers/*.java # satisfy server version
if [ $? -ne 0 ]
then
    echo "Fail to compile source code"
    exit $?
fi
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
echo "Successful compile the TCP server code."
cd ..
