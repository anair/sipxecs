#!/bin/bash
#
# Copyright (C) 2008 Nortel, certain elements licensed under a Contributor Agreement.  
# Contributors retain copyright to elements licensed under a Contributor Agreement.
# Licensed to the User under the LGPL license.
#
###################################################

# See http://sipx-wiki.calivia.com/index.php/Express_Development_Environment_Setup for instructions.

# Clean up the old (which may not even exist...)
if test -x /etc/init.d/sipxpbx
then
    sudo /sbin/service sipxpbx stop
elif test -x /etc/init.d/sipxecs
then
    sudo /sbin/service sipxecs stop
fi
sudo killall httpd

# You can override the CODE directory, relative to the current directory.
INSTALL=INSTALL
BUILD=BUILD
CODE=main
LINKS=links
EDE_LOGS=ede_logs
if [ $# -gt 0 ]
then
   CODE=$1
fi
if [ ! -d $CODE ]; then
   echo "Error: Directory './$CODE' does not exist."
   exit 1
fi
echo INSTALL=`pwd`/$INSTALL > env
echo BUILD=`pwd`/$BUILD >> env
echo CODE=`pwd`/$CODE >> env
echo LINKS=`pwd`/$LINKS >> env
sudo rm -rf $INSTALL $BUILD $LINKS $CODE/lib/freeswitch/dist $EDE_LOGS
mkdir $INSTALL
mkdir $BUILD
mkdir $EDE_LOGS
FULL_PATH_EDE_LOGS=`pwd`/$EDE_LOGS

# Easy scripts to start, stop, restart, and get status.
echo sudo `pwd`/$INSTALL/etc/init.d/sipxecs start > /tmp/sstart
sudo mv /tmp/sstart /usr/bin/
sudo chmod a+rx /usr/bin/sstart
echo sudo `pwd`/$INSTALL/etc/init.d/sipxecs stop > /tmp/sstop
sudo mv /tmp/sstop /usr/bin/
sudo chmod a+rx /usr/bin/sstop
echo sudo `pwd`/$INSTALL/etc/init.d/sipxecs status > /tmp/sstatus
sudo mv /tmp/sstatus /usr/bin/
sudo chmod a+rx /usr/bin/sstatus
echo sudo `pwd`/$INSTALL/etc/init.d/sipxecs stop > /tmp/srestart
echo sudo `pwd`/$INSTALL/etc/init.d/sipxecs start >> /tmp/srestart
sudo mv /tmp/srestart /usr/bin/
sudo chmod a+rx /usr/bin/srestart

# Get ready to build RPMs.
mv ~/.rpmmacros ~/.rpmmacros.old 2> /dev/null
rpmdev-setuptree

# Configure and compile FreeSWITCH RPMs.
pushd $CODE/lib/freeswitch
rm -rf dist
autoreconf -if
if [ $? != 0 ]
then
   echo "Error: FreeSWITCH autoreconf failed, see console output."
   exit 2
fi
./configure SIPXPBXUSER='whoami' &> $FULL_PATH_EDE_LOGS/fs_configure_output.txt
if [ $? != 0 ]
then
   echo "Error: FreeSWITCH configure failed, see $EDE_LOGS/fs_configure_output.txt."
   exit 3
fi
sudo make &> $FULL_PATH_EDE_LOGS/fs_make_output.txt
if [ $? != 0 ]
then
   echo "Error: FreeSWITCH RPM build failed, see $EDE_LOGS/fs_make_output.txt."
   exit 4
fi

# Uninstall any old FreeSWITCH RPMs.
for fs_old_rpm in `rpm -qa | grep sipx-freeswitch`
do
   sudo rpm --erase --nodeps $fs_old_rpm &> $FULL_PATH_EDE_LOGS/fs_install_output.txt
   if [ $? != 0 ]
   then
      echo "Error: FreeSWITCH old RPM uninstall failed, see $EDE_LOGS/fs_install_output.txt"
      exit 5
   fi
done

# Install the new FreeSWITCH RPMs.
sudo yum -y localinstall dist/RPM/*.rpm &> $FULL_PATH_EDE_LOGS/fs_install_output.txt
if [ $? != 0 ]
then
   echo "Error: FreeSWITCH RPM install failed, see $EDE_LOGS/fs_install_output.txt."
   exit 6
fi

popd

# Configure, compile, test, and installi sipXecs.
pushd $INSTALL
FULL_INSTALL_PATH=`pwd`
popd
pushd $CODE
FULL_CODE_PATH=`pwd`
autoreconf -if  
if [ $? != 0 ]
then
   echo "Error: sipXecs autoreconf failed, see console output."
   exit 7
fi
popd

pushd $BUILD
$FULL_CODE_PATH/configure --srcdir=$FULL_CODE_PATH --cache-file=`pwd`/ac-cache-file SIPXPBXUSER=`whoami` JAVAC_DEBUG=on --prefix=$FULL_INSTALL_PATH --enable-reports --enable-agent --enable-cdr --enable-conference &> $FULL_PATH_EDE_LOGS/configure_output.txt
if [ $? != 0 ]
then
   echo "Error: sipXecs configure failed, see $EDE_LOGS/configure_output.txt."
   exit 8
fi
make recurse TARGETS="all install" &> $FULL_PATH_EDE_LOGS/make_output.txt
if [ $? != 0 ]
then
   echo "Error: sipXecs build/install failed, see $EDE_LOGS/make_output.txt."
   exit 9
fi
popd

# This is needed so often, we might as well make it easily available with "sudo /sbin/service sipxecs xxx", 
# and started automatically after reboot.  
sudo rm -rf /etc/init.d/sipxecs
sudo ln -s $FULL_INSTALL_PATH/etc/init.d/sipxecs /etc/init.d/sipxecs

# Cause the logs to be rotated.
sudo rm -rf /etc/logrotate.d/sipxchange
sudo ln -s $FULL_INSTALL_PATH/etc/logrotate.d/sipxchange /etc/logrotate.d/sipxchange
sudo rm -rf /etc/logrotate.d/freeswitch
sudo ln -s $FULL_INSTALL_PATH/etc/logrotate.d/freeswitch /etc/logrotate.d/freeswitch

# Adjust the TFTP/FTP directory.
TFTP_PATH=$FULL_INSTALL_PATH/var/sipxdata/configserver/phone/profile/tftproot
ruby -e 'path=""; ARGV[0].split("/").each {|x| path+=x+"/"; `sudo chmod g+x #{path}`}' $TFTP_PATH
sudo rm -rf /tftpboot
sudo ln -s $TFTP_PATH /tftpboot

# Clear any database contents that might be left over from the last install.
$FULL_INSTALL_PATH/bin/sipxconfig.sh --database drop create &> $FULL_PATH_EDE_LOGS/sipxconfig_drop_create.log
$FULL_INSTALL_PATH/bin/sipxconfig.sh --first-run &> $FULL_PATH_EDE_LOGS/sipxconfig_first-run.log

# Fix FreeSWITCH
sudo $FULL_INSTALL_PATH/bin/freeswitch.sh --configtest &> $FULL_PATH_EDE_LOGS/freeswitch_configtest.log

# Create some helpful links.
mkdir $LINKS
pushd $LINKS
ln -s $FULL_INSTALL_PATH/var/log/sipxpbx log
ln -s $FULL_INSTALL_PATH/var/sipxdata/configserver/phone/profile/tftproot tftproot
ln -s $FULL_INSTALL_PATH/var/sipxdata/sipdb sipdb
ln -s $FULL_INSTALL_PATH/etc/sipxpbx home
ln -s $FULL_INSTALL_PATH/bin bin
ln -s $FULL_INSTALL_PATH/share/sipxecs/process.d process.d
popd

# sipxecs-setup
$FULL_INSTALL_PATH/bin/sipxecs-setup

# Restart sipXecs twice.  This gets around the "httpd-sipxchange-common-ssl.conf: No 
# such file or directory" error on first start, and I've also seen it fix the 
# a "Resource Required" state for services.
echo ""
echo "Restarting sipXecs twice...."
srestart
sleep 10
srestart
sstatus

echo ""
echo "TO START     : sstart"
echo "TO STOP      : sstop"
echo "TO RESTART   : srestart"
echo "TO GET STATUS: sstatus"
echo ""
echo "ENV:"
cat env
echo ""
echo "DONE!"


