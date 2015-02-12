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

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/signature.c,v 1.2 2008-09-22 20:22:10 guy Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <string.h>

#include "interface.h"
#include "signature.h"

#ifdef HAVE_LIBCRYPTO
#include <openssl/md5.h>
#endif

const struct tok signature_check_values[] = {
    { SIGNATURE_VALID, "valid"},
    { SIGNATURE_INVALID, "invalid"},
    { CANT_CHECK_SIGNATURE, "unchecked"},
    { 0, NULL }
};


#ifdef HAVE_LIBCRYPTO
/*
 * Compute a HMAC MD5 sum.
 * Taken from rfc2104, Appendix.
 */
USES_APPLE_DEPRECATED_API
static void
signature_compute_hmac_md5(const u_int8_t *text, int text_len, unsigned char *key,
                           unsigned int key_len, u_int8_t *digest)
{
    MD5_CTX context;
    unsigned char k_ipad[65];    /* inner padding - key XORd with ipad */
    unsigned char k_opad[65];    /* outer padding - key XORd with opad */
    unsigned char tk[16];
    int i;

    /* if key is longer than 64 bytes reset it to key=MD5(key) */
    if (key_len > 64) {

        MD5_CTX tctx;

        MD5_Init(&tctx);
        MD5_Update(&tctx, key, key_len);
        MD5_Final(tk, &tctx);

        key = tk;
        key_len = 16;
    }

    /*
     * the HMAC_MD5 transform looks like:
     *
     * MD5(K XOR opad, MD5(K XOR ipad, text))
     *
     * where K is an n byte key
     * ipad is the byte 0x36 repeated 64 times
     * opad is the byte 0x5c repeated 64 times
     * and text is the data being protected
     */

    /* start out by storing key in pads */
    memset(k_ipad, 0, sizeof k_ipad);
    memset(k_opad, 0, sizeof k_opad);
    memcpy(k_ipad, key, key_len);
    memcpy(k_opad, key, key_len);

    /* XOR key with ipad and opad values */
    for (i=0; i<64; i++) {
        k_ipad[i] ^= 0x36;
        k_opad[i] ^= 0x5c;
    }

    /*
     * perform inner MD5
     */
    MD5_Init(&context);                   /* init context for 1st pass */
    MD5_Update(&context, k_ipad, 64);     /* start with inner pad */
    MD5_Update(&context, text, text_len); /* then text of datagram */
    MD5_Final(digest, &context);          /* finish up 1st pass */

    /*
     * perform outer MD5
     */
    MD5_Init(&context);                   /* init context for 2nd pass */
    MD5_Update(&context, k_opad, 64);     /* start with outer pad */
    MD5_Update(&context, digest, 16);     /* then results of 1st hash */
    MD5_Final(digest, &context);          /* finish up 2nd pass */
}
USES_APPLE_RST
#endif

#ifdef HAVE_LIBCRYPTO
/*
 * Verify a cryptographic signature of the packet.
 * Currently only MD5 is supported.
 */
int
signature_verify (const u_char *pptr, u_int plen, u_char *sig_ptr)
{
    u_int8_t rcvsig[16];
    u_int8_t sig[16];
    unsigned int i;

    /*
     * Save the signature before clearing it.
     */
    memcpy(rcvsig, sig_ptr, sizeof(rcvsig));
    memset(sig_ptr, 0, sizeof(rcvsig));

    if (!sigsecret) {
        return (CANT_CHECK_SIGNATURE);
    }

    signature_compute_hmac_md5(pptr, plen, (unsigned char *)sigsecret,
                               strlen(sigsecret), sig);

    if (memcmp(rcvsig, sig, sizeof(sig)) == 0) {
        return (SIGNATURE_VALID);

    } else {

        for (i = 0; i < sizeof(sig); ++i) {
            (void)printf("%02x", sig[i]);
        }

        return (SIGNATURE_INVALID);
    }
}
#endif

/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 4
 * End:
 */
