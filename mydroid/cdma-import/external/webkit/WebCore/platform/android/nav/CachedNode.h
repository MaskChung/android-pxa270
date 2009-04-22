/* 
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

#ifndef CachedNode_H
#define CachedNode_H

#include "AtomicString.h"
#include "CachedDebug.h"
#include "CachedNodeType.h"
#include "IntRect.h"
#include "PlatformString.h"
#include "wtf/Vector.h"

namespace WebCore {
    class Node;
}

namespace android {

class CachedFrame;

class CachedNode {
public:
// Nodes are rejected because either they are spacially not the best (first set)
// or because they have the wrong DOM attribute (in focus, a focused child, etc)
// findClosest() gives only spacially rejected nodes a second chance
    enum Condition { // if bigger than 32, increase bitfield size below
        // rejections that get a second chance
        NOT_REJECTED = 0,
        SECOND_CHANCE_START = NOT_REJECTED, // must be first in list
        BUTTED_UP,
        CENTER_FURTHER,
        CLOSER,
        CLOSER_IN_FOCUS,
        CLOSER_OVERLAP,
        CLOSER_TOP,
        FOCUSABLE,
        FURTHER,
        IN_UMBRA,
        IN_WORKING,
        LEFTMOST,
        OVERLAP_OR_EDGE_FURTHER,
        PREFERRED, // better overlap measure
        SECOND_CHANCE_END = PREFERRED, // must be last in list
        // rejections that don't get a second chance
        ANCHOR_IN_ANCHOR,
        BEST_DIRECTION, // can be reached by another direction
        CHILD,
        DISABLED,
        IN_FOCUS,
        IN_FOCUS_CHILDREN,
        NOT_ENCLOSING_FOCUS,
    //    NOT_FOCUS_CHILD,
        NOT_FOCUS_NODE,
        OUTSIDE_OF_BEST, // containership
        OUTSIDE_OF_ORIGINAL, // containership
        CONDITION_SIZE // FIXME: test that CONDITION_SIZE fits in mCondition
    };
    CachedNode() {}
    bool anchorHasSrcUrl() const { return mAnchorHasSrcUrl; }
    const WebCore::IntRect& bounds() const { return mBounds; }
    WebCore::IntRect* boundsPtr() { return &mBounds; }
    int childFrameIndex() const { return mChildFrameIndex; }
    void clearCondition() const { mCondition = NOT_REJECTED; }
    void clearFocus(CachedFrame* );
    static bool Clip(const WebCore::IntRect& outer, WebCore::IntRect* inner,
        WTF::Vector<WebCore::IntRect>* rings);
    bool clip(const WebCore::IntRect& );
    bool clippedOut() { return mClippedOut; }
    bool disabled() const { return mDisabled; }
    const CachedNode* document() const { return &this[-mIndex]; }
    void fixUpFocusRects();
    void focusRingBounds(WebCore::IntRect* ) const;
    WTF::Vector<WebCore::IntRect>& focusRings() { return mFocusRing; }
    const WTF::Vector<WebCore::IntRect>& focusRings() const { return mFocusRing; }
    const WebCore::IntRect& getBounds() const { return mBounds; }
    void getBounds(WebCore::IntRect* bounds) const { *bounds = mBounds; }
    const WebCore::String& getExport() const { return mExport; }
    bool hasFocusRing() const { return mHasFocusRing; }
    bool hasMouseOver() const { return mHasMouseOver; }
    const WebCore::IntRect& hitBounds() const { return mHitBounds; }
    int index() const { return mIndex; }
    void init(CachedFrame* , WebCore::Node* node);
    bool isAnchor() const { return mIsAnchor; }
    bool isArea() const { return mIsArea; }
    bool isFocus() const { return mIsFocus; }
    bool isFocusable(const WebCore::IntRect& clip) const {
        return clip.intersects(mBounds);
    }
    bool isFrame() const { return mChildFrameIndex >= 0 ; }
    bool isInput() const { return mIsInput; }
    bool isPassword() const { return mIsPassword; }
    bool isRtlText() const { return mIsRtlText; }
    bool isTextArea() const { return mIsTextArea; }
    bool isTextField() const { return mIsTextField; }
    bool isTransparent() const { return mIsTransparent; }
    bool isUnclipped() const { return mIsUnclipped; }
    int maxLength() const { return mMaxLength; };
    void move(int x, int y);
    const WebCore::String& name() const { return mName; }
    int navableRects() const { return mNavableRects; }
    void* nodePointer() const { return mNode; }
    bool noSecondChance() const { return mCondition > SECOND_CHANCE_END; }
    const CachedNode* parent() const { return document() + mParentIndex; }
    void* parentGroup() const { return mParentGroup; }
    int parentIndex() const { return mParentIndex; }
    bool partRectsContains(const CachedNode* other) const;
    void reset();
    void setAnchorHasSrcUrl(bool isURL) { mAnchorHasSrcUrl = isURL; }
    void setBounds(const WebCore::IntRect& bounds) { mBounds = bounds; }
    void setChildFrameIndex(int index) { mChildFrameIndex = index;  }
    void setClippedOut(bool clipped) { mClippedOut = clipped; }
    void setCondition(Condition condition) const { mCondition = condition; }
    void setDisabled(bool disabled) { mDisabled = disabled; }
    void setExport(const WebCore::String& exported) { mExport = exported; }
    void setHasFocusRing(bool hasFocusRing) { mHasFocusRing = hasFocusRing; }
    void setHasMouseOver(bool hasMouseOver) { mHasMouseOver = hasMouseOver; }
    void setHitBounds(const WebCore::IntRect& bounds) { mHitBounds = bounds; }
    void setIndex(int index) { mIndex = index; }
    void setIsAnchor(bool isAnchor) { mIsAnchor = isAnchor; }
    void setIsArea(bool isArea) { mIsArea = isArea; }
    void setIsFocus(bool isFocus) { mIsFocus = isFocus; }
    void setIsInput(bool isInput) { mIsInput = isInput; }
    void setIsParentAnchor(bool isAnchor) { mIsParentAnchor = isAnchor; }
    void setIsPassword(bool isPassword) { mIsPassword = isPassword; }
    void setIsRtlText(bool isRtlText) { mIsRtlText = isRtlText; }
    void setIsTextArea(bool isTextArea) { mIsTextArea = isTextArea; }
    void setIsTextField(bool isTextField) { mIsTextField = isTextField; }
    void setIsTransparent(bool isTransparent) { mIsTransparent = isTransparent; }
    void setIsUnclipped(bool unclipped) { mIsUnclipped = unclipped; }
    void setLast() { mLast = true; }
    void setMaxLength(int maxLength) { mMaxLength = maxLength; }
    void setName(const WebCore::String& name) { mName = name; }
    void setNavableRects() { mNavableRects = mFocusRing.size(); }
    void setParentGroup(void* group) { mParentGroup = group; }
    void setParentIndex(int parent) { mParentIndex = parent; }
    void setTextSize(int textSize) { mTextSize = textSize; }
    void setType(CachedNodeType type) { mType = type; }
    const CachedNode* traverseNextNode() const { return mLast ? NULL : &this[1]; }
    int textSize() const { return mTextSize; }
    CachedNodeType type() const { return mType; }
private:
    WebCore::String mExport;
    WebCore::String mName;
    WebCore::IntRect mBounds;
    WebCore::IntRect mHitBounds;
    WTF::Vector<WebCore::IntRect> mFocusRing;
    void* mNode; // WebCore::Node*, only used to match pointers
    void* mParentGroup; // WebCore::Node*, only used to match pointers
    int mChildFrameIndex; // set to -1 if node is not a frame
    int mIndex; // index of itself, to find first in array (document)
    int mMaxLength;
    int mNavableRects; // FIXME: could be bitfield once I limit max number of rects
    int mParentIndex;
    int mTextSize;
    mutable Condition mCondition : 5; // why the node was not chosen on the first pass
    CachedNodeType mType : 3;
    bool mAnchorHasSrcUrl : 1;
    bool mClippedOut : 1;
    bool mDisabled : 1;
    bool mFixedUpFocusRects : 1;
    bool mHasFocusRing : 1;
    bool mHasMouseOver : 1;
    bool mIsAnchor : 1;
    bool mIsArea : 1;
    bool mIsFocus : 1;
    bool mIsInput : 1;
    bool mIsParentAnchor : 1;
    bool mIsPassword : 1;
    bool mIsRtlText : 1;
    bool mIsTextArea : 1;
    bool mIsTextField : 1;
    bool mIsTransparent : 1;
    bool mIsUnclipped : 1;
    bool mLast : 1;
#ifdef BROWSER_DEBUG
public:
    WebCore::Node* webCoreNode() const { return (WebCore::Node*) mNode; }
    bool mDisplayMeasure;
    mutable bool mInCompare;
 //   mutable int mCondition;
    int mSideDistance;
    int mSecondSide;
#endif    
#if DEBUG_NAV_UI || DUMP_NAV_CACHE
public:
    class Debug {
public:
        CachedNode* base() const;
        const char* condition(Condition t) const;
        void print() const;
        const char* type(CachedNodeType t) const;
#if DUMP_NAV_CACHE
        int mNodeIndex;
        int mParentGroupIndex;
#endif
    } mDebug;
    friend class CachedNode::Debug;
#endif
};

}

#endif
