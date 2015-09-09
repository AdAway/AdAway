#!/bin/sh

# NFLOG support depends on both DLT_NFLOG and working <pcap/nflog.h>

if grep '^#define HAVE_PCAP_NFLOG_H 1$' ../config.h >/dev/null
then
  ./TESTonce nflog-e nflog.pcap nflog-e.out '-t -e'
else
	printf '    %-30s: TEST SKIPPED (compiled w/o NFLOG)\n' 'nflog-e'
fi
