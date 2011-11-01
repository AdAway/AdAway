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

#ifdef __ANDROID__
#include <android/log.h>
#endif

/* Implement logging to /dev/log asynchronously. If syslogd is 
   making DNS lookups through dnsmasq, and dnsmasq blocks awaiting
   syslogd, then the two daemons can deadlock. We get around this
   by not blocking when talking to syslog, instead we queue up to 
   MAX_LOGS messages. If more are queued, they will be dropped,
   and the drop event itself logged. */

/* The "wire" protocol for logging is defined in RFC 3164 */

/* From RFC 3164 */
#define MAX_MESSAGE 1024

/* defaults in case we die() before we log_start() */
static int log_fac = LOG_DAEMON;
static int log_stderr = 0; 
static int log_fd = -1;
static int log_to_file = 0;
static int entries_alloced = 0;
static int entries_lost = 0;
static int connection_good = 1;
static int max_logs = 0;
static int connection_type = SOCK_DGRAM;

struct log_entry {
  int offset, length;
  pid_t pid; /* to avoid duplicates over a fork */
  struct log_entry *next;
  char payload[MAX_MESSAGE];
};

static struct log_entry *entries = NULL;
static struct log_entry *free_entries = NULL;


int log_start(struct passwd *ent_pw, int errfd)
{
  int ret = 0;

  log_stderr = !!(daemon->options & OPT_DEBUG);

  if (daemon->log_fac != -1)
    log_fac = daemon->log_fac;
#ifdef LOG_LOCAL0
  else if (daemon->options & OPT_DEBUG)
    log_fac = LOG_LOCAL0;
#endif

  if (daemon->log_file)
    { 
      log_to_file = 1;
      daemon->max_logs = 0;
    }
  
  max_logs = daemon->max_logs;

  if (!log_reopen(daemon->log_file))
    {
      send_event(errfd, EVENT_LOG_ERR, errno);
      _exit(0);
    }

  /* if queuing is inhibited, make sure we allocate
     the one required buffer now. */
  if (max_logs == 0)
    {  
      free_entries = safe_malloc(sizeof(struct log_entry));
      free_entries->next = NULL;
      entries_alloced = 1;
    }

  /* If we're running as root and going to change uid later,
     change the ownership here so that the file is always owned by
     the dnsmasq user. Then logrotate can just copy the owner.
     Failure of the chown call is OK, (for instance when started as non-root) */
  if (log_to_file && ent_pw && ent_pw->pw_uid != 0 && 
      fchown(log_fd, ent_pw->pw_uid, -1) != 0)
    ret = errno;

  return ret;
}

int log_reopen(char *log_file)
{
  if (log_fd != -1)
    close(log_fd);

  /* NOTE: umask is set to 022 by the time this gets called */
     
  if (log_file)
    {
      log_fd = open(log_file, O_WRONLY|O_CREAT|O_APPEND, S_IRUSR|S_IWUSR|S_IRGRP);
      return log_fd != -1;
    }
  else
#ifdef HAVE_SOLARIS_NETWORK
    /* Solaris logging is "different", /dev/log is not unix-domain socket.
       Just leave log_fd == -1 and use the vsyslog call for everything.... */
#   define _PATH_LOG ""  /* dummy */
    log_fd = -1;
#else
    {
       int flags;
       log_fd = socket(AF_UNIX, connection_type, 0);
      
      if (log_fd == -1)
	return 0;
      
      /* if max_logs is zero, leave the socket blocking */
      if (max_logs != 0 && (flags = fcntl(log_fd, F_GETFL)) != -1)
	fcntl(log_fd, F_SETFL, flags | O_NONBLOCK);
    }
#endif

  return 1;
}

static void free_entry(void)
{
  struct log_entry *tmp = entries;
  entries = tmp->next;
  tmp->next = free_entries;
  free_entries = tmp;
}      

static void log_write(void)
{
  ssize_t rc;
   
  while (entries)
    {
      /* Avoid duplicates over a fork() */
      if (entries->pid != getpid())
	{
	  free_entry();
	  continue;
	}

      connection_good = 1;

      if ((rc = write(log_fd, entries->payload + entries->offset, entries->length)) != -1)
	{
	  entries->length -= rc;
	  entries->offset += rc;
	  if (entries->length == 0)
	    {
	      free_entry();
	      if (entries_lost != 0)
		{
		  int e = entries_lost;
		  entries_lost = 0; /* avoid wild recursion */
		  my_syslog(LOG_WARNING, _("overflow: %d log entries lost"), e);
		}	  
	    }
	  continue;
	}
      
      if (errno == EINTR)
	continue;

      if (errno == EAGAIN)
	return; /* syslogd busy, go again when select() or poll() says so */
      
      if (errno == ENOBUFS)
	{
	  connection_good = 0;
	  return;
	}

      /* errors handling after this assumes sockets */ 
      if (!log_to_file)
	{
	  /* Once a stream socket hits EPIPE, we have to close and re-open
	     (we ignore SIGPIPE) */
	  if (errno == EPIPE)
	    {
	      if (log_reopen(NULL))
		continue;
	    }
	  else if (errno == ECONNREFUSED || 
		   errno == ENOTCONN || 
		   errno == EDESTADDRREQ || 
		   errno == ECONNRESET)
	    {
	      /* socket went (syslogd down?), try and reconnect. If we fail,
		 stop trying until the next call to my_syslog() 
		 ECONNREFUSED -> connection went down
		 ENOTCONN -> nobody listening
		 (ECONNRESET, EDESTADDRREQ are *BSD equivalents) */
	      
	      struct sockaddr_un logaddr;
	      
#ifdef HAVE_SOCKADDR_SA_LEN
	      logaddr.sun_len = sizeof(logaddr) - sizeof(logaddr.sun_path) + strlen(_PATH_LOG) + 1; 
#endif
	      logaddr.sun_family = AF_UNIX;
	      strncpy(logaddr.sun_path, _PATH_LOG, sizeof(logaddr.sun_path));
	      
	      /* Got connection back? try again. */
	      if (connect(log_fd, (struct sockaddr *)&logaddr, sizeof(logaddr)) != -1)
		continue;
	      
	      /* errors from connect which mean we should keep trying */
	      if (errno == ENOENT || 
		  errno == EALREADY || 
		  errno == ECONNREFUSED ||
		  errno == EISCONN || 
		  errno == EINTR ||
		  errno == EAGAIN)
		{
		  /* try again on next syslog() call */
		  connection_good = 0;
		  return;
		}
	      
	      /* try the other sort of socket... */
	      if (errno == EPROTOTYPE)
		{
		  connection_type = connection_type == SOCK_DGRAM ? SOCK_STREAM : SOCK_DGRAM;
		  if (log_reopen(NULL))
		    continue;
		}
	    }
	}

      /* give up - fall back to syslog() - this handles out-of-space
	 when logging to a file, for instance. */
      log_fd = -1;
      my_syslog(LOG_CRIT, _("log failed: %s"), strerror(errno));
      return;
    }
}

/* priority is one of LOG_DEBUG, LOG_INFO, LOG_NOTICE, etc. See sys/syslog.h.
   OR'd to priority can be MS_TFTP, MS_DHCP, ... to be able to do log separation between
   DNS, DHCP and TFTP services.
*/
void my_syslog(int priority, const char *format, ...)
{
  va_list ap;
  struct log_entry *entry;
  time_t time_now;
  char *p;
  size_t len;
  pid_t pid = getpid();
  char *func = "";
#ifdef __ANDROID__
  int alog_lvl;
#endif

  if ((LOG_FACMASK & priority) == MS_TFTP)
    func = "-tftp";
  else if ((LOG_FACMASK & priority) == MS_DHCP)
    func = "-dhcp";
      
  priority = LOG_PRI(priority);
  
  if (log_stderr) 
    {
      fprintf(stderr, "dnsmasq%s: ", func);
      va_start(ap, format);
      vfprintf(stderr, format, ap);
      va_end(ap);
      fputc('\n', stderr);
    }

#ifdef __ANDROID__
    if (priority <= LOG_ERR)
      alog_lvl = ANDROID_LOG_ERROR;
    else if (priority == LOG_WARNING)
      alog_lvl = ANDROID_LOG_WARN;
    else if (priority <= LOG_INFO)
      alog_lvl = ANDROID_LOG_INFO;
    else
      alog_lvl = ANDROID_LOG_DEBUG;
    va_start(ap, format);
    __android_log_vprint(alog_lvl, "dnsmasq", format, ap);
    va_end(ap);
#else

  if (log_fd == -1)
    {
      /* fall-back to syslog if we die during startup or fail during running. */
      static int isopen = 0;
      if (!isopen)
	{
	  openlog("dnsmasq", LOG_PID, log_fac);
	  isopen = 1;
	}
      va_start(ap, format);  
      vsyslog(priority, format, ap);
      va_end(ap);
      return;
    }
  
  if ((entry = free_entries))
    free_entries = entry->next;
  else if (entries_alloced < max_logs && (entry = malloc(sizeof(struct log_entry))))
    entries_alloced++;
  
  if (!entry)
    entries_lost++;
  else
    {
      /* add to end of list, consumed from the start */
      entry->next = NULL;
      if (!entries)
	entries = entry;
      else
	{
	  struct log_entry *tmp;
	  for (tmp = entries; tmp->next; tmp = tmp->next);
	  tmp->next = entry;
	}
      
      time(&time_now);
      p = entry->payload;
      if (!log_to_file)
	p += sprintf(p, "<%d>", priority | log_fac);

      p += sprintf(p, "%.15s dnsmasq%s[%d]: ", ctime(&time_now) + 4, func, (int)pid);
        
      len = p - entry->payload;
      va_start(ap, format);  
      len += vsnprintf(p, MAX_MESSAGE - len, format, ap) + 1; /* include zero-terminator */
      va_end(ap);
      entry->length = len > MAX_MESSAGE ? MAX_MESSAGE : len;
      entry->offset = 0;
      entry->pid = pid;

      /* replace terminator with \n */
      if (log_to_file)
	entry->payload[entry->length - 1] = '\n';
    }
  
  /* almost always, logging won't block, so try and write this now,
     to save collecting too many log messages during a select loop. */
  log_write();
  
  /* Since we're doing things asynchronously, a cache-dump, for instance,
     can now generate log lines very fast. With a small buffer (desirable),
     that means it can overflow the log-buffer very quickly,
     so that the cache dump becomes mainly a count of how many lines 
     overflowed. To avoid this, we delay here, the delay is controlled 
     by queue-occupancy, and grows exponentially. The delay is limited to (2^8)ms.
     The scaling stuff ensures that when the queue is bigger than 8, the delay
     only occurs for the last 8 entries. Once the queue is full, we stop delaying
     to preserve performance.
  */

  if (entries && max_logs != 0)
    {
      int d;
      
      for (d = 0,entry = entries; entry; entry = entry->next, d++);
      
      if (d == max_logs)
	d = 0;
      else if (max_logs > 8)
	d -= max_logs - 8;

      if (d > 0)
	{
	  struct timespec waiter;
	  waiter.tv_sec = 0;
	  waiter.tv_nsec = 1000000 << (d - 1); /* 1 ms */
	  nanosleep(&waiter, NULL);
      
	  /* Have another go now */
	  log_write();
	}
    }
#endif
}

void set_log_writer(fd_set *set, int *maxfdp)
{
  if (entries && log_fd != -1 && connection_good)
    {
      FD_SET(log_fd, set);
      bump_maxfd(log_fd, maxfdp);
    }
}

void check_log_writer(fd_set *set)
{
  if (log_fd != -1 && (!set || FD_ISSET(log_fd, set)))
    log_write();
}

void flush_log(void)
{
  /* block until queue empty */
  if (log_fd != -1)
    {
      int flags;
      if ((flags = fcntl(log_fd, F_GETFL)) != -1)
	fcntl(log_fd, F_SETFL, flags & ~O_NONBLOCK);
      log_write();
      close(log_fd);
    }
}

void die(char *message, char *arg1, int exit_code)
{
  char *errmess = strerror(errno);
  
  if (!arg1)
    arg1 = errmess;

  log_stderr = 1; /* print as well as log when we die.... */
  fputc('\n', stderr); /* prettyfy  startup-script message */
  my_syslog(LOG_CRIT, message, arg1, errmess);
  
  log_stderr = 0;
  my_syslog(LOG_CRIT, _("FAILED to start up"));
  flush_log();
  
  exit(exit_code);
}
