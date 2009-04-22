/*
 * Copyright (C) 2006 Nikolas Zimmermann <zimmermann@kde.org> 
 * Copyright (C) 2006 Zack Rusin <zack@kde.org>
 * Copyright (C) 2006 Apple Computer, Inc.
 *
 * All rights reserved.
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
#include "EditorClientQt.h"

#include "qwebpage.h"
#include "qwebpage_p.h"

#include "Document.h"
#include "EditCommandQt.h"
#include "Page.h"
#include "Editor.h"
#include "FocusController.h"
#include "Frame.h"
#include "KeyboardCodes.h"
#include "KeyboardEvent.h"
#include "Page.h"
#include "PlatformKeyboardEvent.h"
#include "NotImplemented.h"
#include "Node.h"
#include "Range.h"

#include <stdio.h>

#include <QUndoStack>
#define methodDebug() qDebug("EditorClientQt: %s", __FUNCTION__);

static bool dumpEditingCallbacks = false;
static bool drt_run = false;
static bool acceptsEditing = true;
void QWEBKIT_EXPORT qt_dump_editing_callbacks(bool b)
{
    dumpEditingCallbacks = b;
}
void QWEBKIT_EXPORT qt_drt_run(bool b)
{
    drt_run = b;
}

void QWEBKIT_EXPORT qt_dump_set_accepts_editing(bool b)
{
    acceptsEditing = b;
}


static QString dumpPath(WebCore::Node *node)
{
    QString str = node->nodeName();
    
    WebCore::Node *parent = node->parentNode();
    while (parent) {
        str.append(QLatin1String(" > "));
        str.append(parent->nodeName());
        parent = parent->parentNode();
    }
    return str;
}

static QString dumpRange(WebCore::Range *range)
{
    if (!range)
        return QLatin1String("(null)");
    QString str;
    WebCore::ExceptionCode code;
    str.sprintf("range from %ld of %ls to %ld of %ls",
                range->startOffset(code), dumpPath(range->startContainer(code)).unicode(),
                range->endOffset(code), dumpPath(range->endContainer(code)).unicode());
    return str;
}
    

namespace WebCore {


bool EditorClientQt::shouldDeleteRange(Range* range)
{
    if (dumpEditingCallbacks) 
        printf("EDITING DELEGATE: shouldDeleteDOMRange:%s\n", dumpRange(range).toUtf8().constData());

    return true;
}

bool EditorClientQt::shouldShowDeleteInterface(HTMLElement* element)
{
    if (drt_run) 
        return element->className() == "needsDeletionUI";
    return false;
}

bool EditorClientQt::isContinuousSpellCheckingEnabled()
{
    return false;
}

bool EditorClientQt::isGrammarCheckingEnabled()
{
    return false;
}

int EditorClientQt::spellCheckerDocumentTag()
{
    return 0;
}

bool EditorClientQt::shouldBeginEditing(WebCore::Range* range)
{
    if (dumpEditingCallbacks)
        printf("EDITING DELEGATE: shouldBeginEditingInDOMRange:%s\n", dumpRange(range).toUtf8().constData());
    return true;
}

bool EditorClientQt::shouldEndEditing(WebCore::Range* range)
{
    if (dumpEditingCallbacks)
        printf("EDITING DELEGATE: shouldEndEditingInDOMRange:%s\n", dumpRange(range).toUtf8().constData());
    return true;
}

bool EditorClientQt::shouldInsertText(String string, Range* range, EditorInsertAction action)
{
    if (dumpEditingCallbacks) {
        static const char *insertactionstring[] = {
            "WebViewInsertActionTyped",
            "WebViewInsertActionPasted",
            "WebViewInsertActionDropped",
        };
        
        printf("EDITING DELEGATE: shouldInsertText:%s replacingDOMRange:%s givenAction:%s\n",
               QString(string).toUtf8().constData(), dumpRange(range).toUtf8().constData(), insertactionstring[action]);
    }
    return acceptsEditing;
}

bool EditorClientQt::shouldChangeSelectedRange(Range* currentRange, Range* proposedRange, EAffinity selectionAffinity, bool stillSelecting)
{
    if (dumpEditingCallbacks) {
        static const char *affinitystring[] = {
            "NSSelectionAffinityUpstream",
            "NSSelectionAffinityDownstream"
        };
        static const char *boolstring[] = {
            "FALSE",
            "TRUE"
        };
        
        printf("EDITING DELEGATE: shouldChangeSelectedDOMRange:%s toDOMRange:%s affinity:%s stillSelecting:%s\n",
               dumpRange(currentRange).toUtf8().constData(),
               dumpRange(proposedRange).toUtf8().constData(),
               affinitystring[selectionAffinity], boolstring[stillSelecting]);
    }
    return acceptsEditing;
}

bool EditorClientQt::shouldApplyStyle(WebCore::CSSStyleDeclaration* style,
                                      WebCore::Range* range)
{
    if (dumpEditingCallbacks)
        printf("EDITING DELEGATE: shouldApplyStyle:%s toElementsInDOMRange:%s\n",
               QString(style->cssText()).toUtf8().constData(), dumpRange(range).toUtf8().constData());
    return acceptsEditing;
}

bool EditorClientQt::shouldMoveRangeAfterDelete(WebCore::Range*, WebCore::Range*)
{
    notImplemented();
    return true;
}

void EditorClientQt::didBeginEditing()
{
    if (dumpEditingCallbacks)
        printf("EDITING DELEGATE: webViewDidBeginEditing:WebViewDidBeginEditingNotification\n");
    m_editing = true;
}

void EditorClientQt::respondToChangedContents()
{
    if (dumpEditingCallbacks)
        printf("EDITING DELEGATE: webViewDidChange:WebViewDidChangeNotification\n");
    m_page->d->modified = true;
}

void EditorClientQt::respondToChangedSelection()
{
    if (dumpEditingCallbacks)
        printf("EDITING DELEGATE: webViewDidChangeSelection:WebViewDidChangeSelectionNotification\n");
//     const Selection &selection = m_page->d->page->selection();
//     char buffer[1024];
//     selection.formatForDebugger(buffer, sizeof(buffer));
//     printf("%s\n", buffer);

    m_page->d->updateEditorActions();
    emit m_page->selectionChanged();
}

void EditorClientQt::didEndEditing()
{
    if (dumpEditingCallbacks)
        printf("EDITING DELEGATE: webViewDidEndEditing:WebViewDidEndEditingNotification\n");
    m_editing = false;
}

void EditorClientQt::didWriteSelectionToPasteboard()
{
}

void EditorClientQt::didSetSelectionTypesForPasteboard()
{
}

bool EditorClientQt::selectWordBeforeMenuEvent()
{
    notImplemented();
    return false;
}

bool EditorClientQt::isEditable()
{ 
    // FIXME: should be controllable by a setting in QWebPage
    return false;
}

void EditorClientQt::registerCommandForUndo(WTF::PassRefPtr<WebCore::EditCommand> cmd)
{
    Frame* frame = m_page->d->page->focusController()->focusedOrMainFrame();
    if (m_inUndoRedo || (frame && !frame->editor()->lastEditCommand() /* HACK!! Don't recreate undos */)) {
        return;
    }
    m_page->undoStack()->push(new EditCommandQt(cmd));
}

void EditorClientQt::registerCommandForRedo(WTF::PassRefPtr<WebCore::EditCommand>)
{
}

void EditorClientQt::clearUndoRedoOperations()
{
    return m_page->undoStack()->clear();
}

bool EditorClientQt::canUndo() const
{
    return m_page->undoStack()->canUndo();
}

bool EditorClientQt::canRedo() const
{
    return m_page->undoStack()->canRedo();
}

void EditorClientQt::undo()
{
    m_inUndoRedo = true;
    m_page->undoStack()->undo();
    m_inUndoRedo = false;
}

void EditorClientQt::redo()
{
    m_inUndoRedo = true;
    m_page->undoStack()->redo();
    m_inUndoRedo = false;
}

bool EditorClientQt::shouldInsertNode(Node* node, Range* range, EditorInsertAction action)
{
    if (dumpEditingCallbacks) {
        static const char *insertactionstring[] = {
            "WebViewInsertActionTyped",
            "WebViewInsertActionPasted",
            "WebViewInsertActionDropped",
        };
        
        printf("EDITING DELEGATE: shouldInsertNode:%s replacingDOMRange:%s givenAction:%s\n", dumpPath(node).toUtf8().constData(),
               dumpRange(range).toUtf8().constData(), insertactionstring[action]);
    }
    return acceptsEditing;
}

void EditorClientQt::pageDestroyed()
{
    delete this;
}

bool EditorClientQt::smartInsertDeleteEnabled()
{
    notImplemented();
    return false;
}

void EditorClientQt::toggleContinuousSpellChecking()
{
    notImplemented();
}

void EditorClientQt::toggleGrammarChecking()
{
    notImplemented();
}

void EditorClientQt::handleKeyboardEvent(KeyboardEvent* event)
{
    Frame* frame = m_page->d->page->focusController()->focusedOrMainFrame();
    if (!frame || !frame->document()->focusedNode())
        return;

    const PlatformKeyboardEvent* kevent = event->keyEvent();
    if (!kevent || kevent->type() == PlatformKeyboardEvent::KeyUp)
        return;

    Node* start = frame->selectionController()->start().node();
    if (!start)
        return;

    // FIXME: refactor all of this to use Actions or something like them
    if (start->isContentEditable()) {
        switch (kevent->windowsVirtualKeyCode()) {
            case VK_RETURN:
                frame->editor()->command("InsertLineBreak").execute();
                break;
            case VK_BACK:
                frame->editor()->deleteWithDirection(SelectionController::BACKWARD,
                        CharacterGranularity, false, true);
                break;
            case VK_DELETE:
                frame->editor()->deleteWithDirection(SelectionController::FORWARD,
                        CharacterGranularity, false, true);
                break;
            case VK_LEFT:
                if (kevent->shiftKey())
                    frame->editor()->command("MoveLeftAndModifySelection").execute();
                else frame->editor()->command("MoveLeft").execute();
                break;
            case VK_RIGHT:
                if (kevent->shiftKey())
                    frame->editor()->command("MoveRightAndModifySelection").execute();
                else frame->editor()->command("MoveRight").execute();
                break;
            case VK_UP:
                if (kevent->shiftKey())
                    frame->editor()->command("MoveUpAndModifySelection").execute();
                else frame->editor()->command("MoveUp").execute();
                break;
            case VK_DOWN:
                if (kevent->shiftKey())
                    frame->editor()->command("MoveDownAndModifySelection").execute();
                else frame->editor()->command("MoveDown").execute();
                break;
            case VK_PRIOR:  // PageUp
                frame->editor()->command("MovePageUp").execute();
                break;
            case VK_NEXT:  // PageDown
                frame->editor()->command("MovePageDown").execute();
                break;
            case VK_TAB:
                return;
            default:
                if (!kevent->ctrlKey() && !kevent->altKey() && !kevent->text().isEmpty()) {
                    frame->editor()->insertText(kevent->text(), event);
                } else if (kevent->ctrlKey()) {
                    switch (kevent->windowsVirtualKeyCode()) {
                        case VK_A:
                            frame->editor()->command("SelectAll").execute();
                            break;
                        case VK_B:
                            frame->editor()->command("ToggleBold").execute();
                            break;
                        case VK_C:
                            frame->editor()->command("Copy").execute();
                            break;
                        case VK_I:
                            frame->editor()->command("ToggleItalic").execute();
                            break;
                        case VK_V:
                            frame->editor()->command("Paste").execute();
                            break;
                        case VK_X:
                            frame->editor()->command("Cut").execute();
                            break;
                        case VK_Y:
                            frame->editor()->command("Redo").execute();
                            break;
                        case VK_Z:
                            frame->editor()->command("Undo").execute();
                            break;
                        default:
                            return;
                    }
                } else return;
        }
    } else {
        switch (kevent->windowsVirtualKeyCode()) {
            case VK_UP:
                frame->editor()->command("MoveUp").execute();
                break;
            case VK_DOWN:
                frame->editor()->command("MoveDown").execute();
                break;
            case VK_PRIOR:  // PageUp
                frame->editor()->command("MovePageUp").execute();
                break;
            case VK_NEXT:  // PageDown
                frame->editor()->command("MovePageDown").execute();
                break;
            case VK_HOME:
                if (kevent->ctrlKey())
                    frame->editor()->command("MoveToBeginningOfDocument").execute();
                break;
            case VK_END:
                if (kevent->ctrlKey())
                    frame->editor()->command("MoveToEndOfDocument").execute();
                break;
            default:
                if (kevent->ctrlKey()) {
                    switch (kevent->windowsVirtualKeyCode()) {
                        case VK_A:
                            frame->editor()->command("SelectAll").execute();
                            break;
                        case VK_C: case VK_X:
                            frame->editor()->command("Copy").execute();
                            break;
                        default:
                            return;
                    }
                } else return;
        }
    }
    event->setDefaultHandled();
}

void EditorClientQt::handleInputMethodKeydown(KeyboardEvent*)
{
}

EditorClientQt::EditorClientQt(QWebPage* page)
    : m_page(page), m_editing(false), m_inUndoRedo(false)
{
}

void EditorClientQt::textFieldDidBeginEditing(Element*)
{
    m_editing = true;
}

void EditorClientQt::textFieldDidEndEditing(Element*)
{
    m_editing = false;
}

void EditorClientQt::textDidChangeInTextField(Element*)
{
}

bool EditorClientQt::doTextFieldCommandFromEvent(Element*, KeyboardEvent*)
{
    return false;
}

void EditorClientQt::textWillBeDeletedInTextField(Element*)
{
}

void EditorClientQt::textDidChangeInTextArea(Element*)
{
}

void EditorClientQt::ignoreWordInSpellDocument(const String&)
{
    notImplemented();
}

void EditorClientQt::learnWord(const String&)
{
    notImplemented();
}

void EditorClientQt::checkSpellingOfString(const UChar*, int, int*, int*)
{
    notImplemented();
}

void EditorClientQt::checkGrammarOfString(const UChar*, int, Vector<GrammarDetail>&, int*, int*)
{
    notImplemented();
}

void EditorClientQt::updateSpellingUIWithGrammarString(const String&, const GrammarDetail&)
{
    notImplemented();
}

void EditorClientQt::updateSpellingUIWithMisspelledWord(const String&)
{
    notImplemented();
}

void EditorClientQt::showSpellingUI(bool)
{
    notImplemented();
}

bool EditorClientQt::spellingUIIsShowing()
{
    notImplemented();
    return false;
}

void EditorClientQt::getGuessesForWord(const String&, Vector<String>&)
{
    notImplemented();
}

bool EditorClientQt::isEditing() const
{
    return m_editing;
}
    
void EditorClientQt::setInputMethodState(bool)
{
}

}

// vim: ts=4 sw=4 et
