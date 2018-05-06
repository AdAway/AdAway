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

#define ESRC(ep) ((ep)->ether_shost)
#define EDST(ep) ((ep)->ether_dhost)

#ifndef NTOHL
#define NTOHL(x)	(x) = ntohl(x)
#define NTOHS(x)	(x) = ntohs(x)
#define HTONL(x)	(x) = htonl(x)
#define HTONS(x)	(x) = htons(x)
#endif
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
#define TTEST2(var, l) \
	((uintptr_t)snapend - (l) <= (uintptr_t)snapend && \
	   (uintptr_t)&(var) <= (uintptr_t)snapend - (l))

/* True if "var" was captured */
#define TTEST(var) TTEST2(var, sizeof(var))

/* Bail if "l" bytes of "var" were not captured */
#define TCHECK2(var, l) if (!TTEST2(var, l)) goto trunc

/* Bail if "var" was not captured */
#define TCHECK(var) TCHECK2(var, sizeof(var))

extern int mask2plen(uint32_t);
extern const char *tok2strary_internal(const char **, int, const char *, int);
#define	tok2strary(a,f,i) tok2strary_internal(a, sizeof(a)/sizeof(a[0]),f,i)

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

extern const char *dnname_string(u_short);
extern const char *dnnum_string(u_short);

/* checksum routines */
extern void init_checksum(void);
extern uint16_t verify_crc10_cksum(uint16_t, const u_char *, int);
extern uint16_t create_osi_cksum(const uint8_t *, int, int);

/* The printer routines. */

#include <pcap.h>

extern char *smb_errstr(int, int);
extern const char *nt_errstr(uint32_t);

#ifdef INET6
extern int mask62plen(const u_char *);
#endif /*INET6*/

struct cksum_vec {
	const uint8_t	*ptr;
	int		len;
};
extern uint16_t in_cksum(const struct cksum_vec *, int);
extern uint16_t in_cksum_shouldbe(uint16_t, uint16_t);

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

extern void default_print(const u_char *, u_int);

#endif
