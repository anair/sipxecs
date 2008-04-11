#! /usr/bin/perl

use CGI qw/:standard/;
use IPC::Open3;
use File::Temp qw/ tempfile /;;
use strict;

my $query = new CGI;

my @tokens;

# get the tokens (if any) for the requested messages
push @tokens, $query->param('token1') if $query->param('token1');
push @tokens, $query->param('token2') if $query->param('token2');
push @tokens, $query->param('token3') if $query->param('token3');

if ( @tokens )
{
    # this is a submission, so we will scan logs and produce a trace
    
    # make a file for the output to go in
    my ( $outfile, $outfilename ) = tempfile();

    print $outfile "\n"; # give it some content so it won't go away
    close $outfile;

    # run the sipx-trace command to grep logs and merge the trace data
    my $pid = open3(\*TRACE_WRITE, \*TRACE_READ, \*TRACE_ERR,
                    'sipx-trace', '--all-components', '-o', $outfilename, @tokens );
    my $err = <TRACE_ERR>;
    waitpid( $pid, 0 ) or die "$!\n";
    my $status = $?;

    if ( $status == 0 )
    {
        # normal exit, so get the output trace file
        print $query->header(-type=>'text/xml',
                             -attachment=>'trace.xml'
            );
        open(TRACE, "<$outfilename");
        print <TRACE>;
        close TRACE;
    }
    else
    {
        # normal exit, so get the output trace file
        print $query->header();
        print $query->start_html(-title=>'Trace Error');
        print $query->h1('Trace Error');
        print "<pre>";
        print $err;
        print "</pre>";
        print $query->end_html();
    }
}
else
{
    # no tokens submitted.
    # generate the form page
    print $query->header();
    print $query->start_html(-title=>'Interop Online Trace');
    print $query->a({href=>'http://www.pingtel.com/'},
                    '<img src="logo_pingtel.gif" alt="Pingtel Corp." border="0" height="53 width="133" align="left" />',
                    '<br/>'
                   );
    print $query->h1('Interop Online Trace');
    print $query->hr();
    print $query->blockquote(
       'Any information you obtain about other users of this server you may use only under the ',
        $query->a({href=>"https://www.sipit.net/FirstTimers"},"SIPit rules"),
        '.'
        );
    print $query->hr();
    print $query->p(
        'Submitting the form below extracts messages from the sipXecs log files on this system.',
        ' The messages are returned in a "trace.xml" file that you should save onto your system.',
        ' The trace can be displayed using the sipXecs "sipviewer" application.',
        ' You can download ', 
        $query->a({href=>"sipviewer-install.jar"},'an installer for the application.'),
        ' The installer is a java jar file; execute it on your system to install sipviewer;',
        ' it should work on any system with java installed.'
        );
    print $query->hr();
    print $query->p(
        'Enter tokens below - all messages containing any of the tokens will be included in the'
        .' returned trace file.  Call-Id values make especially good selectors.'
        );
    print $query->start_form(-method=>'GET');

    print "Token 1: ", $query->textfield(-name=>'token1', -size=>80, -maxlength=>80), "<br/>";
    print "Token 2: ", $query->textfield(-name=>'token2', -size=>80, -maxlength=>80), "<br/>";
    print "Token 3: ", $query->textfield(-name=>'token3', -size=>80, -maxlength=>80), "<br/>";

    print $query->reset();
    print $query->submit();

    print $query->end_form();
    print $query->end_html();
}    
