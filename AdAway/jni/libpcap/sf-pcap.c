/*
 * Copyright (c) 1993, 1994, 1995, 1996, 1997
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
 * sf-pcap.c - libpcap-file-format-specific code from savefile.c
 *	Extraction/creation by Jeffrey Mogul, DECWRL
 *	Modified by Steve McCanne, LBL.
 *
 * Used to save the received packet headers, after filtering, to
 * a file, and then read them later.
 * The first record in the file contains saved values for the machine
 * dependent values so we can print the dump file on any architecture.
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header$ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#ifdef WIN32
#include <pcap-stdinc.h>
#else /* WIN32 */
#if HAVE_INTTYPES_H
#include <inttypes.h>
#elif HAVE_STDINT_H
#include <stdint.h>
#endif
#ifdef HAVE_SYS_BITYPES_H
#include <sys/bitypes.h>
#endif
#include <sys/types.h>
#endif /* WIN32 */

#include <errno.h>
#include <memory.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "pcap-int.h"

#include "pcap-common.h"

#ifdef HAVE_OS_PROTO_H
#include "os-proto.h"
#endif

#include "sf-pcap.h"

/*
 * Setting O_BINARY on DOS/Windows is a bit tricky
 */
#if defined(WIN32)
  #define SET_BINMODE(f)  _setmode(_fileno(f), _O_BINARY)
#elif defined(MSDOS)
  #if defined(__HIGHC__)
  #define SET_BINMODE(f)  setmode(f, O_BINARY)
  #else
  #define SET_BINMODE(f)  setmode(fileno(f), O_BINARY)
  #endif
#endif

/*
 * Standard libpcap format.
 */
#define TCPDUMP_MAGIC		0xa1b2c3d4

/*
 * Alexey Kuznetzov's modified libpcap format.
 */
#define KUZNETZOV_TCPDUMP_MAGIC	0xa1b2cd34

/*
 * Reserved for Francisco Mesquita <francisco.mesquita@radiomovel.pt>
 * for another modified format.
 */
#define FMESQUITA_TCPDUMP_MAGIC	0xa1b234cd

/*
 * Navtel Communcations' format, with nanosecond timestamps,
 * as per a request from Dumas Hwang <dumas.hwang@navtelcom.com>.
 */
#define NAVTEL_TCPDUMP_MAGIC	0xa12b3c4d

/*
 * Normal libpcap format, except for seconds/nanoseconds timestamps,
 * as per a request by Ulf Lamping <ulf.lamping@web.de>
 */
#define NSEC_TCPDUMP_MAGIC	0xa1b23c4d

/*
 * Mechanism for storing information about a capture in the upper
 * 6 bits of a linktype value in a capture file.
 *
 * LT_LINKTYPE_EXT(x) extracts the additional information.
 *
 * The rest of the bits are for a value describing the link-layer
 * value.  LT_LINKTYPE(x) extracts that value.
 */
#define LT_LINKTYPE(x)		((x) & 0x03FFFFFF)
#define LT_LINKTYPE_EXT(x)	((x) & 0xFC000000)

static int pcap_next_packet(pcap_t *p, struct pcap_pkthdr *hdr, u_char **datap);

/*
 * Private data for reading pcap savefiles.
 */
typedef enum {
	NOT_SWAPPED,
	SWAPPED,
	MAYBE_SWAPPED
} swapped_type_t;

typedef enum {
	PASS_THROUGH,
	SCALE_UP,
	SCALE_DOWN
} tstamp_scale_type_t;

struct pcap_sf {
	size_t hdrsize;
	swapped_type_t lengths_swapped;
	tstamp_scale_type_t scale_type;
};

/*
 * Check whether this is a pcap savefile and, if it is, extract the
 * relevant information from the header.
 */
pcap_t *
pcap_check_header(bpf_u_int32 magic, FILE *fp, u_int precision, char *errbuf,
    int *err)
{
	struct pcap_file_header hdr;
	size_t amt_read;
	pcap_t *p;
	int swapped = 0;
	struct pcap_sf *ps;

	/*
	 * Assume no read errors.
	 */
	*err = 0;

	/*
	 * Check whether the first 4 bytes of the file are the magic
	 * number for a pcap savefile, or for a byte-swapped pcap
	 * savefile.
	 */
	if (magic != TCPDUMP_MAGIC && magic != KUZNETZOV_TCPDUMP_MAGIC &&
	    magic != NSEC_TCPDUMP_MAGIC) {
		magic = SWAPLONG(magic);
		if (magic != TCPDUMP_MAGIC && magic != KUZNETZOV_TCPDUMP_MAGIC &&
		    magic != NSEC_TCPDUMP_MAGIC)
			return (NULL);	/* nope */
		swapped = 1;
	}

	/*
	 * They are.  Put the magic number in the header, and read
	 * the rest of the header.
	 */
	hdr.magic = magic;
	amt_read = fread(((char *)&hdr) + sizeof hdr.magic, 1,
	    sizeof(hdr) - sizeof(hdr.magic), fp);
	if (amt_read != sizeof(hdr) - sizeof(hdr.magic)) {
		if (ferror(fp)) {
			snprintf(errbuf, PCAP_ERRBUF_SIZE,
			    "error reading dump file: %s",
			    pcap_strerror(errno));
		} else {
			snprintf(errbuf, PCAP_ERRBUF_SIZE,
			    "truncated dump file; tried to read %lu file header bytes, only got %lu",
			    (unsigned long)sizeof(hdr),
			    (unsigned long)amt_read);
		}
		*err = 1;
		return (NULL);
	}

	/*
	 * If it's a byte-swapped capture file, byte-swap the header.
	 */
	if (swapped) {
		hdr.version_major = SWAPSHORT(hdr.version_major);
		hdr.version_minor = SWAPSHORT(hdr.version_minor);
		hdr.thiszone = SWAPLONG(hdr.thiszone);
		hdr.sigfigs = SWAPLONG(hdr.sigfigs);
		hdr.snaplen = SWAPLONG(hdr.snaplen);
		hdr.linktype = SWAPLONG(hdr.linktype);
	}

	if (hdr.version_major < PCAP_VERSION_MAJOR) {
		snprintf(errbuf, PCAP_ERRBUF_SIZE,
		    "archaic pcap savefile format");
		*err = 1;
		return (NULL);
	}

	/*
	 * OK, this is a good pcap file.
	 * Allocate a pcap_t for it.
	 */
	p = pcap_open_offline_common(errbuf, sizeof (struct pcap_sf));
	if (p == NULL) {
		/* Allocation failed. */
		*err = 1;
		return (NULL);
	}
	p->swapped = swapped;
	p->version_major = hdr.version_major;
	p->version_minor = hdr.version_minor;
	p->tzoff = hdr.thiszone;
	p->snapshot = hdr.snaplen;
	p->linktype = linktype_to_dlt(LT_LINKTYPE(hdr.linktype));
	p->linktype_ext = LT_LINKTYPE_EXT(hdr.linktype);

	p->next_packet_op = pcap_next_packet;

	ps = p->priv;

	p->opt.tstamp_precision = precision;

	/*
	 * Will we need to scale the timestamps to match what the
	 * user wants?
	 */
	switch (precision) {

	case PCAP_TSTAMP_PRECISION_MICRO:
		if (magic == NSEC_TCPDUMP_MAGIC) {
			/*
			 * The file has nanoseconds, the user
			 * wants microseconds; scale the
			 * precision down.
			 */
			ps->scale_type = SCALE_DOWN;
		} else {
			/*
			 * The file has microseconds, the
			 * user wants microseconds; nothing to do.
			 */
			ps->scale_type = PASS_THROUGH;
		}
		break;

	case PCAP_TSTAMP_PRECISION_NANO:
		if (magic == NSEC_TCPDUMP_MAGIC) {
			/*
			 * The file has nanoseconds, the
			 * user wants nanoseconds; nothing to do.
			 */
			ps->scale_type = PASS_THROUGH;
		} else {
			/*
			 * The file has microoseconds, the user
			 * wants nanoseconds; scale the
			 * precision up.
			 */
			ps->scale_type = SCALE_UP;
		}
		break;

	default:
		snprintf(errbuf, PCAP_ERRBUF_SIZE,
		    "unknown time stamp resolution %u", precision);
		free(p);
		*err = 1;
		return (NULL);
	}

	/*
	 * We interchanged the caplen and len fields at version 2.3,
	 * in order to match the bpf header layout.  But unfortunately
	 * some files were written with version 2.3 in their headers
	 * but without the interchanged fields.
	 *
	 * In addition, DG/UX tcpdump writes out files with a version
	 * number of 543.0, and with the caplen and len fields in the
	 * pre-2.3 order.
	 */
	switch (hdr.version_major) {

	case 2:
		if (hdr.version_minor < 3)
			ps->lengths_swapped = SWAPPED;
		else if (hdr.version_minor == 3)
			ps->lengths_swapped = MAYBE_SWAPPED;
		else
			ps->lengths_swapped = NOT_SWAPPED;
		break;

	case 543:
		ps->lengths_swapped = SWAPPED;
		break;

	default:
		ps->lengths_swapped = NOT_SWAPPED;
		break;
	}

	if (magic == KUZNETZOV_TCPDUMP_MAGIC) {
		/*
		 * XXX - the patch that's in some versions of libpcap
		 * changes the packet header but not the magic number,
		 * and some other versions with this magic number have
		 * some extra debugging information in the packet header;
		 * we'd have to use some hacks^H^H^H^H^Hheuristics to
		 * detect those variants.
		 *
		 * Ethereal does that, but it does so by trying to read
		 * the first two packets of the file with each of the
		 * record header formats.  That currently means it seeks
		 * backwards and retries the reads, which doesn't work
		 * on pipes.  We want to be able to read from a pipe, so
		 * that strategy won't work; we'd have to buffer some
		 * data ourselves and read from that buffer in order to
		 * make that work.
		 */
		ps->hdrsize = sizeof(struct pcap_sf_patched_pkthdr);

		if (p->linktype == DLT_EN10MB) {
			/*
			 * This capture might have been done in raw mode
			 * or cooked mode.
			 *
			 * If it was done in cooked mode, p->snapshot was
			 * passed to recvfrom() as the buffer size, meaning
			 * that the most packet data that would be copied
			 * would be p->snapshot.  However, a faked Ethernet
			 * header would then have been added to it, so the
			 * most data that would be in a packet in the file
			 * would be p->snapshot + 14.
			 *
			 * We can't easily tell whether the capture was done
			 * in raw mode or cooked mode, so we'll assume it was
			 * cooked mode, and add 14 to the snapshot length.
			 * That means that, for a raw capture, the snapshot
			 * length will be misleading if you use it to figure
			 * out why a capture doesn't have all the packet data,
			 * but there's not much we can do to avoid that.
			 */
			p->snapshot += 14;
		}
	} else
		ps->hdrsize = sizeof(struct pcap_sf_pkthdr);

	/*
	 * Allocate a buffer for the packet data.
	 */
	p->bufsize = p->snapshot;
	if (p->bufsize <= 0) {
		/*
		 * Bogus snapshot length; use the maximum as a fallback.
		 */
		p->bufsize = MAXIMUM_SNAPLEN;
	}
	p->buffer = malloc(p->bufsize);
	if (p->buffer == NULL) {
		snprintf(errbuf, PCAP_ERRBUF_SIZE, "out of memory");
		free(p);
		*err = 1;
		return (NULL);
	}

	p->cleanup_op = sf_cleanup;

	return (p);
}

/*
 * Read and return the next packet from the savefile.  Return the header
 * in hdr and a pointer to the contents in data.  Return 0 on success, 1
 * if there were no more packets, and -1 on an error.
 */
static int
pcap_next_packet(pcap_t *p, struct pcap_pkthdr *hdr, u_char **data)
{
	struct pcap_sf *ps = p->priv;
	struct pcap_sf_patched_pkthdr sf_hdr;
	FILE *fp = p->rfile;
	size_t amt_read;
	bpf_u_int32 t;

	/*
	 * Read the packet header; the structure we use as a buffer
	 * is the longer structure for files generated by the patched
	 * libpcap, but if the file has the magic number for an
	 * unpatched libpcap we only read as many bytes as the regular
	 * header has.
	 */
	amt_read = fread(&sf_hdr, 1, ps->hdrsize, fp);
	if (amt_read != ps->hdrsize) {
		if (ferror(fp)) {
			snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
			    "error reading dump file: %s",
			    pcap_strerror(errno));
			return (-1);
		} else {
			if (amt_read != 0) {
				snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
				    "truncated dump file; tried to read %lu header bytes, only got %lu",
				    (unsigned long)ps->hdrsize,
				    (unsigned long)amt_read);
				return (-1);
			}
			/* EOF */
			return (1);
		}
	}

	if (p->swapped) {
		/* these were written in opposite byte order */
		hdr->caplen = SWAPLONG(sf_hdr.caplen);
		hdr->len = SWAPLONG(sf_hdr.len);
		hdr->ts.tv_sec = SWAPLONG(sf_hdr.ts.tv_sec);
		hdr->ts.tv_usec = SWAPLONG(sf_hdr.ts.tv_usec);
	} else {
		hdr->caplen = sf_hdr.caplen;
		hdr->len = sf_hdr.len;
		hdr->ts.tv_sec = sf_hdr.ts.tv_sec;
		hdr->ts.tv_usec = sf_hdr.ts.tv_usec;
	}

	switch (ps->scale_type) {

	case PASS_THROUGH:
		/*
		 * Just pass the time stamp through.
		 */
		break;

	case SCALE_UP:
		/*
		 * File has microseconds, user wants nanoseconds; convert
		 * it.
		 */
		hdr->ts.tv_usec = hdr->ts.tv_usec * 1000;
		break;

	case SCALE_DOWN:
		/*
		 * File has nanoseconds, user wants microseconds; convert
		 * it.
		 */
		hdr->ts.tv_usec = hdr->ts.tv_usec / 1000;
		break;
	}

	/* Swap the caplen and len fields, if necessary. */
	switch (ps->lengths_swapped) {

	case NOT_SWAPPED:
		break;

	case MAYBE_SWAPPED:
		if (hdr->caplen <= hdr->len) {
			/*
			 * The captured length is <= the actual length,
			 * so presumably they weren't swapped.
			 */
			break;
		}
		/* FALLTHROUGH */

	case SWAPPED:
		t = hdr->caplen;
		hdr->caplen = hdr->len;
		hdr->len = t;
		break;
	}

	if (hdr->caplen > p->bufsize) {
		/*
		 * This can happen due to Solaris 2.3 systems tripping
		 * over the BUFMOD problem and not setting the snapshot
		 * correctly in the savefile header.  If the caplen isn't
		 * grossly wrong, try to salvage.
		 */
		static u_char *tp = NULL;
		static size_t tsize = 0;

		if (hdr->caplen > MAXIMUM_SNAPLEN) {
			snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
			    "bogus savefile header");
			return (-1);
		}

		if (tsize < hdr->caplen) {
			tsize = ((hdr->caplen + 1023) / 1024) * 1024;
			if (tp != NULL)
				free((u_char *)tp);
			tp = (u_char *)malloc(tsize);
			if (tp == NULL) {
				tsize = 0;
				snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
				    "BUFMOD hack malloc");
				return (-1);
			}
		}
		amt_read = fread((char *)tp, 1, hdr->caplen, fp);
		if (amt_read != hdr->caplen) {
			if (ferror(fp)) {
				snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
				    "error reading dump file: %s",
				    pcap_strerror(errno));
			} else {
				snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
				    "truncated dump file; tried to read %u captured bytes, only got %lu",
				    hdr->caplen, (unsigned long)amt_read);
			}
			return (-1);
		}
		/*
		 * We can only keep up to p->bufsize bytes.  Since
		 * caplen > p->bufsize is exactly how we got here,
		 * we know we can only keep the first p->bufsize bytes
		 * and must drop the remainder.  Adjust caplen accordingly,
		 * so we don't get confused later as to how many bytes we
		 * have to play with.
		 */
		hdr->caplen = p->bufsize;
		memcpy(p->buffer, (char *)tp, p->bufsize);
	} else {
		/* read the packet itself */
		amt_read = fread(p->buffer, 1, hdr->caplen, fp);
		if (amt_read != hdr->caplen) {
			if (ferror(fp)) {
				snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
				    "error reading dump file: %s",
				    pcap_strerror(errno));
			} else {
				snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
				    "truncated dump file; tried to read %u captured bytes, only got %lu",
				    hdr->caplen, (unsigned long)amt_read);
			}
			return (-1);
		}
	}
	*data = p->buffer;

	if (p->swapped)
		swap_pseudo_headers(p->linktype, hdr, *data);

	return (0);
}

static int
sf_write_header(pcap_t *p, FILE *fp, int linktype, int thiszone, int snaplen)
{
	struct pcap_file_header hdr;

	hdr.magic = p->opt.tstamp_precision == PCAP_TSTAMP_PRECISION_NANO ? NSEC_TCPDUMP_MAGIC : TCPDUMP_MAGIC;
	hdr.version_major = PCAP_VERSION_MAJOR;
	hdr.version_minor = PCAP_VERSION_MINOR;

	hdr.thiszone = thiszone;
	hdr.snaplen = snaplen;
	hdr.sigfigs = 0;
	hdr.linktype = linktype;

	if (fwrite((char *)&hdr, sizeof(hdr), 1, fp) != 1)
		return (-1);

	return (0);
}

/*
 * Output a packet to the initialized dump file.
 */
void
pcap_dump(u_char *user, const struct pcap_pkthdr *h, const u_char *sp)
{
	register FILE *f;
	struct pcap_sf_pkthdr sf_hdr;

	f = (FILE *)user;
	sf_hdr.ts.tv_sec  = h->ts.tv_sec;
	sf_hdr.ts.tv_usec = h->ts.tv_usec;
	sf_hdr.caplen     = h->caplen;
	sf_hdr.len        = h->len;
	/* XXX we should check the return status */
	(void)fwrite(&sf_hdr, sizeof(sf_hdr), 1, f);
	(void)fwrite(sp, h->caplen, 1, f);
}

static pcap_dumper_t *
pcap_setup_dump(pcap_t *p, int linktype, FILE *f, const char *fname)
{

#if defined(WIN32) || defined(MSDOS)
	/*
	 * If we're writing to the standard output, put it in binary
	 * mode, as savefiles are binary files.
	 *
	 * Otherwise, we turn off buffering.
	 * XXX - why?  And why not on the standard output?
	 */
	if (f == stdout)
		SET_BINMODE(f);
	else
		setbuf(f, NULL);
#endif
	if (sf_write_header(p, f, linktype, p->tzoff, p->snapshot) == -1) {
		snprintf(p->errbuf, PCAP_ERRBUF_SIZE, "Can't write to %s: %s",
		    fname, pcap_strerror(errno));
		if (f != stdout)
			(void)fclose(f);
		return (NULL);
	}
	return ((pcap_dumper_t *)f);
}

/*
 * Initialize so that sf_write() will output to the file named 'fname'.
 */
pcap_dumper_t *
pcap_dump_open(pcap_t *p, const char *fname)
{
	FILE *f;
	int linktype;

	/*
	 * If this pcap_t hasn't been activated, it doesn't have a
	 * link-layer type, so we can't use it.
	 */
	if (!p->activated) {
		snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
		    "%s: not-yet-activated pcap_t passed to pcap_dump_open",
		    fname);
		return (NULL);
	}
	linktype = dlt_to_linktype(p->linktype);
	if (linktype == -1) {
		snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
		    "%s: link-layer type %d isn't supported in savefiles",
		    fname, p->linktype);
		return (NULL);
	}
	linktype |= p->linktype_ext;

	if (fname[0] == '-' && fname[1] == '\0') {
		f = stdout;
		fname = "standard output";
	} else {
#if !defined(WIN32) && !defined(MSDOS)
		f = fopen(fname, "w");
#else
		f = fopen(fname, "wb");
#endif
		if (f == NULL) {
			snprintf(p->errbuf, PCAP_ERRBUF_SIZE, "%s: %s",
			    fname, pcap_strerror(errno));
			return (NULL);
		}
	}
	return (pcap_setup_dump(p, linktype, f, fname));
}

/*
 * Initialize so that sf_write() will output to the given stream.
 */
pcap_dumper_t *
pcap_dump_fopen(pcap_t *p, FILE *f)
{
	int linktype;

	linktype = dlt_to_linktype(p->linktype);
	if (linktype == -1) {
		snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
		    "stream: link-layer type %d isn't supported in savefiles",
		    p->linktype);
		return (NULL);
	}
	linktype |= p->linktype_ext;

	return (pcap_setup_dump(p, linktype, f, "stream"));
}

pcap_dumper_t *
pcap_dump_open_append(pcap_t *p, const char *fname)
{
	FILE *f;
	int linktype;
	int amt_read;
	struct pcap_file_header ph;

	linktype = dlt_to_linktype(p->linktype);
	if (linktype == -1) {
		snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
		    "%s: link-layer type %d isn't supported in savefiles",
		    fname, linktype);
		return (NULL);
	}
	if (fname[0] == '-' && fname[1] == '\0')
		return (pcap_setup_dump(p, linktype, stdout, "standard output"));

#if !defined(WIN32) && !defined(MSDOS)
	f = fopen(fname, "r+");
#else
	f = fopen(fname, "rb+");
#endif
	if (f == NULL) {
		snprintf(p->errbuf, PCAP_ERRBUF_SIZE, "%s: %s",
		    fname, pcap_strerror(errno));
		return (NULL);
	}

	/*
	 * Try to read a pcap header.
	 */
	amt_read = fread(&ph, 1, sizeof (ph), f);
	if (amt_read != sizeof (ph)) {
		if (ferror(f)) {
			snprintf(p->errbuf, PCAP_ERRBUF_SIZE, "%s: %s",
			    fname, pcap_strerror(errno));
			fclose(f);
			return (NULL);
		} else if (feof(f) && amt_read > 0) {
			snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
			    "%s: truncated pcap file header", fname);
			fclose(f);
			return (NULL);
		}
	}

#if defined(WIN32) || defined(MSDOS)
	/*
	 * We turn off buffering.
	 * XXX - why?  And why not on the standard output?
	 */
	setbuf(f, NULL);
#endif

	/*
	 * If a header is already present and:
	 *
	 *	it's not for a pcap file of the appropriate resolution
	 *	and the right byte order for this machine;
	 *
	 *	the link-layer header types don't match;
	 *
	 *	the snapshot lengths don't match;
	 *
	 * return an error.
	 */
	if (amt_read > 0) {
		/*
		 * A header is already present.
		 * Do the checks.
		 */
		switch (ph.magic) {

		case TCPDUMP_MAGIC:
			if (p->opt.tstamp_precision != PCAP_TSTAMP_PRECISION_MICRO) {
				snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
				    "%s: different time stamp precision, cannot append to file", fname);
				fclose(f);
				return (NULL);
			}
			break;

		case NSEC_TCPDUMP_MAGIC:
			if (p->opt.tstamp_precision != PCAP_TSTAMP_PRECISION_NANO) {
				snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
				    "%s: different time stamp precision, cannot append to file", fname);
				fclose(f);
				return (NULL);
			}
			break;

		case SWAPLONG(TCPDUMP_MAGIC):
		case SWAPLONG(NSEC_TCPDUMP_MAGIC):
			snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
			    "%s: different byte order, cannot append to file", fname);
			fclose(f);
			return (NULL);

		case KUZNETZOV_TCPDUMP_MAGIC:
		case SWAPLONG(KUZNETZOV_TCPDUMP_MAGIC):
		case NAVTEL_TCPDUMP_MAGIC:
		case SWAPLONG(NAVTEL_TCPDUMP_MAGIC):
			snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
			    "%s: not a pcap file to which we can append", fname);
			fclose(f);
			return (NULL);

		default:
			snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
			    "%s: not a pcap file", fname);
			fclose(f);
			return (NULL);
		}

		/*
		 * Good version?
		 */
		if (ph.version_major != PCAP_VERSION_MAJOR ||
		    ph.version_minor != PCAP_VERSION_MINOR) {
			snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
			    "%s: version is %u.%u, cannot append to file", fname,
			    ph.version_major, ph.version_minor);
			fclose(f);
			return (NULL);
		}
		if (linktype != ph.linktype) {
			snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
			    "%s: different linktype, cannot append to file", fname);
			fclose(f);
			return (NULL);
		}
		if (p->snapshot != ph.snaplen) {
			snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
			    "%s: different snaplen, cannot append to file", fname);
			fclose(f);
			return (NULL);
		}
	} else {
		/*
		 * A header isn't present; attempt to write it.
		 */
		if (sf_write_header(p, f, linktype, p->tzoff, p->snapshot) == -1) {
			snprintf(p->errbuf, PCAP_ERRBUF_SIZE, "Can't write to %s: %s",
			    fname, pcap_strerror(errno));
			(void)fclose(f);
			return (NULL);
		}
	}

	/*
	 * Start writing at the end of the file.
	 */
	if (fseek(f, 0, SEEK_END) == -1) {
		snprintf(p->errbuf, PCAP_ERRBUF_SIZE, "Can't seek to end of %s: %s",
		    fname, pcap_strerror(errno));
		(void)fclose(f);
		return (NULL);
	}
	return ((pcap_dumper_t *)f);
}

FILE *
pcap_dump_file(pcap_dumper_t *p)
{
	return ((FILE *)p);
}

long
pcap_dump_ftell(pcap_dumper_t *p)
{
	return (ftell((FILE *)p));
}

int
pcap_dump_flush(pcap_dumper_t *p)
{

	if (fflush((FILE *)p) == EOF)
		return (-1);
	else
		return (0);
}

void
pcap_dump_close(pcap_dumper_t *p)
{

#ifdef notyet
	if (ferror((FILE *)p))
		return-an-error;
	/* XXX should check return from fclose() too */
#endif
	(void)fclose((FILE *)p);
}
