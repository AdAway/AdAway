#!/bin/sh

uudecode mpls-ldp-hello.puu

echo -n test mpls-ldp-hello ...
../tcpdump -t -n -v -r mpls-ldp-hello.pcap >mpls-ldp-hello.new
if diff mpls-ldp-hello.new mpls-ldp-hello.out
then
	echo passed.
else
	echo failed.
fi
	

