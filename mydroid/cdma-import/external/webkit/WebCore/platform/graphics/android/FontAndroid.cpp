/*
 * Copyright (C) 2006 Apple Computer, Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY APPLE COMPUTER, INC. ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL APPLE COMPUTER, INC. OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */

#include "config.h"
#include "Font.h"

#include "FontData.h"
#include "FontFallbackList.h"
#include "GraphicsContext.h"
#include "GlyphBuffer.h"
#include "PlatformGraphicsContext.h"
#include "IntRect.h"

#include "SkCanvas.h"
#include "SkPaint.h"
#include "SkTemplates.h"
#include "SkTypeface.h"
#include "SkUtils.h"

namespace WebCore {

void Font::drawGlyphs(GraphicsContext* gc, const SimpleFontData* font,
                      const GlyphBuffer& glyphBuffer,  int from, int numGlyphs,
                      const FloatPoint& point) const {
    SkCanvas*   canvas = gc->platformContext()->mCanvas;
    SkPaint     paint;

    font->platformData().setupPaint(&paint);
    paint.setTextEncoding(SkPaint::kGlyphID_TextEncoding);
    paint.setColor(gc->fillColor().rgb());

    SkASSERT(sizeof(GlyphBufferGlyph) == sizeof(uint16_t));  // compile-time assert

    const GlyphBufferGlyph*     glyphs = glyphBuffer.glyphs(from);
    SkScalar                    x = SkFloatToScalar(point.x());
    SkScalar                    y = SkFloatToScalar(point.y());

    if (glyphBuffer.hasAdjustedWidths()) {
        const GlyphBufferAdvance*   adv = glyphBuffer.advances(from);
        SkAutoSTMalloc<32, SkPoint> storage(numGlyphs);
        SkPoint*                    pos = storage.get();
        
        for (int i = 0; i < numGlyphs; i++) {
            pos[i].set(x, y);
            x += SkFloatToScalar(adv[i].width());
            y += SkFloatToScalar(adv[i].height());
        }
        canvas->drawPosText(glyphs, numGlyphs << 1, pos, paint);
    } else {
        canvas->drawText(glyphs, numGlyphs << 1, x, y, paint);
    }
}

FloatRect Font::selectionRectForComplexText(const TextRun& run, const IntPoint& point, int h, int, int) const
{
    SkPaint              paint;
    SkScalar             width, left;
    SkPaint::FontMetrics metrics;

    primaryFont()->platformData().setupPaint(&paint);

    width = paint.measureText(run.characters(), run.length() << 1);
    SkScalar spacing = paint.getFontMetrics(&metrics);
    
    return FloatRect(point.x(),
					 point.y() - floorf(SkScalarToFloat(-metrics.fAscent)),
                     roundf(SkScalarToFloat(width)),
                     roundf(SkScalarToFloat(spacing)));
}

void Font::drawComplexText(GraphicsContext* gc, TextRun const& run, FloatPoint const& point, int, int) const
{
    SkCanvas*   canvas = gc->platformContext()->mCanvas;
    SkPaint     paint;

    primaryFont()->platformData().setupPaint(&paint);
    paint.setColor(gc->fillColor().rgb());

#if 0
    int n = run.to() - run.from();
printf("------------- complex draw %d chars", n);
    for (int i = 0; i < n; i++)
        printf(" %04X", run.data(run.from())[i]);
    printf("\n");
#endif

    canvas->drawText(run.characters(), run.length() << 1,
                     SkFloatToScalar(point.x()), SkFloatToScalar(point.y()),
                     paint);
}

float Font::floatWidthForComplexText(const TextRun& run) const
{
    SkPaint paint;

    primaryFont()->platformData().setupPaint(&paint);

//printf("--------- complext measure %d chars\n", run.to() - run.from());

    SkScalar width = paint.measureText(run.characters(), run.length() << 1);
    return SkScalarToFloat(width);
}

int Font::offsetForPositionForComplexText(const TextRun& run, int x, bool includePartialGlyphs) const
{
    SkPaint                         paint;
    int                             count = run.length();
    SkAutoSTMalloc<64, SkScalar>    storage(count);
    SkScalar*                       widths = storage.get();

    primaryFont()->platformData().setupPaint(&paint);

    count = paint.getTextWidths(run.characters(), count << 1, widths);

    if (count > 0)
    {
        SkScalar pos = 0;
        for (int i = 0; i < count; i++)
        {
            if (x < SkScalarRound(pos + SkScalarHalf(widths[i])))
                return i;
            pos += widths[i];
        }
    }
    return count;
}

}
