/*
 * Copyright (C) 2006 Apple Computer, Inc.  All rights reserved.
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

#import "WebArchiver.h"

#import "WebArchive.h"
#import "WebDOMOperationsPrivate.h"
#import "WebDataSource.h"
#import "WebDocument.h"
#import "WebFrame.h"
#import "WebFrameBridge.h"
#import "WebFrameInternal.h"
#import "WebResource.h"
#import <JavaScriptCore/Assertions.h>
#import <WebCore/Frame.h>
#import <WebCore/SelectionController.h>
#import <WebKit/DOM.h>

using namespace WebCore;

@implementation WebArchiver

+ (NSArray *)_subframeArchivesForFrame:(WebFrame *)frame
{
    NSEnumerator *enumerator = [[frame childFrames] objectEnumerator];
    NSMutableArray *subframeArchives = [NSMutableArray array];
    WebFrame *childFrame;
    while ((childFrame = [enumerator nextObject])) {
        WebArchive *childFrameArchive = [self archiveFrame:childFrame];
        if (childFrameArchive)
            [subframeArchives addObject:childFrameArchive];
    }

    return subframeArchives;
}

+ (WebArchive *)archiveFrame:(WebFrame *)frame;
{
    return [[[WebArchive alloc] initWithMainResource:[[frame _dataSource] mainResource]
                                        subresources:[[frame _dataSource] subresources]
                                    subframeArchives:[self _subframeArchivesForFrame:frame]] autorelease];
}

+ (WebArchive *)archiveMainResourceForFrame:(WebFrame *)frame;
{
    return [[[WebArchive alloc] initWithMainResource:[[frame _dataSource] mainResource]
                                        subresources:nil
                                    subframeArchives:nil] autorelease];
}

+ (WebArchive *)_archiveCurrentStateForFrame:(WebFrame *)frame
{
    if ([frame DOMDocument])
        return [self archiveNode:[frame DOMDocument]];

    return [self archiveFrame:frame];
}

+ (WebArchive *)_archiveWithMarkupString:(NSString *)markupString fromFrame:(WebFrame *)frame nodes:(NSArray *)nodes
{ 
    NSURLResponse *response = [[frame _dataSource] response];
    NSURL *responseURL = [response URL];
    
    // it's possible to have a response without a URL here
    // <rdar://problem/5454935>
    if (!responseURL)
        responseURL = [NSURL URLWithString:@""];
    
    WebResource *mainResource = [[WebResource alloc] initWithData:[markupString dataUsingEncoding:NSUTF8StringEncoding]
                                                              URL:responseURL
                                                         MIMEType:[response MIMEType]
                                                 textEncodingName:@"UTF-8"
                                                        frameName:[frame name]];
    
    NSMutableArray *subframeArchives = [[NSMutableArray alloc] init];
    NSMutableArray *subresources = [[NSMutableArray alloc] init];
    NSMutableSet *uniqueSubresources = [[NSMutableSet alloc] init];
    NSEnumerator *enumerator = [nodes objectEnumerator];
    DOMNode *node;
    while ((node = [enumerator nextObject]) != nil) {
        WebFrame *childFrame;
        if (([node isKindOfClass:[DOMHTMLFrameElement class]] || 
             [node isKindOfClass:[DOMHTMLIFrameElement class]] || 
             [node isKindOfClass:[DOMHTMLObjectElement class]]) &&
            ((childFrame = [(DOMHTMLFrameElement *)node contentFrame]) != nil)) {
            [subframeArchives addObject:[self _archiveCurrentStateForFrame:childFrame]];
        } else {
            NSEnumerator *enumerator = [[node _subresourceURLs] objectEnumerator];
            NSURL *URL;
            while ((URL = [enumerator nextObject]) != nil) {
                if ([uniqueSubresources containsObject:URL])
                    continue;
                [uniqueSubresources addObject:URL];
                WebResource *subresource = [[frame _dataSource] subresourceForURL:URL];
                if (subresource)
                    [subresources addObject:subresource];
                else
                    // FIXME: should do something better than spew to console here
                    LOG_ERROR("Failed to archive subresource for %@", URL);
            }
        }
    }
    
    WebArchive *archive = [[[WebArchive alloc] initWithMainResource:mainResource subresources:subresources subframeArchives:subframeArchives] autorelease];
    [mainResource release];
    [uniqueSubresources release];
    [subresources release];
    [subframeArchives release];
    
    return archive;
}

+ (WebArchive *)archiveRange:(DOMRange *)range
{
    WebFrameBridge *bridge = [range _bridge];
    WebFrame *frame = [bridge webFrame];
    NSArray *nodes;
    NSString *markupString = [bridge markupStringFromRange:range nodes:&nodes];
    return [self _archiveWithMarkupString:markupString fromFrame:frame nodes:nodes];
}

+ (WebArchive *)archiveNode:(DOMNode *)node
{
    WebFrame *frame = [[node ownerDocument] webFrame];
    WebFrameBridge *bridge = [frame _bridge];
    NSArray *nodes;
    NSString *markupString = [bridge markupStringFromNode:node nodes:&nodes];
    return [self _archiveWithMarkupString:markupString fromFrame:frame nodes:nodes];
}

+ (WebArchive *)archiveSelectionInFrame:(WebFrame *)frame
{
    Frame* coreFrame = core(frame);
    if (!coreFrame)
        return nil;

    WebFrameBridge *bridge = [frame _bridge];
    NSArray *nodes;
    NSString *markupString = [bridge markupStringFromRange:kit(coreFrame->selectionController()->toRange().get()) nodes:&nodes];
    WebArchive *archive = [self _archiveWithMarkupString:markupString fromFrame:frame nodes:nodes];

    if (coreFrame->isFrameSet()) {
        // Wrap the frameset document in an iframe so it can be pasted into
        // another document (which will have a body or frameset of its own). 

        NSString *iframeMarkup = [[NSString alloc] initWithFormat:@"<iframe frameborder=\"no\" marginwidth=\"0\" marginheight=\"0\" width=\"98%%\" height=\"98%%\" src=\"%@\"></iframe>", [[[frame _dataSource] response] URL]];
        WebResource *iframeResource = [[WebResource alloc] initWithData:[iframeMarkup dataUsingEncoding:NSUTF8StringEncoding]
                                                                  URL:[NSURL URLWithString:@"about:blank"]
                                                             MIMEType:@"text/html"
                                                     textEncodingName:@"UTF-8"
                                                            frameName:nil];
        
        NSArray *subframeArchives = [NSArray arrayWithObject:archive];
        archive = [[[WebArchive alloc] initWithMainResource:iframeResource subresources:nil subframeArchives:subframeArchives] autorelease];
        
        [iframeResource release];
        [iframeMarkup release];
    }

    return archive;
}

@end
 
