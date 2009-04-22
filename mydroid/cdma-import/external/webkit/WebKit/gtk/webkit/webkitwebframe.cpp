/*
 * Copyright (C) 2007 Holger Hans Peter Freyther
 * Copyright (C) 2007 Alp Toker <alp@atoker.com>
 * Copyright (C) 2007 Apple Inc.
 * Copyright (C) 2008 Christian Dywan <christian@imendio.com>
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
 */

#include "config.h"

#include "webkitwebframe.h"
#include "webkitwebview.h"
#include "webkit-marshal.h"
#include "webkitprivate.h"

#include "CString.h"
#include "FrameLoader.h"
#include "FrameLoaderClientGtk.h"
#include "FrameTree.h"
#include "FrameView.h"
#include "GraphicsContext.h"
#include "HTMLFrameOwnerElement.h"
#include "RenderView.h"
#include "kjs_binding.h"
#include "kjs_proxy.h"
#include "kjs_window.h"

#include <JavaScriptCore/APICast.h>

using namespace WebKit;
using namespace WebCore;

extern "C" {

enum {
    CLEARED,
    LOAD_COMMITTED,
    LOAD_DONE,
    TITLE_CHANGED,
    HOVERING_OVER_LINK,
    LAST_SIGNAL
};

enum {
    PROP_0,

    PROP_NAME,
    PROP_TITLE,
    PROP_URI
};

static guint webkit_web_frame_signals[LAST_SIGNAL] = { 0, };

G_DEFINE_TYPE(WebKitWebFrame, webkit_web_frame, G_TYPE_OBJECT)

static void webkit_web_frame_get_property(GObject* object, guint prop_id, GValue* value, GParamSpec* pspec)
{
    WebKitWebFrame* frame = WEBKIT_WEB_FRAME(object);

    switch(prop_id) {
    case PROP_NAME:
        g_value_set_string(value, webkit_web_frame_get_name(frame));
        break;
    case PROP_TITLE:
        g_value_set_string(value, webkit_web_frame_get_title(frame));
        break;
    case PROP_URI:
        g_value_set_string(value, webkit_web_frame_get_uri(frame));
        break;
    default:
        G_OBJECT_WARN_INVALID_PROPERTY_ID(object, prop_id, pspec);
        break;
    }
}

static void webkit_web_frame_finalize(GObject* object)
{
    WebKitWebFrame* frame = WEBKIT_WEB_FRAME(object);
    WebKitWebFramePrivate* priv = frame->priv;

    priv->coreFrame->loader()->cancelAndClear();
    g_free(priv->name);
    g_free(priv->title);
    g_free(priv->uri);
    delete priv->coreFrame;

    G_OBJECT_CLASS(webkit_web_frame_parent_class)->finalize(object);
}

static void webkit_web_frame_class_init(WebKitWebFrameClass* frameClass)
{
    webkit_init();

    /*
     * signals
     */
    webkit_web_frame_signals[CLEARED] = g_signal_new("cleared",
            G_TYPE_FROM_CLASS(frameClass),
            (GSignalFlags)(G_SIGNAL_RUN_LAST | G_SIGNAL_ACTION),
            0,
            NULL,
            NULL,
            g_cclosure_marshal_VOID__VOID,
            G_TYPE_NONE, 0);

    webkit_web_frame_signals[LOAD_COMMITTED] = g_signal_new("load-committed",
            G_TYPE_FROM_CLASS(frameClass),
            (GSignalFlags)(G_SIGNAL_RUN_LAST | G_SIGNAL_ACTION),
            0,
            NULL,
            NULL,
            g_cclosure_marshal_VOID__VOID,
            G_TYPE_NONE, 0);

    webkit_web_frame_signals[LOAD_DONE] = g_signal_new("load-done",
            G_TYPE_FROM_CLASS(frameClass),
            (GSignalFlags)(G_SIGNAL_RUN_LAST | G_SIGNAL_ACTION),
            0,
            NULL,
            NULL,
            g_cclosure_marshal_VOID__BOOLEAN,
            G_TYPE_NONE, 1,
            G_TYPE_BOOLEAN);

    webkit_web_frame_signals[TITLE_CHANGED] = g_signal_new("title-changed",
            G_TYPE_FROM_CLASS(frameClass),
            (GSignalFlags)(G_SIGNAL_RUN_LAST | G_SIGNAL_ACTION),
            0,
            NULL,
            NULL,
            webkit_marshal_VOID__STRING,
            G_TYPE_NONE, 1,
            G_TYPE_STRING);

    webkit_web_frame_signals[HOVERING_OVER_LINK] = g_signal_new("hovering-over-link",
            G_TYPE_FROM_CLASS(frameClass),
            (GSignalFlags)(G_SIGNAL_RUN_LAST | G_SIGNAL_ACTION),
            0,
            NULL,
            NULL,
            webkit_marshal_VOID__STRING_STRING,
            G_TYPE_NONE, 2,
            G_TYPE_STRING, G_TYPE_STRING);

    /*
     * implementations of virtual methods
     */
    GObjectClass* objectClass = G_OBJECT_CLASS(frameClass);
    objectClass->finalize = webkit_web_frame_finalize;
    objectClass->get_property = webkit_web_frame_get_property;

    /*
     * properties
     */
    g_object_class_install_property(objectClass, PROP_NAME,
                                    g_param_spec_string("name",
                                                        "Name",
                                                        "The name of the frame",
                                                        NULL,
                                                        WEBKIT_PARAM_READABLE));

    g_object_class_install_property(objectClass, PROP_TITLE,
                                    g_param_spec_string("title",
                                                        "Title",
                                                        "The document title of the frame",
                                                        NULL,
                                                        WEBKIT_PARAM_READABLE));

    g_object_class_install_property(objectClass, PROP_URI,
                                    g_param_spec_string("uri",
                                                        "URI",
                                                        "The current URI of the contents displayed by the frame",
                                                        NULL,
                                                        WEBKIT_PARAM_READABLE));

    g_type_class_add_private(frameClass, sizeof(WebKitWebFramePrivate));
}

static void webkit_web_frame_init(WebKitWebFrame* frame)
{
    WebKitWebFramePrivate* priv = WEBKIT_WEB_FRAME_GET_PRIVATE(frame);

    // TODO: Move constructor code here.
    frame->priv = priv;
}

/**
 * webkit_web_frame_new:
 * @web_view: the controlling #WebKitWebView
 *
 * Creates a new #WebKitWebFrame initialized with a controlling #WebKitWebView.
 *
 * Returns: a new #WebKitWebFrame
 **/
WebKitWebFrame* webkit_web_frame_new(WebKitWebView* webView)
{
    g_return_val_if_fail(WEBKIT_IS_WEB_VIEW(webView), NULL);

    WebKitWebFrame* frame = WEBKIT_WEB_FRAME(g_object_new(WEBKIT_TYPE_WEB_FRAME, NULL));
    WebKitWebFramePrivate* priv = frame->priv;
    WebKitWebViewPrivate* viewPriv = WEBKIT_WEB_VIEW_GET_PRIVATE(webView);

    priv->client = new WebKit::FrameLoaderClient(frame);
    priv->coreFrame = new Frame(viewPriv->corePage, 0, priv->client);

    FrameView* frameView = new FrameView(priv->coreFrame);
    frameView->setContainingWindow(GTK_CONTAINER(webView));
    frameView->setGtkAdjustments(GTK_ADJUSTMENT(gtk_adjustment_new(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
                                 GTK_ADJUSTMENT(gtk_adjustment_new(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)));
    priv->coreFrame->setView(frameView);
    priv->coreFrame->init();
    priv->webView = webView;

    return frame;
}

WebKitWebFrame* webkit_web_frame_init_with_web_view(WebKitWebView* webView, HTMLFrameOwnerElement* element)
{
    WebKitWebFrame* frame = WEBKIT_WEB_FRAME(g_object_new(WEBKIT_TYPE_WEB_FRAME, NULL));
    WebKitWebFramePrivate* priv = frame->priv;
    WebKitWebViewPrivate* viewPriv = WEBKIT_WEB_VIEW_GET_PRIVATE(webView);

    priv->client = new WebKit::FrameLoaderClient(frame);
    priv->coreFrame = new Frame(viewPriv->corePage, element, priv->client);

    FrameView* frameView = new FrameView(priv->coreFrame);
    frameView->setContainingWindow(GTK_CONTAINER(webView));
    priv->coreFrame->setView(frameView);
    frameView->deref();
    priv->coreFrame->init();
    priv->webView = webView;

    return frame;
}

/**
 * webkit_web_frame_get_title:
 * @frame: a #WebKitWebFrame
 *
 * Returns the @frame's document title
 *
 * Return value: the title of @frame
 */
const gchar* webkit_web_frame_get_title(WebKitWebFrame* frame)
{
    g_return_val_if_fail(WEBKIT_IS_WEB_FRAME(frame), NULL);

    WebKitWebFramePrivate* priv = frame->priv;
    return priv->title;
}

/**
 * webkit_web_frame_get_uri:
 * @frame: a #WebKitWebFrame
 *
 * Returns the current URI of the contents displayed by the @frame
 *
 * Return value: the URI of @frame
 */
const gchar* webkit_web_frame_get_uri(WebKitWebFrame* frame)
{
    g_return_val_if_fail(WEBKIT_IS_WEB_FRAME(frame), NULL);

    WebKitWebFramePrivate* priv = frame->priv;
    return priv->uri;
}

/**
 * webkit_web_frame_get_web_view:
 * @frame: a #WebKitWebFrame
 *
 * Returns the #WebKitWebView that manages this #WebKitWebFrame.
 *
 * The #WebKitWebView returned manages the entire hierarchy of #WebKitWebFrame
 * objects that contains @frame.
 *
 * Return value: the #WebKitWebView that manages @frame
 */
WebKitWebView* webkit_web_frame_get_web_view(WebKitWebFrame* frame)
{
    g_return_val_if_fail(WEBKIT_IS_WEB_FRAME(frame), NULL);

    WebKitWebFramePrivate* priv = frame->priv;
    return priv->webView;
}

/**
 * webkit_web_frame_get_name:
 * @frame: a #WebKitWebFrame
 *
 * Returns the @frame's name
 *
 * Return value: the name of @frame
 */
const gchar* webkit_web_frame_get_name(WebKitWebFrame* frame)
{
    g_return_val_if_fail(WEBKIT_IS_WEB_FRAME(frame), NULL);

    WebKitWebFramePrivate* priv = frame->priv;

    if (priv->name)
        return priv->name;

    Frame* coreFrame = core(frame);
    g_return_val_if_fail(coreFrame, NULL);

    String string = coreFrame->tree()->name();
    priv->name = g_strdup(string.utf8().data());
    return priv->name;
}

/**
 * webkit_web_frame_get_parent:
 * @frame: a #WebKitWebFrame
 *
 * Returns the @frame's parent frame, or %NULL if it has none.
 *
 * Return value: the parent #WebKitWebFrame or %NULL in case there is none
 */
WebKitWebFrame* webkit_web_frame_get_parent(WebKitWebFrame* frame)
{
    g_return_val_if_fail(WEBKIT_IS_WEB_FRAME(frame), NULL);

    Frame* coreFrame = core(frame);
    g_return_val_if_fail(coreFrame, NULL);

    return kit(coreFrame->tree()->parent());
}

/**
 * webkit_web_frame_load_request:
 * @frame: a #WebKitWebFrame
 * @request: a #WebKitNetworkRequest
 *
 * Connects to a given URI by initiating an asynchronous client request.
 *
 * Creates a provisional data source that will transition to a committed data
 * source once any data has been received. Use webkit_web_frame_stop_loading() to
 * stop the load. This function is typically invoked on the main frame.
 */
void webkit_web_frame_load_request(WebKitWebFrame* frame, WebKitNetworkRequest* request)
{
    g_return_if_fail(WEBKIT_IS_WEB_FRAME(frame));
    g_return_if_fail(WEBKIT_IS_NETWORK_REQUEST(request));

    Frame* coreFrame = core(frame);
    g_return_if_fail(coreFrame);

    // TODO: Use the ResourceRequest carried by WebKitNetworkRequest when it is implemented.
    DeprecatedString string = DeprecatedString::fromUtf8(webkit_network_request_get_uri(request));
    coreFrame->loader()->load(ResourceRequest(KURL(string)));
}

/**
 * webkit_web_frame_stop_loading:
 * @frame: a #WebKitWebFrame
 *
 * Stops any pending loads on @frame's data source, and those of its children.
 */
void webkit_web_frame_stop_loading(WebKitWebFrame* frame)
{
    g_return_if_fail(WEBKIT_IS_WEB_FRAME(frame));

    Frame* coreFrame = core(frame);
    g_return_if_fail(coreFrame);

    coreFrame->loader()->stopAllLoaders();
}

/**
 * webkit_web_frame_reload:
 * @frame: a #WebKitWebFrame
 *
 * Reloads the initial request.
 */
void webkit_web_frame_reload(WebKitWebFrame* frame)
{
    g_return_if_fail(WEBKIT_IS_WEB_FRAME(frame));

    Frame* coreFrame = core(frame);
    g_return_if_fail(coreFrame);

    coreFrame->loader()->reload();
}

/**
 * webkit_web_frame_find_frame:
 * @frame: a #WebKitWebFrame
 * @name: the name of the frame to be found
 *
 * For pre-defined names, returns @frame if @name is "_self" or "_current",
 * returns @frame's parent frame if @name is "_parent", and returns the main
 * frame if @name is "_top". Also returns @frame if it is the main frame and
 * @name is either "_parent" or "_top". For other names, this function returns
 * the first frame that matches @name. This function searches @frame and its
 * descendents first, then @frame's parent and its children moving up the
 * hierarchy until a match is found. If no match is found in @frame's
 * hierarchy, this function will search for a matching frame in other main
 * frame hierarchies. Returns %NULL if no match is found.
 *
 * Return value: the found #WebKitWebFrame or %NULL in case none is found
 */
WebKitWebFrame* webkit_web_frame_find_frame(WebKitWebFrame* frame, const gchar* name)
{
    g_return_val_if_fail(WEBKIT_IS_WEB_FRAME(frame), NULL);
    g_return_val_if_fail(name, NULL);

    Frame* coreFrame = core(frame);
    g_return_val_if_fail(coreFrame, NULL);

    String nameString = String::fromUTF8(name);
    return kit(coreFrame->tree()->find(AtomicString(nameString)));
}

/**
 * webkit_web_frame_get_global_context:
 * @frame: a #WebKitWebFrame
 *
 * Gets the global JavaScript execution context. Use this function to bridge
 * between the WebKit and JavaScriptCore APIs.
 *
 * Return value: the global JavaScript context
 */
JSGlobalContextRef webkit_web_frame_get_global_context(WebKitWebFrame* frame)
{
    g_return_val_if_fail(WEBKIT_IS_WEB_FRAME(frame), NULL);

    Frame* coreFrame = core(frame);
    g_return_val_if_fail(coreFrame, NULL);

    return toGlobalRef(coreFrame->scriptProxy()->globalObject()->globalExec());
}

/**
 * webkit_web_frame_get_children:
 * @frame: a #WebKitWebFrame
 *
 * Return value: child frames of @frame
 */
GSList* webkit_web_frame_get_children(WebKitWebFrame* frame)
{
    g_return_val_if_fail(WEBKIT_IS_WEB_FRAME(frame), NULL);

    GSList* children = NULL;
    Frame* coreFrame = core(frame);

    for (Frame* child = coreFrame->tree()->firstChild(); child; child = child->tree()->nextSibling()) {
        FrameLoader* loader = child->loader();
        WebKit::FrameLoaderClient* client = static_cast<WebKit::FrameLoaderClient*>(loader->client());
        if (client)
          children = g_slist_append(children, client->webFrame());
    }

    return children;
}

/**
 * webkit_web_frame_get_inner_text:
 * @frame: a #WebKitWebFrame
 *
 * Return value: inner text of @frame
 */
gchar* webkit_web_frame_get_inner_text(WebKitWebFrame* frame)
{
    g_return_val_if_fail(WEBKIT_IS_WEB_FRAME(frame), NULL);

    Frame* coreFrame = core(frame);
    FrameView* view = coreFrame->view();

    if (view->layoutPending())
        view->layout();

    Element* documentElement = coreFrame->document()->documentElement();
    String string =  documentElement->innerText();
    return g_strdup(string.utf8().data());
}

#if GTK_CHECK_VERSION(2,10,0)

// This could be shared between ports once it's complete
class PrintContext
{
public:
    PrintContext(Frame* frame)
        : m_frame(frame)
    {
    }

    ~PrintContext()
    {
        m_pageRects.clear();
    }

    int pageCount()
    {
        return m_pageRects.size();
    }

    void computePageRects(const FloatRect& printRect, float headerHeight, float footerHeight, float userScaleFactor, float& outPageHeight)
    {
        m_pageRects.clear();
        outPageHeight = 0;

        if (!m_frame->document() || !m_frame->view() || !m_frame->document()->renderer())
            return;

        RenderView* root = static_cast<RenderView*>(m_frame->document()->renderer());

        if (!root) {
            LOG_ERROR("document to be printed has no renderer");
            return;
        }

        if (userScaleFactor <= 0) {
            LOG_ERROR("userScaleFactor has bad value %.2f", userScaleFactor);
            return;
        }

        float ratio = printRect.height() / printRect.width();

        float pageWidth  = (float)root->docWidth();
        float pageHeight = pageWidth * ratio;
        outPageHeight = pageHeight;   // this is the height of the page adjusted by margins
        pageHeight -= headerHeight + footerHeight;

        if (pageHeight <= 0) {
            LOG_ERROR("pageHeight has bad value %.2f", pageHeight);
            return;
        }

        float currPageHeight = pageHeight / userScaleFactor;
        float docHeight = root->layer()->height();
        float currPageWidth = pageWidth / userScaleFactor;

        // always return at least one page, since empty files should print a blank page
        float printedPagesHeight = 0.0;
        do {
            float proposedBottom = min(docHeight, printedPagesHeight + pageHeight);
            m_frame->adjustPageHeight(&proposedBottom, printedPagesHeight, proposedBottom, printedPagesHeight);
            currPageHeight = max(1.0f, proposedBottom - printedPagesHeight);

            m_pageRects.append(IntRect(0, (int)printedPagesHeight, (int)currPageWidth, (int)currPageHeight));
            printedPagesHeight += currPageHeight;
        } while (printedPagesHeight < docHeight);
    }

    // TODO: eliminate width param
    void begin(float width)
    {
        // By imaging to a width a little wider than the available pixels,
        // thin pages will be scaled down a little, matching the way they
        // print in IE and Camino. This lets them use fewer sheets than they
        // would otherwise, which is presumably why other browsers do this.
        // Wide pages will be scaled down more than this.
        const float PrintingMinimumShrinkFactor = 1.25f;

        // This number determines how small we are willing to reduce the page content
        // in order to accommodate the widest line. If the page would have to be
        // reduced smaller to make the widest line fit, we just clip instead (this
        // behavior matches MacIE and Mozilla, at least)
        const float PrintingMaximumShrinkFactor = 2.0f;

        float minLayoutWidth = width * PrintingMinimumShrinkFactor;
        float maxLayoutWidth = width * PrintingMaximumShrinkFactor;

        // FIXME: This will modify the rendering of the on-screen frame.
        // Could lead to flicker during printing.
        m_frame->setPrinting(true, minLayoutWidth, maxLayoutWidth, true);
    }

    // TODO: eliminate width param
    void spoolPage(GraphicsContext& ctx, int pageNumber, float width)
    {
        IntRect pageRect = m_pageRects[pageNumber];
        float scale = width / pageRect.width();

        ctx.save();
        ctx.scale(FloatSize(scale, scale));
        ctx.translate(-pageRect.x(), -pageRect.y());
        ctx.clip(pageRect);
        m_frame->paint(&ctx, pageRect);
        ctx.restore();
    }

    void end()
    {
        m_frame->setPrinting(false, 0, 0, true);
    }

protected:
    Frame* m_frame;
    Vector<IntRect> m_pageRects;
};

static void begin_print(GtkPrintOperation* op, GtkPrintContext* context, gpointer user_data)
{
    PrintContext* printContext = reinterpret_cast<PrintContext*>(user_data);

    float width = gtk_print_context_get_width(context);
    float height = gtk_print_context_get_height(context);
    FloatRect printRect = FloatRect(0, 0, width, height);

    printContext->begin(width);

    // TODO: Margin adjustments and header/footer support
    float headerHeight = 0;
    float footerHeight = 0;
    float pageHeight; // height of the page adjusted by margins
    printContext->computePageRects(printRect, headerHeight, footerHeight, 1.0, pageHeight);
    gtk_print_operation_set_n_pages(op, printContext->pageCount());
}

static void draw_page(GtkPrintOperation* op, GtkPrintContext* context, gint page_nr, gpointer user_data)
{
    PrintContext* printContext = reinterpret_cast<PrintContext*>(user_data);

    cairo_t* cr = gtk_print_context_get_cairo_context(context);
    GraphicsContext ctx(cr);
    float width = gtk_print_context_get_width(context);
    printContext->spoolPage(ctx, page_nr, width);
}

static void end_print(GtkPrintOperation* op, GtkPrintContext* context, gpointer user_data)
{
    PrintContext* printContext = reinterpret_cast<PrintContext*>(user_data);
    printContext->end();
}

void webkit_web_frame_print(WebKitWebFrame* frame)
{
    GtkWidget* topLevel = gtk_widget_get_toplevel(GTK_WIDGET(webkit_web_frame_get_web_view(frame)));
    if (!GTK_WIDGET_TOPLEVEL(topLevel))
        topLevel = NULL;

    Frame* coreFrame = core(frame);
    PrintContext printContext(coreFrame);

    GtkPrintOperation* op = gtk_print_operation_new();
    g_signal_connect(G_OBJECT(op), "begin-print", G_CALLBACK(begin_print), &printContext);
    g_signal_connect(G_OBJECT(op), "draw-page", G_CALLBACK(draw_page), &printContext);
    g_signal_connect(G_OBJECT(op), "end-print", G_CALLBACK(end_print), &printContext);
    GError *error = NULL;
    gtk_print_operation_run(op, GTK_PRINT_OPERATION_ACTION_PRINT_DIALOG, GTK_WINDOW(topLevel), &error);
    g_object_unref(op);

    if (error) {
        GtkWidget* dialog = gtk_message_dialog_new(GTK_WINDOW(topLevel),
                                                   GTK_DIALOG_DESTROY_WITH_PARENT,
                                                   GTK_MESSAGE_ERROR,
                                                   GTK_BUTTONS_CLOSE,
                                                   "%s", error->message);
        g_error_free(error);

        g_signal_connect(dialog, "response", G_CALLBACK(gtk_widget_destroy), NULL);
        gtk_widget_show(dialog);
    }
}

#else

void webkit_web_frame_print(WebKitWebFrame*)
{
    g_warning("Printing support is not available in older versions of GTK+");
}

#endif

}
