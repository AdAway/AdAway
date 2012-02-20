/* config.h.  Generated from config.h.in by configure.  */
/* config.h.in.  Generated from configure.in by autoheader.  */
/* Long story short: aclocal.m4 depends on autoconf 2.13
 * implementation details wrt "const"; newer versions
 * have different implementation details so for now we
 * put "const" here.  This may cause duplicate definitions
 * in config.h but that should be OK since they're the same.
 */
/* #undef const */

/* Enable optimizer debugging */
/* #undef BDEBUG */

/* define if you have a cloning BPF device */
/* #undef HAVE_CLONING_BPF */

/* define if you have the DAG API */
/* #undef HAVE_DAG_API */

/* define if you have dag_get_erf_types() */
/* #undef HAVE_DAG_GET_ERF_TYPES */

/* define if you have streams capable DAG API */
/* #undef HAVE_DAG_STREAMS_API */

/* Define to 1 if you have the declaration of `ether_hostton', and to 0 if you
   don't. */
#define HAVE_DECL_ETHER_HOSTTON 0

/* define if you have a /dev/dlpi */
/* #undef HAVE_DEV_DLPI */

/* Define to 1 if you have the `ether_hostton' function. */
/* #undef HAVE_ETHER_HOSTTON */

/* on HP-UX 10.20 or later */
/* #undef HAVE_HPUX10_20_OR_LATER */

/* on HP-UX 9.x */
/* #undef HAVE_HPUX9 */

/* if ppa_info_t_dl_module_id exists */
/* #undef HAVE_HP_PPA_INFO_T_DL_MODULE_ID_1 */

/* Define to 1 if you have the <inttypes.h> header file. */
#define HAVE_INTTYPES_H 1

/* Define to 1 if you have the <limits.h> header file. */
#define HAVE_LIMITS_H 1

/* Define to 1 if you have the <memory.h> header file. */
#define HAVE_MEMORY_H 1

/* Define to 1 if you have the <netinet/ether.h> header file. */
#define HAVE_NETINET_ETHER_H 1

/* Define to 1 if you have the <netinet/if_ether.h> header file. */
#define HAVE_NETINET_IF_ETHER_H 1

/* Define to 1 if you have the <net/pfvar.h> header file. */
/* #undef HAVE_NET_PFVAR_H */

/* if there's an os_proto.h */
/* #undef HAVE_OS_PROTO_H */

/* Define to 1 if you have the <paths.h> header file. */
#define HAVE_PATHS_H 1

/* define if you have a /proc/net/dev */
#define HAVE_PROC_NET_DEV 1

/* define if you have a Septel API */
/* #undef HAVE_SEPTEL_API */

/* Define to 1 if you have the `snprintf' function. */
#define HAVE_SNPRINTF 1

/* if struct sockaddr has sa_len */
/* #undef HAVE_SOCKADDR_SA_LEN */

/* if struct sockaddr_storage exists */
#define HAVE_SOCKADDR_STORAGE 1

/* On solaris */
/* #undef HAVE_SOLARIS */

/* Define to 1 if you have the <stdint.h> header file. */
#define HAVE_STDINT_H 1

/* Define to 1 if you have the <stdlib.h> header file. */
#define HAVE_STDLIB_H 1

/* Define to 1 if you have the `strerror' function. */
#define HAVE_STRERROR 1

/* Define to 1 if you have the <strings.h> header file. */
#define HAVE_STRINGS_H 1

/* Define to 1 if you have the <string.h> header file. */
#define HAVE_STRING_H 1

/* Define to 1 if you have the `strlcpy' function. */
/* #undef HAVE_STRLCPY */

/* Define to 1 if the system has the type `struct ether_addr'. */
/* #undef HAVE_STRUCT_ETHER_ADDR */

/* Define to 1 if you have the <sys/bufmod.h> header file. */
/* #undef HAVE_SYS_BUFMOD_H */

/* Define to 1 if you have the <sys/dlpi_ext.h> header file. */
/* #undef HAVE_SYS_DLPI_EXT_H */

/* Define to 1 if you have the <sys/ioccom.h> header file. */
/* #undef HAVE_SYS_IOCCOM_H */

/* Define to 1 if you have the <sys/sockio.h> header file. */
/* #undef HAVE_SYS_SOCKIO_H */

/* Define to 1 if you have the <sys/stat.h> header file. */
#define HAVE_SYS_STAT_H 1

/* Define to 1 if you have the <sys/types.h> header file. */
#define HAVE_SYS_TYPES_H 1

/* if if_packet.h has tpacket_stats defined */
#define HAVE_TPACKET_STATS 1

/* Define to 1 if you have the <unistd.h> header file. */
#define HAVE_UNISTD_H 1

/* define if version.h is generated in the build procedure */
#define HAVE_VERSION_H 1

/* Define to 1 if you have the `vsnprintf' function. */
#define HAVE_VSNPRINTF 1

/* define if your compiler has __attribute__ */
#define HAVE___ATTRIBUTE__ 1

/* IPv6 */
#define INET6

/* if unaligned access fails */
/* #undef LBL_ALIGN */

/* Define to 1 if netinet/ether.h declares `ether_hostton' */
/* #undef NETINET_ETHER_H_DECLARES_ETHER_HOSTTON */

/* Define to 1 if netinet/if_ether.h declares `ether_hostton' */
/* #undef NETINET_IF_ETHER_H_DECLARES_ETHER_HOSTTON */
#define NETINET_IF_ETHER_H_DECLARES_ETHER_HOSTTON 1

/* do not use protochain */
/* #undef NO_PROTOCHAIN */

/* Define to the address where bug reports for this package should be sent. */
#define PACKAGE_BUGREPORT ""

/* Define to the full name of this package. */
#define PACKAGE_NAME ""

/* Define to the full name and version of this package. */
#define PACKAGE_STRING ""

/* Define to the one symbol short name of this package. */
#define PACKAGE_TARNAME ""

/* Define to the version of this package. */
#define PACKAGE_VERSION ""

/* /dev/dlpi directory */
/* #undef PCAP_DEV_PREFIX */

/* Define to 1 if you have the ANSI C header files. */
#define STDC_HEADERS 1

/* Enable parser debugging */
/* #undef YYDEBUG */

/* needed on HP-UX */
/* #undef _HPUX_SOURCE */

/* define on AIX to get certain functions */
/* #undef _SUN */

/* Define as token for inline if inlining supported */
#define inline inline

/* on sinix */
/* #undef sinix */

/* if we have u_int16_t */
/* #undef u_int16_t */

/* if we have u_int32_t */
/* #undef u_int32_t */

/* if we have u_int8_t */
/* #undef u_int8_t */
