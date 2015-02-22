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
 * Functions for signature and digest verification.
 *
 * Original code by Hannes Gredler (hannes@juniper.net)
 */

/* @(#) $Header: /tcpdump/master/tcpdump/signature.h,v 1.1 2008-08-16 11:36:20 hannes Exp $ (LBL) */

/* signature checking result codes */
#define SIGNATURE_VALID		0
#define SIGNATURE_INVALID	1
#define CANT_CHECK_SIGNATURE	2

extern const struct tok signature_check_values[];
extern int signature_verify (const u_char *, u_int, u_char *);
