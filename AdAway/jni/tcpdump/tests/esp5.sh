#!/bin/sh

uudecode 08-sunrise-sunset-aes.puu

echo -n test esp5...
../tcpdump -t -n -E "file esp-secrets.txt" -r 08-sunrise-sunset-aes.pcap > esp5.new
if diff esp5.new esp5.out
then
	echo passed.
else
	echo failed.
fi

