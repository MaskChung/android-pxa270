/*
 * Copyright (C) 2007 Apple Inc.  All rights reserved.
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

#include "config.h"
#include "WebNodeHighlight.h"

#include "WebView.h"
#pragma warning(push, 0)
#include <WebCore/Color.h>
#include <WebCore/GraphicsContext.h>
#include <WebCore/InspectorController.h>
#include <WebCore/Page.h>
#include <WebCore/WindowMessageBroadcaster.h>
#pragma warning(pop)
#include <wtf/OwnPtr.h>
#include <wtf/HashSet.h>

using namespace WebCore;

static LPCTSTR kOverlayWindowClassName = TEXT("WebNodeHighlightWindowClass");
static ATOM registerOverlayClass();
static LPCTSTR kWebNodeHighlightPointerProp = TEXT("WebNodeHighlightPointer");

WebNodeHighlight::WebNodeHighlight(WebView* webView)
    : m_inspectedWebView(webView)
    , m_inspectedWebViewWindow(0)
    , m_overlay(0)
    , m_observedWindow(0)
{
}

WebNodeHighlight::~WebNodeHighlight()
{
    if (m_observedWindow)
        WindowMessageBroadcaster::removeListener(m_observedWindow, this);

    if (m_overlay)
        ::DestroyWindow(m_overlay);
}

void WebNodeHighlight::show()
{
    if (!m_overlay) {
        if (FAILED(m_inspectedWebView->viewWindow(reinterpret_cast<OLE_HANDLE*>(&m_inspectedWebViewWindow))) || !IsWindow(m_inspectedWebViewWindow))
            return;

        registerOverlayClass();

        m_overlay = ::CreateWindowEx(WS_EX_LAYERED | WS_EX_TOOLWINDOW, kOverlayWindowClassName, 0, WS_POPUP | WS_VISIBLE,
                                     0, 0, 0, 0,
                                     m_inspectedWebViewWindow, 0, 0, 0);
        if (!m_overlay)
            return;

        ::SetProp(m_overlay, kWebNodeHighlightPointerProp, reinterpret_cast<HANDLE>(this));
        ::SetWindowPos(m_overlay, m_inspectedWebViewWindow, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);

        m_observedWindow = GetAncestor(m_inspectedWebViewWindow, GA_ROOT);
        WindowMessageBroadcaster::addListener(m_observedWindow, this);
    }

    updateWindow();
    ::ShowWindow(m_overlay, SW_SHOW);
}

void WebNodeHighlight::hide()
{
    if (m_overlay)
        ::ShowWindow(m_overlay, SW_HIDE);
}

bool WebNodeHighlight::visible() const
{
    return m_overlay && ::IsWindowVisible(m_overlay);
}

void WebNodeHighlight::updateWindow()
{
    ASSERT(m_overlay);

    HDC hdc = ::CreateCompatibleDC(::GetDC(m_overlay));
    if (!hdc)
        return;

    RECT webViewRect;
    ::GetWindowRect(m_inspectedWebViewWindow, &webViewRect);

    SIZE size;
    size.cx = webViewRect.right - webViewRect.left;
    size.cy = webViewRect.bottom - webViewRect.top;

    BITMAPINFO bitmapInfo;
    bitmapInfo.bmiHeader.biSize          = sizeof(BITMAPINFOHEADER);
    bitmapInfo.bmiHeader.biWidth         = size.cx;
    bitmapInfo.bmiHeader.biHeight        = -size.cy;
    bitmapInfo.bmiHeader.biPlanes        = 1;
    bitmapInfo.bmiHeader.biBitCount      = 32;
    bitmapInfo.bmiHeader.biCompression   = BI_RGB;
    bitmapInfo.bmiHeader.biSizeImage     = 0;
    bitmapInfo.bmiHeader.biXPelsPerMeter = 0;
    bitmapInfo.bmiHeader.biYPelsPerMeter = 0;
    bitmapInfo.bmiHeader.biClrUsed       = 0;
    bitmapInfo.bmiHeader.biClrImportant  = 0;

    void* pixels = 0;
    OwnPtr<HBITMAP> hbmp(::CreateDIBSection(hdc, &bitmapInfo, DIB_RGB_COLORS, &pixels, 0, 0));

    ::SelectObject(hdc, hbmp.get());

    GraphicsContext context(hdc);

    m_inspectedWebView->page()->inspectorController()->drawNodeHighlight(context);

    BLENDFUNCTION bf;
    bf.BlendOp = AC_SRC_OVER;
    bf.BlendFlags = 0;
    bf.SourceConstantAlpha = 255;
    bf.AlphaFormat = AC_SRC_ALPHA;

    POINT srcPoint;
    srcPoint.x = 0;
    srcPoint.y = 0;

    POINT dstPoint;
    dstPoint.x = webViewRect.left;
    dstPoint.y = webViewRect.top;

    ::UpdateLayeredWindow(m_overlay, ::GetDC(0), &dstPoint, &size, hdc, &srcPoint, 0, &bf, ULW_ALPHA);

    ::DeleteDC(hdc);
}

static ATOM registerOverlayClass()
{
    static bool haveRegisteredWindowClass = false;

    if (haveRegisteredWindowClass)
        return true;

    WNDCLASSEX wcex;

    wcex.cbSize = sizeof(WNDCLASSEX);

    wcex.style          = 0;
    wcex.lpfnWndProc    = OverlayWndProc;
    wcex.cbClsExtra     = 0;
    wcex.cbWndExtra     = 0;
    wcex.hInstance      = 0;
    wcex.hIcon          = 0;
    wcex.hCursor        = LoadCursor(0, IDC_ARROW);
    wcex.hbrBackground  = 0;
    wcex.lpszMenuName   = 0;
    wcex.lpszClassName  = kOverlayWindowClassName;
    wcex.hIconSm        = 0;

    haveRegisteredWindowClass = true;

    return ::RegisterClassEx(&wcex);
}

LRESULT CALLBACK OverlayWndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam)
{
    WebNodeHighlight* highlight = reinterpret_cast<WebNodeHighlight*>(::GetProp(hwnd, kWebNodeHighlightPointerProp));
    if (!highlight)
        return ::DefWindowProc(hwnd, msg, wParam, lParam);

    return ::DefWindowProc(hwnd, msg, wParam, lParam);
}

void WebNodeHighlight::windowReceivedMessage(HWND, UINT msg, WPARAM, LPARAM)
{
    switch (msg) {
        case WM_WINDOWPOSCHANGED:
            if (visible())
                updateWindow();
            break;
        default:
            break;
    }
}
