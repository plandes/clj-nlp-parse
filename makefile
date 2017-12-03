## makefile automates the build and deployment for lein projects

# type of project, currently one of: clojure, python
PROJ_TYPE=		clojure
PROJ_MODULES=		model

include $(if $(ZBHOME),$(ZBHOME),../zenbuild)/main.mk
