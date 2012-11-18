#!/bin/sh

uudecode eapon1.puu

echo -n test eapon1...
../tcpdump -t -N -r eapon1.pcap > eapon1.new
if diff eapon1.new eapon1.out
then
	echo passed.
else
	echo failed.
fi

