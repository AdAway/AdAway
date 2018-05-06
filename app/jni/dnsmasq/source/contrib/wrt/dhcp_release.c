/* Copyright (c) 2006 Simon Kelley

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; version 2 dated June, 1991.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
*/

/* dhcp_release <interface> <address> <MAC address> <client_id>
   MUST be run as root - will fail otherwise. */

/* Send a DHCPRELEASE message via the specified interface 
   to tell the local DHCP server to delete a particular lease. 
   
   The interface argument is the interface in which a DHCP
   request _would_ be received if it was coming from the client, 
   rather than being faked up here.
   
   The address argument is a dotted-quad IP addresses and mandatory. 
   
   The MAC address is colon separated hex, and is mandatory. It may be 
   prefixed by an address-type byte followed by -, eg

   10-11:22:33:44:55:66

   but if the address-type byte is missing it is assumed to be 1, the type 
   for ethernet. This encoding is the one used in dnsmasq lease files.

   The client-id is optional. If it is "*" then it treated as being missing.
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
#define OPTION_SERVER_IDENTIFIER 54
#define OPTION_CLIENT_ID         61
#define OPTION_MESSAGE_TYPE      53
#define OPTION_END               255
#define DHCPRELEASE              7
#define DHCP_SERVER_PORT         67

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

static struct iovec iov;

static int expand_buf(struct iovec *iov, size_t size)
{
  void *new;

  if (size <= iov->iov_len)
    return 1;

  if (!(new = malloc(size)))
    {
      errno = ENOMEM;
      return 0;
    }

  if (iov->iov_base)
    {
      memcpy(new, iov->iov_base, iov->iov_len);
      free(iov->iov_base);
    }

  iov->iov_base = new;
  iov->iov_len = size;

  return 1;
}

static ssize_t netlink_recv(int fd)
{
  struct msghdr msg;
  ssize_t rc;

  msg.msg_control = NULL;
  msg.msg_controllen = 0;
  msg.msg_name = NULL;
  msg.msg_namelen = 0;
  msg.msg_iov = &iov;
  msg.msg_iovlen = 1;
    
  while (1)
    {
      msg.msg_flags = 0;
      while ((rc = recvmsg(fd, &msg, MSG_PEEK)) == -1 && errno == EINTR);
      
      /* 2.2.x doesn't suport MSG_PEEK at all, returning EOPNOTSUPP, so we just grab a 
         big buffer and pray in that case. */
      if (rc == -1 && errno == EOPNOTSUPP)
        {
          if (!expand_buf(&iov, 2000))
            return -1;
          break;
        }
      
      if (rc == -1 || !(msg.msg_flags & MSG_TRUNC))
        break;
            
      if (!expand_buf(&iov, iov.iov_len + 100))
        return -1;
    }

  /* finally, read it for real */
  while ((rc = recvmsg(fd, &msg, 0)) == -1 && errno == EINTR);
  
  return rc;
}

static int parse_hex(char *in, unsigned char *out, int maxlen, int *mac_type)
{
  int i = 0;
  char *r;
    
  if (mac_type)
    *mac_type = 0;
  
  while (maxlen == -1 || i < maxlen)
    {
      for (r = in; *r != 0 && *r != ':' && *r != '-'; r++);
      if (*r == 0)
        maxlen = i;
      
      if (r != in )
        {
          if (*r == '-' && i == 0 && mac_type)
           {
              *r = 0;
              *mac_type = strtol(in, NULL, 16);
              mac_type = NULL;
           }
          else
            {
              *r = 0;
	      out[i] = strtol(in, NULL, 16);
              i++;
            }
        }
      in = r+1;
    }
    return i;
}

static int is_same_net(struct in_addr a, struct in_addr b, struct in_addr mask)
{
  return (a.s_addr & mask.s_addr) == (b.s_addr & mask.s_addr);
}

static struct in_addr find_interface(struct in_addr client, int fd, int index)
{
  struct sockaddr_nl addr;
  struct nlmsghdr *h;
  ssize_t len;
 
  struct {
    struct nlmsghdr nlh;
    struct rtgenmsg g; 
  } req;

  addr.nl_family = AF_NETLINK;
  addr.nl_pad = 0;
  addr.nl_groups = 0;
  addr.nl_pid = 0; /* address to kernel */

  req.nlh.nlmsg_len = sizeof(req);
  req.nlh.nlmsg_type = RTM_GETADDR;
  req.nlh.nlmsg_flags = NLM_F_ROOT | NLM_F_MATCH | NLM_F_REQUEST | NLM_F_ACK; 
  req.nlh.nlmsg_pid = 0;
  req.nlh.nlmsg_seq = 1;
  req.g.rtgen_family = AF_INET; 

  if (sendto(fd, (void *)&req, sizeof(req), 0, 
	     (struct sockaddr *)&addr, sizeof(addr)) == -1)
    {
      perror("sendto failed");
      exit(1);
    }
  
  while (1)
    {
      if ((len = netlink_recv(fd)) == -1)
	{
	  perror("netlink");
	  exit(1);
	}

      for (h = (struct nlmsghdr *)iov.iov_base; NLMSG_OK(h, (size_t)len); h = NLMSG_NEXT(h, len))
	if (h->nlmsg_type == NLMSG_DONE)
	  exit(0);
	else if (h->nlmsg_type == RTM_NEWADDR)
          {
            struct ifaddrmsg *ifa = NLMSG_DATA(h);  
            struct rtattr *rta;
            unsigned int len1 = h->nlmsg_len - NLMSG_LENGTH(sizeof(*ifa));
            
            if (ifa->ifa_index == index && ifa->ifa_family == AF_INET)
              {
                struct in_addr netmask, addr;
                
                netmask.s_addr = htonl(0xffffffff << (32 - ifa->ifa_prefixlen));
                addr.s_addr = 0;
                
                for (rta = IFA_RTA(ifa); RTA_OK(rta, len1); rta = RTA_NEXT(rta, len1))
		  if (rta->rta_type == IFA_LOCAL)
		    addr = *((struct in_addr *)(rta+1));
		
                if (addr.s_addr && is_same_net(addr, client, netmask))
		  return addr;
	      }
	  }
    }
 
  exit(0);
}

int main(int argc, char **argv)
{ 
  struct in_addr server, lease;
  int mac_type;
  struct dhcp_packet packet;
  unsigned char *p = packet.options;
  struct sockaddr_in dest;
  struct ifreq ifr;
  int fd = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP);
  int nl = socket(AF_NETLINK, SOCK_RAW, NETLINK_ROUTE);
  struct iovec iov;
 
  iov.iov_len = 200;
  iov.iov_base = malloc(iov.iov_len);

  if (argc < 4 || argc > 5)
    { 
      fprintf(stderr, "usage: dhcp_release <interface> <addr> <mac> [<client_id>]\n");
      exit(1);
    }

  if (fd == -1 || nl == -1)
    {
      perror("cannot create socket");
      exit(1);
    }
  
  /* This voodoo fakes up a packet coming from the correct interface, which really matters for 
     a DHCP server */
  strcpy(ifr.ifr_name, argv[1]);
  if (setsockopt(fd, SOL_SOCKET, SO_BINDTODEVICE, &ifr, sizeof(ifr)) == -1)
    {
      perror("cannot setup interface");
      exit(1);
    }
  
  
  lease.s_addr = inet_addr(argv[2]);
  server = find_interface(lease, nl, if_nametoindex(argv[1]));
  
  memset(&packet, 0, sizeof(packet));
 
  packet.hlen = parse_hex(argv[3], packet.chaddr, DHCP_CHADDR_MAX, &mac_type);
  if (mac_type == 0)
    packet.htype = ARPHRD_ETHER;
  else
    packet.htype = mac_type;

  packet.op = BOOTREQUEST;
  packet.ciaddr = lease;
  packet.cookie = htonl(DHCP_COOKIE);

  *(p++) = OPTION_MESSAGE_TYPE;
  *(p++) = 1;
  *(p++) = DHCPRELEASE;

  *(p++) = OPTION_SERVER_IDENTIFIER;
  *(p++) = sizeof(server);
  memcpy(p, &server, sizeof(server));
  p += sizeof(server);

  if (argc == 5 && strcmp(argv[4], "*") != 0)
    {
      unsigned int clid_len = parse_hex(argv[4], p+2, 255, NULL);
      *(p++) = OPTION_CLIENT_ID;
      *(p++) = clid_len;
      p += clid_len;
    }
  
  *(p++) = OPTION_END;
 
  dest.sin_family = AF_INET;
  dest.sin_port = ntohs(DHCP_SERVER_PORT);
  dest.sin_addr = server;

  if (sendto(fd, &packet, sizeof(packet), 0, 
	     (struct sockaddr *)&dest, sizeof(dest)) == -1)
    {
      perror("sendto failed");
      exit(1);
    }

  return 0;
}
