if GENERATE_MANPAGES
MAN_PAGES_OPT = $(MAN_PAGES)
endif

man_MANS = $(MAN_PAGES_OPT)

MAN_PAGES = \
	sipx-stats.1 \
	sipx-alias2dot.1 \
	sipx-readroute.1 \
	sipx-trace.1 \
	sipx-log-cut.1 \
	sipx-servtest.1 \
	expire-spread.1 \
	monitor-spread.1 \
	regtimes.1 \
	sipxcar.1 \
	sipxproc.1 \
	polycom-config.1 \
	sipdb-dump.1

EXTRA_DIST = \
	$(subst .1,.txt2man,$(MAN_PAGES))

%.xml : %.txt2man
	$(ASCIIDOC) -d manpage -b docbook -o $@ $< 

%.1 : %.xml
	$(XSLTPROC) $(DOCBOOK_2_MAN_XSL) $<
