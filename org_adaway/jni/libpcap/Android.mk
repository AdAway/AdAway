LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=\
	bpf_dump.c\
	bpf/net/bpf_filter.c\
	bpf_image.c\
	etherent.c\
	fad-gifc.c\
	gencode.c\
	grammar.c\
	inet.c\
	nametoaddr.c\
	optimize.c\
	pcap.c\
	pcap-linux.c\
	savefile.c\
	scanner.c\
	version.c

LOCAL_CFLAGS:=-O2 -g
LOCAL_CFLAGS+=-DHAVE_CONFIG_H -D_U_="__attribute__((unused))" -Dlinux -D__GLIBC__ -D_GNU_SOURCE

LOCAL_MODULE:= libpcap

include $(BUILD_STATIC_LIBRARY)
