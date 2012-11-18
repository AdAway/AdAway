#!/bin/sh

echo -n test esp0...
uudecode 02-sunrise-sunset-esp.puu
if (../tcpdump -t -n -r 02-sunrise-sunset-esp.pcap | diff - esp0.out)
then
	echo passed.
else
	echo failed.
fi
	

