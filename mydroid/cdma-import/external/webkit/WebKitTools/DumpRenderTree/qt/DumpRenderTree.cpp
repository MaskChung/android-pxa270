/*
 * Copyright (C) 2005, 2006 Apple Computer, Inc.  All rights reserved.
 * Copyright (C) 2006 Nikolas Zimmermann <zimmermann@kde.org>
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

#include "DumpRenderTree.h"
#include "jsobjects.h"

#include <QDir>
#include <QFile>
#include <QTimer>
#include <QBoxLayout>
#include <QScrollArea>
#include <QApplication>
#include <QUrl>
#include <QFocusEvent>

#include <qwebpage.h>
#include <qwebframe.h>
#include <qwebview.h>
#include <qwebsettings.h>

#include <unistd.h>
#include <qdebug.h>
extern void qt_drt_run(bool b);
extern void qt_dump_set_accepts_editing(bool b);


namespace WebCore {

// Choose some default values.
const unsigned int maxViewWidth = 800;
const unsigned int maxViewHeight = 600;

class WebPage : public QWebPage {
    Q_OBJECT
public:
    WebPage(QWidget *parent, DumpRenderTree *drt);

    QWebPage *createWindow();

    void javaScriptAlert(QWebFrame *frame, const QString& message);
    void javaScriptConsoleMessage(const QString& message, unsigned int lineNumber, const QString& sourceID);
    bool javaScriptConfirm(QWebFrame *frame, const QString& msg);
    bool javaScriptPrompt(QWebFrame *frame, const QString& msg, const QString& defaultValue, QString* result);

private slots:
    void setViewGeometry(const QRect &r)
    {
        QWidget *v = view();
        if (v)
            v->setGeometry(r);
    }
private:
    DumpRenderTree *m_drt;
};

WebPage::WebPage(QWidget *parent, DumpRenderTree *drt)
    : QWebPage(parent), m_drt(drt)
{
    settings()->setAttribute(QWebSettings::JavascriptCanOpenWindows, true);
    settings()->setAttribute(QWebSettings::JavascriptCanAccessClipboard, true);
    settings()->setAttribute(QWebSettings::LinksIncludedInFocusChain, false);
    connect(this, SIGNAL(geometryChangeRequest(const QRect &)),
            this, SLOT(setViewGeometry(const QRect & )));
}

QWebPage *WebPage::createWindow()
{
    return m_drt->createWindow();
}

void WebPage::javaScriptAlert(QWebFrame *frame, const QString& message)
{
    fprintf(stdout, "ALERT: %s\n", message.toUtf8().constData());
}

void WebPage::javaScriptConsoleMessage(const QString& message, unsigned int lineNumber, const QString&)
{
    fprintf (stdout, "CONSOLE MESSAGE: line %d: %s\n", lineNumber, message.toUtf8().constData());
}

bool WebPage::javaScriptConfirm(QWebFrame *frame, const QString& msg)
{
    fprintf(stdout, "CONFIRM: %s\n", msg.toUtf8().constData());
    return true;
}

bool WebPage::javaScriptPrompt(QWebFrame *frame, const QString& msg, const QString& defaultValue, QString* result)
{
    fprintf(stdout, "PROMPT: %s, default text: %s\n", msg.toUtf8().constData(), defaultValue.toUtf8().constData());
    *result = defaultValue;
    return true;
}

DumpRenderTree::DumpRenderTree()
    : m_stdin(0)
    , m_notifier(0)
{
    m_controller = new LayoutTestController(this);
    connect(m_controller, SIGNAL(done()), this, SLOT(dump()), Qt::QueuedConnection);

    QWebView *view = new QWebView(0);
    view->resize(QSize(maxViewWidth, maxViewHeight));
    m_page = new WebPage(view, this);
    view->setPage(m_page);
    connect(m_page, SIGNAL(frameCreated(QWebFrame *)), this, SLOT(connectFrame(QWebFrame *)));
    connectFrame(m_page->mainFrame());
    
    m_page->mainFrame()->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
    m_page->mainFrame()->setVerticalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
    connect(m_page->mainFrame(), SIGNAL(titleChanged(const QString&)),
            SLOT(titleChanged(const QString&)));

    m_eventSender = new EventSender(m_page);
    m_textInputController = new TextInputController(m_page);

    QObject::connect(this, SIGNAL(quit()), qApp, SLOT(quit()), Qt::QueuedConnection);
    qt_drt_run(true);
    QFocusEvent event(QEvent::FocusIn, Qt::ActiveWindowFocusReason);
    QApplication::sendEvent(view, &event);
}

DumpRenderTree::~DumpRenderTree()
{
    delete m_page;

    delete m_stdin;
    delete m_notifier;
}

void DumpRenderTree::open()
{
    if (!m_stdin) {
        m_stdin = new QFile;
        m_stdin->open(stdin, QFile::ReadOnly);
    }

    if (!m_notifier) {
        m_notifier = new QSocketNotifier(STDIN_FILENO, QSocketNotifier::Read);
        connect(m_notifier, SIGNAL(activated(int)), this, SLOT(readStdin(int)));
    }
}

void DumpRenderTree::open(const QUrl& url)
{
    resetJSObjects();
    m_page->mainFrame()->load(url);
}

void DumpRenderTree::readStdin(int /* socket */)
{
    // Read incoming data from stdin...
    QByteArray line = m_stdin->readLine();
    if (line.endsWith('\n'))
        line.truncate(line.size()-1);
    //fprintf(stderr, "\n    opening %s\n", line.constData());
    if (line.isEmpty())
        quit();
    QFileInfo fi(line);
    open(QUrl::fromLocalFile(fi.absoluteFilePath()));
    fflush(stdout);
}

void DumpRenderTree::resetJSObjects()
{
    m_controller->reset();
    foreach(QWidget *widget, windows)
        delete widget;
    windows.clear();
}

void DumpRenderTree::initJSObjects()
{
    QWebFrame *frame = qobject_cast<QWebFrame*>(sender());
    Q_ASSERT(frame);
    frame->addToJSWindowObject(QLatin1String("layoutTestController"), m_controller);
    frame->addToJSWindowObject(QLatin1String("eventSender"), m_eventSender);
    frame->addToJSWindowObject(QLatin1String("textInputController"), m_textInputController);
}


QString DumpRenderTree::dumpFramesAsText(QWebFrame* frame)
{
    if (!frame)
        return QString();

    QString result;
    QWebFrame *parent = qobject_cast<QWebFrame *>(frame->parent());
    if (parent) {
        result.append(QLatin1String("\n--------\nFrame: '"));
        result.append(frame->name());
        result.append(QLatin1String("'\n--------\n"));
    }

    result.append(frame->innerText());
    result.append(QLatin1String("\n"));

    if (m_controller->shouldDumpChildrenAsText()) {
        QList<QWebFrame *> children = frame->childFrames();
        for (int i = 0; i < children.size(); ++i)
            result += dumpFramesAsText(children.at(i));
    }

    return result;
}

void DumpRenderTree::dump()
{
    QWebFrame *frame = m_page->mainFrame();

    //fprintf(stderr, "    Dumping\n");
    if (!m_notifier) {
        // Dump markup in single file mode...
        QString markup = frame->markup();
        fprintf(stdout, "Source:\n\n%s\n", markup.toUtf8().constData());
    }

    // Dump render text...
    QString renderDump;
    if (m_controller->shouldDumpAsText()) {
        renderDump = dumpFramesAsText(frame);
    } else {
        renderDump = frame->renderTreeDump();
    }
    if (renderDump.isEmpty()) {
        printf("ERROR: nil result from %s", m_controller->shouldDumpAsText() ? "[documentElement innerText]" : "[frame renderTreeAsExternalRepresentation]");
    } else {
        fprintf(stdout, "%s", renderDump.toUtf8().constData());
    }

    fprintf(stdout, "#EOF\n");

    fflush(stdout);

    if (!m_notifier) {
        // Exit now in single file mode...
        quit();
    }
}

void DumpRenderTree::titleChanged(const QString &s)
{
    if (m_controller->shouldDumpTitleChanges())
        printf("TITLE CHANGED: %s\n", s.toUtf8().data());
}

void DumpRenderTree::connectFrame(QWebFrame *frame)
{
    connect(frame, SIGNAL(cleared()), this, SLOT(initJSObjects()));
    connect(frame, SIGNAL(provisionalLoad()),
            layoutTestController(), SLOT(provisionalLoad()));

    if (frame == m_page->mainFrame()) {
        connect(frame, SIGNAL(loadDone(bool)),
                layoutTestController(), SLOT(maybeDump(bool)));
    }
}

QWebPage *DumpRenderTree::createWindow()
{
    if (!m_controller->canOpenWindows())
        return 0;
    QWidget *container = new QWidget(0);
    container->resize(0, 0);
    container->move(-1, -1);
    container->hide();
    QWebPage *page = new WebPage(container, this);
    connect(m_page, SIGNAL(frameCreated(QWebFrame *)), this, SLOT(connectFrame(QWebFrame *)));
    windows.append(container);
    return page;
}

int DumpRenderTree::windowCount() const
{
    int count = 0;
    foreach(QWidget *w, windows) {
        if (w->children().count())
            ++count;
    }
    return count + 1;
}

}

#include "DumpRenderTree.moc"
