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
 * sf-pcap-ng.c - pcap-ng-file-format-specific code from savefile.c
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

#include "sf-pcap-ng.h"

/*
 * Block types.
 */

/*
 * Common part at the beginning of all blocks.
 */
struct block_header {
	bpf_u_int32	block_type;
	bpf_u_int32	total_length;
};

/*
 * Common trailer at the end of all blocks.
 */
struct block_trailer {
	bpf_u_int32	total_length;
};

/*
 * Common options.
 */
#define OPT_ENDOFOPT	0	/* end of options */
#define OPT_COMMENT	1	/* comment string */

/*
 * Option header.
 */
struct option_header {
	u_short		option_code;
	u_short		option_length;
};

/*
 * Structures for the part of each block type following the common
 * part.
 */

/*
 * Section Header Block.
 */
#define BT_SHB			0x0A0D0D0A

struct section_header_block {
	bpf_u_int32	byte_order_magic;
	u_short		major_version;
	u_short		minor_version;
	u_int64_t	section_length;
	/* followed by options and trailer */
};

/*
 * Byte-order magic value.
 */
#define BYTE_ORDER_MAGIC	0x1A2B3C4D

/*
 * Current version number.  If major_version isn't PCAP_NG_VERSION_MAJOR,
 * that means that this code can't read the file.
 */
#define PCAP_NG_VERSION_MAJOR	1

/*
 * Interface Description Block.
 */
#define BT_IDB			0x00000001

struct interface_description_block {
	u_short		linktype;
	u_short		reserved;
	bpf_u_int32	snaplen;
	/* followed by options and trailer */
};

/*
 * Options in the IDB.
 */
#define IF_NAME		2	/* interface name string */
#define IF_DESCRIPTION	3	/* interface description string */
#define IF_IPV4ADDR	4	/* interface's IPv4 address and netmask */
#define IF_IPV6ADDR	5	/* interface's IPv6 address and prefix length */
#define IF_MACADDR	6	/* interface's MAC address */
#define IF_EUIADDR	7	/* interface's EUI address */
#define IF_SPEED	8	/* interface's speed, in bits/s */
#define IF_TSRESOL	9	/* interface's time stamp resolution */
#define IF_TZONE	10	/* interface's time zone */
#define IF_FILTER	11	/* filter used when capturing on interface */
#define IF_OS		12	/* string OS on which capture on this interface was done */
#define IF_FCSLEN	13	/* FCS length for this interface */
#define IF_TSOFFSET	14	/* time stamp offset for this interface */

/*
 * Enhanced Packet Block.
 */
#define BT_EPB			0x00000006

struct enhanced_packet_block {
	bpf_u_int32	interface_id;
	bpf_u_int32	timestamp_high;
	bpf_u_int32	timestamp_low;
	bpf_u_int32	caplen;
	bpf_u_int32	len;
	/* followed by packet data, options, and trailer */
};

/*
 * Simple Packet Block.
 */
#define BT_SPB			0x00000003

struct simple_packet_block {
	bpf_u_int32	len;
	/* followed by packet data and trailer */
};

/*
 * Packet Block.
 */
#define BT_PB			0x00000002

struct packet_block {
	u_short		interface_id;
	u_short		drops_count;
	bpf_u_int32	timestamp_high;
	bpf_u_int32	timestamp_low;
	bpf_u_int32	caplen;
	bpf_u_int32	len;
	/* followed by packet data, options, and trailer */
};

/*
 * Block cursor - used when processing the contents of a block.
 * Contains a pointer into the data being processed and a count
 * of bytes remaining in the block.
 */
struct block_cursor {
	u_char		*data;
	size_t		data_remaining;
	bpf_u_int32	block_type;
};

typedef enum {
	PASS_THROUGH,
	SCALE_UP,
	SCALE_DOWN
} tstamp_scale_type_t;

/*
 * Per-interface information.
 */
struct pcap_ng_if {
	u_int tsresol;			/* time stamp resolution */
	u_int64_t tsoffset;		/* time stamp offset */
	tstamp_scale_type_t scale_type;	/* how to scale */
};

struct pcap_ng_sf {
	u_int user_tsresol;		/* time stamp resolution requested by the user */
	bpf_u_int32 ifcount;		/* number of interfaces seen in this capture */
	bpf_u_int32 ifaces_size;	/* size of arrary below */
	struct pcap_ng_if *ifaces;	/* array of interface information */
};

static void pcap_ng_cleanup(pcap_t *p);
static int pcap_ng_next_packet(pcap_t *p, struct pcap_pkthdr *hdr,
    u_char **data);

static int
read_bytes(FILE *fp, void *buf, size_t bytes_to_read, int fail_on_eof,
    char *errbuf)
{
	size_t amt_read;

	amt_read = fread(buf, 1, bytes_to_read, fp);
	if (amt_read != bytes_to_read) {
		if (ferror(fp)) {
			snprintf(errbuf, PCAP_ERRBUF_SIZE,
			    "error reading dump file: %s",
			    pcap_strerror(errno));
		} else {
			if (amt_read == 0 && !fail_on_eof)
				return (0);	/* EOF */
			snprintf(errbuf, PCAP_ERRBUF_SIZE,
			    "truncated dump file; tried to read %lu bytes, only got %lu",
			    (unsigned long)bytes_to_read,
			    (unsigned long)amt_read);
		}
		return (-1);
	}
	return (1);
}

static int
read_block(FILE *fp, pcap_t *p, struct block_cursor *cursor, char *errbuf)
{
	int status;
	struct block_header bhdr;

	status = read_bytes(fp, &bhdr, sizeof(bhdr), 0, errbuf);
	if (status <= 0)
		return (status);	/* error or EOF */

	if (p->swapped) {
		bhdr.block_type = SWAPLONG(bhdr.block_type);
		bhdr.total_length = SWAPLONG(bhdr.total_length);
	}

	/*
	 * Is this block "too big"?
	 *
	 * We choose 16MB as "too big", for now, so that we handle
	 * "reasonably" large buffers but don't chew up all the
	 * memory if we read a malformed file.
	 */
	if (bhdr.total_length > 16*1024*1024) {
		snprintf(errbuf, PCAP_ERRBUF_SIZE,
		    "pcap-ng block size %u > maximum %u",
		    bhdr.total_length, 16*1024*1024);
		    return (-1);
	}

	/*
	 * Is this block "too small" - i.e., is it shorter than a block
	 * header plus a block trailer?
	 */
	if (bhdr.total_length < sizeof(struct block_header) +
	    sizeof(struct block_trailer)) {
		snprintf(errbuf, PCAP_ERRBUF_SIZE,
		    "block in pcap-ng dump file has a length of %u < %lu",
		    bhdr.total_length,
		    (unsigned long)(sizeof(struct block_header) + sizeof(struct block_trailer)));
		return (-1);
	}

	/*
	 * Is the buffer big enough?
	 */
	if (p->bufsize < bhdr.total_length) {
		/*
		 * No - make it big enough.
		 */
		p->buffer = realloc(p->buffer, bhdr.total_length);
		if (p->buffer == NULL) {
			snprintf(errbuf, PCAP_ERRBUF_SIZE, "out of memory");
			return (-1);
		}
	}

	/*
	 * Copy the stuff we've read to the buffer, and read the rest
	 * of the block.
	 */
	memcpy(p->buffer, &bhdr, sizeof(bhdr));
	if (read_bytes(fp, p->buffer + sizeof(bhdr),
	    bhdr.total_length - sizeof(bhdr), 1, errbuf) == -1)
		return (-1);

	/*
	 * Initialize the cursor.
	 */
	cursor->data = p->buffer + sizeof(bhdr);
	cursor->data_remaining = bhdr.total_length - sizeof(bhdr) -
	    sizeof(struct block_trailer);
	cursor->block_type = bhdr.block_type;
	return (1);
}

static void *
get_from_block_data(struct block_cursor *cursor, size_t chunk_size,
    char *errbuf)
{
	void *data;

	/*
	 * Make sure we have the specified amount of data remaining in
	 * the block data.
	 */
	if (cursor->data_remaining < chunk_size) {
		snprintf(errbuf, PCAP_ERRBUF_SIZE,
		    "block of type %u in pcap-ng dump file is too short",
		    cursor->block_type);
		return (NULL);
	}

	/*
	 * Return the current pointer, and skip past the chunk.
	 */
	data = cursor->data;
	cursor->data += chunk_size;
	cursor->data_remaining -= chunk_size;
	return (data);
}

static struct option_header *
get_opthdr_from_block_data(pcap_t *p, struct block_cursor *cursor, char *errbuf)
{
	struct option_header *opthdr;

	opthdr = get_from_block_data(cursor, sizeof(*opthdr), errbuf);
	if (opthdr == NULL) {
		/*
		 * Option header is cut short.
		 */
		return (NULL);
	}

	/*
	 * Byte-swap it if necessary.
	 */
	if (p->swapped) {
		opthdr->option_code = SWAPSHORT(opthdr->option_code);
		opthdr->option_length = SWAPSHORT(opthdr->option_length);
	}

	return (opthdr);
}

static void *
get_optvalue_from_block_data(struct block_cursor *cursor,
    struct option_header *opthdr, char *errbuf)
{
	size_t padded_option_len;
	void *optvalue;

	/* Pad option length to 4-byte boundary */
	padded_option_len = opthdr->option_length;
	padded_option_len = ((padded_option_len + 3)/4)*4;

	optvalue = get_from_block_data(cursor, padded_option_len, errbuf);
	if (optvalue == NULL) {
		/*
		 * Option value is cut short.
		 */
		return (NULL);
	}

	return (optvalue);
}

static int
process_idb_options(pcap_t *p, struct block_cursor *cursor, u_int *tsresol,
    u_int64_t *tsoffset, char *errbuf)
{
	struct option_header *opthdr;
	void *optvalue;
	int saw_tsresol, saw_tsoffset;
	u_char tsresol_opt;
	u_int i;

	saw_tsresol = 0;
	saw_tsoffset = 0;
	while (cursor->data_remaining != 0) {
		/*
		 * Get the option header.
		 */
		opthdr = get_opthdr_from_block_data(p, cursor, errbuf);
		if (opthdr == NULL) {
			/*
			 * Option header is cut short.
			 */
			return (-1);
		}

		/*
		 * Get option value.
		 */
		optvalue = get_optvalue_from_block_data(cursor, opthdr,
		    errbuf);
		if (optvalue == NULL) {
			/*
			 * Option value is cut short.
			 */
			return (-1);
		}

		switch (opthdr->option_code) {

		case OPT_ENDOFOPT:
			if (opthdr->option_length != 0) {
				snprintf(errbuf, PCAP_ERRBUF_SIZE,
				    "Interface Description Block has opt_endofopt option with length %u != 0",
				    opthdr->option_length);
				return (-1);
			}
			goto done;

		case IF_TSRESOL:
			if (opthdr->option_length != 1) {
				snprintf(errbuf, PCAP_ERRBUF_SIZE,
				    "Interface Description Block has if_tsresol option with length %u != 1",
				    opthdr->option_length);
				return (-1);
			}
			if (saw_tsresol) {
				snprintf(errbuf, PCAP_ERRBUF_SIZE,
				    "Interface Description Block has more than one if_tsresol option");
				return (-1);
			}
			saw_tsresol = 1;
			memcpy(&tsresol_opt, optvalue, sizeof(tsresol_opt));
			if (tsresol_opt & 0x80) {
				/*
				 * Resolution is negative power of 2.
				 */
				*tsresol = 1 << (tsresol_opt & 0x7F);
			} else {
				/*
				 * Resolution is negative power of 10.
				 */
				*tsresol = 1;
				for (i = 0; i < tsresol_opt; i++)
					*tsresol *= 10;
			}
			if (*tsresol == 0) {
				/*
				 * Resolution is too high.
				 */
				if (tsresol_opt & 0x80) {
					snprintf(errbuf, PCAP_ERRBUF_SIZE,
					    "Interface Description Block if_tsresol option resolution 2^-%u is too high",
					    tsresol_opt & 0x7F);
				} else {
					snprintf(errbuf, PCAP_ERRBUF_SIZE,
					    "Interface Description Block if_tsresol option resolution 10^-%u is too high",
					    tsresol_opt);
				}
				return (-1);
			}
			break;

		case IF_TSOFFSET:
			if (opthdr->option_length != 8) {
				snprintf(errbuf, PCAP_ERRBUF_SIZE,
				    "Interface Description Block has if_tsoffset option with length %u != 8",
				    opthdr->option_length);
				return (-1);
			}
			if (saw_tsoffset) {
				snprintf(errbuf, PCAP_ERRBUF_SIZE,
				    "Interface Description Block has more than one if_tsoffset option");
				return (-1);
			}
			saw_tsoffset = 1;
			memcpy(tsoffset, optvalue, sizeof(*tsoffset));
			if (p->swapped)
				*tsoffset = SWAPLL(*tsoffset);
			break;

		default:
			break;
		}
	}

done:
	return (0);
}

static int
add_interface(pcap_t *p, struct block_cursor *cursor, char *errbuf)
{
	struct pcap_ng_sf *ps;
	u_int tsresol;
	u_int64_t tsoffset;

	ps = p->priv;

	/*
	 * Count this interface.
	 */
	ps->ifcount++;

	/*
	 * Grow the array of per-interface information as necessary.
	 */
	if (ps->ifcount > ps->ifaces_size) {
		/*
		 * We need to grow the array.
		 */
		if (ps->ifaces == NULL) {
			/*
			 * It's currently empty.
			 */
			ps->ifaces_size = 1;
			ps->ifaces = malloc(sizeof (struct pcap_ng_if));
		} else {
			/*
			 * It's not currently empty; double its size.
			 * (Perhaps overkill once we have a lot of interfaces.)
			 */
			ps->ifaces_size *= 2;
			ps->ifaces = realloc(ps->ifaces, ps->ifaces_size * sizeof (struct pcap_ng_if));
		}
		if (ps->ifaces == NULL) {
			/*
			 * We ran out of memory.
			 * Give up.
			 */
			snprintf(errbuf, PCAP_ERRBUF_SIZE,
			    "out of memory for per-interface information (%u interfaces)",
			    ps->ifcount);
			return (0);
		}
	}

	/*
	 * Set the default time stamp resolution and offset.
	 */
	tsresol = 1000000;	/* microsecond resolution */
	tsoffset = 0;		/* absolute timestamps */

	/*
	 * Now look for various time stamp options, so we know
	 * how to interpret the time stamps for this interface.
	 */
	if (process_idb_options(p, cursor, &tsresol, &tsoffset, errbuf) == -1)
		return (0);

	ps->ifaces[ps->ifcount - 1].tsresol = tsresol;
	ps->ifaces[ps->ifcount - 1].tsoffset = tsoffset;

	/*
	 * Determine whether we're scaling up or down or not
	 * at all for this interface.
	 */
	switch (p->opt.tstamp_precision) {

	case PCAP_TSTAMP_PRECISION_MICRO:
		if (tsresol == 1000000) {
			/*
			 * The resolution is 1 microsecond,
			 * so we don't have to do scaling.
			 */
			ps->ifaces[ps->ifcount - 1].scale_type = PASS_THROUGH;
		} else if (tsresol > 1000000) {
			/*
			 * The resolution is greater than
			 * 1 microsecond, so we have to
			 * scale the timestamps down.
			 */
			ps->ifaces[ps->ifcount - 1].scale_type = SCALE_DOWN;
		} else {
			/*
			 * The resolution is less than 1
			 * microsecond, so we have to scale
			 * the timestamps up.
			 */
			ps->ifaces[ps->ifcount - 1].scale_type = SCALE_UP;
		}
		break;

	case PCAP_TSTAMP_PRECISION_NANO:
		if (tsresol == 1000000000) {
			/*
			 * The resolution is 1 nanosecond,
			 * so we don't have to do scaling.
			 */
			ps->ifaces[ps->ifcount - 1].scale_type = PASS_THROUGH;
		} else if (tsresol > 1000000000) {
			/*
			 * The resolution is greater than
			 * 1 nanosecond, so we have to
			 * scale the timestamps down.
			 */
			ps->ifaces[ps->ifcount - 1].scale_type = SCALE_DOWN;
		} else {
			/*
			 * The resolution is less than 1
			 * nanosecond, so we have to scale
			 * the timestamps up.
			 */
			ps->ifaces[ps->ifcount - 1].scale_type = SCALE_UP;
		}
		break;
	}
	return (1);
}

/*
 * Check whether this is a pcap-ng savefile and, if it is, extract the
 * relevant information from the header.
 */
pcap_t *
pcap_ng_check_header(bpf_u_int32 magic, FILE *fp, u_int precision, char *errbuf,
    int *err)
{
	size_t amt_read;
	bpf_u_int32 total_length;
	bpf_u_int32 byte_order_magic;
	struct block_header *bhdrp;
	struct section_header_block *shbp;
	pcap_t *p;
	int swapped = 0;
	struct pcap_ng_sf *ps;
	int status;
	struct block_cursor cursor;
	struct interface_description_block *idbp;

	/*
	 * Assume no read errors.
	 */
	*err = 0;

	/*
	 * Check whether the first 4 bytes of the file are the block
	 * type for a pcap-ng savefile.
	 */
	if (magic != BT_SHB) {
		/*
		 * XXX - check whether this looks like what the block
		 * type would be after being munged by mapping between
		 * UN*X and DOS/Windows text file format and, if it
		 * does, look for the byte-order magic number in
		 * the appropriate place and, if we find it, report
		 * this as possibly being a pcap-ng file transferred
		 * between UN*X and Windows in text file format?
		 */
		return (NULL);	/* nope */
	}

	/*
	 * OK, they are.  However, that's just \n\r\r\n, so it could,
	 * conceivably, be an ordinary text file.
	 *
	 * It could not, however, conceivably be any other type of
	 * capture file, so we can read the rest of the putative
	 * Section Header Block; put the block type in the common
	 * header, read the rest of the common header and the
	 * fixed-length portion of the SHB, and look for the byte-order
	 * magic value.
	 */
	amt_read = fread(&total_length, 1, sizeof(total_length), fp);
	if (amt_read < sizeof(total_length)) {
		if (ferror(fp)) {
			snprintf(errbuf, PCAP_ERRBUF_SIZE,
			    "error reading dump file: %s",
			    pcap_strerror(errno));
			*err = 1;
			return (NULL);	/* fail */
		}

		/*
		 * Possibly a weird short text file, so just say
		 * "not pcap-ng".
		 */
		return (NULL);
	}
	amt_read = fread(&byte_order_magic, 1, sizeof(byte_order_magic), fp);
	if (amt_read < sizeof(byte_order_magic)) {
		if (ferror(fp)) {
			snprintf(errbuf, PCAP_ERRBUF_SIZE,
			    "error reading dump file: %s",
			    pcap_strerror(errno));
			*err = 1;
			return (NULL);	/* fail */
		}

		/*
		 * Possibly a weird short text file, so just say
		 * "not pcap-ng".
		 */
		return (NULL);
	}
	if (byte_order_magic != BYTE_ORDER_MAGIC) {
		byte_order_magic = SWAPLONG(byte_order_magic);
		if (byte_order_magic != BYTE_ORDER_MAGIC) {
			/*
			 * Not a pcap-ng file.
			 */
			return (NULL);
		}
		swapped = 1;
		total_length = SWAPLONG(total_length);
	}

	/*
	 * Check the sanity of the total length.
	 */
	if (total_length < sizeof(*bhdrp) + sizeof(*shbp) + sizeof(struct block_trailer)) {
		snprintf(errbuf, PCAP_ERRBUF_SIZE,
		    "Section Header Block in pcap-ng dump file has a length of %u < %lu",
		    total_length,
		    (unsigned long)(sizeof(*bhdrp) + sizeof(*shbp) + sizeof(struct block_trailer)));
		*err = 1;
		return (NULL);
	}

	/*
	 * OK, this is a good pcap-ng file.
	 * Allocate a pcap_t for it.
	 */
	p = pcap_open_offline_common(errbuf, sizeof (struct pcap_ng_sf));
	if (p == NULL) {
		/* Allocation failed. */
		*err = 1;
		return (NULL);
	}
	p->swapped = swapped;
	ps = p->priv;

	/*
	 * What precision does the user want?
	 */
	switch (precision) {

	case PCAP_TSTAMP_PRECISION_MICRO:
		ps->user_tsresol = 1000000;
		break;

	case PCAP_TSTAMP_PRECISION_NANO:
		ps->user_tsresol = 1000000000;
		break;

	default:
		snprintf(errbuf, PCAP_ERRBUF_SIZE,
		    "unknown time stamp resolution %u", precision);
		free(p);
		*err = 1;
		return (NULL);
	}

	p->opt.tstamp_precision = precision;

	/*
	 * Allocate a buffer into which to read blocks.  We default to
	 * the maximum of:
	 *
	 *	the total length of the SHB for which we read the header;
	 *
	 *	2K, which should be more than large enough for an Enhanced
	 *	Packet Block containing a full-size Ethernet frame, and
	 *	leaving room for some options.
	 *
	 * If we find a bigger block, we reallocate the buffer.
	 */
	p->bufsize = 2048;
	if (p->bufsize < total_length)
		p->bufsize = total_length;
	p->buffer = malloc(p->bufsize);
	if (p->buffer == NULL) {
		snprintf(errbuf, PCAP_ERRBUF_SIZE, "out of memory");
		free(p);
		*err = 1;
		return (NULL);
	}

	/*
	 * Copy the stuff we've read to the buffer, and read the rest
	 * of the SHB.
	 */
	bhdrp = (struct block_header *)p->buffer;
	shbp = (struct section_header_block *)(p->buffer + sizeof(struct block_header));
	bhdrp->block_type = magic;
	bhdrp->total_length = total_length;
	shbp->byte_order_magic = byte_order_magic;
	if (read_bytes(fp,
	    p->buffer + (sizeof(magic) + sizeof(total_length) + sizeof(byte_order_magic)),
	    total_length - (sizeof(magic) + sizeof(total_length) + sizeof(byte_order_magic)),
	    1, errbuf) == -1)
		goto fail;

	if (p->swapped) {
		/*
		 * Byte-swap the fields we've read.
		 */
		shbp->major_version = SWAPSHORT(shbp->major_version);
		shbp->minor_version = SWAPSHORT(shbp->minor_version);

		/*
		 * XXX - we don't care about the section length.
		 */
	}
	if (shbp->major_version != PCAP_NG_VERSION_MAJOR) {
		snprintf(errbuf, PCAP_ERRBUF_SIZE,
		    "unknown pcap-ng savefile major version number %u",
		    shbp->major_version);
		goto fail;
	}
	p->version_major = shbp->major_version;
	p->version_minor = shbp->minor_version;

	/*
	 * Save the time stamp resolution the user requested.
	 */
	p->opt.tstamp_precision = precision;

	/*
	 * Now start looking for an Interface Description Block.
	 */
	for (;;) {
		/*
		 * Read the next block.
		 */
		status = read_block(fp, p, &cursor, errbuf);
		if (status == 0) {
			/* EOF - no IDB in this file */
			snprintf(errbuf, PCAP_ERRBUF_SIZE,
			    "the capture file has no Interface Description Blocks");
			goto fail;
		}
		if (status == -1)
			goto fail;	/* error */
		switch (cursor.block_type) {

		case BT_IDB:
			/*
			 * Get a pointer to the fixed-length portion of the
			 * IDB.
			 */
			idbp = get_from_block_data(&cursor, sizeof(*idbp),
			    errbuf);
			if (idbp == NULL)
				goto fail;	/* error */

			/*
			 * Byte-swap it if necessary.
			 */
			if (p->swapped) {
				idbp->linktype = SWAPSHORT(idbp->linktype);
				idbp->snaplen = SWAPLONG(idbp->snaplen);
			}

			/*
			 * Try to add this interface.
			 */
			if (!add_interface(p, &cursor, errbuf))
				goto fail;
			goto done;

		case BT_EPB:
		case BT_SPB:
		case BT_PB:
			/*
			 * Saw a packet before we saw any IDBs.  That's
			 * not valid, as we don't know what link-layer
			 * encapsulation the packet has.
			 */
			snprintf(errbuf, PCAP_ERRBUF_SIZE,
			    "the capture file has a packet block before any Interface Description Blocks");
			goto fail;

		default:
			/*
			 * Just ignore it.
			 */
			break;
		}
	}

done:
	p->tzoff = 0;	/* XXX - not used in pcap */
	p->snapshot = idbp->snaplen;
	p->linktype = linktype_to_dlt(idbp->linktype);
	p->linktype_ext = 0;

	p->next_packet_op = pcap_ng_next_packet;
	p->cleanup_op = pcap_ng_cleanup;

	return (p);

fail:
	free(ps->ifaces);
	free(p->buffer);
	free(p);
	*err = 1;
	return (NULL);
}

static void
pcap_ng_cleanup(pcap_t *p)
{
	struct pcap_ng_sf *ps = p->priv;

	free(ps->ifaces);
	sf_cleanup(p);
}

/*
 * Read and return the next packet from the savefile.  Return the header
 * in hdr and a pointer to the contents in data.  Return 0 on success, 1
 * if there were no more packets, and -1 on an error.
 */
static int
pcap_ng_next_packet(pcap_t *p, struct pcap_pkthdr *hdr, u_char **data)
{
	struct pcap_ng_sf *ps = p->priv;
	struct block_cursor cursor;
	int status;
	struct enhanced_packet_block *epbp;
	struct simple_packet_block *spbp;
	struct packet_block *pbp;
	bpf_u_int32 interface_id = 0xFFFFFFFF;
	struct interface_description_block *idbp;
	struct section_header_block *shbp;
	FILE *fp = p->rfile;
	u_int64_t t, sec, frac;

	/*
	 * Look for an Enhanced Packet Block, a Simple Packet Block,
	 * or a Packet Block.
	 */
	for (;;) {
		/*
		 * Read the block type and length; those are common
		 * to all blocks.
		 */
		status = read_block(fp, p, &cursor, p->errbuf);
		if (status == 0)
			return (1);	/* EOF */
		if (status == -1)
			return (-1);	/* error */
		switch (cursor.block_type) {

		case BT_EPB:
			/*
			 * Get a pointer to the fixed-length portion of the
			 * EPB.
			 */
			epbp = get_from_block_data(&cursor, sizeof(*epbp),
			    p->errbuf);
			if (epbp == NULL)
				return (-1);	/* error */

			/*
			 * Byte-swap it if necessary.
			 */
			if (p->swapped) {
				/* these were written in opposite byte order */
				interface_id = SWAPLONG(epbp->interface_id);
				hdr->caplen = SWAPLONG(epbp->caplen);
				hdr->len = SWAPLONG(epbp->len);
				t = ((u_int64_t)SWAPLONG(epbp->timestamp_high)) << 32 |
				    SWAPLONG(epbp->timestamp_low);
			} else {
				interface_id = epbp->interface_id;
				hdr->caplen = epbp->caplen;
				hdr->len = epbp->len;
				t = ((u_int64_t)epbp->timestamp_high) << 32 |
				    epbp->timestamp_low;
			}
			goto found;

		case BT_SPB:
			/*
			 * Get a pointer to the fixed-length portion of the
			 * SPB.
			 */
			spbp = get_from_block_data(&cursor, sizeof(*spbp),
			    p->errbuf);
			if (spbp == NULL)
				return (-1);	/* error */

			/*
			 * SPB packets are assumed to have arrived on
			 * the first interface.
			 */
			interface_id = 0;

			/*
			 * Byte-swap it if necessary.
			 */
			if (p->swapped) {
				/* these were written in opposite byte order */
				hdr->len = SWAPLONG(spbp->len);
			} else
				hdr->len = spbp->len;

			/*
			 * The SPB doesn't give the captured length;
			 * it's the minimum of the snapshot length
			 * and the packet length.
			 */
			hdr->caplen = hdr->len;
			if (hdr->caplen > p->snapshot)
				hdr->caplen = p->snapshot;
			t = 0;	/* no time stamps */
			goto found;

		case BT_PB:
			/*
			 * Get a pointer to the fixed-length portion of the
			 * PB.
			 */
			pbp = get_from_block_data(&cursor, sizeof(*pbp),
			    p->errbuf);
			if (pbp == NULL)
				return (-1);	/* error */

			/*
			 * Byte-swap it if necessary.
			 */
			if (p->swapped) {
				/* these were written in opposite byte order */
				interface_id = SWAPSHORT(pbp->interface_id);
				hdr->caplen = SWAPLONG(pbp->caplen);
				hdr->len = SWAPLONG(pbp->len);
				t = ((u_int64_t)SWAPLONG(pbp->timestamp_high)) << 32 |
				    SWAPLONG(pbp->timestamp_low);
			} else {
				interface_id = pbp->interface_id;
				hdr->caplen = pbp->caplen;
				hdr->len = pbp->len;
				t = ((u_int64_t)pbp->timestamp_high) << 32 |
				    pbp->timestamp_low;
			}
			goto found;

		case BT_IDB:
			/*
			 * Interface Description Block.  Get a pointer
			 * to its fixed-length portion.
			 */
			idbp = get_from_block_data(&cursor, sizeof(*idbp),
			    p->errbuf);
			if (idbp == NULL)
				return (-1);	/* error */

			/*
			 * Byte-swap it if necessary.
			 */
			if (p->swapped) {
				idbp->linktype = SWAPSHORT(idbp->linktype);
				idbp->snaplen = SWAPLONG(idbp->snaplen);
			}

			/*
			 * If the link-layer type or snapshot length
			 * differ from the ones for the first IDB we
			 * saw, quit.
			 *
			 * XXX - just discard packets from those
			 * interfaces?
			 */
			if (p->linktype != idbp->linktype) {
				snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
				    "an interface has a type %u different from the type of the first interface",
				    idbp->linktype);
				return (-1);
			}
			if (p->snapshot != idbp->snaplen) {
				snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
				    "an interface has a snapshot length %u different from the type of the first interface",
				    idbp->snaplen);
				return (-1);
			}

			/*
			 * Try to add this interface.
			 */
			if (!add_interface(p, &cursor, p->errbuf))
				return (-1);
			break;

		case BT_SHB:
			/*
			 * Section Header Block.  Get a pointer
			 * to its fixed-length portion.
			 */
			shbp = get_from_block_data(&cursor, sizeof(*shbp),
			    p->errbuf);
			if (shbp == NULL)
				return (-1);	/* error */

			/*
			 * Assume the byte order of this section is
			 * the same as that of the previous section.
			 * We'll check for that later.
			 */
			if (p->swapped) {
				shbp->byte_order_magic =
				    SWAPLONG(shbp->byte_order_magic);
				shbp->major_version =
				    SWAPSHORT(shbp->major_version);
			}

			/*
			 * Make sure the byte order doesn't change;
			 * pcap_is_swapped() shouldn't change its
			 * return value in the middle of reading a capture.
			 */
			switch (shbp->byte_order_magic) {

			case BYTE_ORDER_MAGIC:
				/*
				 * OK.
				 */
				break;

			case SWAPLONG(BYTE_ORDER_MAGIC):
				/*
				 * Byte order changes.
				 */
				snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
				    "the file has sections with different byte orders");
				return (-1);

			default:
				/*
				 * Not a valid SHB.
				 */
				snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
				    "the file has a section with a bad byte order magic field");
				return (-1);
			}

			/*
			 * Make sure the major version is the version
			 * we handle.
			 */
			if (shbp->major_version != PCAP_NG_VERSION_MAJOR) {
				snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
				    "unknown pcap-ng savefile major version number %u",
				    shbp->major_version);
				return (-1);
			}

			/*
			 * Reset the interface count; this section should
			 * have its own set of IDBs.  If any of them
			 * don't have the same interface type, snapshot
			 * length, or resolution as the first interface
			 * we saw, we'll fail.  (And if we don't see
			 * any IDBs, we'll fail when we see a packet
			 * block.)
			 */
			ps->ifcount = 0;
			break;

		default:
			/*
			 * Not a packet block, IDB, or SHB; ignore it.
			 */
			break;
		}
	}

found:
	/*
	 * Is the interface ID an interface we know?
	 */
	if (interface_id >= ps->ifcount) {
		/*
		 * Yes.  Fail.
		 */
		snprintf(p->errbuf, PCAP_ERRBUF_SIZE,
		    "a packet arrived on interface %u, but there's no Interface Description Block for that interface",
		    interface_id);
		return (-1);
	}

	/*
	 * Convert the time stamp to seconds and fractions of a second,
	 * with the fractions being in units of the file-supplied resolution.
	 */
	sec = t / ps->ifaces[interface_id].tsresol + ps->ifaces[interface_id].tsoffset;
	frac = t % ps->ifaces[interface_id].tsresol;

	/*
	 * Convert the fractions from units of the file-supplied resolution
	 * to units of the user-requested resolution.
	 */
	switch (ps->ifaces[interface_id].scale_type) {

	case PASS_THROUGH:
		/*
		 * The interface resolution is what the user wants,
		 * so we're done.
		 */
		break;

	case SCALE_UP:
	case SCALE_DOWN:
		/*
		 * The interface resolution is different from what the
		 * user wants; convert the fractions to units of the
		 * resolution the user requested by multiplying by the
		 * quotient of the user-requested resolution and the
		 * file-supplied resolution.  We do that by multiplying
		 * by the user-requested resolution and dividing by the
		 * file-supplied resolution, as the quotient might not
		 * fit in an integer.
		 *
		 * XXX - if ps->ifaces[interface_id].tsresol is a power
		 * of 10, we could just multiply by the quotient of
		 * ps->user_tsresol and ps->ifaces[interface_id].tsresol
		 * in the scale-up case, and divide by the quotient of
		 * ps->ifaces[interface_id].tsresol and ps->user_tsresol
		 * in the scale-down case, as we know those will be integers.
		 * That would involve fewer arithmetic operations, and
		 * would run less risk of overflow.
		 *
		 * Is there something clever we could do if
		 * ps->ifaces[interface_id].tsresol is a power of 2?
		 */
		frac *= ps->user_tsresol;
		frac /= ps->ifaces[interface_id].tsresol;
		break;
	}
	hdr->ts.tv_sec = sec;
	hdr->ts.tv_usec = frac;

	/*
	 * Get a pointer to the packet data.
	 */
	*data = get_from_block_data(&cursor, hdr->caplen, p->errbuf);
	if (*data == NULL)
		return (-1);

	if (p->swapped)
		swap_pseudo_headers(p->linktype, hdr, *data);

	return (0);
}
