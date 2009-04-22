/*
 * Copyright (C) 2007 Alp Toker <alp@atoker.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; see the file COPYING.LIB.  If not, write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301, USA.
 */

#include "WorkQueueItem.h"
#include "DumpRenderTree.h"

#include <JavaScriptCore/JSStringRef.h>
#include <webkit/webkit.h>

// Returns a newly allocated UTF-8 character buffer which must be freed with g_free()
static gchar* JSStringCopyUTF8CString(JSStringRef jsString)
{
    size_t dataSize = JSStringGetMaximumUTF8CStringSize(jsString);
    gchar* utf8 = (gchar*)g_malloc(dataSize);
    JSStringGetUTF8CString(jsString, utf8, dataSize);

    return utf8;
}

void LoadItem::invoke() const
{
    gchar* targetString = JSStringCopyUTF8CString(target());

    WebKitWebFrame* targetFrame;
    if (!strlen(targetString))
        targetFrame = mainFrame;
    else
        targetFrame = webkit_web_frame_find_frame(mainFrame, targetString);
    g_free(targetString);

    gchar* urlString = JSStringCopyUTF8CString(url());
    WebKitNetworkRequest* request = webkit_network_request_new(urlString);
    g_free(urlString);
    webkit_web_frame_load_request(targetFrame, request);
    g_object_unref(request);
}

void ReloadItem::invoke() const
{
    webkit_web_frame_reload(mainFrame);
}

void ScriptItem::invoke() const
{
    WebKitWebView* webView = webkit_web_frame_get_web_view(mainFrame);
    gchar* scriptString = JSStringCopyUTF8CString(script());
    // TODO: does this return something we need to free? If not, why not?
    webkit_web_view_execute_script(webView, scriptString);
    g_free(scriptString);
}

void BackForwardItem::invoke() const
{
    WebKitWebView* webView = webkit_web_frame_get_web_view(mainFrame);
    webkit_web_view_go_back_or_forward(webView, m_howFar);
}
