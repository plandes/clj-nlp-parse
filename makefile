## makefile automates the build and deployment for lein projects

# location of the http://github.com/plandes/clj-zenbuild cloned directory
ZBHOME ?=	../clj-zenbuild

# clean the generated app assembly file
MLINK ?=	$(HOME)/opt/nlp/model
ADD_CLEAN +=	model

all:		info

include $(ZBHOME)/src/mk/compile.mk

.PHONY: test
test:
	ln -s $(MLINK) || true
	lein test
