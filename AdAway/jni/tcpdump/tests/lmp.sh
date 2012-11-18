#!/bin/sh

uudecode lmp.puu

echo -n test lmp ...
../tcpdump -t -n -v -r lmp.pcap >lmp.new
if diff lmp.new lmp.out
then
	echo passed.
else
	echo failed.
fi
	

