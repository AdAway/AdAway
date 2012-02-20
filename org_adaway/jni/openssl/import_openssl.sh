#!/bin/bash
#
# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#
# This script imports new versions of OpenSSL (http://openssl.org/source) into the
# Android source tree.  To run, (1) fetch the appropriate tarball from the OpenSSL repository,
# (2) check the gpg/pgp signature, and then (3) run:
#   ./import_openssl.sh import openssl-*.tar.gz
#
# IMPORTANT: See README.android for additional details.

# turn on exit on error as well as a warning when it happens
set -e
trap  "echo WARNING: Exiting on non-zero subprocess exit code" ERR;

function die() {
  declare -r message=$1

  echo $message
  exit 1
}

function usage() {
  declare -r message=$1

  if [ ! "$message" = "" ]; then
    echo $message
  fi
  echo "Usage:"
  echo "  ./import_openssl.sh import </path/to/openssl-*.tar.gz>"
  echo "  ./import_openssl.sh regenerate <patch/*.patch>"
  echo "  ./import_openssl.sh generate <patch/*.patch> </path/to/openssl-*.tar.gz>"
  exit 1
}

function main() {
  if [ ! -d patches ]; then
    die "OpenSSL patch directory patches/ not found"
  fi

  if [ ! -f openssl.version ]; then
    die "openssl.version not found"
  fi

  source openssl.version
  if [ "$OPENSSL_VERSION" == "" ]; then
    die "Invalid openssl.version; see README.android for more information"
  fi

  OPENSSL_DIR=openssl-$OPENSSL_VERSION
  OPENSSL_DIR_ORIG=$OPENSSL_DIR.orig

  if [ ! -f openssl.config ]; then
    die "openssl.config not found"
  fi

  source openssl.config
  if [ "$CONFIGURE_ARGS" == "" -o "$UNNEEDED_SOURCES" == "" -o "$NEEDED_SOURCES" == "" ]; then
    die "Invalid openssl.config; see README.android for more information"
  fi

  declare -r command=$1
  shift || usage "No command specified. Try import, regenerate, or generate."
  if [ "$command" = "import" ]; then
    declare -r tar=$1
    shift || usage "No tar file specified."
    import $tar
  elif [ "$command" = "regenerate" ]; then
    declare -r patch=$1
    shift || usage "No patch file specified."
    [ -d $OPENSSL_DIR ] || usage "$OPENSSL_DIR not found, did you mean to use generate?"
    [ -d $OPENSSL_DIR_ORIG_ORIG ] || usage "$OPENSSL_DIR_ORIG not found, did you mean to use generate?"
    regenerate $patch
  elif [ "$command" = "generate" ]; then
    declare -r patch=$1
    shift || usage "No patch file specified."
    declare -r tar=$1
    shift || usage "No tar file specified."
    generate $patch $tar
  else
    usage "Unknown command specified $command. Try import, regenerate, or generate."
  fi
}

function import() {
  declare -r OPENSSL_SOURCE=$1

  untar $OPENSSL_SOURCE readonly
  applypatches $OPENSSL_DIR

  cd $OPENSSL_DIR

  # Configure source (and print Makefile defines for review, see README.android)
  ./Configure $CONFIGURE_ARGS
  rm -f apps/CA.pl.bak crypto/opensslconf.h.bak
  echo
  echo BEGIN Makefile defines to compare with android-config.mk
  echo
  grep -e -D Makefile | grep -v CONFIGURE_ARGS= | grep -v OPTIONS= | grep -v -e -DOPENSSL_NO_DEPRECATED
  echo
  echo END Makefile defines to compare with android-config.mk
  echo

  # TODO(): Fixup android-config.mk

  cp -f LICENSE ../NOTICE
  touch ../MODULE_LICENSE_BSD_LIKE

  # Avoid checking in symlinks
  for i in `find include/openssl -type l`; do
    target=`readlink $i`
    rm -f $i
    if [ -f include/openssl/$target ]; then
      cp include/openssl/$target $i
    fi
  done

  # Copy Makefiles
  cp ../patches/apps_Android.mk apps/Android.mk
  cp ../patches/crypto_Android.mk crypto/Android.mk
  cp ../patches/ssl_Android.mk ssl/Android.mk

  # Generate asm
  perl crypto/aes/asm/aes-armv4.pl         > crypto/aes/asm/aes-armv4.s
  perl crypto/bn/asm/armv4-mont.pl         > crypto/bn/asm/armv4-mont.s
  perl crypto/sha/asm/sha1-armv4-large.pl  > crypto/sha/asm/sha1-armv4-large.s
  perl crypto/sha/asm/sha256-armv4.pl      > crypto/sha/asm/sha256-armv4.s
  perl crypto/sha/asm/sha512-armv4.pl      > crypto/sha/asm/sha512-armv4.s

  # Setup android.testssl directory
  mkdir android.testssl
  cat test/testssl | \
    sed 's#../util/shlib_wrap.sh ./ssltest#adb shell /system/bin/ssltest#' | \
    sed 's#../util/shlib_wrap.sh ../apps/openssl#adb shell /system/bin/openssl#' | \
    sed 's#adb shell /system/bin/openssl no-dh#[ `adb shell /system/bin/openssl no-dh` = no-dh ]#' | \
    sed 's#adb shell /system/bin/openssl no-rsa#[ `adb shell /system/bin/openssl no-rsa` = no-dh ]#' | \
    sed 's#../apps/server2.pem#/sdcard/android.testssl/server2.pem#' | \
    cat > \
    android.testssl/testssl
  chmod +x android.testssl/testssl
  cat test/Uss.cnf | sed 's#./.rnd#/sdcard/android.testssl/.rnd#' >> android.testssl/Uss.cnf
  cat test/CAss.cnf | sed 's#./.rnd#/sdcard/android.testssl/.rnd#' >> android.testssl/CAss.cnf
  cp apps/server2.pem android.testssl/
  cp ../patches/testssl.sh android.testssl/

  cd ..

  # Prune unnecessary sources
  prune

  NEEDED_SOURCES="$NEEDED_SOURCES android.testssl"
  for i in $NEEDED_SOURCES; do
    echo "Updating $i"
    rm -r $i
    mv $OPENSSL_DIR/$i .
  done

  cleantar
}

function regenerate() {
  declare -r patch=$1

  generatepatch $patch
}

function generate() {
  declare -r patch=$1
  declare -r OPENSSL_SOURCE=$2

  untar $OPENSSL_SOURCE
  applypatches $OPENSSL_DIR_ORIG $patch
  prune

  for i in $NEEDED_SOURCES; do
    echo "Restoring $i"
    rm -r $OPENSSL_DIR/$i
    cp -rf $i $OPENSSL_DIR/$i
  done

  generatepatch $patch
  cleantar
}

function untar() {
  declare -r OPENSSL_SOURCE=$1
  declare -r readonly=$2

  # Remove old source
  cleantar

  # Process new source
  tar -zxf $OPENSSL_SOURCE
  mv $OPENSSL_DIR $OPENSSL_DIR_ORIG
  if [ ! -z $readonly ]; then
    find $OPENSSL_DIR_ORIG -type f -print0 | xargs -0 chmod a-w
  fi
  tar -zxf $OPENSSL_SOURCE
}

function prune() {
  echo "Removing $UNNEEDED_SOURCES"
  (cd $OPENSSL_DIR_ORIG && rm -rf $UNNEEDED_SOURCES)
  (cd $OPENSSL_DIR      && rm -r  $UNNEEDED_SOURCES)
}

function cleantar() {
  rm -rf $OPENSSL_DIR_ORIG
  rm -rf $OPENSSL_DIR
}

function applypatches () {
  declare -r dir=$1
  declare -r skip_patch=$2

  cd $dir

  # Apply appropriate patches
  for i in $OPENSSL_PATCHES; do
    if [ ! "$skip_patch" = "patches/$i" ]; then
      echo "Applying patch $i"
      patch -p1 < ../patches/$i || die "Could not apply patches/$i. Fix source and run: $0 regenerate patches/$i"
    else
      echo "Skiping patch $i"
    fi

  done

  # Cleanup patch output
  find . -type f -name "*.orig" -print0 | xargs -0 rm -f

  cd ..
}

function generatepatch() {
  declare -r patch=$1

  # Cleanup stray files before generating patch
  find $BOUNCYCASTLE_DIR -type f -name "*.orig" -print0 | xargs -0 rm -f
  find $BOUNCYCASTLE_DIR -type f -name "*~" -print0 | xargs -0 rm -f

  declare -r variable_name=OPENSSL_PATCHES_`basename $patch .patch | sed s/-/_/`_SOURCES
  # http://tldp.org/LDP/abs/html/ivr.html
  eval declare -r sources=\$$variable_name
  rm -f $patch
  touch $patch
  for i in $sources; do
    LC_ALL=C TZ=UTC0 diff -aup $OPENSSL_DIR_ORIG/$i $OPENSSL_DIR/$i >> $patch && die "ERROR: No diff for patch $path in file $i"
  done
  echo "Generated patch $patch"
  echo "NOTE To make sure there are not unwanted changes from conflicting patches, be sure to review the generated patch."
}

main $@
