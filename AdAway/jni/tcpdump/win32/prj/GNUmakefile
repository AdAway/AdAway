# Makefile for cygwin gcc
# Nate Lawson <nate@rootlabs.oom>

# Location of your pcap src tree, build it first
PCAP_DIR = ../../../winpcap

# OPTFLAGS = -g
OPTFLAGS = -O
# -O2 may break things. Use at your own risk.

CFLAGS = -I ${PCAP_DIR}/wpcap/libpcap/bpf \
	-I ${PCAP_DIR}/wpcap/libpcap \
	-I ${PCAP_DIR}/wpcap/libpcap/Win32/Include \
	-I ${PCAP_DIR}/wpcap/libpcap/Win32/Include/net \
	-I ../../Win32/Include -I ../../linux-Include \
	-I ../../lbl -I../.. \
	-DWIN32 -DINET6 -DHAVE_ADDRINFO=1 -DHAVE_SOCKADDR_STORAGE=1 \
	-DHAVE_PCAP_LIST_DATALINKS=1 -DHAVE_PCAP_SET_DATALINK=1 \
	-DHAVE_PCAP_DATALINK_NAME_TO_VAL=1 \
	-DHAVE_PCAP_DATALINK_VAL_TO_DESCRIPTION=1 \
	-DHAVE_PCAP_DUMP_FTELL=1 -DHAVE_BPF_DUMP=1 \
	-DHAVE_PCAP_DUMP_FLUSH=1 -DHAVE_PCAP_FINDALLDEVS=1 \
	-DHAVE_PCAP_IF_T=1 -DHAVE_PCAP_LIB_VERSION=1 \
	-D_U_="__attribute__((unused))" \
	-D_WIN32_WINNT=0x0501 \
	-mno-cygwin ${OPTFLAGS}
LDFLAGS = 
LIBS = -L ${PCAP_DIR}/WPCAP/LIB -lwpcap -lws2_32
OBJS = \
	../../addrtoname.o \
	../../af.o \
	../../checksum.o \
	../../gmpls.o \
	../../gmt2local.o \
	../../missing/inet_aton.o \
	../../missing/inet_ntop.o \
	../../missing/strlcpy.o \
	../../missing/dlnames.o \
	../../missing/datalinks.o \
	../../missing/strsep.o \
	../../missing/inet_pton.o \
	../../machdep.o \
	../../oui.o \
	../../parsenfsfh.o \
	../../print-802_11.o \
	../../print-ah.o \
	../../print-aodv.o \
	../../print-ap1394.o \
	../../print-arcnet.o \
	../../print-arp.o \
	../../print-ascii.o \
	../../print-atalk.o \
	../../print-atm.o \
	../../print-beep.o \
	../../print-bfd.o \
	../../print-bgp.o \
	../../print-bootp.o \
	../../print-cdp.o \
	../../print-cfm.o \
	../../print-chdlc.o \
	../../print-cip.o \
	../../print-cnfp.o \
	../../print-decnet.o \
	../../print-dhcp6.o \
	../../print-domain.o \
	../../print-dtp.o \
	../../print-dvmrp.o \
	../../print-egp.o \
	../../print-enc.o \
	../../print-esp.o \
	../../print-ether.o \
	../../print-fddi.o \
	../../print-fr.o \
	../../print-frag6.o \
	../../print-gre.o \
	../../print-hsrp.o \
	../../print-icmp.o \
	../../print-icmp6.o \
	../../print-igmp.o \
	../../print-igrp.o \
	../../print-ip.o \
	../../print-ip6.o \
	../../print-ip6opts.o \
	../../print-ipcomp.o \
	../../print-ipfc.o \
	../../print-ipx.o \
	../../print-isakmp.o \
	../../print-isoclns.o \
	../../print-krb.o \
	../../print-l2tp.o \
	../../print-lane.o \
	../../print-ldp.o \
	../../print-lldp.o \
	../../print-llc.o \
	../../print-lwapp.o \
	../../print-lwres.o \
	../../print-mobile.o \
	../../print-mobility.o \
	../../print-mpcp.o \
	../../print-mpls.o \
	../../print-msdp.o \
	../../print-nfs.o \
	../../print-ntp.o \
	../../print-null.o \
	../../print-olsr.o \
	../../print-ospf.o \
	../../print-ospf6.o \
	../../print-pim.o \
	../../print-pgm.o \
	../../print-ppp.o \
	../../print-pppoe.o \
	../../print-pptp.o \
	../../print-radius.o \
	../../print-raw.o \
	../../print-rrcp.o \
	../../print-rip.o \
	../../print-ripng.o \
	../../print-rsvp.o \
	../../print-rt6.o \
	../../print-rx.o \
	../../print-sctp.o \
	../../print-sflow.o \
	../../print-sl.o \
	../../print-sll.o \
	../../print-slow.o \
	../../print-smb.o \
	../../print-snmp.o \
	../../print-stp.o \
	../../print-sunatm.o \
	../../print-sunrpc.o \
	../../print-symantec.o \
	../../print-tcp.o \
	../../print-telnet.o \
	../../print-tftp.o \
	../../print-timed.o \
	../../print-token.o \
	../../print-udld.o \
	../../print-udp.o \
	../../print-vjc.o \
	../../print-vqp.o \
	../../print-vrrp.o \
        ../../print-vtp.o \
	../../print-wb.o \
	../../print-zephyr.o \
	../../setsignal.o \
	../../smbutil.o \
	../../tcpdump.o \
	../../util.o \
	../../Win32/src/getopt.o \
	../../cpack.o \
	../../ipproto.o \
	../../l2vpn.o \
	../../nlpid.o \
	../../print-eigrp.o \
	../../print-juniper.o \
	../../print-lspping.o \
	../../print-sip.o \
	../../print-eap.o \
	../../print-lmp.o \
	../../print-syslog.o \
	../../print-dccp.o \
	../../print-bt.o \
	../../signature.o

main: ${OBJS}
	${CC} ${CFLAGS} ${LDFLAGS} -o windump.exe ${OBJS} ${LIBS}

install: windump.exe
	cp windump.exe c:/windows

clean:
	rm -f ${OBJS} windump.exe

.c.o:
	${CC} ${CFLAGS} -o $*.o -c $<
