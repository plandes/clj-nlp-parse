## makefile automates the build and deployment for lein projects

# type of project, currently one of: clojure, python
PROJ_TYPE=		clojure

# the test target creates this symlink
ADD_CLEAN=		model

include ../zenbuild/src/mk/env.mk
include $(BUILD_MK_DIR)/model.mk

projinfo:	info modelinfo
	@echo "mlink: $(MLINK)"

.PHONY: test
test:
	ln -s $(ZMODEL) || true
	lein test
