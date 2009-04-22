/* 
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#include "config.h"
#include "ResourceHandle.h"

#include "DocLoader.h"
#include "FrameAndroid.h"
#include "ResourceHandleClient.h"
#include "ResourceHandleInternal.h"
#include "WebCoreFrameBridge.h"
#include "WebCoreResourceLoader.h"

// #define notImplemented() do { fprintf(stderr, "FIXME: UNIMPLEMENTED %s %s:%d\n", __PRETTY_FUNCTION__, __FILE__, __LINE__); } while(0)

namespace WebCore {

ResourceHandleInternal::~ResourceHandleInternal()
{
    Release(m_loader);
}

ResourceHandle::~ResourceHandle()
{
}

bool ResourceHandle::start(Frame* frame)
{
    FrameAndroid* f = Android(frame);
    android::WebCoreResourceLoader* loader;
    bool highPriority = true;
    CachedResource* r = d->m_request.getCachedResource();
    if (r) {
        CachedResource::Type t = r->type();
        highPriority = !(t == CachedResource::ImageResource ||
                       t == CachedResource::FontResource);
    }
    loader = f->bridge()->startLoadingResource(this, d->m_request, highPriority, false);

    if (loader) {
        Release(d->m_loader);
        d->m_loader = loader;
    }

    return loader != NULL;
}

void ResourceHandle::cancel()
{
    if (d->m_loader)
        d->m_loader->cancel();
}

PassRefPtr<SharedBuffer> ResourceHandle::bufferedData()
{
    return 0;
}

bool ResourceHandle::supportsBufferedData()
{
    // We don't support buffering data on the native side.
    return false;
}

void ResourceHandle::setDefersLoading(bool defers)
{
    notImplemented();
}

/*
* This static method is called to check to see if a POST response is in
* the cache. The JNI call through to the HTTP cache stored on the Java
* side may be slow, but is only used during a navigation to
* a POST response.
*/
bool ResourceHandle::willLoadFromCache(ResourceRequest& request) 
{
    // set the cache policy correctly, copied from
    // network/mac/ResourceHandleMac.mm
    request.setCachePolicy(ReturnCacheDataDontLoad);
    return android::WebCoreResourceLoader::willLoadFromCache(request.url());
}

bool ResourceHandle::loadsBlocked() 
{
    // FIXME, need to check whether connection pipe is blocked.
    // return false for now
    return false; 
}

// Class to handle synchronized loading of resources.
class SyncLoader : public ResourceHandleClient {
public:
    SyncLoader(ResourceError& error, ResourceResponse& response, Vector<char>& data) {
        m_error = &error;
        m_response = &response;
        m_data = &data;
    }
    ~SyncLoader() {}

    virtual void didReceiveResponse(ResourceHandle*, const ResourceResponse& response) {
        *m_response = response;
    }

    virtual void didReceiveData(ResourceHandle*, const char* data, int len, int lengthReceived) {
        m_data->append(data, len);
    }

    virtual void didFail(ResourceHandle*, const ResourceError& error) {
        *m_error = error;
    }

private:
    ResourceError*    m_error;
    ResourceResponse* m_response;
    Vector<char>*     m_data;
};

void ResourceHandle::loadResourceSynchronously(const ResourceRequest& request, 
        ResourceError& error, ResourceResponse& response, Vector<char>& data,
        Frame* frame) 
{
    FrameAndroid* f = Android(frame);
    SyncLoader s(error, response, data);
    ResourceHandle h(request, &s, false, false, false);
    // This blocks until the load is finished.
    f->bridge()->startLoadingResource(&h, request, true, true);
}

} // namespace WebCore
