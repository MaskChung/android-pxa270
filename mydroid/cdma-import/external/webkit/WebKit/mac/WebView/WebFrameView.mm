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

#import "WebFrameView.h"

#import "WebClipView.h"
#import "WebDataSourcePrivate.h"
#import "WebDocument.h"
#import "WebDynamicScrollBarsView.h"
#import "WebFrame.h"
#import "WebFrameInternal.h"
#import "WebFrameBridge.h"
#import "WebFrameViewInternal.h"
#import "WebFrameViewPrivate.h"
#import "WebHistoryItemInternal.h"
#import "WebHTMLViewPrivate.h"
#import "WebKeyGenerator.h"
#import "WebKitErrorsPrivate.h"
#import "WebKitStatisticsPrivate.h"
#import "WebKitVersionChecks.h"
#import "WebNSDictionaryExtras.h"
#import "WebNSObjectExtras.h"
#import "WebNSPasteboardExtras.h"
#import "WebNSViewExtras.h"
#import "WebNSWindowExtras.h"
#import "WebPDFView.h"
#import "WebPreferenceKeysPrivate.h"
#import "WebSystemInterface.h"
#import "WebViewFactory.h"
#import "WebViewInternal.h"
#import "WebViewPrivate.h"
#import <Foundation/NSURLRequest.h>
#import <JavaScriptCore/Assertions.h>
#import <WebCore/DragController.h>
#import <WebCore/Frame.h>
#import <WebCore/FrameView.h>
#import <WebCore/HistoryItem.h>
#import <WebCore/Page.h>
#import <WebCore/ThreadCheck.h>
#import <WebCore/WebCoreFrameView.h>
#import <WebCore/WebCoreView.h>
#import <WebKitSystemInterface.h>

using namespace WebCore;

@interface NSWindow (WindowPrivate)
- (BOOL)_needsToResetDragMargins;
- (void)_setNeedsToResetDragMargins:(BOOL)s;
@end

@interface NSClipView (AppKitSecretsIKnow)
- (BOOL)_scrollTo:(const NSPoint *)newOrigin animate:(BOOL)animate; // need the boolean result from this method
@end

enum {
    SpaceKey = 0x0020
};

@interface WebFrameView (WebFrameViewFileInternal) <WebCoreBridgeHolder>
- (float)_verticalKeyboardScrollDistance;
- (WebCoreFrameBridge *) webCoreBridge;
@end

@interface WebFrameViewPrivate : NSObject {
@public
    WebFrame *webFrame;
    WebDynamicScrollBarsView *frameScrollView;
    
    // These margin values are used to temporarily hold the margins of a frame until
    // we have the appropriate document view type.
    int marginWidth;
    int marginHeight;
}
@end

@implementation WebFrameViewPrivate

- init
{
    [super init];
    
    marginWidth = -1;
    marginHeight = -1;
    
    return self;
}

- (void)dealloc
{
    [frameScrollView release];
    [super dealloc];
}

@end

@implementation WebFrameView (WebFrameViewFileInternal)

- (float)_verticalKeyboardScrollDistance
{
    // Arrow keys scroll the same distance that clicking the scroll arrow does.
    return [[self _scrollView] verticalLineScroll];
}

- (WebCoreFrameBridge *) webCoreBridge
{
    return [_private->webFrame _bridge];
}

@end

@implementation WebFrameView (WebInternal)

// Note that the WebVew is not retained.
- (WebView *)_webView
{
    return [_private->webFrame webView];
}

- (void)_setMarginWidth:(int)w
{
    _private->marginWidth = w;
}

- (int)_marginWidth
{
    return _private->marginWidth;
}

- (void)_setMarginHeight:(int)h
{
    _private->marginHeight = h;
}

- (int)_marginHeight
{
    return _private->marginHeight;
}

- (void)_setDocumentView:(NSView <WebDocumentView> *)view
{
    WebDynamicScrollBarsView *sv = [self _scrollView];
    core([self _webView])->dragController()->setDidInitiateDrag(false);
    
    [sv setSuppressLayout:YES];
    
    // If the old view is the first responder, transfer first responder status to the new view as 
    // a convenience and so that we don't leave the window pointing to a view that's no longer in it.
    NSWindow *window = [sv window];
    NSResponder *firstResponder = [window firstResponder];
    bool makeNewViewFirstResponder = [firstResponder isKindOfClass:[NSView class]] && [(NSView *)firstResponder isDescendantOf:[sv documentView]];

    // Suppress the resetting of drag margins since we know we can't affect them.
    BOOL resetDragMargins = [window _needsToResetDragMargins];
    [window _setNeedsToResetDragMargins:NO];
    [sv setDocumentView:view];
    [window _setNeedsToResetDragMargins:resetDragMargins];

    if (makeNewViewFirstResponder)
        [window makeFirstResponder:view];
    [sv setSuppressLayout:NO];
}

-(NSView <WebDocumentView> *)_makeDocumentViewForDataSource:(WebDataSource *)dataSource
{
    NSString* MIMEType = [dataSource _responseMIMEType];
    if (!MIMEType)
        MIMEType = @"text/html";
    Class viewClass = [[self class] _viewClassForMIMEType:MIMEType];
    NSView <WebDocumentView> *documentView;
    if (viewClass) {
        // If the dataSource's representation has already been created, and it is also the
        // same class as the desired documentView, then use it as the documentView instead
        // of creating another one (Radar 4340787).
        id <WebDocumentRepresentation> dataSourceRepresentation = [dataSource representation];
        if (dataSourceRepresentation && [dataSourceRepresentation class] == viewClass)
            documentView = (NSView <WebDocumentView> *)[dataSourceRepresentation retain];
        else
            documentView = [[viewClass alloc] initWithFrame:[self bounds]];
    } else
        documentView = nil;
    
    [self _setDocumentView:documentView];
    [documentView release];
    
    return documentView;
}

- (void)_setWebFrame:(WebFrame *)webFrame
{
    if (!webFrame) {
        NSView *docV = [self documentView];
        if ([docV respondsToSelector:@selector(close)])
            [docV performSelector:@selector(close)];
    }

    // Not retained because the WebView owns the WebFrame, which owns the WebFrameView.
    _private->webFrame = webFrame;    
}

- (WebDynamicScrollBarsView *)_scrollView
{
    // this can be called by [super dealloc] when cleaning up the keyview loop,
    // after _private has been nilled out.
    if (_private == nil) {
        return nil;
    }
    return _private->frameScrollView;
}

- (float)_verticalPageScrollDistance
{
    float overlap = [self _verticalKeyboardScrollDistance];
    float height = [[self _contentView] bounds].size.height;
    return (height < overlap) ? height / 2 : height - overlap;
}

static inline void addTypesFromClass(NSMutableDictionary *allTypes, Class objCClass, NSArray *supportTypes)
{
    NSEnumerator *enumerator = [supportTypes objectEnumerator];
    ASSERT(enumerator != nil);
    NSString *mime = nil;
    while ((mime = [enumerator nextObject]) != nil) {
        // Don't clobber previously-registered classes.
        if ([allTypes objectForKey:mime] == nil)
            [allTypes setObject:objCClass forKey:mime];
    }
}

+ (NSMutableDictionary *)_viewTypesAllowImageTypeOmission:(BOOL)allowImageTypeOmission
{
    static NSMutableDictionary *viewTypes = nil;
    static BOOL addedImageTypes = NO;
    
    if (!viewTypes) {
        viewTypes = [[NSMutableDictionary alloc] init];
        addTypesFromClass(viewTypes, [WebHTMLView class], [WebHTMLView supportedNonImageMIMETypes]);

        // Since this is a "secret default" we don't bother registering it.
        BOOL omitPDFSupport = [[NSUserDefaults standardUserDefaults] boolForKey:@"WebKitOmitPDFSupport"];
        if (!omitPDFSupport)
            addTypesFromClass(viewTypes, [WebPDFView class], [WebPDFView supportedMIMETypes]);
    }
    
    if (!addedImageTypes && !allowImageTypeOmission) {
        addTypesFromClass(viewTypes, [WebHTMLView class], [WebHTMLView supportedImageMIMETypes]);
        addedImageTypes = YES;
    }
    
    return viewTypes;
}

+ (BOOL)_canShowMIMETypeAsHTML:(NSString *)MIMEType
{
    return [[[self _viewTypesAllowImageTypeOmission:YES] _webkit_objectForMIMEType:MIMEType] isSubclassOfClass:[WebHTMLView class]];
}

+ (Class)_viewClassForMIMEType:(NSString *)MIMEType
{
    Class viewClass;
    return [WebView _viewClass:&viewClass andRepresentationClass:nil forMIMEType:MIMEType] ? viewClass : nil;
}

@end

@implementation WebFrameView

- initWithCoder:(NSCoder *)decoder
{
    // Older nibs containing WebViews will also contain WebFrameViews. We need to keep track of
    // their count also to match the decrement in -dealloc.
    ++WebFrameViewCount;
    return [super initWithCoder:decoder];
}

- initWithFrame:(NSRect)frame
{
    self = [super initWithFrame:frame];
    if (!self)
        return nil;
 
    static bool didFirstTimeInitialization;
    if (!didFirstTimeInitialization) {
        didFirstTimeInitialization = true;
        InitWebCoreSystemInterface();
        
        // Need to tell WebCore what function to call for the 
        // "History Item has Changed" notification
        // Note: We also do this in WebHistoryItem's init method
        WebCore::notifyHistoryItemChanged = WKNotifyHistoryItemChanged;

        [WebViewFactory createSharedFactory];
        [WebKeyGenerator createSharedGenerator];

        NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
        
        // CoreGraphics deferred updates are disabled if WebKitEnableCoalescedUpdatesPreferenceKey is set
        // to NO, or has no value.  For compatibility with Mac OS X 10.4.6, deferred updates are OFF by
        // default.
        if (![defaults boolForKey:WebKitEnableDeferredUpdatesPreferenceKey])
            WKDisableCGDeferredUpdates();

        if (!WebKitLinkedOnOrAfter(WEBKIT_FIRST_VERSION_WITH_MAIN_THREAD_EXCEPTIONS))
            setDefaultThreadViolationBehavior(LogOnFirstThreadViolation);
    }
    
    _private = [[WebFrameViewPrivate alloc] init];

    WebDynamicScrollBarsView *scrollView  = [[WebDynamicScrollBarsView alloc] initWithFrame:NSMakeRect(0.0f, 0.0f, frame.size.width, frame.size.height)];
    _private->frameScrollView = scrollView;
    [scrollView setContentView:[[[WebClipView alloc] initWithFrame:[scrollView bounds]] autorelease]];
    [scrollView setDrawsBackground:NO];
    [scrollView setHasVerticalScroller:NO];
    [scrollView setHasHorizontalScroller:NO];
    [scrollView setAutoresizingMask:NSViewWidthSizable | NSViewHeightSizable];
    [scrollView setLineScroll:40.0f];
    [self addSubview:scrollView];
    // don't call our overridden version here; we need to make the standard NSView link between us
    // and our subview so that previousKeyView and previousValidKeyView work as expected. This works
    // together with our becomeFirstResponder and setNextKeyView overrides.
    [super setNextKeyView:scrollView];
    
    ++WebFrameViewCount;
    
    return self;
}

- (void)dealloc 
{
    --WebFrameViewCount;
    
    [_private release];
    _private = nil;
    
    [super dealloc];
}

- (void)finalize 
{
    --WebFrameViewCount;

    [super finalize];
}

- (WebFrame *)webFrame
{
    return _private->webFrame;
}

- (void)setAllowsScrolling:(BOOL)flag
{
    WebDynamicScrollBarsView *scrollView = [self _scrollView];
    [scrollView setAllowsScrolling:flag];
    WebCore::Frame *frame = core([self webFrame]);
    if (WebCore::FrameView *view = frame? frame->view() : 0) {
        view->setHScrollbarMode((WebCore::ScrollbarMode)[scrollView horizontalScrollingMode]);
        view->setVScrollbarMode((WebCore::ScrollbarMode)[scrollView verticalScrollingMode]);
    }
}

- (BOOL)allowsScrolling
{
    return [[self _scrollView] allowsScrolling];
}

- (NSView <WebDocumentView> *)documentView
{
    return [[self _scrollView] documentView];
}

- (BOOL)acceptsFirstResponder
{
    // We always accept first responder; this matches OS X 10.2 WebKit
    // behavior (see 3469791).
    return YES;
}

- (BOOL)becomeFirstResponder
{
    // This works together with setNextKeyView to splice the WebFrameView into
    // the key loop similar to the way NSScrollView does this. Note that
    // WebView has similar code.
    
    // If the scrollView won't accept first-responderness now, then we just become
    // the first responder ourself like a normal view. This lets us be the first 
    // responder in cases where no page has yet been loaded (see 3469791).
    if ([[self _scrollView] acceptsFirstResponder]) {
        NSWindow *window = [self window];
        if ([window keyViewSelectionDirection] == NSSelectingPrevious) {
            NSView *previousValidKeyView = [self previousValidKeyView];
            // If we couldn't find a previous valid key view, ask the webview. This handles frameset
            // cases like 3748628. Note that previousValidKeyView should never be self but can be
            // due to AppKit oddness (mentioned in 3748628).
            if (previousValidKeyView == nil || previousValidKeyView == self) {
                previousValidKeyView = [[[self webFrame] webView] previousValidKeyView];
            }
            // I don't know if the following cases ever occur anymore, but I'm leaving in the old test for
            // now to avoid causing trouble just before shipping Tiger.
            ASSERT((previousValidKeyView != self) && (previousValidKeyView != [self _scrollView]));
            if ((previousValidKeyView != self) && (previousValidKeyView != [self _scrollView])) {
                [window makeFirstResponder:previousValidKeyView];
            }
        } else {
            [window makeFirstResponder:[self _scrollView]];
        }
    }    
    
    return YES;
}

- (void)setNextKeyView:(NSView *)aView
{
    // This works together with becomeFirstResponder to splice the WebFrameView into
    // the key loop similar to the way NSScrollView does this. Note that
    // WebView has very similar code.
    if ([self _scrollView] != nil) {
        [[self _scrollView] setNextKeyView:aView];
    } else {
        [super setNextKeyView:aView];
    }
}

- (BOOL)isOpaque
{
    return [[self _webView] drawsBackground];
}

- (void)drawRect:(NSRect)rect
{
    if ([self documentView] == nil) {
        // Need to paint ourselves if there's no documentView to do it instead.
        if ([[self _webView] drawsBackground]) {
            [[[self _webView] backgroundColor] set];
            NSRectFill(rect);
        }
    } else {
#ifndef NDEBUG
        if ([[self _scrollView] drawsBackground]) {
            [[NSColor cyanColor] set];
            NSRectFill(rect);
        }
#endif
    }
}

- (void)setFrameSize:(NSSize)size
{
    if (!NSEqualSizes(size, [self frame].size) && [[[self webFrame] webView] drawsBackground]) {
        [[self _scrollView] setDrawsBackground:YES];
    }
    [super setFrameSize:size];
}

- (WebFrameBridge *)_bridge
{
    return [[self webFrame] _bridge];
}

- (BOOL)_scrollOverflowInDirection:(WebScrollDirection)direction granularity:(WebScrollGranularity)granularity
{
    // scrolling overflows is only applicable if we're dealing with an WebHTMLView
    return ([[self documentView] isKindOfClass:[WebHTMLView class]] && [[self _bridge] scrollOverflowInDirection:direction granularity:granularity]);
}

- (void)scrollToBeginningOfDocument:(id)sender
{
    if (![self _scrollOverflowInDirection:WebScrollUp granularity:WebScrollDocument]) {

        if (![self _hasScrollBars]) {
            [[self _largestChildWithScrollBars] scrollToBeginningOfDocument:sender];
            return;
        }

        [[self _contentView] scrollPoint:[[[self _scrollView] documentView] frame].origin];
    }
}

- (void)scrollToEndOfDocument:(id)sender
{
    if (![self _scrollOverflowInDirection:WebScrollDown granularity:WebScrollDocument]) {

        if (![self _hasScrollBars]) {
            [[self _largestChildWithScrollBars] scrollToEndOfDocument:sender];
            return;
        }
        
        NSRect frame = [[[self _scrollView] documentView] frame];
        [[self _contentView] scrollPoint:NSMakePoint(frame.origin.x, NSMaxY(frame))];
    }
}

- (void)_goBack
{
    [[self _webView] goBack];
}

- (void)_goForward
{
    [[self _webView] goForward];
}

- (BOOL)_scrollVerticallyBy:(float)delta
{
    // This method uses the secret method _scrollTo on NSClipView.
    // It does that because it needs to know definitively whether scrolling was
    // done or not to help implement the "scroll parent if we are at the limit" feature.
    // In the presence of smooth scrolling, there's no easy way to tell if the method
    // did any scrolling or not with the public API.
    NSPoint point = [[self _contentView] bounds].origin;
    point.y += delta;
    return [[self _contentView] _scrollTo:&point animate:YES];
}

- (BOOL)_scrollHorizontallyBy:(float)delta
{
    NSPoint point = [[self _contentView] bounds].origin;
    point.x += delta;
    return [[self _contentView] _scrollTo:&point animate:YES];
}

- (float)_horizontalKeyboardScrollDistance
{
    // Arrow keys scroll the same distance that clicking the scroll arrow does.
    return [[self _scrollView] horizontalLineScroll];
}

- (float)_horizontalPageScrollDistance
{
    float overlap = [self _horizontalKeyboardScrollDistance];
    float width = [[self _contentView] bounds].size.width;
    return (width < overlap) ? width / 2 : width - overlap;
}

- (BOOL)_pageVertically:(BOOL)up
{
    if ([self _scrollOverflowInDirection:up ? WebScrollUp : WebScrollDown granularity:WebScrollPage])
        return YES;
    
    if (![self _hasScrollBars])
        return [[self _largestChildWithScrollBars] _pageVertically:up];

    float delta = [self _verticalPageScrollDistance];
    return [self _scrollVerticallyBy:up ? -delta : delta];
}

- (BOOL)_pageHorizontally:(BOOL)left
{
    if ([self _scrollOverflowInDirection:left ? WebScrollLeft : WebScrollRight granularity:WebScrollPage])
        return YES;

    if (![self _hasScrollBars])
        return [[self _largestChildWithScrollBars] _pageHorizontally:left];
    
    float delta = [self _horizontalPageScrollDistance];
    return [self _scrollHorizontallyBy:left ? -delta : delta];
}

- (BOOL)_scrollLineVertically:(BOOL)up
{
    if ([self _scrollOverflowInDirection:up ? WebScrollUp : WebScrollDown granularity:WebScrollLine])
        return YES;

    if (![self _hasScrollBars])
        return [[self _largestChildWithScrollBars] _scrollLineVertically:up];
    
    float delta = [self _verticalKeyboardScrollDistance];
    return [self _scrollVerticallyBy:up ? -delta : delta];
}

- (BOOL)_scrollLineHorizontally:(BOOL)left
{
    if ([self _scrollOverflowInDirection:left ? WebScrollLeft : WebScrollRight granularity:WebScrollLine])
        return YES;

    if (![self _hasScrollBars])
        return [[self _largestChildWithScrollBars] _scrollLineHorizontally:left];

    float delta = [self _horizontalKeyboardScrollDistance];
    return [self _scrollHorizontallyBy:left ? -delta : delta];
}

- (void)scrollPageUp:(id)sender
{
    if (![self _pageVertically:YES]) {
        // If we were already at the top, tell the next responder to scroll if it can.
        [[self nextResponder] tryToPerform:@selector(scrollPageUp:) with:sender];
    }
}

- (void)scrollPageDown:(id)sender
{
    if (![self _pageVertically:NO]) {
        // If we were already at the bottom, tell the next responder to scroll if it can.
        [[self nextResponder] tryToPerform:@selector(scrollPageDown:) with:sender];
    }
}

- (void)scrollLineUp:(id)sender
{
    [self _scrollLineVertically:YES];
}

- (void)scrollLineDown:(id)sender
{
    [self _scrollLineVertically:NO];
}

- (BOOL)_firstResponderIsFormControl
{
    NSResponder *firstResponder = [[self window] firstResponder];
    
    // WebHTMLView is an NSControl subclass these days, but it's not a form control
    if ([firstResponder isKindOfClass:[WebHTMLView class]]) {
        return NO;
    }
    return [firstResponder isKindOfClass:[NSControl class]];
}

- (void)keyDown:(NSEvent *)event
{
    NSString *characters = [event characters];
    int index, count;
    BOOL callSuper = YES;
    BOOL maintainsBackForwardList = core([self webFrame])->page()->backForwardList()->enabled() ? YES : NO;
    
    count = [characters length];
    for (index = 0; index < count; ++index) {
        switch ([characters characterAtIndex:index]) {
            case NSDeleteCharacter:
                if (!maintainsBackForwardList) {
                    callSuper = YES;
                    break;
                }
                // This odd behavior matches some existing browsers,
                // including Windows IE
                if ([event modifierFlags] & NSShiftKeyMask) {
                    [self _goForward];
                } else {
                    [self _goBack];
                }
                callSuper = NO;
                break;
            case SpaceKey:
                // Checking for a control will allow events to percolate 
                // correctly when the focus is on a form control and we
                // are in full keyboard access mode.
                if ((![self allowsScrolling] && ![self _largestChildWithScrollBars]) || [self _firstResponderIsFormControl]) {
                    callSuper = YES;
                    break;
                }
                if ([event modifierFlags] & NSShiftKeyMask) {
                    [self scrollPageUp:nil];
                } else {
                    [self scrollPageDown:nil];
                }
                callSuper = NO;
                break;
            case NSPageUpFunctionKey:
                if (![self allowsScrolling] && ![self _largestChildWithScrollBars]) {
                    callSuper = YES;
                    break;
                }
                [self scrollPageUp:nil];
                callSuper = NO;
                break;
            case NSPageDownFunctionKey:
                if (![self allowsScrolling] && ![self _largestChildWithScrollBars]) {
                    callSuper = YES;
                    break;
                }
                [self scrollPageDown:nil];
                callSuper = NO;
                break;
            case NSHomeFunctionKey:
                if (![self allowsScrolling] && ![self _largestChildWithScrollBars]) {
                    callSuper = YES;
                    break;
                }
                [self scrollToBeginningOfDocument:nil];
                callSuper = NO;
                break;
            case NSEndFunctionKey:
                if (![self allowsScrolling] && ![self _largestChildWithScrollBars]) {
                    callSuper = YES;
                    break;
                }
                [self scrollToEndOfDocument:nil];
                callSuper = NO;
                break;
            case NSUpArrowFunctionKey:
                // We don't handle shifted or control-arrow keys here, so let super have a chance.
                if ([event modifierFlags] & (NSShiftKeyMask | NSControlKeyMask)) {
                    callSuper = YES;
                    break;
                }
                if ((![self allowsScrolling] && ![self _largestChildWithScrollBars]) ||
                    [[[self window] firstResponder] isKindOfClass:[NSPopUpButton class]]) {
                    // Let arrow keys go through to pop up buttons
                    // <rdar://problem/3455910>: hitting up or down arrows when focus is on a 
                    // pop-up menu should pop the menu
                    callSuper = YES;
                    break;
                }
                if ([event modifierFlags] & NSCommandKeyMask) {
                    [self scrollToBeginningOfDocument:nil];
                } else if ([event modifierFlags] & NSAlternateKeyMask) {
                    [self scrollPageUp:nil];
                } else {
                    [self scrollLineUp:nil];
                }
                callSuper = NO;
                break;
            case NSDownArrowFunctionKey:
                // We don't handle shifted or control-arrow keys here, so let super have a chance.
                if ([event modifierFlags] & (NSShiftKeyMask | NSControlKeyMask)) {
                    callSuper = YES;
                    break;
                }
                if ((![self allowsScrolling] && ![self _largestChildWithScrollBars]) ||
                    [[[self window] firstResponder] isKindOfClass:[NSPopUpButton class]]) {
                    // Let arrow keys go through to pop up buttons
                    // <rdar://problem/3455910>: hitting up or down arrows when focus is on a 
                    // pop-up menu should pop the menu
                    callSuper = YES;
                    break;
                }
                if ([event modifierFlags] & NSCommandKeyMask) {
                    [self scrollToEndOfDocument:nil];
                } else if ([event modifierFlags] & NSAlternateKeyMask) {
                    [self scrollPageDown:nil];
                } else {
                    [self scrollLineDown:nil];
                }
                callSuper = NO;
                break;
            case NSLeftArrowFunctionKey:
                // We don't handle shifted or control-arrow keys here, so let super have a chance.
                if ([event modifierFlags] & (NSShiftKeyMask | NSControlKeyMask)) {
                    callSuper = YES;
                    break;
                }
                // Check back/forward related keys.
                if ([event modifierFlags] & NSCommandKeyMask) {
                    if (!maintainsBackForwardList) {
                        callSuper = YES;
                        break;
                    }
                    [self _goBack];
                } else {
                    // Now check scrolling related keys.
                    if ((![self allowsScrolling] && ![self _largestChildWithScrollBars])) {
                        callSuper = YES;
                        break;
                    }

                    if ([event modifierFlags] & NSAlternateKeyMask) {
                        [self _pageHorizontally:YES];
                    } else {
                        [self _scrollLineHorizontally:YES];
                    }
                }
                callSuper = NO;
                break;
            case NSRightArrowFunctionKey:
                // We don't handle shifted or control-arrow keys here, so let super have a chance.
                if ([event modifierFlags] & (NSShiftKeyMask | NSControlKeyMask)) {
                    callSuper = YES;
                    break;
                }
                // Check back/forward related keys.
                if ([event modifierFlags] & NSCommandKeyMask) {
                    if (!maintainsBackForwardList) {
                        callSuper = YES;
                        break;
                    }
                    [self _goForward];
                } else {
                    // Now check scrolling related keys.
                    if ((![self allowsScrolling] && ![self _largestChildWithScrollBars])) {
                        callSuper = YES;
                        break;
                    }

                    if ([event modifierFlags] & NSAlternateKeyMask) {
                        [self _pageHorizontally:NO];
                    } else {
                        [self _scrollLineHorizontally:NO];
                    }
                }
                callSuper = NO;
                break;
        }
    }
    
    if (callSuper) {
        [super keyDown:event];
    } else {
        // if we did something useful, get the cursor out of the way
        [NSCursor setHiddenUntilMouseMoves:YES];
    }
}

- (NSView *)_webcore_effectiveFirstResponder
{
    NSView *view = [self documentView];
    return view ? [view _webcore_effectiveFirstResponder] : [super _webcore_effectiveFirstResponder];
}

- (BOOL)canPrintHeadersAndFooters
{
    NSView *documentView = [[self _scrollView] documentView];
    if ([documentView respondsToSelector:@selector(canPrintHeadersAndFooters)]) {
        return [(id)documentView canPrintHeadersAndFooters];
    }
    return NO;
}

- (NSPrintOperation *)printOperationWithPrintInfo:(NSPrintInfo *)printInfo
{
    NSView *documentView = [[self _scrollView] documentView];
    if (!documentView) {
        return nil;
    }
    if ([documentView respondsToSelector:@selector(printOperationWithPrintInfo:)]) {
        return [(id)documentView printOperationWithPrintInfo:printInfo];
    }
    return [NSPrintOperation printOperationWithView:documentView printInfo:printInfo];
}

- (BOOL)documentViewShouldHandlePrint
{
    NSView *documentView = [[self _scrollView] documentView];
    if (documentView && [documentView respondsToSelector:@selector(documentViewShouldHandlePrint)])
        return [(id)documentView documentViewShouldHandlePrint];
    
    return NO;
}

- (void)printDocumentView
{
    NSView *documentView = [[self _scrollView] documentView];
    if (documentView && [documentView respondsToSelector:@selector(printDocumentView)])
        [(id)documentView printDocumentView];
}

@end

@implementation WebFrameView (WebPrivate)

- (float)_area
{
    NSRect frame = [self frame];
    return frame.size.height * frame.size.width;
}

- (BOOL)_hasScrollBars
{
    NSScrollView *scrollView = [self _scrollView];
    return [scrollView hasHorizontalScroller] || [scrollView hasVerticalScroller];
}

- (WebFrameView *)_largestChildWithScrollBars
{
    WebFrameView *largest = nil;
    NSArray *frameChildren = [[self webFrame] childFrames];
    
    unsigned i;
    for (i=0; i < [frameChildren count]; i++) {
        WebFrameView *childFrameView = [[frameChildren objectAtIndex:i] frameView];
        WebFrameView *scrollableFrameView = [childFrameView _hasScrollBars] ? childFrameView : [childFrameView _largestChildWithScrollBars];
        if (!scrollableFrameView)
            continue;
        
        // Some ads lurk in child frames of zero width and height, see radar 4406994. These don't count as scrollable.
        // Maybe someday we'll discover that this minimum area check should be larger, but this covers the known cases.
        float area = [scrollableFrameView _area];
        if (area < 1.0)
            continue;
        
        if (!largest || (area > [largest _area])) {
            largest = scrollableFrameView;
        }
    }
    
    return largest;
}

- (NSClipView *)_contentView
{
    return [[self _scrollView] contentView];
}

- (Class)_customScrollViewClass
{
    if ([_private->frameScrollView class] == [WebDynamicScrollBarsView class])
        return nil;
    return [_private->frameScrollView class];
}

- (void)_setCustomScrollViewClass:(Class)customClass
{
    if (!customClass)
        customClass = [WebDynamicScrollBarsView class];
    ASSERT([customClass isSubclassOfClass:[WebDynamicScrollBarsView class]]);
    if (customClass == [_private->frameScrollView class])
        return;
    if ([customClass isSubclassOfClass:[WebDynamicScrollBarsView class]]) {
        WebDynamicScrollBarsView *oldScrollView = _private->frameScrollView; // already retained
        NSView <WebDocumentView> *documentView = [[self documentView] retain];

        WebDynamicScrollBarsView *scrollView  = [[customClass alloc] initWithFrame:[oldScrollView frame]];
        [scrollView setContentView:[[[WebClipView alloc] initWithFrame:[scrollView bounds]] autorelease]];
        [scrollView setDrawsBackground:[oldScrollView drawsBackground]];
        [scrollView setHasVerticalScroller:[oldScrollView hasVerticalScroller]];
        [scrollView setHasHorizontalScroller:[oldScrollView hasHorizontalScroller]];
        [scrollView setAutoresizingMask:[oldScrollView autoresizingMask]];
        [scrollView setLineScroll:[oldScrollView lineScroll]];
        [self addSubview:scrollView];

        // don't call our overridden version here; we need to make the standard NSView link between us
        // and our subview so that previousKeyView and previousValidKeyView work as expected. This works
        // together with our becomeFirstResponder and setNextKeyView overrides.
        [super setNextKeyView:scrollView];

        _private->frameScrollView = scrollView;

        [self _setDocumentView:documentView];
        [[self _bridge] installInFrame:scrollView];

        [oldScrollView removeFromSuperview];
        [oldScrollView release];
        [documentView release];
    }
}

@end
