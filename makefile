## makefile automates the build and deployment for lein projects

# edit these if you want
APP_SCR_NAME=	nlparse

# location of the http://github.com/plandes/clj-zenbuild cloned directory
ZBHOME=		../clj-zenbuild

# where the stanford model files are located
#ZMODEL=	$(HOME)/opt/nlp/model

# clean the generated app assembly file
MLINK=		~/opt/nlp/model
ADD_CLEAN+=	$(ASBIN_DIR) model

all:		info

include $(ZBHOME)/src/mk/compile.mk
include $(ZBHOME)/src/mk/dist.mk

.PHONY: test
test:
	ln -s $(MLINK) || true
	lein test
