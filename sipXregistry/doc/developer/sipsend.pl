#!/usr/bin/perl

###
###   Tool to probe SIP servers.
###
### Written by: Scott Lawrence <slawrence@pingtel.com>
### Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
### Contributors retain copyright to elements licensed under a Contributor Agreement.
### Licensed to the User under the LGPL license.
###

require 'getargs.pl';
use Socket;
use Digest::MD5 qw(md5_hex);

$VERSION = '0.03';

#') fix the perl indenting funnies caused by the above

## Constants
$CRLF = "\r\n";
$SipVersion = '2.0';

## Defaults
$SipPort = 5060;
$Method = '';
$UserAgent = '';
$MaxForwards = 20;
$UseViaName = 0;
$SymmetricPort = 0;

$DefaultUserAgent = "sipsend/$VERSION";

$HELP = <<HELP;
    Sends a request to SIP server <server> with the specified <method> and
    <request-uri>, and displays the response(s).

    (Note some options start with triple-dash.)

        ---verbose
           Prints the message it is sending before sending it.
        ---timeout <seconds> (not implemented yet)
        ---show1xx|-1
           Also output provisional responses.
        ---symmetric <port>
           Use symmetric signalling (send and listen on the same port)
        ---credentials <username> <password>
           Specify credentials for Digest authentication.
        -t|--to <to>
           'To' header value
        -f|--from <from>
           'From' header value
        -i|--call-id <call-id>
           'Call-ID' header value
        -m|--contact <contact>
           'Contact' header value
        --max-forwards <hop-count>
           'Max-Forwards' header value.
           Limits the number of proxies which may forward the request.
           Defaults to $MaxForwards.
        --user-agent <user-agent>
           'User-Agent' header value
        --cseq <cseq>
           'Cseq' number (method is automatic)
        --route <route>
           'Route' header value
        --expires <expires>
           'Expires' header value
        --event <event>
           'Event' header value
        ---header '<header-name: header-value>'
           Arbitrary header string (do not use for those specified above)
        ---use-via-name
           Use the system name in the via, not the IP address
        ---transport
           Use specified transport, (valid: tcp, udp; default: tcp) 

    (Options with one dash set the SIP short-form header with the same name.
    Options with two dashes set the SIP long-form header with the same name.
    Options with three dashs are specific to $0.)

    <server>
      The server to which the request is sent.  This may include
      the port number as specified in a URL (port defaults to $SipPort).
      Valid examples:
         sipxchange.pingtel.com
         myserver.example.com:5000
         127.0.0.1:12000

    <method>
      The SIP request method (this is upcased before sending).

    <request-uri>
      The URI sent to the server.
HELP

## Legal Values

    &getargs('h', 'help',          0, 'HELP',
             '-', '--verbose',     0, 'ShowRequest',
             '-', '--timeout',     1, 'Timeout',
             '-', '--show1xx',     0, 'Show1xx',
             '-', '--symmetric',   1, 'SymmetricPort',
             '-', '--credentials', 2, 'Credentials',
             '-', 't|to',          1, 'To',
             '-', 'f|from',        1, 'From',
             '-', 'i|call-id',     1, 'CallId',
             '-', 'm|contact',     1, 'Contact',
             '-', '-max-forwards', 1, 'MaxForwards',
             '-', '-user-agent',   1, 'UserAgent',
             '-', '-cseq',         1, 'Cseq',
             '-', '-route',        1, 'Route',
             '-', '-expires',      1, 'Expires',
             '-', '-event',        1, 'Event',
             '-', '--header',      1, 'Header',
             '-', '--use-via-name',0, 'UseViaName',
             '-', '--transport',   1, 'TransportIn',
             'm', 'server',        1, 'Server',
             'm', 'method',        1, 'Method',
             'm', 'request-uri',   1, 'Target',
             )
    || exit 1;

die "--timeout is not implemented" if $Timeout;

if ( $Server =~ m/([^:]+):(\d+)/ )
{
    $Server = $1;
    $Port = $2;
}
else
{
    $Port = $SipPort;
}

### Connect to server

if ( ! $TransportIn )
{
    # If no Transport is specified, use tcp.
    $Transport = "tcp";
}
else
{
    $Transport = lc($TransportIn);
}

# Set socket type based on protocol
if ( $Transport eq "tcp" )
{
    $SockType = SOCK_STREAM;
}
else
{
    $SockType = SOCK_DGRAM;
}

$proto = getprotobyname($Transport);
socket( SIP, PF_INET, $SockType, $proto );

if ( $SymmetricPort )
{
    bind( SIP, sockaddr_in( $SymmetricPort, INADDR_ANY ))
        || die "Bind to INADDR_ANY:$SymmetricPort failed: $!\n";
}

$serverAddr = sockaddr_in( $Port, inet_aton( $Server ) );

connect( SIP, $serverAddr )
    || die "Connect to $Server:$Port failed: $!\n";
($myPort, $myAddr) = sockaddr_in(getsockname(SIP));

select(SIP); $| = 1; select(STDOUT);

## Request Line
$Method = uc($Method);

if ( ! $To )
{
    # If no To is specified, use the request-URI.
    # But To is in name-addr form.
    $To = "<".$Target.">";
}

die "Invalid hop count '$MaxForwards';\n   must be numeric\n"
    if defined( $MaxForwards ) && $MaxForwards !~ m/^\d+/;

$MyAddr = inet_ntoa($myAddr);

if ( $UseViaName )
{
  $MyName = scalar gethostbyaddr($myAddr, AF_INET);
  $MySystem = $MyName || $MyAddr;
}
else
{
  $MySystem = $MyAddr;
}
$Branch = "z9hG4bK-".&RandHash;
$UCTransport = uc($Transport);
$Via = "SIP/$SipVersion/$UCTransport $MySystem:$myPort;branch=$Branch";


if ( ! $From )
{
    $From = "Sip Send <sip:sipsend\@$MySystem;transport=$Transport>;tag=".substr(&RandHash,0,8);
}

if ( ! $Contact )
{
    $Contact = "<sip:sipsend\@$MySystem:$MyPort;transport=$Transport>";
}

if ( ! $CallId )
{
    $CallId = &RandHash;
}

$Cseq = 1 unless $Cseq;

$UserAgent = $DefaultUserAgent unless $UserAgent;

# Construct initial request

$Request = &SipRequest;


print STDERR "Sending: to $Server:$Port \n$Request" if ( $ShowRequest );

print SIP $Request;

$| = 1;

$StatusClass=0;
$AuthState = 'None';

while ( $StatusClass < 2 )
{
    $Rsp = '';
    $RspState = 'None';
    $RspContentLength = 0;

    while( <SIP> ) # see also conditional 'last' at bottom
    {
        #print STDERR "trace: xxxx\n$_\nxxxx\n";
        if ( $RspState eq 'None' )
        {
            next if /^\s*$/;
            if ( m|^SIP/(\d+\.\d+)\s+(([1-6])[0-9][0-9])\s+(.+)$| )
            {
                $StatusClass = $3;
                $StatusCode = $2;
            }
            else
            {
                die "Invalid response line: $_\n";
            }

            $RspState = 'Headers';
        }
        elsif ( $RspState eq 'Headers' )
        {
            if ( m|^Content-Length\s*:\s*(\d+)|i )
            {
                $RspContentLength = $1;
            }
            if ( m|^WWW-Authenticate\s*:\s*Digest\s+(.+)|i )
            {
                $Challenge = $1;
            }
            if ( m|^Proxy-Authenticate\s*:\s*Digest\s+(.+)|i )
            {
                $ProxyChallenge = $1;
            }
            elsif ( $_ eq $CRLF )
            {
                $RspState = $RspContentLength ? 'Body' : 'Done';
            }
        }
        elsif ( $RspState eq 'Body' )
        {
            $RspContentLength -= length;
            if ( $RspContentLength == 0 )
            {
                $RspState = 'Done';
            }
            elsif ( $RspContentLength < 0 )
            {
                $Rsp .= $_;
                print $Rsp;
                die "Lost stream syncronization\n";
            }
        }

        $Rsp .= $_ unless ( $StatusClass == 1 && ! $Show1xx );

        last if $RspState eq 'Done';
    }

    if ( $RspState ne 'Done' )
    {
        die "[premature connection close; state=$RspState]\n";
    }

    if ( $AuthState eq 'None'
         && ( $StatusCode == 401 || $StatusCode == 407 )
         )
    {
        if ( @Credentials )
        {
            print STDERR "Challenge: $Rsp" if $ShowRequest;

            &AuthenticateRequest;
            $Cseq++;
            $Request = &SipRequest;

            print STDERR "Sending:\n$Request" if $ShowRequest;
            print SIP $Request;

            $StatusClass = 0; # wait for another response
            $AuthState = 'Attempted';
       }
    }
}

close SIP;

    print $Rsp;

exit 0;

sub SipRequest
{
    my $request;
    my @wdname = ( "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" );
    my @mname = ( "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
                  );

    my ($sec,$min,$hour,$mday,$mon,$year,$wday,$yday) = gmtime;
    my $Date = sprintf( "%s, %d %s %d %02d:%02d:%02d GMT",
                        $wdname[$wday], $mday, $mname[$mon],
                        $year+1900, $hour, $min, $sec
                        );

    $request  = "$Method $Target SIP/$SipVersion" . $CRLF;
    $request .= "Route: $Route" . $CRLF if $Route;
    $request .= "Via: $Via" . $CRLF;
    $request .= "Authorization: $Authorization" . $CRLF if $Authorization;
    $request .= "Proxy-Authorization: $ProxyAuthorization" . $CRLF if $ProxyAuthorization;
    $request .= "To: $To" . $CRLF if $To;
    $request .= "From: $From" . $CRLF;
    $request .= "Call-ID: $CallId" . $CRLF;
    $request .= "Cseq: $Cseq $Method" . $CRLF;
    $request .= "Max-Forwards: $MaxForwards" . $CRLF;
    $request .= "User-Agent: $UserAgent" . $CRLF;
    $request .= "Contact: $Contact" . $CRLF;
    $request .= "Expires: $Expires" . $CRLF if $Expires;
    $request .= "Event: $Event" . $CRLF if $Event;
    $request .= "Date: $Date" . $CRLF;
    $request .= "Content-Length: 0" . $CRLF; # TBD - body from stdin?

    $request .= $Header . $CRLF if ( $Header );
    $request .= $CRLF;

    return $request;
}


sub AuthenticateRequest
{
    ( $User, $Password ) = @Credentials;

    if ( $Challenge )
    {
        $Authorization = &DigestResponse( $Challenge );
    }

    if ( $ProxyChallenge )
    {
        $ProxyAuthorization = &DigestResponse( $ProxyChallenge );
    }
}

sub DigestResponse
{
    my ( $challenge ) = @_;

    if ( $challenge =~ m/nonce="([0-9a-f]+)"/i )
    {
        $nonce = $1;
    }
    if ( $challenge =~ m/realm="([^"]+)"/i ) #"
    {
        $realm = $1;
    }
    if ( $challenge =~ m/opaque="([^"]+)"/i ) #"
    {
        $opaque = $1;
    }
    if ( $challenge =~ m/algorithm=(MD5|MD5-sess)/i )
    {
        $algorithm = $1;
    }
    else
    {
        $algorithm = "MD5";
    }
    if ( $challenge =~ m/qop=/i )
    {
        $do_2616 = 1;
    }

        if ( $do_2616 )
        {
            die "RFC2616 TBD:\n\tnonce='$nonce'\n\trealm='$realm'\n\topaque='$opaque'\n\talgorithm='$algorithm'\n\t\n";
        }
        else
        {
            $A1 = &md5_hex("$User:$realm:$Password");
            $A2 = &md5_hex("$Method:$Target");
        }

        $hash  = &md5_hex("$A1:$nonce:$A2");
        $auth  = "Digest ";
        $auth .= "algorithm=$algorithm";
        $auth .= ",username=\"$User\"";
        $auth .= ",realm=\"$realm\"";
        $auth .= ",nonce=\"$nonce\"";
        $auth .= ",uri=\"$Target\"";
        $auth .= ",opaque=\"$opaque\"" if $opaque;
        $auth .= ",response=\"$hash\"";

    return $auth;
}

sub RandHash
{
    my $in = rand;
    return &md5_hex("$in");
}


__END__
### Local Variables: ***
### mode: perl ***
### comment-start: "## "  ***
### End: ***
