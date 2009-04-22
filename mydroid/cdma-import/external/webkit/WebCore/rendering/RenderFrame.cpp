/**
 * This file is part of the KDE project.
 *
 * Copyright (C) 1999 Lars Knoll (knoll@kde.org)
 *           (C) 2000 Simon Hausmann <hausmann@kde.org>
 *           (C) 2000 Stefan Schimanski (1Stein@gmx.de)
 * Copyright (C) 2004, 2005, 2006 Apple Computer, Inc.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; see the file COPYING.LIB.  If not, write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301, USA.
 *
 */

#include "config.h"
#include "RenderFrame.h"
#include "RenderFrameSet.h"
#include "FrameView.h"
#include "HTMLFrameSetElement.h"
#include "HTMLNames.h"

#ifdef FLATTEN_FRAMESET
#include "Frame.h"
#include "Document.h"
#include "RenderView.h"
#endif

namespace WebCore {

using namespace HTMLNames;

RenderFrame::RenderFrame(HTMLFrameElement* frame)
    : RenderPart(frame)
{
    setInline(false);
}

FrameEdgeInfo RenderFrame::edgeInfo() const
{
    return FrameEdgeInfo(element()->noResize(), element()->hasFrameBorder());
}

void RenderFrame::viewCleared()
{
    if (element() && m_widget && m_widget->isFrameView()) {
        FrameView* view = static_cast<FrameView*>(m_widget);
        int marginw = element()->getMarginWidth();
        int marginh = element()->getMarginHeight();

        if (marginw != -1)
            view->setMarginWidth(marginw);
        if (marginh != -1)
            view->setMarginHeight(marginh);
    }
}

#ifdef FLATTEN_FRAMESET
void RenderFrame::layout()
{
    if (m_widget && m_widget->isFrameView()) {
        FrameView* view = static_cast<FrameView*>(m_widget);
        RenderView* root = NULL;
        if (view->frame() && view->frame()->document() && 
            view->frame()->document()->renderer() && view->frame()->document()->renderer()->isRenderView())
            root = static_cast<RenderView*>(view->frame()->document()->renderer());
        if (root) {
            // Resize the widget so that the RenderView will layout according to those dimensions.
            view->resize(m_width, m_height);
            view->layout();
            // We can only grow in width and height because if positionFrames gives us a width and we become smaller,
            // then the fixup process of forcing the frame to fill extra space will fail.
            if (m_width > root->docWidth()) {
                view->resize(root->docWidth(), 0);
                view->layout();
            }
            // Honor the height set by RenderFrameSet::positionFrames unless our document height is larger.
            m_height = max(root->docHeight(), m_height);
            m_width = max(root->docWidth(), m_width);
        }
    }
    setNeedsLayout(false);
}
#endif

} // namespace WebCore
