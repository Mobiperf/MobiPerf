#!/bin/bash
#Compile and deploy for MLab servers
node=nodeList
if [ $1 = "-c" ]; then
	mkdir mlab

	cd bin

	for i in Downlink Uplink KeepAlive Command
	do
		#-e: backslash-escaped characters is enabled
		echo -e "Main-Class: servers."$i"\n" > manifest
		jar cvfm $i.jar manifest servers/$i*.class  common/*.class
		mv $i.jar ../mlab/
	done

	rm manifest
	cd ..

elif [ $1 = "-d" ]; then
	for n in `cat $node`
	do
	#	if [ $n = "mlab3.atl01.measurement-lab.org" ];then
	#		echo "this one"
	#	else
	#		continue
	#	fi
		
		if [ $n = "mobiperf.com" ]; then
			user="hjx"
			port=22
			echo "For mobiperf.com, you need to go to server and do bash end.sh => bash start.sh because need to type in password for sudo, not for Mlab nodes"
			ip=`dig +short $n`
		else
			user="michigan_1"
			port=806
			n=1.michigan.$n
			ip=`dig +short @alfred.cs.princeton.edu $n`
		fi

		ping=`ping -c 2 -W 2 $ip | grep " 0.0\% packet loss" | wc -l`
		if [ $ping = "1" ]; then
			echo $n "($ip)  on"
		else
			echo $n "($ip) off"
			continue
		fi
		echo "Deploy"
		if [ $2 = "-e" ]; then
			ssh -o "StrictHostKeyChecking no" -p $port -l $user $ip 'bash ~/mobiperf/end.sh'
		elif [ $2 = "-i" ]; then
			ssh -o "StrictHostKeyChecking no" -p $port -l $user $ip 'sudo yum -y install java' &
		else
			ssh -o "StrictHostKeyChecking no" -p $port -l $user $n 'mkdir ~/mobiperf'
			scp -o "StrictHostKeyChecking no" -P $port  -r mlab/* $user@$ip:~/mobiperf
			#first terminate
			ssh -o "StrictHostKeyChecking no" -p $port -l $user $ip 'bash ~/mobiperf/end.sh'
			ssh -o "StrictHostKeyChecking no" -p $port -l $user $ip 'bash ~/mobiperf/start.sh' &
		fi
		echo $n " ($ip) done"
	done
elif [ $1 = "-t" ];then
        ps aux | grep "measurement-lab.org" | awk '{system("sudo kill -9 " $2);}'
        ps aux | grep "mobiperf.com" | awk '{system("sudo kill -9 " $2);}'
        ps aux | grep "michigan_1" | awk '{system("sudo kill -9 " $2);}'
else
	echo "Usage: compile -c; deploy -d; terminate remotely -d -e; install java -d -i; kill all local process -t"
fi
