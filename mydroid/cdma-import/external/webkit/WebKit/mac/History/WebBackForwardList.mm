/*
 * Copyright (C) 2005, 2007 Apple Inc.  All rights reserved.
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

#import "WebBackForwardList.h"
#import "WebBackForwardListInternal.h"

#import "WebFrameInternal.h"
#import "WebHistoryItemInternal.h"
#import "WebHistoryItemPrivate.h"
#import "WebKitLogging.h"
#import "WebNSObjectExtras.h"
#import "WebPreferencesPrivate.h"
#import "WebTypesInternal.h"
#import "WebViewPrivate.h"
#import <JavaScriptCore/Assertions.h>
#import <WebCore/BackForwardList.h>
#import <WebCore/HistoryItem.h>
#import <WebCore/Page.h>
#import <WebCore/PageCache.h>
#import <WebCore/Settings.h>
#import <WebCore/ThreadCheck.h>
#import <WebCore/WebCoreObjCExtras.h>
#import <wtf/RetainPtr.h>

using namespace WebCore;

static HashMap<BackForwardList*, WebBackForwardList*>& backForwardLists()
{
    static HashMap<BackForwardList*, WebBackForwardList*> staticBackForwardLists;
    return staticBackForwardLists;
}

@implementation WebBackForwardList (WebBackForwardListInternal)

BackForwardList* core(WebBackForwardList *webBackForwardList)
{
    if (!webBackForwardList)
        return 0;

    return reinterpret_cast<BackForwardList*>(webBackForwardList->_private);
}

WebBackForwardList *kit(BackForwardList* backForwardList)
{
    if (!backForwardList)
        return nil;

    if (WebBackForwardList *webBackForwardList = backForwardLists().get(backForwardList))
        return webBackForwardList;

    return [[[WebBackForwardList alloc] initWithBackForwardList:backForwardList] autorelease];
}

- (id)initWithBackForwardList:(PassRefPtr<BackForwardList>)backForwardList
{   
    WebCoreThreadViolationCheck();
    self = [super init];
    if (!self)
        return nil;

    _private = reinterpret_cast<WebBackForwardListPrivate*>(backForwardList.releaseRef());
    backForwardLists().set(core(self), self);
    return self;
}

@end

@implementation WebBackForwardList

#ifndef BUILDING_ON_TIGER
+ (void)initialize
{
    WebCoreObjCFinalizeOnMainThread(self);
}
#endif

- (id)init
{
    RefPtr<BackForwardList> coreList(new BackForwardList(0));
    return [self initWithBackForwardList:coreList.release()];
}

- (void)dealloc
{
    WebCoreThreadViolationCheck();
    BackForwardList* backForwardList = core(self);
    ASSERT(backForwardList->closed());
    backForwardLists().remove(backForwardList);
    backForwardList->deref();
        
    [super dealloc];
}

- (void)finalize
{
    WebCoreThreadViolationCheck();
    BackForwardList* backForwardList = core(self);
    ASSERT(backForwardList->closed());
    backForwardLists().remove(backForwardList);
    backForwardList->deref();
        
    [super finalize];
}

- (void)_close
{
    core(self)->close();
}

- (void)addItem:(WebHistoryItem *)entry;
{
    core(self)->addItem(core(entry));
    
    // Since the assumed contract with WebBackForwardList is that it retains its WebHistoryItems,
    // the following line prevents a whole class of problems where a history item will be created in
    // a function, added to the BFlist, then used in the rest of that function.
    [[entry retain] autorelease];
}

- (void)removeItem:(WebHistoryItem *)item
{
    core(self)->removeItem(core(item));
}

- (BOOL)containsItem:(WebHistoryItem *)item
{
    return core(self)->containsItem(core(item));
}

- (void)goBack
{
    core(self)->goBack();
}

- (void)goForward
{
    core(self)->goForward();
}

- (void)goToItem:(WebHistoryItem *)item
{
    core(self)->goToItem(core(item));
}

- (WebHistoryItem *)backItem
{
    return [[kit(core(self)->backItem()) retain] autorelease];
}

- (WebHistoryItem *)currentItem
{
    return [[kit(core(self)->currentItem()) retain] autorelease];
}

- (WebHistoryItem *)forwardItem
{
    return [[kit(core(self)->forwardItem()) retain] autorelease];
}

static NSArray* vectorToNSArray(HistoryItemVector& list)
{
    unsigned size = list.size();
    NSMutableArray *result = [[[NSMutableArray alloc] initWithCapacity:size] autorelease];
    for (unsigned i = 0; i < size; ++i)
        [result addObject:kit(list[i].get())];

    return result;
}

- (NSArray *)backListWithLimit:(int)limit;
{
    HistoryItemVector list;
    core(self)->backListWithLimit(limit, list);
    return vectorToNSArray(list);
}

- (NSArray *)forwardListWithLimit:(int)limit;
{
    HistoryItemVector list;
    core(self)->forwardListWithLimit(limit, list);
    return vectorToNSArray(list);
}

- (int)capacity
{
    return core(self)->capacity();
}

- (void)setCapacity:(int)size
{
    core(self)->setCapacity(size);
}


-(NSString *)description
{
    NSMutableString *result;
    
    result = [NSMutableString stringWithCapacity:512];
    
    [result appendString:@"\n--------------------------------------------\n"];    
    [result appendString:@"WebBackForwardList:\n"];
    
    BackForwardList* backForwardList = core(self);
    HistoryItemVector& entries = backForwardList->entries();
    
    unsigned size = entries.size();
    for (unsigned i = 0; i < size; ++i) {
        if (entries[i] == backForwardList->currentItem()) {
            [result appendString:@" >>>"]; 
        } else {
            [result appendString:@"    "]; 
        }   
        [result appendFormat:@"%2d) ", i];
        int currPos = [result length];
        [result appendString:[kit(entries[i].get()) description]];

        // shift all the contents over.  a bit slow, but this is for debugging
        NSRange replRange = {currPos, [result length]-currPos};
        [result replaceOccurrencesOfString:@"\n" withString:@"\n        " options:0 range:replRange];
        
        [result appendString:@"\n"];
    }

    [result appendString:@"\n--------------------------------------------\n"];    

    return result;
}

- (void)setPageCacheSize:(NSUInteger)size
{
    [kit(core(self)->page()) setUsesPageCache:size != 0];
}

- (NSUInteger)pageCacheSize
{
    return [kit(core(self)->page()) usesPageCache] ? pageCache()->capacity() : 0;
}

- (int)backListCount
{
    return core(self)->backListCount();
}

- (int)forwardListCount
{
    return core(self)->forwardListCount();
}

- (WebHistoryItem *)itemAtIndex:(int)index
{
    return [[kit(core(self)->itemAtIndex(index)) retain] autorelease];
}

@end
