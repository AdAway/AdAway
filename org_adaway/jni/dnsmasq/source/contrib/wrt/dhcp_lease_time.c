/* Copyright (c) 2007 Simon Kelley

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; version 2 dated June, 1991.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
*/

/* dhcp_lease_time <address> */

/* Send a DHCPINFORM message to a dnsmasq server running on the local host
   and print (to stdout) the time remaining in any lease for the given
   address. The time is given as string printed to stdout.

   If an error occurs or no lease exists for the given address, 
   nothing is sent to stdout a message is sent to stderr and a
   non-zero error code is returned.

   Requires dnsmasq 2.40 or later. 
*/

#include <sys/types.h> 
#include <netinet/in.h>
#include <net/if.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <net/if_arp.h>
#include <sys/ioctl.h>
#include <linux/types.h>
#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include <errno.h>

#define DHCP_CHADDR_MAX          16
#define BOOTREQUEST              1
#define DHCP_COOKIE              0x63825363
#define OPTION_PAD               0
#define OPTION_LEASE_TIME        51
#define OPTION_OVERLOAD          52
#define OPTION_MESSAGE_TYPE      53
#define OPTION_END               255
#define DHCPINFORM               8
#define DHCP_SERVER_PORT         67

#define option_len(opt) ((int)(((unsigned char *)(opt))[1]))
#define option_ptr(opt) ((void *)&(((unsigned char *)(opt))[2]))


typedef unsigned char u8;
typedef unsigned short u16;
typedef unsigned int u32;

struct dhcp_packet {
  u8 op, htype, hlen, hops;
  u32 xid;
  u16 secs, flags;
  struct in_addr ciaddr, yiaddr, siaddr, giaddr;
  u8 chaddr[DHCP_CHADDR_MAX], sname[64], file[128];
  u32 cookie;
  unsigned char options[308];
};

static unsigned char *option_find1(unsigned char *p, unsigned char *end, int opt, int minsize)
{
  while (*p != OPTION_END) 
    {
      if (p >= end)
        return NULL; /* malformed packet */
      else if (*p == OPTION_PAD)
        p++;
      else 
        { 
          int opt_len;
          if (p >= end - 2)
            return NULL; /* malformed packet */
          opt_len = option_len(p);
          if (p >= end - (2 + opt_len))
            return NULL; /* malformed packet */
          if (*p == opt && opt_len >= minsize)
            return p;
          p += opt_len + 2;
        }
    }
  
  return opt == OPTION_END ? p : NULL;
}
 
static unsigned char *option_find(struct dhcp_packet *mess, size_t size, int opt_type, int minsize)
{
  unsigned char *ret, *overload;
  
  /* skip over DHCP cookie; */
  if ((ret = option_find1(&mess->options[0], ((unsigned char *)mess) + size, opt_type, minsize)))
    return ret;

  /* look for overload option. */
  if (!(overload = option_find1(&mess->options[0], ((unsigned char *)mess) + size, OPTION_OVERLOAD, 1)))
    return NULL;
  
  /* Can we look in filename area ? */
  if ((overload[2] & 1) &&
      (ret = option_find1(&mess->file[0], &mess->file[128], opt_type, minsize)))
    return ret;

  /* finally try sname area */
  if ((overload[2] & 2) &&
      (ret = option_find1(&mess->sname[0], &mess->sname[64], opt_type, minsize)))
    return ret;

  return NULL;
}

static unsigned int option_uint(unsigned char *opt, int size)
{
  /* this worries about unaligned data and byte order */
  unsigned int ret = 0;
  int i;
  unsigned char *p = option_ptr(opt);
  
  for (i = 0; i < size; i++)
    ret = (ret << 8) | *p++;

  return ret;
}

int main(int argc, char **argv)
{ 
  struct in_addr lease;
  struct dhcp_packet packet;
  unsigned char *p = packet.options;
  struct sockaddr_in dest;
  int fd = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP);
  ssize_t rc;
  
  if (argc < 2)
    { 
      fprintf(stderr, "usage: dhcp_lease_time <address>\n");
      exit(1);
    }

  if (fd == -1)
    {
      perror("cannot create socket");
      exit(1);
    }
 
  lease.s_addr = inet_addr(argv[1]);
   
  memset(&packet, 0, sizeof(packet));
 
  packet.hlen = 0;
  packet.htype = 0;

  packet.op = BOOTREQUEST;
  packet.ciaddr = lease;
  packet.cookie = htonl(DHCP_COOKIE);

  *(p++) = OPTION_MESSAGE_TYPE;
  *(p++) = 1;
  *(p++) = DHCPINFORM;

  *(p++) = OPTION_END;
 
  dest.sin_family = AF_INET; 
  dest.sin_addr.s_addr = inet_addr("127.0.0.1");
  dest.sin_port = ntohs(DHCP_SERVER_PORT);
  
  if (sendto(fd, &packet, sizeof(packet), 0, 
	     (struct sockaddr *)&dest, sizeof(dest)) == -1)
    {
      perror("sendto failed");
      exit(1);
    }

  alarm(3); /* noddy timeout. */

  rc = recv(fd, &packet, sizeof(packet), 0);
  
  if (rc < (ssize_t)(sizeof(packet) - sizeof(packet.options)))
    {
      perror("recv failed");
      exit(1);
    }

  if ((p = option_find(&packet, (size_t)rc, OPTION_LEASE_TIME, 4)))
    {
      unsigned int t = option_uint(p, 4);
      if (t == 0xffffffff)
	printf("infinite");
      else
	{
	  unsigned int x;
	  if ((x = t/86400))
	    printf("%dd", x);
	  if ((x = (t/3600)%24))
	    printf("%dh", x);
	  if ((x = (t/60)%60))
	    printf("%dm", x);
	  if ((x = t%60))
	    printf("%ds", x);
	}
      return 0;
    }

  return 1; /* no lease */
}
