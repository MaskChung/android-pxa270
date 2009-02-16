#!/usr/bin/env python
#
# Modify a file. Insert part of a file into another file.
# Patterns are used to find the place where to insert / replace
# 
# (C) 2005 Thomas Gleixner <tglx@linutronix.de>
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License version 2 as
# published by the Free Software Foundation.
#

import os
import sys
import getopt
import pprint
import shutil
import string
import smtplib
import socket
import time
import xml.sax
import commands
import re

# patterns
patterns = []

# insert mode
insert = 0

# Print the usage information
def usage(res):
	print "USAGE:"
	print "modify.py <-o options srcfile dstfile>"
	print "  -i insert if srcpattern not exists, otherwise do nothing"
	print "  -p search patterns seperated by ','"
	print "     srcstart,srcend,dststart,dstend"
	sys.exit(res)

def findLines(lines, start, end):
	found = 0
	startline = -1
	linecnt = 0
	
	for line in lines:
		linecnt = linecnt + 1
		if startline < 0:
			if line.find(start) < 0:
				continue
			startline = linecnt

		if line.find(end) >= 0:
			break

	# end of files
	if linecnt == len(lines):
		linecnt = linecnt + 1
		
	return startline - 1, linecnt - 1	

# Here we go
# Parse the commandline
try:
	(options, arguments) = getopt.getopt(sys.argv[1:],'hip:')
except getopt.GetoptError, ex:
	print
	print "ERROR:"
	print ex.msg
	usage(1)

for option, value in options:
	if option == "-i":
		insert = 1
	elif option == "-p":
		patterns = value.split(',')
	elif option == '-h':
		usage(0)

if len(arguments) != 2:
	usage(1)

# Get filenames
srcfile = arguments[0]
dstfile = arguments[1]

if not os.path.isfile(srcfile):
	print "%s does not exist" %(srcfile)
	sys.exit(1)

if not os.path.isfile(dstfile):
	print "%s does not exist" %(dstfile)
	sys.exit(1)

fd = open(srcfile, 'r')
srclines = fd.readlines()
fd.close()
fd = open(dstfile, 'r')
dstlines = fd.readlines()
fd.close()

# Find source file pattern
(srcstart, srcend) = findLines(srclines,
			       patterns[0], patterns[1])
if srcstart < 0:
	print "Search pattern %s in %s does not exist" %(patterns[0], srcfile)
	sys.exit(1)

# Insert mode. Check whether the section exists or not
if insert > 0:
	(dststart, dstend) = findLines(dstlines,
			       patterns[0], patterns[1])
	if dststart >= 0:
		sys.exit(0)

# Find target file pattern
(dststart, dstend) = findLines(dstlines,
			       patterns[2], patterns[3])

if dststart < 0:
	if insert == 1:
		print "Search pattern %s in %s does not exist" %(patterns[2], dstfile)
	sys.exit(insert)

# Insert / modify the sections
if dststart == 0:
	dststart = 1
newlines = dstlines[0:dststart]
if srcstart == srcend:
	srcend = srcend + 1
newlines = newlines + srclines[srcstart:srcend]
newlines = newlines + dstlines[dstend:]

# write back into destination file
fd = open(dstfile, 'w')
dstlines = fd.writelines(newlines)
fd.close()
