#!/bin/sh

uudecode isakmp-identification-segfault.puu

echo -n test isakmp3...
../tcpdump -t -v -n -r isakmp-identification-segfault.pcap > isakmp3.new
if diff isakmp3.new isakmp3.out
then
	echo passed.
else
	echo failed.
fi

