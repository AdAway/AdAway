#!/bin/sh

uudecode print-flags.puu

for i in x xx X XX A AA; do
	if (../tcpdump -$i -s0 -nr print-flags.pcap | tee print-$i.new | diff - print-$i.out)
	then
		echo print-$i passed.
	else
		echo print-$i failed.
	fi
done
