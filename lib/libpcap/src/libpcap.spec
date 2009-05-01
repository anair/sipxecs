%define prefix   /usr
%define version 1.0.0

Summary: packet capture library
Name: libpcap
Version: %version
Release: 1
Group: Development/Libraries
License: BSD
Source: libpcap-1.0.0.tar.gz
BuildRoot: /tmp/%{name}-buildroot
URL: http://www.tcpdump.org

BuildRequires: flex
BuildRequires: bison

%description
Packet-capture library LIBPCAP 1.0.0
Now maintained by "The Tcpdump Group"
See http://www.tcpdump.org
Please send inquiries/comments/reports to tcpdump-workers@lists.tcpdump.org

%prep
%setup

%post
ldconfig

%build
CFLAGS="$RPM_OPT_FLAGS" ./configure --prefix=%prefix
make

%install
rm -rf $RPM_BUILD_ROOT
make install DESTDIR=$RPM_BUILD_ROOT mandir=/usr/share/man
cd $RPM_BUILD_ROOT/usr/lib
V1=`echo 1.0.0 | sed 's/\\.[^\.]*$//g'`
V2=`echo 1.0.0 | sed 's/\\.[^\.]*\.[^\.]*$//g'`
ln -sf libpcap.so.1.0.0 libpcap.so.$V1
if test "$V2" -ne "$V1"; then
    ln -sf libpcap.so.$V1 libpcap.so.$V2
    ln -sf libpcap.so.$V2 libpcap.so
else
    ln -sf libpcap.so.$V1 libpcap.so
fi

%clean
rm -rf $RPM_BUILD_ROOT

%files
%defattr(-,root,root)
%doc LICENSE CHANGES INSTALL.txt README.linux TODO VERSION CREDITS
/usr/lib/libpcap.a
/usr/bin/pcap-config
/usr/share/man/man1/*
/usr/share/man/man3/*
/usr/share/man/man5/*
/usr/share/man/man7/*
/usr/include/pcap.h
/usr/include/pcap/*.h
/usr/include/pcap-bpf.h
/usr/include/pcap-namedb.h
/usr/lib/libpcap.so*
