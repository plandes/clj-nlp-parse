## makefile automates the build and deployment for lein projects

# edit these if you want
APP_SCR_NAME=	nlparse

# location of the http://github.com/plandes/clj-zenbuild cloned directory
ZBHOME=		../clj-zenbuild

all:		info

include $(ZBHOME)/src/mk/compile.mk

.PHONEY:
prepare-dist:
	mkdir src/asbin
	echo 'JAVA_OPTS="-Dzensols.model=$(HOME)/opt/nlp/model"' > src/asbin/setupenv

.PHONEY:
clean-prepare-dist:
	rm -rf src/asbin
