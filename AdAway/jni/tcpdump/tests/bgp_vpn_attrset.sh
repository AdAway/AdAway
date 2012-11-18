#!/bin/sh

echo -n test bgp_vpn_attrset...
if (../tcpdump -t -n -v -r bgp_vpn_attrset.pcap | diff -w -  bgp_vpn_attrset.out)
then
	echo passed.
else
	echo failed.
fi
	

