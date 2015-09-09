/*
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
 * tcpdump/Win32 functions for reading and parsing system's Ethernet
 * address file:
 *    '%SystemRoot%/drivers/etc/ethers'  (Win-NT+)
 * or '%Windir%/etc/ethers'              (Win-9x/ME)
 *
 * G. Vanem <gvanem@yahoo.no> 2012.
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include "ether.h"
#include "interface.h"
#include "addrtoname.h"

typedef struct ether_addr {
        unsigned char octet[ETHER_ADDR_LEN];
      } ether_address;

typedef struct ether_entry {
        ether_address      eth_addr;  /* MAC address */
        char               *name;     /* name of MAC-address */
        struct ether_entry *next;
      } ether_entry;

static struct ether_entry *eth0 = NULL;

/*
 * The reason to avoid using 'pcap_next_etherent()' in addrtoname.c
 * are several:
 *   1) wpcap.dll and 'pcap_next_etherent()' could have been built in
 *      debug-mode (-MDd) or release-mode (-MD) and tcpdump in
 *      the opposite model.
 *   2) If this is built by MSVC, wpcap.dll could have been built by
 *      MingW. It has no debug-model.
 *   3) It may not have been exported from wpcap.dll (present in wpcap.def).
 *
 * So we shoe-horn the building of tcpdump with '-DUSE_ETHER_NTOHOST' to
 * make 'init_etherarray()' call the below 'ether_ntohost()' instead.
 */
#if !defined(USE_ETHER_NTOHOST)
#error "'-DUSE_ETHER_NTOHOST' must be set"
#endif

/*
 * Return TRUE if running under Win-95/98/ME.
 */
static BOOL is_win9x (void)
{
  OSVERSIONINFO ovi;
  DWORD os_ver = GetVersion();
  DWORD major_ver = LOBYTE (LOWORD(os_ver));

  return (os_ver >= 0x80000000 && major_ver >= 4);
}

/*
 * Return path to "%SystemRoot%/drivers/etc/<file>"  (Win-NT+)
 *          or to "%Windir%/etc/<file>"              (Win-9x/ME)
 */
const char *etc_path (const char *file)
{
  BOOL win9x = is_win9x();
  const char *env = win9x ? getenv("WinDir") : getenv("SystemRoot");
  static char path[MAX_PATH];

  if (!env)
    return (file);

  if (win9x)
    snprintf (path, sizeof(path), "%s\\etc\\%s", env, file);
  else
    snprintf (path, sizeof(path), "%s\\system32\\drivers\\etc\\%s", env, file);

  return (path);
}

/*
 * Parse a string-buf containing an MAC address and name.
 * Accepts MAC addresses on both "xx:xx:xx.." and "xx-xx-xx.." forms.
 *
 * We could have used pcap_ether_aton(), but problem 3) above could apply.
 * or we could have cut & pasted 'pcap_next_etherent(FILE *fp)' below.
 */
#define MIN_LEN  sizeof("0:0:0:0:0:0 X")

static
int parse_ether_buf (const char *buf, char **result, struct ether_addr *e)
{
  const char *fmt;
  char       *name;
  char       *str = (char*)buf;
  unsigned    eth [sizeof(*e)];
  int         i;

  /* Find first non-blank in 'buf' */
  while (str[0] && str[1] && isspace((int)str[0]))
       str++;

  if (*str == '#' || *str == ';' || *str == '\n' || strlen(str) < MIN_LEN)
     return (0);

  if (str[2] == ':')
    fmt = "%02x:%02x:%02x:%02x:%02x:%02x";
  else
    fmt = "%02x-%02x-%02x-%02x-%02x-%02x";

  if (sscanf(str, fmt, &eth[0], &eth[1], &eth[2], &eth[3], &eth[4], &eth[5]) != ETHER_ADDR_LEN)
     return (0);

  str  = strtok (str, " \t");
  name = strtok (NULL, " #\t\n");

  if (!str || !name || strlen(name) < 1)
     return (0);

  *result = name;

  for (i = 0; i < ETHER_ADDR_LEN; i++)
      e->octet[i] = eth[i];

  return (1);
}

static void free_ethers (void)
{
  struct ether_entry *e, *next;

  for (e = eth0; e; e = next) {
    next = e->next;
    free(e->name);
    free(e);
  }
  eth0 = NULL;
}

static int init_ethers (void)
{
  char  buf[BUFSIZE];
  FILE *fp = fopen (etc_path("ethers"), "r");

  if (!fp)
     return (0);

  while (fgets(buf,sizeof(buf),fp))
  {
    struct ether_entry *e;
    char  *name;
    ether_address eth;

    if (!parse_ether_buf(buf,&name,&eth))
       continue;

    e = calloc (sizeof(*e), 1);
    if (!e)
       break;

    memcpy(&e->eth_addr, &eth, ETHER_ADDR_LEN);
    e->name = strdup(name);
    if (!e->name) {
      free(e);
      break;
    }

    e->next = eth0;
    eth0 = e;
  }
  fclose(fp);
  atexit(free_ethers);
  return (1);
}

/*
 * Map an ethernet address 'e' to a 'name'.
 * Returns 0 on success.
 *
 * This function is called at startup by init_etherarray() and then
 * by etheraddr_string() as needed. To avoid doing an expensive fopen()
 * on each call, the contents of 'etc_path("ethers")' is cached here in
 * a linked-list 'eth0'.
 */
int ether_ntohost (char *name, struct ether_addr *e)
{
  const struct ether_entry *cache;
  static int init = 0;

  if (!init) {
    init_ethers();
    init = 1;
  }

  for (cache = eth0; cache; cache = cache->next)
     if (!memcmp(&e->octet, &cache->eth_addr, ETHER_ADDR_LEN)) {
       strcpy (name,cache->name);
       return (0);
     }
  return (1);
}

