#!/usr/bin/env python
#
# XML configurable tree walking 
#  
# See treewall.xml for examples and XML entities doc
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

# Configuration file
config_file = "treewalk.xml"
# Interactive mode
interactive = 0
# Verbose
verbose = 0

# Commandline options
cmdline_actions = []
cmdline_actdict = {}

# Configuration file options with help texts
config_dict = {}

# Actions
actions = {}
default_actionorder = []
default_actions = []

# Subdirectories and subconfiguration
subdirs = []

# Target directory validation check
targetchecks = []

# Print the usage information
def usage(res):
	print "USAGE:"
	print "treewalk.py <-f config -h -i -a actions -v path>"
	print "  -a actions actions depending on config file seperated by ','"
	print "  -f config  configuration file. Default is treewalk.xml"
	print "  -i         interactive mode"
	print "  -v lvl     verbosity level"

	if len(config_dict):
		print "Valid options:"
		for cfg in config_dict.keys():
			print cfg + "\t" + config_dict[cfg]
	sys.exit(res)

def verbose_print(lvl, line):
	if verbose >= lvl:
		print line

# Except/depends storage class
class treeApplies:
	def __init__(self, excepts, depends):
		if excepts:
			self.excepts = excepts.split(',')
		else:
			self.excepts = []
		if depends:
			self.depends = depends.split(',')
		else:
			self.depends = []

	def validateOptions(self,dict):
		for e in self.excepts:
			if dict.has_key(e):
				return 0
			
		for e in self.depends:
			if not dict.has_key(e):
				return 0
		return 1

# Action storage class
class treeAction:
	def __init__(self, name, action, excepts, depends, mode):

		self.name = name
		self.action = action
		self.applies = treeApplies(excepts, depends)
		self.mode = (mode and mode == 'all')
		self.active = 1
		return

	# Validate the commandline options with except/depend
	def validateOptions(self,options):
		self.active = self.applies.validateOptions(options)
		return self.active

	def getName(self):
		return self.name
	
	# Set the action
	def setAction(self, action):
		self.action = action.replace("\n","")

	# Is action active ?
	def isActive(self):
		return self.active
			
	# Get the action for a subdirectory
	def getAction(self, subdir):
		return self.action

	# Get the mode configuration
	def getMode(self):
		return self.mode

	# Run the action
	def run(self, cmd):
		retval = commands.getstatusoutput(cmd)
		if retval[0] != 0:
			sys.stderr.write("Error processing command\n")
			sys.stderr.write(cmd)
			sys.stderr.write('\n')
			sys.stderr.write(retval[1])
			sys.stderr.write('\n')
			sys.exit(1)

	# Process the action
	def process(self, srcpath, dstpath, srcfiles, dstfiles, options):
		cmd = "export srcpath=\'%s\';" %(srcpath)
		cmd = cmd + "export dstpath=\'%s\';" %(dstpath)
		cmd = cmd + "export options=\'%s\';" %(options)
		cmd = cmd + "export srcbase=\'%s\';" %(srcbase)
		cmd = cmd + "export dstbase=\'%s\';" %(dstbase)
		relpath = os.path.join(srcpath.replace(srcbase,""))
		cmd = cmd + "export srcrelpath=\'%s\';" %(relpath)
		relpath = os.path.join(dstpath.replace(dstbase,""))
		cmd = cmd + "export dstrelpath=\'%s\';" %(relpath)
		# All files mode ?
		if self.mode:
			cmd = cmd + "export srcfiles=\'"
			for file in srcfiles:
				cmd = cmd + " %s" %(file)
			cmd = cmd + "\';"
			cmd = cmd + "export dstfiles=\'"
			for file in dstfiles:
				cmd = cmd + " %s" %(file)
			cmd = cmd + "\';"
 			cmd = cmd + self.action
			self.run(cmd)
			return
		cnt = 0
		while cnt < len(srcfiles):
			fcmd = cmd + "export srcfiles=\'%s\';" %(srcfiles[cnt])
			fcmd = fcmd + "export dstfiles=\'%s\';" %(dstfiles[cnt])
 			fcmd = fcmd + self.action
			self.run(fcmd)
			cnt = cnt + 1

# Subdirs storage class
class treeSubdir:
	def __init__(self, name, excepts, depends, actions, recurse):
		self.name = name
		self.applies = treeApplies(excepts, depends)
		self.active = 1
		self.update = []
		self.exclude = []
		if actions:
			self.actions = actions.split(',')
		else:
			self.actions = []
		self.recurse = (recurse and recurse == 'yes')

	# Validate the commandline options with except/depend
	def validateOptions(self,options):
		self.active = self.applies.validateOptions(options)
		for pat in self.update:
			pat.validateOptions(options)
		for pat in self.exclude:
			pat.validateOptions(options)
		return self.active

	# Add an update pattern
	def addUpdate(self,pattern):
		self.update[len(self.update):] = [pattern]

	# Add an exclude pattern
	def addExclude(self,pattern):
		self.exclude[len(self.exclude):] = [pattern]

	# Get update patterns
	def getUpdates(self):
		return self.update

	# Get exclude patterns
	def getExcludes(self):
		return self.exclude

	# Get actions
	def getActions(self):
		return self.actions

	# Is dir active ?
	def isActive(self):
		return self.active

	# Get the directory name
	def getDirname(self):
		return self.name

	# Get recursion info
	def isRecursive(self):
		return self.recurse

# Storage class for file patterns
class treePattern:
	def __init__(self, pattern, target, excepts, depends, options):
		self.pattern = re.compile(pattern)
		self.target = target
		self.applies = treeApplies(excepts, depends)
		self.options = options
		self.active = 1

	# Validate the commandline options with except/depend
	def validateOptions(self,options):
		self.active = self.applies.validateOptions(options)
		return self.active

	# Update pattern active ?
	def isActive(self):
		return self.active

	# Get update pattern
	def getPattern(self):
		return self.pattern

	# Get target pattern
	def getTarget(self):
		return self.target

	# Get options
	def getOptions(self):
		return self.options

# configuration parser
class docHandler(xml.sax.ContentHandler):

	def __init__(self):
		self.subdir = -1
		self.action = ""
		return
    
	def startElement(self, name, attrs):
		if name == "OPTION":
			config_dict[attrs.get('name')] = attrs.get('help')

		elif name == "ACTION":
			act = attrs.get('name')
			self.action = act
			actions[act] = treeAction(self.action,
						    "",
						    attrs.get('except'),
						    attrs.get('depends'),
						    attrs.get('mode'))
		elif name == "ACTIONORDER":
			globals()['default_actionorder'] = attrs.get('order').split(',')
			
		elif name == "SUBDIR":
			i = len(subdirs)
			subdirs[i:] = [treeSubdir(attrs.get('name'),
						  attrs.get('except'),
						  attrs.get('depends'),
						  attrs.get('actions'),
						  attrs.get('recurse'))]
			self.subdir = i
			
		elif name == 'UPDATE':
			i = self.subdir
			subdirs[i].addUpdate(
				treePattern(attrs.get('pattern'),
					     attrs.get('target'),
					     attrs.get('except'),
					     attrs.get('depends'),
					     attrs.get('options')))
					     
		elif name == 'EXCLUDE':
			i = self.subdir
			subdirs[i].addExclude(
				treePattern(attrs.get('pattern'),
					     attrs.get('target'),
					     attrs.get('except'),
					     attrs.get('depends'),
					     attrs.get('options')))
		elif name == 'CHECKTARGET':
			i = len(targetchecks)
			targetchecks[i:] = [
				treePattern(attrs.get('pattern'),
					     attrs.get('file'),
					     attrs.get('except'),
					     attrs.get('depends'),
					     attrs.get('help'))]

		self.element = name
		self.content = ""

	def characters(self, ch):
		self.content = self.content + ch

	def endElement(self, name):
		if name == 'OPTION':
			return
		
		elif name == "ACTION":
			actions[self.action].setAction(self.content)
			
		elif name == 'SUBDIR':
			self.subdir = -1

# error handler
class errHandler(xml.sax.ErrorHandler):
	def __init__(self):
		return

	def error(self, exception):
		sys.stderr.write("%s\n" % exception)

	def fatalError(self, exception):
		sys.stderr.write("Fatal error while parsing configuration\n")
		sys.stderr.write("%s\n" % exception)
		sys.exit(1)

# parse the configuration file
def parseConfig(file):
	# handlers
	dh = docHandler()
	eh = errHandler()

	# Create an XML parser
	parser = xml.sax.make_parser()

	# Set the handlers
	parser.setContentHandler(dh)
	parser.setErrorHandler(eh)

	fd = open(file, 'r')

	# Parse the file
	parser.parse(fd)
	fd.close()

# Process actions
def processDiractions(dir, srcpath, dstpath, diractions, srcfiles):

	# Scan the update sections
	for upd in dir.getUpdates():
		# skip inactive update rules
		if upd.isActive() == 0:
			continue
		update = []
		src_update = []
		dst_update = []

		# Select files by selection pattern
		for file in srcfiles:
			if upd.getPattern().match(file):
				update[len(update):] = [file]

		# Process excludes
		for file in update:
			try:
				for excl in dir.getExcludes():
					# skip inactive rules
					if excl.isActive() == 0:
						continue
					# Check exclude pattern
					if excl.getPattern().match(file):
						raise exception(0)
				src_update[len(src_update):] = [file]
			except:
				continue

		# Updates available ?	
		if len(src_update) == 0:
			continue
		
		# Create dest filenames
		repl = upd.getTarget() 
		for file in src_update:
			if repl:
				dstfile = upd.getPattern().sub(repl,file)
			else:
				dstfile = file
			verbose_print(2, dstfile)
			dst_update[len(dst_update):] = [dstfile]

		# Process the actions
		for action in diractions:
			verbose_print(2, "Action: %s" %(action.getName()))
			action.process(srcpath,
				       dstpath,
				       src_update,
				       dst_update,
				       upd.options)


# Process subdirectory
def processSubdir(dir, srcbasedir, dstbasedir):
	
	# Skip inactive subdirs
	if dir.isActive() == 0:
		return

	# Check valid actions
	diractions = []
	if dir.getActions():
		for act in dir.getActions():
			if not actions.has_key(act):
				continue
			if actions[act].isActive() > 0:
				diractions[len(diractions):] = [actions[act]]
	else:
		diractions = default_actions

	if len(diractions) == 0:
		return
	
	# Set the pathnames
	srcpath = os.path.join(srcbasedir, dir.getDirname())
	dstpath = os.path.join(dstbasedir, dir.getDirname())
	# Read files in source directory
	srcfiles = os.listdir(srcpath)

	verbose_print(1, "Processing %s" %(srcpath))

	processDiractions(dir, srcpath, dstpath, diractions, srcfiles)

	# Handle directory recursive ?
	if not dir.isRecursive():
		return

	dirname = dir.name

	for file in srcfiles:
		if not os.path.isdir(os.path.join(srcpath,file)):
			continue
		dir.name = os.path.join(dirname, file)
		processSubdir(dir, srcbasedir, dstbasedir)
		
# Here we go
# Parse the commandline
try:
	(options, arguments) = getopt.getopt(sys.argv[1:],'a:f:hiv:')
except getopt.GetoptError, ex:
	print
	print "ERROR:"
	print ex.msg
	usage(1)

for option, value in options:
	if option == "-f":
		config_file = value

	elif option == "-a":
		cmdline_actions = value.split(',')
	
	elif option == '-i':
		interactive = 1

	elif option == '-v':
		if value:
			verbose = int(value)
		else:
			verbose = 1

	elif option == '-h':
		usage(0)

# Read the configuration
parseConfig(config_file)

for act in cmdline_actions:
	if len(act) == 0:
		continue
	if not config_dict.has_key(act):
		print "Invalid action: " + act
		print 
		usage(1)
	cmdline_actdict[act] = act


# Validate actions against depends and except
for action in actions.keys():
	actions[action].validateOptions(cmdline_actdict)

# Build default actions list
#  - order by configured actionsorder
for action in default_actionorder:
	if not actions.has_key(action):
		continue
	if not actions[action].isActive():
		continue
	default_actions[len(default_actions):] = [actions[action]]


for dir in subdirs:
	dir.validateOptions(cmdline_actdict)

if len(arguments) != 1:
	usage(1)

# Get current directory
srcbase = os.getcwd()
dstbase = arguments[0]

# Do target checks
for check in targetchecks:
	try:
		fd = open(os.path.join(dstbase,check.getTarget()))
		lines = fd.readlines()
		fd.close()
		found = 0
		for line in lines:
			if check.getPattern().search(line):
				found = 1
				break;
		if found == 0:
			raise exception(found)
	except:		
		print
		print "Target-Directory check failed: " + check.getOptions()
		sys.exit(1)
		
# Ok, the target seems to be what we are looking for
# Now process the subdirectory entries
for dir in subdirs:
	processSubdir(dir, srcbase, dstbase)
		
