#!/bin/bash
exec >> /var/log/mobiperf 2>&1
echo "####### Running /home/michigan_1/init/initialize.sh at `date` ########"

set -e
# install java JDK
yum -y install java-1.7.0-openjdk.i686
if [ $? -ne 0 ] 
then
    echo "Fail to install java 1.7. Please try again"
    exit 1
else
    echo "Success install java 1.7 package."
fi
