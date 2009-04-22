/*
    This file is part of the KDE libraries

    Copyright (C) 1998 Lars Knoll (knoll@mpi-hd.mpg.de)
    Copyright (C) 2001 Dirk Mueller (mueller@kde.org)
    Copyright (C) 2002 Waldo Bastian (bastian@kde.org)
    Copyright (C) 2004, 2005, 2006 Apple Computer, Inc.

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

#include "config.h"
#include "DocLoader.h"

#include "Cache.h"
#include "CachedCSSStyleSheet.h"
#include "CachedFont.h"
#include "CachedImage.h"
#include "CachedScript.h"
#include "CachedXSLStyleSheet.h"
#include "Document.h"
#include "Frame.h"
#include "FrameLoader.h"
#include "loader.h"

#define PRELOAD_DEBUG 0

namespace WebCore {

DocLoader::DocLoader(Frame *frame, Document* doc)
    : m_cache(cache())
    , m_cachePolicy(CachePolicyVerify)
    , m_frame(frame)
    , m_doc(doc)
    , m_requestCount(0)
#ifdef ANDROID_BLOCK_NETWORK_IMAGE
    , m_blockNetworkImage(false)
#endif
    , m_autoLoadImages(true)
    , m_loadInProgress(false)
    , m_allowStaleResources(false)
{
    m_cache->addDocLoader(this);
}

DocLoader::~DocLoader()
{
#ifdef ANDROID_PRELOAD_CHANGES
    clearPreloads();
#endif
    HashMap<String, CachedResource*>::iterator end = m_docResources.end();
    for (HashMap<String, CachedResource*>::iterator it = m_docResources.begin(); it != end; ++it)
        it->second->setDocLoader(0);
    m_cache->removeDocLoader(this);
}

void DocLoader::checkForReload(const KURL& fullURL)
{
    if (m_allowStaleResources)
        return; //Don't reload resources while pasting
    if (m_cachePolicy == CachePolicyVerify) {
       if (!m_reloadedURLs.contains(fullURL.string())) {
          CachedResource* existing = cache()->resourceForURL(fullURL.string());
#ifdef ANDROID_PRELOAD_CHANGES
          if (existing && existing->isExpired() && !existing->isPreloaded()) {
#else
          if (existing && existing->isExpired()) {
#endif
             cache()->remove(existing);
             m_reloadedURLs.add(fullURL.string());
          }
       }
    } else if ((m_cachePolicy == CachePolicyReload) || (m_cachePolicy == CachePolicyRefresh)) {
       if (!m_reloadedURLs.contains(fullURL.string())) {
          CachedResource* existing = cache()->resourceForURL(fullURL.string());
#ifdef ANDROID_PRELOAD_CHANGES
          if (existing && !existing->isPreloaded()) {
#else
          if (existing)
#endif
             cache()->remove(existing);
          m_reloadedURLs.add(fullURL.string());
#ifdef ANDROID_PRELOAD_CHANGES
          }
#endif
       }
    }
}

CachedImage* DocLoader::requestImage(const String& url)
{
    CachedImage* resource = static_cast<CachedImage*>(requestResource(CachedResource::ImageResource, url));
    if (autoLoadImages() && resource && resource->stillNeedsLoad()) {
#ifdef ANDROID_BLOCK_NETWORK_IMAGE
        if (shouldBlockNetworkImage(url)) {
            return resource;
        }
#endif
        resource->setLoading(true);
        cache()->loader()->load(this, resource, true);
    }
    return resource;
}

CachedFont* DocLoader::requestFont(const String& url)
{
    return static_cast<CachedFont*>(requestResource(CachedResource::FontResource, url));
}

CachedCSSStyleSheet* DocLoader::requestCSSStyleSheet(const String& url, const String& charset, bool isUserStyleSheet)
{
    // FIXME: Passing true for "skipCanLoadCheck" here in the isUserStyleSheet case  won't have any effect
    // if this resource is already in the cache. It's theoretically possible that what's in the cache already
    // is a load that failed because of the canLoad check. Probably not an issue in practice.
    CachedCSSStyleSheet *sheet = static_cast<CachedCSSStyleSheet*>(requestResource(CachedResource::CSSStyleSheet, url, &charset, isUserStyleSheet, !isUserStyleSheet));

    // A user style sheet can outlive its DocLoader so don't store any pointers to it
    if (sheet && isUserStyleSheet) {
        sheet->setDocLoader(0);
        m_docResources.remove(sheet->url());
    }
    
    return sheet;
}

CachedCSSStyleSheet* DocLoader::requestUserCSSStyleSheet(const String& url, const String& charset)
{
    return requestCSSStyleSheet(url, charset, true);
}

CachedScript* DocLoader::requestScript(const String& url, const String& charset)
{
    return static_cast<CachedScript*>(requestResource(CachedResource::Script, url, &charset));
}

#if ENABLE(XSLT)
CachedXSLStyleSheet* DocLoader::requestXSLStyleSheet(const String& url)
{
    return static_cast<CachedXSLStyleSheet*>(requestResource(CachedResource::XSLStyleSheet, url));
}
#endif

#if ENABLE(XBL)
CachedXBLDocument* DocLoader::requestXBLDocument(const String& url)
{
    return static_cast<CachedXSLStyleSheet*>(requestResource(CachedResource::XBL, url));
}
#endif

#ifdef ANDROID_PRELOAD_CHANGES
CachedResource* DocLoader::requestResource(CachedResource::Type type, const String& url, const String* charset, bool skipCanLoadCheck, bool sendResourceLoadCallbacks, bool isPreload)
#else
CachedResource* DocLoader::requestResource(CachedResource::Type type, const String& url, const String* charset, bool skipCanLoadCheck, bool sendResourceLoadCallbacks)
#endif
{
    KURL fullURL = m_doc->completeURL(url.deprecatedString());
    
    if (cache()->disabled()) {
        HashMap<String, CachedResource*>::iterator it = m_docResources.find(fullURL.string());
        
        if (it != m_docResources.end()) {
            it->second->setDocLoader(0);
            m_docResources.remove(it);
        }
    }
                                                          
    if (m_frame && m_frame->loader()->isReloading())
        setCachePolicy(CachePolicyReload);

    checkForReload(fullURL);
#ifdef ANDROID_PRELOAD_CHANGES
    CachedResource* resource = cache()->requestResource(this, type, fullURL, charset, skipCanLoadCheck, sendResourceLoadCallbacks, isPreload);
#else
    CachedResource* resource = cache()->requestResource(this, type, fullURL, charset, skipCanLoadCheck, sendResourceLoadCallbacks);
#endif
    if (resource) {
        m_docResources.set(resource->url(), resource);
        checkCacheObjectStatus(resource);
    }
    return resource;
}

void DocLoader::setAutoLoadImages(bool enable)
{
    if (enable == m_autoLoadImages)
        return;

    m_autoLoadImages = enable;

    if (!m_autoLoadImages)
        return;

    HashMap<String, CachedResource*>::iterator end = m_docResources.end();
    for (HashMap<String, CachedResource*>::iterator it = m_docResources.begin(); it != end; ++it) {
        CachedResource* resource = it->second;
        if (resource->type() == CachedResource::ImageResource) {
            CachedImage* image = const_cast<CachedImage*>(static_cast<const CachedImage*>(resource));
#ifdef ANDROID_BLOCK_NETWORK_IMAGE
            if (shouldBlockNetworkImage(image->url()))
                continue;
#endif

            if (image->stillNeedsLoad())
                cache()->loader()->load(this, image, true);
        }
    }
}

#ifdef ANDROID_BLOCK_NETWORK_IMAGE
bool DocLoader::shouldBlockNetworkImage(const String& url) const
{
    if (!m_blockNetworkImage)
        return false;

    KURL kurl(url.deprecatedString());
    if (kurl.protocolIs("http") || kurl.protocolIs("https"))
        return true;

    return false;
}

void DocLoader::setBlockNetworkImage(bool block)
{
    if (block == m_blockNetworkImage)
        return;

    m_blockNetworkImage = block;

    if (!m_autoLoadImages || m_blockNetworkImage)
        return;

    HashMap<String, CachedResource*>::iterator end = m_docResources.end();
    for (HashMap<String, CachedResource*>::iterator it = m_docResources.begin(); it != end; ++it) {
        CachedResource* resource = it->second;
        if (resource->type() == CachedResource::ImageResource) {
            CachedImage* image = const_cast<CachedImage*>(static_cast<const CachedImage*>(resource));
            if (image->stillNeedsLoad())
                cache()->loader()->load(this, image, true);
        }
    }
}
#endif

void DocLoader::setCachePolicy(CachePolicy cachePolicy)
{
    m_cachePolicy = cachePolicy;
}

void DocLoader::removeCachedResource(CachedResource* resource) const
{
    m_docResources.remove(resource->url());
}

void DocLoader::setLoadInProgress(bool load)
{
    m_loadInProgress = load;
    if (!load && m_frame)
        m_frame->loader()->loadDone();
}

void DocLoader::checkCacheObjectStatus(CachedResource* resource)
{
    // Return from the function for objects that we didn't load from the cache or if we don't have a frame.
    if (!resource || !m_frame)
        return;

    switch (resource->status()) {
        case CachedResource::Cached:
            break;
        case CachedResource::NotCached:
        case CachedResource::Unknown:
        case CachedResource::New:
        case CachedResource::Pending:
            return;
    }

    // FIXME: If the WebKit client changes or cancels the request, WebCore does not respect this and continues the load.
    m_frame->loader()->loadedResourceFromMemoryCache(resource);
}

void DocLoader::incrementRequestCount()
{
    ++m_requestCount;
}

void DocLoader::decrementRequestCount()
{
    --m_requestCount;
    ASSERT(m_requestCount > -1);
}

int DocLoader::requestCount()
{
    if (loadInProgress())
         return m_requestCount + 1;
    return m_requestCount;
}

#ifdef ANDROID_PRELOAD_CHANGES
void DocLoader::preload(CachedResource::Type type, const String& url, const String& charset, bool inBody)
{
    if ((inBody || type == CachedResource::ImageResource) && (!m_doc->body() || !m_doc->body()->renderer())) {
        // Don't preload images or body resources before we have the first rendering.
        PendingPreload pendingPreload = { type, url, charset };
        m_pendingPreloads.append(pendingPreload);
        return;
    }
    requestPreload(type, url, charset);
}
    
void DocLoader::checkForPendingPreloads() 
{
    unsigned count = m_pendingPreloads.size();
    if (!count || !m_doc->body() || !m_doc->body()->renderer())
        return;
    for (unsigned n = 0; n < count; ++n) {
        PendingPreload& preload = m_pendingPreloads[n];
        requestPreload(preload.m_type, preload.m_url, preload.m_charset);
    }
    m_pendingPreloads.clear();
}
    
void DocLoader::requestPreload(CachedResource::Type type, const String& url, const String& charset)
{
    String encoding;
    if (type == CachedResource::Script || type == CachedResource::CSSStyleSheet)
        encoding = charset.isEmpty() ? m_doc->frame()->loader()->encoding() : charset;

    CachedResource* resource = requestResource(type, url, &encoding, false, true, true);
    if (!resource || m_preloads.contains(resource))
        return;
    resource->increasePreloadCount();
    m_preloads.add(resource);
#if PRELOAD_DEBUG
    printf("PRELOADING %s\n",  resource->url().latin1().data());
#endif
}

void DocLoader::clearPreloads()
{
#if PRELOAD_DEBUG
    printPreloadStats();
#endif
    ListHashSet<CachedResource*>::iterator end = m_preloads.end();
    for (ListHashSet<CachedResource*>::iterator it = m_preloads.begin(); it != end; ++it) {
        CachedResource* res = *it;
        res->decreasePreloadCount();
        if (res->canDelete() && !res->inCache())
            delete res;
        else if (res->preloadResult() == CachedResource::PreloadNotReferenced)
            cache()->remove(res);
    }
    m_preloads.clear();
}

#if PRELOAD_DEBUG
void DocLoader::printPreloadStats()
{
    unsigned scripts = 0;
    unsigned scriptMisses = 0;
    unsigned stylesheets = 0;
    unsigned stylesheetMisses = 0;
    unsigned images = 0;
    unsigned imageMisses = 0;
    ListHashSet<CachedResource*>::iterator end = m_preloads.end();
    for (ListHashSet<CachedResource*>::iterator it = m_preloads.begin(); it != end; ++it) {
        CachedResource* res = *it;
        if (res->preloadResult() == CachedResource::PreloadNotReferenced)
            printf("!! UNREFERENCED PRELOAD %s\n", res->url().latin1().data());
        else if (res->preloadResult() == CachedResource::PreloadReferencedWhileComplete)
            printf("HIT COMPLETE PRELOAD %s\n", res->url().latin1().data());
        else if (res->preloadResult() == CachedResource::PreloadReferencedWhileLoading)
            printf("HIT LOADING PRELOAD %s\n", res->url().latin1().data());
        
        if (res->type() == CachedResource::Script) {
            scripts++;
            if (res->preloadResult() < CachedResource::PreloadReferencedWhileLoading)
                scriptMisses++;
        } else if (res->type() == CachedResource::CSSStyleSheet) {
            stylesheets++;
            if (res->preloadResult() < CachedResource::PreloadReferencedWhileLoading)
                stylesheetMisses++;
        } else {
            images++;
            if (res->preloadResult() < CachedResource::PreloadReferencedWhileLoading)
                imageMisses++;
        }
        
        if (res->errorOccurred())
            cache()->remove(res);
        
        res->decreasePreloadCount();
    }
    m_preloads.clear();
    
    if (scripts)
        printf("SCRIPTS: %d (%d hits, hit rate %d%%)\n", scripts, scripts - scriptMisses, (scripts - scriptMisses) * 100 / scripts);
    if (stylesheets)
        printf("STYLESHEETS: %d (%d hits, hit rate %d%%)\n", stylesheets, stylesheets - stylesheetMisses, (stylesheets - stylesheetMisses) * 100 / stylesheets);
    if (images)
        printf("IMAGES:  %d (%d hits, hit rate %d%%)\n", images, images - imageMisses, (images - imageMisses) * 100 / images);
}
#endif
#endif // ANDROID_PRELOAD_CHANGES
}
