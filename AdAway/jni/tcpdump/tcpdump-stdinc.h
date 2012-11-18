/*
 * Copyright (c) 2002 - 2003
 * NetGroup, Politecnico di Torino (Italy)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Politecnico di Torino nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 * @(#) $Header: /tcpdump/master/tcpdump/tcpdump-stdinc.h,v 1.12.2.5 2006/06/23 02:07:27 hannes Exp $ (LBL)
 */

/*
 * Include the appropriate OS header files on Windows and various flavors
 * of UNIX, and also define some additional items and include various
 * non-OS header files on Windows, and; this isolates most of the platform
 * differences to this one file.
 */

#ifndef tcpdump_stdinc_h
#define tcpdump_stdinc_h

#ifdef WIN32

#include <stdio.h>
#include <winsock2.h>
#include <Ws2tcpip.h>
#include "bittypes.h"
#include <ctype.h>
#include <time.h>
#include <io.h>
#include <fcntl.h>
#include <sys/types.h>
#include <net/netdb.h>  /* in wpcap's Win32/include */

#if !defined(__MINGW32__) && !defined(__WATCOMC__)
#undef toascii
#define isascii __isascii
#define toascii __toascii
#define stat _stat
#define open _open
#define fstat _fstat
#define read _read
#define close _close
#define O_RDONLY _O_RDONLY

typedef short ino_t;
#endif /* __MINGW32__ */

#ifdef __MINGW32__
#include <stdint.h>
#endif

/* Protos for missing/x.c functions (ideally <missing/addrinfo.h>
 * should be used, but it clashes with <ws2tcpip.h>).
 */
extern const char *inet_ntop (int, const void *, char *, size_t);
extern int inet_pton (int, const char *, void *);
extern int inet_aton (const char *cp, struct in_addr *addr);

#ifndef INET6_ADDRSTRLEN
#define INET6_ADDRSTRLEN 46
#endif

#ifndef toascii
#define toascii(c) ((c) & 0x7f)
#endif

#ifndef caddr_t
typedef char* caddr_t;
#endif /* caddr_t */

#define MAXHOSTNAMELEN	64
#define	NI_MAXHOST	1025
#define snprintf _snprintf
#define vsnprintf _vsnprintf
#define RETSIGTYPE void

#else /* WIN32 */

#include <ctype.h>
#include <unistd.h>
#include <netdb.h>
#if HAVE_INTTYPES_H
#include <inttypes.h>
#else
#if HAVE_STDINT_H
#include <stdint.h>
#endif
#endif
#ifdef HAVE_SYS_BITYPES_H
#include <sys/bitypes.h>
#endif
#include <sys/param.h>
#include <sys/types.h>			/* concession to AIX */
#include <sys/time.h>
#include <sys/socket.h>
#include <netinet/in.h>

#ifdef TIME_WITH_SYS_TIME
#include <time.h>
#endif

#include <arpa/inet.h>

#endif /* WIN32 */

#ifdef INET6
#include "ip6.h"
#endif

#if defined(WIN32) || defined(MSDOS)
  #define FOPEN_READ_TXT   "rt"
  #define FOPEN_READ_BIN   "rb"
  #define FOPEN_WRITE_TXT  "wt"
  #define FOPEN_WRITE_BIN  "wb"
#else
  #define FOPEN_READ_TXT   "r"
  #define FOPEN_READ_BIN   FOPEN_READ_TXT
  #define FOPEN_WRITE_TXT  "w"
  #define FOPEN_WRITE_BIN  FOPEN_WRITE_TXT
#endif

#if defined(__GNUC__) && defined(__i386__) && !defined(__ntohl)
  #undef ntohl
  #undef ntohs
  #undef htonl
  #undef htons

  extern __inline__ unsigned long __ntohl (unsigned long x);
  extern __inline__ unsigned short __ntohs (unsigned short x);

  #define ntohl(x)  __ntohl(x)
  #define ntohs(x)  __ntohs(x)
  #define htonl(x)  __ntohl(x)
  #define htons(x)  __ntohs(x)

  extern __inline__ unsigned long __ntohl (unsigned long x)
  {
    __asm__ ("xchgb %b0, %h0\n\t"   /* swap lower bytes  */
             "rorl  $16, %0\n\t"    /* swap words        */
             "xchgb %b0, %h0"       /* swap higher bytes */
            : "=q" (x) : "0" (x));
    return (x);
  }

  extern __inline__ unsigned short __ntohs (unsigned short x)
  {
    __asm__ ("xchgb %b0, %h0"       /* swap bytes */
            : "=q" (x) : "0" (x));
    return (x);
  }
#endif

#ifndef INET_ADDRSTRLEN
#define INET_ADDRSTRLEN 16
#endif

#ifndef TRUE
#define TRUE 1
#endif

#ifndef FALSE
#define FALSE 0
#endif

#endif /* tcpdump_stdinc_h */
