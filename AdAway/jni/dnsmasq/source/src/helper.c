/* dnsmasq is Copyright (c) 2000-2009 Simon Kelley

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; version 2 dated June, 1991, or
   (at your option) version 3 dated 29 June, 2007.
 
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
     
   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

#include "dnsmasq.h"

/* This file has code to fork a helper process which recieves data via a pipe 
   shared with the main process and which is responsible for calling a script when
   DHCP leases change.

   The helper process is forked before the main process drops root, so it retains root 
   privs to pass on to the script. For this reason it tries to be paranoid about 
   data received from the main process, in case that has been compromised. We don't
   want the helper to give an attacker root. In particular, the script to be run is
   not settable via the pipe, once the fork has taken place it is not alterable by the 
   main process.
*/

#if defined(HAVE_DHCP) && defined(HAVE_SCRIPT)

static void my_setenv(const char *name, const char *value, int *error);

struct script_data
{
  unsigned char action, hwaddr_len, hwaddr_type;
  unsigned char clid_len, hostname_len, uclass_len, vclass_len, shost_len;
  struct in_addr addr, giaddr;
  unsigned int remaining_time;
#ifdef HAVE_BROKEN_RTC
  unsigned int length;
#else
  time_t expires;
#endif
  unsigned char hwaddr[DHCP_CHADDR_MAX];
  char interface[IF_NAMESIZE];
};

static struct script_data *buf = NULL;
static size_t bytes_in_buf = 0, buf_size = 0;

int create_helper(int event_fd, int err_fd, uid_t uid, gid_t gid, long max_fd)
{
  pid_t pid;
  int i, pipefd[2];
  struct sigaction sigact;

  /* create the pipe through which the main program sends us commands,
     then fork our process. */
  if (pipe(pipefd) == -1 || !fix_fd(pipefd[1]) || (pid = fork()) == -1)
    {
      send_event(err_fd, EVENT_PIPE_ERR, errno);
      _exit(0);
    }

  if (pid != 0)
    {
      close(pipefd[0]); /* close reader side */
      return pipefd[1];
    }

  /* ignore SIGTERM, so that we can clean up when the main process gets hit
     and SIGALRM so that we can use sleep() */
  sigact.sa_handler = SIG_IGN;
  sigact.sa_flags = 0;
  sigemptyset(&sigact.sa_mask);
  sigaction(SIGTERM, &sigact, NULL);
  sigaction(SIGALRM, &sigact, NULL);

  if (!(daemon->options & OPT_DEBUG) && uid != 0)
    {
      gid_t dummy;
      if (setgroups(0, &dummy) == -1 || 
	  setgid(gid) == -1 || 
	  setuid(uid) == -1)
	{
	  if (daemon->options & OPT_NO_FORK)
	    /* send error to daemon process if no-fork */
	    send_event(event_fd, EVENT_HUSER_ERR, errno);
	  else
	    {
	      /* kill daemon */
	      send_event(event_fd, EVENT_DIE, 0);
	      /* return error */
	      send_event(err_fd, EVENT_HUSER_ERR, errno);
	    }
	  _exit(0);
	}
    }

  /* close all the sockets etc, we don't need them here. This closes err_fd, so that
     main process can return. */
  for (max_fd--; max_fd >= 0; max_fd--)
    if (max_fd != STDOUT_FILENO && max_fd != STDERR_FILENO && 
	max_fd != STDIN_FILENO && max_fd != pipefd[0] && max_fd != event_fd)
      close(max_fd);
  
  /* loop here */
  while(1)
    {
      struct script_data data;
      char *p, *action_str, *hostname = NULL;
      unsigned char *buf = (unsigned char *)daemon->namebuff;
      int err = 0;

      /* we read zero bytes when pipe closed: this is our signal to exit */ 
      if (!read_write(pipefd[0], (unsigned char *)&data, sizeof(data), 1))
	_exit(0);
      
      if (data.action == ACTION_DEL)
	action_str = "del";
      else if (data.action == ACTION_ADD)
	action_str = "add";
      else if (data.action == ACTION_OLD || data.action == ACTION_OLD_HOSTNAME)
	action_str = "old";
      else
	continue;
	
      /* stringify MAC into dhcp_buff */
      p = daemon->dhcp_buff;
      if (data.hwaddr_type != ARPHRD_ETHER || data.hwaddr_len == 0) 
        p += sprintf(p, "%.2x-", data.hwaddr_type);
      for (i = 0; (i < data.hwaddr_len) && (i < DHCP_CHADDR_MAX); i++)
        {
          p += sprintf(p, "%.2x", data.hwaddr[i]);
          if (i != data.hwaddr_len - 1)
            p += sprintf(p, ":");
        }
      
      /* and CLID into packet */
      if (!read_write(pipefd[0], buf, data.clid_len, 1))
	continue;
      for (p = daemon->packet, i = 0; i < data.clid_len; i++)
	{
	  p += sprintf(p, "%.2x", buf[i]);
	  if (i != data.clid_len - 1) 
	    p += sprintf(p, ":");
	}
      
      /* and expiry or length into dhcp_buff2 */
#ifdef HAVE_BROKEN_RTC
      sprintf(daemon->dhcp_buff2, "%u ", data.length);
#else
      sprintf(daemon->dhcp_buff2, "%lu ", (unsigned long)data.expires);
#endif
      
      if (!read_write(pipefd[0], buf, 
		      data.hostname_len + data.uclass_len + data.vclass_len + data.shost_len, 1))
	continue;
      
      /* possible fork errors are all temporary resource problems */
      while ((pid = fork()) == -1 && (errno == EAGAIN || errno == ENOMEM))
	sleep(2);
      
      if (pid == -1)
	continue;
	  
      /* wait for child to complete */
      if (pid != 0)
	{
	  /* reap our children's children, if necessary */
	  while (1)
	    {
	      int status;
	      pid_t rc = wait(&status);
	      
	      if (rc == pid)
		{
		  /* On error send event back to main process for logging */
		  if (WIFSIGNALED(status))
		    send_event(event_fd, EVENT_KILLED, WTERMSIG(status));
		  else if (WIFEXITED(status) && WEXITSTATUS(status) != 0)
		    send_event(event_fd, EVENT_EXITED, WEXITSTATUS(status));
		  break;
		}
	      
	      if (rc == -1 && errno != EINTR)
		break;
	    }
	  
	  continue;
	}
      
      if (data.clid_len != 0)
	my_setenv("DNSMASQ_CLIENT_ID", daemon->packet, &err);

      if (strlen(data.interface) != 0)
	my_setenv("DNSMASQ_INTERFACE", data.interface, &err);
            
#ifdef HAVE_BROKEN_RTC
      my_setenv("DNSMASQ_LEASE_LENGTH", daemon->dhcp_buff2, &err);
#else
      my_setenv("DNSMASQ_LEASE_EXPIRES", daemon->dhcp_buff2, &err); 
#endif
      
      if (data.vclass_len != 0)
	{
	  buf[data.vclass_len - 1] = 0; /* don't trust zero-term */
	  /* cannot have = chars in env - truncate if found . */
	  if ((p = strchr((char *)buf, '=')))
	    *p = 0;
	  my_setenv("DNSMASQ_VENDOR_CLASS", (char *)buf, &err);
	  buf += data.vclass_len;
	}
      
      if (data.uclass_len != 0)
	{
	  unsigned char *end = buf + data.uclass_len;
	  buf[data.uclass_len - 1] = 0; /* don't trust zero-term */
	  
	  for (i = 0; buf < end;)
	    {
	      size_t len = strlen((char *)buf) + 1;
	      if ((p = strchr((char *)buf, '=')))
		*p = 0;
	      if (strlen((char *)buf) != 0)
		{
		  sprintf(daemon->dhcp_buff2, "DNSMASQ_USER_CLASS%i", i++);
		  my_setenv(daemon->dhcp_buff2, (char *)buf, &err);
		}
	      buf += len;
	    }
	}
      
      if (data.shost_len != 0)
	{
	  buf[data.shost_len - 1] = 0; /* don't trust zero-term */
	  /* cannot have = chars in env - truncate if found . */
	  if ((p = strchr((char *)buf, '=')))
	    *p = 0;
	  my_setenv("DNSMASQ_SUPPLIED_HOSTNAME", (char *)buf, &err);
	  buf += data.shost_len;
	}

      if (data.giaddr.s_addr != 0)
	my_setenv("DNSMASQ_RELAY_ADDRESS", inet_ntoa(data.giaddr), &err); 

      sprintf(daemon->dhcp_buff2, "%u ", data.remaining_time);
      my_setenv("DNSMASQ_TIME_REMAINING", daemon->dhcp_buff2, &err);
      
      if (data.hostname_len != 0)
	{
	  char *dot;
	  hostname = (char *)buf;
	  hostname[data.hostname_len - 1] = 0;
	  if (!legal_hostname(hostname))
	    hostname = NULL;
	  else if ((dot = strchr(hostname, '.')))
	    {
	      my_setenv("DNSMASQ_DOMAIN", dot+1, &err);
	      *dot = 0;
	    }
	}
      
      if (data.action == ACTION_OLD_HOSTNAME && hostname)
	{
	  my_setenv("DNSMASQ_OLD_HOSTNAME", hostname, &err);
	  hostname = NULL;
	}

      /* we need to have the event_fd around if exec fails */
      if ((i = fcntl(event_fd, F_GETFD)) != -1)
	fcntl(event_fd, F_SETFD, i | FD_CLOEXEC);
      close(pipefd[0]);

      p =  strrchr(daemon->lease_change_command, '/');
      if (err == 0)
	{
	  execl(daemon->lease_change_command, 
		p ? p+1 : daemon->lease_change_command,
		action_str, daemon->dhcp_buff, inet_ntoa(data.addr), hostname, (char*)NULL);
	  err = errno;
	}
      /* failed, send event so the main process logs the problem */
      send_event(event_fd, EVENT_EXEC_ERR, err);
      _exit(0); 
    }
}

static void my_setenv(const char *name, const char *value, int *error)
{
  if (*error == 0 && setenv(name, value, 1) != 0)
    *error = errno;
}
 
/* pack up lease data into a buffer */    
void queue_script(int action, struct dhcp_lease *lease, char *hostname, time_t now)
{
  unsigned char *p;
  size_t size;
  unsigned int hostname_len = 0, clid_len = 0, vclass_len = 0;
  unsigned int uclass_len = 0, shost_len = 0;
  
  /* no script */
  if (daemon->helperfd == -1)
    return;

  if (lease->vendorclass)
    vclass_len = lease->vendorclass_len;
  if (lease->userclass)
    uclass_len = lease->userclass_len;
  if (lease->supplied_hostname)
    shost_len = lease->supplied_hostname_len;
  if (lease->clid)
    clid_len = lease->clid_len;
  if (hostname)
    hostname_len = strlen(hostname) + 1;

  size = sizeof(struct script_data) +  clid_len + vclass_len + uclass_len + shost_len + hostname_len;

  if (size > buf_size)
    {
      struct script_data *new;
      
      /* start with reasonable size, will almost never need extending. */
      if (size < sizeof(struct script_data) + 200)
	size = sizeof(struct script_data) + 200;

      if (!(new = whine_malloc(size)))
	return;
      if (buf)
	free(buf);
      buf = new;
      buf_size = size;
    }

  buf->action = action;
  buf->hwaddr_len = lease->hwaddr_len;
  buf->hwaddr_type = lease->hwaddr_type;
  buf->clid_len = clid_len;
  buf->vclass_len = vclass_len;
  buf->uclass_len = uclass_len;
  buf->shost_len = shost_len;
  buf->hostname_len = hostname_len;
  buf->addr = lease->addr;
  buf->giaddr = lease->giaddr;
  memcpy(buf->hwaddr, lease->hwaddr, lease->hwaddr_len);
  buf->interface[0] = 0;
#ifdef HAVE_LINUX_NETWORK
  if (lease->last_interface != 0)
    {
      struct ifreq ifr;
      ifr.ifr_ifindex = lease->last_interface;
      if (ioctl(daemon->dhcpfd, SIOCGIFNAME, &ifr) != -1)
	strncpy(buf->interface, ifr.ifr_name, IF_NAMESIZE);
    }
#else
  if (lease->last_interface != 0)
    if_indextoname(lease->last_interface, buf->interface);
#endif
  
#ifdef HAVE_BROKEN_RTC 
  buf->length = lease->length;
#else
  buf->expires = lease->expires;
#endif
  buf->remaining_time = (unsigned int)difftime(lease->expires, now);

  p = (unsigned char *)(buf+1);
  if (clid_len != 0)
    {
      memcpy(p, lease->clid, clid_len);
      p += clid_len;
    }
  if (vclass_len != 0)
    {
      memcpy(p, lease->vendorclass, vclass_len);
      p += vclass_len;
    }
  if (uclass_len != 0)
    {
      memcpy(p, lease->userclass, uclass_len);
      p += uclass_len;
    }
  if (shost_len != 0)
    {
      memcpy(p, lease->supplied_hostname, shost_len);
      p += shost_len;
    } 
  if (hostname_len != 0)
    {
      memcpy(p, hostname, hostname_len);
      p += hostname_len;
    }

  bytes_in_buf = p - (unsigned char *)buf;
}

int helper_buf_empty(void)
{
  return bytes_in_buf == 0;
}

void helper_write(void)
{
  ssize_t rc;

  if (bytes_in_buf == 0)
    return;
  
  if ((rc = write(daemon->helperfd, buf, bytes_in_buf)) != -1)
    {
      if (bytes_in_buf != (size_t)rc)
	memmove(buf, buf + rc, bytes_in_buf - rc); 
      bytes_in_buf -= rc;
    }
  else
    {
      if (errno == EAGAIN || errno == EINTR)
	return;
      bytes_in_buf = 0;
    }
}

#endif


