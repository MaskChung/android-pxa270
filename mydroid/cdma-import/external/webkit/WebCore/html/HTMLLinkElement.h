/*
 * This file is part of the DOM implementation for KDE.
 *
 * Copyright (C) 1999 Lars Knoll (knoll@kde.org)
 *           (C) 1999 Antti Koivisto (koivisto@kde.org)
 * Copyright (C) 2003 Apple Computer, Inc.
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
 *
 */
#ifndef HTMLLinkElement_h
#define HTMLLinkElement_h

#include "HTMLElement.h"
#include "CachedResourceClient.h"
#include "CSSStyleSheet.h"

namespace WebCore {

class CachedCSSStyleSheet;

class HTMLLinkElement : public HTMLElement, public CachedResourceClient
{
public:
    HTMLLinkElement(Document*);
    ~HTMLLinkElement();

    virtual HTMLTagStatus endTagRequirement() const { return TagStatusForbidden; }
    virtual int tagPriority() const { return 0; }

    bool disabled() const;
    void setDisabled(bool);

    String charset() const;
    void setCharset(const String&);

    String href() const;
    void setHref(const String&);

    String hreflang() const;
    void setHreflang(const String&);

    String media() const;
    void setMedia(const String&);

    String rel() const;
    void setRel(const String&);

    String rev() const;
    void setRev(const String&);

    virtual String target() const;
    void setTarget(const String&);

    String type() const;
    void setType(const String&);

    StyleSheet* sheet() const;

    // overload from HTMLElement
    virtual void parseMappedAttribute(MappedAttribute*);

    void process();

    virtual void insertedIntoDocument();
    virtual void removedFromDocument();

    // from CachedResourceClient
    virtual void setCSSStyleSheet(const String &url, const String& charset, const String &sheet);
    bool isLoading() const;
    virtual bool sheetLoaded();

    bool isAlternate() const { return m_disabledState == 0 && m_alternate; }
    bool isDisabled() const { return m_disabledState == 2; }
    bool isEnabledViaScript() const { return m_disabledState == 1; }

    int disabledState() { return m_disabledState; }
    void setDisabledState(bool _disabled);

    virtual bool isURLAttribute(Attribute*) const;
#ifdef ANDROID_PRELOAD_CHANGES
    static void tokenizeRelAttribute(const AtomicString& value, bool& stylesheet, bool& alternate, bool& icon);
#else
    void tokenizeRelAttribute(const AtomicString& rel);
#endif

protected:
    CachedCSSStyleSheet* m_cachedSheet;
    RefPtr<CSSStyleSheet> m_sheet;
    String m_url;
    String m_type;
    String m_media;
    int m_disabledState; // 0=unset(default), 1=enabled via script, 2=disabled
#ifdef ANDROID_PRELOAD_CHANGES
    bool m_loading;
    bool m_alternate;
    bool m_isStyleSheet;
    bool m_isIcon;
#else
    bool m_loading : 1;
    bool m_alternate : 1;
    bool m_isStyleSheet : 1;
    bool m_isIcon : 1;
#endif
};

} //namespace

#endif
