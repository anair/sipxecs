#!/bin/sh
echo "bootstrap: checking installation..."

BASEDIR=`pwd`;
LIBDIR=${BASEDIR}/libs;
SUBDIRS="codec/ilbc curl iax iksemel voipcodecs \
        js js/nsprpub libdingaling libedit libresample libsndfile pcre sofia-sip \
        speex sqlite srtp xmlrpc-c openzap";


if [ ! -f modules.conf ]; then 
    cp build/modules.conf.in modules.conf
fi

# keep automake from making us magically GPL, and to stop complaining about missing files.
cp -f docs/COPYING .
cp -f docs/AUTHORS .
cp -f docs/ChangeLog .
touch NEWS
touch README

# autoconf 2.59 or newer
ac_version=`${AUTOCONF:-autoconf} --version 2>/dev/null|sed -e 's/^[^0-9]*//;s/[a-z]* *$//;s/[- ].*//g;q'`
if test -z "$ac_version"; then
echo "bootstrap: autoconf not found."
echo "           You need autoconf version 2.59 or newer installed"
echo "           to build FreeSWITCH from SVN."
exit 1
fi
IFS=_; set $ac_version; IFS=' '
ac_version=$1
IFS=.; set $ac_version; IFS=' '
if test "$1" = "2" -a "$2" -lt "59" || test "$1" -lt "2"; then
echo "bootstrap: autoconf version $ac_version found."
echo "           You need autoconf version 2.59 or newer installed"
echo "           to build FreeSWITCH from SVN."
exit 1
else
echo "bootstrap: autoconf version $ac_version (ok)"
fi

# automake 1.7 or newer

am_version=`${AUTOMAKE:-automake} --version 2>/dev/null|sed -e 's/^[^0-9]*//;s/[a-z]* *$//;s/[- ].*//g;q'`
if test -z "$am_version"; then
echo "bootstrap: automake not found."
echo "           You need automake version 1.7 or newer installed"
echo "           to build FreeSWITCH from SVN."
exit 1
fi
IFS=_; set $am_version; IFS=' '
am_version=$1
IFS=.; set $am_version; IFS=' '
if test "$1" = "1" -a "$2" -lt "7"; then
echo "bootstrap: automake version $am_version found."
echo "           You need automake version 1.7 or newer installed"
echo "           to build FreeSWITCH from SVN."
exit 1
else
echo "bootstrap: automake version $am_version (ok)"
fi

# Sample libtool --version outputs:
# ltmain.sh (GNU libtool) 1.3.3 (1.385.2.181 1999/07/02 15:49:11)
# ltmain.sh (GNU libtool 1.1361 2004/01/02 23:10:52) 1.5a
# output is multiline from 1.5 onwards

# Require libtool 1.4 or newer
libtool=`${LIBDIR}/apr/build/PrintPath glibtool libtool libtool15 libtool14`
lt_pversion=`$libtool --version 2>/dev/null|sed -e 's/([^)]*)//g;s/^[^0-9]*//;s/[- ].*//g;q'`
if test -z "$lt_pversion"; then
echo "bootstrap: libtool not found."
echo "           You need libtool version 1.5.14 or newer installed"
echo "           to build FreeSWITCH from SVN."
exit 1
fi
lt_version=`echo $lt_pversion|sed -e 's/\([a-z]*\)$/.\1/'`
IFS=.; set $lt_version; IFS=' '
lt_status="good"

if test -z "$1"; then a=0 ; else a=$1;fi
if test -z "$2"; then b=0 ; else b=$2;fi
if test -z "$3"; then c=0 ; else c=$3;fi

if test "$a" -lt "2"; then
   if test "$b" -lt "5" -o "$b" =  "5" -a "$c" -lt "14" ; then
      lt_status="bad"
   fi
else
    lt_status="bad"
fi
if test $lt_status = "good"; then
   echo "bootstrap: libtool version $lt_pversion (ok)"
else
echo "bootstrap: libtool version $lt_pversion found."
echo "           You need libtool version 1.5.14 or newer installed"
echo "           to build FreeSWITCH from SVN."

exit 1
fi

echo "Entering directory ${LIBDIR}/apr"
cd ${LIBDIR}/apr

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
# 
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#

# bootstrap: Build the support scripts needed to compile from a
#            checked-out version of the source code.


libtoolize=`build/PrintPath glibtoolize libtoolize15 libtoolize14 libtoolize`
if [ "x$libtoolize" = "x" ]; then
    echo "libtoolize not found in path"
    exit 1
fi

# Create the libtool helper files
#
# Note: we copy (rather than link) them to simplify distribution.
# Note: APR supplies its own config.guess and config.sub -- we do not
#       rely on libtool's versions
#
echo "Copying libtool helper files ..."

# Remove any libtool files so one can switch between libtool 1.3
# and libtool 1.4 by simply rerunning the bootstrap script.
(cd build ; rm -f ltconfig ltmain.sh libtool.m4)

$libtoolize --copy --automake

if [ -f libtool.m4 ]; then 
   ltfile=`pwd`/libtool.m4
else
   ltfindcmd="`sed -n \"/=[^\\\`]/p;/libtool_m4=/{s/.*=/echo /p;q;}\" \
                   < $libtoolize`"
   ltfile=${LIBTOOL_M4-`eval "$ltfindcmd"`}
   # Expecting the code above to be very portable, but just in case...
   if [ -z "$ltfile" -o ! -f "$ltfile" ]; then
     ltpath=`dirname $libtoolize`
     ltfile=`cd $ltpath/../share/aclocal ; pwd`/libtool.m4
   fi
fi
  
if [ ! -f $ltfile ]; then
    echo "$ltfile not found"
    exit 1
fi

echo "bootstrap: Using libtool.m4 at ${ltfile}."

cat $ltfile | sed -e 's/LIBTOOL=\(.*\)top_build/LIBTOOL=\1apr_build/' > build/libtool.m4

# libtool.m4 from 1.6 requires ltsugar.m4
if [ -f ltsugar.m4 ]; then
   rm -f build/ltsugar.m4
   mv ltsugar.m4 build/ltsugar.m4
fi

# Clean up any leftovers
rm -f aclocal.m4 libtool.m4

#
# Generate the autoconf header and ./configure
#
echo "Creating include/arch/unix/apr_private.h.in ..."
${AUTOHEADER:-autoheader}

echo "Creating configure ..."
### do some work to toss config.cache?
${AUTOCONF:-autoconf}

# Remove autoconf 2.5x's cache directory
rm -rf autom4te*.cache

# fix for FreeBSD (at least):
# libtool.m4 is in share/aclocal, while e.g. aclocal19 only looks in share/aclocal19
# get aclocal's default directory and include the libtool.m4 directory via -I if
# it's in a different location

aclocal_dir="`${ACLOCAL:-aclocal} --print-ac-dir`"

if [ -n "${aclocal_dir}" -a -n "${ltfile}" -a "`dirname ${ltfile}`" != "${aclocal_dir}" ] ; then
  ACLOCAL_OPTS="-I `dirname ${ltfile}`"
fi

echo "Entering directory ${LIBDIR}/apr-util"
cd ${LIBDIR}/apr-util
./buildconf

echo "Entering directory ${LIBDIR}/openmrcp"
cd ${LIBDIR}/openmrcp
./bootstrap


for i in ${SUBDIRS}
do
  echo "Entering directory ${LIBDIR}/${i}"
  cd ${LIBDIR}/${i}
  rm -f aclocal.m4
  CFFILE=
  if [ -f ${LIBDIR}/${i}/configure.in ] ; then
      CFFILE="${LIBDIR}/${i}/configure.in"
  else
      if [ -f ${LIBDIR}/${i}/configure.ac ] ; then
          CFFILE="${LIBDIR}/${i}/configure.ac"
      fi
  fi

  if [ ! -z ${CFFILE} ] ; then

      LTTEST=`grep "AC_PROG_LIBTOOL" ${CFFILE}`
      LTTEST2=`grep "AM_PROG_LIBTOOL" ${CFFILE}`
      AMTEST=`grep "AM_INIT_AUTOMAKE" ${CFFILE}`
      AHTEST=`grep "AC_CONFIG_HEADERS" ${CFFILE}`

      echo "Creating aclocal.m4"
      ${ACLOCAL:-aclocal} ${ACLOCAL_OPTS} ${ACLOCAL_FLAGS}

#only run if AC_PROG_LIBTOOL is in configure.in/configure.ac
      if [ ! -z "${LTTEST}" -o "${LTTEST2}" ] ; then
          echo "Running libtoolize..."
          $libtoolize --force --copy ;
      fi

      echo "Creating configure"
      ${AUTOCONF:-autoconf}

#only run if AC_CONFIG_HEADERS is found in configure.in/configure.ac
      if [ ! -z "${AHTEST}" ] ; then
          echo "Running autoheader..."
          ${AUTOHEADER:-autoheader} ;
      fi

#only run if AM_INIT_AUTOMAKE is in configure.in/configure.ac
      if [ ! -z "${AMTEST}" ] ; then
          if [ -f ${LIBDIR}/${i}/Makefile.am ] ; then
              echo "Creating Makefile.in"
              ${AUTOMAKE:-automake} --no-force --add-missing ;
          fi
      fi
      rm -rf autom4te*.cache
  fi
done

cd ${BASEDIR}

rm -f aclocal.m4
${ACLOCAL:-aclocal} ${ACLOCAL_OPTS}
$libtoolize --copy --automake
${AUTOCONF:-autoconf}
${AUTOHEADER:-autoheader}
${AUTOMAKE:-automake} --no-force --add-missing
rm -rf autom4te*.cache

