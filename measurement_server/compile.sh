#!/bin/bash

cd src
#javac -d ../bin servers/*.java
mkdir ../bin 2> /dev/null

# compile TCP server
javac -target 1.7 -source 1.7 -d ../bin servers/*.java # compile tcp server, satisfy server version
if [ $? -ne 0 ]
then
    echo "Fail to compile source code of tcp server"
    exit $?
fi

# compile UDP server
javac -target 1.7 -source 1.7 -d ../bin com/udpmeasurement/*.java #satisfy server version
if [ $? -ne 0 ]
then
    echo "Fail to compile source code of udp server"
    exit $?
fi

cd ..
mkdir mlab 2> /dev/null

# generate jars for TCP server
cd bin
for i in Uplink Downlink ServerConfig
do
	echo "Main-Class: servers.$i" > manifest
	jar cvfm $i.jar manifest servers/$i*.class servers/Definition.class servers/Utilities.class
	mv $i.jar ../mlab
done

rm manifest
echo "Successful compile the TCP server code."

# generate jar for UDP server
echo "Main-Class: com.udpmeasurement.UDPServer" > manifest
jar cvfm UDPServer.jar manifest com/udpmeasurement/*.class
mv UDPServer.jar ../mlab

rm manifest
echo "Successful compile the UDP server code."
cd ..
