#!/bin/sh

# Only attempt OpenSSL-specific tests when compiled with the library.

if grep '^#define HAVE_LIBCRYPTO 1$' ../config.h >/dev/null
then
	./TESTonce esp1 02-sunrise-sunset-esp.pcap esp1.out '-t -E "0x12345678@192.1.2.45 3des-cbc-hmac96:0x4043434545464649494a4a4c4c4f4f515152525454575758"'
	./TESTonce esp2 08-sunrise-sunset-esp2.pcap esp2.out '-t -E "0x12345678@192.1.2.45 3des-cbc-hmac96:0x43434545464649494a4a4c4c4f4f51515252545457575840,0xabcdabcd@192.0.1.1 3des-cbc-hmac96:0x434545464649494a4a4c4c4f4f5151525254545757584043"'
	./TESTonce esp3 02-sunrise-sunset-esp.pcap esp1.out '-t -E "3des-cbc-hmac96:0x4043434545464649494a4a4c4c4f4f515152525454575758"'
	# Reading the secret(s) from a file does not work with Capsicum.
	if grep '^#define HAVE_CAPSICUM 1$' ../config.h >/dev/null
	then
		FORMAT='    %-30s: TEST SKIPPED (compiled w/Capsicum)\n'
		printf "$FORMAT" esp4
		printf "$FORMAT" esp5
		printf "$FORMAT" espudp1
		printf "$FORMAT" ikev2pI2
		printf "$FORMAT" isakmp4
	else
		./TESTonce esp4 08-sunrise-sunset-esp2.pcap esp2.out '-t -E "file esp-secrets.txt"'
		./TESTonce esp5 08-sunrise-sunset-aes.pcap esp5.out '-t -E "file esp-secrets.txt"'
		./TESTonce espudp1 espudp1.pcap espudp1.out '-nnnn -t -E "file esp-secrets.txt"'
		./TESTonce ikev2pI2 ikev2pI2.pcap ikev2pI2.out '-t -E "file ikev2pI2-secrets.txt" -v -v -v -v'
		./TESTonce isakmp4 isakmp4500.pcap isakmp4.out '-t -E "file esp-secrets.txt"'
	fi
else
	FORMAT='    %-30s: TEST SKIPPED (compiled w/o OpenSSL)\n'
	printf "$FORMAT" esp1
	printf "$FORMAT" esp2
	printf "$FORMAT" esp3
	printf "$FORMAT" esp4
	printf "$FORMAT" esp5
	printf "$FORMAT" espudp1
	printf "$FORMAT" ikev2pI2
	printf "$FORMAT" isakmp4
fi
