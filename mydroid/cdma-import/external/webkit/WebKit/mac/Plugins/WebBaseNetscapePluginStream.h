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
#ifndef __LP64__
#import <Foundation/Foundation.h>

#import <WebKit/npfunctions.h>
#import <WebKit/WebPlugInStreamLoaderDelegate.h>

@class WebBaseNetscapePluginView;
@class NSURLResponse;

@interface WebBaseNetscapePluginStream : NSObject<WebPlugInStreamLoaderDelegate>
{
    NSMutableData *deliveryData;
    NSURL *requestURL;
    NSURL *responseURL;
    NSString *MIMEType;
    
    NPP plugin;
    uint16 transferMode;
    int32 offset;
    NPStream stream;
    NSString *path;
    int fileDescriptor;
    BOOL sendNotification;
    void *notifyData;
    char *headers;
    WebBaseNetscapePluginView *pluginView;
    NPReason reason;
    BOOL isTerminated;
        
    NPP_NewStreamProcPtr NPP_NewStream;
    NPP_DestroyStreamProcPtr NPP_DestroyStream;
    NPP_StreamAsFileProcPtr NPP_StreamAsFile;
    NPP_WriteReadyProcPtr NPP_WriteReady;
    NPP_WriteProcPtr NPP_Write;
    NPP_URLNotifyProcPtr NPP_URLNotify;
}

+ (NPP)ownerForStream:(NPStream *)stream;
+ (NPReason)reasonForError:(NSError *)error;

- (NSError *)errorForReason:(NPReason)theReason;

- (id)initWithRequestURL:(NSURL *)theRequestURL
                  plugin:(NPP)thePlugin
              notifyData:(void *)theNotifyData
        sendNotification:(BOOL)flag;

- (void)setRequestURL:(NSURL *)theRequestURL;
- (void)setResponseURL:(NSURL *)theResponseURL;
- (void)setPlugin:(NPP)thePlugin;

- (uint16)transferMode;
- (NPP)plugin;

- (void)startStreamResponseURL:(NSURL *)theResponseURL
         expectedContentLength:(long long)expectedContentLength
              lastModifiedDate:(NSDate *)lastModifiedDate
                      MIMEType:(NSString *)MIMEType
                       headers:(NSData *)theHeaders;

// cancelLoadWithError cancels the NSURLConnection and informs WebKit of the load error.
// This method is overriden by subclasses.
- (void)cancelLoadWithError:(NSError *)error;

@end
#endif
