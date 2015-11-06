/*
 * Copyright (C) 1999 WIDE Project.
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
#ifndef _BITTYPES_H
#define _BITTYPES_H

#ifndef HAVE_U_INT8_T
typedef unsigned char u_int8_t;
typedef signed char int8_t;
#endif /* HAVE_U_INT8_T */

#ifndef HAVE_U_INT16_T
typedef unsigned short u_int16_t;
typedef signed short int16_t;
#endif /* HAVE_U_INT16_T */

#ifndef HAVE_U_INT32_T
typedef unsigned int u_int32_t;
typedef signed int int32_t;
#endif /* HAVE_U_INT32_T */

#ifndef HAVE_U_INT64_T
#ifdef _MSC_EXTENSIONS
typedef unsigned _int64 u_int64_t;
typedef _int64 int64_t;
#else /* _MSC_EXTENSIONS */
typedef unsigned long long u_int64_t;
typedef long long int64_t;
#endif /* _MSC_EXTENSIONS */
#endif /* HAVE_U_INT64_T */

#ifndef PRId64
#ifdef _MSC_EXTENSIONS
#define PRId64	"I64d"
#else /* _MSC_EXTENSIONS */
#define PRId64	"lld"
#endif /* _MSC_EXTENSIONS */
#endif /* PRId64 */

#ifndef PRIo64
#ifdef _MSC_EXTENSIONS
#define PRIo64	"I64o"
#else /* _MSC_EXTENSIONS */
#define PRIo64	"llo"
#endif /* _MSC_EXTENSIONS */
#endif /* PRIo64 */

#ifndef PRIx64
#ifdef _MSC_EXTENSIONS
#define PRIx64	"I64x"
#else /* _MSC_EXTENSIONS */
#define PRIx64	"llx"
#endif /* _MSC_EXTENSIONS */
#endif /* PRIx64 */

#ifndef PRIu64
#ifdef _MSC_EXTENSIONS
#define PRIu64	"I64u"
#else /* _MSC_EXTENSIONS */
#define PRIu64	"llu"
#endif /* _MSC_EXTENSIONS */
#endif /* PRIu64 */

#endif /* _BITTYPES_H */
