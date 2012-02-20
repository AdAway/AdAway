/*
 * Copyright (C) 1995, 1996, 1997, and 1998 WIDE Project.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the project nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE PROJECT AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE PROJECT OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
/* YIPS @(#)$Id: isakmp.h,v 1.10 2002/12/11 07:13:54 guy Exp $ */

/* refer to RFC 2408 */

/* must include <netinet/in.h> */

#if !defined(_ISAKMP_H_)
#define _ISAKMP_H_

typedef u_char cookie_t[8];
typedef u_char msgid_t[4];

typedef struct { /* i_cookie + r_cookie */
	cookie_t i_ck;
	cookie_t r_ck;
} isakmp_index;

#define INITIATOR       1
#define RESPONDER       2

#define PORT_ISAKMP 500

#define GENERATE  1
#define VALIDATE  0

/* Phase of oakley definition */
/*
  0000 0000 0000 0000
       |    |||| ||||
       |    |||| ++++--> negosiation number in phase
       |    ++++-------> phase number
       +---------------> expire ?
 */
#define ISAKMP_PH1      0x0010
#define ISAKMP_PH2      0x0020
#define ISAKMP_EXPIRED  0x0100

#define ISAKMP_NGP_0    0x0000
#define ISAKMP_NGP_1    0x0001
#define ISAKMP_NGP_2    0x0002
#define ISAKMP_NGP_3    0x0003
#define ISAKMP_NGP_4    0x0004

#define ISAKMP_PH1_N    (ISAKMP_PH1 | ISAKMP_NGP_0)  /* i.e. spawn */
#define ISAKMP_PH1_1    (ISAKMP_PH1 | ISAKMP_NGP_1)
#define ISAKMP_PH1_2    (ISAKMP_PH1 | ISAKMP_NGP_2)
#define ISAKMP_PH1_3    (ISAKMP_PH1 | ISAKMP_NGP_3)
#define ISAKMP_PH2_N    (ISAKMP_PH2 | ISAKMP_NGP_0)
#define ISAKMP_PH2_1    (ISAKMP_PH2 | ISAKMP_NGP_1)
#define ISAKMP_PH2_2    (ISAKMP_PH2 | ISAKMP_NGP_2)
#define ISAKMP_PH2_3    (ISAKMP_PH2 | ISAKMP_NGP_3)

#define ISAKMP_TIMER_DEFAULT     10 /* seconds */
#define ISAKMP_TRY_DEFAULT        3 /* times */

/* 3.1 ISAKMP Header Format
         0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        !                          Initiator                            !
        !                            Cookie                             !
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        !                          Responder                            !
        !                            Cookie                             !
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        !  Next Payload ! MjVer ! MnVer ! Exchange Type !     Flags     !
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        !                          Message ID                           !
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        !                            Length                             !
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
*/
struct isakmp {
	cookie_t i_ck;		/* Initiator Cookie */
	cookie_t r_ck;		/* Responder Cookie */
	u_int8_t np;		/* Next Payload Type */
	u_int8_t vers;
#define ISAKMP_VERS_MAJOR	0xf0
#define ISAKMP_VERS_MAJOR_SHIFT	4
#define ISAKMP_VERS_MINOR	0x0f
#define ISAKMP_VERS_MINOR_SHIFT	0
	u_int8_t etype;		/* Exchange Type */
	u_int8_t flags;		/* Flags */
	msgid_t msgid;
	u_int32_t len;		/* Length */
};

/* Next Payload Type */
#define ISAKMP_NPTYPE_NONE   0 /* NONE*/
#define ISAKMP_NPTYPE_SA     1 /* Security Association */
#define ISAKMP_NPTYPE_P      2 /* Proposal */
#define ISAKMP_NPTYPE_T      3 /* Transform */
#define ISAKMP_NPTYPE_KE     4 /* Key Exchange */
#define ISAKMP_NPTYPE_ID     5 /* Identification */
#define ISAKMP_NPTYPE_CERT   6 /* Certificate */
#define ISAKMP_NPTYPE_CR     7 /* Certificate Request */
#define ISAKMP_NPTYPE_HASH   8 /* Hash */
#define ISAKMP_NPTYPE_SIG    9 /* Signature */
#define ISAKMP_NPTYPE_NONCE 10 /* Nonce */
#define ISAKMP_NPTYPE_N     11 /* Notification */
#define ISAKMP_NPTYPE_D     12 /* Delete */
#define ISAKMP_NPTYPE_VID   13 /* Vendor ID */

#define ISAKMP_MAJOR_VERSION  1
#define ISAKMP_MINOR_VERSION  0

/* Exchange Type */
#define ISAKMP_ETYPE_NONE   0 /* NONE */
#define ISAKMP_ETYPE_BASE   1 /* Base */
#define ISAKMP_ETYPE_IDENT  2 /* Identity Proteciton */
#define ISAKMP_ETYPE_AUTH   3 /* Authentication Only */
#define ISAKMP_ETYPE_AGG    4 /* Aggressive */
#define ISAKMP_ETYPE_INF    5 /* Informational */

/* Flags */
#define ISAKMP_FLAG_E 0x01 /* Encryption Bit */
#define ISAKMP_FLAG_C 0x02 /* Commit Bit */

/* 3.2 Payload Generic Header
         0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        ! Next Payload  !   RESERVED    !         Payload Length        !
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
*/
struct isakmp_gen {
	u_int8_t  np;       /* Next Payload */
	u_int8_t  reserved; /* RESERVED, unused, must set to 0 */
	u_int16_t len;      /* Payload Length */
};

/* 3.3 Data Attributes
         0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        !A!       Attribute Type        !    AF=0  Attribute Length     !
        !F!                             !    AF=1  Attribute Value      !
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        .                   AF=0  Attribute Value                       .
        .                   AF=1  Not Transmitted                       .
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
*/
struct isakmp_data {
	u_int16_t type;     /* defined by DOI-spec, and Attribute Format */
	u_int16_t lorv;     /* if f equal 1, Attribute Length */
	                  /* if f equal 0, Attribute Value */
	/* if f equal 1, Attribute Value */
};
#define ISAKMP_GEN_TLV 0x0000
#define ISAKMP_GEN_TV  0x8000
	/* mask for type of attribute format */
#define ISAKMP_GEN_MASK 0x8000

/* 3.4 Security Association Payload */
	/* MAY NOT be used, because of being defined in ipsec-doi. */
	/*
	If the current payload is the last in the message,
	then the value of the next payload field will be 0.
	This field MUST NOT contain the
	values for the Proposal or Transform payloads as they are considered
	part of the security association negotiation.  For example, this
	field would contain the value "10" (Nonce payload) in the first
	message of a Base Exchange (see Section 4.4) and the value "0" in the
	first message of an Identity Protect Exchange (see Section 4.5).
	*/
struct isakmp_pl_sa {
	struct isakmp_gen h;
	u_int32_t doi; /* Domain of Interpretation */
	u_int32_t sit; /* Situation */
};

/* 3.5 Proposal Payload */
	/*
	The value of the next payload field MUST only contain the value "2"
	or "0".  If there are additional Proposal payloads in the message,
	then this field will be 2.  If the current Proposal payload is the
	last within the security association proposal, then this field will
	be 0.
	*/
struct isakmp_pl_p {
	struct isakmp_gen h;
	u_int8_t p_no;      /* Proposal # */
	u_int8_t prot_id;   /* Protocol */
	u_int8_t spi_size;  /* SPI Size */
	u_int8_t num_t;     /* Number of Transforms */
	/* SPI */
};

/* 3.6 Transform Payload */
	/*
	The value of the next payload field MUST only contain the value "3"
	or "0".  If there are additional Transform payloads in the proposal,
	then this field will be 3.  If the current Transform payload is the
	last within the proposal, then this field will be 0.
	*/
struct isakmp_pl_t {
	struct isakmp_gen h;
	u_int8_t  t_no;     /* Transform # */
	u_int8_t  t_id;     /* Transform-Id */
	u_int16_t reserved; /* RESERVED2 */
	/* SA Attributes */
};

/* 3.7 Key Exchange Payload */
struct isakmp_pl_ke {
	struct isakmp_gen h;
	/* Key Exchange Data */
};

/* 3.8 Identification Payload */
	/* MUST NOT to be used, because of being defined in ipsec-doi. */
struct isakmp_pl_id {
	struct isakmp_gen h;
	union {
		u_int8_t  id_type;   /* ID Type */
		u_int32_t doi_data;  /* DOI Specific ID Data */
	} d;
	/* Identification Data */
};

/* 3.9 Certificate Payload */
struct isakmp_pl_cert {
	struct isakmp_gen h;
	u_int8_t encode; /* Cert Encoding */
	char   cert;   /* Certificate Data */
		/*
		This field indicates the type of
		certificate or certificate-related information contained in the
		Certificate Data field.
		*/
};

/* Certificate Type */
#define ISAKMP_CERT_NONE   0
#define ISAKMP_CERT_PKCS   1
#define ISAKMP_CERT_PGP    2
#define ISAKMP_CERT_DNS    3
#define ISAKMP_CERT_SIGN   4
#define ISAKMP_CERT_KE     5
#define ISAKMP_CERT_KT     6
#define ISAKMP_CERT_CRL    7
#define ISAKMP_CERT_ARL    8
#define ISAKMP_CERT_SPKI   9

/* 3.10 Certificate Request Payload */
struct isakmp_pl_cr {
	struct isakmp_gen h;
	u_int8_t num_cert; /* # Cert. Types */
	/*
	Certificate Types (variable length)
	  -- Contains a list of the types of certificates requested,
	  sorted in order of preference.  Each individual certificate
	  type is 1 octet.  This field is NOT requiredo
	*/
	/* # Certificate Authorities (1 octet) */
	/* Certificate Authorities (variable length) */
};

/* 3.11 Hash Payload */
	/* may not be used, because of having only data. */
struct isakmp_pl_hash {
	struct isakmp_gen h;
	/* Hash Data */
};

/* 3.12 Signature Payload */
	/* may not be used, because of having only data. */
struct isakmp_pl_sig {
	struct isakmp_gen h;
	/* Signature Data */
};

/* 3.13 Nonce Payload */
	/* may not be used, because of having only data. */
struct isakmp_pl_nonce {
	struct isakmp_gen h;
	/* Nonce Data */
};

/* 3.14 Notification Payload */
struct isakmp_pl_n {
	struct isakmp_gen h;
	u_int32_t doi;      /* Domain of Interpretation */
	u_int8_t  prot_id;  /* Protocol-ID */
	u_int8_t  spi_size; /* SPI Size */
	u_int16_t type;     /* Notify Message Type */
	/* SPI */
	/* Notification Data */
};

/* 3.14.1 Notify Message Types */
/* NOTIFY MESSAGES - ERROR TYPES */
#define ISAKMP_NTYPE_INVALID_PAYLOAD_TYPE           1
#define ISAKMP_NTYPE_DOI_NOT_SUPPORTED              2
#define ISAKMP_NTYPE_SITUATION_NOT_SUPPORTED        3
#define ISAKMP_NTYPE_INVALID_COOKIE                 4
#define ISAKMP_NTYPE_INVALID_MAJOR_VERSION          5
#define ISAKMP_NTYPE_INVALID_MINOR_VERSION          6
#define ISAKMP_NTYPE_INVALID_EXCHANGE_TYPE          7
#define ISAKMP_NTYPE_INVALID_FLAGS                  8
#define ISAKMP_NTYPE_INVALID_MESSAGE_ID             9
#define ISAKMP_NTYPE_INVALID_PROTOCOL_ID            10
#define ISAKMP_NTYPE_INVALID_SPI                    11
#define ISAKMP_NTYPE_INVALID_TRANSFORM_ID           12
#define ISAKMP_NTYPE_ATTRIBUTES_NOT_SUPPORTED       13
#define ISAKMP_NTYPE_NO_PROPOSAL_CHOSEN             14
#define ISAKMP_NTYPE_BAD_PROPOSAL_SYNTAX            15
#define ISAKMP_NTYPE_PAYLOAD_MALFORMED              16
#define ISAKMP_NTYPE_INVALID_KEY_INFORMATION        17
#define ISAKMP_NTYPE_INVALID_ID_INFORMATION         18
#define ISAKMP_NTYPE_INVALID_CERT_ENCODING          19
#define ISAKMP_NTYPE_INVALID_CERTIFICATE            20
#define ISAKMP_NTYPE_BAD_CERT_REQUEST_SYNTAX        21
#define ISAKMP_NTYPE_INVALID_CERT_AUTHORITY         22
#define ISAKMP_NTYPE_INVALID_HASH_INFORMATION       23
#define ISAKMP_NTYPE_AUTHENTICATION_FAILED          24
#define ISAKMP_NTYPE_INVALID_SIGNATURE              25
#define ISAKMP_NTYPE_ADDRESS_NOTIFICATION           26
/* NOTIFY MESSAGES - STATUS TYPES */
#define ISAKMP_NTYPE_CONNECTED                   16384
/* using only to log */
#define ISAKMP_LOG_RETRY_LIMIT_REACHED           65530

/* 3.15 Delete Payload */
struct isakmp_pl_d {
	struct isakmp_gen h;
	u_int32_t doi;      /* Domain of Interpretation */
	u_int8_t  prot_id;  /* Protocol-Id */
	u_int8_t  spi_size; /* SPI Size */
	u_int16_t num_spi;  /* # of SPIs */
	/* SPI(es) */
};


struct isakmp_ph1tab {
	struct isakmp_ph1 *head;
	struct isakmp_ph1 *tail;
	int len;
};

struct isakmp_ph2tab {
	struct isakmp_ph2 *head;
	struct isakmp_ph2 *tail;
	int len;
};

#define EXCHANGE_PROXY   1
#define EXCHANGE_MYSELF  0

#define PFS_NEED	1
#define PFS_NONEED	0

#endif /* !defined(_ISAKMP_H_) */
