#! /usr/bin/perl

use CGI qw/:standard/;
use XML::Parser;
use strict;

# This must be the redirector's log, since the NOTIFY is in-dialog and bypasses
# the proxy.
my($log_file) = '@SIPX_LOGDIR@/sipregistrar.log';
my($registration_file) = '@SIPX_DBDIR@/registration.xml';

# The SIP domain of this server.
my($sip_domain) = '@SIPXCHANGE_DOMAIN_NAME@';

# The un-escape table for backslash escapes.
my(%unescape) = ("r", "\r",
		 "n", "\n",
		 "\"", "\"",
		 "\\", "\\");

# Get the extension.
my($extension) = &param('extension');

# Start the HTML.
print &header,
    &start_html('Dialog event package analysis'), "\n",
    &h1("Dialog event package analysis for extension $extension"), "\n";

# The count of NOTIFYs without body XML.
my($empty_notifies) = 0;

# Start a block.  Any exit from the block will print &end_html.
HTML:{

    # Validate the extension.
    if ($extension !~ /^1\d\d[1-9]$/) {
	print &p(&escapeHTML("Invalid extension '$extension'.")),
	"\n";
	last HTML;
    }

    # Get the dialog event package.
    my($notify) = &find_last_notify($extension);

    # Squawk if there are empty bodies.
    print &p(&strong(&escapeHTML("NOTIFY bodies without <dialog-info> elements:")),
	     ($empty_notifies > 0 ? &red(&escapeHTML("$empty_notifies found.")) :
	      "None found.")),
	  "\n";

    # If we can't find a NOTIFY.
    if ($notify eq '') {
	print &p(&escapeHTML("No dialog event with state other than " .
			     "\"terminated\" found for extension $extension.")),
	    "\n";
	last HTML;
    }

    # Separate the headers and body.
    my($headers, $body) = $notify =~ /^([\000-\377]*?)\n\n([\000-\377]*)$/;

    print &p(&escapeHTML("The latest dialog event package for extension " .
			 "$extension with a state other than \"terminated\" is:")),
	"\n";
    print &pre(&escapeHTML($headers . "\n\n" . $body)),
    "\n";

    # Pretty-print it.

    # Insert line breaks.
    $body =~ s/>\s*</>\n</g;
    # Indent each line appropriately.
    my(@body) = split(/\n/, $body);
    my($depth) = 0;
    my($line);
    foreach $line (@body) {
	my($x) = $line;
	my($delta) = 0;
	# Find all the start and end tags and adjust the depth accordingly.
	$x =~ s%(<(/?)[a-z][^>]*?(/?)>)% ($2 ne '' ? $delta-- : $delta++),
	($3 ne '' ? $delta-- : 0),
	$1 %egi;
	# If the line starts with an end tag, outdent it so it aligns with 
	# its open tag.
	my($outdent) = ($x =~ m%^</[a-z]%i);
	$line = ('  ' x ($depth - $outdent)) . $line;
	$depth += $delta;
    }

    # Print the user-agent identification.
    my($user_agent);
    # Get the User-Agent header from the NOTIFY, if there is one.
    ($user_agent) = $headers =~ /\nUser-Agent:\s*(.*)\n/i;
    # If that did not work, get the User-Agent header from the latest registration.
    if ($user_agent eq '') {
	$user_agent = &user_agent($log_file, $extension);
	$user_agent .= ' (guessed from REGISTER)' if $user_agent ne '';
    }
    # If we got a User-Agent value, print it.
    if ($user_agent ne '') {
	print &p(&strong(&escapeHTML("User-Agent:")), $user_agent),
	      "\n";
    }

    # Content-Type header test.

    my($ok) =
	$headers =~ m%\nContent-Type\s*:\s*application/dialog-info\+xml\s*[;\n]%;
    print &p(&strong('Content-Type', &code('application/dialog-info+xml'),
		     'header:'),
	     ($ok ? "Present" : &red("Absent"))),
	"\n";

    # Subscription-State header test.

    $ok =
	$headers =~ m%\nSubscription-State\s*:%;
    print &p(&strong('Subscription-State header:'),
	     ($ok ? "Present" : &red("Absent"))),
          "\n";

    print &p(&escapeHTML("Reformatted, the body looks like this:")),
    "\n";
    print &pre(&escapeHTML(join("\n", @body))),
    "\n";

    # Parse the event body.
    my($parser) = new XML::Parser(Style => 'Tree');
    my($tree);
    # &XML::Parse::parse dies if it can't parse the string.
    eval { $tree = $parser->parse($body) };

    if ($@ ne '') {
	print &p(&strong("XML syntax:"),
		 &red(&escapeHTML("Could not parse.")) .
		 &br .
		 &escapeHTML("XML parser message was: " . $@)),
		 "\n";
	last HTML;
    }

    print &p(&strong("XML syntax:"), " Passed"),
    "\n";

    # Select a dialog element.

    if ($tree->[0] ne 'dialog-info') {
	print &p(&red(&escapeHTML("Top element is not 'dialog-info'."))),
	"\n";
	last HTML;
    }

    my($di_content) = $tree->[1];
    my($i, $dialog);
    for ($i = 1; $i <= $#$di_content; $i += 2) {
	if ($di_content->[$i] eq 'dialog') {
	    my($d_content) = $di_content->[$i+1];
	    my($j);
	    for ($j = 1; $j <= $#$d_content; $j += 2) {
		if ($d_content->[$j] eq 'state' &&
		    &text($d_content->[$j+1]) ne 'terminated') {
		    $dialog = $d_content;
		}
	    }
	}
    }

    if (!defined($dialog)) {
	print &p(&red(&escapeHTML("No <dialog> with <state> not " .
				  "'terminated' found."))),
	"\n";
	last HTML;
    }

    print &p(&escapeHTML("Analyzing the <dialog> element with id='" .
			 $dialog->[0]->{'id'} . "'.")),
	"\n";

    # The dialog attributes.

    my($call_id) = $dialog->[0]->{'call-id'};
    print &p(&strong('Guideline 5:') . " Attribute 'call-id' " .
	     ($call_id ne '' ? "present" : &red("absent")) . '.'),
	"\n";

    my($local_tag) = $dialog->[0]->{'local-tag'};
    print &p(&strong('Guideline 5:') . " Attribute 'local-tag' " .
	     ($local_tag ne '' ? "present" : &red("absent")) . '.'),
	"\n";

    my($remote_tag) = $dialog->[0]->{'remote-tag'};
    print &p(&strong('Guideline 5:') . " Attribute 'remote-tag' " .
	     ($remote_tag ne '' ? "present" : &red("absent")) . '.'),
	"\n";

    my($direction) = $dialog->[0]->{'direction'};
    print &p(&strong('Guideline 5:') . " Attribute 'direction' " .
	     ($direction ne '' ? "present" : &red("absent")) . '.'),
	"\n";

    # The <duration> element.
    my($duration);
    for ($i = 1; $i <= $#$dialog; $i += 2) {
	if ($dialog->[$i] eq 'duration') {
	    $duration = &text($dialog->[$i+1]);
	    last;
	}
    }
    print &p(&strong('Guideline 5:') . " Duration element " .
	     ($duration =~ /^\d+$/ ? "present" : &red("absent")) . '.'),
	"\n";

    # The local and remote URIs.

    my($local_identity, $local_target, $remote_identity, $remote_target);
    for ($i = 1; $i <= $#$dialog; $i += 2) {
	if ($dialog->[$i] eq 'local') {
	    ($local_identity, $local_target) = &get_URIs($dialog->[$i+1]);
	} elsif ($dialog->[$i] eq 'remote') {
	    ($remote_identity, $remote_target) = &get_URIs($dialog->[$i+1]);
	}
    }

    print &p(&strong('Guideline 6B/6D:') . " Local identity " .
	     ($local_identity ne '' ? "present" : &red("absent")) . '.'),
	"\n";
    if ($local_identity ne '') {
	my($i) = &strip($local_identity);
	my($j) = "sip:$extension\@$sip_domain";
	print &p(&strong('Guideline 6B/6D:') . " Local identity '" .
		 &code(&escapeHTML($i)) . "' " .
		 ($i eq $j ? "matches" : &red("does not match")) .
		 " AOR '" . &code(&escapeHTML($j)) . "'."),
		 "\n";
    }

    print &p(&strong('Guideline 6C/6E:') . " Local target " .
	     ($local_target ne '' ? "present" : &red("absent")) . '.'),
	"\n";
    if ($local_target ne '') {
	# The test on the local target depends on whether the UA has a GRUU.
	my($gruu) = &get_GRUU;
	if ($gruu ne '') {
	    # If the UA has a GRUU, the local target should be the GRUU.
	    my($i) = &strip($local_target);
	    # Copy any sip:/sips: prefix from $i to $gruu.
	    $gruu = $1 . $gruu if $i =~ /^(sips?:)/;
	    print &p(&strong('Guideline 6C/6E:') . " Local target '" .
		     &code(&escapeHTML($i)) . "' " .
		     ($i eq $gruu ? "is" : &red("is not")) .
		     " the UA's GRUU '" . &code(&escapeHTML($gruu)) . "'."),
		     "\n";
	} else {
	    # If the UA has no GRUU, the local target should be a contact URI,
	    # That is, one with an IP address as its host.
	    my($i) = &strip($local_target);
	    my($c) = $i =~ /^sip:((.*)\@)?[0-9.]+(:\d+)?$/;
	    print &p(&strong('Guideline 6C/6E:') . " Local target '" .
		     &code($i) . "' " .
		     ($c ? "is" : &red("is not")) .
		     " an IP address."),
		     "\n";
	}
    }

    print &p(&strong('Guideline 6B/6D:') . " Remote identity " .
	     ($remote_identity ne '' ? "present" : &red("absent")) . '.'),
	"\n";
    if ($remote_identity ne '') {
	my($i) = &strip($remote_identity);
	my($c) = $i =~ /^sip:\d+\@$sip_domain$/;
	print &p(&strong('Guideline 6B/6D:') . " Remote identity '" .
		 &code(&escapeHTML($i)) . "' " .
		 ($c ? "is" : &red("is not")) .
		 " an AOR in " . &code($sip_domain) . "."),
		 "\n";
    }

    print &p(&strong('Guideline 6C/6E:') . " Remote target " .
	     ($remote_target ne '' ? "present" : &red("absent")) . '.'),
	"\n";
    if ($remote_target ne '') {
	my($i) = &strip($remote_target);
	my($c) = $i =~ /^sips?:((.*)\@)?[0-9.]+(:\d+)?$/;
	my($g) = ($i =~ /^sips?:\d+\@$sip_domain;opaque=/ ||
		  $i =~ /^sips?:gruu~/);
	print &p(&strong('Guideline 6C/6E:') . " Remote target '" . &code($i) .
		 "' " .
		 ($c ? "is an IP address" :
		  $g ? "is a GRUU in " . &code($sip_domain) :
		  &red("is neither an IP address nor a GRUU") . " in " .
		  &code($sip_domain)) . "."),
		 "\n";
    }
}

print &end_html,
    "\n";

exit 0;

sub find_last_notify {
    my($extension) = @_;

    # Read through the log file and find the last NOTIFY from this extension.
    my($log_line) = '';
    open(LOG, $log_file) ||
	die "Error opening file '$log_file' for input: $!\n";
    while (<LOG>) {
	next unless /:INCOMING:/;
	next unless /----\\nNOTIFY\s/i;
        # See http://list.sipfoundry.org/archive/sipx-dev/msg10767.html.
	next unless /\\r\\nFrom\s*:(\s*|[^<\\]*<|\s*\\"([^"\\]|\\\\[^\\"]|\\\\\\"|\\\\\\\\)*\\"\s*<)sips?:$extension@/i;
	next unless /\\nEvent\s*:\s*dialog\b/i;
	# Count the number of empty bodies.
	$empty_notifies++ unless /<dialog-info/;
	# Make sure a <state> that isn't "terminated" is present, as
	# terminated notices may not have complete information.
        # See http://list.sipfoundry.org/archive/sipx-dev/msg10792.html.
	next unless m%<state(\s([^>"']|"[^"<]*"|'[^'<]*')*)?>(early|confirmed)</state>%;
	# This line passes the tests, save it.
	$log_line = $_;
    }
    close LOG;

    # Normalize the log line.
    $log_line =~ s/^.*?----\\n//;
    $log_line =~ s/====*END====*\\n"\n$//;
    $log_line =~ s/\\(.)/$unescape{$1}/eg;
    $log_line =~ s/\r\n/\n/g;

    return $log_line;
}

no strict;

# Read and parse the registrations file to find the GRUU for this extension.
sub get_GRUU {
    my($parser) = new XML::Parser(Style => 'Tree');
    my($tree) = $parser->parsefile($registration_file);
    my($local_gruu);

    if ($tree->[0] eq 'items') {
	my $c = $tree->[1];
	my $i;
	for ($i = 1; $i < $#$c; $i += 2) {
	    if ($c->[$i] eq 'item') {
		my($d) = $c->[$i+1];
		my($aor, $gruu);
		my($i);
		for ($i = 1; $i < $#$d; $i += 2) {
		    $e = $d->[$i];
		    $f = $d->[$i+1];
                    if ($e eq 'uri') {
			$aor = &text($f);
		    } elsif ($e eq 'gruu') {
			$gruu = &text($f);
		    }
		}
		if ($aor =~ /^<?sips?:$extension@/) {
		    $local_gruu = $gruu;
		    last;
		}
	    }
	}
    }

    return $local_gruu;
}

# Extract the (top-level) content from an element.
sub text {
    my($tree) = @_;
    my($text) = '';
    my $i;
    for ($i = 1; $i < $#$tree; $i += 2) {
	if ($tree->[$i] eq '0') {
	    $text .= $tree->[$i+1];
        }
    }
    return $text;
}

# Display content in red.
sub red {
    my(@content) = @_;

    return &font({-color => 'red'}, @content);
}

# Get the identity and target content from a local or remote element.
sub get_URIs {
    my($element) = @_;
    my($identity, $target);
    
    my($i);
    for ($i = 1; $i <= $#$element; $i += 2) {
	if ($element->[$i] eq 'identity') {
	    $identity = &text($element->[$i+1]);
        } elsif ($element->[$i] eq 'target') {
	    $target = $element->[$i+1]->[0]->{'uri'};
        }	    
    }
    return ($identity, $target);
}

# Normalize the URI scheme and remove the parameters.
sub strip {
    my($uri) = @_;

    $uri =~ s/^sips?:/sip:/i;
    $uri =~ s/;.*$//;
    return $uri;
}

# Find the user-agent value for a particular extension.
sub user_agent {
    my($log_file, $extension) = @_;
    my($user_agent);

    # Read through the log file and find all the REGISTERs.
    my($log_line) = '';
    open(LOG, $log_file) ||
	die "Error opening file '$log_file' for input: $!\n";
    while (<LOG>) {
	next unless /:INCOMING:/;
	next unless /----\\nREGISTER\s/i;
	# This line passes the tests, process it.

	# Normalize the log line.
	s/^.*?----\\n//;
	s/====*END====*\\n"\n$//;
        s/\\(.)/$unescape{$1}/eg;
        s/\r\n/\n/g;

        # Get the extension.
        if ($extension eq (/\nTo:.*?sips?:(\d+)@/i)[0]) {
            # If the extension matches, record the user-agent.
            ($user_agent) = /\nUser-Agent:\s*(.*)\n/i;
        }
    }
    close LOG;

    # Return the last User-Agent value seen, which is most likely the
    # one that generated the last dialog event.
    return $user_agent;
}
