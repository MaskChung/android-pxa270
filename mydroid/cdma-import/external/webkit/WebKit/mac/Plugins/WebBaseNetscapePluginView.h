/*
 * Copyright (C) 2005, 2007 Apple Inc. All rights reserved.
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
#import <Cocoa/Cocoa.h>

#import <AGL/agl.h>
#import <WebKit/npfunctions.h>
#import <WebKit/npapi.h>
#import <WebKit/WebBasePluginPackage.h>

@class DOMElement;
@class WebDataSource;
@class WebFrame;
@class WebNetscapePluginPackage;
@class WebNetscapePluginNullEventSender;
@class WebBaseNetscapePluginStream;
@class WebNetscapePluginStream;
@class WebView;

typedef union PluginPort {
#ifndef NP_NO_QUICKDRAW
    NP_Port qdPort;
#endif        
    NP_CGContext cgPort;
    NP_GLContext aglPort;
} PluginPort;

@interface WebBaseNetscapePluginView : NSView <WebPluginManualLoader>
{
    WebNetscapePluginPackage *pluginPackage;
    
    NSURL *sourceURL;
    WebFrame *_webFrame;
    
    BOOL _loadManually;
    WebNetscapePluginStream *_manualStream;
    unsigned _dataLengthReceived;
    NSError *_error;
    
    int mode;
    
    unsigned argsCount;
    char **cAttributes;
    char **cValues;
        
    NPP plugin;
    NPWindow window;
    NPWindow lastSetWindow;
    PluginPort nPort;
    PluginPort lastSetPort;
    NPDrawingModel drawingModel;
    
    // These are only valid when drawingModel is NPDrawingModelOpenGL
    AGLContext aglContext;
    NSWindow *aglWindow;

#ifndef NP_NO_QUICKDRAW
    // This is only valid when drawingModel is NPDrawingModelQuickDraw
    GWorldPtr offscreenGWorld;
#endif

    BOOL isStarted;
    BOOL inSetWindow;
    BOOL suspendKeyUpEvents;
    BOOL hasFocus;
    BOOL currentEventIsUserGesture;
    BOOL isTransparent;
    BOOL isCompletelyObscured;
    BOOL shouldStopSoon;

    unsigned pluginFunctionCallDepth;
    
    DOMElement *element;
    
    int32 specifiedHeight;
    int32 specifiedWidth;
            
    NSString *MIMEType;
    NSURL *baseURL;
    NSTrackingRectTag trackingTag;
    NSMutableArray *streams;
    NSMutableDictionary *pendingFrameLoads;
    NSTimer *nullEventTimer;
    
    NPP_NewProcPtr NPP_New;
    NPP_DestroyProcPtr NPP_Destroy;
    NPP_SetWindowProcPtr NPP_SetWindow;
    NPP_NewStreamProcPtr NPP_NewStream;
    NPP_DestroyStreamProcPtr NPP_DestroyStream;
    NPP_StreamAsFileProcPtr NPP_StreamAsFile;
    NPP_WriteReadyProcPtr NPP_WriteReady;
    NPP_WriteProcPtr NPP_Write;
    NPP_PrintProcPtr NPP_Print;
    NPP_HandleEventProcPtr NPP_HandleEvent;
    NPP_URLNotifyProcPtr NPP_URLNotify;
    NPP_GetValueProcPtr NPP_GetValue;
    NPP_SetValueProcPtr NPP_SetValue;
    
    EventHandlerRef keyEventHandler;
}

+ (WebBaseNetscapePluginView *)currentPluginView;


- (id)initWithFrame:(NSRect)r
      pluginPackage:(WebNetscapePluginPackage *)thePluginPackage
                URL:(NSURL *)URL
            baseURL:(NSURL *)baseURL
           MIMEType:(NSString *)MIME
      attributeKeys:(NSArray *)keys
    attributeValues:(NSArray *)values
       loadManually:(BOOL)loadManually
         DOMElement:(DOMElement *)anElement;


- (BOOL)start;
- (BOOL)isStarted;
- (void)stop;

- (WebFrame *)webFrame;
- (WebDataSource *)dataSource;
- (WebView *)webView;
- (NSWindow *)currentWindow;

- (NPP)plugin;

- (WebNetscapePluginPackage *)pluginPackage;
- (void)setPluginPackage:(WebNetscapePluginPackage *)thePluginPackage;
- (void)setMIMEType:(NSString *)theMIMEType;
- (void)setBaseURL:(NSURL *)theBaseURL;
- (void)setAttributeKeys:(NSArray *)keys andValues:(NSArray *)values;
- (void)setMode:(int)theMode;
- (void)viewWillMoveToHostWindow:(NSWindow *)hostWindow;
- (void)viewDidMoveToHostWindow;
- (void)disconnectStream:(WebBaseNetscapePluginStream*)stream;

// Returns the NPObject that represents the plugin interface.
// The return value is expected to be retained.
- (NPObject *)createPluginScriptableObject;

// -willCallPlugInFunction must be called before calling any of the NPP_* functions for this view's plugin.
// This is necessary to ensure that plug-ins are not destroyed while WebKit calls into them.  Some plug-ins (Flash
// at least) are written with the assumption that nothing they do in their plug-in functions can cause NPP_Destroy()
// to be called.  Unfortunately, this is not true, especially if the plug-in uses NPN_Invoke() to execute a
// document.write(), which clears the document and destroys the plug-in.
// See <rdar://problem/4480737>.
- (void)willCallPlugInFunction;

// -didCallPlugInFunction should be called after returning from a plug-in function.  It should be called exactly
// once for every call to -willCallPlugInFunction.
// See <rdar://problem/4480737>.
- (void)didCallPlugInFunction;

@end
#endif

