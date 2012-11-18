#!/bin/sh

uudecode isakmp-delete-segfault.puu

echo -n test isakmp1...
if (../tcpdump -t -n -r isakmp-delete-segfault.pcap | diff - isakmp1.out)
then
	echo passed.
else
	echo failed.
fi

