// -*- mode: c++; c-basic-offset: 4 -*-
/*
 * Copyright (C) 2003, 2006 Apple Computer, Inc.  All rights reserved.
 * Copyright (C) 2006 Samuel Weinig <sam.weinig@gmail.com>
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

#ifndef ResourceRequest_h
#define ResourceRequest_h

#include "CachedResource.h"
#include "ResourceRequestBase.h"

namespace WebCore {

    struct ResourceRequest : ResourceRequestBase {

        ResourceRequest(const String& url) 
            : ResourceRequestBase(KURL(url.deprecatedString()), UseProtocolCachePolicy)
            , m_cachedResource(0)
#ifdef ANDROID_USER_GESTURE
            , m_wasUserGesture(false)
#endif
        {
        }

        ResourceRequest(const KURL& url) 
            : ResourceRequestBase(url, UseProtocolCachePolicy)
            , m_cachedResource(0)
#ifdef ANDROID_USER_GESTURE
            , m_wasUserGesture(false)
#endif
        {
        }

        ResourceRequest(const KURL& url, const String& referrer, ResourceRequestCachePolicy policy = UseProtocolCachePolicy) 
            : ResourceRequestBase(url, policy)
            , m_cachedResource(0)
#ifdef ANDROID_USER_GESTURE
            , m_wasUserGesture(false)
#endif
        {
            setHTTPReferrer(referrer);
        }
        
        ResourceRequest()
            : ResourceRequestBase(KURL(), UseProtocolCachePolicy)
            , m_cachedResource(0)
#ifdef ANDROID_USER_GESTURE
            , m_wasUserGesture(false)
#endif
        {
        }
        
        void doUpdatePlatformRequest() {}
        void doUpdateResourceRequest() {}
        void setCachedResource(CachedResource* r) { m_cachedResource = r; }
        CachedResource* getCachedResource() const { return m_cachedResource; }
#ifdef ANDROID_USER_GESTURE
        void setUserGesture(bool userGesture)     { m_wasUserGesture = userGesture; }
        bool userGesture() const                  { return m_wasUserGesture; }
#endif
    private:
        friend class ResourceRequestBase;
        CachedResource* m_cachedResource;
#ifdef ANDROID_USER_GESTURE
        bool m_wasUserGesture;
#endif
    };

} // namespace WebCore

#endif // ResourceRequest_h
