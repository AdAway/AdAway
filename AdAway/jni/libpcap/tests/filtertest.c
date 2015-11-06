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
 */

#ifndef lint
static const char copyright[] _U_ =
    "@(#) Copyright (c) 1988, 1989, 1990, 1991, 1992, 1993, 1994, 1995, 1996, 1997, 2000\n\
The Regents of the University of California.  All rights reserved.\n";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <pcap.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/stat.h>

#ifndef HAVE___ATTRIBUTE__
#define __attribute__(x)
#endif

static char *program_name;

/* Forwards */
static void usage(void) __attribute__((noreturn));
static void error(const char *, ...)
    __attribute__((noreturn, format (printf, 1, 2)));
static void warn(const char *, ...)
    __attribute__((format (printf, 1, 2)));

extern int optind;
extern int opterr;
extern char *optarg;
#ifdef BDEBUG
int dflag;
#endif

/*
 * On Windows, we need to open the file in binary mode, so that
 * we get all the bytes specified by the size we get from "fstat()".
 * On UNIX, that's not necessary.  O_BINARY is defined on Windows;
 * we define it as 0 if it's not defined, so it does nothing.
 */
#ifndef O_BINARY
#define O_BINARY	0
#endif

static char *
read_infile(char *fname)
{
	register int i, fd, cc;
	register char *cp;
	struct stat buf;

	fd = open(fname, O_RDONLY|O_BINARY);
	if (fd < 0)
		error("can't open %s: %s", fname, pcap_strerror(errno));

	if (fstat(fd, &buf) < 0)
		error("can't stat %s: %s", fname, pcap_strerror(errno));

	cp = malloc((u_int)buf.st_size + 1);
	if (cp == NULL)
		error("malloc(%d) for %s: %s", (u_int)buf.st_size + 1,
			fname, pcap_strerror(errno));
	cc = read(fd, cp, (u_int)buf.st_size);
	if (cc < 0)
		error("read %s: %s", fname, pcap_strerror(errno));
	if (cc != buf.st_size)
		error("short read %s (%d != %d)", fname, cc, (int)buf.st_size);

	close(fd);
	/* replace "# comment" with spaces */
	for (i = 0; i < cc; i++) {
		if (cp[i] == '#')
			while (i < cc && cp[i] != '\n')
				cp[i++] = ' ';
	}
	cp[cc] = '\0';
	return (cp);
}

/* VARARGS */
static void
error(const char *fmt, ...)
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
warn(const char *fmt, ...)
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
 * Copy arg vector into a new buffer, concatenating arguments with spaces.
 */
static char *
copy_argv(register char **argv)
{
	register char **p;
	register u_int len = 0;
	char *buf;
	char *src, *dst;

	p = argv;
	if (*p == 0)
		return 0;

	while (*p)
		len += strlen(*p++) + 1;

	buf = (char *)malloc(len);
	if (buf == NULL)
		error("copy_argv: malloc");

	p = argv;
	dst = buf;
	while ((src = *p++) != NULL) {
		while ((*dst++ = *src++) != '\0')
			;
		dst[-1] = ' ';
	}
	dst[-1] = '\0';

	return buf;
}

int
main(int argc, char **argv)
{
	char *cp;
	int op;
#ifndef BDEBUG
	int dflag;
#endif
	char *infile;
	int Oflag;
	long snaplen;
	char *p;
	int dlt;
	bpf_u_int32 netmask = PCAP_NETMASK_UNKNOWN;
	char *cmdbuf;
	pcap_t *pd;
	struct bpf_program fcode;

#ifdef WIN32
	if(wsockinit() != 0) return 1;
#endif /* WIN32 */

#ifndef BDEBUG
	dflag = 1;
#else
	/* if optimizer debugging is enabled, output DOT graph
	 * `dflag=4' is equivalent to -dddd to follow -d/-dd/-ddd
     * convention in tcpdump command line
	 */
	dflag = 4;
#endif
	infile = NULL;
	Oflag = 1;
	snaplen = 68;

	if ((cp = strrchr(argv[0], '/')) != NULL)
		program_name = cp + 1;
	else
		program_name = argv[0];

	opterr = 0;
	while ((op = getopt(argc, argv, "dF:m:Os:")) != -1) {
		switch (op) {

		case 'd':
			++dflag;
			break;

		case 'F':
			infile = optarg;
			break;

		case 'O':
			Oflag = 0;
			break;

		case 'm': {
			in_addr_t addr;

			addr = inet_addr(optarg);
			if (addr == INADDR_NONE)
				error("invalid netmask %s", optarg);
			netmask = addr;
			break;
		}

		case 's': {
			char *end;

			snaplen = strtol(optarg, &end, 0);
			if (optarg == end || *end != '\0'
			    || snaplen < 0 || snaplen > 65535)
				error("invalid snaplen %s", optarg);
			else if (snaplen == 0)
				snaplen = 65535;
			break;
		}

		default:
			usage();
			/* NOTREACHED */
		}
	}

	if (optind >= argc) {
		usage();
		/* NOTREACHED */
	}

	dlt = pcap_datalink_name_to_val(argv[optind]);
	if (dlt < 0) {
		dlt = (int)strtol(argv[optind], &p, 10);
		if (p == argv[optind] || *p != '\0')
			error("invalid data link type %s", argv[optind]);
	}

	if (infile)
		cmdbuf = read_infile(infile);
	else
		cmdbuf = copy_argv(&argv[optind+1]);

	pd = pcap_open_dead(dlt, snaplen);
	if (pd == NULL)
		error("Can't open fake pcap_t");

	if (pcap_compile(pd, &fcode, cmdbuf, Oflag, netmask) < 0)
		error("%s", pcap_geterr(pd));

	if (!bpf_validate(fcode.bf_insns, fcode.bf_len))
		warn("Filter doesn't pass validation");

#ifdef BDEBUG
	// replace line feed with space
	for (cp = cmdbuf; *cp != '\0'; ++cp) {
		if (*cp == '\r' || *cp == '\n') {
			*cp = ' ';
		}
	}
	// only show machine code if BDEBUG defined, since dflag > 3
	printf("machine codes for filter: %s\n", cmdbuf);
#endif

	bpf_dump(&fcode, dflag);
	pcap_close(pd);
	exit(0);
}

static void
usage(void)
{
	(void)fprintf(stderr, "%s, with %s\n", program_name,
	    pcap_lib_version());
	(void)fprintf(stderr,
	    "Usage: %s [-dO] [ -F file ] [ -m netmask] [ -s snaplen ] dlt [ expression ]\n",
	    program_name);
	exit(1);
}
