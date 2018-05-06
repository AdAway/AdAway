/* @(#) $Header: /tcpdump/master/tcpdump/token.h,v 1.6 2002-12-11 07:14:12 guy Exp $ (LBL) */
/*
 * Copyright (c) 1998, Larry Lile
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice unmodified, this list of conditions, and the following
 *    disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 */

#define TOKEN_HDRLEN		14
#define TOKEN_RING_MAC_LEN	6
#define ROUTING_SEGMENT_MAX	16
#define IS_SOURCE_ROUTED(trp)	((trp)->token_shost[0] & 0x80)
#define FRAME_TYPE(trp)		(((trp)->token_fc & 0xC0) >> 6)
#define TOKEN_FC_LLC		1

#define BROADCAST(trp)		((EXTRACT_16BITS(&(trp)->token_rcf) & 0xE000) >> 13)
#define RIF_LENGTH(trp)		((EXTRACT_16BITS(&(trp)->token_rcf) & 0x1f00) >> 8)
#define DIRECTION(trp)		((EXTRACT_16BITS(&(trp)->token_rcf) & 0x0080) >> 7)
#define LARGEST_FRAME(trp)	((EXTRACT_16BITS(&(trp)->token_rcf) & 0x0070) >> 4)
#define RING_NUMBER(trp, x)	((EXTRACT_16BITS(&(trp)->token_rseg[x]) & 0xfff0) >> 4)
#define BRIDGE_NUMBER(trp, x)	((EXTRACT_16BITS(&(trp)->token_rseg[x]) & 0x000f))
#define SEGMENT_COUNT(trp)	((int)((RIF_LENGTH(trp) - 2) / 2))

struct token_header {
	u_int8_t  token_ac;
	u_int8_t  token_fc;
	u_int8_t  token_dhost[TOKEN_RING_MAC_LEN];
	u_int8_t  token_shost[TOKEN_RING_MAC_LEN];
	u_int16_t token_rcf;
	u_int16_t token_rseg[ROUTING_SEGMENT_MAX];
};
