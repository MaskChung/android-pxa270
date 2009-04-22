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

#import "WebFrameBridge.h"

#import "WebBackForwardList.h"
#import "WebBaseNetscapePluginView.h"
#import "WebBasePluginPackage.h"
#import "WebDataSourceInternal.h"
#import "WebDefaultUIDelegate.h"
#import "WebEditingDelegate.h"
#import "WebFormDelegate.h"
#import "WebFrameInternal.h"
#import "WebFrameLoadDelegate.h"
#import "WebFrameLoaderClient.h"
#import "WebFrameViewInternal.h"
#import "WebHTMLRepresentationPrivate.h"
#import "WebHTMLViewInternal.h"
#import "WebHistoryItemInternal.h"
#import "WebHistoryItemPrivate.h"
#import "WebJavaPlugIn.h"
#import "WebJavaScriptTextInputPanel.h"
#import "WebKitErrorsPrivate.h"
#import "WebKitLogging.h"
#import "WebKitNSStringExtras.h"
#import "WebKitPluginContainerView.h"
#import "WebKitStatisticsPrivate.h"
#import "WebKitSystemBits.h"
#import "WebLocalizableStrings.h"
#import "WebNSObjectExtras.h"
#import "WebNSURLExtras.h"
#import "WebNSURLRequestExtras.h"
#import "WebNSViewExtras.h"
#import "WebNetscapePluginEmbeddedView.h"
#import "WebNetscapePluginPackage.h"
#import "WebNullPluginView.h"
#import "WebPlugin.h"
#import "WebPluginController.h"
#import "WebPluginDatabase.h"
#import "WebPluginPackage.h"
#import "WebPluginViewFactoryPrivate.h"
#import "WebPreferencesPrivate.h"
#import "WebResourcePrivate.h"
#import "WebScriptDebugServerPrivate.h"
#import "WebUIDelegatePrivate.h"
#import "WebViewInternal.h"
#import <Foundation/NSURLConnection.h>
#import <Foundation/NSURLRequest.h>
#import <Foundation/NSURLResponse.h>
#import <JavaScriptCore/Assertions.h>
#import <JavaScriptCore/JSLock.h>
#import <JavaScriptCore/object.h>
#import <JavaVM/jni.h>
#import <WebCore/Cache.h>
#import <WebCore/Document.h>
#import <WebCore/DocumentLoader.h>
#import <WebCore/DragController.h>
#import <WebCore/Element.h>
#import <WebCore/FoundationExtras.h>
#import <WebCore/Frame.h>
#import <WebCore/FrameLoader.h>
#import <WebCore/FrameLoaderClient.h>
#import <WebCore/FrameTree.h>
#import <WebCore/HTMLFrameOwnerElement.h>
#import <WebCore/Page.h>
#import <WebCore/ResourceLoader.h>
#import <WebCore/SubresourceLoader.h>
#import <WebCore/WebCoreObjCExtras.h>
#import <WebKitSystemInterface.h>
#import <wtf/RefPtr.h>
#import <WebCore/MIMETypeRegistry.h>

// For compatibility with old SPI. 
@interface NSView (OldWebPlugin)
- (void)setIsSelected:(BOOL)f;
@end

@interface NSView (JavaPluginSecrets)
- (jobject)pollForAppletInWindow:(NSWindow *)window;
@end

using namespace WebCore;

NSString *WebPluginBaseURLKey =     @"WebPluginBaseURL";
NSString *WebPluginAttributesKey =  @"WebPluginAttributes";
NSString *WebPluginContainerKey =   @"WebPluginContainer";

#define KeyboardUIModeDidChangeNotification @"com.apple.KeyboardUIModeDidChange"
#define AppleKeyboardUIMode CFSTR("AppleKeyboardUIMode")
#define UniversalAccessDomain CFSTR("com.apple.universalaccess")

@implementation WebFrameBridge

#ifndef BUILDING_ON_TIGER
+ (void)initialize
{
    WebCoreObjCFinalizeOnMainThread(self);
}
#endif

- (WebView *)webView
{
    if (!m_frame)
        return nil;
    
    return kit(m_frame->page());
}

- (void)finishInitializingWithPage:(Page*)page frameName:(NSString *)name frameView:(WebFrameView *)frameView ownerElement:(HTMLFrameOwnerElement*)ownerElement
{
    ++WebBridgeCount;

    WebView *webView = kit(page);

    _frame = [[WebFrame alloc] _initWithWebFrameView:frameView webView:webView bridge:self];

    m_frame = new Frame(page, ownerElement, new WebFrameLoaderClient(_frame));
    m_frame->setBridge(self);
    m_frame->tree()->setName(name);
    m_frame->init();
    
    [self setTextSizeMultiplier:[webView textSizeMultiplier]];
}

- (id)initMainFrameWithPage:(Page*)page frameName:(NSString *)name frameView:(WebFrameView *)frameView
{
    self = [super init];
    [self finishInitializingWithPage:page frameName:name frameView:frameView ownerElement:0];
    return self;
}

- (id)initSubframeWithOwnerElement:(HTMLFrameOwnerElement*)ownerElement frameName:(NSString *)name frameView:(WebFrameView *)frameView
{
    self = [super init];
    [self finishInitializingWithPage:ownerElement->document()->frame()->page() frameName:name frameView:frameView ownerElement:ownerElement];
    return self;
}

- (void)fini
{
    if (_keyboardUIModeAccessed) {
        [[NSDistributedNotificationCenter defaultCenter] 
            removeObserver:self name:KeyboardUIModeDidChangeNotification object:nil];
        [[NSNotificationCenter defaultCenter] 
            removeObserver:self name:WebPreferencesChangedNotification object:nil];
    }

    ASSERT(_frame == nil);
    --WebBridgeCount;
}

- (void)dealloc
{
    [lastDashboardRegions release];
    [_frame release];
    
    [self fini];
    [super dealloc];
}

- (void)finalize
{
    ASSERT_MAIN_THREAD();
    [self fini];
    [super finalize];
}

- (WebPreferences *)_preferences
{
    return [[self webView] preferences];
}

- (void)_retrieveKeyboardUIModeFromPreferences:(NSNotification *)notification
{
    CFPreferencesAppSynchronize(UniversalAccessDomain);

    Boolean keyExistsAndHasValidFormat;
    int mode = CFPreferencesGetAppIntegerValue(AppleKeyboardUIMode, UniversalAccessDomain, &keyExistsAndHasValidFormat);
    
    // The keyboard access mode is reported by two bits:
    // Bit 0 is set if feature is on
    // Bit 1 is set if full keyboard access works for any control, not just text boxes and lists
    // We require both bits to be on.
    // I do not know that we would ever get one bit on and the other off since
    // checking the checkbox in system preferences which is marked as "Turn on full keyboard access"
    // turns on both bits.
    _keyboardUIMode = (mode & 0x2) ? KeyboardAccessFull : KeyboardAccessDefault;
    
    // check for tabbing to links
    if ([[self _preferences] tabsToLinks])
        _keyboardUIMode = (KeyboardUIMode)(_keyboardUIMode | KeyboardAccessTabsToLinks);
}

- (KeyboardUIMode)keyboardUIMode
{
    if (!_keyboardUIModeAccessed) {
        _keyboardUIModeAccessed = YES;
        [self _retrieveKeyboardUIModeFromPreferences:nil];
        
        [[NSDistributedNotificationCenter defaultCenter] 
            addObserver:self selector:@selector(_retrieveKeyboardUIModeFromPreferences:) 
            name:KeyboardUIModeDidChangeNotification object:nil];

        [[NSNotificationCenter defaultCenter] 
            addObserver:self selector:@selector(_retrieveKeyboardUIModeFromPreferences:) 
                   name:WebPreferencesChangedNotification object:nil];
    }
    return _keyboardUIMode;
}

- (WebFrame *)webFrame
{
    return _frame;
}

- (WebCoreFrameBridge *)mainFrame
{
    ASSERT(_frame != nil);
    return [[[self webView] mainFrame] _bridge];
}

- (NSResponder *)firstResponder
{
    ASSERT(_frame != nil);
    WebView *webView = [self webView];
    return [[webView _UIDelegateForwarder] webViewFirstResponder:webView];
}

- (void)makeFirstResponder:(NSResponder *)view
{
    ASSERT(_frame != nil);
    WebView *webView = [self webView];
    ASSERT([view isKindOfClass:[NSView class]]);
    ASSERT([(NSView *)view window]);
    ASSERT([(NSView *)view window] == [webView window]);
    [webView _pushPerformingProgrammaticFocus];
    [[webView _UIDelegateForwarder] webView:webView makeFirstResponder:view];
    [webView _popPerformingProgrammaticFocus];
}

- (NSWindow *)window
{
    ASSERT(_frame != nil);
    return [[_frame frameView] window];
}

- (void)runOpenPanelForFileButtonWithResultListener:(id<WebCoreOpenPanelResultListener>)resultListener
{
    WebView *wv = [self webView];
    [[wv _UIDelegateForwarder] webView:wv runOpenPanelForFileButtonWithResultListener:(id<WebOpenPanelResultListener>)resultListener];
}

- (WebDataSource *)dataSource
{
    ASSERT(_frame != nil);
    WebDataSource *dataSource = [_frame _dataSource];

    ASSERT(dataSource != nil);

    return dataSource;
}

- (void)close
{
    [super close];
    [_frame release];
    _frame = nil;
}

- (Frame*)createChildFrameNamed:(NSString *)frameName 
                                      withURL:(NSURL *)URL
                                     referrer:(const String&)referrer
                                 ownerElement:(HTMLFrameOwnerElement*)ownerElement
                              allowsScrolling:(BOOL)allowsScrolling 
                                  marginWidth:(int)width
                                 marginHeight:(int)height
{
    ASSERT(_frame);
    
    WebFrameView *childView = [[WebFrameView alloc] initWithFrame:NSMakeRect(0,0,0,0)];
    [childView setAllowsScrolling:allowsScrolling];
    [childView _setMarginWidth:width];
    [childView _setMarginHeight:height];

    WebFrameBridge *newBridge = [[WebFrameBridge alloc] initSubframeWithOwnerElement:ownerElement frameName:frameName frameView:childView];
    [childView release];

    if (!newBridge)
        return 0;

    [_frame _addChild:[newBridge webFrame]];
    [newBridge release];

    RefPtr<Frame> newFrame = [newBridge _frame];
    
    [_frame _loadURL:URL referrer:referrer intoChild:kit(newFrame.get())];

    // The frame's onload handler may have removed it from the document.
    if (!newFrame->tree()->parent())
        return 0;

    return newFrame.get();
}

- (NSView *)pluginViewWithPackage:(WebPluginPackage *)pluginPackage
                   attributeNames:(NSArray *)attributeNames
                  attributeValues:(NSArray *)attributeValues
                          baseURL:(NSURL *)baseURL
                       DOMElement:(DOMElement *)element
                     loadManually:(BOOL)loadManually
{
    WebHTMLView *docView = (WebHTMLView *)[[_frame frameView] documentView];
    ASSERT([docView isKindOfClass:[WebHTMLView class]]);
        
    WebPluginController *pluginController = [docView _pluginController];
    
    // Store attributes in a dictionary so they can be passed to WebPlugins.
    NSMutableDictionary *attributes = [[NSMutableDictionary alloc] initWithObjects:attributeValues forKeys:attributeNames];
    
    [pluginPackage load];
    Class viewFactory = [pluginPackage viewFactory];
    
    NSView *view = nil;
    NSDictionary *arguments = nil;
    
    if ([viewFactory respondsToSelector:@selector(plugInViewWithArguments:)]) {
        arguments = [NSDictionary dictionaryWithObjectsAndKeys:
            baseURL, WebPlugInBaseURLKey,
            attributes, WebPlugInAttributesKey,
            pluginController, WebPlugInContainerKey,
            [NSNumber numberWithInt:loadManually ? WebPlugInModeFull : WebPlugInModeEmbed], WebPlugInModeKey,
            [NSNumber numberWithBool:!loadManually], WebPlugInShouldLoadMainResourceKey,
            element, WebPlugInContainingElementKey,
            nil];
        LOG(Plugins, "arguments:\n%@", arguments);
    } else if ([viewFactory respondsToSelector:@selector(pluginViewWithArguments:)]) {
        arguments = [NSDictionary dictionaryWithObjectsAndKeys:
            baseURL, WebPluginBaseURLKey,
            attributes, WebPluginAttributesKey,
            pluginController, WebPluginContainerKey,
            element, WebPlugInContainingElementKey,
            nil];
        LOG(Plugins, "arguments:\n%@", arguments);
    }

    view = [WebPluginController plugInViewWithArguments:arguments fromPluginPackage:pluginPackage];
    [attributes release];
    return view;
}

- (NSString *)valueForKey:(NSString *)key keys:(NSArray *)keys values:(NSArray *)values
{
    unsigned count = [keys count];
    unsigned i;
    for (i = 0; i < count; i++)
        if ([[keys objectAtIndex:i] _webkit_isCaseInsensitiveEqualToString:key])
            return [values objectAtIndex:i];
    return nil;
}

- (NSView *)viewForPluginWithFrame:(NSRect)frame
                               URL:(NSURL *)URL
                    attributeNames:(NSArray *)attributeNames
                   attributeValues:(NSArray *)attributeValues
                          MIMEType:(NSString *)MIMEType
                        DOMElement:(DOMElement *)element
                      loadManually:(BOOL)loadManually
{
    ASSERT([attributeNames count] == [attributeValues count]);

    WebBasePluginPackage *pluginPackage = nil;
    NSView *view = nil;
    int errorCode = 0;

    WebView *webView = [self webView];
    SEL selector = @selector(webView:plugInViewWithArguments:);

    if ([[webView UIDelegate] respondsToSelector:selector]) {
        NSMutableDictionary *attributes = [[NSMutableDictionary alloc] initWithObjects:attributeValues forKeys:attributeNames];
        NSDictionary *arguments = [[NSDictionary alloc] initWithObjectsAndKeys:
            attributes, WebPlugInAttributesKey,
            [NSNumber numberWithInt:loadManually ? WebPlugInModeFull : WebPlugInModeEmbed], WebPlugInModeKey,
            [NSNumber numberWithBool:!loadManually], WebPlugInShouldLoadMainResourceKey,
            element, WebPlugInContainingElementKey,
            URL, WebPlugInBaseURLKey, // URL might be nil, so add it last
            nil];

        view = CallUIDelegate(webView, selector, arguments);

        [attributes release];
        [arguments release];

        if (view)
            return view;
    }

    if ([MIMEType length] != 0)
        pluginPackage = [[self webView] _pluginForMIMEType:MIMEType];
    else
        MIMEType = nil;
    
    NSString *extension = [[URL path] pathExtension];
    if (!pluginPackage && [extension length] != 0) {
        pluginPackage = [[self webView] _pluginForExtension:extension];
        if (pluginPackage) {
            NSString *newMIMEType = [pluginPackage MIMETypeForExtension:extension];
            if ([newMIMEType length] != 0)
                MIMEType = newMIMEType;
        }
    }

    NSURL *baseURL = [self baseURL];
    if (pluginPackage) {
        if ([pluginPackage isKindOfClass:[WebPluginPackage class]]) {
            view = [self pluginViewWithPackage:(WebPluginPackage *)pluginPackage
                                attributeNames:attributeNames
                               attributeValues:attributeValues
                                       baseURL:baseURL
                                    DOMElement:element
                                  loadManually:loadManually];
            
        }
#ifndef __LP64__
        else if ([pluginPackage isKindOfClass:[WebNetscapePluginPackage class]]) {
            WebNetscapePluginEmbeddedView *embeddedView = [[[WebNetscapePluginEmbeddedView alloc] initWithFrame:frame
                                                           pluginPackage:(WebNetscapePluginPackage *)pluginPackage
                                                                     URL:URL
                                                                 baseURL:baseURL
                                                                MIMEType:MIMEType
                                                           attributeKeys:attributeNames
                                                         attributeValues:attributeValues
                                                            loadManually:loadManually
                                                              DOMElement:element] autorelease];
            view = embeddedView;
        } 
#endif
    } else
        errorCode = WebKitErrorCannotFindPlugIn;

    if (!errorCode && !view)
        errorCode = WebKitErrorCannotLoadPlugIn;

    if (errorCode) {
        NSString *pluginPage = [self valueForKey:@"pluginspage" keys:attributeNames values:attributeValues];
        NSURL *pluginPageURL = pluginPage != nil ? [self URLWithAttributeString:pluginPage] : nil;
        NSError *error = [[NSError alloc] _initWithPluginErrorCode:errorCode
                                                        contentURL:URL
                                                     pluginPageURL:pluginPageURL
                                                        pluginName:[pluginPackage name]
                                                          MIMEType:MIMEType];
        WebNullPluginView *nullView = [[[WebNullPluginView alloc] initWithFrame:frame error:error DOMElement:element] autorelease];
        view = nullView;
        [error release];
    }
    
    ASSERT(view);
    return view;
}

- (void)redirectDataToPlugin:(NSView *)pluginView
{
    WebHTMLRepresentation *representation = (WebHTMLRepresentation *)[[_frame _dataSource] representation];

#ifndef __LP64__
    if ([pluginView isKindOfClass:[WebNetscapePluginEmbeddedView class]])
        [representation _redirectDataToManualLoader:(WebNetscapePluginEmbeddedView *)pluginView forPluginView:pluginView];
    else {
#else
    {
#endif
        WebHTMLView *docView = (WebHTMLView *)[[_frame frameView] documentView];
        ASSERT([docView isKindOfClass:[WebHTMLView class]]);
        
        WebPluginController *pluginController = [docView _pluginController];
        [representation _redirectDataToManualLoader:pluginController forPluginView:pluginView];
    }

}

- (NSView *)viewForJavaAppletWithFrame:(NSRect)theFrame
                        attributeNames:(NSArray *)attributeNames
                       attributeValues:(NSArray *)attributeValues
                               baseURL:(NSURL *)baseURL
                            DOMElement:(DOMElement *)element
{
    NSString *MIMEType = @"application/x-java-applet";
    WebBasePluginPackage *pluginPackage;
    NSView *view = nil;
    
    pluginPackage = [[self webView] _pluginForMIMEType:MIMEType];

    if (pluginPackage) {
        if ([pluginPackage isKindOfClass:[WebPluginPackage class]]) {
            // For some reason, the Java plug-in requires that we pass the dimension of the plug-in as attributes.
            NSMutableArray *names = [attributeNames mutableCopy];
            NSMutableArray *values = [attributeValues mutableCopy];
            if ([self valueForKey:@"width" keys:attributeNames values:attributeValues] == nil) {
                [names addObject:@"width"];
                [values addObject:[NSString stringWithFormat:@"%d", (int)theFrame.size.width]];
            }
            if ([self valueForKey:@"height" keys:attributeNames values:attributeValues] == nil) {
                [names addObject:@"height"];
                [values addObject:[NSString stringWithFormat:@"%d", (int)theFrame.size.height]];
            }
            view = [self pluginViewWithPackage:(WebPluginPackage *)pluginPackage
                                attributeNames:names
                               attributeValues:values
                                       baseURL:baseURL
                                    DOMElement:element
                                  loadManually:NO];
            [names release];
            [values release];
            
        } 
#ifndef __LP64__
        else if ([pluginPackage isKindOfClass:[WebNetscapePluginPackage class]]) {
            view = [[[WebNetscapePluginEmbeddedView alloc] initWithFrame:theFrame
                                                           pluginPackage:(WebNetscapePluginPackage *)pluginPackage
                                                                     URL:nil
                                                                 baseURL:baseURL
                                                                MIMEType:MIMEType
                                                           attributeKeys:attributeNames
                                                         attributeValues:attributeValues
                                                            loadManually:NO
                                                              DOMElement:element] autorelease];
        } else {
            ASSERT_NOT_REACHED();
        }
#endif
    }

    if (!view) {
        NSError *error = [[NSError alloc] _initWithPluginErrorCode:WebKitErrorJavaUnavailable
                                                        contentURL:nil
                                                     pluginPageURL:nil
                                                        pluginName:[pluginPackage name]
                                                          MIMEType:MIMEType];
        view = [[[WebNullPluginView alloc] initWithFrame:theFrame error:error DOMElement:element] autorelease];
        [error release];
    }

    ASSERT(view);

    return view;
}

- (ObjectContentType)determineObjectFromMIMEType:(NSString*)MIMEType URL:(NSURL*)URL
{
    // This is a quirk that ensures Tiger Mail's WebKit plug-in will load during layout
    // and not attach time. (5520541)
    static BOOL isTigerMail = WKAppVersionCheckLessThan(@"com.apple.mail", -1, 3.0);
    if (isTigerMail && [MIMEType isEqualToString:@"application/x-apple-msg-attachment"])
        return ObjectContentNetscapePlugin;
    
    if ([MIMEType length] == 0) {
        // Try to guess the MIME type based off the extension.
        NSString *extension = [[URL path] pathExtension];
        if ([extension length] > 0) {
            MIMEType = WKGetMIMETypeForExtension(extension);
            if ([MIMEType length] == 0) {
                // If no MIME type is specified, use a plug-in if we have one that can handle the extension.
                if (WebBasePluginPackage *package = [[self webView] _pluginForExtension:extension]) {
                    if ([package isKindOfClass:[WebPluginPackage class]]) 
                        return ObjectContentOtherPlugin;
#ifndef __LP64__
                    else {
                        ASSERT([package isKindOfClass:[WebNetscapePluginPackage class]]);
                        return ObjectContentNetscapePlugin;
                    }
#endif
                }
            }
        }
    }

    if ([MIMEType length] == 0)
        return ObjectContentFrame; // Go ahead and hope that we can display the content.

    if (MIMETypeRegistry::isSupportedImageMIMEType(MIMEType))
        return ObjectContentImage;

    if (WebBasePluginPackage *package = [[self webView] _pluginForMIMEType:MIMEType]) {
        if ([package isKindOfClass:[WebPluginPackage class]]) 
            return ObjectContentOtherPlugin;
#ifndef __LP64__
        else {
            ASSERT([package isKindOfClass:[WebNetscapePluginPackage class]]);
            return ObjectContentNetscapePlugin;
        }
#endif
    }

    if ([WebFrameView _viewClassForMIMEType:MIMEType])
        return ObjectContentFrame;
    
    return ObjectContentNone;
}

- (jobject)getAppletInView:(NSView *)view
{
    if ([view respondsToSelector:@selector(webPlugInGetApplet)])
        return [view webPlugInGetApplet];
    return [self pollForAppletInView:view];
}

// NOTE: pollForAppletInView: will block until the block is ready to use, or
// until a timeout is exceeded.  It will return nil if the timeout is
// exceeded.
// Deprecated, use getAppletInView:.
- (jobject)pollForAppletInView:(NSView *)view
{
    if ([view respondsToSelector:@selector(pollForAppletInWindow:)])
        // The Java VM needs the containing window of the view to
        // initialize. The view may not yet be in the window's view 
        // hierarchy, so we have to pass the window when requesting
        // the applet.
        return [view pollForAppletInWindow:[[self webView] window]];
    return 0;
}

- (void)respondToChangedContents
{
    NSView <WebDocumentView> *view = [[_frame frameView] documentView];
    if ([view isKindOfClass:[WebHTMLView class]])
        [(WebHTMLView *)view _updateFontPanel];
    [[NSNotificationCenter defaultCenter] postNotificationName:WebViewDidChangeNotification object:[self webView]];
}

- (NSUndoManager *)undoManager
{
    return [[self webView] undoManager];
}

- (void)issuePasteCommand
{
    NSView* documentView = [[_frame frameView] documentView];
    if ([documentView isKindOfClass:[WebHTMLView class]])
        [(WebHTMLView*)documentView paste:nil];
}

- (void)setIsSelected:(BOOL)isSelected forView:(NSView *)view
{
    if ([view respondsToSelector:@selector(webPlugInSetIsSelected:)])
        [view webPlugInSetIsSelected:isSelected];
    else if ([view respondsToSelector:@selector(setIsSelected:)])
        [view setIsSelected:isSelected];
}

- (void)windowObjectCleared
{
    WebView *webView = getWebView(_frame);
    WebFrameLoadDelegateImplementationCache* implementations = WebViewGetFrameLoadDelegateImplementations(webView);
    if (implementations->didClearWindowObjectForFrameFunc)
        CallFrameLoadDelegate(implementations->didClearWindowObjectForFrameFunc, webView, @selector(webView:didClearWindowObject:forFrame:), m_frame->windowScriptObject(), _frame);
    else if (implementations->windowScriptObjectAvailableFunc)
        CallFrameLoadDelegate(implementations->windowScriptObjectAvailableFunc, webView, @selector(webView:windowScriptObjectAvailable:), m_frame->windowScriptObject());

    if ([webView scriptDebugDelegate] || [WebScriptDebugServer listenerCount]) {
        [_frame _detachScriptDebugger];
        [_frame _attachScriptDebugger];
    }
}

- (BOOL)_compareDashboardRegions:(NSDictionary *)regions
{
    return [lastDashboardRegions isEqualToDictionary:regions];
}

- (void)dashboardRegionsChanged:(NSMutableDictionary *)regions
{
    WebView *webView = [self webView];
    [webView _addScrollerDashboardRegions:regions];

    if (![self _compareDashboardRegions:regions]) {
        CallUIDelegate(webView, @selector(webView:dashboardRegionsChanged:), regions);

        [lastDashboardRegions release];
        lastDashboardRegions = [regions retain];
    }
}

- (void)willPopupMenu:(NSMenu *)menu
{
    CallUIDelegate([self webView], @selector(webView:willPopupMenu:), menu);
}

- (NSRect)customHighlightRect:(NSString*)type forLine:(NSRect)lineRect representedNode:(WebCore::Node *)node
{
    ASSERT(_frame != nil);
    NSView *documentView = [[_frame frameView] documentView];
    if (![documentView isKindOfClass:[WebHTMLView class]])
        return NSZeroRect;

    WebHTMLView *webHTMLView = (WebHTMLView *)documentView;
    id<WebHTMLHighlighter> highlighter = [webHTMLView _highlighterForType:type];
    if ([(NSObject *)highlighter respondsToSelector:@selector(highlightRectForLine:representedNode:)])
        return [highlighter highlightRectForLine:lineRect representedNode:kit(node)];
    return [highlighter highlightRectForLine:lineRect];
}

- (void)paintCustomHighlight:(NSString*)type forBox:(NSRect)boxRect onLine:(NSRect)lineRect behindText:(BOOL)text entireLine:(BOOL)line representedNode:(WebCore::Node *)node
{
    ASSERT(_frame != nil);
    NSView *documentView = [[_frame frameView] documentView];
    if (![documentView isKindOfClass:[WebHTMLView class]])
        return;

    WebHTMLView *webHTMLView = (WebHTMLView *)documentView;
    id<WebHTMLHighlighter> highlighter = [webHTMLView _highlighterForType:type];
    if ([(NSObject *)highlighter respondsToSelector:@selector(paintHighlightForBox:onLine:behindText:entireLine:representedNode:)])
        [highlighter paintHighlightForBox:boxRect onLine:lineRect behindText:text entireLine:line representedNode:kit(node)];
    else
        [highlighter paintHighlightForBox:boxRect onLine:lineRect behindText:text entireLine:line];
}

- (NSString*)imageTitleForFilename:(NSString*)filename size:(NSSize)size
{
    return [NSString stringWithFormat:UI_STRING("%@ %.0f×%.0f pixels", "window title for a standalone image (uses multiplication symbol, not x)"), filename, size.width, size.height];
}

@end
