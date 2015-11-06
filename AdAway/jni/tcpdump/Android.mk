LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

tcpdump_CSRC =	addrtoname.c af.c checksum.c cpack.c gmpls.c oui.c gmt2local.c ipproto.c \
        nlpid.c l2vpn.c machdep.c parsenfsfh.c in_cksum.c \
	print-802_11.c print-802_15_4.c print-ap1394.c print-ah.c print-ahcp.c \
	print-arcnet.c print-aodv.c print-aoe.c print-arp.c print-ascii.c print-atalk.c \
	print-atm.c print-beep.c print-bfd.c print-bgp.c \
	print-bootp.c print-bt.c print-calm-fast.c print-carp.c print-cdp.c print-cfm.c \
	print-chdlc.c print-cip.c print-cnfp.c print-dccp.c print-decnet.c \
	print-domain.c print-dtp.c print-dvmrp.c print-enc.c print-egp.c \
	print-eap.c print-eigrp.c \
	print-esp.c print-ether.c print-fddi.c print-forces.c print-fr.c print-ftp.c \
	print-geneve.c print-geonet.c print-gre.c print-hsrp.c print-http.c print-icmp.c print-igmp.c \
	print-igrp.c print-ip.c print-ipcomp.c print-ipfc.c print-ipnet.c \
	print-ipx.c print-isoclns.c print-juniper.c print-krb.c \
	print-l2tp.c print-lane.c print-ldp.c print-lldp.c print-llc.c \
    print-lmp.c print-loopback.c print-lspping.c print-lwapp.c print-lwres.c \
	print-m3ua.c print-mobile.c print-mpcp.c print-mpls.c print-mptcp.c print-msdp.c \
	print-msnlb.c print-nflog.c print-nfs.c print-ntp.c print-null.c \
	print-olsr.c print-openflow.c print-openflow-1.0.c print-ospf.c \
	print-pgm.c print-pim.c print-pktap.c\
	print-ppi.c print-ppp.c print-pppoe.c print-pptp.c \
	print-radius.c print-raw.c print-rip.c print-rpki-rtr.c print-rrcp.c print-rsvp.c \
	print-rtsp.c print-rx.c print-sctp.c print-sflow.c print-sip.c print-sl.c print-sll.c \
	print-slow.c print-smtp.c print-snmp.c print-stp.c print-sunatm.c print-sunrpc.c \
	print-symantec.c print-syslog.c print-tcp.c print-telnet.c print-tftp.c \
	print-timed.c print-tipc.c print-token.c print-udld.c print-udp.c \
	print-usb.c print-vjc.c print-vqp.c print-vrrp.c print-vtp.c \
	print-wb.c print-zephyr.c print-zeromq.c print-vxlan.c print-otv.c signature.c setsignal.c tcpdump.c util.c

tcpdump_LIBNETDISSECT_SRC=print-isakmp.c
tcpdump_LOCALSRC = print-ip6.c print-ip6opts.c print-mobility.c print-ripng.c print-icmp6.c print-frag6.c print-rt6.c print-ospf6.c print-dhcp6.c print-babel.c print-smb.c smbutil.c 
tcpdump_GENSRC = version.c

LOCAL_SRC_FILES:=\
	$(tcpdump_CSRC) $(tcpdump_LIBNETDISSECT_SRC) $(tcpdump_LOCALSRC) $(tcpdump_GENSRC)

LOCAL_CFLAGS := -O2 -g -pie -fPIE
LOCAL_LDFLAGS += -pie -fPIE
LOCAL_CFLAGS += -DHAVE_CONFIG_H -D_U_="__attribute__((unused))"

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/missing\
	$(LOCAL_PATH)/../libpcap

#LOCAL_SHARED_LIBRARIES += libssl libcrypto

LOCAL_STATIC_LIBRARIES += libpcap

LOCAL_MODULE_PATH := $(TARGET_OUT_OPTIONAL_EXECUTABLES)

LOCAL_MODULE_TAGS := eng

LOCAL_MODULE := tcpdump

include $(BUILD_EXECUTABLE)
