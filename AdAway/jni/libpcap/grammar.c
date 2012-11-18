/* A Bison parser, made by GNU Bison 2.1.  */

/* Skeleton parser for Yacc-like parsing with Bison,
   Copyright (C) 1984, 1989, 1990, 2000, 2001, 2002, 2003, 2004, 2005 Free Software Foundation, Inc.

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2, or (at your option)
   any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 51 Franklin Street, Fifth Floor,
   Boston, MA 02110-1301, USA.  */

/* As a special exception, when this file is copied by Bison into a
   Bison output file, you may use that output file without restriction.
   This special exception was added by the Free Software Foundation
   in version 1.24 of Bison.  */

/* Written by Richard Stallman by simplifying the original so called
   ``semantic'' parser.  */

/* All symbols defined below should begin with yy or YY, to avoid
   infringing on user name space.  This should be done even for local
   variables, as they might otherwise be expanded by user macros.
   There are some unavoidable exceptions within include files to
   define necessary library symbols; they are noted "INFRINGES ON
   USER NAME SPACE" below.  */

/* Identify Bison output.  */
#define YYBISON 1

/* Bison version.  */
#define YYBISON_VERSION "2.1"

/* Skeleton name.  */
#define YYSKELETON_NAME "yacc.c"

/* Pure parsers.  */
#define YYPURE 0

/* Using locations.  */
#define YYLSP_NEEDED 0

/* Substitute the variable and function names.  */
#define yyparse pcap_parse
#define yylex   pcap_lex
#define yyerror pcap_error
#define yylval  pcap_lval
#define yychar  pcap_char
#define yydebug pcap_debug
#define yynerrs pcap_nerrs


/* Tokens.  */
#ifndef YYTOKENTYPE
# define YYTOKENTYPE
   /* Put the tokens into the symbol table, so that GDB and other debuggers
      know about them.  */
   enum yytokentype {
     DST = 258,
     SRC = 259,
     HOST = 260,
     GATEWAY = 261,
     NET = 262,
     NETMASK = 263,
     PORT = 264,
     PORTRANGE = 265,
     LESS = 266,
     GREATER = 267,
     PROTO = 268,
     PROTOCHAIN = 269,
     CBYTE = 270,
     ARP = 271,
     RARP = 272,
     IP = 273,
     SCTP = 274,
     TCP = 275,
     UDP = 276,
     ICMP = 277,
     IGMP = 278,
     IGRP = 279,
     PIM = 280,
     VRRP = 281,
     ATALK = 282,
     AARP = 283,
     DECNET = 284,
     LAT = 285,
     SCA = 286,
     MOPRC = 287,
     MOPDL = 288,
     TK_BROADCAST = 289,
     TK_MULTICAST = 290,
     NUM = 291,
     INBOUND = 292,
     OUTBOUND = 293,
     PF_IFNAME = 294,
     PF_RSET = 295,
     PF_RNR = 296,
     PF_SRNR = 297,
     PF_REASON = 298,
     PF_ACTION = 299,
     LINK = 300,
     GEQ = 301,
     LEQ = 302,
     NEQ = 303,
     ID = 304,
     EID = 305,
     HID = 306,
     HID6 = 307,
     AID = 308,
     LSH = 309,
     RSH = 310,
     LEN = 311,
     IPV6 = 312,
     ICMPV6 = 313,
     AH = 314,
     ESP = 315,
     VLAN = 316,
     MPLS = 317,
     PPPOED = 318,
     PPPOES = 319,
     ISO = 320,
     ESIS = 321,
     CLNP = 322,
     ISIS = 323,
     L1 = 324,
     L2 = 325,
     IIH = 326,
     LSP = 327,
     SNP = 328,
     CSNP = 329,
     PSNP = 330,
     STP = 331,
     IPX = 332,
     NETBEUI = 333,
     LANE = 334,
     LLC = 335,
     METAC = 336,
     BCC = 337,
     SC = 338,
     ILMIC = 339,
     OAMF4EC = 340,
     OAMF4SC = 341,
     OAM = 342,
     OAMF4 = 343,
     CONNECTMSG = 344,
     METACONNECT = 345,
     VPI = 346,
     VCI = 347,
     RADIO = 348,
     FISU = 349,
     LSSU = 350,
     MSU = 351,
     SIO = 352,
     OPC = 353,
     DPC = 354,
     SLS = 355,
     AND = 356,
     OR = 357,
     UMINUS = 358
   };
#endif
/* Tokens.  */
#define DST 258
#define SRC 259
#define HOST 260
#define GATEWAY 261
#define NET 262
#define NETMASK 263
#define PORT 264
#define PORTRANGE 265
#define LESS 266
#define GREATER 267
#define PROTO 268
#define PROTOCHAIN 269
#define CBYTE 270
#define ARP 271
#define RARP 272
#define IP 273
#define SCTP 274
#define TCP 275
#define UDP 276
#define ICMP 277
#define IGMP 278
#define IGRP 279
#define PIM 280
#define VRRP 281
#define ATALK 282
#define AARP 283
#define DECNET 284
#define LAT 285
#define SCA 286
#define MOPRC 287
#define MOPDL 288
#define TK_BROADCAST 289
#define TK_MULTICAST 290
#define NUM 291
#define INBOUND 292
#define OUTBOUND 293
#define PF_IFNAME 294
#define PF_RSET 295
#define PF_RNR 296
#define PF_SRNR 297
#define PF_REASON 298
#define PF_ACTION 299
#define LINK 300
#define GEQ 301
#define LEQ 302
#define NEQ 303
#define ID 304
#define EID 305
#define HID 306
#define HID6 307
#define AID 308
#define LSH 309
#define RSH 310
#define LEN 311
#define IPV6 312
#define ICMPV6 313
#define AH 314
#define ESP 315
#define VLAN 316
#define MPLS 317
#define PPPOED 318
#define PPPOES 319
#define ISO 320
#define ESIS 321
#define CLNP 322
#define ISIS 323
#define L1 324
#define L2 325
#define IIH 326
#define LSP 327
#define SNP 328
#define CSNP 329
#define PSNP 330
#define STP 331
#define IPX 332
#define NETBEUI 333
#define LANE 334
#define LLC 335
#define METAC 336
#define BCC 337
#define SC 338
#define ILMIC 339
#define OAMF4EC 340
#define OAMF4SC 341
#define OAM 342
#define OAMF4 343
#define CONNECTMSG 344
#define METACONNECT 345
#define VPI 346
#define VCI 347
#define RADIO 348
#define FISU 349
#define LSSU 350
#define MSU 351
#define SIO 352
#define OPC 353
#define DPC 354
#define SLS 355
#define AND 356
#define OR 357
#define UMINUS 358




/* Copy the first part of user declarations.  */
#line 1 "grammar.y"

/*
 * Copyright (c) 1988, 1989, 1990, 1991, 1992, 1993, 1994, 1995, 1996
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
 */
#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/libpcap/grammar.y,v 1.86.2.9 2007/09/12 19:17:25 guy Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#ifdef WIN32
#include <pcap-stdinc.h>
#else /* WIN32 */
#include <sys/types.h>
#include <sys/socket.h>
#endif /* WIN32 */

#include <stdlib.h>

#ifndef WIN32
#if __STDC__
struct mbuf;
struct rtentry;
#endif

#include <netinet/in.h>
#endif /* WIN32 */

#include <stdio.h>

#include "pcap-int.h"

#include "gencode.h"
#ifdef HAVE_NET_PFVAR_H
#include <net/if.h>
#include <net/pfvar.h>
#include <net/if_pflog.h>
#endif
#include <pcap-namedb.h>

#ifdef HAVE_OS_PROTO_H
#include "os-proto.h"
#endif

#define QSET(q, p, d, a) (q).proto = (p),\
			 (q).dir = (d),\
			 (q).addr = (a)

int n_errors = 0;

static struct qual qerr = { Q_UNDEF, Q_UNDEF, Q_UNDEF, Q_UNDEF };

static void
yyerror(const char *msg)
{
	++n_errors;
	bpf_error("%s", msg);
	/* NOTREACHED */
}

#ifndef YYBISON
int yyparse(void);

int
pcap_parse()
{
	return (yyparse());
}
#endif

#ifdef HAVE_NET_PFVAR_H
static int
pfreason_to_num(const char *reason)
{
	const char *reasons[] = PFRES_NAMES;
	int i;

	for (i = 0; reasons[i]; i++) {
		if (pcap_strcasecmp(reason, reasons[i]) == 0)
			return (i);
	}
	bpf_error("unknown PF reason");
	/*NOTREACHED*/
}

static int
pfaction_to_num(const char *action)
{
	if (pcap_strcasecmp(action, "pass") == 0 ||
	    pcap_strcasecmp(action, "accept") == 0)
		return (PF_PASS);
	else if (pcap_strcasecmp(action, "drop") == 0 ||
		pcap_strcasecmp(action, "block") == 0)
		return (PF_DROP);
	else {
		bpf_error("unknown PF action");
		/*NOTREACHED*/
	}
}
#else /* !HAVE_NET_PFVAR_H */
static int
pfreason_to_num(const char *reason)
{
	bpf_error("libpcap was compiled on a machine without pf support");
	/*NOTREACHED*/
}

static int
pfaction_to_num(const char *action)
{
	bpf_error("libpcap was compiled on a machine without pf support");
	/*NOTREACHED*/
}
#endif /* HAVE_NET_PFVAR_H */


/* Enabling traces.  */
#ifndef YYDEBUG
# define YYDEBUG 0
#endif

/* Enabling verbose error messages.  */
#ifdef YYERROR_VERBOSE
# undef YYERROR_VERBOSE
# define YYERROR_VERBOSE 1
#else
# define YYERROR_VERBOSE 0
#endif

/* Enabling the token table.  */
#ifndef YYTOKEN_TABLE
# define YYTOKEN_TABLE 0
#endif

#if ! defined (YYSTYPE) && ! defined (YYSTYPE_IS_DECLARED)
#line 138 "grammar.y"
typedef union YYSTYPE {
	int i;
	bpf_u_int32 h;
	u_char *e;
	char *s;
	struct stmt *stmt;
	struct arth *a;
	struct {
		struct qual q;
		int atmfieldtype;
		int mtp3fieldtype;
		struct block *b;
	} blk;
	struct block *rblk;
} YYSTYPE;
/* Line 196 of yacc.c.  */
#line 452 "y.tab.c"
# define yystype YYSTYPE /* obsolescent; will be withdrawn */
# define YYSTYPE_IS_DECLARED 1
# define YYSTYPE_IS_TRIVIAL 1
#endif



/* Copy the second part of user declarations.  */


/* Line 219 of yacc.c.  */
#line 464 "y.tab.c"

#if ! defined (YYSIZE_T) && defined (__SIZE_TYPE__)
# define YYSIZE_T __SIZE_TYPE__
#endif
#if ! defined (YYSIZE_T) && defined (size_t)
# define YYSIZE_T size_t
#endif
#if ! defined (YYSIZE_T) && (defined (__STDC__) || defined (__cplusplus))
# include <stddef.h> /* INFRINGES ON USER NAME SPACE */
# define YYSIZE_T size_t
#endif
#if ! defined (YYSIZE_T)
# define YYSIZE_T unsigned int
#endif

#ifndef YY_
# if YYENABLE_NLS
#  if ENABLE_NLS
#   include <libintl.h> /* INFRINGES ON USER NAME SPACE */
#   define YY_(msgid) dgettext ("bison-runtime", msgid)
#  endif
# endif
# ifndef YY_
#  define YY_(msgid) msgid
# endif
#endif

#if ! defined (yyoverflow) || YYERROR_VERBOSE

/* The parser invokes alloca or malloc; define the necessary symbols.  */

# ifdef YYSTACK_USE_ALLOCA
#  if YYSTACK_USE_ALLOCA
#   ifdef __GNUC__
#    define YYSTACK_ALLOC __builtin_alloca
#   else
#    define YYSTACK_ALLOC alloca
#    if defined (__STDC__) || defined (__cplusplus)
#     include <stdlib.h> /* INFRINGES ON USER NAME SPACE */
#     define YYINCLUDED_STDLIB_H
#    endif
#   endif
#  endif
# endif

# ifdef YYSTACK_ALLOC
   /* Pacify GCC's `empty if-body' warning. */
#  define YYSTACK_FREE(Ptr) do { /* empty */; } while (0)
#  ifndef YYSTACK_ALLOC_MAXIMUM
    /* The OS might guarantee only one guard page at the bottom of the stack,
       and a page size can be as small as 4096 bytes.  So we cannot safely
       invoke alloca (N) if N exceeds 4096.  Use a slightly smaller number
       to allow for a few compiler-allocated temporary stack slots.  */
#   define YYSTACK_ALLOC_MAXIMUM 4032 /* reasonable circa 2005 */
#  endif
# else
#  define YYSTACK_ALLOC YYMALLOC
#  define YYSTACK_FREE YYFREE
#  ifndef YYSTACK_ALLOC_MAXIMUM
#   define YYSTACK_ALLOC_MAXIMUM ((YYSIZE_T) -1)
#  endif
#  ifdef __cplusplus
extern "C" {
#  endif
#  ifndef YYMALLOC
#   define YYMALLOC malloc
#   if (! defined (malloc) && ! defined (YYINCLUDED_STDLIB_H) \
	&& (defined (__STDC__) || defined (__cplusplus)))
void *malloc (YYSIZE_T); /* INFRINGES ON USER NAME SPACE */
#   endif
#  endif
#  ifndef YYFREE
#   define YYFREE free
#   if (! defined (free) && ! defined (YYINCLUDED_STDLIB_H) \
	&& (defined (__STDC__) || defined (__cplusplus)))
void free (void *); /* INFRINGES ON USER NAME SPACE */
#   endif
#  endif
#  ifdef __cplusplus
}
#  endif
# endif
#endif /* ! defined (yyoverflow) || YYERROR_VERBOSE */


#if (! defined (yyoverflow) \
     && (! defined (__cplusplus) \
	 || (defined (YYSTYPE_IS_TRIVIAL) && YYSTYPE_IS_TRIVIAL)))

/* A type that is properly aligned for any stack member.  */
union yyalloc
{
  short int yyss;
  YYSTYPE yyvs;
  };

/* The size of the maximum gap between one aligned stack and the next.  */
# define YYSTACK_GAP_MAXIMUM (sizeof (union yyalloc) - 1)

/* The size of an array large to enough to hold all stacks, each with
   N elements.  */
# define YYSTACK_BYTES(N) \
     ((N) * (sizeof (short int) + sizeof (YYSTYPE))			\
      + YYSTACK_GAP_MAXIMUM)

/* Copy COUNT objects from FROM to TO.  The source and destination do
   not overlap.  */
# ifndef YYCOPY
#  if defined (__GNUC__) && 1 < __GNUC__
#   define YYCOPY(To, From, Count) \
      __builtin_memcpy (To, From, (Count) * sizeof (*(From)))
#  else
#   define YYCOPY(To, From, Count)		\
      do					\
	{					\
	  YYSIZE_T yyi;				\
	  for (yyi = 0; yyi < (Count); yyi++)	\
	    (To)[yyi] = (From)[yyi];		\
	}					\
      while (0)
#  endif
# endif

/* Relocate STACK from its old location to the new one.  The
   local variables YYSIZE and YYSTACKSIZE give the old and new number of
   elements in the stack, and YYPTR gives the new location of the
   stack.  Advance YYPTR to a properly aligned location for the next
   stack.  */
# define YYSTACK_RELOCATE(Stack)					\
    do									\
      {									\
	YYSIZE_T yynewbytes;						\
	YYCOPY (&yyptr->Stack, Stack, yysize);				\
	Stack = &yyptr->Stack;						\
	yynewbytes = yystacksize * sizeof (*Stack) + YYSTACK_GAP_MAXIMUM; \
	yyptr += yynewbytes / sizeof (*yyptr);				\
      }									\
    while (0)

#endif

#if defined (__STDC__) || defined (__cplusplus)
   typedef signed char yysigned_char;
#else
   typedef short int yysigned_char;
#endif

/* YYFINAL -- State number of the termination state. */
#define YYFINAL  3
/* YYLAST -- Last index in YYTABLE.  */
#define YYLAST   605

/* YYNTOKENS -- Number of terminals. */
#define YYNTOKENS  119
/* YYNNTS -- Number of nonterminals. */
#define YYNNTS  41
/* YYNRULES -- Number of rules. */
#define YYNRULES  186
/* YYNRULES -- Number of states. */
#define YYNSTATES  254

/* YYTRANSLATE(YYLEX) -- Bison symbol number corresponding to YYLEX.  */
#define YYUNDEFTOK  2
#define YYMAXUTOK   358

#define YYTRANSLATE(YYX)						\
  ((unsigned int) (YYX) <= YYMAXUTOK ? yytranslate[YYX] : YYUNDEFTOK)

/* YYTRANSLATE[YYLEX] -- Bison symbol number corresponding to YYLEX.  */
static const unsigned char yytranslate[] =
{
       0,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,   103,     2,     2,     2,     2,   105,     2,
     112,   111,   108,   106,     2,   107,     2,   109,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,   118,     2,
     115,   114,   113,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,   116,     2,   117,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,   104,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     1,     2,     3,     4,
       5,     6,     7,     8,     9,    10,    11,    12,    13,    14,
      15,    16,    17,    18,    19,    20,    21,    22,    23,    24,
      25,    26,    27,    28,    29,    30,    31,    32,    33,    34,
      35,    36,    37,    38,    39,    40,    41,    42,    43,    44,
      45,    46,    47,    48,    49,    50,    51,    52,    53,    54,
      55,    56,    57,    58,    59,    60,    61,    62,    63,    64,
      65,    66,    67,    68,    69,    70,    71,    72,    73,    74,
      75,    76,    77,    78,    79,    80,    81,    82,    83,    84,
      85,    86,    87,    88,    89,    90,    91,    92,    93,    94,
      95,    96,    97,    98,    99,   100,   101,   102,   110
};

#if YYDEBUG
/* YYPRHS[YYN] -- Index of the first RHS symbol of rule number YYN in
   YYRHS.  */
static const unsigned short int yyprhs[] =
{
       0,     0,     3,     6,     8,     9,    11,    15,    19,    23,
      27,    29,    31,    33,    35,    39,    41,    45,    49,    51,
      55,    57,    59,    61,    64,    66,    68,    70,    74,    78,
      80,    82,    84,    87,    91,    94,    97,   100,   103,   106,
     109,   113,   115,   119,   123,   125,   127,   129,   132,   134,
     137,   139,   140,   142,   144,   148,   152,   156,   160,   162,
     164,   166,   168,   170,   172,   174,   176,   178,   180,   182,
     184,   186,   188,   190,   192,   194,   196,   198,   200,   202,
     204,   206,   208,   210,   212,   214,   216,   218,   220,   222,
     224,   226,   228,   230,   232,   234,   236,   238,   240,   242,
     244,   246,   249,   252,   255,   258,   263,   265,   267,   270,
     272,   275,   277,   279,   281,   283,   286,   289,   292,   295,
     298,   301,   303,   305,   307,   309,   311,   313,   315,   317,
     319,   321,   323,   328,   335,   339,   343,   347,   351,   355,
     359,   363,   367,   370,   374,   376,   378,   380,   382,   384,
     386,   388,   392,   394,   396,   398,   400,   402,   404,   406,
     408,   410,   412,   414,   416,   418,   420,   422,   425,   428,
     432,   434,   436,   440,   442,   444,   446,   448,   450,   452,
     454,   456,   459,   462,   466,   468,   470
};

/* YYRHS -- A `-1'-separated list of the rules' RHS. */
static const short int yyrhs[] =
{
     120,     0,    -1,   121,   122,    -1,   121,    -1,    -1,   131,
      -1,   122,   123,   131,    -1,   122,   123,   125,    -1,   122,
     124,   131,    -1,   122,   124,   125,    -1,   101,    -1,   102,
      -1,   126,    -1,   148,    -1,   128,   129,   111,    -1,    49,
      -1,    51,   109,    36,    -1,    51,     8,    51,    -1,    51,
      -1,    52,   109,    36,    -1,    52,    -1,    50,    -1,    53,
      -1,   127,   125,    -1,   103,    -1,   112,    -1,   126,    -1,
     130,   123,   125,    -1,   130,   124,   125,    -1,   148,    -1,
     129,    -1,   133,    -1,   127,   131,    -1,   134,   135,   136,
      -1,   134,   135,    -1,   134,   136,    -1,   134,    13,    -1,
     134,    14,    -1,   134,   137,    -1,   132,   125,    -1,   128,
     122,   111,    -1,   138,    -1,   145,   143,   145,    -1,   145,
     144,   145,    -1,   139,    -1,   149,    -1,   150,    -1,   151,
     152,    -1,   155,    -1,   156,   157,    -1,   138,    -1,    -1,
       4,    -1,     3,    -1,     4,   102,     3,    -1,     3,   102,
       4,    -1,     4,   101,     3,    -1,     3,   101,     4,    -1,
       5,    -1,     7,    -1,     9,    -1,    10,    -1,     6,    -1,
      45,    -1,    18,    -1,    16,    -1,    17,    -1,    19,    -1,
      20,    -1,    21,    -1,    22,    -1,    23,    -1,    24,    -1,
      25,    -1,    26,    -1,    27,    -1,    28,    -1,    29,    -1,
      30,    -1,    31,    -1,    33,    -1,    32,    -1,    57,    -1,
      58,    -1,    59,    -1,    60,    -1,    65,    -1,    66,    -1,
      68,    -1,    69,    -1,    70,    -1,    71,    -1,    72,    -1,
      73,    -1,    75,    -1,    74,    -1,    67,    -1,    76,    -1,
      77,    -1,    78,    -1,    93,    -1,   134,    34,    -1,   134,
      35,    -1,    11,    36,    -1,    12,    36,    -1,    15,    36,
     147,    36,    -1,    37,    -1,    38,    -1,    61,   148,    -1,
      61,    -1,    62,   148,    -1,    62,    -1,    63,    -1,    64,
      -1,   140,    -1,    39,    49,    -1,    40,    49,    -1,    41,
      36,    -1,    42,    36,    -1,    43,   141,    -1,    44,   142,
      -1,    36,    -1,    49,    -1,    49,    -1,   113,    -1,    46,
      -1,   114,    -1,    47,    -1,   115,    -1,    48,    -1,   148,
      -1,   146,    -1,   138,   116,   145,   117,    -1,   138,   116,
     145,   118,    36,   117,    -1,   145,   106,   145,    -1,   145,
     107,   145,    -1,   145,   108,   145,    -1,   145,   109,   145,
      -1,   145,   105,   145,    -1,   145,   104,   145,    -1,   145,
      54,   145,    -1,   145,    55,   145,    -1,   107,   145,    -1,
     128,   146,   111,    -1,    56,    -1,   105,    -1,   104,    -1,
     115,    -1,   113,    -1,   114,    -1,    36,    -1,   128,   148,
     111,    -1,    79,    -1,    80,    -1,    81,    -1,    82,    -1,
      85,    -1,    86,    -1,    83,    -1,    84,    -1,    87,    -1,
      88,    -1,    89,    -1,    90,    -1,    91,    -1,    92,    -1,
     153,    -1,   143,    36,    -1,   144,    36,    -1,   128,   154,
     111,    -1,    36,    -1,   153,    -1,   154,   124,   153,    -1,
      94,    -1,    95,    -1,    96,    -1,    97,    -1,    98,    -1,
      99,    -1,   100,    -1,   158,    -1,   143,    36,    -1,   144,
      36,    -1,   128,   159,   111,    -1,    36,    -1,   158,    -1,
     159,   124,   158,    -1
};

/* YYRLINE[YYN] -- source line where rule number YYN was defined.  */
static const unsigned short int yyrline[] =
{
       0,   210,   210,   214,   216,   218,   219,   220,   221,   222,
     224,   226,   228,   229,   231,   233,   234,   236,   238,   243,
     252,   261,   270,   279,   281,   283,   285,   286,   287,   289,
     291,   293,   294,   296,   297,   298,   299,   300,   301,   303,
     304,   305,   306,   308,   310,   311,   312,   313,   314,   315,
     318,   319,   322,   323,   324,   325,   326,   327,   330,   331,
     332,   333,   336,   338,   339,   340,   341,   342,   343,   344,
     345,   346,   347,   348,   349,   350,   351,   352,   353,   354,
     355,   356,   357,   358,   359,   360,   361,   362,   363,   364,
     365,   366,   367,   368,   369,   370,   371,   372,   373,   374,
     375,   377,   378,   379,   380,   381,   382,   383,   384,   385,
     386,   387,   388,   389,   390,   393,   394,   395,   396,   397,
     398,   401,   402,   405,   408,   409,   410,   412,   413,   414,
     416,   417,   419,   420,   421,   422,   423,   424,   425,   426,
     427,   428,   429,   430,   431,   433,   434,   435,   436,   437,
     439,   440,   442,   443,   444,   445,   446,   447,   448,   449,
     451,   452,   453,   454,   457,   458,   460,   461,   462,   463,
     465,   472,   473,   476,   477,   478,   481,   482,   483,   484,
     486,   487,   488,   489,   491,   500,   501
};
#endif

#if YYDEBUG || YYERROR_VERBOSE || YYTOKEN_TABLE
/* YYTNAME[SYMBOL-NUM] -- String name of the symbol SYMBOL-NUM.
   First, the terminals, then, starting at YYNTOKENS, nonterminals. */
static const char *const yytname[] =
{
  "$end", "error", "$undefined", "DST", "SRC", "HOST", "GATEWAY", "NET",
  "NETMASK", "PORT", "PORTRANGE", "LESS", "GREATER", "PROTO", "PROTOCHAIN",
  "CBYTE", "ARP", "RARP", "IP", "SCTP", "TCP", "UDP", "ICMP", "IGMP",
  "IGRP", "PIM", "VRRP", "ATALK", "AARP", "DECNET", "LAT", "SCA", "MOPRC",
  "MOPDL", "TK_BROADCAST", "TK_MULTICAST", "NUM", "INBOUND", "OUTBOUND",
  "PF_IFNAME", "PF_RSET", "PF_RNR", "PF_SRNR", "PF_REASON", "PF_ACTION",
  "LINK", "GEQ", "LEQ", "NEQ", "ID", "EID", "HID", "HID6", "AID", "LSH",
  "RSH", "LEN", "IPV6", "ICMPV6", "AH", "ESP", "VLAN", "MPLS", "PPPOED",
  "PPPOES", "ISO", "ESIS", "CLNP", "ISIS", "L1", "L2", "IIH", "LSP", "SNP",
  "CSNP", "PSNP", "STP", "IPX", "NETBEUI", "LANE", "LLC", "METAC", "BCC",
  "SC", "ILMIC", "OAMF4EC", "OAMF4SC", "OAM", "OAMF4", "CONNECTMSG",
  "METACONNECT", "VPI", "VCI", "RADIO", "FISU", "LSSU", "MSU", "SIO",
  "OPC", "DPC", "SLS", "AND", "OR", "'!'", "'|'", "'&'", "'+'", "'-'",
  "'*'", "'/'", "UMINUS", "')'", "'('", "'>'", "'='", "'<'", "'['", "']'",
  "':'", "$accept", "prog", "null", "expr", "and", "or", "id", "nid",
  "not", "paren", "pid", "qid", "term", "head", "rterm", "pqual", "dqual",
  "aqual", "ndaqual", "pname", "other", "pfvar", "reason", "action",
  "relop", "irelop", "arth", "narth", "byteop", "pnum", "atmtype",
  "atmmultitype", "atmfield", "atmvalue", "atmfieldvalue", "atmlistvalue",
  "mtp2type", "mtp3field", "mtp3value", "mtp3fieldvalue", "mtp3listvalue", 0
};
#endif

# ifdef YYPRINT
/* YYTOKNUM[YYLEX-NUM] -- Internal token number corresponding to
   token YYLEX-NUM.  */
static const unsigned short int yytoknum[] =
{
       0,   256,   257,   258,   259,   260,   261,   262,   263,   264,
     265,   266,   267,   268,   269,   270,   271,   272,   273,   274,
     275,   276,   277,   278,   279,   280,   281,   282,   283,   284,
     285,   286,   287,   288,   289,   290,   291,   292,   293,   294,
     295,   296,   297,   298,   299,   300,   301,   302,   303,   304,
     305,   306,   307,   308,   309,   310,   311,   312,   313,   314,
     315,   316,   317,   318,   319,   320,   321,   322,   323,   324,
     325,   326,   327,   328,   329,   330,   331,   332,   333,   334,
     335,   336,   337,   338,   339,   340,   341,   342,   343,   344,
     345,   346,   347,   348,   349,   350,   351,   352,   353,   354,
     355,   356,   357,    33,   124,    38,    43,    45,    42,    47,
     358,    41,    40,    62,    61,    60,    91,    93,    58
};
# endif

/* YYR1[YYN] -- Symbol number of symbol that rule YYN derives.  */
static const unsigned char yyr1[] =
{
       0,   119,   120,   120,   121,   122,   122,   122,   122,   122,
     123,   124,   125,   125,   125,   126,   126,   126,   126,   126,
     126,   126,   126,   126,   127,   128,   129,   129,   129,   130,
     130,   131,   131,   132,   132,   132,   132,   132,   132,   133,
     133,   133,   133,   133,   133,   133,   133,   133,   133,   133,
     134,   134,   135,   135,   135,   135,   135,   135,   136,   136,
     136,   136,   137,   138,   138,   138,   138,   138,   138,   138,
     138,   138,   138,   138,   138,   138,   138,   138,   138,   138,
     138,   138,   138,   138,   138,   138,   138,   138,   138,   138,
     138,   138,   138,   138,   138,   138,   138,   138,   138,   138,
     138,   139,   139,   139,   139,   139,   139,   139,   139,   139,
     139,   139,   139,   139,   139,   140,   140,   140,   140,   140,
     140,   141,   141,   142,   143,   143,   143,   144,   144,   144,
     145,   145,   146,   146,   146,   146,   146,   146,   146,   146,
     146,   146,   146,   146,   146,   147,   147,   147,   147,   147,
     148,   148,   149,   149,   149,   149,   149,   149,   149,   149,
     150,   150,   150,   150,   151,   151,   152,   152,   152,   152,
     153,   154,   154,   155,   155,   155,   156,   156,   156,   156,
     157,   157,   157,   157,   158,   159,   159
};

/* YYR2[YYN] -- Number of symbols composing right hand side of rule YYN.  */
static const unsigned char yyr2[] =
{
       0,     2,     2,     1,     0,     1,     3,     3,     3,     3,
       1,     1,     1,     1,     3,     1,     3,     3,     1,     3,
       1,     1,     1,     2,     1,     1,     1,     3,     3,     1,
       1,     1,     2,     3,     2,     2,     2,     2,     2,     2,
       3,     1,     3,     3,     1,     1,     1,     2,     1,     2,
       1,     0,     1,     1,     3,     3,     3,     3,     1,     1,
       1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
       1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
       1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
       1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
       1,     2,     2,     2,     2,     4,     1,     1,     2,     1,
       2,     1,     1,     1,     1,     2,     2,     2,     2,     2,
       2,     1,     1,     1,     1,     1,     1,     1,     1,     1,
       1,     1,     4,     6,     3,     3,     3,     3,     3,     3,
       3,     3,     2,     3,     1,     1,     1,     1,     1,     1,
       1,     3,     1,     1,     1,     1,     1,     1,     1,     1,
       1,     1,     1,     1,     1,     1,     1,     2,     2,     3,
       1,     1,     3,     1,     1,     1,     1,     1,     1,     1,
       1,     2,     2,     3,     1,     1,     3
};

/* YYDEFACT[STATE-NAME] -- Default rule to reduce with in state
   STATE-NUM when YYTABLE doesn't specify something else to do.  Zero
   means the default is an error.  */
static const unsigned char yydefact[] =
{
       4,     0,    51,     1,     0,     0,     0,    65,    66,    64,
      67,    68,    69,    70,    71,    72,    73,    74,    75,    76,
      77,    78,    79,    81,    80,   150,   106,   107,     0,     0,
       0,     0,     0,     0,    63,   144,    82,    83,    84,    85,
     109,   111,   112,   113,    86,    87,    96,    88,    89,    90,
      91,    92,    93,    95,    94,    97,    98,    99,   152,   153,
     154,   155,   158,   159,   156,   157,   160,   161,   162,   163,
     164,   165,   100,   173,   174,   175,   176,   177,   178,   179,
      24,     0,    25,     2,    51,    51,     5,     0,    31,     0,
      50,    44,   114,     0,   131,   130,    45,    46,     0,    48,
       0,   103,   104,     0,   115,   116,   117,   118,   121,   122,
     119,   123,   120,     0,   108,   110,     0,     0,   142,    10,
      11,    51,    51,    32,     0,   131,   130,    15,    21,    18,
      20,    22,    39,    12,     0,     0,    13,    53,    52,    58,
      62,    59,    60,    61,    36,    37,   101,   102,    34,    35,
      38,     0,   125,   127,   129,     0,     0,     0,     0,     0,
       0,     0,     0,   124,   126,   128,     0,     0,   170,     0,
       0,     0,    47,   166,   184,     0,     0,     0,    49,   180,
     146,   145,   148,   149,   147,     0,     0,     0,     7,    51,
      51,     6,   130,     9,     8,    40,   143,   151,     0,     0,
       0,    23,    26,    30,     0,    29,     0,     0,     0,     0,
      33,     0,   140,   141,   139,   138,   134,   135,   136,   137,
      42,    43,   171,     0,   167,   168,   185,     0,   181,   182,
     105,   130,    17,    16,    19,    14,     0,     0,    57,    55,
      56,    54,   132,     0,   169,     0,   183,     0,    27,    28,
       0,   172,   186,   133
};

/* YYDEFGOTO[NTERM-NUM]. */
static const short int yydefgoto[] =
{
      -1,     1,     2,   124,   121,   122,   201,   133,   134,   116,
     203,   204,    86,    87,    88,    89,   148,   149,   150,   117,
      91,    92,   110,   112,   166,   167,    93,    94,   185,    95,
      96,    97,    98,   172,   173,   223,    99,   100,   178,   179,
     227
};

/* YYPACT[STATE-NUM] -- Index in YYTABLE of the portion describing
   STATE-NUM.  */
#define YYPACT_NINF -181
static const short int yypact[] =
{
    -181,    19,   200,  -181,    -3,     1,    15,  -181,  -181,  -181,
    -181,  -181,  -181,  -181,  -181,  -181,  -181,  -181,  -181,  -181,
    -181,  -181,  -181,  -181,  -181,  -181,  -181,  -181,    13,    20,
      18,    44,   -26,    35,  -181,  -181,  -181,  -181,  -181,  -181,
     -19,   -19,  -181,  -181,  -181,  -181,  -181,  -181,  -181,  -181,
    -181,  -181,  -181,  -181,  -181,  -181,  -181,  -181,  -181,  -181,
    -181,  -181,  -181,  -181,  -181,  -181,  -181,  -181,  -181,  -181,
    -181,  -181,  -181,  -181,  -181,  -181,  -181,  -181,  -181,  -181,
    -181,   493,  -181,    41,   396,   396,  -181,   -24,  -181,   155,
       2,  -181,  -181,   488,  -181,  -181,  -181,  -181,     9,  -181,
     134,  -181,  -181,    62,  -181,  -181,  -181,  -181,  -181,  -181,
    -181,  -181,  -181,   -19,  -181,  -181,   493,    -9,  -181,  -181,
    -181,   298,   298,  -181,   -53,   -14,    -1,  -181,  -181,     0,
       3,  -181,  -181,  -181,   -24,   -24,  -181,    77,    82,  -181,
    -181,  -181,  -181,  -181,  -181,  -181,  -181,  -181,   437,  -181,
    -181,   493,  -181,  -181,  -181,   493,   493,   493,   493,   493,
     493,   493,   493,  -181,  -181,  -181,   493,   493,  -181,    78,
      81,    89,  -181,  -181,  -181,    95,   108,   113,  -181,  -181,
    -181,  -181,  -181,  -181,  -181,   120,    -1,   -34,  -181,   298,
     298,  -181,     4,  -181,  -181,  -181,  -181,  -181,   112,   149,
     150,  -181,  -181,    80,    41,    -1,   188,   189,   205,   206,
    -181,   -41,    65,    65,    98,   144,   -93,   -93,  -181,  -181,
     -34,   -34,  -181,   -80,  -181,  -181,  -181,   -58,  -181,  -181,
    -181,    46,  -181,  -181,  -181,  -181,   -24,   -24,  -181,  -181,
    -181,  -181,  -181,   174,  -181,    78,  -181,    95,  -181,  -181,
      96,  -181,  -181,  -181
};

/* YYPGOTO[NTERM-NUM].  */
static const short int yypgoto[] =
{
    -181,  -181,  -181,   212,    50,  -180,   -86,   -89,     5,    -2,
    -181,  -181,   -81,  -181,  -181,  -181,  -181,   107,  -181,     7,
    -181,  -181,  -181,  -181,   -66,   -39,   -21,   -74,  -181,   -35,
    -181,  -181,  -181,  -181,  -151,  -181,  -181,  -181,  -181,  -145,
    -181
};

/* YYTABLE[YYPACT[STATE-NUM]].  What to do in state STATE-NUM.  If
   positive, shift that token.  If negative, reduce the rule which
   number is the opposite.  If zero, do what YYDEFACT says.
   If YYTABLE_NINF, syntax error.  */
#define YYTABLE_NINF -42
static const short int yytable[] =
{
      85,   132,   -41,   123,   -13,   114,   115,    84,   198,    90,
     108,   125,    25,   155,   156,   161,   162,    25,   222,     3,
     155,   156,   120,   109,   237,   127,   128,   129,   130,   131,
     226,   244,   170,   101,   176,   188,   193,   102,   113,   113,
     191,   194,   125,   245,   120,   168,   202,   247,   119,   120,
     126,   103,   136,   246,   106,   152,   153,   154,   195,   171,
     118,   177,   104,   157,   158,   159,   160,   161,   162,   105,
     157,   158,   159,   160,   161,   162,   242,   243,   186,    80,
     107,   126,    85,    85,   111,   135,   192,   192,    82,    84,
      84,    90,    90,    82,   251,   187,   169,   196,   175,   136,
     205,   202,   252,   -41,   -41,   -13,   -13,   151,   123,   199,
     197,   113,   200,   -41,   168,   -13,   125,   224,   151,   190,
     190,    82,   163,   164,   165,   225,   189,   189,    90,    90,
     211,   174,   135,   113,   212,   213,   214,   215,   216,   217,
     218,   219,   119,   120,   228,   220,   221,   -29,   -29,   229,
     248,   249,   155,   156,   192,   231,   230,   197,   137,   138,
     139,   140,   141,   232,   142,   143,   180,   181,   144,   145,
     174,   159,   160,   161,   162,   182,   183,   184,   206,   207,
     152,   153,   154,   208,   209,   233,   234,   190,    85,   146,
     147,   235,   238,   239,   189,   189,    90,    90,   155,   156,
      -3,   136,   136,   158,   159,   160,   161,   162,   240,   241,
     250,     4,     5,   253,    83,     6,     7,     8,     9,    10,
      11,    12,    13,    14,    15,    16,    17,    18,    19,    20,
      21,    22,    23,    24,   135,   135,    25,    26,    27,    28,
      29,    30,    31,    32,    33,    34,    82,   163,   164,   165,
     159,   160,   161,   162,   236,   210,    35,    36,    37,    38,
      39,    40,    41,    42,    43,    44,    45,    46,    47,    48,
      49,    50,    51,    52,    53,    54,    55,    56,    57,    58,
      59,    60,    61,    62,    63,    64,    65,    66,    67,    68,
      69,    70,    71,    72,    73,    74,    75,    76,    77,    78,
      79,     0,     0,    80,     0,     0,     0,    81,     0,     4,
       5,     0,    82,     6,     7,     8,     9,    10,    11,    12,
      13,    14,    15,    16,    17,    18,    19,    20,    21,    22,
      23,    24,     0,     0,    25,    26,    27,    28,    29,    30,
      31,    32,    33,    34,     0,     0,     0,   127,   128,   129,
     130,   131,     0,     0,    35,    36,    37,    38,    39,    40,
      41,    42,    43,    44,    45,    46,    47,    48,    49,    50,
      51,    52,    53,    54,    55,    56,    57,    58,    59,    60,
      61,    62,    63,    64,    65,    66,    67,    68,    69,    70,
      71,    72,    73,    74,    75,    76,    77,    78,    79,     0,
       0,    80,     0,     0,     0,    81,     0,     4,     5,     0,
      82,     6,     7,     8,     9,    10,    11,    12,    13,    14,
      15,    16,    17,    18,    19,    20,    21,    22,    23,    24,
       0,     0,    25,    26,    27,    28,    29,    30,    31,    32,
      33,    34,   139,     0,   141,     0,   142,   143,     0,     0,
       0,     0,    35,    36,    37,    38,    39,    40,    41,    42,
      43,    44,    45,    46,    47,    48,    49,    50,    51,    52,
      53,    54,    55,    56,    57,    58,    59,    60,    61,    62,
      63,    64,    65,    66,    67,    68,    69,    70,    71,    72,
      73,    74,    75,    76,    77,    78,    79,     0,     0,    80,
       0,     0,     0,    81,     0,     0,     0,     0,    82,     7,
       8,     9,    10,    11,    12,    13,    14,    15,    16,    17,
      18,    19,    20,    21,    22,    23,    24,     0,     0,    25,
       0,     0,     0,     0,   152,   153,   154,     0,    34,     0,
       0,     0,   155,   156,     0,     0,     0,     0,     0,    35,
      36,    37,    38,    39,     0,     0,     0,     0,    44,    45,
      46,    47,    48,    49,    50,    51,    52,    53,    54,    55,
      56,    57,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,    72,     0,     0,     0,
       0,     0,   157,   158,   159,   160,   161,   162,     0,     0,
      81,   163,   164,   165,     0,    82
};

static const short int yycheck[] =
{
       2,    87,     0,    84,     0,    40,    41,     2,     8,     2,
      36,    85,    36,    54,    55,   108,   109,    36,   169,     0,
      54,    55,   102,    49,   204,    49,    50,    51,    52,    53,
     175,   111,    98,    36,   100,   121,   122,    36,    40,    41,
     121,   122,   116,   223,   102,    36,   135,   227,   101,   102,
      85,    36,    87,   111,    36,    46,    47,    48,   111,    98,
      81,   100,    49,   104,   105,   106,   107,   108,   109,    49,
     104,   105,   106,   107,   108,   109,   117,   118,   113,   103,
      36,   116,    84,    85,    49,    87,   121,   122,   112,    84,
      85,    84,    85,   112,   245,   116,    98,   111,   100,   134,
     135,   190,   247,   101,   102,   101,   102,   116,   189,   109,
     111,   113,   109,   111,    36,   111,   190,    36,   116,   121,
     122,   112,   113,   114,   115,    36,   121,   122,   121,   122,
     151,    36,   134,   135,   155,   156,   157,   158,   159,   160,
     161,   162,   101,   102,    36,   166,   167,   101,   102,    36,
     236,   237,    54,    55,   189,   190,    36,   111,     3,     4,
       5,     6,     7,    51,     9,    10,   104,   105,    13,    14,
      36,   106,   107,   108,   109,   113,   114,   115,   101,   102,
      46,    47,    48,   101,   102,    36,    36,   189,   190,    34,
      35,   111,     4,     4,   189,   190,   189,   190,    54,    55,
       0,   236,   237,   105,   106,   107,   108,   109,     3,     3,
      36,    11,    12,   117,     2,    15,    16,    17,    18,    19,
      20,    21,    22,    23,    24,    25,    26,    27,    28,    29,
      30,    31,    32,    33,   236,   237,    36,    37,    38,    39,
      40,    41,    42,    43,    44,    45,   112,   113,   114,   115,
     106,   107,   108,   109,   204,   148,    56,    57,    58,    59,
      60,    61,    62,    63,    64,    65,    66,    67,    68,    69,
      70,    71,    72,    73,    74,    75,    76,    77,    78,    79,
      80,    81,    82,    83,    84,    85,    86,    87,    88,    89,
      90,    91,    92,    93,    94,    95,    96,    97,    98,    99,
     100,    -1,    -1,   103,    -1,    -1,    -1,   107,    -1,    11,
      12,    -1,   112,    15,    16,    17,    18,    19,    20,    21,
      22,    23,    24,    25,    26,    27,    28,    29,    30,    31,
      32,    33,    -1,    -1,    36,    37,    38,    39,    40,    41,
      42,    43,    44,    45,    -1,    -1,    -1,    49,    50,    51,
      52,    53,    -1,    -1,    56,    57,    58,    59,    60,    61,
      62,    63,    64,    65,    66,    67,    68,    69,    70,    71,
      72,    73,    74,    75,    76,    77,    78,    79,    80,    81,
      82,    83,    84,    85,    86,    87,    88,    89,    90,    91,
      92,    93,    94,    95,    96,    97,    98,    99,   100,    -1,
      -1,   103,    -1,    -1,    -1,   107,    -1,    11,    12,    -1,
     112,    15,    16,    17,    18,    19,    20,    21,    22,    23,
      24,    25,    26,    27,    28,    29,    30,    31,    32,    33,
      -1,    -1,    36,    37,    38,    39,    40,    41,    42,    43,
      44,    45,     5,    -1,     7,    -1,     9,    10,    -1,    -1,
      -1,    -1,    56,    57,    58,    59,    60,    61,    62,    63,
      64,    65,    66,    67,    68,    69,    70,    71,    72,    73,
      74,    75,    76,    77,    78,    79,    80,    81,    82,    83,
      84,    85,    86,    87,    88,    89,    90,    91,    92,    93,
      94,    95,    96,    97,    98,    99,   100,    -1,    -1,   103,
      -1,    -1,    -1,   107,    -1,    -1,    -1,    -1,   112,    16,
      17,    18,    19,    20,    21,    22,    23,    24,    25,    26,
      27,    28,    29,    30,    31,    32,    33,    -1,    -1,    36,
      -1,    -1,    -1,    -1,    46,    47,    48,    -1,    45,    -1,
      -1,    -1,    54,    55,    -1,    -1,    -1,    -1,    -1,    56,
      57,    58,    59,    60,    -1,    -1,    -1,    -1,    65,    66,
      67,    68,    69,    70,    71,    72,    73,    74,    75,    76,
      77,    78,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    93,    -1,    -1,    -1,
      -1,    -1,   104,   105,   106,   107,   108,   109,    -1,    -1,
     107,   113,   114,   115,    -1,   112
};

/* YYSTOS[STATE-NUM] -- The (internal number of the) accessing
   symbol of state STATE-NUM.  */
static const unsigned char yystos[] =
{
       0,   120,   121,     0,    11,    12,    15,    16,    17,    18,
      19,    20,    21,    22,    23,    24,    25,    26,    27,    28,
      29,    30,    31,    32,    33,    36,    37,    38,    39,    40,
      41,    42,    43,    44,    45,    56,    57,    58,    59,    60,
      61,    62,    63,    64,    65,    66,    67,    68,    69,    70,
      71,    72,    73,    74,    75,    76,    77,    78,    79,    80,
      81,    82,    83,    84,    85,    86,    87,    88,    89,    90,
      91,    92,    93,    94,    95,    96,    97,    98,    99,   100,
     103,   107,   112,   122,   127,   128,   131,   132,   133,   134,
     138,   139,   140,   145,   146,   148,   149,   150,   151,   155,
     156,    36,    36,    36,    49,    49,    36,    36,    36,    49,
     141,    49,   142,   128,   148,   148,   128,   138,   145,   101,
     102,   123,   124,   131,   122,   146,   148,    49,    50,    51,
      52,    53,   125,   126,   127,   128,   148,     3,     4,     5,
       6,     7,     9,    10,    13,    14,    34,    35,   135,   136,
     137,   116,    46,    47,    48,    54,    55,   104,   105,   106,
     107,   108,   109,   113,   114,   115,   143,   144,    36,   128,
     143,   144,   152,   153,    36,   128,   143,   144,   157,   158,
     104,   105,   113,   114,   115,   147,   148,   145,   125,   127,
     128,   131,   148,   125,   131,   111,   111,   111,     8,   109,
     109,   125,   126,   129,   130,   148,   101,   102,   101,   102,
     136,   145,   145,   145,   145,   145,   145,   145,   145,   145,
     145,   145,   153,   154,    36,    36,   158,   159,    36,    36,
      36,   148,    51,    36,    36,   111,   123,   124,     4,     4,
       3,     3,   117,   118,   111,   124,   111,   124,   125,   125,
      36,   153,   158,   117
};

#define yyerrok		(yyerrstatus = 0)
#define yyclearin	(yychar = YYEMPTY)
#define YYEMPTY		(-2)
#define YYEOF		0

#define YYACCEPT	goto yyacceptlab
#define YYABORT		goto yyabortlab
#define YYERROR		goto yyerrorlab


/* Like YYERROR except do call yyerror.  This remains here temporarily
   to ease the transition to the new meaning of YYERROR, for GCC.
   Once GCC version 2 has supplanted version 1, this can go.  */

#define YYFAIL		goto yyerrlab

#define YYRECOVERING()  (!!yyerrstatus)

#define YYBACKUP(Token, Value)					\
do								\
  if (yychar == YYEMPTY && yylen == 1)				\
    {								\
      yychar = (Token);						\
      yylval = (Value);						\
      yytoken = YYTRANSLATE (yychar);				\
      YYPOPSTACK;						\
      goto yybackup;						\
    }								\
  else								\
    {								\
      yyerror (YY_("syntax error: cannot back up")); \
      YYERROR;							\
    }								\
while (0)


#define YYTERROR	1
#define YYERRCODE	256


/* YYLLOC_DEFAULT -- Set CURRENT to span from RHS[1] to RHS[N].
   If N is 0, then set CURRENT to the empty location which ends
   the previous symbol: RHS[0] (always defined).  */

#define YYRHSLOC(Rhs, K) ((Rhs)[K])
#ifndef YYLLOC_DEFAULT
# define YYLLOC_DEFAULT(Current, Rhs, N)				\
    do									\
      if (N)								\
	{								\
	  (Current).first_line   = YYRHSLOC (Rhs, 1).first_line;	\
	  (Current).first_column = YYRHSLOC (Rhs, 1).first_column;	\
	  (Current).last_line    = YYRHSLOC (Rhs, N).last_line;		\
	  (Current).last_column  = YYRHSLOC (Rhs, N).last_column;	\
	}								\
      else								\
	{								\
	  (Current).first_line   = (Current).last_line   =		\
	    YYRHSLOC (Rhs, 0).last_line;				\
	  (Current).first_column = (Current).last_column =		\
	    YYRHSLOC (Rhs, 0).last_column;				\
	}								\
    while (0)
#endif


/* YY_LOCATION_PRINT -- Print the location on the stream.
   This macro was not mandated originally: define only if we know
   we won't break user code: when these are the locations we know.  */

#ifndef YY_LOCATION_PRINT
# if YYLTYPE_IS_TRIVIAL
#  define YY_LOCATION_PRINT(File, Loc)			\
     fprintf (File, "%d.%d-%d.%d",			\
              (Loc).first_line, (Loc).first_column,	\
              (Loc).last_line,  (Loc).last_column)
# else
#  define YY_LOCATION_PRINT(File, Loc) ((void) 0)
# endif
#endif


/* YYLEX -- calling `yylex' with the right arguments.  */

#ifdef YYLEX_PARAM
# define YYLEX yylex (YYLEX_PARAM)
#else
# define YYLEX yylex ()
#endif

/* Enable debugging if requested.  */
#if YYDEBUG

# ifndef YYFPRINTF
#  include <stdio.h> /* INFRINGES ON USER NAME SPACE */
#  define YYFPRINTF fprintf
# endif

# define YYDPRINTF(Args)			\
do {						\
  if (yydebug)					\
    YYFPRINTF Args;				\
} while (0)

# define YY_SYMBOL_PRINT(Title, Type, Value, Location)		\
do {								\
  if (yydebug)							\
    {								\
      YYFPRINTF (stderr, "%s ", Title);				\
      yysymprint (stderr,					\
                  Type, Value);	\
      YYFPRINTF (stderr, "\n");					\
    }								\
} while (0)

/*------------------------------------------------------------------.
| yy_stack_print -- Print the state stack from its BOTTOM up to its |
| TOP (included).                                                   |
`------------------------------------------------------------------*/

#if defined (__STDC__) || defined (__cplusplus)
static void
yy_stack_print (short int *bottom, short int *top)
#else
static void
yy_stack_print (bottom, top)
    short int *bottom;
    short int *top;
#endif
{
  YYFPRINTF (stderr, "Stack now");
  for (/* Nothing. */; bottom <= top; ++bottom)
    YYFPRINTF (stderr, " %d", *bottom);
  YYFPRINTF (stderr, "\n");
}

# define YY_STACK_PRINT(Bottom, Top)				\
do {								\
  if (yydebug)							\
    yy_stack_print ((Bottom), (Top));				\
} while (0)


/*------------------------------------------------.
| Report that the YYRULE is going to be reduced.  |
`------------------------------------------------*/

#if defined (__STDC__) || defined (__cplusplus)
static void
yy_reduce_print (int yyrule)
#else
static void
yy_reduce_print (yyrule)
    int yyrule;
#endif
{
  int yyi;
  unsigned long int yylno = yyrline[yyrule];
  YYFPRINTF (stderr, "Reducing stack by rule %d (line %lu), ",
             yyrule - 1, yylno);
  /* Print the symbols being reduced, and their result.  */
  for (yyi = yyprhs[yyrule]; 0 <= yyrhs[yyi]; yyi++)
    YYFPRINTF (stderr, "%s ", yytname[yyrhs[yyi]]);
  YYFPRINTF (stderr, "-> %s\n", yytname[yyr1[yyrule]]);
}

# define YY_REDUCE_PRINT(Rule)		\
do {					\
  if (yydebug)				\
    yy_reduce_print (Rule);		\
} while (0)

/* Nonzero means print parse trace.  It is left uninitialized so that
   multiple parsers can coexist.  */
int yydebug;
#else /* !YYDEBUG */
# define YYDPRINTF(Args)
# define YY_SYMBOL_PRINT(Title, Type, Value, Location)
# define YY_STACK_PRINT(Bottom, Top)
# define YY_REDUCE_PRINT(Rule)
#endif /* !YYDEBUG */


/* YYINITDEPTH -- initial size of the parser's stacks.  */
#ifndef	YYINITDEPTH
# define YYINITDEPTH 200
#endif

/* YYMAXDEPTH -- maximum size the stacks can grow to (effective only
   if the built-in stack extension method is used).

   Do not make this value too large; the results are undefined if
   YYSTACK_ALLOC_MAXIMUM < YYSTACK_BYTES (YYMAXDEPTH)
   evaluated with infinite-precision integer arithmetic.  */

#ifndef YYMAXDEPTH
# define YYMAXDEPTH 10000
#endif



#if YYERROR_VERBOSE

# ifndef yystrlen
#  if defined (__GLIBC__) && defined (_STRING_H)
#   define yystrlen strlen
#  else
/* Return the length of YYSTR.  */
static YYSIZE_T
#   if defined (__STDC__) || defined (__cplusplus)
yystrlen (const char *yystr)
#   else
yystrlen (yystr)
     const char *yystr;
#   endif
{
  const char *yys = yystr;

  while (*yys++ != '\0')
    continue;

  return yys - yystr - 1;
}
#  endif
# endif

# ifndef yystpcpy
#  if defined (__GLIBC__) && defined (_STRING_H) && defined (_GNU_SOURCE)
#   define yystpcpy stpcpy
#  else
/* Copy YYSRC to YYDEST, returning the address of the terminating '\0' in
   YYDEST.  */
static char *
#   if defined (__STDC__) || defined (__cplusplus)
yystpcpy (char *yydest, const char *yysrc)
#   else
yystpcpy (yydest, yysrc)
     char *yydest;
     const char *yysrc;
#   endif
{
  char *yyd = yydest;
  const char *yys = yysrc;

  while ((*yyd++ = *yys++) != '\0')
    continue;

  return yyd - 1;
}
#  endif
# endif

# ifndef yytnamerr
/* Copy to YYRES the contents of YYSTR after stripping away unnecessary
   quotes and backslashes, so that it's suitable for yyerror.  The
   heuristic is that double-quoting is unnecessary unless the string
   contains an apostrophe, a comma, or backslash (other than
   backslash-backslash).  YYSTR is taken from yytname.  If YYRES is
   null, do not copy; instead, return the length of what the result
   would have been.  */
static YYSIZE_T
yytnamerr (char *yyres, const char *yystr)
{
  if (*yystr == '"')
    {
      size_t yyn = 0;
      char const *yyp = yystr;

      for (;;)
	switch (*++yyp)
	  {
	  case '\'':
	  case ',':
	    goto do_not_strip_quotes;

	  case '\\':
	    if (*++yyp != '\\')
	      goto do_not_strip_quotes;
	    /* Fall through.  */
	  default:
	    if (yyres)
	      yyres[yyn] = *yyp;
	    yyn++;
	    break;

	  case '"':
	    if (yyres)
	      yyres[yyn] = '\0';
	    return yyn;
	  }
    do_not_strip_quotes: ;
    }

  if (! yyres)
    return yystrlen (yystr);

  return yystpcpy (yyres, yystr) - yyres;
}
# endif

#endif /* YYERROR_VERBOSE */



#if YYDEBUG
/*--------------------------------.
| Print this symbol on YYOUTPUT.  |
`--------------------------------*/

#if defined (__STDC__) || defined (__cplusplus)
static void
yysymprint (FILE *yyoutput, int yytype, YYSTYPE *yyvaluep)
#else
static void
yysymprint (yyoutput, yytype, yyvaluep)
    FILE *yyoutput;
    int yytype;
    YYSTYPE *yyvaluep;
#endif
{
  /* Pacify ``unused variable'' warnings.  */
  (void) yyvaluep;

  if (yytype < YYNTOKENS)
    YYFPRINTF (yyoutput, "token %s (", yytname[yytype]);
  else
    YYFPRINTF (yyoutput, "nterm %s (", yytname[yytype]);


# ifdef YYPRINT
  if (yytype < YYNTOKENS)
    YYPRINT (yyoutput, yytoknum[yytype], *yyvaluep);
# endif
  switch (yytype)
    {
      default:
        break;
    }
  YYFPRINTF (yyoutput, ")");
}

#endif /* ! YYDEBUG */
/*-----------------------------------------------.
| Release the memory associated to this symbol.  |
`-----------------------------------------------*/

#if defined (__STDC__) || defined (__cplusplus)
static void
yydestruct (const char *yymsg, int yytype, YYSTYPE *yyvaluep)
#else
static void
yydestruct (yymsg, yytype, yyvaluep)
    const char *yymsg;
    int yytype;
    YYSTYPE *yyvaluep;
#endif
{
  /* Pacify ``unused variable'' warnings.  */
  (void) yyvaluep;

  if (!yymsg)
    yymsg = "Deleting";
  YY_SYMBOL_PRINT (yymsg, yytype, yyvaluep, yylocationp);

  switch (yytype)
    {

      default:
        break;
    }
}


/* Prevent warnings from -Wmissing-prototypes.  */

#ifdef YYPARSE_PARAM
# if defined (__STDC__) || defined (__cplusplus)
int yyparse (void *YYPARSE_PARAM);
# else
int yyparse ();
# endif
#else /* ! YYPARSE_PARAM */
#if defined (__STDC__) || defined (__cplusplus)
int yyparse (void);
#else
int yyparse ();
#endif
#endif /* ! YYPARSE_PARAM */



/* The look-ahead symbol.  */
int yychar;

/* The semantic value of the look-ahead symbol.  */
YYSTYPE yylval;

/* Number of syntax errors so far.  */
int yynerrs;



/*----------.
| yyparse.  |
`----------*/

#ifdef YYPARSE_PARAM
# if defined (__STDC__) || defined (__cplusplus)
int yyparse (void *YYPARSE_PARAM)
# else
int yyparse (YYPARSE_PARAM)
  void *YYPARSE_PARAM;
# endif
#else /* ! YYPARSE_PARAM */
#if defined (__STDC__) || defined (__cplusplus)
int
yyparse (void)
#else
int
yyparse ()
    ;
#endif
#endif
{
  
  int yystate;
  int yyn;
  int yyresult;
  /* Number of tokens to shift before error messages enabled.  */
  int yyerrstatus;
  /* Look-ahead token as an internal (translated) token number.  */
  int yytoken = 0;

  /* Three stacks and their tools:
     `yyss': related to states,
     `yyvs': related to semantic values,
     `yyls': related to locations.

     Refer to the stacks thru separate pointers, to allow yyoverflow
     to reallocate them elsewhere.  */

  /* The state stack.  */
  short int yyssa[YYINITDEPTH];
  short int *yyss = yyssa;
  short int *yyssp;

  /* The semantic value stack.  */
  YYSTYPE yyvsa[YYINITDEPTH];
  YYSTYPE *yyvs = yyvsa;
  YYSTYPE *yyvsp;



#define YYPOPSTACK   (yyvsp--, yyssp--)

  YYSIZE_T yystacksize = YYINITDEPTH;

  /* The variables used to return semantic value and location from the
     action routines.  */
  YYSTYPE yyval;


  /* When reducing, the number of symbols on the RHS of the reduced
     rule.  */
  int yylen;

  YYDPRINTF ((stderr, "Starting parse\n"));

  yystate = 0;
  yyerrstatus = 0;
  yynerrs = 0;
  yychar = YYEMPTY;		/* Cause a token to be read.  */

  /* Initialize stack pointers.
     Waste one element of value and location stack
     so that they stay on the same level as the state stack.
     The wasted elements are never initialized.  */

  yyssp = yyss;
  yyvsp = yyvs;

  goto yysetstate;

/*------------------------------------------------------------.
| yynewstate -- Push a new state, which is found in yystate.  |
`------------------------------------------------------------*/
 yynewstate:
  /* In all cases, when you get here, the value and location stacks
     have just been pushed. so pushing a state here evens the stacks.
     */
  yyssp++;

 yysetstate:
  *yyssp = yystate;

  if (yyss + yystacksize - 1 <= yyssp)
    {
      /* Get the current used size of the three stacks, in elements.  */
      YYSIZE_T yysize = yyssp - yyss + 1;

#ifdef yyoverflow
      {
	/* Give user a chance to reallocate the stack. Use copies of
	   these so that the &'s don't force the real ones into
	   memory.  */
	YYSTYPE *yyvs1 = yyvs;
	short int *yyss1 = yyss;


	/* Each stack pointer address is followed by the size of the
	   data in use in that stack, in bytes.  This used to be a
	   conditional around just the two extra args, but that might
	   be undefined if yyoverflow is a macro.  */
	yyoverflow (YY_("memory exhausted"),
		    &yyss1, yysize * sizeof (*yyssp),
		    &yyvs1, yysize * sizeof (*yyvsp),

		    &yystacksize);

	yyss = yyss1;
	yyvs = yyvs1;
      }
#else /* no yyoverflow */
# ifndef YYSTACK_RELOCATE
      goto yyexhaustedlab;
# else
      /* Extend the stack our own way.  */
      if (YYMAXDEPTH <= yystacksize)
	goto yyexhaustedlab;
      yystacksize *= 2;
      if (YYMAXDEPTH < yystacksize)
	yystacksize = YYMAXDEPTH;

      {
	short int *yyss1 = yyss;
	union yyalloc *yyptr =
	  (union yyalloc *) YYSTACK_ALLOC (YYSTACK_BYTES (yystacksize));
	if (! yyptr)
	  goto yyexhaustedlab;
	YYSTACK_RELOCATE (yyss);
	YYSTACK_RELOCATE (yyvs);

#  undef YYSTACK_RELOCATE
	if (yyss1 != yyssa)
	  YYSTACK_FREE (yyss1);
      }
# endif
#endif /* no yyoverflow */

      yyssp = yyss + yysize - 1;
      yyvsp = yyvs + yysize - 1;


      YYDPRINTF ((stderr, "Stack size increased to %lu\n",
		  (unsigned long int) yystacksize));

      if (yyss + yystacksize - 1 <= yyssp)
	YYABORT;
    }

  YYDPRINTF ((stderr, "Entering state %d\n", yystate));

  goto yybackup;

/*-----------.
| yybackup.  |
`-----------*/
yybackup:

/* Do appropriate processing given the current state.  */
/* Read a look-ahead token if we need one and don't already have one.  */
/* yyresume: */

  /* First try to decide what to do without reference to look-ahead token.  */

  yyn = yypact[yystate];
  if (yyn == YYPACT_NINF)
    goto yydefault;

  /* Not known => get a look-ahead token if don't already have one.  */

  /* YYCHAR is either YYEMPTY or YYEOF or a valid look-ahead symbol.  */
  if (yychar == YYEMPTY)
    {
      YYDPRINTF ((stderr, "Reading a token: "));
      yychar = YYLEX;
    }

  if (yychar <= YYEOF)
    {
      yychar = yytoken = YYEOF;
      YYDPRINTF ((stderr, "Now at end of input.\n"));
    }
  else
    {
      yytoken = YYTRANSLATE (yychar);
      YY_SYMBOL_PRINT ("Next token is", yytoken, &yylval, &yylloc);
    }

  /* If the proper action on seeing token YYTOKEN is to reduce or to
     detect an error, take that action.  */
  yyn += yytoken;
  if (yyn < 0 || YYLAST < yyn || yycheck[yyn] != yytoken)
    goto yydefault;
  yyn = yytable[yyn];
  if (yyn <= 0)
    {
      if (yyn == 0 || yyn == YYTABLE_NINF)
	goto yyerrlab;
      yyn = -yyn;
      goto yyreduce;
    }

  if (yyn == YYFINAL)
    YYACCEPT;

  /* Shift the look-ahead token.  */
  YY_SYMBOL_PRINT ("Shifting", yytoken, &yylval, &yylloc);

  /* Discard the token being shifted unless it is eof.  */
  if (yychar != YYEOF)
    yychar = YYEMPTY;

  *++yyvsp = yylval;


  /* Count tokens shifted since error; after three, turn off error
     status.  */
  if (yyerrstatus)
    yyerrstatus--;

  yystate = yyn;
  goto yynewstate;


/*-----------------------------------------------------------.
| yydefault -- do the default action for the current state.  |
`-----------------------------------------------------------*/
yydefault:
  yyn = yydefact[yystate];
  if (yyn == 0)
    goto yyerrlab;
  goto yyreduce;


/*-----------------------------.
| yyreduce -- Do a reduction.  |
`-----------------------------*/
yyreduce:
  /* yyn is the number of a rule to reduce with.  */
  yylen = yyr2[yyn];

  /* If YYLEN is nonzero, implement the default value of the action:
     `$$ = $1'.

     Otherwise, the following line sets YYVAL to garbage.
     This behavior is undocumented and Bison
     users should not rely upon it.  Assigning to YYVAL
     unconditionally makes the parser a bit smaller, and it avoids a
     GCC warning that YYVAL may be used uninitialized.  */
  yyval = yyvsp[1-yylen];


  YY_REDUCE_PRINT (yyn);
  switch (yyn)
    {
        case 2:
#line 211 "grammar.y"
    {
	finish_parse((yyvsp[0].blk).b);
}
    break;

  case 4:
#line 216 "grammar.y"
    { (yyval.blk).q = qerr; }
    break;

  case 6:
#line 219 "grammar.y"
    { gen_and((yyvsp[-2].blk).b, (yyvsp[0].blk).b); (yyval.blk) = (yyvsp[0].blk); }
    break;

  case 7:
#line 220 "grammar.y"
    { gen_and((yyvsp[-2].blk).b, (yyvsp[0].blk).b); (yyval.blk) = (yyvsp[0].blk); }
    break;

  case 8:
#line 221 "grammar.y"
    { gen_or((yyvsp[-2].blk).b, (yyvsp[0].blk).b); (yyval.blk) = (yyvsp[0].blk); }
    break;

  case 9:
#line 222 "grammar.y"
    { gen_or((yyvsp[-2].blk).b, (yyvsp[0].blk).b); (yyval.blk) = (yyvsp[0].blk); }
    break;

  case 10:
#line 224 "grammar.y"
    { (yyval.blk) = (yyvsp[-1].blk); }
    break;

  case 11:
#line 226 "grammar.y"
    { (yyval.blk) = (yyvsp[-1].blk); }
    break;

  case 13:
#line 229 "grammar.y"
    { (yyval.blk).b = gen_ncode(NULL, (bpf_u_int32)(yyvsp[0].i),
						   (yyval.blk).q = (yyvsp[-1].blk).q); }
    break;

  case 14:
#line 231 "grammar.y"
    { (yyval.blk) = (yyvsp[-1].blk); }
    break;

  case 15:
#line 233 "grammar.y"
    { (yyval.blk).b = gen_scode((yyvsp[0].s), (yyval.blk).q = (yyvsp[-1].blk).q); }
    break;

  case 16:
#line 234 "grammar.y"
    { (yyval.blk).b = gen_mcode((yyvsp[-2].s), NULL, (yyvsp[0].i),
				    (yyval.blk).q = (yyvsp[-3].blk).q); }
    break;

  case 17:
#line 236 "grammar.y"
    { (yyval.blk).b = gen_mcode((yyvsp[-2].s), (yyvsp[0].s), 0,
				    (yyval.blk).q = (yyvsp[-3].blk).q); }
    break;

  case 18:
#line 238 "grammar.y"
    {
				  /* Decide how to parse HID based on proto */
				  (yyval.blk).q = (yyvsp[-1].blk).q;
				  (yyval.blk).b = gen_ncode((yyvsp[0].s), 0, (yyval.blk).q);
				}
    break;

  case 19:
#line 243 "grammar.y"
    {
#ifdef INET6
				  (yyval.blk).b = gen_mcode6((yyvsp[-2].s), NULL, (yyvsp[0].i),
				    (yyval.blk).q = (yyvsp[-3].blk).q);
#else
				  bpf_error("'ip6addr/prefixlen' not supported "
					"in this configuration");
#endif /*INET6*/
				}
    break;

  case 20:
#line 252 "grammar.y"
    {
#ifdef INET6
				  (yyval.blk).b = gen_mcode6((yyvsp[0].s), 0, 128,
				    (yyval.blk).q = (yyvsp[-1].blk).q);
#else
				  bpf_error("'ip6addr' not supported "
					"in this configuration");
#endif /*INET6*/
				}
    break;

  case 21:
#line 261 "grammar.y"
    { 
				  (yyval.blk).b = gen_ecode((yyvsp[0].e), (yyval.blk).q = (yyvsp[-1].blk).q);
				  /*
				   * $1 was allocated by "pcap_ether_aton()",
				   * so we must free it now that we're done
				   * with it.
				   */
				  free((yyvsp[0].e));
				}
    break;

  case 22:
#line 270 "grammar.y"
    {
				  (yyval.blk).b = gen_acode((yyvsp[0].e), (yyval.blk).q = (yyvsp[-1].blk).q);
				  /*
				   * $1 was allocated by "pcap_ether_aton()",
				   * so we must free it now that we're done
				   * with it.
				   */
				  free((yyvsp[0].e));
				}
    break;

  case 23:
#line 279 "grammar.y"
    { gen_not((yyvsp[0].blk).b); (yyval.blk) = (yyvsp[0].blk); }
    break;

  case 24:
#line 281 "grammar.y"
    { (yyval.blk) = (yyvsp[-1].blk); }
    break;

  case 25:
#line 283 "grammar.y"
    { (yyval.blk) = (yyvsp[-1].blk); }
    break;

  case 27:
#line 286 "grammar.y"
    { gen_and((yyvsp[-2].blk).b, (yyvsp[0].blk).b); (yyval.blk) = (yyvsp[0].blk); }
    break;

  case 28:
#line 287 "grammar.y"
    { gen_or((yyvsp[-2].blk).b, (yyvsp[0].blk).b); (yyval.blk) = (yyvsp[0].blk); }
    break;

  case 29:
#line 289 "grammar.y"
    { (yyval.blk).b = gen_ncode(NULL, (bpf_u_int32)(yyvsp[0].i),
						   (yyval.blk).q = (yyvsp[-1].blk).q); }
    break;

  case 32:
#line 294 "grammar.y"
    { gen_not((yyvsp[0].blk).b); (yyval.blk) = (yyvsp[0].blk); }
    break;

  case 33:
#line 296 "grammar.y"
    { QSET((yyval.blk).q, (yyvsp[-2].i), (yyvsp[-1].i), (yyvsp[0].i)); }
    break;

  case 34:
#line 297 "grammar.y"
    { QSET((yyval.blk).q, (yyvsp[-1].i), (yyvsp[0].i), Q_DEFAULT); }
    break;

  case 35:
#line 298 "grammar.y"
    { QSET((yyval.blk).q, (yyvsp[-1].i), Q_DEFAULT, (yyvsp[0].i)); }
    break;

  case 36:
#line 299 "grammar.y"
    { QSET((yyval.blk).q, (yyvsp[-1].i), Q_DEFAULT, Q_PROTO); }
    break;

  case 37:
#line 300 "grammar.y"
    { QSET((yyval.blk).q, (yyvsp[-1].i), Q_DEFAULT, Q_PROTOCHAIN); }
    break;

  case 38:
#line 301 "grammar.y"
    { QSET((yyval.blk).q, (yyvsp[-1].i), Q_DEFAULT, (yyvsp[0].i)); }
    break;

  case 39:
#line 303 "grammar.y"
    { (yyval.blk) = (yyvsp[0].blk); }
    break;

  case 40:
#line 304 "grammar.y"
    { (yyval.blk).b = (yyvsp[-1].blk).b; (yyval.blk).q = (yyvsp[-2].blk).q; }
    break;

  case 41:
#line 305 "grammar.y"
    { (yyval.blk).b = gen_proto_abbrev((yyvsp[0].i)); (yyval.blk).q = qerr; }
    break;

  case 42:
#line 306 "grammar.y"
    { (yyval.blk).b = gen_relation((yyvsp[-1].i), (yyvsp[-2].a), (yyvsp[0].a), 0);
				  (yyval.blk).q = qerr; }
    break;

  case 43:
#line 308 "grammar.y"
    { (yyval.blk).b = gen_relation((yyvsp[-1].i), (yyvsp[-2].a), (yyvsp[0].a), 1);
				  (yyval.blk).q = qerr; }
    break;

  case 44:
#line 310 "grammar.y"
    { (yyval.blk).b = (yyvsp[0].rblk); (yyval.blk).q = qerr; }
    break;

  case 45:
#line 311 "grammar.y"
    { (yyval.blk).b = gen_atmtype_abbrev((yyvsp[0].i)); (yyval.blk).q = qerr; }
    break;

  case 46:
#line 312 "grammar.y"
    { (yyval.blk).b = gen_atmmulti_abbrev((yyvsp[0].i)); (yyval.blk).q = qerr; }
    break;

  case 47:
#line 313 "grammar.y"
    { (yyval.blk).b = (yyvsp[0].blk).b; (yyval.blk).q = qerr; }
    break;

  case 48:
#line 314 "grammar.y"
    { (yyval.blk).b = gen_mtp2type_abbrev((yyvsp[0].i)); (yyval.blk).q = qerr; }
    break;

  case 49:
#line 315 "grammar.y"
    { (yyval.blk).b = (yyvsp[0].blk).b; (yyval.blk).q = qerr; }
    break;

  case 51:
#line 319 "grammar.y"
    { (yyval.i) = Q_DEFAULT; }
    break;

  case 52:
#line 322 "grammar.y"
    { (yyval.i) = Q_SRC; }
    break;

  case 53:
#line 323 "grammar.y"
    { (yyval.i) = Q_DST; }
    break;

  case 54:
#line 324 "grammar.y"
    { (yyval.i) = Q_OR; }
    break;

  case 55:
#line 325 "grammar.y"
    { (yyval.i) = Q_OR; }
    break;

  case 56:
#line 326 "grammar.y"
    { (yyval.i) = Q_AND; }
    break;

  case 57:
#line 327 "grammar.y"
    { (yyval.i) = Q_AND; }
    break;

  case 58:
#line 330 "grammar.y"
    { (yyval.i) = Q_HOST; }
    break;

  case 59:
#line 331 "grammar.y"
    { (yyval.i) = Q_NET; }
    break;

  case 60:
#line 332 "grammar.y"
    { (yyval.i) = Q_PORT; }
    break;

  case 61:
#line 333 "grammar.y"
    { (yyval.i) = Q_PORTRANGE; }
    break;

  case 62:
#line 336 "grammar.y"
    { (yyval.i) = Q_GATEWAY; }
    break;

  case 63:
#line 338 "grammar.y"
    { (yyval.i) = Q_LINK; }
    break;

  case 64:
#line 339 "grammar.y"
    { (yyval.i) = Q_IP; }
    break;

  case 65:
#line 340 "grammar.y"
    { (yyval.i) = Q_ARP; }
    break;

  case 66:
#line 341 "grammar.y"
    { (yyval.i) = Q_RARP; }
    break;

  case 67:
#line 342 "grammar.y"
    { (yyval.i) = Q_SCTP; }
    break;

  case 68:
#line 343 "grammar.y"
    { (yyval.i) = Q_TCP; }
    break;

  case 69:
#line 344 "grammar.y"
    { (yyval.i) = Q_UDP; }
    break;

  case 70:
#line 345 "grammar.y"
    { (yyval.i) = Q_ICMP; }
    break;

  case 71:
#line 346 "grammar.y"
    { (yyval.i) = Q_IGMP; }
    break;

  case 72:
#line 347 "grammar.y"
    { (yyval.i) = Q_IGRP; }
    break;

  case 73:
#line 348 "grammar.y"
    { (yyval.i) = Q_PIM; }
    break;

  case 74:
#line 349 "grammar.y"
    { (yyval.i) = Q_VRRP; }
    break;

  case 75:
#line 350 "grammar.y"
    { (yyval.i) = Q_ATALK; }
    break;

  case 76:
#line 351 "grammar.y"
    { (yyval.i) = Q_AARP; }
    break;

  case 77:
#line 352 "grammar.y"
    { (yyval.i) = Q_DECNET; }
    break;

  case 78:
#line 353 "grammar.y"
    { (yyval.i) = Q_LAT; }
    break;

  case 79:
#line 354 "grammar.y"
    { (yyval.i) = Q_SCA; }
    break;

  case 80:
#line 355 "grammar.y"
    { (yyval.i) = Q_MOPDL; }
    break;

  case 81:
#line 356 "grammar.y"
    { (yyval.i) = Q_MOPRC; }
    break;

  case 82:
#line 357 "grammar.y"
    { (yyval.i) = Q_IPV6; }
    break;

  case 83:
#line 358 "grammar.y"
    { (yyval.i) = Q_ICMPV6; }
    break;

  case 84:
#line 359 "grammar.y"
    { (yyval.i) = Q_AH; }
    break;

  case 85:
#line 360 "grammar.y"
    { (yyval.i) = Q_ESP; }
    break;

  case 86:
#line 361 "grammar.y"
    { (yyval.i) = Q_ISO; }
    break;

  case 87:
#line 362 "grammar.y"
    { (yyval.i) = Q_ESIS; }
    break;

  case 88:
#line 363 "grammar.y"
    { (yyval.i) = Q_ISIS; }
    break;

  case 89:
#line 364 "grammar.y"
    { (yyval.i) = Q_ISIS_L1; }
    break;

  case 90:
#line 365 "grammar.y"
    { (yyval.i) = Q_ISIS_L2; }
    break;

  case 91:
#line 366 "grammar.y"
    { (yyval.i) = Q_ISIS_IIH; }
    break;

  case 92:
#line 367 "grammar.y"
    { (yyval.i) = Q_ISIS_LSP; }
    break;

  case 93:
#line 368 "grammar.y"
    { (yyval.i) = Q_ISIS_SNP; }
    break;

  case 94:
#line 369 "grammar.y"
    { (yyval.i) = Q_ISIS_PSNP; }
    break;

  case 95:
#line 370 "grammar.y"
    { (yyval.i) = Q_ISIS_CSNP; }
    break;

  case 96:
#line 371 "grammar.y"
    { (yyval.i) = Q_CLNP; }
    break;

  case 97:
#line 372 "grammar.y"
    { (yyval.i) = Q_STP; }
    break;

  case 98:
#line 373 "grammar.y"
    { (yyval.i) = Q_IPX; }
    break;

  case 99:
#line 374 "grammar.y"
    { (yyval.i) = Q_NETBEUI; }
    break;

  case 100:
#line 375 "grammar.y"
    { (yyval.i) = Q_RADIO; }
    break;

  case 101:
#line 377 "grammar.y"
    { (yyval.rblk) = gen_broadcast((yyvsp[-1].i)); }
    break;

  case 102:
#line 378 "grammar.y"
    { (yyval.rblk) = gen_multicast((yyvsp[-1].i)); }
    break;

  case 103:
#line 379 "grammar.y"
    { (yyval.rblk) = gen_less((yyvsp[0].i)); }
    break;

  case 104:
#line 380 "grammar.y"
    { (yyval.rblk) = gen_greater((yyvsp[0].i)); }
    break;

  case 105:
#line 381 "grammar.y"
    { (yyval.rblk) = gen_byteop((yyvsp[-1].i), (yyvsp[-2].i), (yyvsp[0].i)); }
    break;

  case 106:
#line 382 "grammar.y"
    { (yyval.rblk) = gen_inbound(0); }
    break;

  case 107:
#line 383 "grammar.y"
    { (yyval.rblk) = gen_inbound(1); }
    break;

  case 108:
#line 384 "grammar.y"
    { (yyval.rblk) = gen_vlan((yyvsp[0].i)); }
    break;

  case 109:
#line 385 "grammar.y"
    { (yyval.rblk) = gen_vlan(-1); }
    break;

  case 110:
#line 386 "grammar.y"
    { (yyval.rblk) = gen_mpls((yyvsp[0].i)); }
    break;

  case 111:
#line 387 "grammar.y"
    { (yyval.rblk) = gen_mpls(-1); }
    break;

  case 112:
#line 388 "grammar.y"
    { (yyval.rblk) = gen_pppoed(); }
    break;

  case 113:
#line 389 "grammar.y"
    { (yyval.rblk) = gen_pppoes(); }
    break;

  case 114:
#line 390 "grammar.y"
    { (yyval.rblk) = (yyvsp[0].rblk); }
    break;

  case 115:
#line 393 "grammar.y"
    { (yyval.rblk) = gen_pf_ifname((yyvsp[0].s)); }
    break;

  case 116:
#line 394 "grammar.y"
    { (yyval.rblk) = gen_pf_ruleset((yyvsp[0].s)); }
    break;

  case 117:
#line 395 "grammar.y"
    { (yyval.rblk) = gen_pf_rnr((yyvsp[0].i)); }
    break;

  case 118:
#line 396 "grammar.y"
    { (yyval.rblk) = gen_pf_srnr((yyvsp[0].i)); }
    break;

  case 119:
#line 397 "grammar.y"
    { (yyval.rblk) = gen_pf_reason((yyvsp[0].i)); }
    break;

  case 120:
#line 398 "grammar.y"
    { (yyval.rblk) = gen_pf_action((yyvsp[0].i)); }
    break;

  case 121:
#line 401 "grammar.y"
    { (yyval.i) = (yyvsp[0].i); }
    break;

  case 122:
#line 402 "grammar.y"
    { (yyval.i) = pfreason_to_num((yyvsp[0].s)); }
    break;

  case 123:
#line 405 "grammar.y"
    { (yyval.i) = pfaction_to_num((yyvsp[0].s)); }
    break;

  case 124:
#line 408 "grammar.y"
    { (yyval.i) = BPF_JGT; }
    break;

  case 125:
#line 409 "grammar.y"
    { (yyval.i) = BPF_JGE; }
    break;

  case 126:
#line 410 "grammar.y"
    { (yyval.i) = BPF_JEQ; }
    break;

  case 127:
#line 412 "grammar.y"
    { (yyval.i) = BPF_JGT; }
    break;

  case 128:
#line 413 "grammar.y"
    { (yyval.i) = BPF_JGE; }
    break;

  case 129:
#line 414 "grammar.y"
    { (yyval.i) = BPF_JEQ; }
    break;

  case 130:
#line 416 "grammar.y"
    { (yyval.a) = gen_loadi((yyvsp[0].i)); }
    break;

  case 132:
#line 419 "grammar.y"
    { (yyval.a) = gen_load((yyvsp[-3].i), (yyvsp[-1].a), 1); }
    break;

  case 133:
#line 420 "grammar.y"
    { (yyval.a) = gen_load((yyvsp[-5].i), (yyvsp[-3].a), (yyvsp[-1].i)); }
    break;

  case 134:
#line 421 "grammar.y"
    { (yyval.a) = gen_arth(BPF_ADD, (yyvsp[-2].a), (yyvsp[0].a)); }
    break;

  case 135:
#line 422 "grammar.y"
    { (yyval.a) = gen_arth(BPF_SUB, (yyvsp[-2].a), (yyvsp[0].a)); }
    break;

  case 136:
#line 423 "grammar.y"
    { (yyval.a) = gen_arth(BPF_MUL, (yyvsp[-2].a), (yyvsp[0].a)); }
    break;

  case 137:
#line 424 "grammar.y"
    { (yyval.a) = gen_arth(BPF_DIV, (yyvsp[-2].a), (yyvsp[0].a)); }
    break;

  case 138:
#line 425 "grammar.y"
    { (yyval.a) = gen_arth(BPF_AND, (yyvsp[-2].a), (yyvsp[0].a)); }
    break;

  case 139:
#line 426 "grammar.y"
    { (yyval.a) = gen_arth(BPF_OR, (yyvsp[-2].a), (yyvsp[0].a)); }
    break;

  case 140:
#line 427 "grammar.y"
    { (yyval.a) = gen_arth(BPF_LSH, (yyvsp[-2].a), (yyvsp[0].a)); }
    break;

  case 141:
#line 428 "grammar.y"
    { (yyval.a) = gen_arth(BPF_RSH, (yyvsp[-2].a), (yyvsp[0].a)); }
    break;

  case 142:
#line 429 "grammar.y"
    { (yyval.a) = gen_neg((yyvsp[0].a)); }
    break;

  case 143:
#line 430 "grammar.y"
    { (yyval.a) = (yyvsp[-1].a); }
    break;

  case 144:
#line 431 "grammar.y"
    { (yyval.a) = gen_loadlen(); }
    break;

  case 145:
#line 433 "grammar.y"
    { (yyval.i) = '&'; }
    break;

  case 146:
#line 434 "grammar.y"
    { (yyval.i) = '|'; }
    break;

  case 147:
#line 435 "grammar.y"
    { (yyval.i) = '<'; }
    break;

  case 148:
#line 436 "grammar.y"
    { (yyval.i) = '>'; }
    break;

  case 149:
#line 437 "grammar.y"
    { (yyval.i) = '='; }
    break;

  case 151:
#line 440 "grammar.y"
    { (yyval.i) = (yyvsp[-1].i); }
    break;

  case 152:
#line 442 "grammar.y"
    { (yyval.i) = A_LANE; }
    break;

  case 153:
#line 443 "grammar.y"
    { (yyval.i) = A_LLC; }
    break;

  case 154:
#line 444 "grammar.y"
    { (yyval.i) = A_METAC;	}
    break;

  case 155:
#line 445 "grammar.y"
    { (yyval.i) = A_BCC; }
    break;

  case 156:
#line 446 "grammar.y"
    { (yyval.i) = A_OAMF4EC; }
    break;

  case 157:
#line 447 "grammar.y"
    { (yyval.i) = A_OAMF4SC; }
    break;

  case 158:
#line 448 "grammar.y"
    { (yyval.i) = A_SC; }
    break;

  case 159:
#line 449 "grammar.y"
    { (yyval.i) = A_ILMIC; }
    break;

  case 160:
#line 451 "grammar.y"
    { (yyval.i) = A_OAM; }
    break;

  case 161:
#line 452 "grammar.y"
    { (yyval.i) = A_OAMF4; }
    break;

  case 162:
#line 453 "grammar.y"
    { (yyval.i) = A_CONNECTMSG; }
    break;

  case 163:
#line 454 "grammar.y"
    { (yyval.i) = A_METACONNECT; }
    break;

  case 164:
#line 457 "grammar.y"
    { (yyval.blk).atmfieldtype = A_VPI; }
    break;

  case 165:
#line 458 "grammar.y"
    { (yyval.blk).atmfieldtype = A_VCI; }
    break;

  case 167:
#line 461 "grammar.y"
    { (yyval.blk).b = gen_atmfield_code((yyvsp[-2].blk).atmfieldtype, (bpf_int32)(yyvsp[0].i), (bpf_u_int32)(yyvsp[-1].i), 0); }
    break;

  case 168:
#line 462 "grammar.y"
    { (yyval.blk).b = gen_atmfield_code((yyvsp[-2].blk).atmfieldtype, (bpf_int32)(yyvsp[0].i), (bpf_u_int32)(yyvsp[-1].i), 1); }
    break;

  case 169:
#line 463 "grammar.y"
    { (yyval.blk).b = (yyvsp[-1].blk).b; (yyval.blk).q = qerr; }
    break;

  case 170:
#line 465 "grammar.y"
    {
	(yyval.blk).atmfieldtype = (yyvsp[-1].blk).atmfieldtype;
	if ((yyval.blk).atmfieldtype == A_VPI ||
	    (yyval.blk).atmfieldtype == A_VCI)
		(yyval.blk).b = gen_atmfield_code((yyval.blk).atmfieldtype, (bpf_int32) (yyvsp[0].i), BPF_JEQ, 0);
	}
    break;

  case 172:
#line 473 "grammar.y"
    { gen_or((yyvsp[-2].blk).b, (yyvsp[0].blk).b); (yyval.blk) = (yyvsp[0].blk); }
    break;

  case 173:
#line 476 "grammar.y"
    { (yyval.i) = M_FISU; }
    break;

  case 174:
#line 477 "grammar.y"
    { (yyval.i) = M_LSSU; }
    break;

  case 175:
#line 478 "grammar.y"
    { (yyval.i) = M_MSU; }
    break;

  case 176:
#line 481 "grammar.y"
    { (yyval.blk).mtp3fieldtype = M_SIO; }
    break;

  case 177:
#line 482 "grammar.y"
    { (yyval.blk).mtp3fieldtype = M_OPC; }
    break;

  case 178:
#line 483 "grammar.y"
    { (yyval.blk).mtp3fieldtype = M_DPC; }
    break;

  case 179:
#line 484 "grammar.y"
    { (yyval.blk).mtp3fieldtype = M_SLS; }
    break;

  case 181:
#line 487 "grammar.y"
    { (yyval.blk).b = gen_mtp3field_code((yyvsp[-2].blk).mtp3fieldtype, (u_int)(yyvsp[0].i), (u_int)(yyvsp[-1].i), 0); }
    break;

  case 182:
#line 488 "grammar.y"
    { (yyval.blk).b = gen_mtp3field_code((yyvsp[-2].blk).mtp3fieldtype, (u_int)(yyvsp[0].i), (u_int)(yyvsp[-1].i), 1); }
    break;

  case 183:
#line 489 "grammar.y"
    { (yyval.blk).b = (yyvsp[-1].blk).b; (yyval.blk).q = qerr; }
    break;

  case 184:
#line 491 "grammar.y"
    {
	(yyval.blk).mtp3fieldtype = (yyvsp[-1].blk).mtp3fieldtype;
	if ((yyval.blk).mtp3fieldtype == M_SIO ||
	    (yyval.blk).mtp3fieldtype == M_OPC ||
	    (yyval.blk).mtp3fieldtype == M_DPC ||
	    (yyval.blk).mtp3fieldtype == M_SLS )
		(yyval.blk).b = gen_mtp3field_code((yyval.blk).mtp3fieldtype, (u_int) (yyvsp[0].i), BPF_JEQ, 0);
	}
    break;

  case 186:
#line 501 "grammar.y"
    { gen_or((yyvsp[-2].blk).b, (yyvsp[0].blk).b); (yyval.blk) = (yyvsp[0].blk); }
    break;


      default: break;
    }

/* Line 1126 of yacc.c.  */
#line 2714 "y.tab.c"

  yyvsp -= yylen;
  yyssp -= yylen;


  YY_STACK_PRINT (yyss, yyssp);

  *++yyvsp = yyval;


  /* Now `shift' the result of the reduction.  Determine what state
     that goes to, based on the state we popped back to and the rule
     number reduced by.  */

  yyn = yyr1[yyn];

  yystate = yypgoto[yyn - YYNTOKENS] + *yyssp;
  if (0 <= yystate && yystate <= YYLAST && yycheck[yystate] == *yyssp)
    yystate = yytable[yystate];
  else
    yystate = yydefgoto[yyn - YYNTOKENS];

  goto yynewstate;


/*------------------------------------.
| yyerrlab -- here on detecting error |
`------------------------------------*/
yyerrlab:
  /* If not already recovering from an error, report this error.  */
  if (!yyerrstatus)
    {
      ++yynerrs;
#if YYERROR_VERBOSE
      yyn = yypact[yystate];

      if (YYPACT_NINF < yyn && yyn < YYLAST)
	{
	  int yytype = YYTRANSLATE (yychar);
	  YYSIZE_T yysize0 = yytnamerr (0, yytname[yytype]);
	  YYSIZE_T yysize = yysize0;
	  YYSIZE_T yysize1;
	  int yysize_overflow = 0;
	  char *yymsg = 0;
#	  define YYERROR_VERBOSE_ARGS_MAXIMUM 5
	  char const *yyarg[YYERROR_VERBOSE_ARGS_MAXIMUM];
	  int yyx;

#if 0
	  /* This is so xgettext sees the translatable formats that are
	     constructed on the fly.  */
	  YY_("syntax error, unexpected %s");
	  YY_("syntax error, unexpected %s, expecting %s");
	  YY_("syntax error, unexpected %s, expecting %s or %s");
	  YY_("syntax error, unexpected %s, expecting %s or %s or %s");
	  YY_("syntax error, unexpected %s, expecting %s or %s or %s or %s");
#endif
	  char *yyfmt;
	  char const *yyf;
	  static char const yyunexpected[] = "syntax error, unexpected %s";
	  static char const yyexpecting[] = ", expecting %s";
	  static char const yyor[] = " or %s";
	  char yyformat[sizeof yyunexpected
			+ sizeof yyexpecting - 1
			+ ((YYERROR_VERBOSE_ARGS_MAXIMUM - 2)
			   * (sizeof yyor - 1))];
	  char const *yyprefix = yyexpecting;

	  /* Start YYX at -YYN if negative to avoid negative indexes in
	     YYCHECK.  */
	  int yyxbegin = yyn < 0 ? -yyn : 0;

	  /* Stay within bounds of both yycheck and yytname.  */
	  int yychecklim = YYLAST - yyn;
	  int yyxend = yychecklim < YYNTOKENS ? yychecklim : YYNTOKENS;
	  int yycount = 1;

	  yyarg[0] = yytname[yytype];
	  yyfmt = yystpcpy (yyformat, yyunexpected);

	  for (yyx = yyxbegin; yyx < yyxend; ++yyx)
	    if (yycheck[yyx + yyn] == yyx && yyx != YYTERROR)
	      {
		if (yycount == YYERROR_VERBOSE_ARGS_MAXIMUM)
		  {
		    yycount = 1;
		    yysize = yysize0;
		    yyformat[sizeof yyunexpected - 1] = '\0';
		    break;
		  }
		yyarg[yycount++] = yytname[yyx];
		yysize1 = yysize + yytnamerr (0, yytname[yyx]);
		yysize_overflow |= yysize1 < yysize;
		yysize = yysize1;
		yyfmt = yystpcpy (yyfmt, yyprefix);
		yyprefix = yyor;
	      }

	  yyf = YY_(yyformat);
	  yysize1 = yysize + yystrlen (yyf);
	  yysize_overflow |= yysize1 < yysize;
	  yysize = yysize1;

	  if (!yysize_overflow && yysize <= YYSTACK_ALLOC_MAXIMUM)
	    yymsg = (char *) YYSTACK_ALLOC (yysize);
	  if (yymsg)
	    {
	      /* Avoid sprintf, as that infringes on the user's name space.
		 Don't have undefined behavior even if the translation
		 produced a string with the wrong number of "%s"s.  */
	      char *yyp = yymsg;
	      int yyi = 0;
	      while ((*yyp = *yyf))
		{
		  if (*yyp == '%' && yyf[1] == 's' && yyi < yycount)
		    {
		      yyp += yytnamerr (yyp, yyarg[yyi++]);
		      yyf += 2;
		    }
		  else
		    {
		      yyp++;
		      yyf++;
		    }
		}
	      yyerror (yymsg);
	      YYSTACK_FREE (yymsg);
	    }
	  else
	    {
	      yyerror (YY_("syntax error"));
	      goto yyexhaustedlab;
	    }
	}
      else
#endif /* YYERROR_VERBOSE */
	yyerror (YY_("syntax error"));
    }



  if (yyerrstatus == 3)
    {
      /* If just tried and failed to reuse look-ahead token after an
	 error, discard it.  */

      if (yychar <= YYEOF)
        {
	  /* Return failure if at end of input.  */
	  if (yychar == YYEOF)
	    YYABORT;
        }
      else
	{
	  yydestruct ("Error: discarding", yytoken, &yylval);
	  yychar = YYEMPTY;
	}
    }

  /* Else will try to reuse look-ahead token after shifting the error
     token.  */
  goto yyerrlab1;


/*---------------------------------------------------.
| yyerrorlab -- error raised explicitly by YYERROR.  |
`---------------------------------------------------*/
yyerrorlab:

  /* Pacify compilers like GCC when the user code never invokes
     YYERROR and the label yyerrorlab therefore never appears in user
     code.  */
  if (0)
     goto yyerrorlab;

yyvsp -= yylen;
  yyssp -= yylen;
  yystate = *yyssp;
  goto yyerrlab1;


/*-------------------------------------------------------------.
| yyerrlab1 -- common code for both syntax error and YYERROR.  |
`-------------------------------------------------------------*/
yyerrlab1:
  yyerrstatus = 3;	/* Each real token shifted decrements this.  */

  for (;;)
    {
      yyn = yypact[yystate];
      if (yyn != YYPACT_NINF)
	{
	  yyn += YYTERROR;
	  if (0 <= yyn && yyn <= YYLAST && yycheck[yyn] == YYTERROR)
	    {
	      yyn = yytable[yyn];
	      if (0 < yyn)
		break;
	    }
	}

      /* Pop the current state because it cannot handle the error token.  */
      if (yyssp == yyss)
	YYABORT;


      yydestruct ("Error: popping", yystos[yystate], yyvsp);
      YYPOPSTACK;
      yystate = *yyssp;
      YY_STACK_PRINT (yyss, yyssp);
    }

  if (yyn == YYFINAL)
    YYACCEPT;

  *++yyvsp = yylval;


  /* Shift the error token. */
  YY_SYMBOL_PRINT ("Shifting", yystos[yyn], yyvsp, yylsp);

  yystate = yyn;
  goto yynewstate;


/*-------------------------------------.
| yyacceptlab -- YYACCEPT comes here.  |
`-------------------------------------*/
yyacceptlab:
  yyresult = 0;
  goto yyreturn;

/*-----------------------------------.
| yyabortlab -- YYABORT comes here.  |
`-----------------------------------*/
yyabortlab:
  yyresult = 1;
  goto yyreturn;

#ifndef yyoverflow
/*-------------------------------------------------.
| yyexhaustedlab -- memory exhaustion comes here.  |
`-------------------------------------------------*/
yyexhaustedlab:
  yyerror (YY_("memory exhausted"));
  yyresult = 2;
  /* Fall through.  */
#endif

yyreturn:
  if (yychar != YYEOF && yychar != YYEMPTY)
     yydestruct ("Cleanup: discarding lookahead",
		 yytoken, &yylval);
  while (yyssp != yyss)
    {
      yydestruct ("Cleanup: popping",
		  yystos[*yyssp], yyvsp);
      YYPOPSTACK;
    }
#ifndef yyoverflow
  if (yyss != yyssa)
    YYSTACK_FREE (yyss);
#endif
  return yyresult;
}


#line 503 "grammar.y"


