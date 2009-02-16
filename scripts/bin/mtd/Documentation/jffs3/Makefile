all: pdf

pdf_files: pics/btree-01.pdf pics/btree-02.pdf pics/comprex-01.pdf \
	   pics/comprex-02.pdf pics/dataobj-01.pdf pics/flash-01.pdf \
	   pics/idxprobl.pdf pics/journal-01.pdf pics/journal-02.pdf \
	   pics/keyex-01.pdf pics/node-01.pdf pics/sb-01.pdf \
	   pics/sb-02.pdf pics/trivkey-01.pdf pics/wandtree.pdf \
	   pics/gcprobl-01.pdf

png_files: pics/btree-01.png pics/btree-02.png pics/comprex-01.png \
	   pics/comprex-02.png pics/dataobj-01.png pics/flash-01.png \
	   pics/idxprobl.png pics/journal-01.png pics/journal-02.png \
	   pics/keyex-01.png pics/node-01.png pics/sb-01.png \
	   pics/sb-02.png pics/trivkey-01.png pics/wandtree.png \
	   pics/gcprobl-01.png


tex_files: JFFS3design.tex


pdf: pdf_files tex_files
	pdflatex JFFS3design.tex

html: png_files tex_files
	latex2html JFFS3design.tex

clean:
	rm -rf *.aux *.log *.out *.bak 	*.pdf *.toc JFFS3design
