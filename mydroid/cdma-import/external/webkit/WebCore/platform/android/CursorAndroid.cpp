/*
 * Copyright (C) 2004, 2006 Apple Computer, Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY APPLE COMPUTER, INC. ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL APPLE COMPUTER, INC. OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */

#include "config.h"
#include "Cursor.h"

#define LOG_TAG "WebCore"
#undef LOG
#include "utils/Log.h"

namespace WebCore {

static void notImplemented() { LOGV("Cursor: NotYetImplemented"); }
    
Cursor::Cursor(Image* image, const IntPoint& )
{
    notImplemented();
}

Cursor::Cursor(const Cursor& other)
{
    notImplemented();
}

Cursor::~Cursor()
{
    notImplemented();
}

Cursor& Cursor::operator=(const Cursor& other)
{
    notImplemented();
    return *this;
}

const Cursor& pointerCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& crossCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& handCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& moveCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& iBeamCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& waitCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& helpCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& eastResizeCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& northResizeCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& northEastResizeCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& northWestResizeCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& southResizeCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& southEastResizeCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& southWestResizeCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& westResizeCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& northSouthResizeCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& eastWestResizeCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& northEastSouthWestResizeCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& northWestSouthEastResizeCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& columnResizeCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& rowResizeCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& verticalTextCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& cellCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& contextMenuCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& noDropCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& copyCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& progressCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& aliasCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

const Cursor& noneCursor()
{
    notImplemented();
    static Cursor c;
    return c;
}

}
