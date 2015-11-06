/*
 * Copyright (c) 1988-1997
 *	The Regents of the University of California.  All rights reserved.
 *
 * Copyright (c) 1998-2012  Michael Richardson <mcr@tcpdump.org>
 *      The TCPDUMP project
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
 */

#ifndef netdissect_h
#define netdissect_h

#ifdef HAVE_OS_PROTO_H
#include "os-proto.h"
#endif
#include <sys/types.h>

#ifndef HAVE___ATTRIBUTE__
#define __attribute__(x)
#endif

/* snprintf et al */

#include <stdarg.h>

#include "ip.h" /* struct ip for nextproto4_cksum() */

#if !defined(HAVE_SNPRINTF)
int snprintf (char *str, size_t sz, const char *format, ...)
#ifdef __ATTRIBUTE___FORMAT_OK
     __attribute__((format (printf, 3, 4)))
#endif /* __ATTRIBUTE___FORMAT_OK */
     ;
#endif /* !defined(HAVE_SNPRINTF) */

#if !defined(HAVE_VSNPRINTF)
int vsnprintf (char *str, size_t sz, const char *format, va_list ap)
#ifdef __ATTRIBUTE___FORMAT_OK
     __attribute__((format (printf, 3, 0)))
#endif /* __ATTRIBUTE___FORMAT_OK */
     ;
#endif /* !defined(HAVE_SNPRINTF) */

#ifndef HAVE_STRLCAT
extern size_t strlcat (char *, const char *, size_t);
#endif
#ifndef HAVE_STRLCPY
extern size_t strlcpy (char *, const char *, size_t);
#endif

#ifndef HAVE_STRDUP
extern char *strdup (const char *str);
#endif

#ifndef HAVE_STRSEP
extern char *strsep(char **, const char *);
#endif

struct tok {
	u_int v;		/* value */
	const char *s;		/* string */
};

#define TOKBUFSIZE 128
extern const char *tok2strbuf(const struct tok *, const char *, u_int,
			      char *buf, size_t bufsize);

/* tok2str is deprecated */
extern const char *tok2str(const struct tok *, const char *, u_int);
extern char *bittok2str(const struct tok *, const char *, u_int);
extern char *bittok2str_nosep(const struct tok *, const char *, u_int);


typedef struct netdissect_options netdissect_options;

struct netdissect_options {
  int ndo_aflag;		/* translate network and broadcast addresses */
  int ndo_bflag;		/* print 4 byte ASes in ASDOT notation */
  int ndo_eflag;		/* print ethernet header */
  int ndo_fflag;		/* don't translate "foreign" IP address */
  int ndo_Kflag;		/* don't check TCP checksums */
  int ndo_nflag;		/* leave addresses as numbers */
  int ndo_Nflag;		/* remove domains from printed host names */
  int ndo_qflag;		/* quick (shorter) output */
  int ndo_Rflag;		/* print sequence # field in AH/ESP*/
  int ndo_sflag;		/* use the libsmi to translate OIDs */
  int ndo_Sflag;		/* print raw TCP sequence numbers */
  int ndo_tflag;		/* print packet arrival time */
  int ndo_Uflag;		/* "unbuffered" output of dump files */
  int ndo_uflag;		/* Print undecoded NFS handles */
  int ndo_vflag;		/* verbose */
  int ndo_xflag;		/* print packet in hex */
  int ndo_Xflag;		/* print packet in hex/ascii */
  int ndo_Aflag;		/* print packet only in ascii observing TAB,
				 * LF, CR and SPACE as graphical chars
				 */
  int ndo_Bflag;		/* buffer size */
  int ndo_Iflag;		/* rfmon (monitor) mode */
  int ndo_Oflag;                /* run filter code optimizer */
  int ndo_dlt;                  /* if != -1, ask libpcap for the DLT it names*/
  int ndo_jflag;                /* packet time stamp source */
  int ndo_pflag;                /* don't go promiscuous */
  int ndo_immediate;            /* use immediate mode */

  int ndo_Cflag;                /* rotate dump files after this many bytes */
  int ndo_Cflag_count;      /* Keep track of which file number we're writing */
  int ndo_Gflag;            /* rotate dump files after this many seconds */
  int ndo_Gflag_count;      /* number of files created with Gflag rotation */
  time_t ndo_Gflag_time;    /* The last time_t the dump file was rotated. */
  int ndo_Wflag;          /* recycle output files after this number of files */
  int ndo_WflagChars;
  int ndo_Hflag;		/* dissect 802.11s draft mesh standard */
  int ndo_packet_number;	/* print a packet number in the beginning of line */
  int ndo_suppress_default_print; /* don't use default_print() for unknown packet types */
  int ndo_tstamp_precision;   /* requested time stamp precision */
  const char *ndo_dltname;

  char *ndo_espsecret;
  struct sa_list *ndo_sa_list_head;  /* used by print-esp.c */
  struct sa_list *ndo_sa_default;

  char *ndo_sigsecret;     	/* Signature verification secret key */

  struct esp_algorithm *ndo_espsecret_xform;   /* cache of decoded  */
  char                 *ndo_espsecret_key;

  int   ndo_packettype;	/* as specified by -T */

  char *ndo_program_name;	/*used to generate self-identifying messages */

  int32_t ndo_thiszone;	/* seconds offset from gmt to local time */

  int   ndo_snaplen;

  /*global pointers to beginning and end of current packet (during printing) */
  const u_char *ndo_packetp;
  const u_char *ndo_snapend;

  /* bookkeeping for ^T output */
  int ndo_infodelay;

  /* pointer to void function to output stuff */
  void (*ndo_default_print)(netdissect_options *,
  		      register const u_char *bp, register u_int length);

  /* pointer to function to print ^T output */
  void (*ndo_info)(netdissect_options *, int verbose);

  /* pointer to function to do regular output */
  int  (*ndo_printf)(netdissect_options *,
		     const char *fmt, ...)
#ifdef __ATTRIBUTE___FORMAT_OK_FOR_FUNCTION_POINTERS
		     __attribute__ ((format (printf, 2, 3)))
#endif
		     ;
  /* pointer to function to output errors */
  void (*ndo_error)(netdissect_options *,
		    const char *fmt, ...)
#ifdef __ATTRIBUTE___NORETURN_OK_FOR_FUNCTION_POINTERS
		     __attribute__ ((noreturn))
#endif /* __ATTRIBUTE___NORETURN_OK_FOR_FUNCTION_POINTERS */
#ifdef __ATTRIBUTE___FORMAT_OK_FOR_FUNCTION_POINTERS
		     __attribute__ ((format (printf, 2, 3)))
#endif /* __ATTRIBUTE___FORMAT_OK_FOR_FUNCTION_POINTERS */
		     ;
  /* pointer to function to output warnings */
  void (*ndo_warning)(netdissect_options *,
		      const char *fmt, ...)
#ifdef __ATTRIBUTE___FORMAT_OK_FOR_FUNCTION_POINTERS
		     __attribute__ ((format (printf, 2, 3)))
#endif
		     ;
};

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

/*
 * Maximum snapshot length.  This should be enough to capture the full
 * packet on most network interfaces.
 *
 *
 * Somewhat arbitrary, but chosen to be:
 *
 *    1) big enough for maximum-size Linux loopback packets (65549)
 *       and some USB packets captured with USBPcap:
 *
 *           http://desowin.org/usbpcap/
 *
 *       (> 131072, < 262144)
 *
 * and
 *
 *    2) small enough not to cause attempts to allocate huge amounts of
 *       memory; some applications might use the snapshot length in a
 *       savefile header to control the size of the buffer they allocate,
 *       so a size of, say, 2^31-1 might not work well.
 *
 * XXX - does it need to be bigger still?
 */
#define MAXIMUM_SNAPLEN	262144

/*
 * The default snapshot length is the maximum.
 */
#define DEFAULT_SNAPLEN	MAXIMUM_SNAPLEN

#define ESRC(ep) ((ep)->ether_shost)
#define EDST(ep) ((ep)->ether_dhost)

#ifndef NTOHL
#define NTOHL(x)	(x) = ntohl(x)
#define NTOHS(x)	(x) = ntohs(x)
#define HTONL(x)	(x) = htonl(x)
#define HTONS(x)	(x) = htons(x)
#endif

/*
 * True if "l" bytes of "var" were captured.
 *
 * The "ndo->ndo_snapend - (l) <= ndo->ndo_snapend" checks to make sure
 * "l" isn't so large that "ndo->ndo_snapend - (l)" underflows.
 *
 * The check is for <= rather than < because "l" might be 0.
 *
 * We cast the pointers to uintptr_t to make sure that the compiler
 * doesn't optimize away any of these tests (which it is allowed to
 * do, as adding an integer to, or subtracting an integer from, a
 * pointer assumes that the pointer is a pointer to an element of an
 * array and that the result of the addition or subtraction yields a
 * pointer to another member of the array, so that, for example, if
 * you subtract a positive integer from a pointer, the result is
 * guaranteed to be less than the original pointer value). See
 *
 *	http://www.kb.cert.org/vuls/id/162289
 */
#define ND_TTEST2(var, l) \
  ((l) >= 0 && \
	((uintptr_t)ndo->ndo_snapend - (l) <= (uintptr_t)ndo->ndo_snapend && \
         (uintptr_t)&(var) <= (uintptr_t)ndo->ndo_snapend - (l)))

/* True if "var" was captured */
#define ND_TTEST(var) ND_TTEST2(var, sizeof(var))

/* Bail if "l" bytes of "var" were not captured */
#define ND_TCHECK2(var, l) if (!ND_TTEST2(var, l)) goto trunc

/* Bail if "var" was not captured */
#define ND_TCHECK(var) ND_TCHECK2(var, sizeof(var))

#define ND_PRINT(STUFF) (*ndo->ndo_printf)STUFF
#define ND_DEFAULTPRINT(ap, length) (*ndo->ndo_default_print)(ndo, ap, length)

extern void ts_print(netdissect_options *, const struct timeval *);
extern void relts_print(netdissect_options *, int);

extern int fn_print(netdissect_options *, const u_char *, const u_char *);
extern int fn_printn(netdissect_options *, const u_char *, u_int, const u_char *);
extern int fn_printzp(netdissect_options *, const u_char *, u_int, const u_char *);

/*
 * Flags for txtproto_print().
 */
#define RESP_CODE_SECOND_TOKEN	0x00000001	/* response code is second token in response line */

extern void txtproto_print(netdissect_options *, const u_char *, u_int,
    const char *, const char **, u_int);

#if 0
extern char *read_infile(netdissect_options *, char *);
extern char *copy_argv(netdissect_options *, char **);
#endif

/*
 * Locale-independent macros for testing character properties and
 * stripping the 8th bit from characters.  Assumed to be handed
 * a value between 0 and 255, i.e. don't hand them a char, as
 * those might be in the range -128 to 127.
 */
#define ND_ISASCII(c)	(!((c) & 0x80))	/* value is an ASCII code point */
#define ND_ISPRINT(c)	((c) >= 0x20 && (c) <= 0x7E)
#define ND_ISGRAPH(c)	((c) > 0x20 && (c) <= 0x7E)
#define ND_TOASCII(c)	((c) & 0x7F)

extern void safeputchar(netdissect_options *, const u_char);
extern void safeputs(netdissect_options *, const u_char *, const u_int);

#ifdef LBL_ALIGN
/*
 * The processor doesn't natively handle unaligned loads,
 * and the compiler might "helpfully" optimize memcpy()
 * and memcmp(), when handed pointers that would normally
 * be properly aligned, into sequences that assume proper
 * alignment.
 *
 * Do copies and compares of possibly-unaligned data by
 * calling routines that wrap memcpy() and memcmp(), to
 * prevent that optimization.
 */
extern void unaligned_memcpy(void *, const void *, size_t);
extern int unaligned_memcmp(const void *, const void *, size_t);
#define UNALIGNED_MEMCPY(p, q, l)	unaligned_memcpy((p), (q), (l))
#define UNALIGNED_MEMCMP(p, q, l)	unaligned_memcmp((p), (q), (l))
#else
/*
 * The procesor natively handles unaligned loads, so just use memcpy()
 * and memcmp(), to enable those optimizations.
 */
#define UNALIGNED_MEMCPY(p, q, l)	memcpy((p), (q), (l))
#define UNALIGNED_MEMCMP(p, q, l)	memcmp((p), (q), (l))
#endif

#define PLURAL_SUFFIX(n) \
	(((n) != 1) ? "s" : "")

#if 0
extern const char *dnname_string(netdissect_options *, u_short);
extern const char *dnnum_string(netdissect_options *, u_short);
#endif

/* The printer routines. */

#include <pcap.h>

extern char *q922_string(netdissect_options *ndo, const u_char *, u_int);

typedef u_int (*if_ndo_printer)(struct netdissect_options *ndo,
				const struct pcap_pkthdr *, const u_char *);
typedef u_int (*if_printer)(const struct pcap_pkthdr *, const u_char *);

extern if_ndo_printer lookup_ndo_printer(int);
extern if_printer lookup_printer(int);

extern void eap_print(netdissect_options *,const u_char *, u_int);
extern int esp_print(netdissect_options *,
		     const u_char *bp, const int length, const u_char *bp2,
		     int *nhdr, int *padlen);
extern void arp_print(netdissect_options *,const u_char *, u_int, u_int);
extern void tipc_print(netdissect_options *, const u_char *, u_int, u_int);
extern void msnlb_print(netdissect_options *, const u_char *);
extern void icmp6_print(netdissect_options *ndo, const u_char *,
                        u_int, const u_char *, int);
extern void isakmp_print(netdissect_options *,const u_char *,
			 u_int, const u_char *);
extern void isakmp_rfc3948_print(netdissect_options *,const u_char *,
				 u_int, const u_char *);
extern void ip_print(netdissect_options *,const u_char *, u_int);
extern void ip_print_inner(netdissect_options *ndo,
			   const u_char *bp, u_int length, u_int nh,
			   const u_char *bp2);
extern void rrcp_print(netdissect_options *,const u_char *, u_int);
extern void loopback_print(netdissect_options *, const u_char *, const u_int);
extern void carp_print(netdissect_options *, const u_char *, u_int, int);

extern void ether_print(netdissect_options *,
                        const u_char *, u_int, u_int,
                        void (*)(netdissect_options *, const u_char *),
                        const u_char *);

extern u_int ether_if_print(netdissect_options *,
                            const struct pcap_pkthdr *,const u_char *);
extern u_int netanalyzer_if_print(netdissect_options *,
                                  const struct pcap_pkthdr *,const u_char *);
extern u_int netanalyzer_transparent_if_print(netdissect_options *,
                                              const struct pcap_pkthdr *,
                                              const u_char *);

extern int ethertype_print(netdissect_options *,u_short, const u_char *,
			     u_int, u_int);

extern int print_unknown_data(netdissect_options *,const u_char *, const char *,int);
extern void ascii_print(netdissect_options *, const u_char *, u_int);
extern void hex_print_with_offset(netdissect_options *, const char *ident, const u_char *cp,
				  u_int, u_int);
extern void hex_print(netdissect_options *,const char *ident, const u_char *cp,u_int);
extern void hex_and_ascii_print_with_offset(netdissect_options *, const char *, const u_char *, u_int, u_int);
extern void hex_and_ascii_print(netdissect_options *, const char *, const u_char *, u_int);

extern int ah_print(netdissect_options *, register const u_char *);
extern void beep_print(netdissect_options *, const u_char *, u_int);
extern void dtp_print(netdissect_options *, const u_char *, u_int);
extern u_int cip_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern int ipcomp_print(netdissect_options *, register const u_char *, int *);
extern u_int ipfc_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern void udld_print(netdissect_options *, const u_char *, u_int);
extern void hsrp_print(netdissect_options *, const u_char *, u_int);
extern void igrp_print(netdissect_options *, const u_char *, u_int);
extern void msdp_print(netdissect_options *, const u_char *, u_int);
extern u_int null_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern void mobile_print(netdissect_options *, const u_char *, u_int);
extern u_int ap1394_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int bt_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern void lane_print(netdissect_options *, const u_char *, u_int, u_int);
extern u_int lane_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern void otv_print(netdissect_options *, const u_char *, u_int);
extern void ahcp_print(netdissect_options *, const u_char *, const u_int);
extern void vxlan_print(netdissect_options *, const u_char *, u_int);
extern u_int arcnet_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int arcnet_linux_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern void bfd_print(netdissect_options *, const u_char *, u_int, u_int);
extern void gre_print(netdissect_options *, const u_char *, u_int);
extern int vjc_print(netdissect_options *, register const char *, u_short);
extern void ipN_print(netdissect_options *, const u_char *, u_int);
extern u_int raw_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int usb_linux_48_byte_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int usb_linux_64_byte_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int symantec_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int chdlc_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int chdlc_print(netdissect_options *, register const u_char *, u_int);
extern void zmtp1_print(netdissect_options *, const u_char *, u_int);
extern void zmtp1_print_datagram(netdissect_options *, const u_char *, const u_int);
extern void ipx_print(netdissect_options *, const u_char *, u_int);
extern void mpls_print(netdissect_options *, const u_char *, u_int);
extern u_int pppoe_print(netdissect_options *, const u_char *, u_int);
extern u_int pppoe_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern void sunrpcrequest_print(netdissect_options *, const u_char *, u_int, const u_char *);
extern u_int pflog_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int token_print(netdissect_options *, const u_char *, u_int, u_int);
extern u_int token_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern void vqp_print(netdissect_options *, register const u_char *, register u_int);
extern void zephyr_print(netdissect_options *, const u_char *, int);
extern void fddi_print(netdissect_options *, const u_char *, u_int, u_int);
extern u_int fddi_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern void mpcp_print(netdissect_options *, const u_char *, u_int);
extern void rpki_rtr_print(netdissect_options *, const u_char *, u_int);
extern u_int sll_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern void dccp_print(netdissect_options *, const u_char *, const u_char *, u_int);
extern int llc_print(netdissect_options *, const u_char *, u_int, u_int, const u_char *, const u_char *, u_short *);
extern int snap_print(netdissect_options *, const u_char *, u_int, u_int, u_int);
extern void eigrp_print(netdissect_options *, const u_char *, u_int);
extern void stp_print(netdissect_options *, const u_char *, u_int);
extern void l2tp_print(netdissect_options *, const u_char *, u_int);
extern void udp_print(netdissect_options *, const u_char *, u_int, const u_char *, int);
extern void icmp_print(netdissect_options *, const u_char *, u_int, const u_char *, int);
extern void openflow_print(netdissect_options *, const u_char *, const u_int);
extern void telnet_print(netdissect_options *, const u_char *, u_int);
extern void slow_print(netdissect_options *, const u_char *, u_int);
extern void radius_print(netdissect_options *, const u_char *, u_int);
extern void lmp_print(netdissect_options *, const u_char *, u_int);
extern u_int fr_print(netdissect_options *, register const u_char *, u_int);
extern u_int mfr_print(netdissect_options *, register const u_char *, u_int);
extern u_int fr_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int mfr_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern void q933_print(netdissect_options *, const u_char *, u_int);
extern void igmp_print(netdissect_options *, const u_char *, u_int);
extern void rip_print(netdissect_options *, const u_char *, u_int);
extern void lwapp_control_print(netdissect_options *, const u_char *, u_int, int);
extern void lwapp_data_print(netdissect_options *, const u_char *, u_int);
extern void pgm_print(netdissect_options *, const u_char *, u_int, const u_char *);
extern void pptp_print(netdissect_options *, const u_char *);
extern void ldp_print(netdissect_options *, const u_char *, u_int);
extern void wb_print(netdissect_options *, const void *, u_int);
extern int oam_print(netdissect_options *, const u_char *, u_int, u_int);
extern void atm_print(netdissect_options *, u_int, u_int, u_int, const u_char *, u_int, u_int);
extern u_int sunatm_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int atm_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern void vtp_print(netdissect_options *, const u_char *, u_int);
extern int mptcp_print(netdissect_options *, const u_char *, u_int, u_char);
extern void ntp_print(netdissect_options *, const u_char *, u_int);
extern void cnfp_print(netdissect_options *, const u_char *);
extern void dvmrp_print(netdissect_options *, const u_char *, u_int);
extern void egp_print(netdissect_options *, const u_char *, u_int);
extern u_int enc_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int sl_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int sl_bsdos_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern void tftp_print(netdissect_options *, const u_char *, u_int);
extern void vrrp_print(netdissect_options *, const u_char *, u_int, const u_char *, int);
extern void pimv1_print(netdissect_options *, const u_char *, u_int);
extern void cisco_autorp_print(netdissect_options *, const u_char *, u_int);
extern void pim_print(netdissect_options *, const u_char *, u_int, u_int);
extern const u_char * ns_nprint (netdissect_options *, register const u_char *, register const u_char *);
extern void ns_print(netdissect_options *, const u_char *, u_int, int);
extern void bootp_print(netdissect_options *, const u_char *, u_int);
extern void sflow_print(netdissect_options *, const u_char *, u_int);
extern void aodv_print(netdissect_options *, const u_char *, u_int, int);
extern void sctp_print(netdissect_options *, const u_char *, const u_char *, u_int);
extern char *bgp_vpn_rd_print (netdissect_options *, const u_char *);
extern void bgp_print(netdissect_options *, const u_char *, int);
extern void olsr_print(netdissect_options *, const u_char *, u_int, int);
extern void forces_print(netdissect_options *, const u_char *, u_int);
extern void lspping_print(netdissect_options *, const u_char *, u_int);
extern void isoclns_print(netdissect_options *, const u_char *, u_int, u_int);
extern void krb_print(netdissect_options *, const u_char *);
extern void cdp_print(netdissect_options *, const u_char *, u_int, u_int);
extern void atalk_print(netdissect_options *, const u_char *, u_int);
extern u_int ltalk_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int llap_print(netdissect_options *, const u_char *, u_int);
extern void aarp_print(netdissect_options *, const u_char *, u_int);
extern u_int juniper_atm1_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_atm2_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_mfr_print(netdissect_options *, const struct pcap_pkthdr *, register const u_char *);
extern u_int juniper_mlfr_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_mlppp_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_pppoe_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_pppoe_atm_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_ggsn_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_es_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_monitor_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_services_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_ether_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_ppp_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_frelay_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int juniper_chdlc_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern void snmp_print(netdissect_options *, const u_char *, u_int);
extern void rx_print(netdissect_options *, register const u_char *, int, int, int, u_char *);
extern void nfsreply_print(netdissect_options *, const u_char *, u_int, const u_char *);
extern void nfsreply_print_noaddr(netdissect_options *, const u_char *, u_int, const u_char *);
extern void nfsreq_print_noaddr(netdissect_options *, const u_char *, u_int, const u_char *);
extern void sip_print(netdissect_options *, const u_char *, u_int);
extern void syslog_print(netdissect_options *, const u_char *, u_int);
extern void lwres_print(netdissect_options *, const u_char *, u_int);
extern void cfm_print(netdissect_options *, const u_char *, u_int);
extern void nbt_tcp_print(netdissect_options *, const u_char *, int);
extern void nbt_udp137_print(netdissect_options *, const u_char *, int);
extern void nbt_udp138_print(netdissect_options *, const u_char *, int);
extern void smb_tcp_print(netdissect_options *, const u_char *, int);
extern void netbeui_print(netdissect_options *, u_short, const u_char *, int);
extern void ipx_netbios_print(netdissect_options *, const u_char *, u_int);
extern void print_data(netdissect_options *, const unsigned char *, int);
extern void decnet_print(netdissect_options *, const u_char *, u_int, u_int);
extern void tcp_print(netdissect_options *, const u_char *, u_int, const u_char *, int);
extern void ospf_print(netdissect_options *, const u_char *, u_int, const u_char *);
extern int ospf_print_te_lsa(netdissect_options *, const uint8_t *, u_int);
extern int ospf_print_grace_lsa(netdissect_options *, const uint8_t *, u_int);
extern u_int ppp_print(netdissect_options *, register const u_char *, u_int);
extern u_int ppp_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int ppp_hdlc_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int ppp_bsdos_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern void lldp_print(netdissect_options *, const u_char *, u_int);
extern void rsvp_print(netdissect_options *, const u_char *, u_int);
extern void timed_print(netdissect_options *, const u_char *);
extern void m3ua_print(netdissect_options *, const u_char *, const u_int);
extern void aoe_print(netdissect_options *, const u_char *, const u_int);
extern void ftp_print(netdissect_options *, const u_char *, u_int);
extern void http_print(netdissect_options *, const u_char *, u_int);
extern void rtsp_print(netdissect_options *, const u_char *, u_int);
extern void smtp_print(netdissect_options *, const u_char *, u_int);
extern void geneve_print(netdissect_options *, const u_char *, u_int);

/* stuff that has not yet been rototiled */

#if 0
extern void ascii_print(netdissect_options *,u_int);
extern void default_print(netdissect_options *,const u_char *, u_int);
extern char *smb_errstr(netdissect_options *,int, int);
extern const char *nt_errstr(netdissect_options *, uint32_t);
#endif

extern u_int ipnet_if_print(netdissect_options *,const struct pcap_pkthdr *, const u_char *);
extern u_int ppi_if_print(netdissect_options *,const struct pcap_pkthdr *, const u_char *);
extern u_int nflog_if_print(netdissect_options *,const struct pcap_pkthdr *, const u_char *);
extern u_int ieee802_15_4_if_print(netdissect_options *,const struct pcap_pkthdr *, const u_char *);
extern u_int pktap_if_print(netdissect_options *,const struct pcap_pkthdr *, const u_char *);
extern u_int ieee802_11_radio_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int ieee802_11_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int ieee802_11_radio_avs_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);
extern u_int prism_if_print(netdissect_options *, const struct pcap_pkthdr *, const u_char *);

extern void ip6_print(netdissect_options *,const u_char *, u_int);
#ifdef INET6
extern int frag6_print(netdissect_options *, const u_char *, const u_char *);
extern int rt6_print(netdissect_options *, const u_char *, const u_char *);
extern int hbhopt_print(netdissect_options *, const u_char *);
extern int dstopt_print(netdissect_options *, const u_char *);
extern void ripng_print(netdissect_options *, const u_char *, unsigned int);
extern int mobility_print(netdissect_options *, const u_char *, const u_char *);
extern void dhcp6_print(netdissect_options *, const u_char *, u_int);
extern void ospf6_print(netdissect_options *, const u_char *, u_int);
extern void babel_print(netdissect_options *, const u_char *, u_int);
#endif /*INET6*/

#if 0
struct cksum_vec {
	const uint8_t	*ptr;
	int		len;
};
extern uint16_t in_cksum(const struct cksum_vec *, int);
extern uint16_t in_cksum_shouldbe(uint16_t, uint16_t);
#endif
extern int nextproto4_cksum(netdissect_options *ndo, const struct ip *, const uint8_t *, u_int, u_int, u_int);
extern int decode_prefix4(netdissect_options *ndo, const u_char *, u_int, char *, u_int);
#ifdef INET6
extern int decode_prefix6(netdissect_options *ndo, const u_char *, u_int, char *, u_int);
#endif

extern void esp_print_decodesecret(netdissect_options *ndo);
extern int esp_print_decrypt_buffer_by_ikev2(netdissect_options *ndo,
					     int initiator,
					     u_char spii[8], u_char spir[8],
					     u_char *buf, u_char *end);


extern void geonet_print(netdissect_options *ndo,const u_char *eth_hdr,const u_char *geo_pck, u_int len);
extern void calm_fast_print(netdissect_options *ndo,const u_char *eth_hdr,const u_char *calm_pck, u_int len);

#endif  /* netdissect_h */
