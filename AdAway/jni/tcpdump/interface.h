/*
 * Copyright (c) 1988-2002
 *	The Regents of the University of California.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that: (1) source code distributions
 * retain the above copyright notice and this paragraph in its entirety, (2)
 * distributions including binary code include the above copyright notice and
 * this paragraph in its entirety in the documentation or other materials
 * provided with the distribution, and (3) all advertising materials mentioning
 * features or use of this software display the following acknowledgement:
 * ``This product includes software developed by the University of California,
 * Lawrence Berkeley Laboratory and its contributors.'' Neither the name of
 * the University nor the names of its contributors may be used to endorse
 * or promote products derived from this software without specific prior
 * written permission.
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 *
 * @(#) $Header: /tcpdump/master/tcpdump/interface.h,v 1.285 2008-08-16 11:36:20 hannes Exp $ (LBL)
 */

#ifndef tcpdump_interface_h
#define tcpdump_interface_h

#ifdef HAVE_OS_PROTO_H
#include "os-proto.h"
#endif

/* snprintf et al */

#include <stdarg.h>

#if HAVE_STDINT_H
#include <stdint.h>
#endif

#if !defined(HAVE_SNPRINTF)
int snprintf(char *, size_t, const char *, ...)
#ifdef __ATTRIBUTE___FORMAT_OK
     __attribute__((format(printf, 3, 4)))
#endif /* __ATTRIBUTE___FORMAT_OK */
     ;
#endif /* !defined(HAVE_SNPRINTF) */

#if !defined(HAVE_VSNPRINTF)
int vsnprintf(char *, size_t, const char *, va_list)
#ifdef __ATTRIBUTE___FORMAT_OK
     __attribute__((format(printf, 3, 0)))
#endif /* __ATTRIBUTE___FORMAT_OK */
     ;
#endif /* !defined(HAVE_VSNPRINTF) */

#ifndef HAVE_STRLCAT
extern size_t strlcat(char *, const char *, size_t);
#endif
#ifndef HAVE_STRLCPY
extern size_t strlcpy(char *, const char *, size_t);
#endif

#ifndef HAVE_STRDUP
extern char *strdup(const char *);
#endif

#ifndef HAVE_STRSEP
extern char *strsep(char **, const char *);
#endif

#define PT_VAT		1	/* Visual Audio Tool */
#define PT_WB		2	/* distributed White Board */
#define PT_RPC		3	/* Remote Procedure Call */
#define PT_RTP		4	/* Real-Time Applications protocol */
#define PT_RTCP		5	/* Real-Time Applications control protocol */
#define PT_SNMP		6	/* Simple Network Management Protocol */
#define PT_CNFP		7	/* Cisco NetFlow protocol */
#define PT_TFTP		8	/* trivial file transfer protocol */
#define PT_AODV		9	/* Ad-hoc On-demand Distance Vector Protocol */
#define PT_CARP		10	/* Common Address Redundancy Protocol */
#define PT_RADIUS	11	/* RADIUS authentication Protocol */
#define PT_ZMTP1	12	/* ZeroMQ Message Transport Protocol 1.0 */
#define PT_VXLAN	13	/* Virtual eXtensible Local Area Network */
#define PT_PGM		14	/* [UDP-encapsulated] Pragmatic General Multicast */
#define PT_PGM_ZMTP1	15	/* ZMTP/1.0 inside PGM (native or UDP-encapsulated) */
#define PT_LMP		16	/* Link Management Protocol */

#ifndef min
#define min(a,b) ((a)>(b)?(b):(a))
#endif
#ifndef max
#define max(a,b) ((b)>(a)?(b):(a))
#endif

#define ESRC(ep) ((ep)->ether_shost)
#define EDST(ep) ((ep)->ether_dhost)

#ifndef NTOHL
#define NTOHL(x)	(x) = ntohl(x)
#define NTOHS(x)	(x) = ntohs(x)
#define HTONL(x)	(x) = htonl(x)
#define HTONS(x)	(x) = htons(x)
#endif
#endif

#ifndef MIN
#define MIN(a,b) ((a)<(b)?(a):(b))
#endif

extern char *program_name;	/* used to generate self-identifying messages */

extern int32_t thiszone;	/* seconds offset from gmt to local time */

/*
 * True if  "l" bytes of "var" were captured.
 *
 * The "snapend - (l) <= snapend" checks to make sure "l" isn't so large
 * that "snapend - (l)" underflows.
 *
 * The check is for <= rather than < because "l" might be 0.
 */
#define TTEST2(var, l) (snapend - (l) <= snapend && \
			(const u_char *)&(var) <= snapend - (l))

/* True if "var" was captured */
#define TTEST(var) TTEST2(var, sizeof(var))

/* Bail if "l" bytes of "var" were not captured */
#define TCHECK2(var, l) if (!TTEST2(var, l)) goto trunc

/* Bail if "var" was not captured */
#define TCHECK(var) TCHECK2(var, sizeof(var))

extern void ts_print(const struct timeval *);
extern void relts_print(int);

extern int fn_print(const u_char *, const u_char *);
extern int fn_printn(const u_char *, u_int, const u_char *);
extern int fn_printzp(const u_char *, u_int, const u_char *);
extern int mask2plen(u_int32_t);
extern const char *tok2strary_internal(const char **, int, const char *, int);
#define	tok2strary(a,f,i) tok2strary_internal(a, sizeof(a)/sizeof(a[0]),f,i)

extern const char *dnaddr_string(u_short);

extern void error(const char *, ...)
     __attribute__((noreturn))
#ifdef __ATTRIBUTE___FORMAT_OK
     __attribute__((format (printf, 1, 2)))
#endif /* __ATTRIBUTE___FORMAT_OK */
     ;
extern void warning(const char *, ...)
#ifdef __ATTRIBUTE___FORMAT_OK
     __attribute__((format (printf, 1, 2)))
#endif /* __ATTRIBUTE___FORMAT_OK */
     ;

extern char *read_infile(char *);
extern char *copy_argv(char **);

extern void safeputchar(int);
extern void safeputs(const char *, int);

extern const char *isonsap_string(const u_char *, register u_int);
extern const char *protoid_string(const u_char *);
extern const char *ipxsap_string(u_short);
extern const char *dnname_string(u_short);
extern const char *dnnum_string(u_short);

/* checksum routines */
extern void init_checksum(void);
extern u_int16_t verify_crc10_cksum(u_int16_t, const u_char *, int);
extern u_int16_t create_osi_cksum(const u_int8_t *, int, int);

/* The printer routines. */

#include <pcap.h>

extern int print_unknown_data(const u_char *, const char *,int);
extern void ascii_print(const u_char *, u_int);
extern void hex_and_ascii_print_with_offset(const char *, const u_char *,
	u_int, u_int);
extern void hex_and_ascii_print(const char *, const u_char *, u_int);
extern void hex_print_with_offset(const char *, const u_char *, u_int, u_int);
extern void hex_print(const char *, const u_char *, u_int);
extern void telnet_print(const u_char *, u_int);
extern int llc_print(const u_char *, u_int, u_int, const u_char *,
	const u_char *, u_short *);
extern int snap_print(const u_char *, u_int, u_int, u_int);
extern void aarp_print(const u_char *, u_int);
extern void aodv_print(const u_char *, u_int, int);
extern void atalk_print(const u_char *, u_int);
extern void atm_print(u_int, u_int, u_int, const u_char *, u_int, u_int);
extern u_int atm_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int sunatm_if_print(const struct pcap_pkthdr *, const u_char *);
extern int oam_print(const u_char *, u_int, u_int);
extern void bootp_print(const u_char *, u_int);
extern void bgp_print(const u_char *, int);
extern void beep_print(const u_char *, u_int);
extern void cnfp_print(const u_char *, const u_char *);
extern void decnet_print(const u_char *, u_int, u_int);
extern void default_print(const u_char *, u_int);
extern void dvmrp_print(const u_char *, u_int);
extern void egp_print(const u_char *, u_int);
extern u_int enc_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int pflog_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int arcnet_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int arcnet_linux_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int token_print(const u_char *, u_int, u_int);
extern u_int token_if_print(const struct pcap_pkthdr *, const u_char *);
extern void fddi_print(const u_char *, u_int, u_int);
extern u_int fddi_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int fr_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int mfr_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int fr_print(register const u_char *, u_int);
extern u_int mfr_print(register const u_char *, u_int);
extern char *q922_string(const u_char *);
extern u_int ieee802_11_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int ieee802_11_radio_if_print(const struct pcap_pkthdr *,
	const u_char *);
extern u_int ap1394_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int ieee802_11_radio_avs_if_print(const struct pcap_pkthdr *,
	const u_char *);
extern void gre_print(const u_char *, u_int);
extern void icmp_print(const u_char *, u_int, const u_char *, int);
extern void igmp_print(const u_char *, u_int);
extern void igrp_print(const u_char *, u_int, const u_char *);
extern void ipN_print(const u_char *, u_int);
extern u_int ipfc_if_print(const struct pcap_pkthdr *, const u_char *);
extern void ipx_print(const u_char *, u_int);
extern void isoclns_print(const u_char *, u_int, u_int);
extern void krb_print(const u_char *);
extern u_int llap_print(const u_char *, u_int);
extern u_int ltalk_if_print(const struct pcap_pkthdr *, const u_char *);
extern void msdp_print(const unsigned char *, u_int);
extern void nfsreply_print(const u_char *, u_int, const u_char *);
extern void nfsreply_print_noaddr(const u_char *, u_int, const u_char *);
extern void nfsreq_print(const u_char *, u_int, const u_char *);
extern void nfsreq_print_noaddr(const u_char *, u_int, const u_char *);
extern void ns_print(const u_char *, u_int, int);
extern const u_char * ns_nprint (register const u_char *, register const u_char *);
extern void ntp_print(const u_char *, u_int);
extern u_int null_if_print(const struct pcap_pkthdr *, const u_char *);
extern void openflow_print(const u_char *, u_int);
extern void ospf_print(const u_char *, u_int, const u_char *);
extern void olsr_print (const u_char *, u_int, int);
extern void pimv1_print(const u_char *, u_int);
extern void cisco_autorp_print(const u_char *, u_int);
extern void rsvp_print(const u_char *, u_int);
extern void ldp_print(const u_char *, u_int);
extern void lldp_print(const u_char *, u_int);
extern void rpki_rtr_print(const u_char *, u_int);
extern void lmp_print(const u_char *, u_int);
extern void lspping_print(const u_char *, u_int);
extern void lwapp_control_print(const u_char *, u_int, int);
extern void lwapp_data_print(const u_char *, u_int);
extern void eigrp_print(const u_char *, u_int);
extern void mobile_print(const u_char *, u_int);
extern void pim_print(const u_char *, u_int, u_int);
extern u_int pppoe_print(const u_char *, u_int);
extern u_int ppp_print(register const u_char *, u_int);
extern u_int ppp_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int ppp_hdlc_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int ppp_bsdos_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int pppoe_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int prism_if_print(const struct pcap_pkthdr *, const u_char *);
extern void q933_print(const u_char *, u_int);
extern int vjc_print(register const char *, u_short);
extern void vqp_print(register const u_char *, register u_int);
extern u_int raw_if_print(const struct pcap_pkthdr *, const u_char *);
extern void rip_print(const u_char *, u_int);
extern u_int sl_if_print(const struct pcap_pkthdr *, const u_char *);
extern void lane_print(const u_char *, u_int, u_int);
extern u_int lane_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int cip_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int sl_bsdos_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int chdlc_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int chdlc_print(register const u_char *, u_int);
extern u_int juniper_atm1_print(const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_atm2_print(const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_mfr_print(const struct pcap_pkthdr *, register const u_char *);
extern u_int juniper_mlfr_print(const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_mlppp_print(const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_pppoe_print(const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_pppoe_atm_print(const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_ggsn_print(const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_es_print(const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_monitor_print(const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_services_print(const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_ether_print(const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_ppp_print(const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_frelay_print(const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_chdlc_print(const struct pcap_pkthdr *, const u_char *);
extern u_int sll_if_print(const struct pcap_pkthdr *, const u_char *);
extern void snmp_print(const u_char *, u_int);
extern void sunrpcrequest_print(const u_char *, u_int, const u_char *);
extern u_int symantec_if_print(const struct pcap_pkthdr *, const u_char *);
extern void tcp_print(const u_char *, u_int, const u_char *, int);
extern void tftp_print(const u_char *, u_int);
extern void timed_print(const u_char *);
extern void udld_print(const u_char *, u_int);
extern void udp_print(const u_char *, u_int, const u_char *, int);
extern void vtp_print(const u_char *, u_int);
extern void wb_print(const void *, u_int);
extern int ah_print(register const u_char *);
extern int ipcomp_print(register const u_char *, int *);
extern void rx_print(register const u_char *, int, int, int, u_char *);
extern void netbeui_print(u_short, const u_char *, int);
extern void ipx_netbios_print(const u_char *, u_int);
extern void nbt_tcp_print(const u_char *, int);
extern void nbt_udp137_print(const u_char *, int);
extern void nbt_udp138_print(const u_char *, int);
extern void smb_tcp_print(const u_char *, int);
extern char *smb_errstr(int, int);
extern const char *nt_errstr(u_int32_t);
extern void print_data(const unsigned char *, int);
extern void l2tp_print(const u_char *, u_int);
extern void vrrp_print(const u_char *, u_int, int);
extern void carp_print(const u_char *, u_int, int);
extern void slow_print(const u_char *, u_int);
extern void sflow_print(const u_char *, u_int);
extern void mpcp_print(const u_char *, u_int);
extern void cfm_print(const u_char *, u_int);
extern void pgm_print(const u_char *, u_int, const u_char *);
extern void cdp_print(const u_char *, u_int, u_int);
extern void dtp_print(const u_char *, u_int);
extern void stp_print(const u_char *, u_int);
extern void radius_print(const u_char *, u_int);
extern void lwres_print(const u_char *, u_int);
extern void pptp_print(const u_char *);
extern void dccp_print(const u_char *, const u_char *, u_int);
extern void sctp_print(const u_char *, const u_char *, u_int);
extern void forces_print(const u_char *, u_int);
extern void mpls_print(const u_char *, u_int);
extern void mpls_lsp_ping_print(const u_char *, u_int);
extern void zephyr_print(const u_char *, int);
extern void zmtp1_print(const u_char *, u_int);
extern void zmtp1_print_datagram(const u_char *, u_int);
extern void hsrp_print(const u_char *, u_int);
extern void bfd_print(const u_char *, u_int, u_int);
extern void sip_print(const u_char *, u_int);
extern void syslog_print(const u_char *, u_int);
extern int mptcp_print(const u_char *, u_int, u_char);
extern u_int bt_if_print(const struct pcap_pkthdr *, const u_char *);
extern u_int usb_linux_48_byte_print(const struct pcap_pkthdr *, const u_char *);
extern u_int usb_linux_64_byte_print(const struct pcap_pkthdr *, const u_char *);
extern void vxlan_print(const u_char *, u_int);
extern void otv_print(const u_char *, u_int);


#ifdef INET6
extern void ip6_opt_print(const u_char *, int);
extern int hbhopt_print(const u_char *);
extern int dstopt_print(const u_char *);
extern int frag6_print(const u_char *, const u_char *);
extern int mobility_print(const u_char *, const u_char *);
extern void ripng_print(const u_char *, unsigned int);
extern int rt6_print(const u_char *, const u_char *);
extern void ospf6_print(const u_char *, u_int);
extern void dhcp6_print(const u_char *, u_int);
extern void babel_print(const u_char *, u_int);
extern int mask62plen(const u_char *);
#endif /*INET6*/

struct cksum_vec {
	const u_int8_t	*ptr;
	int		len;
};
extern u_int16_t in_cksum(const struct cksum_vec *, int);
extern u_int16_t in_cksum_shouldbe(u_int16_t, u_int16_t);

#ifndef HAVE_BPF_DUMP
struct bpf_program;

extern void bpf_dump(const struct bpf_program *, int);

#endif

#include "netdissect.h"

/* forward compatibility */

#ifndef NETDISSECT_REWORKED
extern netdissect_options *gndo;

#define bflag gndo->ndo_bflag 
#define eflag gndo->ndo_eflag 
#define fflag gndo->ndo_fflag 
#define jflag gndo->ndo_jflag
#define Kflag gndo->ndo_Kflag 
#define nflag gndo->ndo_nflag 
#define Nflag gndo->ndo_Nflag 
#define Oflag gndo->ndo_Oflag 
#define pflag gndo->ndo_pflag 
#define qflag gndo->ndo_qflag 
#define Rflag gndo->ndo_Rflag 
#define sflag gndo->ndo_sflag 
#define Sflag gndo->ndo_Sflag 
#define tflag gndo->ndo_tflag 
#define Uflag gndo->ndo_Uflag 
#define uflag gndo->ndo_uflag 
#define vflag gndo->ndo_vflag 
#define xflag gndo->ndo_xflag 
#define Xflag gndo->ndo_Xflag 
#define Cflag gndo->ndo_Cflag 
#define Gflag gndo->ndo_Gflag 
#define Aflag gndo->ndo_Aflag 
#define Bflag gndo->ndo_Bflag 
#define Iflag gndo->ndo_Iflag 
#define suppress_default_print gndo->ndo_suppress_default_print
#define packettype gndo->ndo_packettype
#define sigsecret gndo->ndo_sigsecret
#define Wflag gndo->ndo_Wflag
#define WflagChars gndo->ndo_WflagChars
#define Cflag_count gndo->ndo_Cflag_count
#define Gflag_count gndo->ndo_Gflag_count
#define Gflag_time gndo->ndo_Gflag_time 
#define Hflag gndo->ndo_Hflag
#define snaplen     gndo->ndo_snaplen
#define snapend     gndo->ndo_snapend

#endif
