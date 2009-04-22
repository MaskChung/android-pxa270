/*
 * Copyright (C) 2005, 2006, 2007 Apple Inc. All rights reserved.
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

#ifndef __LP64__
#import "WebBaseNetscapePluginStream.h"

#import "WebBaseNetscapePluginView.h"
#import "WebKitErrorsPrivate.h"
#import "WebKitLogging.h"
#import "WebNSObjectExtras.h"
#import "WebNSURLExtras.h"
#import "WebNetscapePluginPackage.h"
#import <Foundation/NSURLResponse.h>
#import <WebCore/WebCoreObjCExtras.h>
#import <WebKitSystemInterface.h>
#import <wtf/HashMap.h>

#define WEB_REASON_NONE -1

static NSString *CarbonPathFromPOSIXPath(NSString *posixPath);

typedef HashMap<NPStream*, NPP> StreamMap;
static StreamMap& streams()
{
    static StreamMap staticStreams;
    return staticStreams;
}

@implementation WebBaseNetscapePluginStream

#ifndef BUILDING_ON_TIGER
+ (void)initialize
{
    WebCoreObjCFinalizeOnMainThread(self);
}
#endif

+ (NPP)ownerForStream:(NPStream *)stream
{
    return streams().get(stream);
}

+ (NPReason)reasonForError:(NSError *)error
{
    if (error == nil) {
        return NPRES_DONE;
    }
    if ([[error domain] isEqualToString:NSURLErrorDomain] && [error code] == NSURLErrorCancelled) {
        return NPRES_USER_BREAK;
    }
    return NPRES_NETWORK_ERR;
}

- (NSError *)_pluginCancelledConnectionError
{
    return [[[NSError alloc] _initWithPluginErrorCode:WebKitErrorPlugInCancelledConnection
                                           contentURL:responseURL != nil ? responseURL : requestURL
                                        pluginPageURL:nil
                                           pluginName:[[pluginView pluginPackage] name]
                                             MIMEType:MIMEType] autorelease];
}

- (NSError *)errorForReason:(NPReason)theReason
{
    if (theReason == NPRES_DONE) {
        return nil;
    }
    if (theReason == NPRES_USER_BREAK) {
        return [NSError _webKitErrorWithDomain:NSURLErrorDomain
                                          code:NSURLErrorCancelled 
                                           URL:responseURL != nil ? responseURL : requestURL];
    }
    return [self _pluginCancelledConnectionError];
}

- (id)initWithRequestURL:(NSURL *)theRequestURL
                  plugin:(NPP)thePlugin
              notifyData:(void *)theNotifyData
        sendNotification:(BOOL)flag
{
    [super init];
 
    // Temporarily set isTerminated to YES to avoid assertion failure in dealloc in case we are released in this method.
    isTerminated = YES;

    if (theRequestURL == nil || thePlugin == NULL) {
        [self release];
        return nil;
    }
    
    [self setRequestURL:theRequestURL];
    [self setPlugin:thePlugin];
    notifyData = theNotifyData;
    sendNotification = flag;
    fileDescriptor = -1;

    streams().add(&stream, thePlugin);
    
    isTerminated = NO;
    
    return self;
}

- (void)dealloc
{
    ASSERT(!plugin);
    ASSERT(isTerminated);
    ASSERT(stream.ndata == nil);

    // The stream file should have been deleted, and the path freed, in -_destroyStream
    ASSERT(!path);
    ASSERT(fileDescriptor == -1);

    [requestURL release];
    [responseURL release];
    [MIMEType release];
    [pluginView release];
    [deliveryData release];
    
    free((void *)stream.url);
    free(path);
    free(headers);

    streams().remove(&stream);

    [super dealloc];
}

- (void)finalize
{
    ASSERT_MAIN_THREAD();
    ASSERT(isTerminated);
    ASSERT(stream.ndata == nil);

    // The stream file should have been deleted, and the path freed, in -_destroyStream
    ASSERT(!path);
    ASSERT(fileDescriptor == -1);

    free((void *)stream.url);
    free(path);
    free(headers);

    streams().remove(&stream);

    [super finalize];
}

- (uint16)transferMode
{
    return transferMode;
}

- (NPP)plugin
{
    return plugin;
}

- (void)setRequestURL:(NSURL *)theRequestURL
{
    [theRequestURL retain];
    [requestURL release];
    requestURL = theRequestURL;
}

- (void)setResponseURL:(NSURL *)theResponseURL
{
    [theResponseURL retain];
    [responseURL release];
    responseURL = theResponseURL;
}

- (void)setPlugin:(NPP)thePlugin
{
    if (thePlugin) {
        plugin = thePlugin;
        pluginView = [(WebBaseNetscapePluginView *)plugin->ndata retain];
        WebNetscapePluginPackage *pluginPackage = [pluginView pluginPackage];
        NPP_NewStream = [pluginPackage NPP_NewStream];
        NPP_WriteReady = [pluginPackage NPP_WriteReady];
        NPP_Write = [pluginPackage NPP_Write];
        NPP_StreamAsFile = [pluginPackage NPP_StreamAsFile];
        NPP_DestroyStream = [pluginPackage NPP_DestroyStream];
        NPP_URLNotify = [pluginPackage NPP_URLNotify];
    } else {
        WebBaseNetscapePluginView *view = pluginView;

        plugin = NULL;
        NPP_NewStream = NULL;
        NPP_WriteReady = NULL;
        NPP_Write = NULL;
        NPP_StreamAsFile = NULL;
        NPP_DestroyStream = NULL;
        NPP_URLNotify = NULL;
        pluginView = nil;

        [view disconnectStream:self];
        [view release];
    }
}

- (void)setMIMEType:(NSString *)theMIMEType
{
    [theMIMEType retain];
    [MIMEType release];
    MIMEType = theMIMEType;
}

- (void)startStreamResponseURL:(NSURL *)URL
         expectedContentLength:(long long)expectedContentLength
              lastModifiedDate:(NSDate *)lastModifiedDate
                      MIMEType:(NSString *)theMIMEType
                       headers:(NSData *)theHeaders
{
    ASSERT(!isTerminated);
    
    [self setResponseURL:URL];
    [self setMIMEType:theMIMEType];
    
    free((void *)stream.url);
    stream.url = strdup([responseURL _web_URLCString]);

    stream.ndata = self;
    stream.end = expectedContentLength > 0 ? (uint32)expectedContentLength : 0;
    stream.lastmodified = (uint32)[lastModifiedDate timeIntervalSince1970];
    stream.notifyData = notifyData;

    if (theHeaders) {
        unsigned len = [theHeaders length];
        headers = (char*) malloc(len + 1);
        [theHeaders getBytes:headers];
        headers[len] = 0;
        stream.headers = headers;
    }
    
    transferMode = NP_NORMAL;
    offset = 0;
    reason = WEB_REASON_NONE;
    // FIXME: If WebNetscapePluginStream called our initializer we wouldn't have to do this here.
    fileDescriptor = -1;

    // FIXME: Need a way to check if stream is seekable

    WebBaseNetscapePluginView *pv = pluginView;
    [pv willCallPlugInFunction];
    NPError npErr = NPP_NewStream(plugin, (char *)[MIMEType UTF8String], &stream, NO, &transferMode);
    [pv didCallPlugInFunction];
    LOG(Plugins, "NPP_NewStream URL=%@ MIME=%@ error=%d", responseURL, MIMEType, npErr);

    if (npErr != NPERR_NO_ERROR) {
        LOG_ERROR("NPP_NewStream failed with error: %d responseURL: %@", npErr, responseURL);
        // Calling cancelLoadWithError: cancels the load, but doesn't call NPP_DestroyStream.
        [self cancelLoadWithError:[self _pluginCancelledConnectionError]];
        return;
    }

    switch (transferMode) {
        case NP_NORMAL:
            LOG(Plugins, "Stream type: NP_NORMAL");
            break;
        case NP_ASFILEONLY:
            LOG(Plugins, "Stream type: NP_ASFILEONLY");
            break;
        case NP_ASFILE:
            LOG(Plugins, "Stream type: NP_ASFILE");
            break;
        case NP_SEEK:
            LOG_ERROR("Stream type: NP_SEEK not yet supported");
            [self cancelLoadAndDestroyStreamWithError:[self _pluginCancelledConnectionError]];
            break;
        default:
            LOG_ERROR("unknown stream type");
    }
}

- (void)startStreamWithResponse:(NSURLResponse *)r
{
    NSMutableData *theHeaders = nil;
    long long expectedContentLength = [r expectedContentLength];

    if ([r isKindOfClass:[NSHTTPURLResponse class]]) {
        NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)r;
        theHeaders = [NSMutableData dataWithCapacity:1024];
        
        // FIXME: it would be nice to be able to get the raw HTTP header block.
        // This includes the HTTP version, the real status text,
        // all headers in their original order and including duplicates,
        // and all original bytes verbatim, rather than sent through Unicode translation.
        // Unfortunately NSHTTPURLResponse doesn't provide access at that low a level.
        
        [theHeaders appendBytes:"HTTP " length:5];
        char statusStr[10];
        long statusCode = [httpResponse statusCode];
        snprintf(statusStr, sizeof(statusStr), "%ld", statusCode);
        [theHeaders appendBytes:statusStr length:strlen(statusStr)];
        [theHeaders appendBytes:" OK\n" length:4];

        // HACK: pass the headers through as UTF-8.
        // This is not the intended behavior; we're supposed to pass original bytes verbatim.
        // But we don't have the original bytes, we have NSStrings built by the URL loading system.
        // It hopefully shouldn't matter, since RFC2616/RFC822 require ASCII-only headers,
        // but surely someone out there is using non-ASCII characters, and hopefully UTF-8 is adequate here.
        // It seems better than NSASCIIStringEncoding, which will lose information if non-ASCII is used.

        NSDictionary *headerDict = [httpResponse allHeaderFields];
        NSArray *keys = [[headerDict allKeys] sortedArrayUsingSelector:@selector(caseInsensitiveCompare:)];
        NSEnumerator *i = [keys objectEnumerator];
        NSString *k;
        while ((k = [i nextObject]) != nil) {
            NSString *v = [headerDict objectForKey:k];
            [theHeaders appendData:[k dataUsingEncoding:NSUTF8StringEncoding]];
            [theHeaders appendBytes:": " length:2];
            [theHeaders appendData:[v dataUsingEncoding:NSUTF8StringEncoding]];
            [theHeaders appendBytes:"\n" length:1];
        }

        // If the content is encoded (most likely compressed), then don't send its length to the plugin,
        // which is only interested in the decoded length, not yet known at the moment.
        // <rdar://problem/4470599> tracks a request for -[NSURLResponse expectedContentLength] to incorporate this logic.
        NSString *contentEncoding = (NSString *)[[(NSHTTPURLResponse *)r allHeaderFields] objectForKey:@"Content-Encoding"];
        if (contentEncoding && ![contentEncoding isEqualToString:@"identity"])
            expectedContentLength = -1;

        // startStreamResponseURL:... will null-terminate.
    }

    [self startStreamResponseURL:[r URL]
           expectedContentLength:expectedContentLength
                lastModifiedDate:WKGetNSURLResponseLastModifiedDate(r)
                        MIMEType:[r MIMEType]
                         headers:theHeaders];
}

- (void)_destroyStream
{
    if (isTerminated)
        return;

    [self retain];

    ASSERT(reason != WEB_REASON_NONE);
    ASSERT([deliveryData length] == 0);
    
    [NSObject cancelPreviousPerformRequestsWithTarget:self selector:@selector(_deliverData) object:nil];

    if (stream.ndata != nil) {
        if (reason == NPRES_DONE && (transferMode == NP_ASFILE || transferMode == NP_ASFILEONLY)) {
            ASSERT(fileDescriptor == -1);
            ASSERT(path != NULL);
            NSString *carbonPath = CarbonPathFromPOSIXPath(path);
            ASSERT(carbonPath != NULL);
            WebBaseNetscapePluginView *pv = pluginView;
            [pv willCallPlugInFunction];
            NPP_StreamAsFile(plugin, &stream, [carbonPath fileSystemRepresentation]);
            [pv didCallPlugInFunction];
            LOG(Plugins, "NPP_StreamAsFile responseURL=%@ path=%s", responseURL, carbonPath);
        }

        if (path) {
            // Delete the file after calling NPP_StreamAsFile(), instead of in -dealloc/-finalize.  It should be OK
            // to delete the file here -- NPP_StreamAsFile() is always called immediately before NPP_DestroyStream()
            // (the stream destruction function), so there can be no expectation that a plugin will read the stream
            // file asynchronously after NPP_StreamAsFile() is called.
            unlink([path fileSystemRepresentation]);
            [path release];
            path = nil;

            if (isTerminated)
                goto exit;
        }

        if (fileDescriptor != -1) {
            // The file may still be open if we are destroying the stream before it completed loading.
            close(fileDescriptor);
            fileDescriptor = -1;
        }

        NPError npErr;
        WebBaseNetscapePluginView *pv = pluginView;
        [pv willCallPlugInFunction];
        npErr = NPP_DestroyStream(plugin, &stream, reason);
        [pv didCallPlugInFunction];
        LOG(Plugins, "NPP_DestroyStream responseURL=%@ error=%d", responseURL, npErr);

        free(headers);
        headers = NULL;
        stream.headers = NULL;

        stream.ndata = nil;

        if (isTerminated)
            goto exit;
    }

    if (sendNotification) {
        // NPP_URLNotify expects the request URL, not the response URL.
        WebBaseNetscapePluginView *pv = pluginView;
        [pv willCallPlugInFunction];
        NPP_URLNotify(plugin, [requestURL _web_URLCString], reason, notifyData);
        [pv didCallPlugInFunction];
        LOG(Plugins, "NPP_URLNotify requestURL=%@ reason=%d", requestURL, reason);
    }

    isTerminated = YES;

    [self setPlugin:NULL];

exit:
    [self release];
}

- (void)_destroyStreamWithReason:(NPReason)theReason
{
    reason = theReason;
    if (reason != NPRES_DONE) {
        // Stop any pending data from being streamed.
        [deliveryData setLength:0];
    } else if ([deliveryData length] > 0) {
        // There is more data to be streamed, don't destroy the stream now.
        return;
    }
    [self _destroyStream];
    ASSERT(stream.ndata == nil);
}

- (void)cancelLoadWithError:(NSError *)error
{
    // Overridden by subclasses.
    ASSERT_NOT_REACHED();
}

- (void)destroyStreamWithError:(NSError *)error
{
    [self _destroyStreamWithReason:[[self class] reasonForError:error]];
}

- (void)cancelLoadAndDestroyStreamWithError:(NSError *)error
{
    [self retain];
    [self cancelLoadWithError:error];
    [self destroyStreamWithError:error];
    [self setPlugin:NULL];
    [self release];
}

- (void)_deliverData
{
    if (!stream.ndata || [deliveryData length] == 0)
        return;

    [self retain];

    int32 totalBytes = [deliveryData length];
    int32 totalBytesDelivered = 0;

    while (totalBytesDelivered < totalBytes) {
        WebBaseNetscapePluginView *pv = pluginView;
        [pv willCallPlugInFunction];
        int32 deliveryBytes = NPP_WriteReady(plugin, &stream);
        [pv didCallPlugInFunction];
        LOG(Plugins, "NPP_WriteReady responseURL=%@ bytes=%d", responseURL, deliveryBytes);

        if (isTerminated)
            goto exit;

        if (deliveryBytes <= 0) {
            // Plug-in can't receive anymore data right now. Send it later.
            [self performSelector:@selector(_deliverData) withObject:nil afterDelay:0];
            break;
        } else {
            deliveryBytes = MIN(deliveryBytes, totalBytes - totalBytesDelivered);
            NSData *subdata = [deliveryData subdataWithRange:NSMakeRange(totalBytesDelivered, deliveryBytes)];
            pv = pluginView;
            [pv willCallPlugInFunction];
            deliveryBytes = NPP_Write(plugin, &stream, offset, [subdata length], (void *)[subdata bytes]);
            [pv didCallPlugInFunction];
            if (deliveryBytes < 0) {
                // Netscape documentation says that a negative result from NPP_Write means cancel the load.
                [self cancelLoadAndDestroyStreamWithError:[self _pluginCancelledConnectionError]];
                return;
            }
            deliveryBytes = MIN((unsigned)deliveryBytes, [subdata length]);
            offset += deliveryBytes;
            totalBytesDelivered += deliveryBytes;
            LOG(Plugins, "NPP_Write responseURL=%@ bytes=%d total-delivered=%d/%d", responseURL, deliveryBytes, offset, stream.end);
        }
    }

    if (totalBytesDelivered > 0) {
        if (totalBytesDelivered < totalBytes) {
            NSMutableData *newDeliveryData = [[NSMutableData alloc] initWithCapacity:totalBytes - totalBytesDelivered];
            [newDeliveryData appendBytes:(char *)[deliveryData bytes] + totalBytesDelivered length:totalBytes - totalBytesDelivered];
            [deliveryData release];
            deliveryData = newDeliveryData;
        } else {
            [deliveryData setLength:0];
            if (reason != WEB_REASON_NONE) {
                [self _destroyStream];
            }
        }
    }

exit:
    [self release];
}

- (void)_deliverDataToFile:(NSData *)data
{
    if (fileDescriptor == -1 && !path) {
        NSString *temporaryFileMask = [NSTemporaryDirectory() stringByAppendingPathComponent:@"WebKitPlugInStreamXXXXXX"];
        char *temporaryFileName = strdup([temporaryFileMask fileSystemRepresentation]);
        fileDescriptor = mkstemp(temporaryFileName);
        if (fileDescriptor == -1) {
            LOG_ERROR("Can't create a temporary file.");
            // This is not a network error, but the only error codes are "network error" and "user break".
            [self _destroyStreamWithReason:NPRES_NETWORK_ERR];
            free(temporaryFileName);
            return;
        }

        path = [[NSString stringWithUTF8String:temporaryFileName] retain];
        free(temporaryFileName);
    }

    int dataLength = [data length];
    if (!dataLength)
        return;

    int byteCount = write(fileDescriptor, [data bytes], dataLength);
    if (byteCount != dataLength) {
        // This happens only rarely, when we are out of disk space or have a disk I/O error.
        LOG_ERROR("error writing to temporary file, errno %d", errno);
        close(fileDescriptor);
        fileDescriptor = -1;

        // This is not a network error, but the only error codes are "network error" and "user break".
        [self _destroyStreamWithReason:NPRES_NETWORK_ERR];
        [path release];
        path = nil;
    }
}

- (void)finishedLoading
{
    if (!stream.ndata)
        return;

    if (transferMode == NP_ASFILE || transferMode == NP_ASFILEONLY) {
        // Fake the delivery of an empty data to ensure that the file has been created
        [self _deliverDataToFile:[NSData data]];
        if (fileDescriptor != -1)
            close(fileDescriptor);
        fileDescriptor = -1;
    }

    [self _destroyStreamWithReason:NPRES_DONE];
}

- (void)receivedData:(NSData *)data
{
    ASSERT([data length] > 0);
    
    if (transferMode != NP_ASFILEONLY) {
        if (!deliveryData) {
            deliveryData = [[NSMutableData alloc] initWithCapacity:[data length]];
        }
        [deliveryData appendData:data];
        [self _deliverData];
    }
    if (transferMode == NP_ASFILE || transferMode == NP_ASFILEONLY)
        [self _deliverDataToFile:data];

}

@end

static NSString *CarbonPathFromPOSIXPath(NSString *posixPath)
{
    // Doesn't add a trailing colon for directories; this is a problem for paths to a volume,
    // so this function would need to be revised if we ever wanted to call it with that.

    CFURLRef url = (CFURLRef)[NSURL fileURLWithPath:posixPath];
    if (!url)
        return nil;

    return WebCFAutorelease(CFURLCopyFileSystemPath(url, kCFURLHFSPathStyle));
}

#endif
