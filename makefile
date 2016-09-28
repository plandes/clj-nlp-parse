## makefile automates the build and deployment for lein projects

# edit these if you want
USER=		plandes
#APP_NAME=	
APP_BNAME=	nlparse
#PROJ=		
REMOTE=		github

# shouldn't need to change anything below this point
POM=		pom.xml
MTARG=		target
AS_DIR=		$(MTARG)/appassembler
VER=		$(shell git tag -l | sort -V | tail -1 | sed 's/.//')
VPREF=		$(if $(VER),-$(VER),-0.1.0-SNAPSHOT)
ANRCMD=		head -1 project.clj | sed 's/(defproject \(.*\)\/\(.*\) .*/\2/'
APP_NAME_REF=	$(if $(APP_NAME),$(APP_NAME),$(shell $(ANRCMD)))
APP_BNAME_REF=	$(if $(APP_BNAME),$(APP_BNAME),$(APP_NAME_REF))
LIB_JAR=	$(MTARG)/$(APP_NAME_REF)$(VPREF).jar
PNCMD=		git remote -v | grep $(REMOTE) | grep push | sed 's/.*\/\(.*\).git .*/\1/'
PROJ_REF=	$(if $(PROJ),$(PROJ),$(shell $(PNCMD)))
UBER_JAR=	$(MTARG)/$(APP_NAME_REF)$(VPREF)-standalone.jar
DOC_DIR=	$(MTARG)/doc
ASBIN_DIR=	src/asbin
DIST_DIR=	$(HOME)/Desktop/$(APP_NAME_REF)
DIST_BIN_DIR=	$(DIST_DIR)/bin

all:		info

.PHONEY:
compile:	$(LIB_JAR)

.PHONEY:
install:
	lein install

.PHONEY:
uber:		$(UBER_JAR)

.PHONEY:
dist:		$(DIST_BIN_DIR)

.PHONEY:
deploy:
	lein deploy clojars

.PHONEY:
run:
	lein run

.PHONEY:
info:
	@echo "version: $(VER)"
	@echo "project: $(PROJ_REF)"
	@echo "jar: $(LIB_JAR)"
	@echo "uberhar: $(UBER_JAR)"
	@echo "app script name: $(APP_BNAME_REF)"

$(LIB_JAR):
	@echo compiling $(LIB_JAR)
	lein with-profile +appasem jar

$(UBER_JAR):
	@echo compiling $(UBER_JAR)
	lein with-profile +uberjar uberjar

$(POM):
	lein pom

$(AS_DIR):	$(LIB_JAR) $(POM)
	mvn package appassembler:assemble

$(DIST_BIN_DIR):	$(AS_DIR)
	mkdir -p $(DIST_DIR)
	cp -r target/appassembler/* $(DIST_DIR)
	[ -d $(ASBIN_DIR) ] && cp -r $(ASBIN_DIR)/* $(DIST_BIN_DIR) || true
	chmod 0755 $(DIST_BIN_DIR)/$(APP_BNAME_REF)

.PHONEY:
docs:		$(DOC_DIR)

# https://github.com/weavejester/codox/wiki/Deploying-to-GitHub-Pages
$(DOC_DIR):
	rm -rf $(DOC_DIR) && mkdir -p $(DOC_DIR)
	git clone https://github.com/$(USER)/$(PROJ_REF).git $(DOC_DIR)
	git update-ref -d refs/heads/gh-pages 
	git push $(REMOTE) --mirror
	( cd $(DOC_DIR) ; \
	  git symbolic-ref HEAD refs/heads/gh-pages ; \
	  rm .git/index ; \
	  git clean -fdx )
	lein codox

.PHONEY:
pushdocs:	$(DOC_DIR)
	( cd $(DOC_DIR) ; \
	  git add . ; \
	  git commit -am "new doc push" ; \
	  git push -u origin gh-pages )

.PHONEY:
cleandist:
	@echo "removing $(DIST_DIR)..."
	rm -fr $(DIST_DIR)

.PHONEY:
clean:
	rm -fr $(POM)* target dev-resources src/clojure/$(APP_NAME_REF)/version.clj
	rmdir test 2>/dev/null || true

.PHONEY:
cleanall:	clean cleandist
