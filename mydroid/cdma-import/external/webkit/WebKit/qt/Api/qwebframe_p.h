/*
    Copyright (C) 2007 Trolltech ASA
    Copyright (C) 2007 Staikos Computing Services Inc.

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Library General Public
    License as published by the Free Software Foundation; either
    version 2 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Library General Public License for more details.

    You should have received a copy of the GNU Library General Public License
    along with this library; see the file COPYING.LIB.  If not, write to
    the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
    Boston, MA 02110-1301, USA.

    This class provides all functionality needed for loading images, style sheets and html
    pages from the web. It has a memory cache for these objects.
*/
#ifndef QWEBFRAME_P_H
#define QWEBFRAME_P_H

#include "qwebframe.h"
#include "qwebpage_p.h"

#include "EventHandler.h"
#include "FrameView.h"
#include "KURL.h"
#include "PlatformString.h"
#include "wtf/RefPtr.h"

namespace WebCore
{
    class FrameLoaderClientQt;
    class Frame;
    class FrameView;
    class HTMLFrameOwnerElement;
    class PlatformScrollbar;
}
class QWebPage;


class QWebFrameData
{
public:
    WebCore::KURL url;
    WebCore::String name;
    WebCore::HTMLFrameOwnerElement* ownerElement;

    WebCore::String referrer;
    bool allowsScrolling;
    int marginWidth;
    int marginHeight;
};

class QWebFramePrivate
{
public:
    QWebFramePrivate()
        : q(0)
        , frameLoaderClient(0)
        , frame(0)
        , frameView(0)
        , page(0)
        {}
    void init(QWebFrame *qframe, WebCore::Page *page,
              QWebFrameData *frameData);

    inline QWebFrame *parentFrame() { return qobject_cast<QWebFrame*>(q->parent()); }

    WebCore::PlatformScrollbar *horizontalScrollBar() const;
    WebCore::PlatformScrollbar *verticalScrollBar() const;

    inline QPoint pos() const
    { return frameView->frameGeometry().topLeft(); }

    static WebCore::Frame* core(QWebFrame*);
    static QWebFrame* kit(WebCore::Frame*);

    QWebFrame *q;
    WebCore::FrameLoaderClientQt *frameLoaderClient;
    WTF::RefPtr<WebCore::Frame> frame;
    WTF::RefPtr<WebCore::FrameView> frameView;
    QWebPage *page;
};

#endif
