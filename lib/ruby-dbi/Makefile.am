VERSION = 0.1.1
RELEASE = 2

ARCH = noarch

SRC_DIR = $(srcdir)/src
SRC_TARBALL = dbi-$(VERSION).tar.gz
SRC_PATCH = dbi-0.1.0_install.patch
SPEC_FILE = ruby-dbi.spec

RPM = $(DEST_RPM)/ruby-dbi-$(VERSION)-$(RELEASE).$(ARCH).rpm

all : PRINT_HEADER $(RPM) PRINT_FOOTER

$(RPM) : build-rpms
	mv @RPMBUILD_TOPDIR@/RPMS/$(ARCH)/`basename $@` $@

.PHONY: build-rpms
build-rpms : 
	cp $(SRC_DIR)/$(SRC_TARBALL) @RPMBUILD_TOPDIR@/SOURCES/
	cp $(SRC_DIR)/$(SRC_PATCH) @RPMBUILD_TOPDIR@/SOURCES/
	cp $(SRC_DIR)/$(SPEC_FILE) @RPMBUILD_TOPDIR@/SPECS/
	rpmbuild -ba @RPMBUILD_TOPDIR@/SPECS/$(SPEC_FILE)

PRINT_HEADER :
	@printf "\n================================================================================\n"
	@printf "Building Package:\n"
	@printf "\tNAME = %s\n" "$(PACKAGE)"
	@printf "\tVERSION = %s\n" "$(VERSION)"
	@printf "\tRELEASE = %s\n" "$(RELEASE)"
	@printf "\tARCH = %s\n" "$(ARCH)"
	@printf "\tStarted building of %s at %s\n" "$(PACKAGE)" "$(shell date)"
	@printf "================================================================================\n"

PRINT_FOOTER :
	@printf "\n================================================================================\n"
	@printf "Finished Building Package %s at %s\n" "$(PACKAGE)" "$(shell date)"
	@printf "\tBinary RPMS can be found under %s\n" "@RPMBUILD_TOPDIR@"
	@printf "================================================================================\n"
