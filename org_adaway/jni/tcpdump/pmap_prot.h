/* @(#) $Header: /tcpdump/master/tcpdump/pmap_prot.h,v 1.1.2.2 2005/04/27 21:44:06 guy Exp $ (LBL) */
/*
 * Sun RPC is a product of Sun Microsystems, Inc. and is provided for
 * unrestricted use provided that this legend is included on all tape
 * media and as a part of the software program in whole or part.  Users
 * may copy or modify Sun RPC without charge, but are not authorized
 * to license or distribute it to anyone else except as part of a product or
 * program developed by the user.
 *
 * SUN RPC IS PROVIDED AS IS WITH NO WARRANTIES OF ANY KIND INCLUDING THE
 * WARRANTIES OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE, OR ARISING FROM A COURSE OF DEALING, USAGE OR TRADE PRACTICE.
 *
 * Sun RPC is provided with no support and without any obligation on the
 * part of Sun Microsystems, Inc. to assist in its use, correction,
 * modification or enhancement.
 *
 * SUN MICROSYSTEMS, INC. SHALL HAVE NO LIABILITY WITH RESPECT TO THE
 * INFRINGEMENT OF COPYRIGHTS, TRADE SECRETS OR ANY PATENTS BY SUN RPC
 * OR ANY PART THEREOF.
 *
 * In no event will Sun Microsystems, Inc. be liable for any lost revenue
 * or profits or other special, indirect and consequential damages, even if
 * Sun has been advised of the possibility of such damages.
 *
 * Sun Microsystems, Inc.
 * 2550 Garcia Avenue
 * Mountain View, California  94043
 *
 *	from: @(#)pmap_prot.h 1.14 88/02/08 SMI
 *	from: @(#)pmap_prot.h	2.1 88/07/29 4.0 RPCSRC
 * $FreeBSD: src/include/rpc/pmap_prot.h,v 1.9.2.1 1999/08/29 14:39:05 peter Exp $
 */

/*
 * pmap_prot.h
 * Protocol for the local binder service, or pmap.
 *
 * Copyright (C) 1984, Sun Microsystems, Inc.
 *
 * The following procedures are supported by the protocol:
 *
 * PMAPPROC_NULL() returns ()
 * 	takes nothing, returns nothing
 *
 * PMAPPROC_SET(struct pmap) returns (bool_t)
 * 	TRUE is success, FALSE is failure.  Registers the tuple
 *	[prog, vers, prot, port].
 *
 * PMAPPROC_UNSET(struct pmap) returns (bool_t)
 *	TRUE is success, FALSE is failure.  Un-registers pair
 *	[prog, vers].  prot and port are ignored.
 *
 * PMAPPROC_GETPORT(struct pmap) returns (long unsigned).
 *	0 is failure.  Otherwise returns the port number where the pair
 *	[prog, vers] is registered.  It may lie!
 *
 * PMAPPROC_DUMP() RETURNS (struct pmaplist *)
 *
 * PMAPPROC_CALLIT(unsigned, unsigned, unsigned, string<>)
 * 	RETURNS (port, string<>);
 * usage: encapsulatedresults = PMAPPROC_CALLIT(prog, vers, proc, encapsulatedargs);
 * 	Calls the procedure on the local machine.  If it is not registered,
 *	this procedure is quite; ie it does not return error information!!!
 *	This procedure only is supported on rpc/udp and calls via
 *	rpc/udp.  This routine only passes null authentication parameters.
 *	This file has no interface to xdr routines for PMAPPROC_CALLIT.
 *
 * The service supports remote procedure calls on udp/ip or tcp/ip socket 111.
 */

#define SUNRPC_PMAPPORT		((u_int16_t)111)
#define SUNRPC_PMAPPROG		((u_int32_t)100000)
#define SUNRPC_PMAPVERS		((u_int32_t)2)
#define SUNRPC_PMAPVERS_PROTO	((u_int32_t)2)
#define SUNRPC_PMAPVERS_ORIG	((u_int32_t)1)
#define SUNRPC_PMAPPROC_NULL	((u_int32_t)0)
#define SUNRPC_PMAPPROC_SET	((u_int32_t)1)
#define SUNRPC_PMAPPROC_UNSET	((u_int32_t)2)
#define SUNRPC_PMAPPROC_GETPORT	((u_int32_t)3)
#define SUNRPC_PMAPPROC_DUMP	((u_int32_t)4)
#define SUNRPC_PMAPPROC_CALLIT	((u_int32_t)5)

struct sunrpc_pmap {
	u_int32_t pm_prog;
	u_int32_t pm_vers;
	u_int32_t pm_prot;
	u_int32_t pm_port;
};
