/*
    Copyright (C) 2007 Trolltech ASA

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
#ifndef QWEBHISTORY_H
#define QWEBHISTORY_H

#include <QtCore/qurl.h>
#include <QtCore/qstring.h>
#include <QtGui/qicon.h>
#include <QtCore/qdatetime.h>
#include <QtCore/qshareddata.h>

#include "qwebkitglobal.h"

class QWebPage;

class QWebHistoryItemPrivate;
class QWEBKIT_EXPORT QWebHistoryItem
{
public:
    QWebHistoryItem(const QWebHistoryItem &other);
    QWebHistoryItem &operator=(const QWebHistoryItem &other);
    ~QWebHistoryItem();

    QUrl originalUrl() const;
    QUrl currentUrl() const;

    QString title() const;
    QDateTime lastVisited() const;

    QPixmap icon() const;

private:
    QWebHistoryItem(QWebHistoryItemPrivate *priv);
    friend class QWebHistory;
    friend class QWebPage;
    QExplicitlySharedDataPointer<QWebHistoryItemPrivate> d;
};

class QWebHistoryPrivate;
class QWEBKIT_EXPORT QWebHistory
{
public:
    void clear();

    QList<QWebHistoryItem> items() const;
    QList<QWebHistoryItem> backItems(int maxItems) const;
    QList<QWebHistoryItem> forwardItems(int maxItems) const;

    bool canGoBack() const;
    bool canGoForward() const;

    void goBack();
    void goForward();
    void goToItem(const QWebHistoryItem &item);

    QWebHistoryItem backItem() const;
    QWebHistoryItem currentItem() const;
    QWebHistoryItem forwardItem() const;
    QWebHistoryItem itemAtIndex(int i) const;

private:
    QWebHistory();
    ~QWebHistory();

    friend class QWebPage;
    friend class QWebPagePrivate;

    Q_DISABLE_COPY(QWebHistory)

    QWebHistoryPrivate *d;
};

#endif
