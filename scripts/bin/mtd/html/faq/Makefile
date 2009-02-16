#
CONV 	= "../html.py"

TARGETS	= general.html jffs2.html jffs3.html nand.html

all: $(TARGETS)

$(TARGETS): %.html: %.xml ../inc/*.tmpl ../menu1.xml menu2.xml
	$(CONV) -f $<

clean:
	rm -f $(TARGETS)
