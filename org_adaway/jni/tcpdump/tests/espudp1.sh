#!/bin/sh

uudecode espudp1.puu

echo -n test espudp1...
../tcpdump -t -n -E "file esp-secrets.txt" -r espudp1.pcap >espudp1.new
if diff espudp1.new espudp1.out
then
	echo passed.
else
	echo failed.
fi

