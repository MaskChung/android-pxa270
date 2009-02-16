#
CONV 	= "./html.py"

TARGETS	= index.html archive.html mail.html source.html fellows.html
SUBDIRS = doc faq


all: $(TARGETS) subdirs

$(TARGETS): %.html: %.xml inc/*.tmpl menu1.xml menu2.xml
	$(CONV) -f $<

.PHONY: subdirs $(SUBDIRS)
subdirs: $(SUBDIRS)
$(SUBDIRS):
	$(MAKE) -C $@

clean:
	rm -f $(TARGETS)
	for dir in $(SUBDIRS); do make -C $$dir clean; done

