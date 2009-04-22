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

#include "CachedPrefix.h"
#include "CachedHistory.h"
#include "CachedNode.h"
#include "SkBitmap.h"
#include "SkBounder.h"
#include "SkCanvas.h"
#include "SkRegion.h"

#include "CachedRoot.h"

#ifdef DUMP_NAV_CACHE_USING_PRINTF
    extern android::Mutex gWriteLogMutex;
#endif

namespace android {

class CommonCheck : public SkBounder {
public:
    enum Type {
        kNo_Type,
        kDrawBitmap_Type,
        kDrawGlyph_Type,
        kDrawPaint_Type,
        kDrawPath_Type,
        kDrawPicture_Type,
        kDrawPoints_Type,
        kDrawPosText_Type,
        kDrawPosTextH_Type,
        kDrawRect_Type,
        kDrawSprite_Type,
        kDrawText_Type,
        kDrawTextOnPath_Type
    };
    
    static bool isTextType(Type t) {
        return t == kDrawPosTextH_Type || t == kDrawText_Type;
    }
    
    CommonCheck() : mType(kNo_Type), mAllOpaque(true), mIsOpaque(true) {
        setEmpty();
    }

    bool doRect(Type type) { 
        mType = type;
        return doIRect(mUnion); 
    }

    bool joinGlyphs(const SkIRect& rect) {
        bool isGlyph = mType == kDrawGlyph_Type;
        if (isGlyph)
            mUnion.join(rect);
        return isGlyph;
    }
    
    void setAllOpaque(bool opaque) { mAllOpaque = opaque; }
    void setEmpty() { mUnion.setEmpty(); }
    void setIsOpaque(bool opaque) { mIsOpaque = opaque; }
    void setType(Type type) { mType = type; }

    Type mType;
    SkIRect mUnion;
    bool mAllOpaque;
    bool mIsOpaque;
};

#if DEBUG_NAV_UI && !defined BROWSER_DEBUG
    static const char* TypeNames[] = {
        "kNo_Type",
        "kDrawBitmap_Type",
        "kDrawGlyph_Type",
        "kDrawPaint_Type",
        "kDrawPath_Type",
        "kDrawPicture_Type",
        "kDrawPoints_Type",
        "kDrawPosText_Type",
        "kDrawPosTextH_Type",
        "kDrawRect_Type",
        "kDrawSprite_Type",
        "kDrawText_Type",
        "kDrawTextOnPath_Type"
    };
#endif

#define kMargin 16
#define kSlop 2

class BoundsCheck : public CommonCheck {
public:
    BoundsCheck() { 
        mAllDrawnIn.setEmpty();
        mLastAll.setEmpty();
        mLastOver.setEmpty();
    }
    
    static int Area(SkIRect test) {
        return test.width() * test.height();
    }
    
   void checkLast() {
        if (mAllDrawnIn.isEmpty())
            return;
        if (mLastAll.isEmpty() || Area(mLastAll) < Area(mAllDrawnIn)) {
            mLastAll = mAllDrawnIn;
            mDrawnOver.setEmpty();
        }
        mAllDrawnIn.setEmpty();
    }
    
    bool hidden() {
        return (mLastAll.isEmpty() && mLastOver.isEmpty()) ||
            mDrawnOver.contains(mBounds);
    }
    
    virtual bool onIRect(const SkIRect& rect) {
        if (joinGlyphs(rect))
            return false;
        bool interestingType = mType == kDrawBitmap_Type || 
            mType == kDrawRect_Type || isTextType(mType);
        if (SkIRect::Intersects(mBounds, rect) == false) {
#if DEBUG_NAV_UI && !defined BROWSER_DEBUG
        LOGD("%s (no intersect) rect={%d,%d,%d,%d} mType=%s\n", __FUNCTION__,
            rect.fLeft, rect.fTop, rect.fRight, rect.fBottom,
            TypeNames[mType]);
#endif
            if (interestingType)
                checkLast();
            return false;
        }
        if (interestingType == false)
            return false;
        if (mBoundsSlop.contains(rect) || 
                (mBounds.fLeft == rect.fLeft && mBounds.fRight == rect.fRight &&
                mBounds.fTop >= rect.fTop && mBounds.fBottom <= rect.fBottom) ||
                (mBounds.fTop == rect.fTop && mBounds.fBottom == rect.fBottom &&
                mBounds.fLeft >= rect.fLeft && mBounds.fRight <= rect.fRight)) {
            mDrawnOver.setEmpty();
            mAllDrawnIn.join(rect);
#if DEBUG_NAV_UI && !defined BROWSER_DEBUG
        LOGD("%s (contains) rect={%d,%d,%d,%d}"
            " mAllDrawnIn={%d,%d,%d,%d} mType=%s\n", __FUNCTION__,
            rect.fLeft, rect.fTop, rect.fRight, rect.fBottom,
            mAllDrawnIn.fLeft, mAllDrawnIn.fTop, mAllDrawnIn.fRight, mAllDrawnIn.fBottom,
            TypeNames[mType]);
#endif
       } else {
            checkLast();
            if (!isTextType(mType)) {
                if (
#if 0
// should the opaqueness of the bitmap disallow its ability to draw over?
// not sure that this test is needed
                (mType != kDrawBitmap_Type ||
                        (mIsOpaque && mAllOpaque)) && 
#endif                        
                        mLastAll.isEmpty() == false)
                    mDrawnOver.op(rect, SkRegion::kUnion_Op);
            } else {
// FIXME
// sometimes the text is not drawn entirely inside the focus area, even though
// it is the correct text. Until I figure out why, I allow text drawn at the
// end that is not covered up by something else to represent the focusable link
// example that triggers this that should be figured out:
// http://cdn.labpixies.com/campaigns/blackjack/blackjack.html?lang=en&country=US&libs=assets/feature/core
// ( http://tinyurl.com/ywsyzb )
                mLastOver = rect;
            }
#if DEBUG_NAV_UI && !defined BROWSER_DEBUG
        const SkIRect& drawnOver = mDrawnOver.getBounds();
        LOGD("%s (overlaps) rect={%d,%d,%d,%d}"
            " mDrawnOver={%d,%d,%d,%d} mType=%s mIsOpaque=%s mAllOpaque=%s\n", __FUNCTION__,
            rect.fLeft, rect.fTop, rect.fRight, rect.fBottom,
            drawnOver.fLeft, drawnOver.fTop, drawnOver.fRight, drawnOver.fBottom,
            TypeNames[mType], mIsOpaque ? "true" : "false", mAllOpaque ? "true" : "false");
#endif
        }
        return false;
    }
    
    SkIRect mBounds;
    SkIRect mBoundsSlop;
    SkRegion mDrawnOver;
    SkIRect mLastOver;
    SkIRect mAllDrawnIn;
    SkIRect mLastAll;
};

class BoundsCanvas : public SkCanvas {
public:

    BoundsCanvas(CommonCheck* bounder) : mBounder(*bounder) {
        mTransparentLayer = 0;
        setBounder(bounder);
    }

    virtual ~BoundsCanvas() {
        setBounder(NULL);
    }

    virtual void drawPaint(const SkPaint& paint) {
        mBounder.setType(CommonCheck::kDrawPaint_Type);
        SkCanvas::drawPaint(paint);
    }

    virtual void drawPoints(PointMode mode, size_t count, const SkPoint pts[],
                            const SkPaint& paint) {
        mBounder.setType(CommonCheck::kDrawPoints_Type);
        SkCanvas::drawPoints(mode, count, pts, paint);
    }

    virtual void drawRect(const SkRect& rect, const SkPaint& paint) {
        mBounder.setType(CommonCheck::kDrawRect_Type);
        SkCanvas::drawRect(rect, paint);
    }

    virtual void drawPath(const SkPath& path, const SkPaint& paint) {
        mBounder.setType(CommonCheck::kDrawPath_Type);
        SkCanvas::drawPath(path, paint);
    }

    virtual void commonDrawBitmap(const SkBitmap& bitmap,
                              const SkMatrix& matrix, const SkPaint& paint) {
        mBounder.setType(CommonCheck::kDrawBitmap_Type);
        mBounder.setIsOpaque(bitmap.isOpaque());
        SkCanvas::commonDrawBitmap(bitmap, matrix, paint);
    }

    virtual void drawSprite(const SkBitmap& bitmap, int left, int top,
                            const SkPaint* paint = NULL) {
        mBounder.setType(CommonCheck::kDrawSprite_Type);
        mBounder.setIsOpaque(bitmap.isOpaque());
        SkCanvas::drawSprite(bitmap, left, top, paint);
    }

    virtual void drawText(const void* text, size_t byteLength, SkScalar x, 
                          SkScalar y, const SkPaint& paint) {
        mBounder.setEmpty();
        mBounder.setType(CommonCheck::kDrawGlyph_Type);
        SkCanvas::drawText(text, byteLength, x, y, paint);
        mBounder.doRect(CommonCheck::kDrawText_Type);
    }

    virtual void drawPosText(const void* text, size_t byteLength, 
                             const SkPoint pos[], const SkPaint& paint) {
        mBounder.setEmpty();
        mBounder.setType(CommonCheck::kDrawGlyph_Type);
        SkCanvas::drawPosText(text, byteLength, pos, paint);
        mBounder.doRect(CommonCheck::kDrawPosText_Type);
    }

    virtual void drawPosTextH(const void* text, size_t byteLength,
                              const SkScalar xpos[], SkScalar constY,
                              const SkPaint& paint) {
        mBounder.setEmpty();
        mBounder.setType(CommonCheck::kDrawGlyph_Type);
        SkCanvas::drawPosTextH(text, byteLength, xpos, constY, paint);
        if (mBounder.mUnion.isEmpty())
            return;
        SkPaint::FontMetrics metrics;
        paint.getFontMetrics(&metrics);
        SkPoint upDown[2] = { {xpos[0], constY + metrics.fAscent},
            {xpos[0], constY + metrics.fDescent} };
        const SkMatrix& matrix = getTotalMatrix();
        matrix.mapPoints(upDown, 2);
        if (upDown[0].fX == upDown[1].fX) {
            mBounder.mUnion.fTop = SkScalarFloor(upDown[0].fY);
            mBounder.mUnion.fBottom = SkScalarFloor(upDown[1].fY);
        }
        mBounder.doRect(CommonCheck::kDrawPosTextH_Type);
    }

    virtual void drawTextOnPath(const void* text, size_t byteLength, 
                                const SkPath& path, const SkMatrix* matrix, 
                                const SkPaint& paint) {
        mBounder.setEmpty();
        mBounder.setType(CommonCheck::kDrawGlyph_Type);
        SkCanvas::drawTextOnPath(text, byteLength, path, matrix, paint);
        mBounder.doRect(CommonCheck::kDrawTextOnPath_Type);
    }

    virtual void drawPicture(SkPicture& picture) {
        mBounder.setType(CommonCheck::kDrawPicture_Type);
        SkCanvas::drawPicture(picture);
    }
    
    virtual int saveLayer(const SkRect* bounds, const SkPaint* paint,
                          SaveFlags flags) {
        int depth = SkCanvas::saveLayer(bounds, paint, flags);
        if (mTransparentLayer == 0 && paint && paint->getAlpha() < 255) {
            mTransparentLayer = depth;
            mBounder.setAllOpaque(false);
        }
        return depth;
    }

    virtual void restore() {
        int depth = getSaveCount();
        if (depth == mTransparentLayer) {
            mTransparentLayer = 0;
            mBounder.setAllOpaque(true);
        }
        SkCanvas::restore();
    }
    
    int mTransparentLayer;
    CommonCheck& mBounder;
};

/*
CenterCheck examines the text in a picture, within a viewable rectangle,
and returns via center() the optimal amount to scroll in x to display the
paragraph of text.

The caller of CenterCheck has configured (but not allocated) a bitmap
the height and three times the width of the view. The picture is drawn centered 
in the bitmap, so text that would be revealed, if the view was scrolled up to 
a view-width to the left or right, is considered.
*/
class CenterCheck : public CommonCheck {
public:
    CenterCheck(int x, int y, int width) : mX(x), mY(y), 
            mHitLeft(x), mHitRight(x), mMostLeft(INT_MAX), mMostRight(-INT_MAX),
            mViewLeft(width), mViewRight(width << 1) {
        mHit.set(x - CENTER_SLOP, y - CENTER_SLOP, 
            x + CENTER_SLOP, y + CENTER_SLOP);
        mPartial.setEmpty();
    }
    
    int center() {
        doRect(); // process the final line of text
        /* If the touch coordinates aren't near any text, return 0 */
        if (mHitLeft == mHitRight) {
            DBG_NAV_LOGD("abort: mHitLeft=%d ==mHitRight", mHitLeft);
            return 0;
        }
        int leftOver = mHitLeft - mViewLeft;
        int rightOver = mHitRight - mViewRight;
        int center;
        /* If the touched text is too large to entirely fit on the screen,
           center it. */
        if (leftOver < 0 && rightOver > 0) {
            center = (leftOver + rightOver) >> 1;
            DBG_NAV_LOGD("overlap: leftOver=%d rightOver=%d center=%d",
                leftOver, rightOver, center);
            return center;
        }
        center = (mMostLeft + mMostRight) >> 1; // the paragraph center
        if (leftOver > 0 && rightOver >= 0) { // off to the right
            if (center > mMostLeft) // move to center loses left-most text?
                center = mMostLeft;
        } else if (rightOver < 0 && leftOver <= 0) { // off to the left
            if (center < mMostRight) // move to center loses right-most text?
                center = mMostRight;
        } else {
#ifdef DONT_CENTER_IF_ALREADY_VISIBLE
            center = 0; // paragraph is already fully visible
#endif
        }
        DBG_NAV_LOGD("scroll: leftOver=%d rightOver=%d center=%d", 
            leftOver, rightOver, center);
        return center;
    }
    
protected:    
    virtual bool onIRect(const SkIRect& rect) {
        if (joinGlyphs(rect)) // assembles glyphs into a text string
            return false;
        if (!isTextType(mType))
            return false;
        /* Text on one line may be broken into several parts. Reassemble
           the text into a rectangle before considering it. */
        if (rect.fTop < mPartial.fBottom && rect.fBottom > 
                mPartial.fTop && mPartial.fRight + CENTER_SLOP >= rect.fLeft) {
            DBG_NAV_LOGD("join mPartial=(%d, %d, %d, %d) rect=(%d, %d, %d, %d)", 
                mPartial.fLeft, mPartial.fTop, mPartial.fRight, mPartial.fBottom,
                rect.fLeft, rect.fTop, rect.fRight, rect.fBottom);
            mPartial.join(rect);
            return false;
        }
        if (mPartial.isEmpty() == false)
            doRect(); // process the previous line of text
        mPartial = rect;
        return false;
    }    
    
    void doRect()
    {
        /* Record the outer bounds of the lines of text that was 'hit' by the 
           touch coordinates, given some slop */
        if (SkIRect::Intersects(mPartial, mHit)) {
            if (mHitLeft > mPartial.fLeft)
                mHitLeft = mPartial.fLeft;
            if (mHitRight < mPartial.fRight)
                mHitRight = mPartial.fRight;
            DBG_NAV_LOGD("mHitLeft=%d mHitRight=%d", mHitLeft, mHitRight);
        }
        /* If the considered text is completely to the left or right of the
           touch coordinates, skip it */
        if (mPartial.fLeft > mX || mPartial.fRight < mX)
            return;
        int leftOver = mPartial.fLeft - mViewLeft;
        int rightOver = mPartial.fRight - mViewRight;
        /* If leftOver <= 0, the text starts off the screen.
           If rightOver >= 0, the text ends off the screen.
        */
        if (leftOver <= 0 && rightOver >= 0) // discard wider than screen
            return;
#ifdef DONT_CENTER_IF_ALREADY_VISIBLE
        if (leftOver > 0 && rightOver < 0)   // discard already visible
            return;
#endif
        /* record the smallest margins on the left and right */
        if (mMostLeft > leftOver)
            mMostLeft = leftOver;
        if (mMostRight < rightOver)
            mMostRight = rightOver;
        DBG_NAV_LOGD("leftOver=%d rightOver=%d mMostLeft=%d mMostRight=%d", 
            leftOver, rightOver, mMostLeft, mMostRight);
    }
    
    static const int CENTER_SLOP = 10; // space between text parts and lines
    /* const */ SkIRect mHit; // sloppy hit rectangle
    SkIRect mPartial; // accumulated text bounds, per line
    const int mX; // touch location
    const int mY;
    int mHitLeft; // touched text extremes
    int mHitRight;
    int mMostLeft; // paragraph extremes
    int mMostRight;
    const int mViewLeft; // middle third of 3x-wide view
    const int mViewRight;
};

class ImageCheck : public CommonCheck {
public:
    ImageCheck() : mLastIsImage(false) {}
    
    virtual bool onIRect(const SkIRect& rect) {
        if (joinGlyphs(rect))
            return false;
        mLastIsImage = mType == kDrawBitmap_Type;
        return false;
    }
    
    bool mLastIsImage;
};

class JiggleCheck : public CommonCheck {
public:
    JiggleCheck(int delta, int width) : mDelta(delta), mMaxX(width) {
        mMaxJiggle = 0;
        mMinX = mMinJiggle = abs(delta);
        mMaxWidth = width + mMinX;
    }
    
    int jiggle() {
        if (mMinJiggle > mMaxJiggle)
            return mDelta;
        int avg = (mMinJiggle + mMaxJiggle + 1) >> 1;
        return mDelta < 0 ? -avg : avg;
    }
    
    virtual bool onIRect(const SkIRect& rect) {
        if (joinGlyphs(rect))
            return false;
        if (mType != kDrawBitmap_Type && !isTextType(mType))
            return false;
        int min, max;
        if (mDelta < 0) {
            min = mMinX - rect.fLeft;
            max = mMaxWidth - rect.fRight;
        } else {
            min = rect.fRight - mMaxX;
            max = rect.fLeft;
        }
        if (min <= 0)
            return false;
        if (max >= mMinX)
            return false;
        if (mMinJiggle > min)
            mMinJiggle = min;
        if (mMaxJiggle < max)
            mMaxJiggle = max;
        return false;
    }

    int mDelta;
    int mMaxJiggle;
    int mMaxX;
    int mMinJiggle;
    int mMinX;
    int mMaxWidth;
};

bool CachedRoot::adjustForScroll(BestData* best, CachedFrame::Direction direction, 
    WebCore::IntPoint* scrollPtr, bool findClosest)
{        
    WebCore::IntRect newOutset;
    const CachedNode* newNode = best->mNode;
    // see if there's a middle node
        // if the middle node is in the visited list, 
        // or if none was computed and the newNode is in the visited list, 
        // treat result as NULL
    if (newNode != NULL && findClosest) {
        if (best->bounds().intersects(mHistory->mPriorBounds) == false &&
                checkBetween(best, direction))
            newNode = best->mNode;
        if (findClosest && maskIfHidden(best)) {
            innerMove(document(), best, direction, scrollPtr, false);
            return true;
        }
        newNode->focusRingBounds(&newOutset);
    }
    int delta;
    bool newNodeInView = scrollDelta(newOutset, direction, &delta);
    if (delta && scrollPtr && (newNode == NULL || newNodeInView == false || 
            (best->mNavOutside && best->mWorkingOutside)))
        *scrollPtr = WebCore::IntPoint(direction & UP_DOWN ? 0 : delta, 
            direction & UP_DOWN ? delta : 0);
    return false;
}


int CachedRoot::checkForCenter(int x, int y) const
{
    int width = mViewBounds.width();
    CenterCheck centerCheck(x + width - mViewBounds.x(), y - mViewBounds.y(), 
        width);
    BoundsCanvas checker(&centerCheck);
    SkBitmap bitmap;
    bitmap.setConfig(SkBitmap::kARGB_8888_Config, width * 3,
        mViewBounds.height());
    checker.setBitmapDevice(bitmap);
    checker.translate(SkIntToScalar(width - mViewBounds.x()), 
        SkIntToScalar(-mViewBounds.y()));
    checker.drawPicture(*mPicture);
    return centerCheck.center();
}

void CachedRoot::checkForJiggle(int* xDeltaPtr) const
{
    int xDelta = *xDeltaPtr;
    JiggleCheck jiggleCheck(xDelta, mViewBounds.width());
    BoundsCanvas checker(&jiggleCheck);
    SkBitmap bitmap;
    int absDelta = abs(xDelta);
    bitmap.setConfig(SkBitmap::kARGB_8888_Config, mViewBounds.width() +
        absDelta, mViewBounds.height());
    checker.setBitmapDevice(bitmap);
    checker.translate(SkIntToScalar(-mViewBounds.x() -  
        (xDelta < 0 ? xDelta : 0)), SkIntToScalar(-mViewBounds.y()));
    checker.drawPicture(*mPicture);
    *xDeltaPtr = jiggleCheck.jiggle();
}

const CachedNode* CachedRoot::findAt(const WebCore::IntRect& rect,
    const CachedFrame** framePtr, int* x, int* y) const
{
    int best = INT_MAX;
    (const_cast<CachedRoot*>(this))->resetClippedOut();
    const CachedNode* directHit = NULL;
    const CachedNode* node = findBestAt(rect, &best, &directHit, framePtr, x, y);
    DBG_NAV_LOGD("node=%d (%p)", node == NULL ? 0 : node->index(), 
        node == NULL ? NULL : node->nodePointer());
    if (node == NULL) {
        node = findBestHitAt(rect, &best, framePtr, x, y);
        DBG_NAV_LOGD("node=%d (%p)", node == NULL ? 0 : node->index(), 
            node == NULL ? NULL : node->nodePointer());
    }
    if (node == NULL) {
        *framePtr = findBestFrameAt(rect.x() + (rect.width() >> 1),
            rect.y() + (rect.height() >> 1));
    }
    return node;
}

WebCore::IntPoint CachedRoot::focusLocation() const
{
    const WebCore::IntRect& bounds = mHistory->mNavBounds;
    return WebCore::IntPoint(bounds.x() + (bounds.width() >> 1), 
        bounds.y() + (bounds.height() >> 1));
}

// These reset the values because we only want to get the selection the first time.
// After that, the selection is no longer accurate.
int CachedRoot::getAndResetSelectionEnd()
{
    int end = mSelectionEnd;
    mSelectionEnd = -1;
    return end;
}

int CachedRoot::getAndResetSelectionStart()
{
    int start = mSelectionStart;
    mSelectionStart = -1;
    return start;
}

void CachedRoot::getSimulatedMousePosition(WebCore::IntPoint* point)
{
#ifndef NDEBUG
    ASSERT(CachedFrame::mDebug.mInUse);
#endif
    const WebCore::IntRect& mouseBounds = mHistory->mMouseBounds;
    point->setX(mouseBounds.x() + (mouseBounds.width() >> 1));
    point->setY(mouseBounds.y() + (mouseBounds.height() >> 1));
#if DEBUG_NAV_UI && !defined BROWSER_DEBUG
    const WebCore::IntRect& navBounds = mHistory->mNavBounds;
    LOGD("%s mHistory->mNavBounds={%d,%d,%d,%d} "
        "mHistory->mMouseBounds={%d,%d,%d,%d} point={%d,%d}\n", __FUNCTION__,
        navBounds.x(), navBounds.y(), navBounds.width(), navBounds.height(),
        mouseBounds.x(), mouseBounds.y(), mouseBounds.width(), mouseBounds.height(),
        point->x(), point->y());
#endif
}

void CachedRoot::init(WebCore::FrameAndroid* frame, CachedHistory* history) 
{
    CachedFrame::init(this, -1, frame);
    reset();
    mHistory = history;
    mPicture = NULL;
}

bool CachedRoot::innerDown(const CachedNode* test, BestData* bestData) const
{
    ASSERT(minWorkingVertical() >= mViewBounds.x());
    ASSERT(maxWorkingVertical() <= mViewBounds.right());
    setupScrolledBounds();
    // (line up)
    mScrolledBounds.setHeight(mScrolledBounds.height() + mMaxYScroll);
    int testTop = mScrolledBounds.y();
    int viewBottom = mViewBounds.bottom();
    if (mHistory->mFocusBounds.isEmpty() == false &&
            mHistory->mFocusBounds.bottom() > viewBottom && viewBottom < mContents.height())
        return false;
    if (mHistory->mNavBounds.isEmpty() == false) {
        int navTop = mHistory->mNavBounds.y();
        int scrollBottom;
        if (testTop < navTop && navTop < (scrollBottom = mScrolledBounds.bottom())) {
            mScrolledBounds.setHeight(scrollBottom - navTop);
            mScrolledBounds.setY(navTop);
        }
    }
    frameDown(test, NULL, bestData, currentFocus());
    return true;
}

bool CachedRoot::innerLeft(const CachedNode* test, BestData* bestData) const
{
    ASSERT(minWorkingHorizontal() >= mViewBounds.y());
    ASSERT(maxWorkingHorizontal() <= mViewBounds.bottom());
    setupScrolledBounds();
    mScrolledBounds.setX(mScrolledBounds.x() - mMaxXScroll);
    mScrolledBounds.setWidth(mScrolledBounds.width() + mMaxXScroll);
    int testRight = mScrolledBounds.right();
    int viewLeft = mViewBounds.x();
    if (mHistory->mFocusBounds.isEmpty() == false &&
            mHistory->mFocusBounds.x() < viewLeft && viewLeft > mContents.x())
        return false;
    if (mHistory->mNavBounds.isEmpty() == false) {
        int navRight = mHistory->mNavBounds.right();
        int scrollLeft;
        if (testRight > navRight && navRight > (scrollLeft = mScrolledBounds.x()))
            mScrolledBounds.setWidth(navRight - scrollLeft);
    }
    frameLeft(test, NULL, bestData, currentFocus());
    return true;
}


void CachedRoot::innerMove(const CachedNode* node, BestData* bestData, 
    Direction direction, WebCore::IntPoint* scroll, bool firstCall)
{
    bestData->reset();
    mFocusChild = false;
    bool outOfFocus = mFocus < 0;
    bool firstTime = mHistory->didFirstLayout() && outOfFocus;
#if DEBUG_NAV_UI && !defined BROWSER_DEBUG
    LOGD("%s mHistory->didFirstLayout()=%s && mFocus=%d\n", __FUNCTION__,
        mHistory->didFirstLayout() ? "true" : "false", mFocus);
#endif
    if (firstTime)
        mHistory->reset();
    mHistory->setWorking(direction, currentFocus(), mViewBounds);
    bool findClosest = false;
    if (mScrollOnly == false) {
        switch (direction) {
            case LEFT:
                if (outOfFocus)
                    mHistory->mNavBounds = WebCore::IntRect(mViewBounds.right(), 
                        mViewBounds.y(), 1, mViewBounds.height());
                findClosest = innerLeft(node, bestData);
                break;
            case RIGHT: 
                if (outOfFocus)
                    mHistory->mNavBounds = WebCore::IntRect(mViewBounds.x() - 1,
                        mViewBounds.y(), 1, mViewBounds.height());
                findClosest = innerRight(node, bestData);
                break;
            case UP:
                if (outOfFocus)
                    mHistory->mNavBounds = WebCore::IntRect(mViewBounds.x(), 
                        mViewBounds.bottom(), mViewBounds.width(), 1);
                findClosest = innerUp(node, bestData);
                break;
            case DOWN:
                if (outOfFocus)
                    mHistory->mNavBounds = WebCore::IntRect(mViewBounds.x(), 
                        mViewBounds.y() - 1, mViewBounds.width(), 1);
                findClosest = innerDown(node, bestData);
                break;
            case UNINITIALIZED:
            default:
                ASSERT(0);
        }
    }
    if (firstCall)
        mHistory->mPriorBounds = mHistory->mNavBounds; // bounds always advances, even if new node is ultimately NULL
    bestData->mMouseBounds = bestData->mNodeBounds;
    if (adjustForScroll(bestData, direction, scroll, findClosest))
        return;
    if (bestData->mNode != NULL) {
        mHistory->addToVisited(bestData->mNode, direction);
        mHistory->mNavBounds = mHistory->mFocusBounds = bestData->mNodeBounds;
        mHistory->mMouseBounds = bestData->mMouseBounds;
    } else if (scroll->x() != 0 || scroll->y() != 0) {
        WebCore::IntRect newBounds = mHistory->mNavBounds;
        int offsetX = scroll->x();
        int offsetY = scroll->y();
        newBounds.move(offsetX, offsetY);
        if (mViewBounds.x() > newBounds.x())
            offsetX = mViewBounds.x() - mHistory->mNavBounds.x();
        else if (mViewBounds.right() < newBounds.right())
            offsetX = mViewBounds.right() - mHistory->mNavBounds.right();
        if (mViewBounds.y() > newBounds.y())
            offsetY = mViewBounds.y() - mHistory->mNavBounds.y();
        else if (mViewBounds.bottom() < newBounds.bottom())
            offsetY = mViewBounds.bottom() - mHistory->mNavBounds.bottom();
        mHistory->mNavBounds.move(offsetX, offsetY);
    }
    mHistory->setDidFirstLayout(false);
}

bool CachedRoot::innerRight(const CachedNode* test, BestData* bestData) const
{
    ASSERT(minWorkingHorizontal() >= mViewBounds.y());
    ASSERT(maxWorkingHorizontal() <= mViewBounds.bottom());
    setupScrolledBounds();
    // (align)
    mScrolledBounds.setWidth(mScrolledBounds.width() + mMaxXScroll);
    int testLeft = mScrolledBounds.x();
    int viewRight = mViewBounds.right();
    if (mHistory->mFocusBounds.isEmpty() == false &&
            mHistory->mFocusBounds.right() > viewRight && viewRight < mContents.width())
        return false;
    if (mHistory->mNavBounds.isEmpty() == false) {
        int navLeft = mHistory->mNavBounds.x();
        int scrollRight;
        if (testLeft < navLeft && navLeft < (scrollRight = mScrolledBounds.right())) {
            mScrolledBounds.setWidth(scrollRight - navLeft);
            mScrolledBounds.setX(navLeft);
        }
    }
    frameRight(test, NULL, bestData, currentFocus());
    return true;
}

bool CachedRoot::innerUp(const CachedNode* test, BestData* bestData) const
{
    ASSERT(minWorkingVertical() >= mViewBounds.x());
    ASSERT(maxWorkingVertical() <= mViewBounds.right());
    setupScrolledBounds();
    mScrolledBounds.setY(mScrolledBounds.y() - mMaxYScroll);
    mScrolledBounds.setHeight(mScrolledBounds.height() + mMaxYScroll);
    int testBottom = mScrolledBounds.bottom();
    int viewTop = mViewBounds.y();
    if (mHistory->mFocusBounds.isEmpty() == false &&
            mHistory->mFocusBounds.y() < viewTop && viewTop > mContents.y())
        return false;
    if (mHistory->mNavBounds.isEmpty() == false) {
        int navBottom = mHistory->mNavBounds.bottom();
        int scrollTop;
        if (testBottom > navBottom && navBottom > (scrollTop = mScrolledBounds.y()))
            mScrolledBounds.setHeight(navBottom - scrollTop);
    }
    frameUp(test, NULL, bestData, currentFocus());
    return true;
}

bool CachedRoot::isImage(int x, int y) const
{
    ImageCheck imageCheck;
    BoundsCanvas checker(&imageCheck);
    SkBitmap bitmap;
    bitmap.setConfig(SkBitmap::kARGB_8888_Config, 1, 1);
    checker.setBitmapDevice(bitmap);
    checker.translate(SkIntToScalar(-x), SkIntToScalar(-y));
    checker.drawPicture(*mPicture);
    return imageCheck.mLastIsImage;
}

bool CachedRoot::maskIfHidden(BestData* best) const
{
    if (mPicture == NULL) {
#if DEBUG_NAV_UI && !defined BROWSER_DEBUG
        LOGD("%s missing picture\n", __FUNCTION__);
#endif
        return false;
    }
    const CachedNode* bestNode = best->mNode;
    if (bestNode->isUnclipped())
        return false;
    // given the picture matching this nav cache
        // create an SkBitmap with dimensions of the focus intersected w/ extended view
    const WebCore::IntRect& nodeBounds = bestNode->getBounds();
    WebCore::IntRect bounds = nodeBounds;
    bounds.intersect(mScrolledBounds);
    int leftMargin = bounds.x() == nodeBounds.x() ? kMargin : 0;
    int topMargin = bounds.y() == nodeBounds.y() ? kMargin : 0;
    int rightMargin = bounds.right() == nodeBounds.right() ? kMargin : 0;
    int bottomMargin = bounds.bottom() == nodeBounds.bottom() ? kMargin : 0;
    bool unclipped = (leftMargin & topMargin & rightMargin & bottomMargin) != 0;
    WebCore::IntRect marginBounds = nodeBounds;
    marginBounds.inflate(kMargin);
    marginBounds.intersect(mScrolledBounds);
    BoundsCheck boundsCheck;
    BoundsCanvas checker(&boundsCheck);
    boundsCheck.mBounds.set(leftMargin, topMargin, 
        leftMargin + bounds.width(), topMargin + bounds.height());
    boundsCheck.mBoundsSlop = boundsCheck.mBounds;
    boundsCheck.mBoundsSlop.inset(-kSlop, -kSlop);
    SkBitmap bitmap;
    bitmap.setConfig(SkBitmap::kARGB_8888_Config, marginBounds.width(),
        marginBounds.height());
    checker.setBitmapDevice(bitmap);
    // insert probes to be called when the data corresponding to this focus ring is drawn
        // need to know if focus ring was generated by text, image, or parent (like div)
        // ? need to know (like imdb menu bar) to give up sometimes (when?)
    checker.translate(SkIntToScalar(leftMargin - bounds.x()),
        SkIntToScalar(topMargin - bounds.y()));
    checker.drawPicture(*mPicture);
    boundsCheck.checkLast();
    // was it not drawn or clipped out?
    if (boundsCheck.hidden()) { // if hidden, return false so that nav can try again
        CachedNode* node = const_cast<CachedNode*>(best->mNode);
#if DEBUG_NAV_UI && !defined BROWSER_DEBUG
        const SkIRect& m = boundsCheck.mBounds;
        const SkIRect& s = boundsCheck.mBoundsSlop;
        LOGD("%s hidden node:%p (%d) mBounds={%d,%d,%d,%d} mBoundsSlop="
            "{%d,%d,%d,%d}\n", __FUNCTION__, node, node->index(),
            m.fLeft, m.fTop, m.fRight, m.fBottom,
            s.fLeft, s.fTop, s.fRight, s.fBottom);
        const SkIRect& o = boundsCheck.mDrawnOver.getBounds();
        const SkIRect& l = boundsCheck.mLastAll;
        const SkIRect& u = boundsCheck.mUnion;
        LOGD("%s hidden mDrawnOver={%d,%d,%d,%d} mLastAll={%d,%d,%d,%d}"
            " mUnion={%d,%d,%d,%d}\n", __FUNCTION__,
            o.fLeft, o.fTop, o.fRight, o.fBottom,
            l.fLeft, l.fTop, l.fRight, l.fBottom,
            u.fLeft, u.fTop, u.fRight, u.fBottom);
        const SkIRect& a = boundsCheck.mAllDrawnIn;
        const WebCore::IntRect& c = mScrolledBounds;
        const WebCore::IntRect& b = nodeBounds;
        LOGD("%s hidden mAllDrawnIn={%d,%d,%d,%d} mScrolledBounds={%d,%d,%d,%d}"
            " nodeBounds={%d,%d,%d,%d}\n", __FUNCTION__,
            a.fLeft, a.fTop, a.fRight, a.fBottom,
            c.x(), c.y(), c.right(), c.bottom(),
            b.x(), b.y(), b.right(), b.bottom());
        LOGD("%s bits.mWidth=%d bits.mHeight=%d transX=%d transY=%d\n", __FUNCTION__,
            marginBounds.width(),marginBounds.height(),
            kMargin - bounds.x(), kMargin - bounds.y());
#endif
        node->setDisabled(true);
        node->setClippedOut(unclipped == false);
        return true;
    }
    // was it partially occluded by later drawing?
    // if partially occluded, modify the bounds so that the mouse click has a better x,y
       const SkIRect& over = boundsCheck.mDrawnOver.getBounds();
    if (over.isEmpty() == false) {
#if DEBUG_NAV_UI && !defined BROWSER_DEBUG
        SkIRect orig = boundsCheck.mBounds;
#endif
        SkIRect& base = boundsCheck.mBounds;
        if (base.fLeft < over.fRight && base.fRight > over.fRight)
            base.fLeft = over.fRight;
        else if (base.fRight > over.fLeft && base.fLeft < over.fLeft)
            base.fRight = over.fLeft;
        if (base.fTop < over.fBottom && base.fBottom > over.fBottom)
            base.fTop = over.fBottom;
        else if (base.fBottom > over.fTop && base.fTop < over.fTop)
            base.fBottom = over.fTop;
#if DEBUG_NAV_UI && !defined BROWSER_DEBUG
        const SkIRect& modded = boundsCheck.mBounds;
        LOGD("%s partially occluded node:%p (%d) old:{%d,%d,%d,%d} new:{%d,%d,%d,%d}\n",
            __FUNCTION__, best->mNode, best->mNode->index(), 
            orig.fLeft, orig.fTop, orig.fRight, orig.fBottom,
            base.fLeft, base.fTop, base.fRight, base.fBottom);
#endif
        best->mMouseBounds = WebCore::IntRect(bounds.x() + base.fLeft - kMargin, 
            bounds.y() + base.fTop - kMargin, base.width(), base.height());
    }
    return false;
}

const CachedNode* CachedRoot::moveFocus(Direction direction, const CachedFrame** framePtr, 
    WebCore::IntPoint* scroll)
{
#ifndef NDEBUG
    ASSERT(CachedFrame::mDebug.mInUse);
#endif
    CachedRoot* frame = this;
    const CachedNode* node = frame->document();
    if (node == NULL)
        return NULL;
    if (mViewBounds.isEmpty())
        return NULL;
    resetClippedOut();
    setData();
    BestData bestData;
    innerMove(node, &bestData, direction, scroll, true);
    *framePtr = bestData.mFrame;
    return const_cast<CachedNode*>(bestData.mNode);
}

void CachedRoot::reset()
{
#ifndef NDEBUG
    ASSERT(CachedFrame::mDebug.mInUse);
#endif
    mContents = mViewBounds = WebCore::IntRect(0, 0, 0, 0);
    mMaxXScroll = mMaxYScroll = 0;
    mSelectionStart = mSelectionEnd = -1;
    mScrollOnly = false;
//    resetNavClipBounds();
}

bool CachedRoot::scrollDelta(WebCore::IntRect& newOutset, Direction direction, int* delta)
{
    switch (direction) {
        case LEFT:
            *delta = -mMaxXScroll;
            return newOutset.x() >= mViewBounds.x();
        case RIGHT: 
            *delta = mMaxXScroll;
            return newOutset.right() <= mViewBounds.right();
        case UP:
            *delta = -mMaxYScroll;
            return newOutset.y() >= mViewBounds.y();
        case DOWN: 
            *delta = mMaxYScroll;
            return newOutset.bottom() <= mViewBounds.bottom();
        default:
            *delta = 0;
            ASSERT(0);
    }
    return false;
}

void CachedRoot::setCachedFocus(CachedFrame* frame, CachedNode* node)
{
#if !defined NDEBUG
    ASSERT(CachedFrame::mDebug.mInUse);
#endif
#if DEBUG_NAV_UI && !defined BROWSER_DEBUG
    const CachedNode* focus = currentFocus();
    WebCore::IntRect bounds;
    if (focus)
        bounds = focus->bounds();
    LOGD("%s old focus %d (nodePointer=%p) bounds={%d,%d,%d,%d}\n", __FUNCTION__, 
        focus ? focus->index() : 0,
        focus ? focus->nodePointer() : NULL, bounds.x(), bounds.y(), 
        bounds.width(), bounds.height());
#endif
    clearFocus();
    if (node == NULL)
        return;
    node->setIsFocus(true);
    ASSERT(node->isFrame() == false);
    frame->setFocusIndex(node - frame->document());
    ASSERT(frame->focusIndex() > 0 && frame->focusIndex() < (int) frame->size());
    CachedFrame* parent;
    while ((parent = frame->parent()) != NULL) {
        parent->setFocusIndex(frame->indexInParent());
        frame = parent;
    }
#if DEBUG_NAV_UI && !defined BROWSER_DEBUG
    focus = currentFocus();
    bounds = WebCore::IntRect(0, 0, 0, 0);
    if (focus)
        bounds = focus->bounds();
    LOGD("%s new focus %d (nodePointer=%p) bounds={%d,%d,%d,%d}\n", __FUNCTION__,
        focus ? focus->index() : 0,
        focus ? focus->nodePointer() : NULL, bounds.x(), bounds.y(), 
        bounds.width(), bounds.height());
#endif
}

void CachedRoot::setupScrolledBounds() const
{
    mScrolledBounds = mViewBounds;
}

#if DUMP_NAV_CACHE

#define DEBUG_PRINT_BOOL(field) \
    DUMP_NAV_LOGD("// bool " #field "=%s;\n", b->field ? "true" : "false")

CachedRoot* CachedRoot::Debug::base() const {
    CachedRoot* nav = (CachedRoot*) ((char*) this - OFFSETOF(CachedRoot, mDebug));
    return nav; 
}

void CachedRoot::Debug::print() const
{
#ifdef DUMP_NAV_CACHE_USING_PRINTF
    gWriteLogMutex.lock();
    ASSERT(gNavCacheLogFile == NULL);
    gNavCacheLogFile = fopen(NAV_CACHE_LOG_FILE, "a");
#endif
    CachedRoot* b = base();
    b->CachedFrame::mDebug.print();
    b->mHistory->mDebug.print(b);
    DUMP_NAV_LOGD("// int mMaxXScroll=%d, mMaxYScroll=%d;\n", 
        b->mMaxXScroll, b->mMaxYScroll);
    DEBUG_PRINT_BOOL(mFocusChild);
#ifdef DUMP_NAV_CACHE_USING_PRINTF
    if (gNavCacheLogFile)
        fclose(gNavCacheLogFile);
    gNavCacheLogFile = NULL;
    gWriteLogMutex.unlock();
#endif
}

#endif

}
