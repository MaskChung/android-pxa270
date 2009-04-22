/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.editors.layout;

import com.android.ide.eclipse.common.EclipseUiHelper;
import com.android.ide.eclipse.editors.AndroidEditor;
import com.android.ide.eclipse.editors.EditorsPlugin;
import com.android.ide.eclipse.editors.descriptors.DocumentDescriptor;
import com.android.ide.eclipse.editors.layout.descriptors.LayoutDescriptors;
import com.android.ide.eclipse.editors.resources.manager.ResourceFolder;
import com.android.ide.eclipse.editors.resources.manager.ResourceManager;
import com.android.ide.eclipse.editors.uimodel.UiDocumentNode;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.gef.ui.parts.TreeViewer;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IShowEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.w3c.dom.Document;

/**
 * Multi-page form editor for /res/layout XML files. 
 */
public class LayoutEditor extends AndroidEditor implements IShowEditorInput, IPartListener {

    public static final String ID = "com.android.ide.eclipse.editors.layout.LayoutEditor"; //$NON-NLS-1$

    /** Root node of the UI element hierarchy */
    private UiDocumentNode mUiRootNode;
    /** Listener to update the root node if the resource framework changes */
    private Runnable mResourceRefreshListener;
    
    private GraphicalLayoutEditor mGraphicalEditor;
    private int mGraphicalEditorIndex;
    /** Implementation of the {@link IContentOutlinePage} for this editor */
    private UiContentOutlinePage mOutline;
    /** Custom implementation of {@link IPropertySheetPage} for this editor */
    private UiPropertySheetPage mPropertyPage;

   
    /**
     * Creates the form editor for resources XML files.
     */
    public LayoutEditor() {
        super();
        initUiRootNode();
    }

    /**
     * @return The root node of the UI element hierarchy
     */
    @Override
    public UiDocumentNode getUiRootNode() {
        return mUiRootNode;
    }

    // ---- Base Class Overrides ----

    @Override
    public void dispose() {
        if (mResourceRefreshListener != null) {
            EditorsPlugin.getDefault().removeResourceChangedListener(mResourceRefreshListener);
            mResourceRefreshListener = null;
        }
        getSite().getPage().removePartListener(this);

        super.dispose();
    }
    
    /**
     * Save the XML.
     * <p/>
     * The actual save operation is done in the super class by committing
     * all data to the XML model and then having the Structured XML Editor
     * save the XML.
     * <p/>
     * Here we just need to tell the graphical editor that the model has
     * been saved.
     */
    @Override
    public void doSave(IProgressMonitor monitor) {
        super.doSave(monitor);
        if (mGraphicalEditor != null) {
            mGraphicalEditor.doSave(monitor);
        }
    }
    
    /**
     * Returns whether the "save as" operation is supported by this editor.
     * <p/>
     * Save-As is a valid operation for the ManifestEditor since it acts on a
     * single source file. 
     *
     * @see IEditorPart
     */
    @Override
    public boolean isSaveAsAllowed() {
        return true;
    }

    /**
     * Create the various form pages.
     */
    @Override
    protected void createFormPages() {
        try {
            // The graphical layout editor is now enabled by default.
            // In case there's an issue we provide a way to disable it using an
            // env variable.
            if (System.getenv("ANDROID_DISABLE_LAYOUT") == null) {
                if (mGraphicalEditor == null) {
                    mGraphicalEditor = new GraphicalLayoutEditor(this);
                    mGraphicalEditorIndex = addPage(mGraphicalEditor, getEditorInput());
                    setPageText(mGraphicalEditorIndex, mGraphicalEditor.getTitle());
                } else {
                    mGraphicalEditor.reloadEditor();
                }

                // update the config based on the opened file.
                IEditorInput input = getEditorInput();
                if (input instanceof FileEditorInput) {
                    FileEditorInput fileInput = (FileEditorInput)input;
                    ResourceFolder resFolder = ResourceManager.getInstance().getResourceFolder(
                            fileInput.getFile());
                    if (resFolder != null) {
                        mGraphicalEditor.editNewFile(resFolder.getConfiguration());
                    }
                }

                // put in place the listener to handle layout recompute only when needed.
                getSite().getPage().addPartListener(this);
            }
        } catch (PartInitException e) {
            EditorsPlugin.log(e, "Error creating nested page"); //$NON-NLS-1$
        }
     }

    /* (non-java doc)
     * Change the tab/title name to include the name of the layout.
     */
    @Override
    protected void setInput(IEditorInput input) {
        super.setInput(input);
        handleNewInput(input);
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.ui.part.EditorPart#setInputWithNotify(org.eclipse.ui.IEditorInput)
     */
    @Override
    protected void setInputWithNotify(IEditorInput input) {
        super.setInputWithNotify(input);
        handleNewInput(input);
    }
    
    /**
     * Called to replace the current {@link IEditorInput} with another one.
     * <p/>This is used when {@link MatchingStrategy} returned <code>true</code> which means we're
     * opening a different configuration of the same layout.
     */
    public void showEditorInput(IEditorInput editorInput) {
        // save the current editor input.
        doSave(new NullProgressMonitor());
        
        // get the current page
        int currentPage = getActivePage();
        
        // remove the pages, except for the graphical editor, which will be dynamically adapted
        // to the new model.
        // page after the graphical editor:
        int count = getPageCount();
        for (int i = count - 1 ; i > mGraphicalEditorIndex ; i--) {
            removePage(i);
        }
        // pages before the graphical editor
        for (int i = mGraphicalEditorIndex - 1 ; i >= 0 ; i--) {
            removePage(i);
        }
        
        // set the current input.
        setInputWithNotify(editorInput);
        
        // re-create or reload the pages with the default page shown as the previous active page.
        createAndroidPages();
        selectDefaultPage(Integer.toString(currentPage));

        // update the outline
        if (mOutline != null && mGraphicalEditor != null) {
            mOutline.reloadModel();
        }
    }
    
    /**
     * Processes the new XML Model, which XML root node is given.
     * 
     * @param xml_doc The XML document, if available, or null if none exists.
     */
    @Override
    protected void xmlModelChanged(Document xml_doc) {
        mUiRootNode.loadFromXmlNode(xml_doc);

        // update the model first, since it is used by the viewers.
        super.xmlModelChanged(xml_doc);
        
        if (mGraphicalEditor != null) {
            mGraphicalEditor.onXmlModelChanged();
        }
        
        if (mOutline != null) {
            mOutline.reloadModel();
        }
    }
    
    /* (non-java doc)
     * Returns the IContentOutlinePage when asked for it.
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object getAdapter(Class adapter) {
        // for the outline, force it to come from the Graphical Editor.
        // This fixes the case where a layout file is opened in XML view first and the outline
        // gets stuck in the XML outline.
        if (IContentOutlinePage.class == adapter && mGraphicalEditor != null) {
            if (mOutline == null) {
                mOutline = new UiContentOutlinePage(mGraphicalEditor, new TreeViewer());
            }
            
            return mOutline;
        }
        
        if (IPropertySheetPage.class == adapter && mGraphicalEditor != null) {
            if (mPropertyPage == null) {
                mPropertyPage = new UiPropertySheetPage();
            }
            
            return mPropertyPage;
        }

        // return default
        return super.getAdapter(adapter);
    }
    
    @Override
    protected void pageChange(int newPageIndex) {
        super.pageChange(newPageIndex);
        
        if (mGraphicalEditor != null && newPageIndex == mGraphicalEditorIndex) {
            mGraphicalEditor.activated();
        }
    }
    
    // ----- IPartListener Methods ----
    
    public void partActivated(IWorkbenchPart part) {
        if (part == this) {
            if (mGraphicalEditor != null && getActivePage() == mGraphicalEditorIndex) {
                mGraphicalEditor.activated();
            }
        }
    }

    public void partBroughtToTop(IWorkbenchPart part) {
        partActivated(part);
    }

    public void partClosed(IWorkbenchPart part) {
        // pass
    }

    public void partDeactivated(IWorkbenchPart part) {
        // pass
    }

    public void partOpened(IWorkbenchPart part) {
        EclipseUiHelper.showView(EclipseUiHelper.CONTENT_OUTLINE_VIEW_ID, false /* activate */);
        EclipseUiHelper.showView(EclipseUiHelper.PROPERTY_SHEET_VIEW_ID, false /* activate */);
    }
    
    // ---- Local Methods ----
    
    /**
     * Returns true if the Graphics editor page is visible.
     * This <b>must</b> be called from the UI thread.
     */
    boolean isGraphicalEditorActive() {
        IWorkbenchPartSite workbenchSite = getSite();
        IWorkbenchPage workbenchPage = workbenchSite.getPage();
        
        // check if the editor is visible in the workbench page
        if (workbenchPage.isPartVisible(this)) {
            // and then if the page of the editor is visible (not to be confused with
            // the workbench page)
            return mGraphicalEditorIndex == getActivePage();
        }
        
        return false;
    }

    /**
     * Creates the initial UI Root Node, including the known mandatory elements.
     */
    private void initUiRootNode() {
        // The root UI node is always created, even if there's no corresponding XML node.
        if (mUiRootNode == null) {
            DocumentDescriptor desc = LayoutDescriptors.getInstance().getDescriptor();
            mUiRootNode = (UiDocumentNode) desc.createUiNode();
            mUiRootNode.setEditor(this);

            // Add a listener to refresh the root node if the resource framework changes
            // by forcing it to parse its own XML
            mResourceRefreshListener = new Runnable() {
                public void run() {
                    commitPages(false /* onSave */);

                    mUiRootNode.reloadFromXmlNode(mUiRootNode.getXmlDocument());
                    
                    if (mOutline != null) {
                        mOutline.reloadModel();
                    }
                    
                    if (mGraphicalEditor != null) {
                        mGraphicalEditor.recomputeLayout();
                    }
                }
            };
            EditorsPlugin.getDefault().addResourceChangedListener(mResourceRefreshListener);
            mResourceRefreshListener.run();
        }
    }
    

    /**
     * Handles a new input, and update the part name.
     * @param input the new input.
     */
    private void handleNewInput(IEditorInput input) {
        if (input instanceof FileEditorInput) {
            FileEditorInput fileInput = (FileEditorInput) input;
            IFile file = fileInput.getFile();
            setPartName(String.format("%1$s",
                    file.getName()));
        }
    }
}
