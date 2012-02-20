/*
 * Copyright (c) 1988, 1989, 1990, 1991, 1992, 1993, 1994, 1995, 1996, 1997
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
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-nfs.c,v 1.106.2.4 2007/06/15 23:17:40 guy Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <pcap.h>
#include <stdio.h>
#include <string.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"

#include "nfs.h"
#include "nfsfh.h"

#include "ip.h"
#ifdef INET6
#include "ip6.h"
#endif
#include "rpc_auth.h"
#include "rpc_msg.h"

static void nfs_printfh(const u_int32_t *, const u_int);
static void xid_map_enter(const struct sunrpc_msg *, const u_char *);
static int32_t xid_map_find(const struct sunrpc_msg *, const u_char *,
			    u_int32_t *, u_int32_t *);
static void interp_reply(const struct sunrpc_msg *, u_int32_t, u_int32_t, int);
static const u_int32_t *parse_post_op_attr(const u_int32_t *, int);
static void print_sattr3(const struct nfsv3_sattr *sa3, int verbose);
static void print_nfsaddr(const u_char *, const char *, const char *);

/*
 * Mapping of old NFS Version 2 RPC numbers to generic numbers.
 */
u_int32_t nfsv3_procid[NFS_NPROCS] = {
	NFSPROC_NULL,
	NFSPROC_GETATTR,
	NFSPROC_SETATTR,
	NFSPROC_NOOP,
	NFSPROC_LOOKUP,
	NFSPROC_READLINK,
	NFSPROC_READ,
	NFSPROC_NOOP,
	NFSPROC_WRITE,
	NFSPROC_CREATE,
	NFSPROC_REMOVE,
	NFSPROC_RENAME,
	NFSPROC_LINK,
	NFSPROC_SYMLINK,
	NFSPROC_MKDIR,
	NFSPROC_RMDIR,
	NFSPROC_READDIR,
	NFSPROC_FSSTAT,
	NFSPROC_NOOP,
	NFSPROC_NOOP,
	NFSPROC_NOOP,
	NFSPROC_NOOP,
	NFSPROC_NOOP,
	NFSPROC_NOOP,
	NFSPROC_NOOP,
	NFSPROC_NOOP
};

/*
 * NFS V2 and V3 status values.
 *
 * Some of these come from the RFCs for NFS V2 and V3, with the message
 * strings taken from the FreeBSD C library "errlst.c".
 *
 * Others are errors that are not in the RFC but that I suspect some
 * NFS servers could return; the values are FreeBSD errno values, as
 * the first NFS server was the SunOS 2.0 one, and until 5.0 SunOS
 * was primarily BSD-derived.
 */
static struct tok status2str[] = {
	{ 1,     "Operation not permitted" },	/* EPERM */
	{ 2,     "No such file or directory" },	/* ENOENT */
	{ 5,     "Input/output error" },	/* EIO */
	{ 6,     "Device not configured" },	/* ENXIO */
	{ 11,    "Resource deadlock avoided" },	/* EDEADLK */
	{ 12,    "Cannot allocate memory" },	/* ENOMEM */
	{ 13,    "Permission denied" },		/* EACCES */
	{ 17,    "File exists" },		/* EEXIST */
	{ 18,    "Cross-device link" },		/* EXDEV */
	{ 19,    "Operation not supported by device" }, /* ENODEV */
	{ 20,    "Not a directory" },		/* ENOTDIR */
	{ 21,    "Is a directory" },		/* EISDIR */
	{ 22,    "Invalid argument" },		/* EINVAL */
	{ 26,    "Text file busy" },		/* ETXTBSY */
	{ 27,    "File too large" },		/* EFBIG */
	{ 28,    "No space left on device" },	/* ENOSPC */
	{ 30,    "Read-only file system" },	/* EROFS */
	{ 31,    "Too many links" },		/* EMLINK */
	{ 45,    "Operation not supported" },	/* EOPNOTSUPP */
	{ 62,    "Too many levels of symbolic links" }, /* ELOOP */
	{ 63,    "File name too long" },	/* ENAMETOOLONG */
	{ 66,    "Directory not empty" },	/* ENOTEMPTY */
	{ 69,    "Disc quota exceeded" },	/* EDQUOT */
	{ 70,    "Stale NFS file handle" },	/* ESTALE */
	{ 71,    "Too many levels of remote in path" }, /* EREMOTE */
	{ 99,    "Write cache flushed to disk" }, /* NFSERR_WFLUSH (not used) */
	{ 10001, "Illegal NFS file handle" },	/* NFS3ERR_BADHANDLE */
	{ 10002, "Update synchronization mismatch" }, /* NFS3ERR_NOT_SYNC */
	{ 10003, "READDIR/READDIRPLUS cookie is stale" }, /* NFS3ERR_BAD_COOKIE */
	{ 10004, "Operation not supported" },	/* NFS3ERR_NOTSUPP */
	{ 10005, "Buffer or request is too small" }, /* NFS3ERR_TOOSMALL */
	{ 10006, "Unspecified error on server" }, /* NFS3ERR_SERVERFAULT */
	{ 10007, "Object of that type not supported" }, /* NFS3ERR_BADTYPE */
	{ 10008, "Request couldn't be completed in time" }, /* NFS3ERR_JUKEBOX */
	{ 0,     NULL }
};

static struct tok nfsv3_writemodes[] = {
	{ 0,		"unstable" },
	{ 1,		"datasync" },
	{ 2,		"filesync" },
	{ 0,		NULL }
};

static struct tok type2str[] = {
	{ NFNON,	"NON" },
	{ NFREG,	"REG" },
	{ NFDIR,	"DIR" },
	{ NFBLK,	"BLK" },
	{ NFCHR,	"CHR" },
	{ NFLNK,	"LNK" },
	{ NFFIFO,	"FIFO" },
	{ 0,		NULL }
};

static void
print_nfsaddr(const u_char *bp, const char *s, const char *d)
{
	struct ip *ip;
#ifdef INET6
	struct ip6_hdr *ip6;
	char srcaddr[INET6_ADDRSTRLEN], dstaddr[INET6_ADDRSTRLEN];
#else
#ifndef INET_ADDRSTRLEN
#define INET_ADDRSTRLEN	16
#endif
	char srcaddr[INET_ADDRSTRLEN], dstaddr[INET_ADDRSTRLEN];
#endif

	srcaddr[0] = dstaddr[0] = '\0';
	switch (IP_V((struct ip *)bp)) {
	case 4:
		ip = (struct ip *)bp;
		strlcpy(srcaddr, ipaddr_string(&ip->ip_src), sizeof(srcaddr));
		strlcpy(dstaddr, ipaddr_string(&ip->ip_dst), sizeof(dstaddr));
		break;
#ifdef INET6
	case 6:
		ip6 = (struct ip6_hdr *)bp;
		strlcpy(srcaddr, ip6addr_string(&ip6->ip6_src),
		    sizeof(srcaddr));
		strlcpy(dstaddr, ip6addr_string(&ip6->ip6_dst),
		    sizeof(dstaddr));
		break;
#endif
	default:
		strlcpy(srcaddr, "?", sizeof(srcaddr));
		strlcpy(dstaddr, "?", sizeof(dstaddr));
		break;
	}

	(void)printf("%s.%s > %s.%s: ", srcaddr, s, dstaddr, d);
}

static const u_int32_t *
parse_sattr3(const u_int32_t *dp, struct nfsv3_sattr *sa3)
{
	TCHECK(dp[0]);
	sa3->sa_modeset = EXTRACT_32BITS(dp);
	dp++;
	if (sa3->sa_modeset) {
		TCHECK(dp[0]);
		sa3->sa_mode = EXTRACT_32BITS(dp);
		dp++;
	}

	TCHECK(dp[0]);
	sa3->sa_uidset = EXTRACT_32BITS(dp);
	dp++;
	if (sa3->sa_uidset) {
		TCHECK(dp[0]);
		sa3->sa_uid = EXTRACT_32BITS(dp);
		dp++;
	}

	TCHECK(dp[0]);
	sa3->sa_gidset = EXTRACT_32BITS(dp);
	dp++;
	if (sa3->sa_gidset) {
		TCHECK(dp[0]);
		sa3->sa_gid = EXTRACT_32BITS(dp);
		dp++;
	}

	TCHECK(dp[0]);
	sa3->sa_sizeset = EXTRACT_32BITS(dp);
	dp++;
	if (sa3->sa_sizeset) {
		TCHECK(dp[0]);
		sa3->sa_size = EXTRACT_32BITS(dp);
		dp++;
	}

	TCHECK(dp[0]);
	sa3->sa_atimetype = EXTRACT_32BITS(dp);
	dp++;
	if (sa3->sa_atimetype == NFSV3SATTRTIME_TOCLIENT) {
		TCHECK(dp[1]);
		sa3->sa_atime.nfsv3_sec = EXTRACT_32BITS(dp);
		dp++;
		sa3->sa_atime.nfsv3_nsec = EXTRACT_32BITS(dp);
		dp++;
	}

	TCHECK(dp[0]);
	sa3->sa_mtimetype = EXTRACT_32BITS(dp);
	dp++;
	if (sa3->sa_mtimetype == NFSV3SATTRTIME_TOCLIENT) {
		TCHECK(dp[1]);
		sa3->sa_mtime.nfsv3_sec = EXTRACT_32BITS(dp);
		dp++;
		sa3->sa_mtime.nfsv3_nsec = EXTRACT_32BITS(dp);
		dp++;
	}

	return dp;
trunc:
	return NULL;
}

static int nfserr;		/* true if we error rather than trunc */

static void
print_sattr3(const struct nfsv3_sattr *sa3, int verbose)
{
	if (sa3->sa_modeset)
		printf(" mode %o", sa3->sa_mode);
	if (sa3->sa_uidset)
		printf(" uid %u", sa3->sa_uid);
	if (sa3->sa_gidset)
		printf(" gid %u", sa3->sa_gid);
	if (verbose > 1) {
		if (sa3->sa_atimetype == NFSV3SATTRTIME_TOCLIENT)
			printf(" atime %u.%06u", sa3->sa_atime.nfsv3_sec,
			       sa3->sa_atime.nfsv3_nsec);
		if (sa3->sa_mtimetype == NFSV3SATTRTIME_TOCLIENT)
			printf(" mtime %u.%06u", sa3->sa_mtime.nfsv3_sec,
			       sa3->sa_mtime.nfsv3_nsec);
	}
}

void
nfsreply_print(register const u_char *bp, u_int length,
	       register const u_char *bp2)
{
	register const struct sunrpc_msg *rp;
	u_int32_t proc, vers, reply_stat;
	char srcid[20], dstid[20];	/*fits 32bit*/
	enum sunrpc_reject_stat rstat;
	u_int32_t rlow;
	u_int32_t rhigh;
	enum sunrpc_auth_stat rwhy;

	nfserr = 0;		/* assume no error */
	rp = (const struct sunrpc_msg *)bp;

	if (!nflag) {
		strlcpy(srcid, "nfs", sizeof(srcid));
		snprintf(dstid, sizeof(dstid), "%u",
		    EXTRACT_32BITS(&rp->rm_xid));
	} else {
		snprintf(srcid, sizeof(srcid), "%u", NFS_PORT);
		snprintf(dstid, sizeof(dstid), "%u",
		    EXTRACT_32BITS(&rp->rm_xid));
	}
	print_nfsaddr(bp2, srcid, dstid);
	reply_stat = EXTRACT_32BITS(&rp->rm_reply.rp_stat);
	switch (reply_stat) {

	case SUNRPC_MSG_ACCEPTED:
		(void)printf("reply ok %u", length);
		if (xid_map_find(rp, bp2, &proc, &vers) >= 0)
			interp_reply(rp, proc, vers, length);
		break;

	case SUNRPC_MSG_DENIED:
		(void)printf("reply ERR %u: ", length);
		rstat = EXTRACT_32BITS(&rp->rm_reply.rp_reject.rj_stat);
		switch (rstat) {

		case SUNRPC_RPC_MISMATCH:
			rlow = EXTRACT_32BITS(&rp->rm_reply.rp_reject.rj_vers.low);
			rhigh = EXTRACT_32BITS(&rp->rm_reply.rp_reject.rj_vers.high);
			(void)printf("RPC Version mismatch (%u-%u)",
			    rlow, rhigh);
			break;

		case SUNRPC_AUTH_ERROR:
			rwhy = EXTRACT_32BITS(&rp->rm_reply.rp_reject.rj_why);
			(void)printf("Auth ");
			switch (rwhy) {

			case SUNRPC_AUTH_OK:
				(void)printf("OK");
				break;

			case SUNRPC_AUTH_BADCRED:
				(void)printf("Bogus Credentials (seal broken)");
				break;

			case SUNRPC_AUTH_REJECTEDCRED:
				(void)printf("Rejected Credentials (client should begin new session)");
				break;

			case SUNRPC_AUTH_BADVERF:
				(void)printf("Bogus Verifier (seal broken)");
				break;

			case SUNRPC_AUTH_REJECTEDVERF:
				(void)printf("Verifier expired or was replayed");
				break;

			case SUNRPC_AUTH_TOOWEAK:
				(void)printf("Credentials are too weak");
				break;

			case SUNRPC_AUTH_INVALIDRESP:
				(void)printf("Bogus response verifier");
				break;

			case SUNRPC_AUTH_FAILED:
				(void)printf("Unknown failure");
				break;

			default:
				(void)printf("Invalid failure code %u",
				    (unsigned int)rwhy);
				break;
			}
			break;

		default:
			(void)printf("Unknown reason for rejecting rpc message %u",
			    (unsigned int)rstat);
			break;
		}
		break;

	default:
		(void)printf("reply Unknown rpc response code=%u %u",
		    reply_stat, length);
		break;
	}
}

/*
 * Return a pointer to the first file handle in the packet.
 * If the packet was truncated, return 0.
 */
static const u_int32_t *
parsereq(register const struct sunrpc_msg *rp, register u_int length)
{
	register const u_int32_t *dp;
	register u_int len;

	/*
	 * find the start of the req data (if we captured it)
	 */
	dp = (u_int32_t *)&rp->rm_call.cb_cred;
	TCHECK(dp[1]);
	len = EXTRACT_32BITS(&dp[1]);
	if (len < length) {
		dp += (len + (2 * sizeof(*dp) + 3)) / sizeof(*dp);
		TCHECK(dp[1]);
		len = EXTRACT_32BITS(&dp[1]);
		if (len < length) {
			dp += (len + (2 * sizeof(*dp) + 3)) / sizeof(*dp);
			TCHECK2(dp[0], 0);
			return (dp);
		}
	}
trunc:
	return (NULL);
}

/*
 * Print out an NFS file handle and return a pointer to following word.
 * If packet was truncated, return 0.
 */
static const u_int32_t *
parsefh(register const u_int32_t *dp, int v3)
{
	u_int len;

	if (v3) {
		TCHECK(dp[0]);
		len = EXTRACT_32BITS(dp) / 4;
		dp++;
	} else
		len = NFSX_V2FH / 4;

	if (TTEST2(*dp, len * sizeof(*dp))) {
		nfs_printfh(dp, len);
		return (dp + len);
	}
trunc:
	return (NULL);
}

/*
 * Print out a file name and return pointer to 32-bit word past it.
 * If packet was truncated, return 0.
 */
static const u_int32_t *
parsefn(register const u_int32_t *dp)
{
	register u_int32_t len;
	register const u_char *cp;

	/* Bail if we don't have the string length */
	TCHECK(*dp);

	/* Fetch string length; convert to host order */
	len = *dp++;
	NTOHL(len);

	TCHECK2(*dp, ((len + 3) & ~3));

	cp = (u_char *)dp;
	/* Update 32-bit pointer (NFS filenames padded to 32-bit boundaries) */
	dp += ((len + 3) & ~3) / sizeof(*dp);
	putchar('"');
	if (fn_printn(cp, len, snapend)) {
		putchar('"');
		goto trunc;
	}
	putchar('"');

	return (dp);
trunc:
	return NULL;
}

/*
 * Print out file handle and file name.
 * Return pointer to 32-bit word past file name.
 * If packet was truncated (or there was some other error), return 0.
 */
static const u_int32_t *
parsefhn(register const u_int32_t *dp, int v3)
{
	dp = parsefh(dp, v3);
	if (dp == NULL)
		return (NULL);
	putchar(' ');
	return (parsefn(dp));
}

void
nfsreq_print(register const u_char *bp, u_int length,
    register const u_char *bp2)
{
	register const struct sunrpc_msg *rp;
	register const u_int32_t *dp;
	nfs_type type;
	int v3;
	u_int32_t proc;
	struct nfsv3_sattr sa3;
	char srcid[20], dstid[20];	/*fits 32bit*/

	nfserr = 0;		/* assume no error */
	rp = (const struct sunrpc_msg *)bp;
	if (!nflag) {
		snprintf(srcid, sizeof(srcid), "%u",
		    EXTRACT_32BITS(&rp->rm_xid));
		strlcpy(dstid, "nfs", sizeof(dstid));
	} else {
		snprintf(srcid, sizeof(srcid), "%u",
		    EXTRACT_32BITS(&rp->rm_xid));
		snprintf(dstid, sizeof(dstid), "%u", NFS_PORT);
	}
	print_nfsaddr(bp2, srcid, dstid);
	(void)printf("%d", length);

	xid_map_enter(rp, bp2);	/* record proc number for later on */

	v3 = (EXTRACT_32BITS(&rp->rm_call.cb_vers) == NFS_VER3);
	proc = EXTRACT_32BITS(&rp->rm_call.cb_proc);

	if (!v3 && proc < NFS_NPROCS)
		proc =  nfsv3_procid[proc];

	switch (proc) {
	case NFSPROC_NOOP:
		printf(" nop");
		return;
	case NFSPROC_NULL:
		printf(" null");
		return;

	case NFSPROC_GETATTR:
		printf(" getattr");
		if ((dp = parsereq(rp, length)) != NULL &&
		    parsefh(dp, v3) != NULL)
			return;
		break;

	case NFSPROC_SETATTR:
		printf(" setattr");
		if ((dp = parsereq(rp, length)) != NULL &&
		    parsefh(dp, v3) != NULL)
			return;
		break;

	case NFSPROC_LOOKUP:
		printf(" lookup");
		if ((dp = parsereq(rp, length)) != NULL &&
		    parsefhn(dp, v3) != NULL)
			return;
		break;

	case NFSPROC_ACCESS:
		printf(" access");
		if ((dp = parsereq(rp, length)) != NULL &&
		    (dp = parsefh(dp, v3)) != NULL) {
			TCHECK(dp[0]);
			printf(" %04x", EXTRACT_32BITS(&dp[0]));
			return;
		}
		break;

	case NFSPROC_READLINK:
		printf(" readlink");
		if ((dp = parsereq(rp, length)) != NULL &&
		    parsefh(dp, v3) != NULL)
			return;
		break;

	case NFSPROC_READ:
		printf(" read");
		if ((dp = parsereq(rp, length)) != NULL &&
		    (dp = parsefh(dp, v3)) != NULL) {
			if (v3) {
				TCHECK(dp[2]);
				printf(" %u bytes @ %" PRIu64,
				       EXTRACT_32BITS(&dp[2]),
				       EXTRACT_64BITS(&dp[0]));
			} else {
				TCHECK(dp[1]);
				printf(" %u bytes @ %u",
				    EXTRACT_32BITS(&dp[1]),
				    EXTRACT_32BITS(&dp[0]));
			}
			return;
		}
		break;

	case NFSPROC_WRITE:
		printf(" write");
		if ((dp = parsereq(rp, length)) != NULL &&
		    (dp = parsefh(dp, v3)) != NULL) {
			if (v3) {
				TCHECK(dp[2]);
				printf(" %u (%u) bytes @ %" PRIu64,
						EXTRACT_32BITS(&dp[4]),
						EXTRACT_32BITS(&dp[2]),
						EXTRACT_64BITS(&dp[0]));
				if (vflag) {
					dp += 3;
					TCHECK(dp[0]);
					printf(" <%s>",
						tok2str(nfsv3_writemodes,
							NULL, EXTRACT_32BITS(dp)));
				}
			} else {
				TCHECK(dp[3]);
				printf(" %u (%u) bytes @ %u (%u)",
						EXTRACT_32BITS(&dp[3]),
						EXTRACT_32BITS(&dp[2]),
						EXTRACT_32BITS(&dp[1]),
						EXTRACT_32BITS(&dp[0]));
			}
			return;
		}
		break;

	case NFSPROC_CREATE:
		printf(" create");
		if ((dp = parsereq(rp, length)) != NULL &&
		    parsefhn(dp, v3) != NULL)
			return;
		break;

	case NFSPROC_MKDIR:
		printf(" mkdir");
		if ((dp = parsereq(rp, length)) != 0 && parsefhn(dp, v3) != 0)
			return;
		break;

	case NFSPROC_SYMLINK:
		printf(" symlink");
		if ((dp = parsereq(rp, length)) != 0 &&
		    (dp = parsefhn(dp, v3)) != 0) {
			fputs(" ->", stdout);
			if (v3 && (dp = parse_sattr3(dp, &sa3)) == 0)
				break;
			if (parsefn(dp) == 0)
				break;
			if (v3 && vflag)
				print_sattr3(&sa3, vflag);
			return;
		}
		break;

	case NFSPROC_MKNOD:
		printf(" mknod");
		if ((dp = parsereq(rp, length)) != 0 &&
		    (dp = parsefhn(dp, v3)) != 0) {
			TCHECK(*dp);
			type = (nfs_type)EXTRACT_32BITS(dp);
			dp++;
			if ((dp = parse_sattr3(dp, &sa3)) == 0)
				break;
			printf(" %s", tok2str(type2str, "unk-ft %d", type));
			if (vflag && (type == NFCHR || type == NFBLK)) {
				TCHECK(dp[1]);
				printf(" %u/%u",
				       EXTRACT_32BITS(&dp[0]),
				       EXTRACT_32BITS(&dp[1]));
				dp += 2;
			}
			if (vflag)
				print_sattr3(&sa3, vflag);
			return;
		}
		break;

	case NFSPROC_REMOVE:
		printf(" remove");
		if ((dp = parsereq(rp, length)) != NULL &&
		    parsefhn(dp, v3) != NULL)
			return;
		break;

	case NFSPROC_RMDIR:
		printf(" rmdir");
		if ((dp = parsereq(rp, length)) != NULL &&
		    parsefhn(dp, v3) != NULL)
			return;
		break;

	case NFSPROC_RENAME:
		printf(" rename");
		if ((dp = parsereq(rp, length)) != NULL &&
		    (dp = parsefhn(dp, v3)) != NULL) {
			fputs(" ->", stdout);
			if (parsefhn(dp, v3) != NULL)
				return;
		}
		break;

	case NFSPROC_LINK:
		printf(" link");
		if ((dp = parsereq(rp, length)) != NULL &&
		    (dp = parsefh(dp, v3)) != NULL) {
			fputs(" ->", stdout);
			if (parsefhn(dp, v3) != NULL)
				return;
		}
		break;

	case NFSPROC_READDIR:
		printf(" readdir");
		if ((dp = parsereq(rp, length)) != NULL &&
		    (dp = parsefh(dp, v3)) != NULL) {
			if (v3) {
				TCHECK(dp[4]);
				/*
				 * We shouldn't really try to interpret the
				 * offset cookie here.
				 */
				printf(" %u bytes @ %" PRId64,
				    EXTRACT_32BITS(&dp[4]),
				    EXTRACT_64BITS(&dp[0]));
				if (vflag)
					printf(" verf %08x%08x", dp[2],
					       dp[3]);
			} else {
				TCHECK(dp[1]);
				/*
				 * Print the offset as signed, since -1 is
				 * common, but offsets > 2^31 aren't.
				 */
				printf(" %u bytes @ %d",
				    EXTRACT_32BITS(&dp[1]),
				    EXTRACT_32BITS(&dp[0]));
			}
			return;
		}
		break;

	case NFSPROC_READDIRPLUS:
		printf(" readdirplus");
		if ((dp = parsereq(rp, length)) != NULL &&
		    (dp = parsefh(dp, v3)) != NULL) {
			TCHECK(dp[4]);
			/*
			 * We don't try to interpret the offset
			 * cookie here.
			 */
			printf(" %u bytes @ %" PRId64,
				EXTRACT_32BITS(&dp[4]),
				EXTRACT_64BITS(&dp[0]));
			if (vflag) {
				TCHECK(dp[5]);
				printf(" max %u verf %08x%08x",
				       EXTRACT_32BITS(&dp[5]), dp[2], dp[3]);
			}
			return;
		}
		break;

	case NFSPROC_FSSTAT:
		printf(" fsstat");
		if ((dp = parsereq(rp, length)) != NULL &&
		    parsefh(dp, v3) != NULL)
			return;
		break;

	case NFSPROC_FSINFO:
		printf(" fsinfo");
		if ((dp = parsereq(rp, length)) != NULL &&
		    parsefh(dp, v3) != NULL)
			return;
		break;

	case NFSPROC_PATHCONF:
		printf(" pathconf");
		if ((dp = parsereq(rp, length)) != NULL &&
		    parsefh(dp, v3) != NULL)
			return;
		break;

	case NFSPROC_COMMIT:
		printf(" commit");
		if ((dp = parsereq(rp, length)) != NULL &&
		    (dp = parsefh(dp, v3)) != NULL) {
			TCHECK(dp[2]);
			printf(" %u bytes @ %" PRIu64,
				EXTRACT_32BITS(&dp[2]),
				EXTRACT_64BITS(&dp[0]));
			return;
		}
		break;

	default:
		printf(" proc-%u", EXTRACT_32BITS(&rp->rm_call.cb_proc));
		return;
	}

trunc:
	if (!nfserr)
		fputs(" [|nfs]", stdout);
}

/*
 * Print out an NFS file handle.
 * We assume packet was not truncated before the end of the
 * file handle pointed to by dp.
 *
 * Note: new version (using portable file-handle parser) doesn't produce
 * generation number.  It probably could be made to do that, with some
 * additional hacking on the parser code.
 */
static void
nfs_printfh(register const u_int32_t *dp, const u_int len)
{
	my_fsid fsid;
	ino_t ino;
	const char *sfsname = NULL;
	char *spacep;

	if (uflag) {
		u_int i;
		char const *sep = "";

		printf(" fh[");
		for (i=0; i<len; i++) {
			(void)printf("%s%x", sep, dp[i]);
			sep = ":";
		}
		printf("]");
		return;
	}

	Parse_fh((const u_char *)dp, len, &fsid, &ino, NULL, &sfsname, 0);

	if (sfsname) {
		/* file system ID is ASCII, not numeric, for this server OS */
		static char temp[NFSX_V3FHMAX+1];

		/* Make sure string is null-terminated */
		strncpy(temp, sfsname, NFSX_V3FHMAX);
		temp[sizeof(temp) - 1] = '\0';
		/* Remove trailing spaces */
		spacep = strchr(temp, ' ');
		if (spacep)
			*spacep = '\0';

		(void)printf(" fh %s/", temp);
	} else {
		(void)printf(" fh %d,%d/",
			     fsid.Fsid_dev.Major, fsid.Fsid_dev.Minor);
	}

	if(fsid.Fsid_dev.Minor == 257)
		/* Print the undecoded handle */
		(void)printf("%s", fsid.Opaque_Handle);
	else
		(void)printf("%ld", (long) ino);
}

/*
 * Maintain a small cache of recent client.XID.server/proc pairs, to allow
 * us to match up replies with requests and thus to know how to parse
 * the reply.
 */

struct xid_map_entry {
	u_int32_t	xid;		/* transaction ID (net order) */
	int ipver;			/* IP version (4 or 6) */
#ifdef INET6
	struct in6_addr	client;		/* client IP address (net order) */
	struct in6_addr	server;		/* server IP address (net order) */
#else
	struct in_addr	client;		/* client IP address (net order) */
	struct in_addr	server;		/* server IP address (net order) */
#endif
	u_int32_t	proc;		/* call proc number (host order) */
	u_int32_t	vers;		/* program version (host order) */
};

/*
 * Map entries are kept in an array that we manage as a ring;
 * new entries are always added at the tail of the ring.  Initially,
 * all the entries are zero and hence don't match anything.
 */

#define	XIDMAPSIZE	64

struct xid_map_entry xid_map[XIDMAPSIZE];

int	xid_map_next = 0;
int	xid_map_hint = 0;

static void
xid_map_enter(const struct sunrpc_msg *rp, const u_char *bp)
{
	struct ip *ip = NULL;
#ifdef INET6
	struct ip6_hdr *ip6 = NULL;
#endif
	struct xid_map_entry *xmep;

	switch (IP_V((struct ip *)bp)) {
	case 4:
		ip = (struct ip *)bp;
		break;
#ifdef INET6
	case 6:
		ip6 = (struct ip6_hdr *)bp;
		break;
#endif
	default:
		return;
	}

	xmep = &xid_map[xid_map_next];

	if (++xid_map_next >= XIDMAPSIZE)
		xid_map_next = 0;

	xmep->xid = rp->rm_xid;
	if (ip) {
		xmep->ipver = 4;
		memcpy(&xmep->client, &ip->ip_src, sizeof(ip->ip_src));
		memcpy(&xmep->server, &ip->ip_dst, sizeof(ip->ip_dst));
	}
#ifdef INET6
	else if (ip6) {
		xmep->ipver = 6;
		memcpy(&xmep->client, &ip6->ip6_src, sizeof(ip6->ip6_src));
		memcpy(&xmep->server, &ip6->ip6_dst, sizeof(ip6->ip6_dst));
	}
#endif
	xmep->proc = EXTRACT_32BITS(&rp->rm_call.cb_proc);
	xmep->vers = EXTRACT_32BITS(&rp->rm_call.cb_vers);
}

/*
 * Returns 0 and puts NFSPROC_xxx in proc return and
 * version in vers return, or returns -1 on failure
 */
static int
xid_map_find(const struct sunrpc_msg *rp, const u_char *bp, u_int32_t *proc,
	     u_int32_t *vers)
{
	int i;
	struct xid_map_entry *xmep;
	u_int32_t xid = rp->rm_xid;
	struct ip *ip = (struct ip *)bp;
#ifdef INET6
	struct ip6_hdr *ip6 = (struct ip6_hdr *)bp;
#endif
	int cmp;

	/* Start searching from where we last left off */
	i = xid_map_hint;
	do {
		xmep = &xid_map[i];
		cmp = 1;
		if (xmep->ipver != IP_V(ip) || xmep->xid != xid)
			goto nextitem;
		switch (xmep->ipver) {
		case 4:
			if (memcmp(&ip->ip_src, &xmep->server,
				   sizeof(ip->ip_src)) != 0 ||
			    memcmp(&ip->ip_dst, &xmep->client,
				   sizeof(ip->ip_dst)) != 0) {
				cmp = 0;
			}
			break;
#ifdef INET6
		case 6:
			if (memcmp(&ip6->ip6_src, &xmep->server,
				   sizeof(ip6->ip6_src)) != 0 ||
			    memcmp(&ip6->ip6_dst, &xmep->client,
				   sizeof(ip6->ip6_dst)) != 0) {
				cmp = 0;
			}
			break;
#endif
		default:
			cmp = 0;
			break;
		}
		if (cmp) {
			/* match */
			xid_map_hint = i;
			*proc = xmep->proc;
			*vers = xmep->vers;
			return 0;
		}
	nextitem:
		if (++i >= XIDMAPSIZE)
			i = 0;
	} while (i != xid_map_hint);

	/* search failed */
	return (-1);
}

/*
 * Routines for parsing reply packets
 */

/*
 * Return a pointer to the beginning of the actual results.
 * If the packet was truncated, return 0.
 */
static const u_int32_t *
parserep(register const struct sunrpc_msg *rp, register u_int length)
{
	register const u_int32_t *dp;
	u_int len;
	enum sunrpc_accept_stat astat;

	/*
	 * Portability note:
	 * Here we find the address of the ar_verf credentials.
	 * Originally, this calculation was
	 *	dp = (u_int32_t *)&rp->rm_reply.rp_acpt.ar_verf
	 * On the wire, the rp_acpt field starts immediately after
	 * the (32 bit) rp_stat field.  However, rp_acpt (which is a
	 * "struct accepted_reply") contains a "struct opaque_auth",
	 * whose internal representation contains a pointer, so on a
	 * 64-bit machine the compiler inserts 32 bits of padding
	 * before rp->rm_reply.rp_acpt.ar_verf.  So, we cannot use
	 * the internal representation to parse the on-the-wire
	 * representation.  Instead, we skip past the rp_stat field,
	 * which is an "enum" and so occupies one 32-bit word.
	 */
	dp = ((const u_int32_t *)&rp->rm_reply) + 1;
	TCHECK(dp[1]);
	len = EXTRACT_32BITS(&dp[1]);
	if (len >= length)
		return (NULL);
	/*
	 * skip past the ar_verf credentials.
	 */
	dp += (len + (2*sizeof(u_int32_t) + 3)) / sizeof(u_int32_t);
	TCHECK2(dp[0], 0);

	/*
	 * now we can check the ar_stat field
	 */
	astat = (enum sunrpc_accept_stat) EXTRACT_32BITS(dp);
	switch (astat) {

	case SUNRPC_SUCCESS:
		break;

	case SUNRPC_PROG_UNAVAIL:
		printf(" PROG_UNAVAIL");
		nfserr = 1;		/* suppress trunc string */
		return (NULL);

	case SUNRPC_PROG_MISMATCH:
		printf(" PROG_MISMATCH");
		nfserr = 1;		/* suppress trunc string */
		return (NULL);

	case SUNRPC_PROC_UNAVAIL:
		printf(" PROC_UNAVAIL");
		nfserr = 1;		/* suppress trunc string */
		return (NULL);

	case SUNRPC_GARBAGE_ARGS:
		printf(" GARBAGE_ARGS");
		nfserr = 1;		/* suppress trunc string */
		return (NULL);

	case SUNRPC_SYSTEM_ERR:
		printf(" SYSTEM_ERR");
		nfserr = 1;		/* suppress trunc string */
		return (NULL);

	default:
		printf(" ar_stat %d", astat);
		nfserr = 1;		/* suppress trunc string */
		return (NULL);
	}
	/* successful return */
	TCHECK2(*dp, sizeof(astat));
	return ((u_int32_t *) (sizeof(astat) + ((char *)dp)));
trunc:
	return (0);
}

static const u_int32_t *
parsestatus(const u_int32_t *dp, int *er)
{
	int errnum;

	TCHECK(dp[0]);

	errnum = EXTRACT_32BITS(&dp[0]);
	if (er)
		*er = errnum;
	if (errnum != 0) {
		if (!qflag)
			printf(" ERROR: %s",
			    tok2str(status2str, "unk %d", errnum));
		nfserr = 1;
	}
	return (dp + 1);
trunc:
	return NULL;
}

static const u_int32_t *
parsefattr(const u_int32_t *dp, int verbose, int v3)
{
	const struct nfs_fattr *fap;

	fap = (const struct nfs_fattr *)dp;
	TCHECK(fap->fa_gid);
	if (verbose) {
		printf(" %s %o ids %d/%d",
		    tok2str(type2str, "unk-ft %d ",
		    EXTRACT_32BITS(&fap->fa_type)),
		    EXTRACT_32BITS(&fap->fa_mode),
		    EXTRACT_32BITS(&fap->fa_uid),
		    EXTRACT_32BITS(&fap->fa_gid));
		if (v3) {
			TCHECK(fap->fa3_size);
			printf(" sz %" PRIu64,
				EXTRACT_64BITS((u_int32_t *)&fap->fa3_size));
		} else {
			TCHECK(fap->fa2_size);
			printf(" sz %d", EXTRACT_32BITS(&fap->fa2_size));
		}
	}
	/* print lots more stuff */
	if (verbose > 1) {
		if (v3) {
			TCHECK(fap->fa3_ctime);
			printf(" nlink %d rdev %d/%d",
			       EXTRACT_32BITS(&fap->fa_nlink),
			       EXTRACT_32BITS(&fap->fa3_rdev.specdata1),
			       EXTRACT_32BITS(&fap->fa3_rdev.specdata2));
			printf(" fsid %" PRIx64,
				EXTRACT_64BITS((u_int32_t *)&fap->fa3_fsid));
			printf(" fileid %" PRIx64,
				EXTRACT_64BITS((u_int32_t *)&fap->fa3_fileid));
			printf(" a/m/ctime %u.%06u",
			       EXTRACT_32BITS(&fap->fa3_atime.nfsv3_sec),
			       EXTRACT_32BITS(&fap->fa3_atime.nfsv3_nsec));
			printf(" %u.%06u",
			       EXTRACT_32BITS(&fap->fa3_mtime.nfsv3_sec),
			       EXTRACT_32BITS(&fap->fa3_mtime.nfsv3_nsec));
			printf(" %u.%06u",
			       EXTRACT_32BITS(&fap->fa3_ctime.nfsv3_sec),
			       EXTRACT_32BITS(&fap->fa3_ctime.nfsv3_nsec));
		} else {
			TCHECK(fap->fa2_ctime);
			printf(" nlink %d rdev %x fsid %x nodeid %x a/m/ctime",
			       EXTRACT_32BITS(&fap->fa_nlink),
			       EXTRACT_32BITS(&fap->fa2_rdev),
			       EXTRACT_32BITS(&fap->fa2_fsid),
			       EXTRACT_32BITS(&fap->fa2_fileid));
			printf(" %u.%06u",
			       EXTRACT_32BITS(&fap->fa2_atime.nfsv2_sec),
			       EXTRACT_32BITS(&fap->fa2_atime.nfsv2_usec));
			printf(" %u.%06u",
			       EXTRACT_32BITS(&fap->fa2_mtime.nfsv2_sec),
			       EXTRACT_32BITS(&fap->fa2_mtime.nfsv2_usec));
			printf(" %u.%06u",
			       EXTRACT_32BITS(&fap->fa2_ctime.nfsv2_sec),
			       EXTRACT_32BITS(&fap->fa2_ctime.nfsv2_usec));
		}
	}
	return ((const u_int32_t *)((unsigned char *)dp +
		(v3 ? NFSX_V3FATTR : NFSX_V2FATTR)));
trunc:
	return (NULL);
}

static int
parseattrstat(const u_int32_t *dp, int verbose, int v3)
{
	int er;

	dp = parsestatus(dp, &er);
	if (dp == NULL)
		return (0);
	if (er)
		return (1);

	return (parsefattr(dp, verbose, v3) != NULL);
}

static int
parsediropres(const u_int32_t *dp)
{
	int er;

	if (!(dp = parsestatus(dp, &er)))
		return (0);
	if (er)
		return (1);

	dp = parsefh(dp, 0);
	if (dp == NULL)
		return (0);

	return (parsefattr(dp, vflag, 0) != NULL);
}

static int
parselinkres(const u_int32_t *dp, int v3)
{
	int er;

	dp = parsestatus(dp, &er);
	if (dp == NULL)
		return(0);
	if (er)
		return(1);
	if (v3 && !(dp = parse_post_op_attr(dp, vflag)))
		return (0);
	putchar(' ');
	return (parsefn(dp) != NULL);
}

static int
parsestatfs(const u_int32_t *dp, int v3)
{
	const struct nfs_statfs *sfsp;
	int er;

	dp = parsestatus(dp, &er);
	if (dp == NULL)
		return (0);
	if (!v3 && er)
		return (1);

	if (qflag)
		return(1);

	if (v3) {
		if (vflag)
			printf(" POST:");
		if (!(dp = parse_post_op_attr(dp, vflag)))
			return (0);
	}

	TCHECK2(*dp, (v3 ? NFSX_V3STATFS : NFSX_V2STATFS));

	sfsp = (const struct nfs_statfs *)dp;

	if (v3) {
		printf(" tbytes %" PRIu64 " fbytes %" PRIu64 " abytes %" PRIu64,
			EXTRACT_64BITS((u_int32_t *)&sfsp->sf_tbytes),
			EXTRACT_64BITS((u_int32_t *)&sfsp->sf_fbytes),
			EXTRACT_64BITS((u_int32_t *)&sfsp->sf_abytes));
		if (vflag) {
			printf(" tfiles %" PRIu64 " ffiles %" PRIu64 " afiles %" PRIu64 " invar %u",
			       EXTRACT_64BITS((u_int32_t *)&sfsp->sf_tfiles),
			       EXTRACT_64BITS((u_int32_t *)&sfsp->sf_ffiles),
			       EXTRACT_64BITS((u_int32_t *)&sfsp->sf_afiles),
			       EXTRACT_32BITS(&sfsp->sf_invarsec));
		}
	} else {
		printf(" tsize %d bsize %d blocks %d bfree %d bavail %d",
			EXTRACT_32BITS(&sfsp->sf_tsize),
			EXTRACT_32BITS(&sfsp->sf_bsize),
			EXTRACT_32BITS(&sfsp->sf_blocks),
			EXTRACT_32BITS(&sfsp->sf_bfree),
			EXTRACT_32BITS(&sfsp->sf_bavail));
	}

	return (1);
trunc:
	return (0);
}

static int
parserddires(const u_int32_t *dp)
{
	int er;

	dp = parsestatus(dp, &er);
	if (dp == NULL)
		return (0);
	if (er)
		return (1);
	if (qflag)
		return (1);

	TCHECK(dp[2]);
	printf(" offset %x size %d ",
	       EXTRACT_32BITS(&dp[0]), EXTRACT_32BITS(&dp[1]));
	if (dp[2] != 0)
		printf(" eof");

	return (1);
trunc:
	return (0);
}

static const u_int32_t *
parse_wcc_attr(const u_int32_t *dp)
{
	printf(" sz %" PRIu64, EXTRACT_64BITS(&dp[0]));
	printf(" mtime %u.%06u ctime %u.%06u",
	       EXTRACT_32BITS(&dp[2]), EXTRACT_32BITS(&dp[3]),
	       EXTRACT_32BITS(&dp[4]), EXTRACT_32BITS(&dp[5]));
	return (dp + 6);
}

/*
 * Pre operation attributes. Print only if vflag > 1.
 */
static const u_int32_t *
parse_pre_op_attr(const u_int32_t *dp, int verbose)
{
	TCHECK(dp[0]);
	if (!EXTRACT_32BITS(&dp[0]))
		return (dp + 1);
	dp++;
	TCHECK2(*dp, 24);
	if (verbose > 1) {
		return parse_wcc_attr(dp);
	} else {
		/* If not verbose enough, just skip over wcc_attr */
		return (dp + 6);
	}
trunc:
	return (NULL);
}

/*
 * Post operation attributes are printed if vflag >= 1
 */
static const u_int32_t *
parse_post_op_attr(const u_int32_t *dp, int verbose)
{
	TCHECK(dp[0]);
	if (!EXTRACT_32BITS(&dp[0]))
		return (dp + 1);
	dp++;
	if (verbose) {
		return parsefattr(dp, verbose, 1);
	} else
		return (dp + (NFSX_V3FATTR / sizeof (u_int32_t)));
trunc:
	return (NULL);
}

static const u_int32_t *
parse_wcc_data(const u_int32_t *dp, int verbose)
{
	if (verbose > 1)
		printf(" PRE:");
	if (!(dp = parse_pre_op_attr(dp, verbose)))
		return (0);

	if (verbose)
		printf(" POST:");
	return parse_post_op_attr(dp, verbose);
}

static const u_int32_t *
parsecreateopres(const u_int32_t *dp, int verbose)
{
	int er;

	if (!(dp = parsestatus(dp, &er)))
		return (0);
	if (er)
		dp = parse_wcc_data(dp, verbose);
	else {
		TCHECK(dp[0]);
		if (!EXTRACT_32BITS(&dp[0]))
			return (dp + 1);
		dp++;
		if (!(dp = parsefh(dp, 1)))
			return (0);
		if (verbose) {
			if (!(dp = parse_post_op_attr(dp, verbose)))
				return (0);
			if (vflag > 1) {
				printf(" dir attr:");
				dp = parse_wcc_data(dp, verbose);
			}
		}
	}
	return (dp);
trunc:
	return (NULL);
}

static int
parsewccres(const u_int32_t *dp, int verbose)
{
	int er;

	if (!(dp = parsestatus(dp, &er)))
		return (0);
	return parse_wcc_data(dp, verbose) != 0;
}

static const u_int32_t *
parsev3rddirres(const u_int32_t *dp, int verbose)
{
	int er;

	if (!(dp = parsestatus(dp, &er)))
		return (0);
	if (vflag)
		printf(" POST:");
	if (!(dp = parse_post_op_attr(dp, verbose)))
		return (0);
	if (er)
		return dp;
	if (vflag) {
		TCHECK(dp[1]);
		printf(" verf %08x%08x", dp[0], dp[1]);
		dp += 2;
	}
	return dp;
trunc:
	return (NULL);
}

static int
parsefsinfo(const u_int32_t *dp)
{
	struct nfsv3_fsinfo *sfp;
	int er;

	if (!(dp = parsestatus(dp, &er)))
		return (0);
	if (vflag)
		printf(" POST:");
	if (!(dp = parse_post_op_attr(dp, vflag)))
		return (0);
	if (er)
		return (1);

	sfp = (struct nfsv3_fsinfo *)dp;
	TCHECK(*sfp);
	printf(" rtmax %u rtpref %u wtmax %u wtpref %u dtpref %u",
	       EXTRACT_32BITS(&sfp->fs_rtmax),
	       EXTRACT_32BITS(&sfp->fs_rtpref),
	       EXTRACT_32BITS(&sfp->fs_wtmax),
	       EXTRACT_32BITS(&sfp->fs_wtpref),
	       EXTRACT_32BITS(&sfp->fs_dtpref));
	if (vflag) {
		printf(" rtmult %u wtmult %u maxfsz %" PRIu64,
		       EXTRACT_32BITS(&sfp->fs_rtmult),
		       EXTRACT_32BITS(&sfp->fs_wtmult),
		       EXTRACT_64BITS((u_int32_t *)&sfp->fs_maxfilesize));
		printf(" delta %u.%06u ",
		       EXTRACT_32BITS(&sfp->fs_timedelta.nfsv3_sec),
		       EXTRACT_32BITS(&sfp->fs_timedelta.nfsv3_nsec));
	}
	return (1);
trunc:
	return (0);
}

static int
parsepathconf(const u_int32_t *dp)
{
	int er;
	struct nfsv3_pathconf *spp;

	if (!(dp = parsestatus(dp, &er)))
		return (0);
	if (vflag)
		printf(" POST:");
	if (!(dp = parse_post_op_attr(dp, vflag)))
		return (0);
	if (er)
		return (1);

	spp = (struct nfsv3_pathconf *)dp;
	TCHECK(*spp);

	printf(" linkmax %u namemax %u %s %s %s %s",
	       EXTRACT_32BITS(&spp->pc_linkmax),
	       EXTRACT_32BITS(&spp->pc_namemax),
	       EXTRACT_32BITS(&spp->pc_notrunc) ? "notrunc" : "",
	       EXTRACT_32BITS(&spp->pc_chownrestricted) ? "chownres" : "",
	       EXTRACT_32BITS(&spp->pc_caseinsensitive) ? "igncase" : "",
	       EXTRACT_32BITS(&spp->pc_casepreserving) ? "keepcase" : "");
	return (1);
trunc:
	return (0);
}

static void
interp_reply(const struct sunrpc_msg *rp, u_int32_t proc, u_int32_t vers, int length)
{
	register const u_int32_t *dp;
	register int v3;
	int er;

	v3 = (vers == NFS_VER3);

	if (!v3 && proc < NFS_NPROCS)
		proc = nfsv3_procid[proc];

	switch (proc) {

	case NFSPROC_NOOP:
		printf(" nop");
		return;

	case NFSPROC_NULL:
		printf(" null");
		return;

	case NFSPROC_GETATTR:
		printf(" getattr");
		dp = parserep(rp, length);
		if (dp != NULL && parseattrstat(dp, !qflag, v3) != 0)
			return;
		break;

	case NFSPROC_SETATTR:
		printf(" setattr");
		if (!(dp = parserep(rp, length)))
			return;
		if (v3) {
			if (parsewccres(dp, vflag))
				return;
		} else {
			if (parseattrstat(dp, !qflag, 0) != 0)
				return;
		}
		break;

	case NFSPROC_LOOKUP:
		printf(" lookup");
		if (!(dp = parserep(rp, length)))
			break;
		if (v3) {
			if (!(dp = parsestatus(dp, &er)))
				break;
			if (er) {
				if (vflag > 1) {
					printf(" post dattr:");
					dp = parse_post_op_attr(dp, vflag);
				}
			} else {
				if (!(dp = parsefh(dp, v3)))
					break;
				if ((dp = parse_post_op_attr(dp, vflag)) &&
				    vflag > 1) {
					printf(" post dattr:");
					dp = parse_post_op_attr(dp, vflag);
				}
			}
			if (dp)
				return;
		} else {
			if (parsediropres(dp) != 0)
				return;
		}
		break;

	case NFSPROC_ACCESS:
		printf(" access");
		if (!(dp = parserep(rp, length)))
			break;
		if (!(dp = parsestatus(dp, &er)))
			break;
		if (vflag)
			printf(" attr:");
		if (!(dp = parse_post_op_attr(dp, vflag)))
			break;
		if (!er)
			printf(" c %04x", EXTRACT_32BITS(&dp[0]));
		return;

	case NFSPROC_READLINK:
		printf(" readlink");
		dp = parserep(rp, length);
		if (dp != NULL && parselinkres(dp, v3) != 0)
			return;
		break;

	case NFSPROC_READ:
		printf(" read");
		if (!(dp = parserep(rp, length)))
			break;
		if (v3) {
			if (!(dp = parsestatus(dp, &er)))
				break;
			if (!(dp = parse_post_op_attr(dp, vflag)))
				break;
			if (er)
				return;
			if (vflag) {
				TCHECK(dp[1]);
				printf(" %u bytes", EXTRACT_32BITS(&dp[0]));
				if (EXTRACT_32BITS(&dp[1]))
					printf(" EOF");
			}
			return;
		} else {
			if (parseattrstat(dp, vflag, 0) != 0)
				return;
		}
		break;

	case NFSPROC_WRITE:
		printf(" write");
		if (!(dp = parserep(rp, length)))
			break;
		if (v3) {
			if (!(dp = parsestatus(dp, &er)))
				break;
			if (!(dp = parse_wcc_data(dp, vflag)))
				break;
			if (er)
				return;
			if (vflag) {
				TCHECK(dp[0]);
				printf(" %u bytes", EXTRACT_32BITS(&dp[0]));
				if (vflag > 1) {
					TCHECK(dp[1]);
					printf(" <%s>",
						tok2str(nfsv3_writemodes,
							NULL, EXTRACT_32BITS(&dp[1])));
				}
				return;
			}
		} else {
			if (parseattrstat(dp, vflag, v3) != 0)
				return;
		}
		break;

	case NFSPROC_CREATE:
		printf(" create");
		if (!(dp = parserep(rp, length)))
			break;
		if (v3) {
			if (parsecreateopres(dp, vflag) != 0)
				return;
		} else {
			if (parsediropres(dp) != 0)
				return;
		}
		break;

	case NFSPROC_MKDIR:
		printf(" mkdir");
		if (!(dp = parserep(rp, length)))
			break;
		if (v3) {
			if (parsecreateopres(dp, vflag) != 0)
				return;
		} else {
			if (parsediropres(dp) != 0)
				return;
		}
		break;

	case NFSPROC_SYMLINK:
		printf(" symlink");
		if (!(dp = parserep(rp, length)))
			break;
		if (v3) {
			if (parsecreateopres(dp, vflag) != 0)
				return;
		} else {
			if (parsestatus(dp, &er) != 0)
				return;
		}
		break;

	case NFSPROC_MKNOD:
		printf(" mknod");
		if (!(dp = parserep(rp, length)))
			break;
		if (parsecreateopres(dp, vflag) != 0)
			return;
		break;

	case NFSPROC_REMOVE:
		printf(" remove");
		if (!(dp = parserep(rp, length)))
			break;
		if (v3) {
			if (parsewccres(dp, vflag))
				return;
		} else {
			if (parsestatus(dp, &er) != 0)
				return;
		}
		break;

	case NFSPROC_RMDIR:
		printf(" rmdir");
		if (!(dp = parserep(rp, length)))
			break;
		if (v3) {
			if (parsewccres(dp, vflag))
				return;
		} else {
			if (parsestatus(dp, &er) != 0)
				return;
		}
		break;

	case NFSPROC_RENAME:
		printf(" rename");
		if (!(dp = parserep(rp, length)))
			break;
		if (v3) {
			if (!(dp = parsestatus(dp, &er)))
				break;
			if (vflag) {
				printf(" from:");
				if (!(dp = parse_wcc_data(dp, vflag)))
					break;
				printf(" to:");
				if (!(dp = parse_wcc_data(dp, vflag)))
					break;
			}
			return;
		} else {
			if (parsestatus(dp, &er) != 0)
				return;
		}
		break;

	case NFSPROC_LINK:
		printf(" link");
		if (!(dp = parserep(rp, length)))
			break;
		if (v3) {
			if (!(dp = parsestatus(dp, &er)))
				break;
			if (vflag) {
				printf(" file POST:");
				if (!(dp = parse_post_op_attr(dp, vflag)))
					break;
				printf(" dir:");
				if (!(dp = parse_wcc_data(dp, vflag)))
					break;
				return;
			}
		} else {
			if (parsestatus(dp, &er) != 0)
				return;
		}
		break;

	case NFSPROC_READDIR:
		printf(" readdir");
		if (!(dp = parserep(rp, length)))
			break;
		if (v3) {
			if (parsev3rddirres(dp, vflag))
				return;
		} else {
			if (parserddires(dp) != 0)
				return;
		}
		break;

	case NFSPROC_READDIRPLUS:
		printf(" readdirplus");
		if (!(dp = parserep(rp, length)))
			break;
		if (parsev3rddirres(dp, vflag))
			return;
		break;

	case NFSPROC_FSSTAT:
		printf(" fsstat");
		dp = parserep(rp, length);
		if (dp != NULL && parsestatfs(dp, v3) != 0)
			return;
		break;

	case NFSPROC_FSINFO:
		printf(" fsinfo");
		dp = parserep(rp, length);
		if (dp != NULL && parsefsinfo(dp) != 0)
			return;
		break;

	case NFSPROC_PATHCONF:
		printf(" pathconf");
		dp = parserep(rp, length);
		if (dp != NULL && parsepathconf(dp) != 0)
			return;
		break;

	case NFSPROC_COMMIT:
		printf(" commit");
		dp = parserep(rp, length);
		if (dp != NULL && parsewccres(dp, vflag) != 0)
			return;
		break;

	default:
		printf(" proc-%u", proc);
		return;
	}
trunc:
	if (!nfserr)
		fputs(" [|nfs]", stdout);
}
