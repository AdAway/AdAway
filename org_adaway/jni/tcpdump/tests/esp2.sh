#!/bin/sh

uudecode 08-sunrise-sunset-esp2.puu

echo -n test esp2...
../tcpdump -t -n -E "0x12345678@192.1.2.45 3des-cbc-hmac96:0x43434545464649494a4a4c4c4f4f51515252545457575840,0xabcdabcd@192.0.1.1 3des-cbc-hmac96:0x434545464649494a4a4c4c4f4f5151525254545757584043" -r 08-sunrise-sunset-esp2.pcap >esp2.new
if diff esp2.new esp2.out
then
	echo passed.
else
	echo failed.
fi

