#!/bin/sh

uudecode isakmp-pointer-loop.puu

echo -n test isakmp2...
if (../tcpdump -t -n -r isakmp-pointer-loop.pcap | diff - isakmp2.out)
then
	echo passed.
else
	echo failed.
fi

