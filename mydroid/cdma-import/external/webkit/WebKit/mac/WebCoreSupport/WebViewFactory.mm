/*
 * Copyright (C) 2005 Apple Computer, Inc.  All rights reserved.
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

#import <WebKit/WebViewFactory.h>

#import <JavaScriptCore/Assertions.h>
#import <WebKit/WebFrameBridge.h>
#import <WebKit/WebFrameInternal.h>
#import <WebKit/WebViewInternal.h>
#import <WebKit/WebHTMLViewInternal.h>
#import <WebKit/WebLocalizableStrings.h>
#import <WebKit/WebNSUserDefaultsExtras.h>
#import <WebKit/WebNSObjectExtras.h>
#import <WebKit/WebNSViewExtras.h>
#import <WebKit/WebPluginDatabase.h>
#import <WebKitSystemInterface.h>

@interface NSMenu (WebViewFactoryAdditions)
- (NSMenuItem *)addItemWithTitle:(NSString *)title action:(SEL)action tag:(int)tag;
@end

@implementation NSMenu (WebViewFactoryAdditions)

- (NSMenuItem *)addItemWithTitle:(NSString *)title action:(SEL)action tag:(int)tag
{
    NSMenuItem *item = [[[NSMenuItem alloc] initWithTitle:title action:action keyEquivalent:@""] autorelease];
    [item setTag:tag];
    [self addItem:item];
    return item;
}

@end

@implementation WebViewFactory

+ (void)createSharedFactory
{
    if (![self sharedFactory]) {
        [[[self alloc] init] release];
    }
    ASSERT([[self sharedFactory] isKindOfClass:self]);
}

- (NSArray *)pluginsInfo
{
    return [[WebPluginDatabase sharedDatabase] plugins];
}

- (NSString *)pluginNameForMIMEType:(NSString *)MIMEType
{
    return [[[WebPluginDatabase sharedDatabase] pluginForMIMEType:MIMEType] name];
}

- (void)refreshPlugins:(BOOL)reloadPages
{
    [[WebPluginDatabase sharedDatabase] refresh];
    if (reloadPages) {
        [WebView _makeAllWebViewsPerformSelector:@selector(_reloadForPluginChanges)];
    }
}

- (BOOL)pluginSupportsMIMEType:(NSString *)MIMEType
{
    return [[WebPluginDatabase sharedDatabase] pluginForMIMEType:MIMEType] != nil;
}

- (WebCoreFrameBridge *)bridgeForView:(NSView *)v
{
    NSView *aView = [v superview];
    
    while (aView) {
        if ([aView isKindOfClass:[WebHTMLView class]]) {
            return [[[(WebHTMLView *)aView _frame] _dataSource] _bridge];
        }
        aView = [aView superview];
    }
    return nil;
}

- (NSString *)inputElementAltText
{
    return UI_STRING_KEY("Submit", "Submit (input element)", "alt text for <input> elements with no alt, title, or value");
}

- (NSString *)resetButtonDefaultLabel
{
    return UI_STRING("Reset", "default label for Reset buttons in forms on web pages");
}

- (NSString *)searchableIndexIntroduction
{
    return UI_STRING("This is a searchable index. Enter search keywords: ",
        "text that appears at the start of nearly-obsolete web pages in the form of a 'searchable index'");
}

- (NSString *)submitButtonDefaultLabel
{
    return UI_STRING("Submit", "default label for Submit buttons in forms on web pages");
}

- (NSString *)fileButtonChooseFileLabel
{
    return UI_STRING("Choose File", "title for file button used in HTML forms");
}

- (NSString *)fileButtonNoFileSelectedLabel
{
    return UI_STRING("no file selected", "text to display in file button used in HTML forms when no file is selected");
}

- (NSString *)copyImageUnknownFileLabel
{
    return UI_STRING("unknown", "Unknown filename");
}

- (NSString *)searchMenuNoRecentSearchesText
{
    return UI_STRING("No recent searches", "Label for only item in menu that appears when clicking on the search field image, when no searches have been performed");
}

- (NSString *)searchMenuRecentSearchesText
{
    return UI_STRING("Recent Searches", "label for first item in the menu that appears when clicking on the search field image, used as embedded menu title");
}

- (NSString *)searchMenuClearRecentSearchesText
{
    return UI_STRING("Clear Recent Searches", "menu item in Recent Searches menu that empties menu's contents");
}

- (NSString *)defaultLanguageCode
{
    return [NSUserDefaults _webkit_preferredLanguageCode];
}

- (NSString *)contextMenuItemTagOpenLinkInNewWindow
{
    return UI_STRING("Open Link in New Window", "Open in New Window context menu item");
}

- (NSString *)contextMenuItemTagDownloadLinkToDisk
{
    return UI_STRING("Download Linked File", "Download Linked File context menu item");
}

- (NSString *)contextMenuItemTagCopyLinkToClipboard
{
    return UI_STRING("Copy Link", "Copy Link context menu item");
}

- (NSString *)contextMenuItemTagOpenImageInNewWindow
{
    return UI_STRING("Open Image in New Window", "Open Image in New Window context menu item");
}

- (NSString *)contextMenuItemTagDownloadImageToDisk
{
    return UI_STRING("Download Image", "Download Image context menu item");
}

- (NSString *)contextMenuItemTagCopyImageToClipboard
{
    return UI_STRING("Copy Image", "Copy Image context menu item");
}

- (NSString *)contextMenuItemTagOpenFrameInNewWindow
{
    return UI_STRING("Open Frame in New Window", "Open Frame in New Window context menu item");
}

- (NSString *)contextMenuItemTagCopy
{
    return UI_STRING("Copy", "Copy context menu item");
}

- (NSString *)contextMenuItemTagGoBack
{
    return UI_STRING("Back", "Back context menu item");
}

- (NSString *)contextMenuItemTagGoForward
{
    return UI_STRING("Forward", "Forward context menu item");
}

- (NSString *)contextMenuItemTagStop
{
    return UI_STRING("Stop", "Stop context menu item");
}

- (NSString *)contextMenuItemTagReload
{
    return UI_STRING("Reload", "Reload context menu item");
}

- (NSString *)contextMenuItemTagCut
{
    return UI_STRING("Cut", "Cut context menu item");
}

- (NSString *)contextMenuItemTagPaste
{
    return UI_STRING("Paste", "Paste context menu item");
}

- (NSString *)contextMenuItemTagNoGuessesFound
{
    return UI_STRING("No Guesses Found", "No Guesses Found context menu item");
}

- (NSString *)contextMenuItemTagIgnoreSpelling
{
    return UI_STRING("Ignore Spelling", "Ignore Spelling context menu item");
}

- (NSString *)contextMenuItemTagLearnSpelling
{
    return UI_STRING("Learn Spelling", "Learn Spelling context menu item");
}

- (NSString *)contextMenuItemTagSearchInSpotlight
{
    return UI_STRING("Search in Spotlight", "Search in Spotlight context menu item");
}

- (NSString *)contextMenuItemTagSearchWeb
{
    return UI_STRING("Search in Google", "Search in Google context menu item");
}

- (NSString *)contextMenuItemTagLookUpInDictionary
{
    return UI_STRING("Look Up in Dictionary", "Look Up in Dictionary context menu item");
}

- (NSString *)contextMenuItemTagOpenLink
{
    return UI_STRING("Open Link", "Open Link context menu item");
}

- (NSString *)contextMenuItemTagIgnoreGrammar
{
    return UI_STRING("Ignore Grammar", "Ignore Grammar context menu item");
}

- (NSString *)contextMenuItemTagSpellingMenu
{
#ifndef BUILDING_ON_TIGER
    return UI_STRING("Spelling and Grammar", "Spelling and Grammar context sub-menu item");
#else
    return UI_STRING("Spelling", "Spelling context sub-menu item");
#endif
}

- (NSString *)contextMenuItemTagShowSpellingPanel:(bool)show
{
#ifndef BUILDING_ON_TIGER
    if (show)
        return UI_STRING("Show Spelling and Grammar", "menu item title");
    return UI_STRING("Hide Spelling and Grammar", "menu item title");
#else
    return UI_STRING("Spelling...", "menu item title");
#endif
}

- (NSString *)contextMenuItemTagCheckSpelling
{
#ifndef BUILDING_ON_TIGER
    return UI_STRING("Check Document Now", "Check spelling context menu item");
#else
    return UI_STRING("Check Spelling", "Check spelling context menu item");
#endif
}

- (NSString *)contextMenuItemTagCheckSpellingWhileTyping
{
#ifndef BUILDING_ON_TIGER
    return UI_STRING("Check Spelling While Typing", "Check spelling while typing context menu item");
#else
    return UI_STRING("Check Spelling as You Type", "Check spelling while typing context menu item");
#endif
}

- (NSString *)contextMenuItemTagCheckGrammarWithSpelling
{
    return UI_STRING("Check Grammar With Spelling", "Check grammar with spelling context menu item");
}

- (NSString *)contextMenuItemTagFontMenu
{
    return UI_STRING("Font", "Font context sub-menu item");
}

- (NSString *)contextMenuItemTagShowFonts
{
    return UI_STRING("Show Fonts", "Show fonts context menu item");
}

- (NSString *)contextMenuItemTagBold
{
    return UI_STRING("Bold", "Bold context menu item");
}

- (NSString *)contextMenuItemTagItalic
{
    return UI_STRING("Italic", "Italic context menu item");
}

- (NSString *)contextMenuItemTagUnderline
{
    return UI_STRING("Underline", "Underline context menu item");
}

- (NSString *)contextMenuItemTagOutline
{
    return UI_STRING("Outline", "Outline context menu item");
}

- (NSString *)contextMenuItemTagStyles
{
    return UI_STRING("Styles...", "Styles context menu item");
}

- (NSString *)contextMenuItemTagShowColors
{
    return UI_STRING("Show colors", "Show colors context menu item");
}

- (NSString *)contextMenuItemTagSpeechMenu
{
    return UI_STRING("Speech", "Speech context sub-menu item");
}

- (NSString *)contextMenuItemTagStartSpeaking
{
    return UI_STRING("Start Speaking", "Start speaking context menu item");
}

- (NSString *)contextMenuItemTagStopSpeaking
{
    return UI_STRING("Stop Speaking", "Stop speaking context menu item");
}

- (NSString *)contextMenuItemTagWritingDirectionMenu
{
    return UI_STRING("Writing Direction", "Writing direction context sub-menu item");
}

- (NSString *)contextMenuItemTagDefaultDirection
{
    return UI_STRING("Default", "Default writing direction context menu item");
}

- (NSString *)contextMenuItemTagLeftToRight
{
    return UI_STRING("Left to Right", "Left to Right context menu item");
}

- (NSString *)contextMenuItemTagRightToLeft
{
    return UI_STRING("Right to Left", "Right to Left context menu item");
}

- (NSString *)contextMenuItemTagInspectElement
{
    return UI_STRING("Inspect Element", "Inspect Element context menu item");
}

- (BOOL)objectIsTextMarker:(id)object
{
    return object != nil && CFGetTypeID(object) == WKGetAXTextMarkerTypeID();
}

- (BOOL)objectIsTextMarkerRange:(id)object
{
    return object != nil && CFGetTypeID(object) == WKGetAXTextMarkerRangeTypeID();
}

- (WebCoreTextMarker *)textMarkerWithBytes:(const void *)bytes length:(size_t)length
{
    return WebCFAutorelease(WKCreateAXTextMarker(bytes, length));
}

- (BOOL)getBytes:(void *)bytes fromTextMarker:(WebCoreTextMarker *)textMarker length:(size_t)length
{
    return WKGetBytesFromAXTextMarker(textMarker, bytes, length);
}

- (WebCoreTextMarkerRange *)textMarkerRangeWithStart:(WebCoreTextMarker *)start end:(WebCoreTextMarker *)end
{
    ASSERT(start != nil);
    ASSERT(end != nil);
    ASSERT(CFGetTypeID(start) == WKGetAXTextMarkerTypeID());
    ASSERT(CFGetTypeID(end) == WKGetAXTextMarkerTypeID());
    return WebCFAutorelease(WKCreateAXTextMarkerRange(start, end));
}

- (WebCoreTextMarker *)startOfTextMarkerRange:(WebCoreTextMarkerRange *)range
{
    ASSERT(range != nil);
    ASSERT(CFGetTypeID(range) == WKGetAXTextMarkerRangeTypeID());
    return WebCFAutorelease(WKCopyAXTextMarkerRangeStart(range));
}

- (WebCoreTextMarker *)endOfTextMarkerRange:(WebCoreTextMarkerRange *)range
{
    ASSERT(range != nil);
    ASSERT(CFGetTypeID(range) == WKGetAXTextMarkerRangeTypeID());
    return WebCFAutorelease(WKCopyAXTextMarkerRangeEnd(range));
}

- (void)accessibilityHandleFocusChanged
{
    WKAccessibilityHandleFocusChanged();
}

- (AXUIElementRef)AXUIElementForElement:(id)element
{
    return WKCreateAXUIElementRef(element);
}

- (void)unregisterUniqueIdForUIElement:(id)element
{
    WKUnregisterUniqueIdForElement(element);
}

- (NSString *)AXWebAreaText
{
    return UI_STRING("web area", "accessibility role description for web area");
}

- (NSString *)AXLinkText
{
    return UI_STRING("link", "accessibility role description for link");
}

- (NSString *)AXListMarkerText
{
    return UI_STRING("list marker", "accessibility role description for list marker");
}

- (NSString *)AXImageMapText
{
    return UI_STRING("image map", "accessibility role description for image map");
}

- (NSString *)AXHeadingText
{
    return UI_STRING("heading", "accessibility role description for headings");
}

- (NSString *)unknownFileSizeText
{
    return UI_STRING("Unknown", "Unknown filesize FTP directory listing item");
}

@end
