/*
 * Copyright (C) 2005, 2006 Apple Computer, Inc.  All rights reserved.
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


#import <WebKit/WebPluginController.h>

#import <Foundation/NSURLRequest.h>
#import <WebCore/Frame.h>
#import <WebCore/FrameLoader.h>
#import <WebCore/ResourceRequest.h>
#import <WebCore/PlatformString.h>
#import <WebCore/WebCoreFrameBridge.h>
#import <WebCore/DocumentLoader.h>
#import <WebKit/WebDataSourceInternal.h>
#import <WebKit/WebFrameBridge.h>
#import <WebKit/WebFrameInternal.h>
#import <WebKit/WebFrameView.h>
#import <WebKit/WebHTMLViewPrivate.h>
#import <WebKit/WebKitErrorsPrivate.h>
#import <WebKit/WebKitLogging.h>
#import <WebKit/WebNSURLExtras.h>
#import <WebKit/WebNSViewExtras.h>
#import <WebKit/WebPlugin.h>
#import <WebKit/WebPluginContainer.h>
#import <WebKit/WebPluginContainerCheck.h>
#import <WebKit/WebPluginPackage.h>
#import <WebKit/WebPluginPrivate.h>
#import <WebKit/WebPluginViewFactory.h>
#import <WebKit/WebUIDelegate.h>
#import <WebKit/WebViewInternal.h>

using namespace WebCore;

@interface NSView (PluginSecrets)
- (void)setContainingWindow:(NSWindow *)w;
@end

// For compatibility only.
@interface NSObject (OldPluginAPI)
+ (NSView *)pluginViewWithArguments:(NSDictionary *)arguments;
@end

@interface NSView (OldPluginAPI)
- (void)pluginInitialize;
- (void)pluginStart;
- (void)pluginStop;
- (void)pluginDestroy;
@end

static NSMutableSet *pluginViews = nil;

@implementation WebPluginController

+ (NSView *)plugInViewWithArguments:(NSDictionary *)arguments fromPluginPackage:(WebPluginPackage *)pluginPackage
{
    [pluginPackage load];
    Class viewFactory = [pluginPackage viewFactory];
    
    NSView *view = nil;

    if ([viewFactory respondsToSelector:@selector(plugInViewWithArguments:)]) {
        KJS::JSLock::DropAllLocks dropAllLocks;
        view = [viewFactory plugInViewWithArguments:arguments];
    } else if ([viewFactory respondsToSelector:@selector(pluginViewWithArguments:)]) {
        KJS::JSLock::DropAllLocks dropAllLocks;
        view = [viewFactory pluginViewWithArguments:arguments];
    }
    
    if (view == nil) {
        return nil;
    }
    
    if (pluginViews == nil) {
        pluginViews = [[NSMutableSet alloc] init];
    }
    [pluginViews addObject:view];
    
    return view;
}

+ (BOOL)isPlugInView:(NSView *)view
{
    return [pluginViews containsObject:view];
}

- (id)initWithDocumentView:(NSView *)view
{
    [super init];
    _documentView = view;
    _views = [[NSMutableArray alloc] init];
    _checksInProgress = (NSMutableSet *)CFMakeCollectable(CFSetCreateMutable(NULL, 0, NULL));
    return self;
}

- (void)setDataSource:(WebDataSource *)dataSource
{
    _dataSource = dataSource;    
}

- (void)dealloc
{
    [_views release];
    [_checksInProgress release];
    [super dealloc];
}

- (void)startAllPlugins
{
    if (_started)
        return;
    
    if ([_views count] > 0)
        LOG(Plugins, "starting WebKit plugins : %@", [_views description]);
    
    int i, count = [_views count];
    for (i = 0; i < count; i++) {
        id aView = [_views objectAtIndex:i];
        if ([aView respondsToSelector:@selector(webPlugInStart)]) {
            KJS::JSLock::DropAllLocks dropAllLocks;
            [aView webPlugInStart];
        } else if ([aView respondsToSelector:@selector(pluginStart)]) {
            KJS::JSLock::DropAllLocks dropAllLocks;
            [aView pluginStart];
        }
    }
    _started = YES;
}

- (void)stopAllPlugins
{
    if (!_started)
        return;

    if ([_views count] > 0) {
        LOG(Plugins, "stopping WebKit plugins: %@", [_views description]);
    }
    
    int i, count = [_views count];
    for (i = 0; i < count; i++) {
        id aView = [_views objectAtIndex:i];
        if ([aView respondsToSelector:@selector(webPlugInStop)]) {
            KJS::JSLock::DropAllLocks dropAllLocks;
            [aView webPlugInStop];
        } else if ([aView respondsToSelector:@selector(pluginStop)]) {
            KJS::JSLock::DropAllLocks dropAllLocks;
            [aView pluginStop];
        }
    }
    _started = NO;
}

- (void)addPlugin:(NSView *)view
{
    if (!_documentView) {
        LOG_ERROR("can't add a plug-in to a defunct WebPluginController");
        return;
    }
    
    if (![_views containsObject:view]) {
        [_views addObject:view];
        
        LOG(Plugins, "initializing plug-in %@", view);
        if ([view respondsToSelector:@selector(webPlugInInitialize)]) {
            KJS::JSLock::DropAllLocks dropAllLocks;
            [view webPlugInInitialize];
        } else if ([view respondsToSelector:@selector(pluginInitialize)]) {
            KJS::JSLock::DropAllLocks dropAllLocks;
            [view pluginInitialize];
        }

        if (_started) {
            LOG(Plugins, "starting plug-in %@", view);
            if ([view respondsToSelector:@selector(webPlugInStart)]) {
                KJS::JSLock::DropAllLocks dropAllLocks;
                [view webPlugInStart];
            } else if ([view respondsToSelector:@selector(pluginStart)]) {
                KJS::JSLock::DropAllLocks dropAllLocks;
                [view pluginStart];
            }
            
            if ([view respondsToSelector:@selector(setContainingWindow:)]) {
                KJS::JSLock::DropAllLocks dropAllLocks;
                [view setContainingWindow:[_documentView window]];
            }
        }
    }
}

- (void)destroyPlugin:(NSView *)view
{
    if ([_views containsObject:view]) {
        if (_started) {
            if ([view respondsToSelector:@selector(webPlugInStop)]) {
                KJS::JSLock::DropAllLocks dropAllLocks;
                [view webPlugInStop];
            } else if ([view respondsToSelector:@selector(pluginStop)]) {
                KJS::JSLock::DropAllLocks dropAllLocks;
                [view pluginStop];
            }
        }
        
        if ([view respondsToSelector:@selector(webPlugInDestroy)]) {
            KJS::JSLock::DropAllLocks dropAllLocks;
            [view webPlugInDestroy];
        } else if ([view respondsToSelector:@selector(pluginDestroy)]) {
            KJS::JSLock::DropAllLocks dropAllLocks;
            [view pluginDestroy];
        }
        
        if (Frame* frame = core([self webFrame]))
            frame->cleanupScriptObjectsForPlugin(self);
        
        [pluginViews removeObject:view];
        [_views removeObject:view];
    }
}

- (void)_webPluginContainerCancelCheckIfAllowedToLoadRequest:(id)checkIdentifier
{
    [checkIdentifier cancel];
    [_checksInProgress removeObject:checkIdentifier];
}

static void cancelOutstandingCheck(const void *item, void *context)
{
    [(id)item cancel];
}

- (void)_cancelOutstandingChecks
{
    if (_checksInProgress) {
        CFSetApplyFunction((CFSetRef)_checksInProgress, cancelOutstandingCheck, NULL);
        [_checksInProgress release];
        _checksInProgress = nil;
    }
}

- (void)destroyAllPlugins
{    
    [self stopAllPlugins];

    if ([_views count] > 0) {
        LOG(Plugins, "destroying WebKit plugins: %@", [_views description]);
    }

    [self _cancelOutstandingChecks];
    
    int i, count = [_views count];
    for (i = 0; i < count; i++) {
        id aView = [_views objectAtIndex:i];
        if ([aView respondsToSelector:@selector(webPlugInDestroy)]) {
            KJS::JSLock::DropAllLocks dropAllLocks;
            [aView webPlugInDestroy];
        } else if ([aView respondsToSelector:@selector(pluginDestroy)]) {
            KJS::JSLock::DropAllLocks dropAllLocks;
            [aView pluginDestroy];
        }
        
        if (Frame* frame = core([self webFrame]))
            frame->cleanupScriptObjectsForPlugin(self);
        
        [pluginViews removeObject:aView];
    }
    [_views makeObjectsPerformSelector:@selector(removeFromSuperviewWithoutNeedingDisplay)];
    [_views release];
    _views = nil;

    _documentView = nil;
}

- (id)_webPluginContainerCheckIfAllowedToLoadRequest:(NSURLRequest *)request inFrame:(NSString *)target resultObject:(id)obj selector:(SEL)selector
{
    WebPluginContainerCheck *check = [WebPluginContainerCheck checkWithRequest:request target:target resultObject:obj selector:selector controller:self];
    [_checksInProgress addObject:check];
    [check start];

    return check;
}

- (void)webPlugInContainerLoadRequest:(NSURLRequest *)request inFrame:(NSString *)target
{
    if (!request) {
        LOG_ERROR("nil URL passed");
        return;
    }
    if (!_documentView) {
        LOG_ERROR("could not load URL %@ because plug-in has already been destroyed", request);
        return;
    }
    WebFrame *frame = [_dataSource webFrame];
    if (!frame) {
        LOG_ERROR("could not load URL %@ because plug-in has already been stopped", request);
        return;
    }
    if (!target) {
        target = @"_top";
    }
    NSString *JSString = [[request URL] _webkit_scriptIfJavaScriptURL];
    if (JSString) {
        if ([frame findFrameNamed:target] != frame) {
            LOG_ERROR("JavaScript requests can only be made on the frame that contains the plug-in");
            return;
        }
        [[frame _bridge] stringByEvaluatingJavaScriptFromString:JSString];
    } else {
        if (!request) {
            LOG_ERROR("could not load URL %@", [request URL]);
            return;
        }
        [frame _frameLoader]->load(request, target);
    }
}

// For compatibility only.
- (void)showURL:(NSURL *)URL inFrame:(NSString *)target
{
    [self webPlugInContainerLoadRequest:[NSURLRequest requestWithURL:URL] inFrame:target];
}

- (void)webPlugInContainerShowStatus:(NSString *)message
{
    if (!message) {
        message = @"";
    }
    if (!_documentView) {
        LOG_ERROR("could not show status message (%@) because plug-in has already been destroyed", message);
        return;
    }
    WebView *v = [_dataSource _webView];
    [[v _UIDelegateForwarder] webView:v setStatusText:message];
}

// For compatibility only.
- (void)showStatus:(NSString *)message
{
    [self webPlugInContainerShowStatus:message];
}

- (NSColor *)webPlugInContainerSelectionColor
{
    bool primary = true;
    if (Frame* frame = core([self webFrame]))
        primary = frame->selectionController()->isFocusedAndActive();
    return primary ? [NSColor selectedTextBackgroundColor] : [NSColor secondarySelectedControlColor];
}

// For compatibility only.
- (NSColor *)selectionColor
{
    return [self webPlugInContainerSelectionColor];
}

- (WebFrame *)webFrame
{
    return [_dataSource webFrame];
}

- (WebFrameBridge *)bridge
{
    return [[self webFrame] _bridge];
}

- (WebView *)webView
{
    return [[self webFrame] webView];
}

- (NSString *)URLPolicyCheckReferrer
{
    NSURL *responseURL = [[[[self webFrame] _dataSource] response] URL];
    ASSERT(responseURL);
    return [responseURL _web_originalDataAsString];
}

- (void)pluginView:(NSView *)pluginView receivedResponse:(NSURLResponse *)response
{    
    if ([pluginView respondsToSelector:@selector(webPlugInMainResourceDidReceiveResponse:)])
        [pluginView webPlugInMainResourceDidReceiveResponse:response];
    else {
        // Cancel the load since this plug-in does its own loading.
        // FIXME: See <rdar://problem/4258008> for a problem with this.
        NSError *error = [[NSError alloc] _initWithPluginErrorCode:WebKitErrorPlugInWillHandleLoad
                                                        contentURL:[response URL]
                                                     pluginPageURL:nil
                                                        pluginName:nil // FIXME: Get this from somewhere
                                                          MIMEType:[response MIMEType]];
        [_dataSource _documentLoader]->cancelMainResourceLoad(error);
        [error release];
    }        
}

- (void)pluginView:(NSView *)pluginView receivedData:(NSData *)data
{
    if ([pluginView respondsToSelector:@selector(webPlugInMainResourceDidReceiveData:)])
        [pluginView webPlugInMainResourceDidReceiveData:data];
}

- (void)pluginView:(NSView *)pluginView receivedError:(NSError *)error
{
    if ([pluginView respondsToSelector:@selector(webPlugInMainResourceDidFailWithError:)])
        [pluginView webPlugInMainResourceDidFailWithError:error];
}

- (void)pluginViewFinishedLoading:(NSView *)pluginView
{
    if ([pluginView respondsToSelector:@selector(webPlugInMainResourceDidFinishLoading)])
        [pluginView webPlugInMainResourceDidFinishLoading];
}

@end
