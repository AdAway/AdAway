/*
 * Copyright (c) 1990, 1991, 1993, 1994, 1995, 1996, 1997
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

/*
 * txtproto_print() derived from original code by Hannes Gredler
 * (hannes@juniper.net):
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that: (1) source code
 * distributions retain the above copyright notice and this paragraph
 * in its entirety, and (2) distributions including binary code include
 * the above copyright notice and this paragraph in its entirety in
 * the documentation or other materials provided with the distribution.
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND
 * WITHOUT ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, WITHOUT
 * LIMITATION, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE.
 */

#define NETDISSECT_REWORKED
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <sys/stat.h>

#ifdef HAVE_FCNTL_H
#include <fcntl.h>
#endif
#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>

#include "interface.h"

/*
 * Print out a null-terminated filename (or other ascii string).
 * If ep is NULL, assume no truncation check is needed.
 * Return true if truncated.
 */
int
fn_print(netdissect_options *ndo,
         register const u_char *s, register const u_char *ep)
{
	register int ret;
	register u_char c;

	ret = 1;			/* assume truncated */
	while (ep == NULL || s < ep) {
		c = *s++;
		if (c == '\0') {
			ret = 0;
			break;
		}
		if (!ND_ISASCII(c)) {
			c = ND_TOASCII(c);
			ND_PRINT((ndo, "M-"));
		}
		if (!ND_ISPRINT(c)) {
			c ^= 0x40;	/* DEL to ?, others to alpha */
			ND_PRINT((ndo, "^"));
		}
		ND_PRINT((ndo, "%c", c));
	}
	return(ret);
}

/*
 * Print out a counted filename (or other ascii string).
 * If ep is NULL, assume no truncation check is needed.
 * Return true if truncated.
 */
int
fn_printn(netdissect_options *ndo,
          register const u_char *s, register u_int n, register const u_char *ep)
{
	register u_char c;

	while (n > 0 && (ep == NULL || s < ep)) {
		n--;
		c = *s++;
		if (!ND_ISASCII(c)) {
			c = ND_TOASCII(c);
			ND_PRINT((ndo, "M-"));
		}
		if (!ND_ISPRINT(c)) {
			c ^= 0x40;	/* DEL to ?, others to alpha */
			ND_PRINT((ndo, "^"));
		}
		ND_PRINT((ndo, "%c", c));
	}
	return (n == 0) ? 0 : 1;
}

/*
 * Print out a null-padded filename (or other ascii string).
 * If ep is NULL, assume no truncation check is needed.
 * Return true if truncated.
 */
int
fn_printzp(netdissect_options *ndo,
           register const u_char *s, register u_int n,
           register const u_char *ep)
{
	register int ret;
	register u_char c;

	ret = 1;			/* assume truncated */
	while (n > 0 && (ep == NULL || s < ep)) {
		n--;
		c = *s++;
		if (c == '\0') {
			ret = 0;
			break;
		}
		if (!ND_ISASCII(c)) {
			c = ND_TOASCII(c);
			ND_PRINT((ndo, "M-"));
		}
		if (!ND_ISPRINT(c)) {
			c ^= 0x40;	/* DEL to ?, others to alpha */
			ND_PRINT((ndo, "^"));
		}
		ND_PRINT((ndo, "%c", c));
	}
	return (n == 0) ? 0 : ret;
}

/*
 * Format the timestamp
 */
static char *
ts_format(netdissect_options *ndo
#ifndef HAVE_PCAP_SET_TSTAMP_PRECISION
_U_
#endif
, int sec, int usec)
{
	static char buf[sizeof("00:00:00.000000000")];
	const char *format;

#ifdef HAVE_PCAP_SET_TSTAMP_PRECISION
	switch (ndo->ndo_tstamp_precision) {

	case PCAP_TSTAMP_PRECISION_MICRO:
		format = "%02d:%02d:%02d.%06u";
		break;

	case PCAP_TSTAMP_PRECISION_NANO:
		format = "%02d:%02d:%02d.%09u";
		break;

	default:
		format = "%02d:%02d:%02d.{unknown precision}";
		break;
	}
#else
	format = "%02d:%02d:%02d.%06u";
#endif

	snprintf(buf, sizeof(buf), format,
                 sec / 3600, (sec % 3600) / 60, sec % 60, usec);

        return buf;
}

/*
 * Print the timestamp
 */
void
ts_print(netdissect_options *ndo,
         register const struct timeval *tvp)
{
	register int s;
	struct tm *tm;
	time_t Time;
	static unsigned b_sec;
	static unsigned b_usec;
	int d_usec;
	int d_sec;

	switch (ndo->ndo_tflag) {

	case 0: /* Default */
		s = (tvp->tv_sec + thiszone) % 86400;
		ND_PRINT((ndo, "%s ", ts_format(ndo, s, tvp->tv_usec)));
		break;

	case 1: /* No time stamp */
		break;

	case 2: /* Unix timeval style */
		ND_PRINT((ndo, "%u.%06u ",
			     (unsigned)tvp->tv_sec,
			     (unsigned)tvp->tv_usec));
		break;

	case 3: /* Microseconds since previous packet */
        case 5: /* Microseconds since first packet */
		if (b_sec == 0) {
                        /* init timestamp for first packet */
                        b_usec = tvp->tv_usec;
                        b_sec = tvp->tv_sec;
                }

                d_usec = tvp->tv_usec - b_usec;
                d_sec = tvp->tv_sec - b_sec;

                while (d_usec < 0) {
                    d_usec += 1000000;
                    d_sec--;
                }

                ND_PRINT((ndo, "%s ", ts_format(ndo, d_sec, d_usec)));

                if (ndo->ndo_tflag == 3) { /* set timestamp for last packet */
                    b_sec = tvp->tv_sec;
                    b_usec = tvp->tv_usec;
                }
		break;

	case 4: /* Default + Date*/
		s = (tvp->tv_sec + thiszone) % 86400;
		Time = (tvp->tv_sec + thiszone) - s;
		tm = gmtime (&Time);
		if (!tm)
			ND_PRINT((ndo, "Date fail  "));
		else
			ND_PRINT((ndo, "%04d-%02d-%02d %s ",
                               tm->tm_year+1900, tm->tm_mon+1, tm->tm_mday,
                               ts_format(ndo, s, tvp->tv_usec)));
		break;
	}
}

/*
 * Print a relative number of seconds (e.g. hold time, prune timer)
 * in the form 5m1s.  This does no truncation, so 32230861 seconds
 * is represented as 1y1w1d1h1m1s.
 */
void
relts_print(netdissect_options *ndo,
            int secs)
{
	static const char *lengths[] = {"y", "w", "d", "h", "m", "s"};
	static const int seconds[] = {31536000, 604800, 86400, 3600, 60, 1};
	const char **l = lengths;
	const int *s = seconds;

	if (secs == 0) {
		ND_PRINT((ndo, "0s"));
		return;
	}
	if (secs < 0) {
		ND_PRINT((ndo, "-"));
		secs = -secs;
	}
	while (secs > 0) {
		if (secs >= *s) {
			ND_PRINT((ndo, "%d%s", secs / *s, *l));
			secs -= (secs / *s) * *s;
		}
		s++;
		l++;
	}
}

/*
 *  this is a generic routine for printing unknown data;
 *  we pass on the linefeed plus indentation string to
 *  get a proper output - returns 0 on error
 */

int
print_unknown_data(netdissect_options *ndo, const u_char *cp,const char *ident,int len)
{
	if (len < 0) {
          ND_PRINT((ndo,"%sDissector error: print_unknown_data called with negative length",
		    ident));
		return(0);
	}
	if (ndo->ndo_snapend - cp < len)
		len = ndo->ndo_snapend - cp;
	if (len < 0) {
          ND_PRINT((ndo,"%sDissector error: print_unknown_data called with pointer past end of packet",
		    ident));
		return(0);
	}
        hex_print(ndo, ident,cp,len);
	return(1); /* everything is ok */
}

/*
 * Convert a token value to a string; use "fmt" if not found.
 */
const char *
tok2strbuf(register const struct tok *lp, register const char *fmt,
	   register u_int v, char *buf, size_t bufsize)
{
	if (lp != NULL) {
		while (lp->s != NULL) {
			if (lp->v == v)
				return (lp->s);
			++lp;
		}
	}
	if (fmt == NULL)
		fmt = "#%d";

	(void)snprintf(buf, bufsize, fmt, v);
	return (const char *)buf;
}

/*
 * Convert a token value to a string; use "fmt" if not found.
 */
const char *
tok2str(register const struct tok *lp, register const char *fmt,
	register u_int v)
{
	static char buf[4][128];
	static int idx = 0;
	char *ret;

	ret = buf[idx];
	idx = (idx+1) & 3;
	return tok2strbuf(lp, fmt, v, ret, sizeof(buf[0]));
}

/*
 * Convert a bit token value to a string; use "fmt" if not found.
 * this is useful for parsing bitfields, the output strings are seperated
 * if the s field is positive.
 */
static char *
bittok2str_internal(register const struct tok *lp, register const char *fmt,
	   register u_int v, const char *sep)
{
        static char buf[256]; /* our stringbuffer */
        int buflen=0;
        register u_int rotbit; /* this is the bit we rotate through all bitpositions */
        register u_int tokval;
        const char * sepstr = "";

	while (lp != NULL && lp->s != NULL) {
            tokval=lp->v;   /* load our first value */
            rotbit=1;
            while (rotbit != 0) {
                /*
                 * lets AND the rotating bit with our token value
                 * and see if we have got a match
                 */
		if (tokval == (v&rotbit)) {
                    /* ok we have found something */
                    buflen+=snprintf(buf+buflen, sizeof(buf)-buflen, "%s%s",
                                     sepstr, lp->s);
                    sepstr = sep;
                    break;
                }
                rotbit=rotbit<<1; /* no match - lets shift and try again */
            }
            lp++;
	}

        if (buflen == 0)
            /* bummer - lets print the "unknown" message as advised in the fmt string if we got one */
            (void)snprintf(buf, sizeof(buf), fmt == NULL ? "#%08x" : fmt, v);
        return (buf);
}

/*
 * Convert a bit token value to a string; use "fmt" if not found.
 * this is useful for parsing bitfields, the output strings are not seperated.
 */
char *
bittok2str_nosep(register const struct tok *lp, register const char *fmt,
	   register u_int v)
{
    return (bittok2str_internal(lp, fmt, v, ""));
}

/*
 * Convert a bit token value to a string; use "fmt" if not found.
 * this is useful for parsing bitfields, the output strings are comma seperated.
 */
char *
bittok2str(register const struct tok *lp, register const char *fmt,
	   register u_int v)
{
    return (bittok2str_internal(lp, fmt, v, ", "));
}

/*
 * Convert a value to a string using an array; the macro
 * tok2strary() in <interface.h> is the public interface to
 * this function and ensures that the second argument is
 * correct for bounds-checking.
 */
const char *
tok2strary_internal(register const char **lp, int n, register const char *fmt,
	register int v)
{
	static char buf[128];

	if (v >= 0 && v < n && lp[v] != NULL)
		return lp[v];
	if (fmt == NULL)
		fmt = "#%d";
	(void)snprintf(buf, sizeof(buf), fmt, v);
	return (buf);
}

/*
 * Convert a 32-bit netmask to prefixlen if possible
 * the function returns the prefix-len; if plen == -1
 * then conversion was not possible;
 */

int
mask2plen(uint32_t mask)
{
	uint32_t bitmasks[33] = {
		0x00000000,
		0x80000000, 0xc0000000, 0xe0000000, 0xf0000000,
		0xf8000000, 0xfc000000, 0xfe000000, 0xff000000,
		0xff800000, 0xffc00000, 0xffe00000, 0xfff00000,
		0xfff80000, 0xfffc0000, 0xfffe0000, 0xffff0000,
		0xffff8000, 0xffffc000, 0xffffe000, 0xfffff000,
		0xfffff800, 0xfffffc00, 0xfffffe00, 0xffffff00,
		0xffffff80, 0xffffffc0, 0xffffffe0, 0xfffffff0,
		0xfffffff8, 0xfffffffc, 0xfffffffe, 0xffffffff
	};
	int prefix_len = 32;

	/* let's see if we can transform the mask into a prefixlen */
	while (prefix_len >= 0) {
		if (bitmasks[prefix_len] == mask)
			break;
		prefix_len--;
	}
	return (prefix_len);
}

#ifdef INET6
int
mask62plen(const u_char *mask)
{
	u_char bitmasks[9] = {
		0x00,
		0x80, 0xc0, 0xe0, 0xf0,
		0xf8, 0xfc, 0xfe, 0xff
	};
	int byte;
	int cidr_len = 0;

	for (byte = 0; byte < 16; byte++) {
		u_int bits;

		for (bits = 0; bits < (sizeof (bitmasks) / sizeof (bitmasks[0])); bits++) {
			if (mask[byte] == bitmasks[bits]) {
				cidr_len += bits;
				break;
			}
		}

		if (mask[byte] != 0xff)
			break;
	}
	return (cidr_len);
}
#endif /* INET6 */

/*
 * Routine to print out information for text-based protocols such as FTP,
 * HTTP, SMTP, RTSP, SIP, ....
 */
#define MAX_TOKEN	128

/*
 * Fetch a token from a packet, starting at the specified index,
 * and return the length of the token.
 *
 * Returns 0 on error; yes, this is indistinguishable from an empty
 * token, but an "empty token" isn't a valid token - it just means
 * either a space character at the beginning of the line (this
 * includes a blank line) or no more tokens remaining on the line.
 */
static int
fetch_token(netdissect_options *ndo, const u_char *pptr, u_int idx, u_int len,
    u_char *tbuf, size_t tbuflen)
{
	size_t toklen = 0;

	for (; idx < len; idx++) {
		if (!ND_TTEST(*(pptr + idx))) {
			/* ran past end of captured data */
			return (0);
		}
		if (!isascii(*(pptr + idx))) {
			/* not an ASCII character */
			return (0);
		}
		if (isspace(*(pptr + idx))) {
			/* end of token */
			break;
		}
		if (!isprint(*(pptr + idx))) {
			/* not part of a command token or response code */
			return (0);
		}
		if (toklen + 2 > tbuflen) {
			/* no room for this character and terminating '\0' */
			return (0);
		}
		tbuf[toklen] = *(pptr + idx);
		toklen++;
	}
	if (toklen == 0) {
		/* no token */
		return (0);
	}
	tbuf[toklen] = '\0';

	/*
	 * Skip past any white space after the token, until we see
	 * an end-of-line (CR or LF).
	 */
	for (; idx < len; idx++) {
		if (!ND_TTEST(*(pptr + idx))) {
			/* ran past end of captured data */
			break;
		}
		if (*(pptr + idx) == '\r' || *(pptr + idx) == '\n') {
			/* end of line */
			break;
		}
		if (!isascii(*(pptr + idx)) || !isprint(*(pptr + idx))) {
			/* not a printable ASCII character */
			break;
		}
		if (!isspace(*(pptr + idx))) {
			/* beginning of next token */
			break;
		}
	}
	return (idx);
}

/*
 * Scan a buffer looking for a line ending - LF or CR-LF.
 * Return the index of the character after the line ending or 0 if
 * we encounter a non-ASCII or non-printable character or don't find
 * the line ending.
 */
static u_int
print_txt_line(netdissect_options *ndo, const char *protoname,
    const char *prefix, const u_char *pptr, u_int idx, u_int len)
{
	u_int startidx;
	u_int linelen;

	startidx = idx;
	while (idx < len) {
		ND_TCHECK(*(pptr+idx));
		if (*(pptr+idx) == '\n') {
			/*
			 * LF without CR; end of line.
			 * Skip the LF and print the line, with the
			 * exception of the LF.
			 */
			linelen = idx - startidx;
			idx++;
			goto print;
		} else if (*(pptr+idx) == '\r') {
			/* CR - any LF? */
			if ((idx+1) >= len) {
				/* not in this packet */
				return (0);
			}
			ND_TCHECK(*(pptr+idx+1));
			if (*(pptr+idx+1) == '\n') {
				/*
				 * CR-LF; end of line.
				 * Skip the CR-LF and print the line, with
				 * the exception of the CR-LF.
				 */
				linelen = idx - startidx;
				idx += 2;
				goto print;
			}

			/*
			 * CR followed by something else; treat this
			 * as if it were binary data, and don't print
			 * it.
			 */
			return (0);
		} else if (!isascii(*(pptr+idx)) ||
		    (!isprint(*(pptr+idx)) && *(pptr+idx) != '\t')) {
			/*
			 * Not a printable ASCII character and not a tab;
			 * treat this as if it were binary data, and
			 * don't print it.
			 */
			return (0);
		}
		idx++;
	}

	/*
	 * All printable ASCII, but no line ending after that point
	 * in the buffer; treat this as if it were truncated.
	 */
trunc:
	linelen = idx - startidx;
	ND_PRINT((ndo, "%s%.*s[!%s]", prefix, (int)linelen, pptr + startidx,
	    protoname));
	return (0);

print:
	ND_PRINT((ndo, "%s%.*s", prefix, (int)linelen, pptr + startidx));
	return (idx);
}

void
txtproto_print(netdissect_options *ndo, const u_char *pptr, u_int len,
    const char *protoname, const char **cmds, u_int flags)
{
	u_int idx, eol;
	u_char token[MAX_TOKEN+1];
	const char *cmd;
	int is_reqresp = 0;
	const char *pnp;

	if (cmds != NULL) {
		/*
		 * This protocol has more than just request and
		 * response lines; see whether this looks like a
		 * request or response.
		 */
		idx = fetch_token(ndo, pptr, 0, len, token, sizeof(token));
		if (idx != 0) {
			/* Is this a valid request name? */
			while ((cmd = *cmds++) != NULL) {
				if (strcasecmp((const char *)token, cmd) == 0) {
					/* Yes. */
					is_reqresp = 1;
					break;
				}
			}

			/*
			 * No - is this a valid response code (3 digits)?
			 *
			 * Is this token the response code, or is the next
			 * token the response code?
			 */
			if (flags & RESP_CODE_SECOND_TOKEN) {
				/*
				 * Next token - get it.
				 */
				idx = fetch_token(ndo, pptr, idx, len, token,
				    sizeof(token));
			}
			if (idx != 0) {
				if (isdigit(token[0]) && isdigit(token[1]) &&
				    isdigit(token[2]) && token[3] == '\0') {
					/* Yes. */
					is_reqresp = 1;
				}
			}
		}
	} else {
		/*
		 * This protocol has only request and response lines
		 * (e.g., FTP, where all the data goes over a
		 * different connection); assume the payload is
		 * a request or response.
		 */
		is_reqresp = 1;
	}

	/* Capitalize the protocol name */
	for (pnp = protoname; *pnp != '\0'; pnp++)
		ND_PRINT((ndo, "%c", toupper(*pnp)));

	if (is_reqresp) {
		/*
		 * In non-verbose mode, just print the protocol, followed
		 * by the first line as the request or response info.
		 *
		 * In verbose mode, print lines as text until we run out
		 * of characters or see something that's not a
		 * printable-ASCII line.
		 */
		if (ndo->ndo_vflag) {
			/*
			 * We're going to print all the text lines in the
			 * request or response; just print the length
			 * on the first line of the output.
			 */
			ND_PRINT((ndo, ", length: %u", len));
			for (idx = 0;
			    idx < len && (eol = print_txt_line(ndo, protoname, "\n\t", pptr, idx, len)) != 0;
			    idx = eol)
				;
		} else {
			/*
			 * Just print the first text line.
			 */
			print_txt_line(ndo, protoname, ": ", pptr, 0, len);
		}
	}
}

/* VARARGS */
void
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
void
warning(const char *fmt, ...)
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
char *
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

/*
 * On Windows, we need to open the file in binary mode, so that
 * we get all the bytes specified by the size we get from "fstat()".
 * On UNIX, that's not necessary.  O_BINARY is defined on Windows;
 * we define it as 0 if it's not defined, so it does nothing.
 */
#ifndef O_BINARY
#define O_BINARY	0
#endif

char *
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

void
safeputs(netdissect_options *ndo,
         const u_char *s, const u_int maxlen)
{
	u_int idx = 0;

	while (*s && idx < maxlen) {
		safeputchar(ndo, *s);
		idx++;
		s++;
	}
}

void
safeputchar(netdissect_options *ndo,
            const u_char c)
{
	ND_PRINT((ndo, (c < 0x80 && ND_ISPRINT(c)) ? "%c" : "\\0x%02x", c));
}

#ifdef LBL_ALIGN
/*
 * Some compilers try to optimize memcpy(), using the alignment constraint
 * on the argument pointer type.  by using this function, we try to avoid the
 * optimization.
 */
void
unaligned_memcpy(void *p, const void *q, size_t l)
{
	memcpy(p, q, l);
}

/* As with memcpy(), so with memcmp(). */
int
unaligned_memcmp(const void *p, const void *q, size_t l)
{
	return (memcmp(p, q, l));
}
#endif
