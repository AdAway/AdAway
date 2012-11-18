#!/bin/sh

uudecode ospf-gmpls.puu 

echo -n test ospf-gmpls...
../tcpdump -t -n -v -r ospf-gmpls.pcap > ospf-gmpls.new
if diff ospf-gmpls.new ospf-gmpls.out
then
	echo passed.
else
	echo failed.
fi
	

