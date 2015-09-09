LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

libpcap_PSRC =	pcap-linux.c
libpcap_FSRC =  fad-gifc.c
libpcap_CSRC =	pcap.c inet.c gencode.c optimize.c nametoaddr.c etherent.c \
	savefile.c sf-pcap.c sf-pcap-ng.c pcap-common.c \
	bpf_image.c bpf_dump.c
libpcap_GENSRC = scanner.c grammar.c bpf_filter.c version.c

libpcap_SRC =	$(libpcap_PSRC) $(libpcap_FSRC) $(libpcap_CSRC) $(libpcap_GENSRC)


LOCAL_SRC_FILES:=\
	$(libpcap_SRC)

LOCAL_CFLAGS:=-O2 -g
LOCAL_CFLAGS+=-DHAVE_CONFIG_H -D_U_="__attribute__((unused))" -Dlinux -D__GLIBC__ -D_GNU_SOURCE

LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)

LOCAL_MODULE:= libpcap

include $(BUILD_STATIC_LIBRARY)
