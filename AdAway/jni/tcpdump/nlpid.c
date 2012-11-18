/* 
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
 *
 * Original code by Hannes Gredler (hannes@juniper.net)
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/nlpid.c,v 1.4 2004/10/19 15:27:55 hannes Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>
#include "interface.h"
#include "nlpid.h"

struct tok nlpid_values[] = {
    { NLPID_NULLNS, "NULL" },
    { NLPID_Q933, "Q.933" },
    { NLPID_LMI, "LMI" },
    { NLPID_SNAP, "SNAP" },
    { NLPID_CLNP, "CLNP" },
    { NLPID_ESIS, "ES-IS" },
    { NLPID_ISIS, "IS-IS" },
    { NLPID_CONS, "CONS" },
    { NLPID_IDRP, "IDRP" },
    { NLPID_MFR, "FRF.15" },
    { NLPID_IP, "IPv4" },
    { NLPID_PPP, "PPP" },
    { NLPID_X25_ESIS, "X25 ES-IS" },
    { NLPID_IP6, "IPv6" },
    { 0, NULL }
};
