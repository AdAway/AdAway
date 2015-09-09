/*
 * Copyright (c) 1988, 1989, 1990, 1991, 1992, 1993, 1994, 1995, 1996, 1997, 2000
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
 * Support for splitting captures into multiple files with a maximum
 * file size:
 *
 * Copyright (c) 2001
 *	Seth Webster <swebster@sst.ll.mit.edu>
 */

#ifndef lint
static const char copyright[] _U_ =
    "@(#) Copyright (c) 1988, 1989, 1990, 1991, 1992, 1993, 1994, 1995, 1996, 1997, 2000\n\
The Regents of the University of California.  All rights reserved.\n";
#endif

/*
 * tcpdump - monitor tcp/ip traffic on an ethernet.
 *
 * First written in 1987 by Van Jacobson, Lawrence Berkeley Laboratory.
 * Mercilessly hacked and occasionally improved since then via the
 * combined efforts of Van, Steve McCanne and Craig Leres of LBL.
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

/*
 * Mac OS X may ship pcap.h from libpcap 0.6 with a libpcap based on
 * 0.8.  That means it has pcap_findalldevs() but the header doesn't
 * define pcap_if_t, meaning that we can't actually *use* pcap_findalldevs().
 */
#ifdef HAVE_PCAP_FINDALLDEVS
#ifndef HAVE_PCAP_IF_T
#undef HAVE_PCAP_FINDALLDEVS
#endif
#endif

#include <tcpdump-stdinc.h>

#ifdef WIN32
#include "w32_fzs.h"
extern int strcasecmp (const char *__s1, const char *__s2);
extern int SIZE_BUF;
#define off_t long
#define uint UINT
#endif /* WIN32 */

#ifdef USE_LIBSMI
#include <smi.h>
#endif

#ifdef HAVE_LIBCRYPTO
#include <openssl/crypto.h>
#endif

#ifdef HAVE_GETOPT_LONG
#include <getopt.h>
#else
#include "getopt_long.h"
#endif
/* Capsicum-specific code requires macros from <net/bpf.h>, which will fail
 * to compile if <pcap.h> has already been included; including the headers
 * in the opposite order works fine.
 */
#ifdef HAVE_CAPSICUM
#include <sys/capability.h>
#include <sys/ioccom.h>
#include <net/bpf.h>
#include <fcntl.h>
#include <libgen.h>
#endif	/* HAVE_CAPSICUM */
#include <pcap.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>
#ifndef WIN32
#include <sys/wait.h>
#include <sys/resource.h>
#include <pwd.h>
#include <grp.h>
#endif /* WIN32 */

/* capabilities convenience library */
/* If a code depends on HAVE_LIBCAP_NG, it depends also on HAVE_CAP_NG_H.
 * If HAVE_CAP_NG_H is not defined, undefine HAVE_LIBCAP_NG.
 * Thus, the later tests are done only on HAVE_LIBCAP_NG.
 */
#ifdef HAVE_LIBCAP_NG
#ifdef HAVE_CAP_NG_H
#include <cap-ng.h>
#else
#undef HAVE_LIBCAP_NG
#endif /* HAVE_CAP_NG_H */
#endif /* HAVE_LIBCAP_NG */

#include "netdissect.h"
#include "interface.h"
#include "addrtoname.h"
#include "machdep.h"
#include "setsignal.h"
#include "gmt2local.h"
#include "pcap-missing.h"

#ifndef PATH_MAX
#define PATH_MAX 1024
#endif

#ifdef SIGINFO
#define SIGNAL_REQ_INFO SIGINFO
#elif SIGUSR1
#define SIGNAL_REQ_INFO SIGUSR1
#endif

netdissect_options Gndo;
netdissect_options *gndo = &Gndo;

static int Dflag;			/* list available devices and exit */
static int dflag;			/* print filter code */
static int Lflag;			/* list available data link types and exit */
#ifdef HAVE_PCAP_SET_TSTAMP_TYPE
static int Jflag;			/* list available time stamp types */
#endif
#ifdef HAVE_PCAP_SETDIRECTION
int Qflag = -1;				/* restrict captured packet by send/receive direction */
#endif
static char *zflag = NULL;		/* compress each savefile using a specified command (like gzip or bzip2) */

static int infodelay;
static int infoprint;

char *program_name;

int32_t thiszone;		/* seconds offset from gmt to local time */

/* Forwards */
static RETSIGTYPE cleanup(int);
static RETSIGTYPE child_cleanup(int);
static void print_version(void);
static void print_usage(void);
static void show_dlts_and_exit(const char *device, pcap_t *pd) __attribute__((noreturn));

static void print_packet(u_char *, const struct pcap_pkthdr *, const u_char *);
static void ndo_default_print(netdissect_options *, const u_char *, u_int);
static void dump_packet_and_trunc(u_char *, const struct pcap_pkthdr *, const u_char *);
static void dump_packet(u_char *, const struct pcap_pkthdr *, const u_char *);
static void droproot(const char *, const char *);
static void ndo_error(netdissect_options *ndo, const char *fmt, ...)
     __attribute__((noreturn))
#ifdef __ATTRIBUTE___FORMAT_OK
     __attribute__((format (printf, 2, 3)))
#endif /* __ATTRIBUTE___FORMAT_OK */
    ;
static void ndo_warning(netdissect_options *ndo, const char *fmt, ...)
#ifdef __ATTRIBUTE___FORMAT_OK
     __attribute__((format (printf, 2, 3)))
#endif /* __ATTRIBUTE___FORMAT_OK */
    ;

#ifdef SIGNAL_REQ_INFO
RETSIGTYPE requestinfo(int);
#endif

#if defined(USE_WIN32_MM_TIMER)
  #include <MMsystem.h>
  static UINT timer_id;
  static void CALLBACK verbose_stats_dump(UINT, UINT, DWORD_PTR, DWORD_PTR, DWORD_PTR);
#elif defined(HAVE_ALARM)
  static void verbose_stats_dump(int sig);
#endif

static void info(int);
static u_int packets_captured;

struct printer {
        if_printer f;
	int type;
};


struct ndo_printer {
        if_ndo_printer f;
	int type;
};


static const struct printer printers[] = {
	{ NULL,			0 },
};

static const struct ndo_printer ndo_printers[] = {
	{ ether_if_print,	DLT_EN10MB },
#ifdef DLT_IPNET
	{ ipnet_if_print,	DLT_IPNET },
#endif
#ifdef DLT_IEEE802_15_4
	{ ieee802_15_4_if_print, DLT_IEEE802_15_4 },
#endif
#ifdef DLT_IEEE802_15_4_NOFCS
	{ ieee802_15_4_if_print, DLT_IEEE802_15_4_NOFCS },
#endif
#ifdef DLT_PPI
	{ ppi_if_print,		DLT_PPI },
#endif
#ifdef DLT_NETANALYZER
	{ netanalyzer_if_print, DLT_NETANALYZER },
#endif
#ifdef DLT_NETANALYZER_TRANSPARENT
	{ netanalyzer_transparent_if_print, DLT_NETANALYZER_TRANSPARENT },
#endif
#if defined(DLT_NFLOG) && defined(HAVE_PCAP_NFLOG_H)
	{ nflog_if_print,	DLT_NFLOG},
#endif
#ifdef DLT_CIP
	{ cip_if_print,         DLT_CIP },
#endif
#ifdef DLT_ATM_CLIP
	{ cip_if_print,		DLT_ATM_CLIP },
#endif
#ifdef DLT_IP_OVER_FC
	{ ipfc_if_print,	DLT_IP_OVER_FC },
#endif
	{ null_if_print,	DLT_NULL },
#ifdef DLT_LOOP
	{ null_if_print,	DLT_LOOP },
#endif
#ifdef DLT_APPLE_IP_OVER_IEEE1394
	{ ap1394_if_print,	DLT_APPLE_IP_OVER_IEEE1394 },
#endif
#if defined(DLT_BLUETOOTH_HCI_H4_WITH_PHDR) && defined(HAVE_PCAP_BLUETOOTH_H)
	{ bt_if_print,		DLT_BLUETOOTH_HCI_H4_WITH_PHDR},
#endif
#ifdef DLT_LANE8023
	{ lane_if_print,        DLT_LANE8023 },
#endif
	{ arcnet_if_print,	DLT_ARCNET },
#ifdef DLT_ARCNET_LINUX
	{ arcnet_linux_if_print, DLT_ARCNET_LINUX },
#endif
	{ raw_if_print,		DLT_RAW },
#ifdef DLT_IPV4
	{ raw_if_print,		DLT_IPV4 },
#endif
#ifdef DLT_IPV6
	{ raw_if_print,		DLT_IPV6 },
#endif
#ifdef HAVE_PCAP_USB_H
#ifdef DLT_USB_LINUX
	{ usb_linux_48_byte_print, DLT_USB_LINUX},
#endif /* DLT_USB_LINUX */
#ifdef DLT_USB_LINUX_MMAPPED
	{ usb_linux_64_byte_print, DLT_USB_LINUX_MMAPPED},
#endif /* DLT_USB_LINUX_MMAPPED */
#endif /* HAVE_PCAP_USB_H */
#ifdef DLT_SYMANTEC_FIREWALL
	{ symantec_if_print,	DLT_SYMANTEC_FIREWALL },
#endif
#ifdef DLT_C_HDLC
	{ chdlc_if_print,	DLT_C_HDLC },
#endif
#ifdef DLT_HDLC
	{ chdlc_if_print,	DLT_HDLC },
#endif
#ifdef DLT_PPP_ETHER
	{ pppoe_if_print,	DLT_PPP_ETHER },
#endif
#if defined(DLT_PFLOG) && defined(HAVE_NET_PFVAR_H)
	{ pflog_if_print,	DLT_PFLOG },
#endif
	{ token_if_print,	DLT_IEEE802 },
	{ fddi_if_print,	DLT_FDDI },
#ifdef DLT_LINUX_SLL
	{ sll_if_print,		DLT_LINUX_SLL },
#endif
#ifdef DLT_FR
	{ fr_if_print,		DLT_FR },
#endif
#ifdef DLT_FRELAY
	{ fr_if_print,		DLT_FRELAY },
#endif
#ifdef DLT_MFR
	{ mfr_if_print,		DLT_MFR },
#endif
	{ atm_if_print,		DLT_ATM_RFC1483 },
#ifdef DLT_SUNATM
	{ sunatm_if_print,	DLT_SUNATM },
#endif
#ifdef DLT_ENC
	{ enc_if_print,		DLT_ENC },
#endif
	{ sl_if_print,		DLT_SLIP },
#ifdef DLT_SLIP_BSDOS
	{ sl_bsdos_if_print,	DLT_SLIP_BSDOS },
#endif
#ifdef DLT_LTALK
	{ ltalk_if_print,	DLT_LTALK },
#endif
#ifdef DLT_JUNIPER_ATM1
	{ juniper_atm1_print,	DLT_JUNIPER_ATM1 },
#endif
#ifdef DLT_JUNIPER_ATM2
	{ juniper_atm2_print,	DLT_JUNIPER_ATM2 },
#endif
#ifdef DLT_JUNIPER_MFR
	{ juniper_mfr_print,	DLT_JUNIPER_MFR },
#endif
#ifdef DLT_JUNIPER_MLFR
	{ juniper_mlfr_print,	DLT_JUNIPER_MLFR },
#endif
#ifdef DLT_JUNIPER_MLPPP
	{ juniper_mlppp_print,	DLT_JUNIPER_MLPPP },
#endif
#ifdef DLT_JUNIPER_PPPOE
	{ juniper_pppoe_print,	DLT_JUNIPER_PPPOE },
#endif
#ifdef DLT_JUNIPER_PPPOE_ATM
	{ juniper_pppoe_atm_print, DLT_JUNIPER_PPPOE_ATM },
#endif
#ifdef DLT_JUNIPER_GGSN
	{ juniper_ggsn_print,	DLT_JUNIPER_GGSN },
#endif
#ifdef DLT_JUNIPER_ES
	{ juniper_es_print,	DLT_JUNIPER_ES },
#endif
#ifdef DLT_JUNIPER_MONITOR
	{ juniper_monitor_print, DLT_JUNIPER_MONITOR },
#endif
#ifdef DLT_JUNIPER_SERVICES
	{ juniper_services_print, DLT_JUNIPER_SERVICES },
#endif
#ifdef DLT_JUNIPER_ETHER
	{ juniper_ether_print,	DLT_JUNIPER_ETHER },
#endif
#ifdef DLT_JUNIPER_PPP
	{ juniper_ppp_print,	DLT_JUNIPER_PPP },
#endif
#ifdef DLT_JUNIPER_FRELAY
	{ juniper_frelay_print,	DLT_JUNIPER_FRELAY },
#endif
#ifdef DLT_JUNIPER_CHDLC
	{ juniper_chdlc_print,	DLT_JUNIPER_CHDLC },
#endif
#ifdef DLT_PKTAP
	{ pktap_if_print,	DLT_PKTAP },
#endif
#ifdef DLT_IEEE802_11_RADIO
	{ ieee802_11_radio_if_print,	DLT_IEEE802_11_RADIO },
#endif
#ifdef DLT_IEEE802_11
	{ ieee802_11_if_print,	DLT_IEEE802_11},
#endif
#ifdef DLT_IEEE802_11_RADIO_AVS
	{ ieee802_11_radio_avs_if_print,	DLT_IEEE802_11_RADIO_AVS },
#endif
#ifdef DLT_PRISM_HEADER
	{ prism_if_print,	DLT_PRISM_HEADER },
#endif
	{ ppp_if_print,		DLT_PPP },
#ifdef DLT_PPP_WITHDIRECTION
	{ ppp_if_print,		DLT_PPP_WITHDIRECTION },
#endif
#ifdef DLT_PPP_BSDOS
	{ ppp_bsdos_if_print,	DLT_PPP_BSDOS },
#endif
#ifdef DLT_PPP_SERIAL
	{ ppp_hdlc_if_print,	DLT_PPP_SERIAL },
#endif
	{ NULL,			0 },
};

static const struct tok status_flags[] = {
#ifdef PCAP_IF_UP
	{ PCAP_IF_UP,       "Up"       },
#endif
#ifdef PCAP_IF_RUNNING
	{ PCAP_IF_RUNNING,  "Running"  },
#endif
	{ PCAP_IF_LOOPBACK, "Loopback" },
	{ 0, NULL }
};

if_printer
lookup_printer(int type)
{
	const struct printer *p;

	for (p = printers; p->f; ++p)
		if (type == p->type)
			return p->f;

	return NULL;
	/* NOTREACHED */
}

if_ndo_printer
lookup_ndo_printer(int type)
{
	const struct ndo_printer *p;

	for (p = ndo_printers; p->f; ++p)
		if (type == p->type)
			return p->f;

#if defined(DLT_USER2) && defined(DLT_PKTAP)
	/*
	 * Apple incorrectly chose to use DLT_USER2 for their PKTAP
	 * header.
	 *
	 * We map DLT_PKTAP, whether it's DLT_USER2 as it is on Darwin-
	 * based OSes or the same value as LINKTYPE_PKTAP as it is on
	 * other OSes, to LINKTYPE_PKTAP, so files written with
	 * this version of libpcap for a DLT_PKTAP capture have a link-
	 * layer header type of LINKTYPE_PKTAP.
	 *
	 * However, files written on OS X Mavericks for a DLT_PKTAP
	 * capture have a link-layer header type of LINKTYPE_USER2.
	 * If we don't have a printer for DLT_USER2, and type is
	 * DLT_USER2, we look up the printer for DLT_PKTAP and use
	 * that.
	 */
	if (type == DLT_USER2) {
		for (p = ndo_printers; p->f; ++p)
			if (DLT_PKTAP == p->type)
				return p->f;
	}
#endif

	return NULL;
	/* NOTREACHED */
}

static pcap_t *pd;

static int supports_monitor_mode;

extern int optind;
extern int opterr;
extern char *optarg;

struct print_info {
        netdissect_options *ndo;
        union {
                if_printer     printer;
                if_ndo_printer ndo_printer;
        } p;
        int ndo_type;
};

struct dump_info {
	char	*WFileName;
	char	*CurrentFileName;
	pcap_t	*pd;
	pcap_dumper_t *p;
#ifdef HAVE_CAPSICUM
	int	dirfd;
#endif
};

#ifdef HAVE_PCAP_SET_TSTAMP_TYPE
static void
show_tstamp_types_and_exit(const char *device, pcap_t *pd)
{
	int n_tstamp_types;
	int *tstamp_types = 0;
	const char *tstamp_type_name;
	int i;

	n_tstamp_types = pcap_list_tstamp_types(pd, &tstamp_types);
	if (n_tstamp_types < 0)
		error("%s", pcap_geterr(pd));

	if (n_tstamp_types == 0) {
		fprintf(stderr, "Time stamp type cannot be set for %s\n",
		    device);
		exit(0);
	}
	fprintf(stderr, "Time stamp types for %s (use option -j to set):\n",
	    device);
	for (i = 0; i < n_tstamp_types; i++) {
		tstamp_type_name = pcap_tstamp_type_val_to_name(tstamp_types[i]);
		if (tstamp_type_name != NULL) {
			(void) fprintf(stderr, "  %s (%s)\n", tstamp_type_name,
			    pcap_tstamp_type_val_to_description(tstamp_types[i]));
		} else {
			(void) fprintf(stderr, "  %d\n", tstamp_types[i]);
		}
	}
	pcap_free_tstamp_types(tstamp_types);
	exit(0);
}
#endif

static void
show_dlts_and_exit(const char *device, pcap_t *pd)
{
	int n_dlts;
	int *dlts = 0;
	const char *dlt_name;

	n_dlts = pcap_list_datalinks(pd, &dlts);
	if (n_dlts < 0)
		error("%s", pcap_geterr(pd));
	else if (n_dlts == 0 || !dlts)
		error("No data link types.");

	/*
	 * If the interface is known to support monitor mode, indicate
	 * whether these are the data link types available when not in
	 * monitor mode, if -I wasn't specified, or when in monitor mode,
	 * when -I was specified (the link-layer types available in
	 * monitor mode might be different from the ones available when
	 * not in monitor mode).
	 */
	if (supports_monitor_mode)
		(void) fprintf(stderr, "Data link types for %s %s (use option -y to set):\n",
		    device,
		    Iflag ? "when in monitor mode" : "when not in monitor mode");
	else
		(void) fprintf(stderr, "Data link types for %s (use option -y to set):\n",
		    device);

	while (--n_dlts >= 0) {
		dlt_name = pcap_datalink_val_to_name(dlts[n_dlts]);
		if (dlt_name != NULL) {
			(void) fprintf(stderr, "  %s (%s)", dlt_name,
			    pcap_datalink_val_to_description(dlts[n_dlts]));

			/*
			 * OK, does tcpdump handle that type?
			 */
			if (lookup_printer(dlts[n_dlts]) == NULL
                            && lookup_ndo_printer(dlts[n_dlts]) == NULL)
				(void) fprintf(stderr, " (printing not supported)");
			fprintf(stderr, "\n");
		} else {
			(void) fprintf(stderr, "  DLT %d (printing not supported)\n",
			    dlts[n_dlts]);
		}
	}
#ifdef HAVE_PCAP_FREE_DATALINKS
	pcap_free_datalinks(dlts);
#endif
	exit(0);
}

#ifdef HAVE_PCAP_FINDALLDEVS
static void
show_devices_and_exit (void)
{
	pcap_if_t *devpointer;
	char ebuf[PCAP_ERRBUF_SIZE];
	int i;

	if (pcap_findalldevs(&devpointer, ebuf) < 0)
		error("%s", ebuf);
	else {
		for (i = 0; devpointer != NULL; i++) {
			printf("%d.%s", i+1, devpointer->name);
			if (devpointer->description != NULL)
				printf(" (%s)", devpointer->description);
			if (devpointer->flags != 0)
				printf(" [%s]", bittok2str(status_flags, "none", devpointer->flags));
			printf("\n");
			devpointer = devpointer->next;
		}
	}
	exit(0);
}
#endif /* HAVE_PCAP_FINDALLDEVS */

/*
 * Short options.
 *
 * Note that there we use all letters for short options except for g, k,
 * o, and P, and those are used by other versions of tcpdump, and we should
 * only use them for the same purposes that the other versions of tcpdump
 * use them:
 *
 * OS X tcpdump uses -g to force non--v output for IP to be on one
 * line, making it more "g"repable;
 *
 * OS X tcpdump uses -k tospecify that packet comments in pcap-ng files
 * should be printed;
 *
 * OpenBSD tcpdump uses -o to indicate that OS fingerprinting should be done
 * for hosts sending TCP SYN packets;
 *
 * OS X tcpdump uses -P to indicate that -w should write pcap-ng rather
 * than pcap files.
 *
 * OS X tcpdump also uses -Q to specify expressions that match packet
 * metadata, including but not limited to the packet direction.
 * The expression syntax is different from a simple "in|out|inout",
 * and those expressions aren't accepted by OS X tcpdump, but the
 * equivalents would be "in" = "dir=in", "out" = "dir=out", and
 * "inout" = "dir=in or dir=out", and the parser could conceivably
 * special-case "in", "out", and "inout" as expressions for backwards
 * compatibility, so all is not (yet) lost.
 */

/*
 * Set up flags that might or might not be supported depending on the
 * version of libpcap we're using.
 */
#if defined(HAVE_PCAP_CREATE) || defined(WIN32)
#define B_FLAG		"B:"
#define B_FLAG_USAGE	" [ -B size ]"
#else /* defined(HAVE_PCAP_CREATE) || defined(WIN32) */
#define B_FLAG
#define B_FLAG_USAGE
#endif /* defined(HAVE_PCAP_CREATE) || defined(WIN32) */

#ifdef HAVE_PCAP_CREATE
#define I_FLAG		"I"
#else /* HAVE_PCAP_CREATE */
#define I_FLAG
#endif /* HAVE_PCAP_CREATE */

#ifdef HAVE_PCAP_SET_TSTAMP_TYPE
#define j_FLAG		"j:"
#define j_FLAG_USAGE	" [ -j tstamptype ]"
#define J_FLAG		"J"
#else /* PCAP_ERROR_TSTAMP_TYPE_NOTSUP */
#define j_FLAG
#define j_FLAG_USAGE
#define J_FLAG
#endif /* PCAP_ERROR_TSTAMP_TYPE_NOTSUP */

#ifdef HAVE_PCAP_FINDALLDEVS
#define D_FLAG	"D"
#else
#define D_FLAG
#endif

#ifdef HAVE_PCAP_DUMP_FLUSH
#define U_FLAG	"U"
#else
#define U_FLAG
#endif

#ifdef HAVE_PCAP_SETDIRECTION
#define Q_FLAG "Q:"
#else
#define Q_FLAG
#endif

#define SHORTOPTS "aAb" B_FLAG "c:C:d" D_FLAG "eE:fF:G:hHi:" I_FLAG j_FLAG J_FLAG "KlLm:M:nNOpq" Q_FLAG "r:Rs:StT:u" U_FLAG "vV:w:W:xXy:Yz:Z:#"

/*
 * Long options.
 *
 * We do not currently have long options corresponding to all short
 * options; we should probably pick appropriate option names for them.
 *
 * However, the short options where the number of times the option is
 * specified matters, such as -v and -d and -t, should probably not
 * just map to a long option, as saying
 *
 *  tcpdump --verbose --verbose
 *
 * doesn't make sense; it should be --verbosity={N} or something such
 * as that.
 *
 * For long options with no corresponding short options, we define values
 * outside the range of ASCII graphic characters, make that the last
 * component of the entry for the long option, and have a case for that
 * option in the switch statement.
 */
#define OPTION_VERSION		128
#define OPTION_TSTAMP_PRECISION	129
#define OPTION_IMMEDIATE_MODE	130

static const struct option longopts[] = {
#if defined(HAVE_PCAP_CREATE) || defined(WIN32)
	{ "buffer-size", required_argument, NULL, 'B' },
#endif
	{ "list-interfaces", no_argument, NULL, 'D' },
	{ "help", no_argument, NULL, 'h' },
	{ "interface", required_argument, NULL, 'i' },
#ifdef HAVE_PCAP_CREATE
	{ "monitor-mode", no_argument, NULL, 'I' },
#endif
#ifdef HAVE_PCAP_SET_TSTAMP_TYPE
	{ "time-stamp-type", required_argument, NULL, 'j' },
	{ "list-time-stamp-types", no_argument, NULL, 'J' },
#endif
#ifdef HAVE_PCAP_SET_TSTAMP_PRECISION
	{ "time-stamp-precision", required_argument, NULL, OPTION_TSTAMP_PRECISION},
#endif
	{ "dont-verify-checksums", no_argument, NULL, 'K' },
	{ "list-data-link-types", no_argument, NULL, 'L' },
	{ "no-optimize", no_argument, NULL, 'O' },
	{ "no-promiscuous-mode", no_argument, NULL, 'p' },
#ifdef HAVE_PCAP_SETDIRECTION
	{ "direction", required_argument, NULL, 'Q' },
#endif
	{ "snapshot-length", required_argument, NULL, 's' },
	{ "absolute-tcp-sequence-numbers", no_argument, NULL, 'S' },
#ifdef HAVE_PCAP_DUMP_FLUSH
	{ "packet-buffered", no_argument, NULL, 'U' },
#endif
	{ "linktype", required_argument, NULL, 'y' },
#ifdef HAVE_PCAP_SET_IMMEDIATE_MODE
	{ "immediate-mode", no_argument, NULL, OPTION_IMMEDIATE_MODE },
#endif
#if defined(HAVE_PCAP_DEBUG) || defined(HAVE_YYDEBUG)
	{ "debug-filter-parser", no_argument, NULL, 'Y' },
#endif
	{ "relinquish-privileges", required_argument, NULL, 'Z' },
	{ "number", no_argument, NULL, '#' },
	{ "version", no_argument, NULL, OPTION_VERSION },
	{ NULL, 0, NULL, 0 }
};

#ifndef WIN32
/* Drop root privileges and chroot if necessary */
static void
droproot(const char *username, const char *chroot_dir)
{
	struct passwd *pw = NULL;

	if (chroot_dir && !username) {
		fprintf(stderr, "tcpdump: Chroot without dropping root is insecure\n");
		exit(1);
	}

	pw = getpwnam(username);
	if (pw) {
		if (chroot_dir) {
			if (chroot(chroot_dir) != 0 || chdir ("/") != 0) {
				fprintf(stderr, "tcpdump: Couldn't chroot/chdir to '%.64s': %s\n",
				    chroot_dir, pcap_strerror(errno));
				exit(1);
			}
		}
#ifdef HAVE_LIBCAP_NG
		int ret = capng_change_id(pw->pw_uid, pw->pw_gid, CAPNG_NO_FLAG);
		if (ret < 0) {
			fprintf(stderr, "error : ret %d\n", ret);
		}
		else {
			fprintf(stderr, "dropped privs to %s\n", username);
		}
#else
		if (initgroups(pw->pw_name, pw->pw_gid) != 0 ||
		    setgid(pw->pw_gid) != 0 || setuid(pw->pw_uid) != 0) {
			fprintf(stderr, "tcpdump: Couldn't change to '%.32s' uid=%lu gid=%lu: %s\n",
			    username,
			    (unsigned long)pw->pw_uid,
			    (unsigned long)pw->pw_gid,
			    pcap_strerror(errno));
			exit(1);
		}
		else {
			fprintf(stderr, "dropped privs to %s\n", username);
		}
#endif /* HAVE_LIBCAP_NG */
	}
	else {
		fprintf(stderr, "tcpdump: Couldn't find user '%.32s'\n",
		    username);
		exit(1);
	}
#ifdef HAVE_LIBCAP_NG
	/* We don't need CAP_SETUID and CAP_SETGID any more. */
	capng_updatev(
		CAPNG_DROP,
		CAPNG_EFFECTIVE | CAPNG_PERMITTED,
		CAP_SETUID,
		CAP_SETGID,
		-1);
	capng_apply(CAPNG_SELECT_BOTH);
#endif /* HAVE_LIBCAP_NG */

}
#endif /* WIN32 */

static int
getWflagChars(int x)
{
	int c = 0;

	x -= 1;
	while (x > 0) {
		c += 1;
		x /= 10;
	}

	return c;
}


static void
MakeFilename(char *buffer, char *orig_name, int cnt, int max_chars)
{
        char *filename = malloc(PATH_MAX + 1);
        if (filename == NULL)
            error("Makefilename: malloc");

        /* Process with strftime if Gflag is set. */
        if (Gflag != 0) {
          struct tm *local_tm;

          /* Convert Gflag_time to a usable format */
          if ((local_tm = localtime(&Gflag_time)) == NULL) {
                  error("MakeTimedFilename: localtime");
          }

          /* There's no good way to detect an error in strftime since a return
           * value of 0 isn't necessarily failure.
           */
          strftime(filename, PATH_MAX, orig_name, local_tm);
        } else {
          strncpy(filename, orig_name, PATH_MAX);
        }

	if (cnt == 0 && max_chars == 0)
		strncpy(buffer, filename, PATH_MAX + 1);
	else
		if (snprintf(buffer, PATH_MAX + 1, "%s%0*d", filename, max_chars, cnt) > PATH_MAX)
                  /* Report an error if the filename is too large */
                  error("too many output files or filename is too long (> %d)", PATH_MAX);
        free(filename);
}

static int tcpdump_printf(netdissect_options *ndo _U_,
			  const char *fmt, ...)
{

  va_list args;
  int ret;

  va_start(args, fmt);
  ret=vfprintf(stdout, fmt, args);
  va_end(args);

  return ret;
}

static struct print_info
get_print_info(int type)
{
	struct print_info printinfo;

	printinfo.ndo_type = 1;
	printinfo.ndo = gndo;
	printinfo.p.ndo_printer = lookup_ndo_printer(type);
	if (printinfo.p.ndo_printer == NULL) {
		printinfo.p.printer = lookup_printer(type);
		printinfo.ndo_type = 0;
		if (printinfo.p.printer == NULL) {
			gndo->ndo_dltname = pcap_datalink_val_to_name(type);
			if (gndo->ndo_dltname != NULL)
				error("packet printing is not supported for link type %s: use -w",
				      gndo->ndo_dltname);
			else
				error("packet printing is not supported for link type %d: use -w", type);
		}
	}
	return (printinfo);
}

static char *
get_next_file(FILE *VFile, char *ptr)
{
	char *ret;

	ret = fgets(ptr, PATH_MAX, VFile);
	if (!ret)
		return NULL;

	if (ptr[strlen(ptr) - 1] == '\n')
		ptr[strlen(ptr) - 1] = '\0';

	return ret;
}

#ifdef HAVE_PCAP_SET_TSTAMP_PRECISION
static int
tstamp_precision_from_string(const char *precision)
{
	if (strncmp(precision, "nano", strlen("nano")) == 0)
		return PCAP_TSTAMP_PRECISION_NANO;

	if (strncmp(precision, "micro", strlen("micro")) == 0)
		return PCAP_TSTAMP_PRECISION_MICRO;

	return -EINVAL;
}

static const char *
tstamp_precision_to_string(int precision)
{
	switch (precision) {

	case PCAP_TSTAMP_PRECISION_MICRO:
		return "micro";

	case PCAP_TSTAMP_PRECISION_NANO:
		return "nano";

	default:
		return "unknown";
	}
}
#endif

#ifdef HAVE_CAPSICUM
/*
 * Ensure that, on a dump file's descriptor, we have all the rights
 * necessary to make the standard I/O library work with an fdopen()ed
 * FILE * from that descriptor.
 *
 * A long time ago, in a galaxy far far away, AT&T decided that, instead
 * of providing separate APIs for getting and setting the FD_ flags on a
 * descriptor, getting and setting the O_ flags on a descriptor, and
 * locking files, they'd throw them all into a kitchen-sink fcntl() call
 * along the lines of ioctl(), the fact that ioctl() operations are
 * largely specific to particular character devices but fcntl() operations
 * are either generic to all descriptors or generic to all descriptors for
 * regular files nonwithstanding.
 *
 * The Capsicum people decided that fine-grained control of descriptor
 * operations was required, so that you need to grant permission for
 * reading, writing, seeking, and fcntl-ing.  The latter, courtesy of
 * AT&T's decision, means that "fcntl-ing" isn't a thing, but a motley
 * collection of things, so there are *individual* fcntls for which
 * permission needs to be granted.
 *
 * The FreeBSD standard I/O people implemented some optimizations that
 * requires that the standard I/O routines be able to determine whether
 * the descriptor for the FILE * is open append-only or not; as that
 * descriptor could have come from an open() rather than an fopen(),
 * that requires that it be able to do an F_GETFL fcntl() to read
 * the O_ flags.
 *
 * Tcpdump uses ftell() to determine how much data has been written
 * to a file in order to, when used with -C, determine when it's time
 * to rotate capture files.  ftell() therefore needs to do an lseek()
 * to find out the file offset and must, thanks to the aforementioned
 * optimization, also know whether the descriptor is open append-only
 * or not.
 *
 * The net result of all the above is that we need to grant CAP_SEEK,
 * CAP_WRITE, and CAP_FCNTL with the CAP_FCNTL_GETFL subcapability.
 *
 * Perhaps this is the universe's way of saying that either
 *
 *	1) there needs to be an fopenat() call and a pcap_dump_openat() call
 *	   using it, so that Capsicum-capable tcpdump wouldn't need to do
 *	   an fdopen()
 *
 * or
 *
 *	2) there needs to be a cap_fdopen() call in the FreeBSD standard
 *	   I/O library that knows what rights are needed by the standard
 *	   I/O library, based on the open mode, and assigns them, perhaps
 *	   with an additional argument indicating, for example, whether
 *	   seeking should be allowed, so that tcpdump doesn't need to know
 *	   what the standard I/O library happens to require this week.
 */
static void
set_dumper_capsicum_rights(pcap_dumper_t *p)
{
	int fd = fileno(pcap_dump_file(p));
	cap_rights_t rights;

	cap_rights_init(&rights, CAP_SEEK, CAP_WRITE, CAP_FCNTL);
	if (cap_rights_limit(fd, &rights) < 0 && errno != ENOSYS) {
		error("unable to limit dump descriptor");
	}
	if (cap_fcntls_limit(fd, CAP_FCNTL_GETFL) < 0 && errno != ENOSYS) {
		error("unable to limit dump descriptor fcntls");
	}
}
#endif

int
main(int argc, char **argv)
{
	register int cnt, op, i;
	bpf_u_int32 localnet =0 , netmask = 0;
	register char *cp, *infile, *cmdbuf, *device, *RFileName, *VFileName, *WFileName;
	pcap_handler callback;
	int type;
	int dlt;
	int new_dlt;
	const char *dlt_name;
	struct bpf_program fcode;
#ifndef WIN32
	RETSIGTYPE (*oldhandler)(int);
#endif
	struct print_info printinfo;
	struct dump_info dumpinfo;
	u_char *pcap_userdata;
	char ebuf[PCAP_ERRBUF_SIZE];
	char VFileLine[PATH_MAX + 1];
	char *username = NULL;
	char *chroot_dir = NULL;
	char *ret = NULL;
	char *end;
#ifdef HAVE_PCAP_FINDALLDEVS
	pcap_if_t *devpointer;
	int devnum;
#endif
	int status;
	FILE *VFile;
#ifdef HAVE_CAPSICUM
	cap_rights_t rights;
	int cansandbox;
#endif	/* HAVE_CAPSICUM */

#ifdef WIN32
	if(wsockinit() != 0) return 1;
#endif /* WIN32 */

	jflag=-1;	/* not set */
        gndo->ndo_Oflag=1;
	gndo->ndo_Rflag=1;
	gndo->ndo_dlt=-1;
	gndo->ndo_default_print=ndo_default_print;
	gndo->ndo_printf=tcpdump_printf;
	gndo->ndo_error=ndo_error;
	gndo->ndo_warning=ndo_warning;
	gndo->ndo_snaplen = DEFAULT_SNAPLEN;
	gndo->ndo_immediate = 0;

	cnt = -1;
	device = NULL;
	infile = NULL;
	RFileName = NULL;
	VFileName = NULL;
	VFile = NULL;
	WFileName = NULL;
	dlt = -1;
	if ((cp = strrchr(argv[0], '/')) != NULL)
		program_name = cp + 1;
	else
		program_name = argv[0];

	/*
	 * On platforms where the CPU doesn't support unaligned loads,
	 * force unaligned accesses to abort with SIGBUS, rather than
	 * being fixed up (slowly) by the OS kernel; on those platforms,
	 * misaligned accesses are bugs, and we want tcpdump to crash so
	 * that the bugs are reported.
	 */
	if (abort_on_misalignment(ebuf, sizeof(ebuf)) < 0)
		error("%s", ebuf);

#ifdef USE_LIBSMI
	smiInit("tcpdump");
#endif

	while (
	    (op = getopt_long(argc, argv, SHORTOPTS, longopts, NULL)) != -1)
		switch (op) {

		case 'a':
			/* compatibility for old -a */
			break;

		case 'A':
			++Aflag;
			break;

		case 'b':
			++bflag;
			break;

#if defined(HAVE_PCAP_CREATE) || defined(WIN32)
		case 'B':
			Bflag = atoi(optarg)*1024;
			if (Bflag <= 0)
				error("invalid packet buffer size %s", optarg);
			break;
#endif /* defined(HAVE_PCAP_CREATE) || defined(WIN32) */

		case 'c':
			cnt = atoi(optarg);
			if (cnt <= 0)
				error("invalid packet count %s", optarg);
			break;

		case 'C':
			Cflag = atoi(optarg) * 1000000;
			if (Cflag < 0)
				error("invalid file size %s", optarg);
			break;

		case 'd':
			++dflag;
			break;

		case 'D':
			Dflag++;
			break;

		case 'L':
			Lflag++;
			break;

		case 'e':
			++eflag;
			break;

		case 'E':
#ifndef HAVE_LIBCRYPTO
			warning("crypto code not compiled in");
#endif
			gndo->ndo_espsecret = optarg;
			break;

		case 'f':
			++fflag;
			break;

		case 'F':
			infile = optarg;
			break;

		case 'G':
			Gflag = atoi(optarg);
			if (Gflag < 0)
				error("invalid number of seconds %s", optarg);

                        /* We will create one file initially. */
                        Gflag_count = 0;

			/* Grab the current time for rotation use. */
			if ((Gflag_time = time(NULL)) == (time_t)-1) {
				error("main: can't get current time: %s",
				    pcap_strerror(errno));
			}
			break;

		case 'h':
			print_usage();
			exit(0);
			break;

		case 'H':
			++Hflag;
			break;

		case 'i':
			if (optarg[0] == '0' && optarg[1] == 0)
				error("Invalid adapter index");

#ifdef HAVE_PCAP_FINDALLDEVS
			/*
			 * If the argument is a number, treat it as
			 * an index into the list of adapters, as
			 * printed by "tcpdump -D".
			 *
			 * This should be OK on UNIX systems, as interfaces
			 * shouldn't have names that begin with digits.
			 * It can be useful on Windows, where more than
			 * one interface can have the same name.
			 */
			devnum = strtol(optarg, &end, 10);
			if (optarg != end && *end == '\0') {
				if (devnum < 0)
					error("Invalid adapter index");

				if (pcap_findalldevs(&devpointer, ebuf) < 0)
					error("%s", ebuf);
				else {
					/*
					 * Look for the devnum-th entry
					 * in the list of devices
					 * (1-based).
					 */
					for (i = 0;
					    i < devnum-1 && devpointer != NULL;
					    i++, devpointer = devpointer->next)
						;
					if (devpointer == NULL)
						error("Invalid adapter index");
				}
				device = devpointer->name;
				break;
			}
#endif /* HAVE_PCAP_FINDALLDEVS */
			device = optarg;
			break;

#ifdef HAVE_PCAP_CREATE
		case 'I':
			++Iflag;
			break;
#endif /* HAVE_PCAP_CREATE */

#ifdef HAVE_PCAP_SET_TSTAMP_TYPE
		case 'j':
			jflag = pcap_tstamp_type_name_to_val(optarg);
			if (jflag < 0)
				error("invalid time stamp type %s", optarg);
			break;

		case 'J':
			Jflag++;
			break;
#endif

		case 'l':
#ifdef WIN32
			/*
			 * _IOLBF is the same as _IOFBF in Microsoft's C
			 * libraries; the only alternative they offer
			 * is _IONBF.
			 *
			 * XXX - this should really be checking for MSVC++,
			 * not WIN32, if, for example, MinGW has its own
			 * C library that is more UNIX-compatible.
			 */
			setvbuf(stdout, NULL, _IONBF, 0);
#else /* WIN32 */
#ifdef HAVE_SETLINEBUF
			setlinebuf(stdout);
#else
			setvbuf(stdout, NULL, _IOLBF, 0);
#endif
#endif /* WIN32 */
			break;

		case 'K':
			++Kflag;
			break;

		case 'm':
#ifdef USE_LIBSMI
			if (smiLoadModule(optarg) == 0) {
				error("could not load MIB module %s", optarg);
			}
			sflag = 1;
#else
			(void)fprintf(stderr, "%s: ignoring option `-m %s' ",
				      program_name, optarg);
			(void)fprintf(stderr, "(no libsmi support)\n");
#endif
			break;

		case 'M':
			/* TCP-MD5 shared secret */
#ifndef HAVE_LIBCRYPTO
			warning("crypto code not compiled in");
#endif
			sigsecret = optarg;
			break;

		case 'n':
			++nflag;
			break;

		case 'N':
			++Nflag;
			break;

		case 'O':
			Oflag = 0;
			break;

		case 'p':
			++pflag;
			break;

		case 'q':
			++qflag;
			++suppress_default_print;
			break;

#ifdef HAVE_PCAP_SETDIRECTION
		case 'Q':
			if (strcasecmp(optarg, "in") == 0)
				Qflag = PCAP_D_IN;
			else if (strcasecmp(optarg, "out") == 0)
				Qflag = PCAP_D_OUT;
			else if (strcasecmp(optarg, "inout") == 0)
				Qflag = PCAP_D_INOUT;
			else
				error("unknown capture direction `%s'", optarg);
			break;
#endif /* HAVE_PCAP_SETDIRECTION */

		case 'r':
			RFileName = optarg;
			break;

		case 'R':
			Rflag = 0;
			break;

		case 's':
			snaplen = strtol(optarg, &end, 0);
			if (optarg == end || *end != '\0'
			    || snaplen < 0 || snaplen > MAXIMUM_SNAPLEN)
				error("invalid snaplen %s", optarg);
			else if (snaplen == 0)
				snaplen = MAXIMUM_SNAPLEN;
			break;

		case 'S':
			++Sflag;
			break;

		case 't':
			++tflag;
			break;

		case 'T':
			if (strcasecmp(optarg, "vat") == 0)
				packettype = PT_VAT;
			else if (strcasecmp(optarg, "wb") == 0)
				packettype = PT_WB;
			else if (strcasecmp(optarg, "rpc") == 0)
				packettype = PT_RPC;
			else if (strcasecmp(optarg, "rtp") == 0)
				packettype = PT_RTP;
			else if (strcasecmp(optarg, "rtcp") == 0)
				packettype = PT_RTCP;
			else if (strcasecmp(optarg, "snmp") == 0)
				packettype = PT_SNMP;
			else if (strcasecmp(optarg, "cnfp") == 0)
				packettype = PT_CNFP;
			else if (strcasecmp(optarg, "tftp") == 0)
				packettype = PT_TFTP;
			else if (strcasecmp(optarg, "aodv") == 0)
				packettype = PT_AODV;
			else if (strcasecmp(optarg, "carp") == 0)
				packettype = PT_CARP;
			else if (strcasecmp(optarg, "radius") == 0)
				packettype = PT_RADIUS;
			else if (strcasecmp(optarg, "zmtp1") == 0)
				packettype = PT_ZMTP1;
			else if (strcasecmp(optarg, "vxlan") == 0)
				packettype = PT_VXLAN;
			else if (strcasecmp(optarg, "pgm") == 0)
				packettype = PT_PGM;
			else if (strcasecmp(optarg, "pgm_zmtp1") == 0)
				packettype = PT_PGM_ZMTP1;
			else if (strcasecmp(optarg, "lmp") == 0)
				packettype = PT_LMP;
			else
				error("unknown packet type `%s'", optarg);
			break;

		case 'u':
			++uflag;
			break;

#ifdef HAVE_PCAP_DUMP_FLUSH
		case 'U':
			++Uflag;
			break;
#endif

		case 'v':
			++vflag;
			break;

		case 'V':
			VFileName = optarg;
			break;

		case 'w':
			WFileName = optarg;
			break;

		case 'W':
			Wflag = atoi(optarg);
			if (Wflag < 0)
				error("invalid number of output files %s", optarg);
			WflagChars = getWflagChars(Wflag);
			break;

		case 'x':
			++xflag;
			++suppress_default_print;
			break;

		case 'X':
			++Xflag;
			++suppress_default_print;
			break;

		case 'y':
			gndo->ndo_dltname = optarg;
			gndo->ndo_dlt =
			  pcap_datalink_name_to_val(gndo->ndo_dltname);
			if (gndo->ndo_dlt < 0)
				error("invalid data link type %s", gndo->ndo_dltname);
			break;

#if defined(HAVE_PCAP_DEBUG) || defined(HAVE_YYDEBUG)
		case 'Y':
			{
			/* Undocumented flag */
#ifdef HAVE_PCAP_DEBUG
			extern int pcap_debug;
			pcap_debug = 1;
#else
			extern int yydebug;
			yydebug = 1;
#endif
			}
			break;
#endif
		case 'z':
			zflag = strdup(optarg);
			break;

		case 'Z':
			username = strdup(optarg);
			break;

		case '#':
			gndo->ndo_packet_number = 1;
			break;

		case OPTION_VERSION:
			print_version();
			exit(0);
			break;

#ifdef HAVE_PCAP_SET_TSTAMP_PRECISION
		case OPTION_TSTAMP_PRECISION:
			gndo->ndo_tstamp_precision = tstamp_precision_from_string(optarg);
			if (gndo->ndo_tstamp_precision < 0)
				error("unsupported time stamp precision");
			break;
#endif

#ifdef HAVE_PCAP_SET_IMMEDIATE_MODE
		case OPTION_IMMEDIATE_MODE:
			gndo->ndo_immediate = 1;
			break;
#endif

		default:
			print_usage();
			exit(1);
			/* NOTREACHED */
		}

#ifdef HAVE_PCAP_FINDALLDEVS
	if (Dflag)
		show_devices_and_exit();
#endif

	switch (tflag) {

	case 0: /* Default */
	case 4: /* Default + Date*/
		thiszone = gmt2local(0);
		break;

	case 1: /* No time stamp */
	case 2: /* Unix timeval style */
	case 3: /* Microseconds since previous packet */
        case 5: /* Microseconds since first packet */
		break;

	default: /* Not supported */
		error("only -t, -tt, -ttt, -tttt and -ttttt are supported");
		break;
	}

	if (fflag != 0 && (VFileName != NULL || RFileName != NULL))
		error("-f can not be used with -V or -r");

	if (VFileName != NULL && RFileName != NULL)
		error("-V and -r are mutually exclusive.");

#ifdef HAVE_PCAP_SET_IMMEDIATE_MODE
	/*
	 * If we're printing dissected packets to the standard output
	 * rather than saving raw packets to a file, and the standard
	 * output is a terminal, use immediate mode, as the user's
	 * probably expecting to see packets pop up immediately.
	 */
	if (WFileName == NULL && isatty(1))
		gndo->ndo_immediate = 1;
#endif

#ifdef WITH_CHROOT
	/* if run as root, prepare for chrooting */
	if (getuid() == 0 || geteuid() == 0) {
		/* future extensibility for cmd-line arguments */
		if (!chroot_dir)
			chroot_dir = WITH_CHROOT;
	}
#endif

#ifdef WITH_USER
	/* if run as root, prepare for dropping root privileges */
	if (getuid() == 0 || geteuid() == 0) {
		/* Run with '-Z root' to restore old behaviour */
		if (!username)
			username = WITH_USER;
	}
#endif

	if (RFileName != NULL || VFileName != NULL) {
		/*
		 * If RFileName is non-null, it's the pathname of a
		 * savefile to read.  If VFileName is non-null, it's
		 * the pathname of a file containing a list of pathnames
		 * (one per line) of savefiles to read.
		 *
		 * In either case, we're reading a savefile, not doing
		 * a live capture.
		 */
#ifndef WIN32
		/*
		 * We don't need network access, so relinquish any set-UID
		 * or set-GID privileges we have (if any).
		 *
		 * We do *not* want set-UID privileges when opening a
		 * trace file, as that might let the user read other
		 * people's trace files (especially if we're set-UID
		 * root).
		 */
		if (setgid(getgid()) != 0 || setuid(getuid()) != 0 )
			fprintf(stderr, "Warning: setgid/setuid failed !\n");
#endif /* WIN32 */
		if (VFileName != NULL) {
			if (VFileName[0] == '-' && VFileName[1] == '\0')
				VFile = stdin;
			else
				VFile = fopen(VFileName, "r");

			if (VFile == NULL)
				error("Unable to open file: %s\n", strerror(errno));

			ret = get_next_file(VFile, VFileLine);
			if (!ret)
				error("Nothing in %s\n", VFileName);
			RFileName = VFileLine;
		}

#ifdef HAVE_PCAP_SET_TSTAMP_PRECISION
		pd = pcap_open_offline_with_tstamp_precision(RFileName,
		    gndo->ndo_tstamp_precision, ebuf);
#else
		pd = pcap_open_offline(RFileName, ebuf);
#endif

		if (pd == NULL)
			error("%s", ebuf);
#ifdef HAVE_CAPSICUM
		cap_rights_init(&rights, CAP_READ);
		if (cap_rights_limit(fileno(pcap_file(pd)), &rights) < 0 &&
		    errno != ENOSYS) {
			error("unable to limit pcap descriptor");
		}
#endif
		dlt = pcap_datalink(pd);
		dlt_name = pcap_datalink_val_to_name(dlt);
		if (dlt_name == NULL) {
			fprintf(stderr, "reading from file %s, link-type %u\n",
			    RFileName, dlt);
		} else {
			fprintf(stderr,
			    "reading from file %s, link-type %s (%s)\n",
			    RFileName, dlt_name,
			    pcap_datalink_val_to_description(dlt));
		}
	} else {
		/*
		 * We're doing a live capture.
		 */
		if (device == NULL) {
			device = pcap_lookupdev(ebuf);
			if (device == NULL)
				error("%s", ebuf);
		}
#ifdef WIN32
		/*
		 * Print a message to the standard error on Windows.
		 * XXX - why do it here, with a different message?
		 */
		if(strlen(device) == 1)	/* we assume that an ASCII string is always longer than 1 char */
		{						/* a Unicode string has a \0 as second byte (so strlen() is 1) */
			fprintf(stderr, "%s: listening on %ws\n", program_name, device);
		}
		else
		{
			fprintf(stderr, "%s: listening on %s\n", program_name, device);
		}

		fflush(stderr);
#endif /* WIN32 */
#ifdef HAVE_PCAP_CREATE
		pd = pcap_create(device, ebuf);
		if (pd == NULL)
			error("%s", ebuf);
#ifdef HAVE_PCAP_SET_TSTAMP_TYPE
		if (Jflag)
			show_tstamp_types_and_exit(device, pd);
#endif
#ifdef HAVE_PCAP_SET_TSTAMP_PRECISION
		status = pcap_set_tstamp_precision(pd, gndo->ndo_tstamp_precision);
		if (status != 0)
			error("%s: Can't set %ssecond time stamp precision: %s",
				device,
				tstamp_precision_to_string(gndo->ndo_tstamp_precision),
				pcap_statustostr(status));
#endif

#ifdef HAVE_PCAP_SET_IMMEDIATE_MODE
		if (gndo->ndo_immediate) {
			status = pcap_set_immediate_mode(pd, 1);
			if (status != 0)
				error("%s: Can't set immediate mode: %s",
				device,
				pcap_statustostr(status));
		}
#endif
		/*
		 * Is this an interface that supports monitor mode?
		 */
		if (pcap_can_set_rfmon(pd) == 1)
			supports_monitor_mode = 1;
		else
			supports_monitor_mode = 0;
		status = pcap_set_snaplen(pd, snaplen);
		if (status != 0)
			error("%s: Can't set snapshot length: %s",
			    device, pcap_statustostr(status));
		status = pcap_set_promisc(pd, !pflag);
		if (status != 0)
			error("%s: Can't set promiscuous mode: %s",
			    device, pcap_statustostr(status));
		if (Iflag) {
			status = pcap_set_rfmon(pd, 1);
			if (status != 0)
				error("%s: Can't set monitor mode: %s",
				    device, pcap_statustostr(status));
		}
		status = pcap_set_timeout(pd, 1000);
		if (status != 0)
			error("%s: pcap_set_timeout failed: %s",
			    device, pcap_statustostr(status));
		if (Bflag != 0) {
			status = pcap_set_buffer_size(pd, Bflag);
			if (status != 0)
				error("%s: Can't set buffer size: %s",
				    device, pcap_statustostr(status));
		}
#ifdef HAVE_PCAP_SET_TSTAMP_TYPE
                if (jflag != -1) {
			status = pcap_set_tstamp_type(pd, jflag);
			if (status < 0)
				error("%s: Can't set time stamp type: %s",
			              device, pcap_statustostr(status));
		}
#endif
		status = pcap_activate(pd);
		if (status < 0) {
			/*
			 * pcap_activate() failed.
			 */
			cp = pcap_geterr(pd);
			if (status == PCAP_ERROR)
				error("%s", cp);
			else if ((status == PCAP_ERROR_NO_SUCH_DEVICE ||
			          status == PCAP_ERROR_PERM_DENIED) &&
			         *cp != '\0')
				error("%s: %s\n(%s)", device,
				    pcap_statustostr(status), cp);
			else
				error("%s: %s", device,
				    pcap_statustostr(status));
		} else if (status > 0) {
			/*
			 * pcap_activate() succeeded, but it's warning us
			 * of a problem it had.
			 */
			cp = pcap_geterr(pd);
			if (status == PCAP_WARNING)
				warning("%s", cp);
			else if (status == PCAP_WARNING_PROMISC_NOTSUP &&
			         *cp != '\0')
				warning("%s: %s\n(%s)", device,
				    pcap_statustostr(status), cp);
			else
				warning("%s: %s", device,
				    pcap_statustostr(status));
		}
#ifdef HAVE_PCAP_SETDIRECTION
		if (Qflag != -1) {
			status = pcap_setdirection(pd, Qflag);
			if (status != 0)
				error("%s: pcap_setdirection() failed: %s",
				      device,  pcap_geterr(pd));
		}
#endif /* HAVE_PCAP_SETDIRECTION */
#else
		*ebuf = '\0';
		pd = pcap_open_live(device, snaplen, !pflag, 1000, ebuf);
		if (pd == NULL)
			error("%s", ebuf);
		else if (*ebuf)
			warning("%s", ebuf);
#endif /* HAVE_PCAP_CREATE */
		/*
		 * Let user own process after socket has been opened.
		 */
#ifndef WIN32
		if (setgid(getgid()) != 0 || setuid(getuid()) != 0)
			fprintf(stderr, "Warning: setgid/setuid failed !\n");
#endif /* WIN32 */
#if !defined(HAVE_PCAP_CREATE) && defined(WIN32)
		if(Bflag != 0)
			if(pcap_setbuff(pd, Bflag)==-1){
				error("%s", pcap_geterr(pd));
			}
#endif /* !defined(HAVE_PCAP_CREATE) && defined(WIN32) */
		if (Lflag)
			show_dlts_and_exit(device, pd);
		if (gndo->ndo_dlt >= 0) {
#ifdef HAVE_PCAP_SET_DATALINK
			if (pcap_set_datalink(pd, gndo->ndo_dlt) < 0)
				error("%s", pcap_geterr(pd));
#else
			/*
			 * We don't actually support changing the
			 * data link type, so we only let them
			 * set it to what it already is.
			 */
			if (gndo->ndo_dlt != pcap_datalink(pd)) {
				error("%s is not one of the DLTs supported by this device\n",
				      gndo->ndo_dltname);
			}
#endif
			(void)fprintf(stderr, "%s: data link type %s\n",
				      program_name, gndo->ndo_dltname);
			(void)fflush(stderr);
		}
		i = pcap_snapshot(pd);
		if (snaplen < i) {
			warning("snaplen raised from %d to %d", snaplen, i);
			snaplen = i;
		}
                if(fflag != 0) {
                        if (pcap_lookupnet(device, &localnet, &netmask, ebuf) < 0) {
                                warning("foreign (-f) flag used but: %s", ebuf);
                        }
                }

	}
	if (infile)
		cmdbuf = read_infile(infile);
	else
		cmdbuf = copy_argv(&argv[optind]);

	if (pcap_compile(pd, &fcode, cmdbuf, Oflag, netmask) < 0)
		error("%s", pcap_geterr(pd));
	if (dflag) {
		bpf_dump(&fcode, dflag);
		pcap_close(pd);
		free(cmdbuf);
		exit(0);
	}
	init_addrtoname(gndo, localnet, netmask);
        init_checksum();

#ifndef WIN32
	(void)setsignal(SIGPIPE, cleanup);
	(void)setsignal(SIGTERM, cleanup);
	(void)setsignal(SIGINT, cleanup);
#endif /* WIN32 */
#if defined(HAVE_FORK) || defined(HAVE_VFORK)
	(void)setsignal(SIGCHLD, child_cleanup);
#endif
	/* Cooperate with nohup(1) */
#ifndef WIN32
	if ((oldhandler = setsignal(SIGHUP, cleanup)) != SIG_DFL)
		(void)setsignal(SIGHUP, oldhandler);
#endif /* WIN32 */

#ifndef WIN32
	/*
	 * If a user name was specified with "-Z", attempt to switch to
	 * that user's UID.  This would probably be used with sudo,
	 * to allow tcpdump to be run in a special restricted
	 * account (if you just want to allow users to open capture
	 * devices, and can't just give users that permission,
	 * you'd make tcpdump set-UID or set-GID).
	 *
	 * Tcpdump doesn't necessarily write only to one savefile;
	 * the general only way to allow a -Z instance to write to
	 * savefiles as the user under whose UID it's run, rather
	 * than as the user specified with -Z, would thus be to switch
	 * to the original user ID before opening a capture file and
	 * then switch back to the -Z user ID after opening the savefile.
	 * Switching to the -Z user ID only after opening the first
	 * savefile doesn't handle the general case.
	 */

	if (getuid() == 0 || geteuid() == 0) {
#ifdef HAVE_LIBCAP_NG
		/* Initialize capng */
		capng_clear(CAPNG_SELECT_BOTH);
		if (username) {
			capng_updatev(
				CAPNG_ADD,
				CAPNG_PERMITTED | CAPNG_EFFECTIVE,
				CAP_SETUID,
				CAP_SETGID,
				-1);
		}

		if (WFileName) {
			capng_update(
				CAPNG_ADD,
				CAPNG_PERMITTED | CAPNG_EFFECTIVE,
				CAP_DAC_OVERRIDE
				);
		}
		capng_apply(CAPNG_SELECT_BOTH);
#endif /* HAVE_LIBCAP_NG */
		if (username || chroot_dir)
			droproot(username, chroot_dir);

	}
#endif /* WIN32 */

	if (pcap_setfilter(pd, &fcode) < 0)
		error("%s", pcap_geterr(pd));
#ifdef HAVE_CAPSICUM
	if (RFileName == NULL && VFileName == NULL) {
		static const unsigned long cmds[] = { BIOCGSTATS };

		cap_rights_init(&rights, CAP_IOCTL, CAP_READ);
		if (cap_rights_limit(pcap_fileno(pd), &rights) < 0 &&
		    errno != ENOSYS) {
			error("unable to limit pcap descriptor");
		}
		if (cap_ioctls_limit(pcap_fileno(pd), cmds,
		    sizeof(cmds) / sizeof(cmds[0])) < 0 && errno != ENOSYS) {
			error("unable to limit ioctls on pcap descriptor");
		}
	}
#endif
	if (WFileName) {
		pcap_dumper_t *p;
		/* Do not exceed the default PATH_MAX for files. */
		dumpinfo.CurrentFileName = (char *)malloc(PATH_MAX + 1);

		if (dumpinfo.CurrentFileName == NULL)
			error("malloc of dumpinfo.CurrentFileName");

		/* We do not need numbering for dumpfiles if Cflag isn't set. */
		if (Cflag != 0)
		  MakeFilename(dumpinfo.CurrentFileName, WFileName, 0, WflagChars);
		else
		  MakeFilename(dumpinfo.CurrentFileName, WFileName, 0, 0);

		p = pcap_dump_open(pd, dumpinfo.CurrentFileName);
#ifdef HAVE_LIBCAP_NG
		/* Give up CAP_DAC_OVERRIDE capability.
		 * Only allow it to be restored if the -C or -G flag have been
		 * set since we may need to create more files later on.
		 */
		capng_update(
			CAPNG_DROP,
			(Cflag || Gflag ? 0 : CAPNG_PERMITTED)
				| CAPNG_EFFECTIVE,
			CAP_DAC_OVERRIDE
			);
		capng_apply(CAPNG_SELECT_BOTH);
#endif /* HAVE_LIBCAP_NG */
		if (p == NULL)
			error("%s", pcap_geterr(pd));
#ifdef HAVE_CAPSICUM
		set_dumper_capsicum_rights(p);
#endif
		if (Cflag != 0 || Gflag != 0) {
#ifdef HAVE_CAPSICUM
			dumpinfo.WFileName = strdup(basename(WFileName));
			dumpinfo.dirfd = open(dirname(WFileName),
			    O_DIRECTORY | O_RDONLY);
			if (dumpinfo.dirfd < 0) {
				error("unable to open directory %s",
				    dirname(WFileName));
			}
			cap_rights_init(&rights, CAP_CREATE, CAP_FCNTL,
			    CAP_FTRUNCATE, CAP_LOOKUP, CAP_SEEK, CAP_WRITE);
			if (cap_rights_limit(dumpinfo.dirfd, &rights) < 0 &&
			    errno != ENOSYS) {
				error("unable to limit directory rights");
			}
			if (cap_fcntls_limit(dumpinfo.dirfd, CAP_FCNTL_GETFL) < 0 &&
			    errno != ENOSYS) {
				error("unable to limit dump descriptor fcntls");
			}
#else	/* !HAVE_CAPSICUM */
			dumpinfo.WFileName = WFileName;
#endif
			callback = dump_packet_and_trunc;
			dumpinfo.pd = pd;
			dumpinfo.p = p;
			pcap_userdata = (u_char *)&dumpinfo;
		} else {
			callback = dump_packet;
			pcap_userdata = (u_char *)p;
		}
#ifdef HAVE_PCAP_DUMP_FLUSH
		if (Uflag)
			pcap_dump_flush(p);
#endif
	} else {
		type = pcap_datalink(pd);
		printinfo = get_print_info(type);
		callback = print_packet;
		pcap_userdata = (u_char *)&printinfo;
	}

#ifdef SIGNAL_REQ_INFO
	/*
	 * We can't get statistics when reading from a file rather
	 * than capturing from a device.
	 */
	if (RFileName == NULL)
		(void)setsignal(SIGNAL_REQ_INFO, requestinfo);
#endif

	if (vflag > 0 && WFileName) {
		/*
		 * When capturing to a file, "-v" means tcpdump should,
		 * every 10 secodns, "v"erbosely report the number of
		 * packets captured.
		 */
#ifdef USE_WIN32_MM_TIMER
		/* call verbose_stats_dump() each 1000 +/-100msec */
		timer_id = timeSetEvent(1000, 100, verbose_stats_dump, 0, TIME_PERIODIC);
		setvbuf(stderr, NULL, _IONBF, 0);
#elif defined(HAVE_ALARM)
		(void)setsignal(SIGALRM, verbose_stats_dump);
		alarm(1);
#endif
	}

#ifndef WIN32
	if (RFileName == NULL) {
		/*
		 * Live capture (if -V was specified, we set RFileName
		 * to a file from the -V file).  Print a message to
		 * the standard error on UN*X.
		 */
		if (!vflag && !WFileName) {
			(void)fprintf(stderr,
			    "%s: verbose output suppressed, use -v or -vv for full protocol decode\n",
			    program_name);
		} else
			(void)fprintf(stderr, "%s: ", program_name);
		dlt = pcap_datalink(pd);
		dlt_name = pcap_datalink_val_to_name(dlt);
		if (dlt_name == NULL) {
			(void)fprintf(stderr, "listening on %s, link-type %u, capture size %u bytes\n",
			    device, dlt, snaplen);
		} else {
			(void)fprintf(stderr, "listening on %s, link-type %s (%s), capture size %u bytes\n",
			    device, dlt_name,
			    pcap_datalink_val_to_description(dlt), snaplen);
		}
		(void)fflush(stderr);
	}
#endif /* WIN32 */

#ifdef HAVE_CAPSICUM
	cansandbox = (nflag && VFileName == NULL && zflag == NULL);
	if (cansandbox && cap_enter() < 0 && errno != ENOSYS)
		error("unable to enter the capability mode");
	if (cap_sandboxed())
		fprintf(stderr, "capability mode sandbox enabled\n");
#endif	/* HAVE_CAPSICUM */

	do {
		status = pcap_loop(pd, cnt, callback, pcap_userdata);
		if (WFileName == NULL) {
			/*
			 * We're printing packets.  Flush the printed output,
			 * so it doesn't get intermingled with error output.
			 */
			if (status == -2) {
				/*
				 * We got interrupted, so perhaps we didn't
				 * manage to finish a line we were printing.
				 * Print an extra newline, just in case.
				 */
				putchar('\n');
			}
			(void)fflush(stdout);
		}
                if (status == -2) {
			/*
			 * We got interrupted. If we are reading multiple
			 * files (via -V) set these so that we stop.
			 */
			VFileName = NULL;
			ret = NULL;
		}
		if (status == -1) {
			/*
			 * Error.  Report it.
			 */
			(void)fprintf(stderr, "%s: pcap_loop: %s\n",
			    program_name, pcap_geterr(pd));
		}
		if (RFileName == NULL) {
			/*
			 * We're doing a live capture.  Report the capture
			 * statistics.
			 */
			info(1);
		}
		pcap_close(pd);
		if (VFileName != NULL) {
			ret = get_next_file(VFile, VFileLine);
			if (ret) {
				RFileName = VFileLine;
				pd = pcap_open_offline(RFileName, ebuf);
				if (pd == NULL)
					error("%s", ebuf);
#ifdef HAVE_CAPSICUM
				cap_rights_init(&rights, CAP_READ);
				if (cap_rights_limit(fileno(pcap_file(pd)),
				    &rights) < 0 && errno != ENOSYS) {
					error("unable to limit pcap descriptor");
				}
#endif
				new_dlt = pcap_datalink(pd);
				if (WFileName && new_dlt != dlt)
					error("%s: new dlt does not match original", RFileName);
				printinfo = get_print_info(new_dlt);
				dlt_name = pcap_datalink_val_to_name(new_dlt);
				if (dlt_name == NULL) {
					fprintf(stderr, "reading from file %s, link-type %u\n",
					RFileName, new_dlt);
				} else {
					fprintf(stderr,
					"reading from file %s, link-type %s (%s)\n",
					RFileName, dlt_name,
					pcap_datalink_val_to_description(new_dlt));
				}
				if (pcap_compile(pd, &fcode, cmdbuf, Oflag, netmask) < 0)
					error("%s", pcap_geterr(pd));
				if (pcap_setfilter(pd, &fcode) < 0)
					error("%s", pcap_geterr(pd));
			}
		}
	}
	while (ret != NULL);

	free(cmdbuf);
	exit(status == -1 ? 1 : 0);
}

/* make a clean exit on interrupts */
static RETSIGTYPE
cleanup(int signo _U_)
{
#ifdef USE_WIN32_MM_TIMER
	if (timer_id)
		timeKillEvent(timer_id);
	timer_id = 0;
#elif defined(HAVE_ALARM)
	alarm(0);
#endif

#ifdef HAVE_PCAP_BREAKLOOP
	/*
	 * We have "pcap_breakloop()"; use it, so that we do as little
	 * as possible in the signal handler (it's probably not safe
	 * to do anything with standard I/O streams in a signal handler -
	 * the ANSI C standard doesn't say it is).
	 */
	pcap_breakloop(pd);
#else
	/*
	 * We don't have "pcap_breakloop()"; this isn't safe, but
	 * it's the best we can do.  Print the summary if we're
	 * not reading from a savefile - i.e., if we're doing a
	 * live capture - and exit.
	 */
	if (pd != NULL && pcap_file(pd) == NULL) {
		/*
		 * We got interrupted, so perhaps we didn't
		 * manage to finish a line we were printing.
		 * Print an extra newline, just in case.
		 */
		putchar('\n');
		(void)fflush(stdout);
		info(1);
	}
	exit(0);
#endif
}

/*
  On windows, we do not use a fork, so we do not care less about
  waiting a child processes to die
 */
#if defined(HAVE_FORK) || defined(HAVE_VFORK)
static RETSIGTYPE
child_cleanup(int signo _U_)
{
  wait(NULL);
}
#endif /* HAVE_FORK && HAVE_VFORK */

static void
info(register int verbose)
{
	struct pcap_stat stat;

	/*
	 * Older versions of libpcap didn't set ps_ifdrop on some
	 * platforms; initialize it to 0 to handle that.
	 */
	stat.ps_ifdrop = 0;
	if (pcap_stats(pd, &stat) < 0) {
		(void)fprintf(stderr, "pcap_stats: %s\n", pcap_geterr(pd));
		infoprint = 0;
		return;
	}

	if (!verbose)
		fprintf(stderr, "%s: ", program_name);

	(void)fprintf(stderr, "%u packet%s captured", packets_captured,
	    PLURAL_SUFFIX(packets_captured));
	if (!verbose)
		fputs(", ", stderr);
	else
		putc('\n', stderr);
	(void)fprintf(stderr, "%u packet%s received by filter", stat.ps_recv,
	    PLURAL_SUFFIX(stat.ps_recv));
	if (!verbose)
		fputs(", ", stderr);
	else
		putc('\n', stderr);
	(void)fprintf(stderr, "%u packet%s dropped by kernel", stat.ps_drop,
	    PLURAL_SUFFIX(stat.ps_drop));
	if (stat.ps_ifdrop != 0) {
		if (!verbose)
			fputs(", ", stderr);
		else
			putc('\n', stderr);
		(void)fprintf(stderr, "%u packet%s dropped by interface\n",
		    stat.ps_ifdrop, PLURAL_SUFFIX(stat.ps_ifdrop));
	} else
		putc('\n', stderr);
	infoprint = 0;
}

#if defined(HAVE_FORK) || defined(HAVE_VFORK)
static void
compress_savefile(const char *filename)
{
# ifdef HAVE_FORK
	if (fork())
# else
	if (vfork())
# endif
		return;
	/*
	 * Set to lowest priority so that this doesn't disturb the capture
	 */
#ifdef NZERO
	setpriority(PRIO_PROCESS, 0, NZERO - 1);
#else
	setpriority(PRIO_PROCESS, 0, 19);
#endif
	if (execlp(zflag, zflag, filename, (char *)NULL) == -1)
		fprintf(stderr,
			"compress_savefile:execlp(%s, %s): %s\n",
			zflag,
			filename,
			strerror(errno));
# ifdef HAVE_FORK
	exit(1);
# else
	_exit(1);
# endif
}
#else  /* HAVE_FORK && HAVE_VFORK */
static void
compress_savefile(const char *filename)
{
	fprintf(stderr,
		"compress_savefile failed. Functionality not implemented under your system\n");
}
#endif /* HAVE_FORK && HAVE_VFORK */

static void
dump_packet_and_trunc(u_char *user, const struct pcap_pkthdr *h, const u_char *sp)
{
	struct dump_info *dump_info;

	++packets_captured;

	++infodelay;

	dump_info = (struct dump_info *)user;

	/*
	 * XXX - this won't force the file to rotate on the specified time
	 * boundary, but it will rotate on the first packet received after the
	 * specified Gflag number of seconds. Note: if a Gflag time boundary
	 * and a Cflag size boundary coincide, the time rotation will occur
	 * first thereby cancelling the Cflag boundary (since the file should
	 * be 0).
	 */
	if (Gflag != 0) {
		/* Check if it is time to rotate */
		time_t t;

		/* Get the current time */
		if ((t = time(NULL)) == (time_t)-1) {
			error("dump_and_trunc_packet: can't get current_time: %s",
			    pcap_strerror(errno));
		}


		/* If the time is greater than the specified window, rotate */
		if (t - Gflag_time >= Gflag) {
#ifdef HAVE_CAPSICUM
			FILE *fp;
			int fd;
#endif

			/* Update the Gflag_time */
			Gflag_time = t;
			/* Update Gflag_count */
			Gflag_count++;
			/*
			 * Close the current file and open a new one.
			 */
			pcap_dump_close(dump_info->p);

			/*
			 * Compress the file we just closed, if the user asked for it
			 */
			if (zflag != NULL)
				compress_savefile(dump_info->CurrentFileName);

			/*
			 * Check to see if we've exceeded the Wflag (when
			 * not using Cflag).
			 */
			if (Cflag == 0 && Wflag > 0 && Gflag_count >= Wflag) {
				(void)fprintf(stderr, "Maximum file limit reached: %d\n",
				    Wflag);
				exit(0);
				/* NOTREACHED */
			}
			if (dump_info->CurrentFileName != NULL)
				free(dump_info->CurrentFileName);
			/* Allocate space for max filename + \0. */
			dump_info->CurrentFileName = (char *)malloc(PATH_MAX + 1);
			if (dump_info->CurrentFileName == NULL)
				error("dump_packet_and_trunc: malloc");
			/*
			 * Gflag was set otherwise we wouldn't be here. Reset the count
			 * so multiple files would end with 1,2,3 in the filename.
			 * The counting is handled with the -C flow after this.
			 */
			Cflag_count = 0;

			/*
			 * This is always the first file in the Cflag
			 * rotation: e.g. 0
			 * We also don't need numbering if Cflag is not set.
			 */
			if (Cflag != 0)
				MakeFilename(dump_info->CurrentFileName, dump_info->WFileName, 0,
				    WflagChars);
			else
				MakeFilename(dump_info->CurrentFileName, dump_info->WFileName, 0, 0);

#ifdef HAVE_LIBCAP_NG
			capng_update(CAPNG_ADD, CAPNG_EFFECTIVE, CAP_DAC_OVERRIDE);
			capng_apply(CAPNG_SELECT_BOTH);
#endif /* HAVE_LIBCAP_NG */
#ifdef HAVE_CAPSICUM
			fd = openat(dump_info->dirfd,
			    dump_info->CurrentFileName,
			    O_CREAT | O_WRONLY | O_TRUNC, 0644);
			if (fd < 0) {
				error("unable to open file %s",
				    dump_info->CurrentFileName);
			}
			fp = fdopen(fd, "w");
			if (fp == NULL) {
				error("unable to fdopen file %s",
				    dump_info->CurrentFileName);
			}
			dump_info->p = pcap_dump_fopen(dump_info->pd, fp);
#else	/* !HAVE_CAPSICUM */
			dump_info->p = pcap_dump_open(dump_info->pd, dump_info->CurrentFileName);
#endif
#ifdef HAVE_LIBCAP_NG
			capng_update(CAPNG_DROP, CAPNG_EFFECTIVE, CAP_DAC_OVERRIDE);
			capng_apply(CAPNG_SELECT_BOTH);
#endif /* HAVE_LIBCAP_NG */
			if (dump_info->p == NULL)
				error("%s", pcap_geterr(pd));
#ifdef HAVE_CAPSICUM
			set_dumper_capsicum_rights(dump_info->p);
#endif
		}
	}

	/*
	 * XXX - this won't prevent capture files from getting
	 * larger than Cflag - the last packet written to the
	 * file could put it over Cflag.
	 */
	if (Cflag != 0) {
		long size = pcap_dump_ftell(dump_info->p);

		if (size == -1)
			error("ftell fails on output file");
		if (size > Cflag) {
#ifdef HAVE_CAPSICUM
			FILE *fp;
			int fd;
#endif

			/*
			 * Close the current file and open a new one.
			 */
			pcap_dump_close(dump_info->p);

			/*
			 * Compress the file we just closed, if the user
			 * asked for it.
			 */
			if (zflag != NULL)
				compress_savefile(dump_info->CurrentFileName);

			Cflag_count++;
			if (Wflag > 0) {
				if (Cflag_count >= Wflag)
					Cflag_count = 0;
			}
			if (dump_info->CurrentFileName != NULL)
				free(dump_info->CurrentFileName);
			dump_info->CurrentFileName = (char *)malloc(PATH_MAX + 1);
			if (dump_info->CurrentFileName == NULL)
				error("dump_packet_and_trunc: malloc");
			MakeFilename(dump_info->CurrentFileName, dump_info->WFileName, Cflag_count, WflagChars);
#ifdef HAVE_LIBCAP_NG
			capng_update(CAPNG_ADD, CAPNG_EFFECTIVE, CAP_DAC_OVERRIDE);
			capng_apply(CAPNG_SELECT_BOTH);
#endif /* HAVE_LIBCAP_NG */
#ifdef HAVE_CAPSICUM
			fd = openat(dump_info->dirfd, dump_info->CurrentFileName,
			    O_CREAT | O_WRONLY | O_TRUNC, 0644);
			if (fd < 0) {
				error("unable to open file %s",
				    dump_info->CurrentFileName);
			}
			fp = fdopen(fd, "w");
			if (fp == NULL) {
				error("unable to fdopen file %s",
				    dump_info->CurrentFileName);
			}
			dump_info->p = pcap_dump_fopen(dump_info->pd, fp);
#else	/* !HAVE_CAPSICUM */
			dump_info->p = pcap_dump_open(dump_info->pd, dump_info->CurrentFileName);
#endif
#ifdef HAVE_LIBCAP_NG
			capng_update(CAPNG_DROP, CAPNG_EFFECTIVE, CAP_DAC_OVERRIDE);
			capng_apply(CAPNG_SELECT_BOTH);
#endif /* HAVE_LIBCAP_NG */
			if (dump_info->p == NULL)
				error("%s", pcap_geterr(pd));
#ifdef HAVE_CAPSICUM
			set_dumper_capsicum_rights(dump_info->p);
#endif
		}
	}

	pcap_dump((u_char *)dump_info->p, h, sp);
#ifdef HAVE_PCAP_DUMP_FLUSH
	if (Uflag)
		pcap_dump_flush(dump_info->p);
#endif

	--infodelay;
	if (infoprint)
		info(0);
}

static void
dump_packet(u_char *user, const struct pcap_pkthdr *h, const u_char *sp)
{
	++packets_captured;

	++infodelay;

	pcap_dump(user, h, sp);
#ifdef HAVE_PCAP_DUMP_FLUSH
	if (Uflag)
		pcap_dump_flush((pcap_dumper_t *)user);
#endif

	--infodelay;
	if (infoprint)
		info(0);
}

static void
print_packet(u_char *user, const struct pcap_pkthdr *h, const u_char *sp)
{
	struct print_info *print_info;
	u_int hdrlen;
        netdissect_options *ndo;

	++packets_captured;

	++infodelay;

	print_info = (struct print_info *)user;
        ndo = print_info->ndo;

	if(ndo->ndo_packet_number)
		ND_PRINT((ndo, "%5u  ", packets_captured));

	ts_print(ndo, &h->ts);

	/*
	 * Some printers want to check that they're not walking off the
	 * end of the packet.
	 * Rather than pass it all the way down, we set this member
	 * of the netdissect_options structure.
	 */
	ndo->ndo_snapend = sp + h->caplen;

        if(print_info->ndo_type) {
                hdrlen = (*print_info->p.ndo_printer)(print_info->ndo, h, sp);
        } else {
                hdrlen = (*print_info->p.printer)(h, sp);
        }

	/*
	 * Restore the original snapend, as a printer might have
	 * changed it.
	 */
	ndo->ndo_snapend = sp + h->caplen;
	if (ndo->ndo_Xflag) {
		/*
		 * Print the raw packet data in hex and ASCII.
		 */
		if (ndo->ndo_Xflag > 1) {
			/*
			 * Include the link-layer header.
			 */
			hex_and_ascii_print(ndo, "\n\t", sp, h->caplen);
		} else {
			/*
			 * Don't include the link-layer header - and if
			 * we have nothing past the link-layer header,
			 * print nothing.
			 */
			if (h->caplen > hdrlen)
				hex_and_ascii_print(ndo, "\n\t", sp + hdrlen,
				    h->caplen - hdrlen);
		}
	} else if (ndo->ndo_xflag) {
		/*
		 * Print the raw packet data in hex.
		 */
		if (ndo->ndo_xflag > 1) {
			/*
			 * Include the link-layer header.
			 */
                        hex_print(ndo, "\n\t", sp, h->caplen);
		} else {
			/*
			 * Don't include the link-layer header - and if
			 * we have nothing past the link-layer header,
			 * print nothing.
			 */
			if (h->caplen > hdrlen)
				hex_print(ndo, "\n\t", sp + hdrlen,
                                          h->caplen - hdrlen);
		}
	} else if (ndo->ndo_Aflag) {
		/*
		 * Print the raw packet data in ASCII.
		 */
		if (ndo->ndo_Aflag > 1) {
			/*
			 * Include the link-layer header.
			 */
			ascii_print(ndo, sp, h->caplen);
		} else {
			/*
			 * Don't include the link-layer header - and if
			 * we have nothing past the link-layer header,
			 * print nothing.
			 */
			if (h->caplen > hdrlen)
				ascii_print(ndo, sp + hdrlen, h->caplen - hdrlen);
		}
	}

	putchar('\n');

	--infodelay;
	if (infoprint)
		info(0);
}

#ifdef WIN32
	/*
	 * XXX - there should really be libpcap calls to get the version
	 * number as a string (the string would be generated from #defines
	 * at run time, so that it's not generated from string constants
	 * in the library, as, on many UNIX systems, those constants would
	 * be statically linked into the application executable image, and
	 * would thus reflect the version of libpcap on the system on
	 * which the application was *linked*, not the system on which it's
	 * *running*.
	 *
	 * That routine should be documented, unlike the "version[]"
	 * string, so that UNIX vendors providing their own libpcaps
	 * don't omit it (as a couple of vendors have...).
	 *
	 * Packet.dll should perhaps also export a routine to return the
	 * version number of the Packet.dll code, to supply the
	 * "Wpcap_version" information on Windows.
	 */
	char WDversion[]="current-git.tcpdump.org";
#if !defined(HAVE_GENERATED_VERSION)
	char version[]="current-git.tcpdump.org";
#endif
	char pcap_version[]="current-git.tcpdump.org";
	char Wpcap_version[]="3.1";
#endif

/*
 * By default, print the specified data out in hex and ASCII.
 */
static void
ndo_default_print(netdissect_options *ndo, const u_char *bp, u_int length)
{
	hex_and_ascii_print(ndo, "\n\t", bp, length); /* pass on lf and indentation string */
}

void
default_print(const u_char *bp, u_int length)
{
	ndo_default_print(gndo, bp, length);
}

#ifdef SIGNAL_REQ_INFO
RETSIGTYPE requestinfo(int signo _U_)
{
	if (infodelay)
		++infoprint;
	else
		info(0);
}
#endif

/*
 * Called once each second in verbose mode while dumping to file
 */
#ifdef USE_WIN32_MM_TIMER
void CALLBACK verbose_stats_dump (UINT timer_id _U_, UINT msg _U_, DWORD_PTR arg _U_,
				  DWORD_PTR dw1 _U_, DWORD_PTR dw2 _U_)
{
	struct pcap_stat stat;

	if (infodelay == 0 && pcap_stats(pd, &stat) >= 0)
		fprintf(stderr, "Got %u\r", packets_captured);
}
#elif defined(HAVE_ALARM)
static void verbose_stats_dump(int sig _U_)
{
	struct pcap_stat stat;

	if (infodelay == 0 && pcap_stats(pd, &stat) >= 0)
		fprintf(stderr, "Got %u\r", packets_captured);
	alarm(1);
}
#endif

USES_APPLE_DEPRECATED_API
static void
print_version(void)
{
	extern char version[];
#ifndef HAVE_PCAP_LIB_VERSION
#if defined(WIN32) || defined(HAVE_PCAP_VERSION)
	extern char pcap_version[];
#else /* defined(WIN32) || defined(HAVE_PCAP_VERSION) */
	static char pcap_version[] = "unknown";
#endif /* defined(WIN32) || defined(HAVE_PCAP_VERSION) */
#endif /* HAVE_PCAP_LIB_VERSION */

#ifdef HAVE_PCAP_LIB_VERSION
#ifdef WIN32
	(void)fprintf(stderr, "%s version %s, based on tcpdump version %s\n", program_name, WDversion, version);
#else /* WIN32 */
	(void)fprintf(stderr, "%s version %s\n", program_name, version);
#endif /* WIN32 */
	(void)fprintf(stderr, "%s\n",pcap_lib_version());
#else /* HAVE_PCAP_LIB_VERSION */
#ifdef WIN32
	(void)fprintf(stderr, "%s version %s, based on tcpdump version %s\n", program_name, WDversion, version);
	(void)fprintf(stderr, "WinPcap version %s, based on libpcap version %s\n",Wpcap_version, pcap_version);
#else /* WIN32 */
	(void)fprintf(stderr, "%s version %s\n", program_name, version);
	(void)fprintf(stderr, "libpcap version %s\n", pcap_version);
#endif /* WIN32 */
#endif /* HAVE_PCAP_LIB_VERSION */

#if defined(HAVE_LIBCRYPTO) && defined(SSLEAY_VERSION)
	(void)fprintf (stderr, "%s\n", SSLeay_version(SSLEAY_VERSION));
#endif

#ifdef USE_LIBSMI
	(void)fprintf (stderr, "SMI-library: %s\n", smi_version_string);
#endif
}
USES_APPLE_RST

static void
print_usage(void)
{
	print_version();
	(void)fprintf(stderr,
"Usage: %s [-aAbd" D_FLAG "efhH" I_FLAG J_FLAG "KlLnNOpqRStu" U_FLAG "vxX#]" B_FLAG_USAGE " [ -c count ]\n", program_name);
	(void)fprintf(stderr,
"\t\t[ -C file_size ] [ -E algo:secret ] [ -F file ] [ -G seconds ]\n");
	(void)fprintf(stderr,
"\t\t[ -i interface ]" j_FLAG_USAGE " [ -M secret ] [ --number ]\n");
#ifdef HAVE_PCAP_SETDIRECTION
	(void)fprintf(stderr,
"\t\t[ -Q in|out|inout ]\n");
#endif
	(void)fprintf(stderr,
"\t\t[ -r file ] [ -s snaplen ] ");
#ifdef HAVE_PCAP_SET_TSTAMP_PRECISION
	(void)fprintf(stderr, "[ --time-stamp-precision precision ]\n");
	(void)fprintf(stderr,
"\t\t");
#endif
#ifdef HAVE_PCAP_SET_IMMEDIATE_MODE
	(void)fprintf(stderr, "[ --immediate-mode ] ");
#endif
	(void)fprintf(stderr, "[ -T type ] [ --version ] [ -V file ]\n");
	(void)fprintf(stderr,
"\t\t[ -w file ] [ -W filecount ] [ -y datalinktype ] [ -z command ]\n");
	(void)fprintf(stderr,
"\t\t[ -Z user ] [ expression ]\n");
}



/* VARARGS */
static void
ndo_error(netdissect_options *ndo _U_, const char *fmt, ...)
{
	va_list ap;

	(void)fprintf(stderr, "%s: ", program_name);
	va_start(ap, fmt);
	(void)vfprintf(stderr, fmt, ap);
	va_end(ap);
	if (*fmt) {
		fmt += strlen(fmt);
		if (fmt[-1] != '\n')
			(void)fputc('\n', stderr);
	}
	exit(1);
	/* NOTREACHED */
}

/* VARARGS */
static void
ndo_warning(netdissect_options *ndo _U_, const char *fmt, ...)
{
	va_list ap;

	(void)fprintf(stderr, "%s: WARNING: ", program_name);
	va_start(ap, fmt);
	(void)vfprintf(stderr, fmt, ap);
	va_end(ap);
	if (*fmt) {
		fmt += strlen(fmt);
		if (fmt[-1] != '\n')
			(void)fputc('\n', stderr);
	}
}
/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 8
 * End:
 */
