#!/usr/bin/env python
#
# Scan a mbox of CVS commit Mailinglist and display the
# patches (including the patch --dry-run messages), 
# convert CVS users to real names, create patchscripts
# to apply to the mtd git repository ....
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
import difflib
import email
import mailbox
import re
import string
from Tkinter import *

# The script should be invoked from mtd cvs root
mtdusers = "scripts/mtdusers.txt"
# Should be a commandline argument
mtdrepo = "~/work/git-repos/mtd-2.6"
userhash = {}

# Make this configurable !!
mysign = ["Thomas Gleixner", "tglx@linutronix.de"]

# Fix me: read this from the treewalk/patchin xml config
includepaths = ["drivers", "fs", "include"]
excludepatterns = ["jffs3","ecos"]

# Skip unknown (new) files
skipunknown = 0

# Regex to retrieve real author from signed off line
re_author = re.compile(r"""^Signed-.ff-.y: (.*) <(.*)>""")

# Print the usage information
def usage(res):
    print "USAGE:"
    print "creatediffs.py <-m mtdusers> mailbox mailnr"
    print "-m mtdusers    supply a list of mtd users (default:scripts/mtdusers.txt)"
    print "-r repopath    path to mtd git repository"
    print "mailbox containing MTD CVS Mailinglist"

#    print "targetdir to put the diffs and logs"
    sys.exit(res)

# Helper to run a shell command
def doShellCommand(cmd, data=None):
    (inp, out, err) = os.popen3(cmd)
    if data:
        inp.write(data)
    inp.close()
    res = out.read()
    out.close()
    res = res + "\n" + err.read()
    err.close()
    return res

# The commit class
class commitView:

    def __init__(self, root, mbox):
        self.root = root
        fd = open(mbox,"r")
        mbx = mailbox.PortableUnixMailbox(fd, email.message_from_file)
        #mbx = mailbox.Maildir(mbox, email.message_from_file)
        self.entries= []
        msg = mbx.next()
        while msg:
            self.entries.append(msg)
            msg = mbx.next()
        self.entries.reverse()
        fd.close()    
        f1 = Frame(root)
        f1.pack()
        f2 = Frame(root)
        f2.pack()
        f3 = Frame(root)
        f3.pack()
        f4 = Frame(root)
        f4.pack()
        f5 = Frame(root)
        f5.pack()
        self.subject = StringVar()
        self.user = StringVar()
        self.realuser = StringVar()
        self.date = StringVar()
        Button(f1, text="Back", command=self.back).grid(row=0,column=0)
        Button(f1, text="Next", command=self.next).grid(row=0,column=1)
        Button(f1, text="Apply", command=self.apply).grid(row=0,column=2)
        self.signAuthor = IntVar()
        Checkbutton(f1, text="Add Author Signed-off", variable=self.signAuthor).grid(row=0,column=3)
        self.signOwn = IntVar()
        Checkbutton(f1, text="Add own Signed-off", variable=self.signOwn).grid(row=0,column=4)
        self.authSignOff = IntVar()
        Checkbutton(f1, text="Get author from Signed-off", variable=self.authSignOff).grid(row=0,column=5)

        Label(f2, text="What").grid(row=0, column=0, sticky=W)
        Label(f2, textvariable = self.subject, justify=LEFT, relief=SUNKEN).grid(row=0, column=1, sticky=W)
        Label(f2, text="User").grid(row=1, column=0, sticky=W)
        Label(f2, textvariable = self.user, justify=LEFT, relief=SUNKEN).grid(row=1, column=1, sticky=W)
        Label(f2, text="Real user").grid(row=1, column=2, sticky=W)
        Label(f2, textvariable = self.realuser, justify=LEFT, relief=SUNKEN).grid(row=1, column=3, sticky=W)
        Label(f2, text="Date").grid(row=2, column=0, sticky=W)
        Label(f2, textvariable = self.date, justify=LEFT, relief=SUNKEN).grid(row=2, column=1, sticky=W)
        self.log = Text(f3)
        self.patch = Text(f4)
        self.patchres = Text(f5)

        self.curnr = -1
        self.next()

    def getMsg(self):
        self.log.delete(1.0,END)
        self.patch.delete(1.0,END)
        self.patchres.delete(1.0,END)
        print self.curnr
        msg = self.entries[self.curnr]

        subj = msg.get("Subject")
        print subj
        files = subj.split(" ", 1)
        self.patchpath = files[0].split("/",1)[1]
        self.files = files[1]
        print self.patchpath
        for p in excludepatterns:
            if subj.find(p) > 0:
                return 1

        valid = 0
        for p in includepaths:
            if self.patchpath.startswith(p):
                valid = 1
                break
        if valid == 0:
            return 1
        
        self.subject.set(subj)
        usr = msg.get("From")
        self.user.set(usr)
        if usr.find("<") > 0:
            usr = usr.split("<")[1][:-1]
        print usr
        self.usr = userhash.get(usr.split("@")[0],"???")
        self.realuser.set("%s %s" %(self.usr[0],self.usr[1]))
        self.cdate = msg.get("Date")
        self.date.set(self.cdate)
        body = msg.get_payload().splitlines()
        while len(body):
            line = body.pop(0).strip()
            if line.startswith("Log Message:"):
                break
        self.logmsg = ""
        while len(body):
            line = body.pop(0)
            if line.startswith("Index:"):
                break
            self.logmsg = self.logmsg + line + "\n"
        self.log.insert(END, self.logmsg)
        self.log.pack()
        self.patchmsg = ""
        while len(body):
            self.patchmsg = self.patchmsg + \
            body.pop(0).replace("Makefile.common", "Makefile") + "\n"
        self.patch.insert(END, self.patchmsg)
        self.patch.pack()

        # Try to apply the patch and display the results
        cmd = "patch -d %s/%s -p0 -f --verbose --dry-run" %(mtdrepo,self.patchpath)
        print cmd
        self.pres = doShellCommand(cmd, self.patchmsg)
        self.patchres.insert(END, self.pres)
        self.patchres.pack()
        return 0
        
    def apply(self):
        fd = open("%s/patchfromcvs.sh" %(mtdrepo), "w")
        files = self.files.strip()
        while len(files) > 0:
            try:
                fname, files = files.strip().split(",", 1)
                frev1, files = files.strip().split(",", 1)
                files = files.strip()
                if files.find(" ") > 0:
                    frev2, files = files.strip().split(" ", 1)
                else:
                    frev2 = files
                    files = ""
            except:
                break

            # Skip 2.4 files
            if fname.find("-v24") >= 0:
                continue
            if fname == "Makefile.24" >= 0:
                continue
            # Skip Makefile magic files
            if fname == "Makefile" or fname == "Makefile.inc":
                continue
            # Skip unknown (new) files ?
            if skipunknown:
                if not os.path.isdir("%s/%s" %(mtdrepo, self.patchpath)):
                    continue
                if fname != "Makefile.common":
                    if not os.path.isfile("%s/%s/%s" %(mtdrepo,
                                                       self.patchpath, fname)):
                        continue

            if frev1 == "NONE":
                fd.write("pushd %s\n" %(os.getcwd()))
                if fname == "Makefile.common":
                    fnamedest = "Makefile"
                else:
                    fnamedest = fname
                fd.write("cvs up -p -r%s %s/%s >%s/%s/%s\n" %(
                    frev2 ,self.patchpath, fname, mtdrepo, self.patchpath, fnamedest))
                fd.write("popd\n")
                fd.write("git add %s/%s\n" %(self.patchpath, fnamedest))
            elif frev2 == "NONE":
                if fname == "Makefile.common":
                    fname = "Makefile"
		fd.write("git-update-index --remove %s/%s\n" %(self.patchpath, fname))
            else:
                fd.write("pushd %s\n" %(os.getcwd()))
                fd.write("cvs diff -u -r%s -r%s %s/%s >%s/cvspatch\n" %(
                    frev1, frev2, self.patchpath, fname, mtdrepo))
                fd.write("popd\n")
                if fname == "Makefile.common":
                    fname = "Makefile"
                    fd.write("sed -i s@/Makefile.common@/Makefile@ cvspatch\n")
                fd.write("patch -p0 <cvspatch\n")
                fd.write("rm -f cvspatch\n")
		fd.write("git-update-index %s/%s\n" %(self.patchpath, fname))

        fd.write("find -iname *.rej\n")
        fd.write("find -iname *.orig\n")
        fd.write("git status\n")
        fd.close()
        
        fd = open(mtdrepo+"/logmessage", "w")
        lines = self.log.get(1.0,END).splitlines()
        # Strip trailing newlines
        while len(lines) and len(lines[-1].strip()) == 0:
            lines.pop(-1)
        for line in lines:
            fd.write(line+"\n")
        if len(lines) > 0 and lines[-1].find("Signed-") < 0:
            fd.write("\n")

        if self.authSignOff:
            for line in lines:
                try:
                    ainfo = re_author.match(line)
                    name = ainfo.group(1)
                    mail = ainfo.group(2)
                    self.usr[0] = name
                    self.usr[1] = mail
                    break;
                except Exception, ex:
                    pass

        if self.signAuthor.get() == 1:
            fd.write("Signed-off-by: %s <%s>\n" %(self.usr[0], self.usr[1]))
        if self.signOwn.get() == 1:
            fd.write("Signed-off-by: %s <%s>\n" %(mysign[0], mysign[1]))
        fd.close()

        fd = open(mtdrepo + "/commit.sh", "w")
        fd.write('#!/bin/sh\n')
        fd.write('export GIT_AUTHOR_NAME="%s"\n' %(self.usr[0]))
        fd.write('export GIT_AUTHOR_EMAIL="%s"\n' %(self.usr[1]))
        fd.write('export GIT_AUTHOR_DATE="%s"\n' %(self.cdate))
	fd.write('\ngit commit -v -F logmessage\n')
        fd.close()

        self.next()
        
    def next(self):
        if self.curnr == len(self.entries) - 1:
            return
        self.curnr = self.curnr + 1
        while self.getMsg() > 0:
            self.curnr = self.curnr + 1

    def back(self):
        if self.curnr == 0:
            return
        self.curnr = self.curnr - 1
        while self.getMsg() > 0:
            self.curnr = self.curnr - 1
        

# Here we go
# Parse the commandline
try:
    (options, arguments) = getopt.getopt(sys.argv[1:],'m:r:s')
except getopt.GetoptError, ex:
    print
    print "ERROR:"
    print ex.msg
    usage(1)

for option, value in options:
    if option == "-m":
        mtdusers = value
    elif option == "-r":
        mtdrepo = value
    elif option == "-s":
        skipunknown = 1
    elif option == '-h':
        usage(0)

if len(arguments) != 1:
    usage(1)

# Get filenames
mbox = arguments[0]

# Read the user list
fd = open(mtdusers,"r")
lines = fd.readlines()
fd.close()
for line in lines:
    if line.startswith("#"):
    	continue
    if len(line) > 0:
        usr,name,mail=line.strip().split(":")
        userhash[usr] = [name,mail]

root = Tk()

viewer = commitView(root, mbox)
root.mainloop()

