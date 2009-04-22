/*
 * Copyright (C) 2007 Kevin Ollivier  All rights reserved.
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
 *
 * This class provides a default new window implementation for wxWebView clients
 * who don't want/need to roll their own browser frame UI.
 */
 
#include "config.h"

#include "wx/wxprec.h"
#ifndef WX_PRECOMP
    #include "wx/wx.h"
#endif

#include "wx/artprov.h"

#include "WebView.h"
#include "WebFrame.h"
#include "WebViewPrivate.h"

wxPageSourceViewFrame::wxPageSourceViewFrame(const wxString& source)
        : wxFrame(NULL, wxID_ANY, _("Page Source View"), wxDefaultPosition, wxSize(600, 500))
{
    wxTextCtrl* control = new wxTextCtrl(this, -1, source, wxDefaultPosition, wxDefaultSize, wxTE_MULTILINE);
}

enum {
    ID_LOADFILE = wxID_HIGHEST + 1,
    ID_TEXTCTRL = wxID_HIGHEST + 2,
    ID_BACK = wxID_HIGHEST + 3,
    ID_FORWARD = wxID_HIGHEST + 4,
    ID_TOGGLE_BEFORE_LOAD = wxID_HIGHEST + 5,
    ID_MAKE_TEXT_LARGER = wxID_HIGHEST + 6,
    ID_MAKE_TEXT_SMALLER = wxID_HIGHEST + 7,
    ID_STOP = wxID_HIGHEST + 8,
    ID_RELOAD = wxID_HIGHEST + 9,
    ID_GET_SOURCE = wxID_HIGHEST + 10,
    ID_SET_SOURCE = wxID_HIGHEST + 11,
    ID_SEARCHCTRL = wxID_HIGHEST + 12,
    ID_LOADURL = wxID_HIGHEST + 13,
    ID_NEW_WINDOW = wxID_HIGHEST + 14,
    ID_BROWSE = wxID_HIGHEST + 15,
    ID_EDIT = wxID_HIGHEST + 16,
    ID_RUN_SCRIPT = wxID_HIGHEST + 17
};

BEGIN_EVENT_TABLE(wxWebFrame, wxFrame)
    EVT_MENU(wxID_EXIT,  wxWebFrame::OnQuit)
    EVT_MENU(wxID_ABOUT, wxWebFrame::OnAbout)
    EVT_MENU(ID_LOADFILE, wxWebFrame::OnLoadFile)
    EVT_TEXT_ENTER(ID_TEXTCTRL, wxWebFrame::OnAddressBarEnter)
    EVT_TEXT_ENTER(ID_SEARCHCTRL, wxWebFrame::OnSearchCtrlEnter)
    EVT_WEBVIEW_LOAD(wxWebFrame::OnLoadEvent)
    EVT_WEBVIEW_BEFORE_LOAD(wxWebFrame::OnBeforeLoad)
    EVT_MENU(ID_BACK, wxWebFrame::OnBack)
    EVT_MENU(ID_FORWARD, wxWebFrame::OnForward)
    EVT_MENU(ID_STOP, wxWebFrame::OnStop)
    EVT_MENU(ID_RELOAD, wxWebFrame::OnReload)
    EVT_MENU(ID_MAKE_TEXT_LARGER, wxWebFrame::OnMakeTextLarger)
    EVT_MENU(ID_MAKE_TEXT_SMALLER, wxWebFrame::OnMakeTextSmaller)
    EVT_MENU(ID_GET_SOURCE, wxWebFrame::OnGetSource)
    EVT_MENU(ID_SET_SOURCE, wxWebFrame::OnSetSource)
    EVT_MENU(ID_BROWSE, wxWebFrame::OnBrowse)
    EVT_MENU(ID_EDIT, wxWebFrame::OnEdit)
    EVT_MENU(ID_RUN_SCRIPT, wxWebFrame::OnRunScript)
END_EVENT_TABLE()


wxWebFrame::wxWebFrame(const wxString& title) : 
        wxFrame(NULL, wxID_ANY, title, wxDefaultPosition, wxSize(600, 500)),
        m_checkBeforeLoad(false)
{

    // create a menu bar
    wxMenu *fileMenu = new wxMenu;
    fileMenu->Append(ID_NEW_WINDOW, _T("New Window\tCTRL+N"));
    fileMenu->Append(ID_LOADFILE, _T("Open File...\tCTRL+O"));
    fileMenu->Append(ID_LOADURL, _("Open Location...\tCTRL+L"));
    fileMenu->Append(wxID_EXIT, _T("E&xit\tAlt-X"), _T("Quit this program"));
    
    wxMenu *editMenu = new wxMenu;
    editMenu->Append(wxID_CUT, _T("Cut\tCTRL+X"));
    editMenu->Append(wxID_COPY, _T("Copy\tCTRL+C"));
    editMenu->Append(wxID_PASTE, _T("Paste\tCTRL+V"));
    
    wxMenu* viewMenu = new wxMenu;
    viewMenu->AppendRadioItem(ID_BROWSE, _("Browse"));
    viewMenu->AppendRadioItem(ID_EDIT, _("Edit"));
    viewMenu->AppendSeparator();
    viewMenu->Append(ID_STOP, _("Stop"));
    viewMenu->Append(ID_RELOAD, _("Reload Page"));
    viewMenu->Append(ID_MAKE_TEXT_SMALLER, _("Make Text Smaller\tCTRL+-"));
    viewMenu->Append(ID_MAKE_TEXT_LARGER, _("Make Text Bigger\tCTRL++"));
    viewMenu->AppendSeparator();
    viewMenu->Append(ID_GET_SOURCE, _("View Page Source"));
    viewMenu->AppendSeparator();
    
    m_debugMenu = new wxMenu;
    m_debugMenu->Append(ID_SET_SOURCE, _("Test SetPageSource"));
    m_debugMenu->Append(ID_RUN_SCRIPT, _("Test RunScript"));

    // the "About" item should be in the help menu
    wxMenu *helpMenu = new wxMenu;
    helpMenu->Append(wxID_ABOUT, _T("&About...\tF1"), _T("Show about dialog"));

    // now append the freshly created menu to the menu bar...
    wxMenuBar *menuBar = new wxMenuBar();
    menuBar->Append(fileMenu, _T("&File"));
    menuBar->Append(editMenu, _T("&Edit"));
    menuBar->Append(viewMenu, _T("&View"));
    menuBar->Append(helpMenu, _T("&Help"));

    // ... and attach this menu bar to the frame
    SetMenuBar(menuBar);
    
    wxToolBar* toolbar = CreateToolBar();
    toolbar->SetToolBitmapSize(wxSize(32, 32));
    
    wxBitmap back = wxArtProvider::GetBitmap(wxART_GO_BACK, wxART_TOOLBAR, wxSize(32,32));
    toolbar->AddTool(ID_BACK, back, wxT("Back"));
    
    wxBitmap forward = wxArtProvider::GetBitmap(wxART_GO_FORWARD, wxART_TOOLBAR, wxSize(32,32));
    toolbar->AddTool(ID_FORWARD, forward, wxT("Next"));

    addressBar = new wxTextCtrl(toolbar, ID_TEXTCTRL, _T(""), wxDefaultPosition, wxSize(400, -1), wxTE_PROCESS_ENTER);
    toolbar->AddControl(addressBar);
    
    searchCtrl = new wxSearchCtrl(toolbar, ID_SEARCHCTRL, _("Search"), wxDefaultPosition, wxSize(200, -1), wxTE_PROCESS_ENTER);
    toolbar->AddControl(searchCtrl);
    toolbar->Realize();
    
    SetToolBar(toolbar);

    // Create the wxWebView Window
    webview = new wxWebView((wxWindow*)this, 1001, wxDefaultPosition, wxSize(200, 200));
    webview->SetBackgroundColour(*wxWHITE);

    // create a status bar just for fun (by default with 1 pane only)
    CreateStatusBar(2);
}

wxWebFrame::~wxWebFrame()
{
    if (m_debugMenu && GetMenuBar()->FindMenu(_("&Debug")) == wxNOT_FOUND)
        delete m_debugMenu;
}

void wxWebFrame::ShowDebugMenu(bool show)
{
    int debugMenu = GetMenuBar()->FindMenu(_("&Debug"));
    if (show && debugMenu == wxNOT_FOUND) {
        int prevMenu = GetMenuBar()->FindMenu(_("&View"));
        if (prevMenu != wxNOT_FOUND)
            GetMenuBar()->Insert((size_t)prevMenu+1, m_debugMenu, _("&Debug"));
    }
    else if (!show && debugMenu != wxNOT_FOUND) {
        GetMenuBar()->Remove(debugMenu);
    }
}

// event handlers

void wxWebFrame::OnQuit(wxCommandEvent& WXUNUSED(event))
{
    // true is to force the frame to close
    Close(true);
}

void wxWebFrame::OnAbout(wxCommandEvent& WXUNUSED(event))
{
    wxString msg;
    msg.Printf(_T("This is the About dialog of the wxWebKit sample.\n")
               _T("Welcome to %s"), wxVERSION_STRING);

    wxMessageBox(msg, _T("About wxWebKit Sample"), wxOK | wxICON_INFORMATION, this);

}

void wxWebFrame::OnLoadFile(wxCommandEvent& WXUNUSED(event))
{
    wxFileDialog* dialog = new wxFileDialog(this, wxT("Choose a file"));
    if (dialog->ShowModal() == wxID_OK) {  
        wxString path = dialog->GetPath().Prepend(wxT("file://"));
        
        if (webview)
            webview->LoadURL(path);
    }
}

void wxWebFrame::OnLoadEvent(wxWebViewLoadEvent& event)
{
    if (GetStatusBar() != NULL){
        if (event.GetState() == wxWEBVIEW_LOAD_NEGOTIATING) {
            GetStatusBar()->SetStatusText(_("Contacting ") + event.GetURL());
        }
        else if (event.GetState() == wxWEBVIEW_LOAD_TRANSFERRING) {
            GetStatusBar()->SetStatusText(_("Loading ") + event.GetURL());
        }
        else if (event.GetState() == wxWEBVIEW_LOAD_ONLOAD_HANDLED) {
            GetStatusBar()->SetStatusText(_("Load complete."));
            addressBar->SetValue(event.GetURL());
            SetTitle(webview->GetPageTitle());
        }
        else if (event.GetState() == wxWEBVIEW_LOAD_FAILED) {
            GetStatusBar()->SetStatusText(_("Failed to load ") + event.GetURL());
        }
    }
}

void wxWebFrame::OnBeforeLoad(wxWebViewBeforeLoadEvent& myEvent)
{
    if (m_checkBeforeLoad) {
        int reply = wxMessageBox(_("Would you like to continue loading ") + myEvent.GetURL() + wxT("?"), _("Continue Loading?"), wxYES_NO); 
        if (reply == wxNO) {
            myEvent.Cancel();
        }
    }
}

void wxWebFrame::OnAddressBarEnter(wxCommandEvent& event)
{
    if (webview)
        webview->LoadURL(addressBar->GetValue());
}

void wxWebFrame::OnSearchCtrlEnter(wxCommandEvent& event)
{
    if (webview) {
        webview->LoadURL(wxString::Format(wxT("http://www.google.com/search?rls=en&q=%s&ie=UTF-8&oe=UTF-8"), searchCtrl->GetValue().wc_str()));
    }
}

void wxWebFrame::OnBack(wxCommandEvent& event)
{
    if (webview)
        webview->GoBack();
}

void wxWebFrame::OnForward(wxCommandEvent& event)
{
    if (webview)
        webview->GoForward();
}

void wxWebFrame::OnStop(wxCommandEvent& myEvent)
{
    if (webview)
        webview->Stop();
}

void wxWebFrame::OnReload(wxCommandEvent& myEvent)
{
    if (webview)
        webview->Reload();
}

void wxWebFrame::OnMakeTextLarger(wxCommandEvent& myEvent)
{
    if (webview) {
        if (webview->CanIncreaseTextSize())
            webview->IncreaseTextSize();
    }
}

void wxWebFrame::OnMakeTextSmaller(wxCommandEvent& myEvent)
{
    if (webview) {
        if (webview->CanDecreaseTextSize())
            webview->DecreaseTextSize();
    }
}

void wxWebFrame::OnGetSource(wxCommandEvent& myEvent)
{
    if (webview) {
        wxPageSourceViewFrame* wxWebFrame = new wxPageSourceViewFrame(webview->GetPageSource());
        wxWebFrame->Show();
    }
}

void wxWebFrame::OnSetSource(wxCommandEvent& event)
{
    if (webview)
        webview->SetPageSource(wxString(wxT("<p>Hello World!</p>")));
}

void wxWebFrame::OnBrowse(wxCommandEvent& event)
{
    if (webview)
        webview->MakeEditable(!event.IsChecked());
}

void wxWebFrame::OnEdit(wxCommandEvent& event)
{
    if (webview)
        webview->MakeEditable(event.IsChecked());
}

void wxWebFrame::OnRunScript(wxCommandEvent& myEvent){
    if (webview) {
        wxTextEntryDialog* dialog = new wxTextEntryDialog(this, _("Type in a JavaScript to exectute."));
        if (dialog->ShowModal() == wxID_OK)
            wxMessageBox(wxT("Result is: ") + webview->RunScript(dialog->GetValue()));
    
        dialog->Destroy();
    }
}
