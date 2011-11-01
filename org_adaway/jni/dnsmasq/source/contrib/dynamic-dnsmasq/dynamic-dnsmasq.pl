#!/usr/bin/perl
# dynamic-dnsmasq.pl - update dnsmasq's internal dns entries dynamically
# Copyright (C) 2004  Peter Willis
# 
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
# 
# the purpose of this script is to be able to update dnsmasq's dns
# records from a remote dynamic dns client.
# 
# basic use of this script:
# dynamic-dnsmasq.pl add testaccount 1234 testaccount.mydomain.com
# dynamic-dnsmasq.pl listen &
# 
# this script tries to emulate DynDNS.org's dynamic dns service, so
# technically you should be able to use any DynDNS.org client to
# update the records here. tested and confirmed to work with ddnsu
# 1.3.1. just point the client's host to the IP of this machine,
# port 9020, and include the hostname, user and pass, and it should
# work.
# 
# make sure "addn-hosts=/etc/dyndns-hosts" is in your /etc/dnsmasq.conf
# file and "nopoll" is commented out.

use strict;
use IO::Socket;
use MIME::Base64;
use DB_File;
use Fcntl;

my $accountdb = "accounts.db";
my $recordfile = "/etc/dyndns-hosts";
my $dnsmasqpidfile = "/var/run/dnsmasq.pid"; # if this doesn't exist, will look for process in /proc
my $listenaddress = "0.0.0.0";
my $listenport = 9020;

# no editing past this point should be necessary

if ( @ARGV < 1 ) {
	die "Usage: $0 ADD|DEL|LISTUSERS|WRITEHOSTSFILE|LISTEN\n";
} elsif ( lc $ARGV[0] eq "add" ) {
	die "Usage: $0 ADD USER PASS HOSTNAME\n" unless @ARGV == 4;
	add_acct($ARGV[1], $ARGV[2], $ARGV[3]);
} elsif ( lc $ARGV[0] eq "del" ) {
	die "Usage: $0 DEL USER\n" unless @ARGV == 2;
	print "Are you sure you want to delete user \"$ARGV[1]\"? [N/y] ";
	my $resp = <STDIN>;
	chomp $resp;
	if ( lc substr($resp,0,1) eq "y" ) {
		del_acct($ARGV[1]);
	}
} elsif ( lc $ARGV[0] eq "listusers" or lc $ARGV[0] eq "writehostsfile" ) {
	my $X = tie my %h, "DB_File", $accountdb, O_RDWR|O_CREAT, 0600, $DB_HASH;
	my $fh;
	if ( lc $ARGV[0] eq "writehostsfile" ) {
        	open($fh, ">$recordfile") || die "Couldn't open recordfile \"$recordfile\": $!\n";
	       	flock($fh, 2);
	       	seek($fh, 0, 0);
	       	truncate($fh, 0);
        }
	while ( my ($key, $val) = each %h ) {
		my ($pass, $domain, $ip) = split("\t",$val);
		if ( lc $ARGV[0] eq "listusers" ) {
			print "user $key, hostname $domain, ip $ip\n";
		} else {
			if ( defined $ip ) {
				print $fh "$ip\t$domain\n";
			}
		}
	}
	if ( lc $ARGV[0] eq "writehostsfile" ) {
		flock($fh, 8);
		close($fh);
		dnsmasq_rescan_configs();
	}
	undef $X;
	untie %h;
} elsif ( lc $ARGV[0] eq "listen" ) {
	listen_for_updates();
}

sub listen_for_updates {
	my $sock = IO::Socket::INET->new(Listen    => 5,
		LocalAddr => $listenaddress, LocalPort => $listenport,
		Proto     => 'tcp', ReuseAddr => 1,
		MultiHomed => 1) || die "Could not open listening socket: $!\n";
	$SIG{'CHLD'} = 'IGNORE';
	while ( my $client = $sock->accept() ) {
		my $p = fork();
		if ( $p != 0 ) {
			next;
		}
		$SIG{'CHLD'} = 'DEFAULT';
		my @headers;
		my %cgi;
		while ( <$client> ) {
			s/(\r|\n)//g;
			last if $_ eq "";
			push @headers, $_;
		}
		foreach my $header (@headers) {
			if ( $header =~ /^GET \/nic\/update\?([^\s].+) HTTP\/1\.[01]$/ ) {
				foreach my $element (split('&', $1)) {
					$cgi{(split '=', $element)[0]} = (split '=', $element)[1];
				}
			} elsif ( $header =~ /^Authorization: basic (.+)$/ ) {
				unless ( defined $cgi{'hostname'} ) {
					print_http_response($client, undef, "badsys");
					exit(1);
				}
				if ( !exists $cgi{'myip'} ) {
					$cgi{'myip'} = $client->peerhost();
				}
				my ($user,$pass) = split ":", MIME::Base64::decode($1);
				if ( authorize($user, $pass, $cgi{'hostname'}, $cgi{'myip'}) == 0 ) {
					print_http_response($client, $cgi{'myip'}, "good");
					update_dns(\%cgi);
				} else {
					print_http_response($client, undef, "badauth");
					exit(1);
				}
				last;
			}
		}
		exit(0);
	}
	return(0);
}

sub add_acct {
	my ($user, $pass, $hostname) = @_;
	my $X = tie my %h, "DB_File", $accountdb, O_RDWR|O_CREAT, 0600, $DB_HASH;
	$X->put($user, join("\t", ($pass, $hostname)));
	undef $X;
	untie %h;
}

sub del_acct {
        my ($user, $pass, $hostname) = @_;
        my $X = tie my %h, "DB_File", $accountdb, O_RDWR|O_CREAT, 0600, $DB_HASH;
        $X->del($user);
        undef $X;
        untie %h;
}


sub authorize {
	my $user = shift;
	my $pass = shift;
	my $hostname = shift;
	my $ip = shift;;
	my $X = tie my %h, "DB_File", $accountdb, O_RDWR|O_CREAT, 0600, $DB_HASH;
	my ($spass, $shost) = split("\t", $h{$user});
	if ( defined $h{$user} and ($spass eq $pass) and ($shost eq $hostname) ) {
		$X->put($user, join("\t", $spass, $shost, $ip));
		undef $X;
		untie %h;
		return(0);
	}
	undef $X;
	untie %h;
	return(1);
}

sub print_http_response {
	my $sock = shift;
	my $ip = shift;
	my $response = shift;
	print $sock "HTTP/1.0 200 OK\n";
	my @tmp = split /\s+/, scalar gmtime();
	print $sock "Date: $tmp[0], $tmp[2] $tmp[1] $tmp[4] $tmp[3] GMT\n";
	print $sock "Server: Peter's Fake DynDNS.org Server/1.0\n";
	print $sock "Content-Type: text/plain; charset=ISO-8859-1\n";
	print $sock "Connection: close\n";
	print $sock "Transfer-Encoding: chunked\n";
	print $sock "\n";
	#print $sock "12\n"; # this was part of the dyndns response but i'm not sure what it is
	print $sock "$response", defined($ip)? " $ip" : "" . "\n";
}

sub update_dns {
	my $hashref = shift;
	my @records;
	my $found = 0;
	# update the addn-hosts file
	open(FILE, "+<$recordfile") || die "Couldn't open recordfile \"$recordfile\": $!\n";
	flock(FILE, 2);
	while ( <FILE> ) {
		if ( /^(\d+\.\d+\.\d+\.\d+)\s+$$hashref{'hostname'}\n$/si ) {
			if ( $1 ne $$hashref{'myip'} ) {
				push @records, "$$hashref{'myip'}\t$$hashref{'hostname'}\n";
				$found = 1;
			}
		} else {
			push @records, $_;
		}
	}
	unless ( $found ) {
		push @records, "$$hashref{'myip'}\t$$hashref{'hostname'}\n";
	}
	sysseek(FILE, 0, 0);
	truncate(FILE, 0);
	syswrite(FILE, join("", @records));
	flock(FILE, 8);
	close(FILE);
	dnsmasq_rescan_configs();
	return(0);
}

sub dnsmasq_rescan_configs {
	# send the HUP signal to dnsmasq
	if ( -r $dnsmasqpidfile ) {
		open(PID,"<$dnsmasqpidfile") || die "Could not open PID file \"$dnsmasqpidfile\": $!\n";
		my $pid = <PID>;
		close(PID);
		chomp $pid;
		if ( kill(0, $pid) ) {
			kill(1, $pid);
		} else {
			goto LOOKFORDNSMASQ;
		}
	} else {
		LOOKFORDNSMASQ:
		opendir(DIR,"/proc") || die "Couldn't opendir /proc: $!\n";
		my @dirs = grep(/^\d+$/, readdir(DIR));
		closedir(DIR);
		foreach my $process (@dirs) {
			if ( open(FILE,"</proc/$process/cmdline") ) {
				my $cmdline = <FILE>;
				close(FILE);
				if ( (split(/\0/,$cmdline))[0] =~ /dnsmasq/ ) {
					kill(1, $process);
				}
			}
		}
	}
	return(0);
}
