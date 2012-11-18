#!/bin/sh

uudecode 08-sunrise-sunset-esp2.puu

echo -n test esp4...
../tcpdump -t -n -E "file esp-secrets.txt" -r 08-sunrise-sunset-esp2.pcap >esp4.new
if diff esp4.new esp2.out
then
	echo passed.
else
	echo failed.
fi

