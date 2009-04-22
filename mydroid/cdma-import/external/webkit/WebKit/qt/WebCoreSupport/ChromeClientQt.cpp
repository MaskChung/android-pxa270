/*
 * Copyright (C) 2006 Zack Rusin <zack@kde.org>
 * Copyright (C) 2006 Apple Computer, Inc.  All rights reserved.
 *
 * All rights reserved.
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
#include "ChromeClientQt.h"

#include "Frame.h"
#include "FrameLoadRequest.h"
#include "FrameLoader.h"
#include "FrameLoaderClientQt.h"
#include "FrameView.h"
#include "PlatformScrollBar.h"
#include "HitTestResult.h"
#include "NotImplemented.h"
#include "WindowFeatures.h"

#include "qwebpage.h"
#include "qwebpage_p.h"
#include "qwebframe_p.h"

namespace WebCore
{


ChromeClientQt::ChromeClientQt(QWebPage* webPage)
    : m_webPage(webPage)
{

}


ChromeClientQt::~ChromeClientQt()
{

}

void ChromeClientQt::setWindowRect(const FloatRect& rect)
{
    if (!m_webPage)
        return;
    emit m_webPage->geometryChangeRequest(QRect(qRound(rect.x()), qRound(rect.y()),
                            qRound(rect.width()), qRound(rect.height())));
}


FloatRect ChromeClientQt::windowRect()
{
    if (!m_webPage)
        return FloatRect();

    QWidget* view = m_webPage->view();
    if (!view)
        return FloatRect();
    return IntRect(view->topLevelWidget()->geometry());
}


FloatRect ChromeClientQt::pageRect()
{
    if (!m_webPage)
        return FloatRect();
    return FloatRect(QRectF(QPointF(0,0), m_webPage->viewportSize()));
}


float ChromeClientQt::scaleFactor()
{
    notImplemented();
    return 1;
}


void ChromeClientQt::focus()
{
    if (!m_webPage)
        return;
    QWidget* view = m_webPage->view();
    if (!view)
        return;

    view->setFocus();
}


void ChromeClientQt::unfocus()
{
    if (!m_webPage)
        return;
    QWidget* view = m_webPage->view();
    if (!view)
        return;
    view->clearFocus();
}

bool ChromeClientQt::canTakeFocus(FocusDirection)
{
    // This is called when cycling through links/focusable objects and we
    // reach the last focusable object. Then we want to claim that we can
    // take the focus to avoid wrapping.
    return true;
}

void ChromeClientQt::takeFocus(FocusDirection)
{
    // don't do anything. This is only called when cycling to links/focusable objects,
    // which in turn is called from focusNextPrevChild. We let focusNextPrevChild
    // call QWidget::focusNextPrevChild accordingly, so there is no need to do anything
    // here.
}


Page* ChromeClientQt::createWindow(Frame*, const FrameLoadRequest& request, const WindowFeatures& features)
{
    QWebPage *newPage = features.dialog ? m_webPage->createModalDialog() : m_webPage->createWindow();
    if (!newPage)
        return 0;
    newPage->mainFrame()->load(request.resourceRequest().url());
    return newPage->d->page;
}

void ChromeClientQt::show()
{
    if (!m_webPage)
        return;
    QWidget* view = m_webPage->view();
    if (!view)
        return;
    view->topLevelWidget()->show();
}


bool ChromeClientQt::canRunModal()
{
    notImplemented();
    return false;
}


void ChromeClientQt::runModal()
{
    notImplemented();
}


void ChromeClientQt::setToolbarsVisible(bool)
{
    notImplemented();
}


bool ChromeClientQt::toolbarsVisible()
{
    notImplemented();
    return false;
}


void ChromeClientQt::setStatusbarVisible(bool)
{
    notImplemented();
}


bool ChromeClientQt::statusbarVisible()
{
    notImplemented();
    return false;
}


void ChromeClientQt::setScrollbarsVisible(bool)
{
    notImplemented();
}


bool ChromeClientQt::scrollbarsVisible()
{
    notImplemented();
    return true;
}


void ChromeClientQt::setMenubarVisible(bool)
{
    notImplemented();
}

bool ChromeClientQt::menubarVisible()
{
    notImplemented();
    return false;
}

void ChromeClientQt::setResizable(bool)
{
    notImplemented();
}

void ChromeClientQt::addMessageToConsole(const String& message, unsigned int lineNumber,
                                         const String& sourceID)
{
    QString x = message;
    QString y = sourceID;
    m_webPage->javaScriptConsoleMessage(x, lineNumber, y);
}

void ChromeClientQt::chromeDestroyed()
{
    delete this;
}

bool ChromeClientQt::canRunBeforeUnloadConfirmPanel()
{
    return true;
}

bool ChromeClientQt::runBeforeUnloadConfirmPanel(const String& message, Frame* frame)
{
    return runJavaScriptConfirm(frame, message);
}

void ChromeClientQt::closeWindowSoon()
{
    m_webPage->mainFrame()->d->frame->loader()->stopAllLoaders();
    m_webPage->deleteLater();
}

void ChromeClientQt::runJavaScriptAlert(Frame* f, const String& msg)
{
    QString x = msg;
    FrameLoaderClientQt *fl = static_cast<FrameLoaderClientQt*>(f->loader()->client());
    m_webPage->javaScriptAlert(fl->webFrame(), x);
}

bool ChromeClientQt::runJavaScriptConfirm(Frame* f, const String& msg)
{
    QString x = msg;
    FrameLoaderClientQt *fl = static_cast<FrameLoaderClientQt*>(f->loader()->client());
    return m_webPage->javaScriptConfirm(fl->webFrame(), x);
}

bool ChromeClientQt::runJavaScriptPrompt(Frame* f, const String& message, const String& defaultValue, String& result)
{
    QString x = result;
    FrameLoaderClientQt *fl = static_cast<FrameLoaderClientQt*>(f->loader()->client());
    bool rc = m_webPage->javaScriptPrompt(fl->webFrame(), (QString)message, (QString)defaultValue, &x);
    result = x;
    return rc;
}

void ChromeClientQt::setStatusbarText(const String& msg)
{
    QString x = msg;
    emit m_webPage->statusBarTextChanged(x);
}

bool ChromeClientQt::shouldInterruptJavaScript()
{
    notImplemented();
    return false;
}

bool ChromeClientQt::tabsToLinks() const
{
    return m_webPage->settings()->testAttribute(QWebSettings::LinksIncludedInFocusChain);
}

IntRect ChromeClientQt::windowResizerRect() const
{
    return IntRect();
}

void ChromeClientQt::addToDirtyRegion(const IntRect& r)
{
    QWidget* view = m_webPage->view();
    if (view) {
        QRect rect(r);
        rect = rect.intersected(QRect(QPoint(0, 0), m_webPage->viewportSize()));
        if (!r.isEmpty())
            view->update(r);
    }
}

void ChromeClientQt::scrollBackingStore(int dx, int dy, const IntRect& scrollViewRect, const IntRect& clipRect)
{
    QWidget* view = m_webPage->view();
    if (view)
        view->scroll(dx, dy, scrollViewRect);
}

void ChromeClientQt::updateBackingStore()
{
}

void ChromeClientQt::mouseDidMoveOverElement(const HitTestResult& result, unsigned modifierFlags)
{
    if (result.absoluteLinkURL() != lastHoverURL
        || result.title() != lastHoverTitle
        || result.textContent() != lastHoverContent) {
        lastHoverURL = result.absoluteLinkURL();
        lastHoverTitle = result.title();
        lastHoverContent = result.textContent();
        emit m_webPage->hoveringOverLink(lastHoverURL.prettyURL(),
                lastHoverTitle, lastHoverContent);
    }
}

void ChromeClientQt::setToolTip(const String &tip)
{
#ifndef QT_NO_TOOLTIP
    QWidget* view = m_webPage->view();
    if (view)
        view->setToolTip(tip);
#else
    Q_UNUSED(tip);
#endif
}

void ChromeClientQt::print(Frame*)
{
    notImplemented();
}

void ChromeClientQt::exceededDatabaseQuota(Frame*, const String&)
{
    notImplemented();
}

}


