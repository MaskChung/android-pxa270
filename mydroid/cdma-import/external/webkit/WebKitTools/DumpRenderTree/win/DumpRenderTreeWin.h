/*
 * Copyright (C) 2006, 2007 Apple Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1.  Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer. 
 * 2.  Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution. 
 * 3.  Neither the name of Apple Computer, Inc. ("Apple") nor the names of
 *     its contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission. 
 *
 * THIS SOFTWARE IS PROVIDED BY APPLE AND ITS CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL APPLE OR ITS CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef DumpRenderTreeWin_h
#define DumpRenderTreeWin_h

#undef _WIN32_WINNT
#define _WIN32_WINNT 0x0500

#undef WINVER
#define WINVER 0x0500

// If we don't define these, they get defined in windef.h. 
// We want to use std::min and std::max
#undef max
#define max max
#undef min
#define min min

#undef _WINSOCKAPI_
#define _WINSOCKAPI_ // Prevent inclusion of winsock.h in windows.h

// FIXME: we should add a config.h file for DumpRenderTree.
#define WTF_PLATFORM_CF 1

struct IWebFrame;
struct IWebPolicyDelegate;
struct IWebView;
typedef const struct __CFString* CFStringRef;
typedef struct HWND__* HWND;

extern IWebFrame* topLoadingFrame;
extern IWebFrame* frame;
extern IWebPolicyDelegate* policyDelegate;

extern HWND webViewWindow;

#include <string>
#include <wtf/HashMap.h>
#include <wtf/Vector.h>

std::wstring urlSuitableForTestResult(const std::wstring& url);
IWebView* createWebViewAndOffscreenWindow(HWND* webViewWindow = 0);
Vector<HWND>& openWindows();
HashMap<HWND, IWebView*>& windowToWebViewMap();

void setPersistentUserStyleSheetLocation(CFStringRef);

#endif // DumpRenderTreeWin_h
