/*
 *
 * Copyright 2007, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *
 *     http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

#include "CachedPrefix.h"
#include "CachedFrame.h"
#include "CachedHistory.h"
#include "Node.h"
#include "PlatformString.h"

#include "android_graphics.h"
#include "CachedNode.h"

namespace android {

void CachedNode::clearFocus(CachedFrame* parent)
{
    if (isFrame()) {
        CachedFrame* child = const_cast<CachedFrame*>(parent->hasFrame(this));
        child->clearFocus();
    }
    mIsFocus = false;
}

bool CachedNode::Clip(const WebCore::IntRect& outer, WebCore::IntRect* inner,
    WTF::Vector<WebCore::IntRect>* rings)
{
    if (outer.contains(*inner))
        return true;
//    DBG_NAV_LOGD("outer:{%d,%d,%d,%d} does not contain inner:{%d,%d,%d,%d}",
//        outer.x(), outer.y(), outer.width(), outer.height(),
//        inner->x(), inner->y(), inner->width(), inner->height());
    bool intersects = outer.intersects(*inner);
    size_t size = intersects ? rings->size() : 0;
    *inner = WebCore::IntRect(0, 0, 0, 0);
    if (intersects) {
        WebCore::IntRect * const start = rings->begin();
        WebCore::IntRect* ring = start + size - 1;
        do {
            ring->intersect(outer);
            if (ring->isEmpty()) {
                if ((size_t) (ring - start) != --size)
                    *ring = start[size];
            } else
                inner->unite(*ring);
        } while (ring-- != start);
    }
    rings->shrink(size);
//    DBG_NAV_LOGD("size:%d", size);
    return size != 0;
}

bool CachedNode::clip(const WebCore::IntRect& bounds)
{
    return Clip(bounds, &mBounds, &mFocusRing);
}

#define OVERLAP 3

void CachedNode::fixUpFocusRects()
{
    if (mFixedUpFocusRects)
        return;
    mFixedUpFocusRects = true;
    if (mNavableRects <= 1)
        return;
#if DEBUG_NAV_UI
    {
        WebCore::IntRect* boundsPtr = mFocusRing.begin() - 1;
        const WebCore::IntRect* const boundsEnd = mFocusRing.begin() + mFocusRing.size();
        while (++boundsPtr < boundsEnd)
            LOGD("%s %d:(%d, %d, %d, %d)\n", __FUNCTION__, boundsPtr - mFocusRing.begin(),
                boundsPtr->x(), boundsPtr->y(), boundsPtr->width(), boundsPtr->height());
    }
#endif
    // q: need to know when rects are for drawing and hit-testing, but not mouse down calcs?
    bool again;
    do {
        again = false;
        size_t size = mFocusRing.size();
        WebCore::IntRect* unitBoundsPtr = mFocusRing.begin() - 1;
        const WebCore::IntRect* const unitBoundsEnd = mFocusRing.begin() + size;
        while (++unitBoundsPtr < unitBoundsEnd) {
            // any other unitBounds to the left or right of this one?
            int unitTop = unitBoundsPtr->y();
            int unitBottom = unitBoundsPtr->bottom();
            int unitLeft = unitBoundsPtr->x();
            int unitRight = unitBoundsPtr->right();
            WebCore::IntRect* testBoundsPtr = mFocusRing.begin() - 1;
            while (++testBoundsPtr < unitBoundsEnd) {
                if (unitBoundsPtr == testBoundsPtr)
                    continue;
                int testTop = testBoundsPtr->y();
                int testBottom = testBoundsPtr->bottom();
                int testLeft = testBoundsPtr->x();
                int testRight = testBoundsPtr->right();
                int candidateTop = unitTop > testTop ? unitTop : testTop;
                int candidateBottom = unitBottom < testBottom ? unitBottom : testBottom;
                int candidateLeft = unitRight < testLeft ? unitRight : testRight;
                int candidateRight = unitRight > testLeft ? unitLeft : testLeft;
                bool leftRight = true;
                if (candidateTop + OVERLAP >= candidateBottom ||
                        candidateLeft + OVERLAP >= candidateRight) {
                    candidateTop = unitBottom < testTop ? unitBottom : testBottom;
                    candidateBottom = unitBottom > testTop ? unitTop : testTop;
                    candidateLeft = unitLeft > testLeft ? unitLeft : testLeft;
                    candidateRight = unitRight < testRight ? unitRight : testRight;
                    if (candidateTop + OVERLAP >= candidateBottom ||
                            candidateLeft + OVERLAP >= candidateRight)
                        continue;
                    leftRight = false;
                }
                // construct candidate to add
                WebCore::IntRect candidate = WebCore::IntRect(candidateLeft, candidateTop, 
                    candidateRight - candidateLeft, candidateBottom - candidateTop);
                // does a different unit bounds intersect the candidate? if so, don't add
                WebCore::IntRect* checkBoundsPtr = mFocusRing.begin() - 1;
                while (++checkBoundsPtr < unitBoundsEnd) {
                    if (checkBoundsPtr->intersects(candidate) == false)
                        continue;
                    if (leftRight) {
                        if (candidateTop >= checkBoundsPtr->y() &&
                                candidateBottom > checkBoundsPtr->bottom())
                            candidateTop = checkBoundsPtr->bottom(); 
                        else if (candidateTop < checkBoundsPtr->y() &&
                                candidateBottom <= checkBoundsPtr->bottom())
                            candidateBottom = checkBoundsPtr->y();
                        else
                            goto nextCheck;
                    } else {
                        if (candidateLeft >= checkBoundsPtr->x() &&
                                candidateRight > checkBoundsPtr->right())
                            candidateLeft = checkBoundsPtr->right(); 
                        else if (candidateLeft < checkBoundsPtr->x() &&
                                candidateRight <= checkBoundsPtr->right())
                            candidateRight = checkBoundsPtr->x();
                        else
                            goto nextCheck;
                    }
                 } 
                 candidate = WebCore::IntRect(candidateLeft, candidateTop, 
                    candidateRight - candidateLeft, candidateBottom - candidateTop);
                 ASSERT(candidate.isEmpty() == false);
#if DEBUG_NAV_UI
                LOGD("%s %d:(%d, %d, %d, %d)\n", __FUNCTION__, mFocusRing.size(),
                    candidate.x(), candidate.y(), candidate.width(), candidate.height());
#endif
                mFocusRing.append(candidate);
                again = true;
                goto tryAgain;
        nextCheck:
                continue;
            }
        }
tryAgain:
        ;
    } while (again);
}


void CachedNode::focusRingBounds(WebCore::IntRect* bounds) const
{
    int partMax = mNavableRects;
    ASSERT(partMax > 0);
    *bounds = mFocusRing[0];
    for (int partIndex = 1; partIndex < partMax; partIndex++)
        bounds->unite(mFocusRing[partIndex]);
    bounds->inflate(FOCUS_RING_HIT_TEST_RADIUS);
}

void CachedNode::init(CachedFrame* frame, WebCore::Node* node)
{
    bzero(this, sizeof(CachedNode));
    mExport = WebCore::String();
    mName = WebCore::String();
    mNode = node;
    mParentIndex = mChildFrameIndex = -1;
    mType = android::NORMAL_CACHEDNODETYPE;
}

void CachedNode::move(int x, int y)
{
    mBounds.move(x, y);
    // mHitTestBounds will be moved by caller
    WebCore::IntRect* first = mFocusRing.begin();
    WebCore::IntRect* last = first + mFocusRing.size();
    --first;
    while (++first != last)
        first->move(x, y);
}

bool CachedNode::partRectsContains(const CachedNode* other) const
{    
    int outerIndex = 0;
    int outerMax = mNavableRects;
    int innerMax = other->mNavableRects;
    do {
        const WebCore::IntRect& outerBounds = mFocusRing[outerIndex];
        int innerIndex = 0;
        do {
            const WebCore::IntRect& innerBounds = other->mFocusRing[innerIndex];
            if (innerBounds.contains(outerBounds))
                return true;
        } while (++innerIndex < innerMax);
    } while (++outerIndex < outerMax);
    return false;
}

#if DUMP_NAV_CACHE

#define DEBUG_PRINT_BOOL(field) \
    DUMP_NAV_LOGD("// bool " #field "=%s;\n", b->field ? "true" : "false")

#define DEBUG_PRINT_RECT(field) \
    { const WebCore::IntRect& r = b->field; \
    DUMP_NAV_LOGD("// IntRect " #field "={%d, %d, %d, %d};\n", \
        r.x(), r.y(), r.width(), r.height()); }

CachedNode* CachedNode::Debug::base() const {
    CachedNode* nav = (CachedNode*) ((char*) this - OFFSETOF(CachedNode, mDebug));
    return nav; 
}

const char* CachedNode::Debug::condition(Condition t) const
{
    switch (t) {
        case NOT_REJECTED: return "NOT_REJECTED"; break;
        case BUTTED_UP: return "BUTTED_UP"; break;
        case CENTER_FURTHER: return "CENTER_FURTHER"; break;
        case CLOSER: return "CLOSER"; break;
        case CLOSER_IN_FOCUS: return "CLOSER_IN_FOCUS"; break;
        case CLOSER_OVERLAP: return "CLOSER_OVERLAP"; break;
        case CLOSER_TOP: return "CLOSER_TOP"; break;
        case FOCUSABLE: return "FOCUSABLE"; break;
        case FURTHER: return "FURTHER"; break;
        case IN_UMBRA: return "IN_UMBRA"; break;
        case IN_WORKING: return "IN_WORKING"; break;
        case LEFTMOST: return "LEFTMOST"; break;
        case OVERLAP_OR_EDGE_FURTHER: return "OVERLAP_OR_EDGE_FURTHER"; break;
        case PREFERRED: return "PREFERRED"; break;
        case ANCHOR_IN_ANCHOR: return "ANCHOR_IN_ANCHOR"; break;
        case BEST_DIRECTION: return "BEST_DIRECTION"; break;
        case CHILD: return "CHILD"; break;
        case DISABLED: return "DISABLED"; break;
        case IN_FOCUS: return "IN_FOCUS"; break;
        case IN_FOCUS_CHILDREN: return "IN_FOCUS_CHILDREN"; break;
        case NOT_ENCLOSING_FOCUS: return "NOT_ENCLOSING_FOCUS"; break;
   //     case NOT_FOCUS_CHILD: return "NOT_FOCUS_CHILD"; break;
        case NOT_FOCUS_NODE: return "NOT_FOCUS_NODE"; break;
        case OUTSIDE_OF_BEST: return "OUTSIDE_OF_BEST"; break;
        case OUTSIDE_OF_ORIGINAL: return "OUTSIDE_OF_ORIGINAL"; break;
        default: return "???";
    }
}

const char* CachedNode::Debug::type(android::CachedNodeType t) const
{
    switch (t) {
        case NORMAL_CACHEDNODETYPE: return "NORMAL"; break;
        case ADDRESS_CACHEDNODETYPE: return "ADDRESS"; break;
        case EMAIL_CACHEDNODETYPE: return "EMAIL"; break;
        case PHONE_CACHEDNODETYPE: return "PHONE"; break;
        default: return "???";
    }
}

void CachedNode::Debug::print() const
{
    CachedNode* b = base();
    char scratch[256];
    size_t index = snprintf(scratch, sizeof(scratch), "// char* mExport=\"");
    const UChar* ch = b->mExport.characters();
    while (ch && *ch && index < sizeof(scratch))
        scratch[index++] = *ch++;
    DUMP_NAV_LOGD("%.*s\"\n", index, scratch);
    index = snprintf(scratch, sizeof(scratch), "// char* mName=\"");
    ch = b->mName.characters();
    while (ch && *ch && index < sizeof(scratch))
        scratch[index++] = *ch++;
    DUMP_NAV_LOGD("%.*s\"\n", index, scratch);
    DEBUG_PRINT_RECT(mBounds);
    const WTF::Vector<WebCore::IntRect>& rects = b->focusRings();
    size_t size = rects.size();
    DUMP_NAV_LOGD("// IntRect focusRings={ // size=%d\n", size);
    for (size_t i = 0; i < size; i++)
        DUMP_NAV_LOGD("    // {%d, %d, %d, %d}, // %d\n", rects[i].x(), rects[i].y(),
            rects[i].width(), rects[i].height(), i);
    DUMP_NAV_LOGD("// };\n");
    DUMP_NAV_LOGD("// void* mNode=%p; // (%d) \n", b->mNode, mNodeIndex);
    DUMP_NAV_LOGD("// void* mParentGroup=%p; // (%d) \n", b->mParentGroup, mParentGroupIndex);
    DUMP_NAV_LOGD("// int mChildFrameIndex=%d;\n", b->mChildFrameIndex);
    DUMP_NAV_LOGD("// int mIndex=%d;\n", b->mIndex);
    DUMP_NAV_LOGD("// int mMaxLength=%d;\n", b->mMaxLength);
    DUMP_NAV_LOGD("// int mNavableRects=%d;\n", b->mNavableRects);
    DUMP_NAV_LOGD("// int mParentIndex=%d;\n", b->mParentIndex);
    DUMP_NAV_LOGD("// int mTextSize=%d;\n", b->mTextSize);
    DUMP_NAV_LOGD("// Condition mCondition=%s;\n", condition(b->mCondition));
    DUMP_NAV_LOGD("// Type mType=%s;\n", type(b->mType));
    DEBUG_PRINT_BOOL(mClippedOut);
    DEBUG_PRINT_BOOL(mDisabled);
    DEBUG_PRINT_BOOL(mFixedUpFocusRects);
    DEBUG_PRINT_BOOL(mHasMouseOver);
    DEBUG_PRINT_BOOL(mIsAnchor);
    DEBUG_PRINT_BOOL(mIsArea);
    DEBUG_PRINT_BOOL(mIsFocus);
    DEBUG_PRINT_BOOL(mIsInput);
    DEBUG_PRINT_BOOL(mIsParentAnchor);
    DEBUG_PRINT_BOOL(mIsPassword);
    DEBUG_PRINT_BOOL(mIsTextArea);
    DEBUG_PRINT_BOOL(mIsTextField);
    DEBUG_PRINT_BOOL(mIsTransparent);
    DEBUG_PRINT_BOOL(mIsUnclipped);
    DEBUG_PRINT_BOOL(mLast);
    DUMP_NAV_LOGD("\n");
}

#endif

}
