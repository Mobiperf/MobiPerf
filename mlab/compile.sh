#Compile for MobiPerf server

#prepare necessary files structures
mkdir run/tcpdump
mkdir run/server
mkdir run/data
mkdir run/data/android
mkdir run/data/iPhone

cd bin

for i in Tcpdump Downlink Uplink Collector Version Whoami Reach
#Bt BtNondft Http removed   #UserState on hold
do
	#-e: backslash-escaped characters is enabled
	echo -e "Main-Class: servers."$i"\n" > manifest
	jar cvfm $i.jar manifest servers/$i*.class  common/*.class
	mkdir ../run/server/$i
	mv $i.jar ../run/server/$i/
done

rm manifest

cd ..

