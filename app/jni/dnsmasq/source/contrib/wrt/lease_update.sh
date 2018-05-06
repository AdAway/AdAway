#!/bin/sh

# Copyright (c) 2006 Simon Kelley
#
#  This program is free software; you can redistribute it and/or modify
#  it under the terms of the GNU General Public License as published by
#  the Free Software Foundation; version 2 dated June, 1991.
#
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.


# if $1 is add del or old, this is a dnsmasq-called lease-change
# script, update the nvram database. if $1 is init, emit a 
# dnsmasq-format lease file to stdout representing the current state of the 
# database, this is called by dnsmasq at startup.

NVRAM=/usr/sbin/nvram
PREFIX=dnsmasq_lease_

# Arguments.
# $1 is action (add, del, old)
# $2 is MAC 
# $3 is address
# $4 is hostname (optional, may be unset)

# env.
# DNSMASQ_LEASE_LENGTH or DNSMASQ_LEASE_EXPIRES (which depends on HAVE_BROKEN_RTC)
# DNSMASQ_CLIENT_ID (optional, may be unset)

# File.
# length|expires MAC addr hostname|* CLID|* 

# Primary key is address.

if [ ${1} = init ] ; then
     ${NVRAM} show | sed -n -e "/^${PREFIX}.*/ s/^.*=//p"
else
     if [ ${1} = del ] ; then
          ${NVRAM} unset ${PREFIX}${3}
     fi

     if [ ${1} = old ] || [ ${1} = add ] ; then
          ${NVRAM} set ${PREFIX}${3}="${DNSMASQ_LEASE_LENGTH:-}${DNSMASQ_LEASE_EXPIRES:-} ${2} ${3} ${4:-*} ${DNSMASQ_CLIENT_ID:-*}"
     fi
     ${NVRAM} commit
fi




 
