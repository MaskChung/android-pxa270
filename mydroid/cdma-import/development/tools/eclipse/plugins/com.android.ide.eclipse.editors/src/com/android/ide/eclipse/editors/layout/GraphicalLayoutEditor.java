/*
 * Copyright (C) 2008 The Android Open Source Project
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

import com.android.ide.eclipse.common.AndroidConstants;
import com.android.ide.eclipse.common.resources.ResourceType;
import com.android.ide.eclipse.editors.EditorsPlugin;
import com.android.ide.eclipse.editors.IconFactory;
import com.android.ide.eclipse.editors.EditorsPlugin.LayoutBridge;
import com.android.ide.eclipse.editors.layout.LayoutReloadMonitor.ILayoutReloadListener;
import com.android.ide.eclipse.editors.layout.parts.UiElementsEditPartFactory;
import com.android.ide.eclipse.editors.resources.configurations.CountryCodeQualifier;
import com.android.ide.eclipse.editors.resources.configurations.FolderConfiguration;
import com.android.ide.eclipse.editors.resources.configurations.KeyboardStateQualifier;
import com.android.ide.eclipse.editors.resources.configurations.LanguageQualifier;
import com.android.ide.eclipse.editors.resources.configurations.NavigationMethodQualifier;
import com.android.ide.eclipse.editors.resources.configurations.NetworkCodeQualifier;
import com.android.ide.eclipse.editors.resources.configurations.PixelDensityQualifier;
import com.android.ide.eclipse.editors.resources.configurations.RegionQualifier;
import com.android.ide.eclipse.editors.resources.configurations.ScreenDimensionQualifier;
import com.android.ide.eclipse.editors.resources.configurations.ScreenOrientationQualifier;
import com.android.ide.eclipse.editors.resources.configurations.TextInputMethodQualifier;
import com.android.ide.eclipse.editors.resources.configurations.TouchScreenQualifier;
import com.android.ide.eclipse.editors.resources.configurations.KeyboardStateQualifier.KeyboardState;
import com.android.ide.eclipse.editors.resources.configurations.NavigationMethodQualifier.NavigationMethod;
import com.android.ide.eclipse.editors.resources.configurations.ScreenOrientationQualifier.ScreenOrientation;
import com.android.ide.eclipse.editors.resources.configurations.TextInputMethodQualifier.TextInputMethod;
import com.android.ide.eclipse.editors.resources.configurations.TouchScreenQualifier.TouchScreenType;
import com.android.ide.eclipse.editors.resources.manager.ProjectResources;
import com.android.ide.eclipse.editors.resources.manager.ResourceFile;
import com.android.ide.eclipse.editors.resources.manager.ResourceFolderType;
import com.android.ide.eclipse.editors.resources.manager.ResourceManager;
import com.android.ide.eclipse.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.editors.wizards.ConfigurationSelector.DensityVerifier;
import com.android.ide.eclipse.editors.wizards.ConfigurationSelector.DimensionVerifier;
import com.android.ide.eclipse.editors.wizards.ConfigurationSelector.LanguageRegionVerifier;
import com.android.ide.eclipse.editors.wizards.ConfigurationSelector.MobileCodeVerifier;
import com.android.layoutlib.api.ILayoutLog;
import com.android.layoutlib.api.ILayoutResult;
import com.android.layoutlib.api.IResourceValue;
import com.android.layoutlib.api.IStyleResourceValue;
import com.android.layoutlib.api.ILayoutResult.ILayoutViewInfo;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.DefaultEditDomain;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.SelectionManager;
import org.eclipse.gef.dnd.TemplateTransferDragSourceListener;
import org.eclipse.gef.dnd.TemplateTransferDropTargetListener;
import org.eclipse.gef.editparts.ScalableFreeformRootEditPart;
import org.eclipse.gef.palette.PaletteRoot;
import org.eclipse.gef.ui.parts.GraphicalEditor;
import org.eclipse.gef.ui.parts.SelectionSynchronizer;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Graphical layout editor, based on GEF.
 */
public class GraphicalLayoutEditor extends GraphicalEditor/*WithPalette*/
        implements ILayoutReloadListener {
    
    private final static String THEME_SEPARATOR = "----------"; //$NON-NLS-1$

    /** Reference to the layout editor */
    private final LayoutEditor mLayoutEditor;
    
    /** reference to the file being edited. */
    private IFile mEditedFile;

    private Clipboard mClipboard;
    private Composite mParent;

    private Text mCountry;
    private Text mNetwork;
    private Combo mLanguage;
    private Combo mRegion;
    private Combo mOrientation;
    private Text mDensity;
    private Combo mTouch;
    private Combo mKeyboard;
    private Combo mTextInput;
    private Combo mNavigation;
    private Text mSize1;
    private Text mSize2;
    private Combo mThemeCombo;
    private Button mCreateButton;
    
    private Label mCountryIcon;
    private Label mNetworkIcon;
    private Label mLanguageIcon;
    private Label mRegionIcon;
    private Label mOrientationIcon;
    private Label mDensityIcon;
    private Label mTouchIcon;
    private Label mKeyboardIcon;
    private Label mTextInputIcon;
    private Label mNavigationIcon;
    private Label mSizeIcon;
    
    private Label mCurrentLayoutLabel;

    private Image mWarningImage;
    private Image mMatchImage;
    private Image mErrorImage;
    
    /** The {@link FolderConfiguration} representing the state of the UI controls */
    private FolderConfiguration mCurrentConfig = new FolderConfiguration();
    /** The {@link FolderConfiguration} being edited. */
    private FolderConfiguration mEditedConfig;
    
    private Map<String, Map<String, IResourceValue>> mConfiguredFrameworkRes;
    private Map<String, Map<String, IResourceValue>> mConfiguredProjectRes;
    private ProjectCallback mProjectCallback;
    private ILayoutLog mLogger;

    private boolean mNeedsRecompute = false;
    private int mPlatformThemeCount = 0;
    private boolean mDisableUpdates = false;

    private Runnable mFrameworkResourceChangeListener = new Runnable() {
        public void run() {
            // because the SDK changed we must reset the configured framework resource.
            mConfiguredFrameworkRes = null;
            
            updateUIFromResources();
            mThemeCombo.getParent().layout();

            // updateUiFromFramework will reset language/region combo, so we must call
            // setConfiguration after, or the settext on language/region will be lost.
            if (mEditedConfig != null) {
                setConfiguration(mEditedConfig);
            }

            // make sure we remove the custom view loader, since its parent class loader is the
            // bridge class loader.
            mProjectCallback = null;
            
            recomputeLayout();
        }
    };
    
    private final Runnable mConditionalRecomputeRunnable = new Runnable() {
        public void run() {
            if (mLayoutEditor.isGraphicalEditorActive()) {
                recomputeLayout();
            } else {
                mNeedsRecompute = true;
            }
        }
    };
    
    private final Runnable mUiUpdateFromResourcesRunnable = new Runnable() {
        public void run() {
            updateUIFromResources();
            mThemeCombo.getParent().layout();
        }
    };

    public GraphicalLayoutEditor(LayoutEditor layoutEditor) {
        mLayoutEditor = layoutEditor;
        setEditDomain(new DefaultEditDomain(this));
        setPartName("Layout");
        
        IconFactory factory = IconFactory.getInstance();
        mWarningImage = factory.getIcon("warning"); //$NON-NLS-1$
        mMatchImage = factory.getIcon("match"); //$NON-NLS-1$
        mErrorImage = factory.getIcon("error"); //$NON-NLS-1$
        
        EditorsPlugin.getDefault().addResourceChangedListener(mFrameworkResourceChangeListener);
    }
    
    // ------------------------------------
    // Methods overridden from base classes
    //------------------------------------
    
    @Override
    public void createPartControl(Composite parent) {
        mParent = parent;
        GridLayout gl;
        GridData gd;

        mClipboard = new Clipboard(parent.getDisplay());
        
        parent.setLayout(gl = new GridLayout(1, false));
        gl.marginHeight = gl.marginWidth = 0;
        
        // create the top part for the configuration control
        int cols = 10;
        
        Composite topParent = new Composite(parent, SWT.NONE);
        topParent.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        topParent.setLayout(gl = new GridLayout(cols, false));

        new Label(topParent, SWT.NONE).setText("MCC");
        mCountryIcon = createControlComposite(topParent, true /* grab_horizontal */);
        mCountry = new Text(mCountryIcon.getParent(), SWT.BORDER);
        mCountry.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mCountry.addVerifyListener(new MobileCodeVerifier());
        mCountry.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                onCountryCodeChange();
            }
        });
        mCountry.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                onCountryCodeChange();
            }
        });

        new Label(topParent, SWT.NONE).setText("MNC");
        mNetworkIcon = createControlComposite(topParent, true /* grab_horizontal */);
        mNetwork = new Text(mNetworkIcon.getParent(), SWT.BORDER);
        mNetwork.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mNetwork.addVerifyListener(new MobileCodeVerifier());
        mNetwork.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                onNetworkCodeChange();
            }
        });
        mNetwork.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                onNetworkCodeChange();
            }
        });

        new Label(topParent, SWT.NONE).setText("Lang");
        mLanguageIcon = createControlComposite(topParent, true /* grab_horizontal */);
        mLanguage = new Combo(mLanguageIcon.getParent(), SWT.DROP_DOWN);
        mLanguage.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mLanguage.addVerifyListener(new LanguageRegionVerifier());
        mLanguage.addSelectionListener(new SelectionListener() {
            public void widgetDefaultSelected(SelectionEvent e) {
                onLanguageChange();
            }
            public void widgetSelected(SelectionEvent e) {
                onLanguageChange();
            }
        });
        mLanguage.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                onLanguageChange();
            }
        });

        new Label(topParent, SWT.NONE).setText("Region");
        mRegionIcon = createControlComposite(topParent, true /* grab_horizontal */);
        mRegion = new Combo(mRegionIcon.getParent(), SWT.DROP_DOWN);
        mRegion.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mRegion.addVerifyListener(new LanguageRegionVerifier());
        mRegion.addSelectionListener(new SelectionListener() {
            public void widgetDefaultSelected(SelectionEvent e) {
                onRegionChange();
            }
            public void widgetSelected(SelectionEvent e) {
                onRegionChange();
            }
        });
        mRegion.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                onRegionChange();
            }
        });

        new Label(topParent, SWT.NONE).setText("Orient");
        mOrientationIcon = createControlComposite(topParent, true /* grab_horizontal */);
        mOrientation = new Combo(mOrientationIcon.getParent(), SWT.DROP_DOWN | SWT.READ_ONLY);
        ScreenOrientation[] soValues = ScreenOrientation.values();
        mOrientation.add("(Default)");
        for (ScreenOrientation value : soValues) {
            mOrientation.add(value.getDisplayValue());
        }
        mOrientation.select(0);
        mOrientation.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mOrientation.addSelectionListener(new SelectionAdapter() {
           @Override
            public void widgetSelected(SelectionEvent e) {
               onOrientationChange();
            } 
        });
        
        new Label(topParent, SWT.NONE).setText("Density");
        mDensityIcon = createControlComposite(topParent, true /* grab_horizontal */);
        mDensity = new Text(mDensityIcon.getParent(), SWT.BORDER);
        mDensity.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mDensity.addVerifyListener(new DensityVerifier());
        mDensity.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                onDensityChange();
            }
        });
        mDensity.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                onDensityChange();
            }
        });

        new Label(topParent, SWT.NONE).setText("Touch");
        mTouchIcon = createControlComposite(topParent, true /* grab_horizontal */);
        mTouch = new Combo(mTouchIcon.getParent(), SWT.DROP_DOWN | SWT.READ_ONLY);
        TouchScreenType[] tstValues = TouchScreenType.values();
        mTouch.add("(Default)");
        for (TouchScreenType value : tstValues) {
            mTouch.add(value.getDisplayValue());
        }
        mTouch.select(0);
        mTouch.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mTouch.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onTouchChange();
            }
        });

        new Label(topParent, SWT.NONE).setText("Keybrd");
        mKeyboardIcon = createControlComposite(topParent, true /* grab_horizontal */);
        mKeyboard = new Combo(mKeyboardIcon.getParent(), SWT.DROP_DOWN | SWT.READ_ONLY);
        KeyboardState[] ksValues = KeyboardState.values();
        mKeyboard.add("(Default)");
        for (KeyboardState value : ksValues) {
            mKeyboard.add(value.getDisplayValue());
        }
        mKeyboard.select(0);
        mKeyboard.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mKeyboard.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onKeyboardChange();
            }
        });

        new Label(topParent, SWT.NONE).setText("Input");
        mTextInputIcon = createControlComposite(topParent, true /* grab_horizontal */);
        mTextInput = new Combo(mTextInputIcon.getParent(), SWT.DROP_DOWN | SWT.READ_ONLY);
        TextInputMethod[] timValues = TextInputMethod.values();
        mTextInput.add("(Default)");
        for (TextInputMethod value : timValues) {
            mTextInput.add(value.getDisplayValue());
        }
        mTextInput.select(0);
        mTextInput.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mTextInput.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onTextInputChange();
            }
        });

        new Label(topParent, SWT.NONE).setText("Nav");
        mNavigationIcon = createControlComposite(topParent, true /* grab_horizontal */);
        mNavigation = new Combo(mNavigationIcon.getParent(), SWT.DROP_DOWN | SWT.READ_ONLY);
        NavigationMethod[] nValues = NavigationMethod.values();
        mNavigation.add("(Default)");
        for (NavigationMethod value : nValues) {
            mNavigation.add(value.getDisplayValue());
        }
        mNavigation.select(0);
        mNavigation.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mNavigation.addSelectionListener(new SelectionAdapter() {
            @Override
             public void widgetSelected(SelectionEvent e) {
                onNavigationChange();
            } 
        });

        Composite labelParent = new Composite(topParent, SWT.NONE);
        labelParent.setLayout(gl = new GridLayout(8, false));
        gl.marginWidth = gl.marginHeight = 0;
        labelParent.setLayoutData(gd = new GridData(GridData.FILL_HORIZONTAL));
        gd.horizontalSpan = cols;
        
        new Label(labelParent, SWT.NONE).setText("Editing config:");
        mCurrentLayoutLabel = new Label(labelParent, SWT.NONE);
        mCurrentLayoutLabel.setLayoutData(gd = new GridData(GridData.FILL_HORIZONTAL));
        gd.widthHint = 50;

        new Label(labelParent, SWT.NONE).setText("Size");
        mSizeIcon = createControlComposite(labelParent, false);
        Composite sizeParent = new Composite(mSizeIcon.getParent(), SWT.NONE);
        sizeParent.setLayout(gl = new GridLayout(3, false));
        gl.marginWidth = gl.marginHeight = 0;
        gl.horizontalSpacing = 0;
        
        mSize1 = new Text(sizeParent, SWT.BORDER);
        mSize1.setLayoutData(gd = new GridData());
        gd.widthHint = 30;
        new Label(sizeParent, SWT.NONE).setText("x");
        mSize2 = new Text(sizeParent, SWT.BORDER);
        mSize2.setLayoutData(gd = new GridData());
        gd.widthHint = 30;
        
        DimensionVerifier verifier = new DimensionVerifier();
        mSize1.addVerifyListener(verifier);
        mSize2.addVerifyListener(verifier);
        
        SelectionListener sl = new SelectionListener() {
            public void widgetDefaultSelected(SelectionEvent e) {
                onSizeChange();
            }
            public void widgetSelected(SelectionEvent e) {
                onSizeChange();
            }
        };
        
        mSize1.addSelectionListener(sl);
        mSize2.addSelectionListener(sl);
        
        ModifyListener sizeModifyListener = new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                onSizeChange();
            }
        };

        mSize1.addModifyListener(sizeModifyListener);
        mSize2.addModifyListener(sizeModifyListener);

        // first separator
        Label separator = new Label(labelParent, SWT.SEPARATOR | SWT.VERTICAL);
        separator.setLayoutData(gd = new GridData(
                GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_VERTICAL));
        gd.heightHint = 0;
        
        mThemeCombo = new Combo(labelParent, SWT.READ_ONLY | SWT.DROP_DOWN);
        mThemeCombo.setEnabled(false);
        updateUIFromResources();
        mThemeCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onThemeChange();
            }
        });

        // second separator
        separator = new Label(labelParent, SWT.SEPARATOR | SWT.VERTICAL);
        separator.setLayoutData(gd = new GridData(
                GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_VERTICAL));
        gd.heightHint = 0;

        mCreateButton = new Button(labelParent, SWT.PUSH | SWT.FLAT);
        mCreateButton.setText("Create...");
        mCreateButton.setEnabled(false);
        mCreateButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                LayoutCreatorDialog dialog = new LayoutCreatorDialog(mCreateButton.getShell(),
                        mEditedFile.getName(), mCurrentConfig);
                if (dialog.open() == Dialog.OK) {
                    final FolderConfiguration config = new FolderConfiguration();
                    dialog.getConfiguration(config);
                    
                    createAlternateLayout(config);
                }
            }
        });
        
        // create a new composite that will contain the standard editor controls.
        Composite editorParent = new Composite(parent, SWT.NONE);
        editorParent.setLayoutData(new GridData(GridData.FILL_BOTH));
        editorParent.setLayout(new FillLayout());
        super.createPartControl(editorParent);
    }

    @Override
    public void dispose() {
        if (mFrameworkResourceChangeListener != null) {
            EditorsPlugin.getDefault().removeResourceChangedListener(
                    mFrameworkResourceChangeListener);
            mFrameworkResourceChangeListener = null;
        }
        
        LayoutReloadMonitor.getMonitor().removeListener(mEditedFile.getProject(), this);

        if (mClipboard != null) {
            mClipboard.dispose();
            mClipboard = null;
        }
        
        super.dispose();
    }
    
    /* (non-Javadoc)
     * Creates the palette root.
     */
    protected PaletteRoot getPaletteRoot() {
        return PaletteFactory.createPaletteRoot();
    }
    
    public Clipboard getClipboard() {
        return mClipboard;
    }

    /**
     * Save operation in the Graphical Layout Editor.
     * <p/>
     * In our workflow, the model is owned by the Structured XML Editor.
     * The graphical layout editor just displays it -- thus we don't really
     * save anything here.
     * <p/>
     * This must NOT call the parent editor part. At the contrary, the parent editor
     * part will call this *after* having done the actual save operation.
     * <p/>
     * The only action this editor must do is mark the undo command stack as
     * being no longer dirty.
     */
    @Override
    public void doSave(IProgressMonitor monitor) {
        getCommandStack().markSaveLocation();
        firePropertyChange(PROP_DIRTY);
    }
    
    /* (non-javadoc)
     * Configure the graphical viewer before it receives its contents.
     */
    @Override
    protected void configureGraphicalViewer() {
        super.configureGraphicalViewer();
        
        GraphicalViewer viewer = getGraphicalViewer();
        viewer.setEditPartFactory(new UiElementsEditPartFactory(mParent.getDisplay()));
        viewer.setRootEditPart(new ScalableFreeformRootEditPart());

        // TODO: viewer.setKeyHandler()
        // TODO: custom ContextMenuProvider => viewer.setContextMenu & registerContextMenu

        viewer.addDragSourceListener(new TemplateTransferDragSourceListener(viewer));
        viewer.addDropTargetListener(new TemplateTransferDropTargetListener(viewer));
    }

    /* (non-javadoc)
     * Set the contents of the GraphicalViewer after it has been created.
     */
    @Override
    protected void initializeGraphicalViewer() {
        GraphicalViewer viewer = getGraphicalViewer();
        viewer.setContents(getModel());
        
        IEditorInput input = getEditorInput();
        if (input instanceof FileEditorInput) {
            FileEditorInput fileInput = (FileEditorInput)input;
            mEditedFile = fileInput.getFile();
            
            updateUIFromResources();

            LayoutReloadMonitor.getMonitor().addListener(mEditedFile.getProject(), this);
        } else {
            // really this shouldn't happen! Log it in case it happens
            mEditedFile = null;
            EditorsPlugin.log(IStatus.ERROR, "Input is not of type FileEditorInput: %1$s",
                    input.toString());
        }
    }
    
    //--------------
    // Local methods
    //--------------
    
    public LayoutEditor getLayoutEditor() {
        return mLayoutEditor;
    }

    /**
     * Sets the UI for the edition of a new file.
     * @param configuration the configuration of the new file.
     */
    public void editNewFile(FolderConfiguration configuration) {
        // update the configuration UI
        setConfiguration(configuration);
        
        // enable the create button if the current and edited config are not equals
        mCreateButton.setEnabled(mEditedConfig.equals(mCurrentConfig) == false);
    }
    
    public Rectangle getBounds() {
        ScreenOrientation orientation = null;
        if (mOrientation.getSelectionIndex() == 0) {
            orientation = ScreenOrientation.PORTRAIT;
        } else {
            orientation = ScreenOrientation.getByIndex(
                    mOrientation.getSelectionIndex() - 1);
        }

        int s1, s2;

        // get the size from the UI controls. If it fails, revert to default values.
        try {
            s1 = Integer.parseInt(mSize1.getText().trim());
        } catch (NumberFormatException e) {
            s1 = 480;
        }

        try {
            s2 = Integer.parseInt(mSize2.getText().trim());
        } catch (NumberFormatException e) {
            s2 = 320;
        }

        // make sure s1 is bigger than s2
        if (s1 < s2) {
            int tmp = s1;
            s1 = s2;
            s2 = tmp;
        }
            
        switch (orientation) {
            default:
            case PORTRAIT:
                return new Rectangle(0, 0, s2, s1);
            case LANDSCAPE:
                return new Rectangle(0, 0, s1, s2);
            case SQUARE:
                return new Rectangle(0, 0, s1, s1);
        }
    }

    /**
     * Reloads this editor, by getting the new model from the {@link LayoutEditor}.
     */
    void reloadEditor() {
        GraphicalViewer viewer = getGraphicalViewer();
        viewer.setContents(getModel());

        IEditorInput input = mLayoutEditor.getEditorInput();
        setInput(input);
        
        if (input instanceof FileEditorInput) {
            FileEditorInput fileInput = (FileEditorInput)input;
            mEditedFile = fileInput.getFile();
        } else {
            // really this shouldn't happen! Log it in case it happens
            mEditedFile = null;
            EditorsPlugin.log(IStatus.ERROR, "Input is not of type FileEditorInput: %1$s",
                    input.toString());
        }
    }
    
    /**
     * Update the layout editor when the Xml model is changed.
     */
    void onXmlModelChanged() {
        GraphicalViewer viewer = getGraphicalViewer();
        
        // try to preserve the selection before changing the content
        SelectionManager selMan = viewer.getSelectionManager();
        ISelection selection = selMan.getSelection();
        
        try {
            viewer.setContents(getModel());
        } finally {
            selMan.setSelection(selection);
        }       
        
        if (mLayoutEditor.isGraphicalEditorActive()) {
            recomputeLayout();
        } else {
            mNeedsRecompute = true;
        }
    }
    
    /**
     * Update the UI controls state with a given {@link FolderConfiguration}.
     * <p/>If a qualifier is not present in the {@link FolderConfiguration} object, the UI control
     * is not modified. However if the value in the control is not the default value, a warning
     * icon is showed.
     */
    void setConfiguration(FolderConfiguration config) {
        mDisableUpdates = true; // we do not want to trigger onXXXChange when setting new values in the widgets.

        mEditedConfig = config;
        mConfiguredFrameworkRes = mConfiguredProjectRes = null;

        mCountryIcon.setImage(mMatchImage);
        CountryCodeQualifier countryQualifier = config.getCountryCodeQualifier();
        if (countryQualifier != null) {
            mCountry.setText(String.format("%1$d", countryQualifier.getCode()));
            mCurrentConfig.setCountryCodeQualifier(countryQualifier);
        } else if (mCountry.getText().length() > 0) {
            mCountryIcon.setImage(mWarningImage);
        }
        
        mNetworkIcon.setImage(mMatchImage);
        NetworkCodeQualifier networkQualifier = config.getNetworkCodeQualifier();
        if (networkQualifier != null) {
            mNetwork.setText(String.format("%1$d", networkQualifier.getCode()));
            mCurrentConfig.setNetworkCodeQualifier(networkQualifier);
        } else if (mNetwork.getText().length() > 0) {
            mNetworkIcon.setImage(mWarningImage);
        }
        
        mLanguageIcon.setImage(mMatchImage);
        LanguageQualifier languageQualifier = config.getLanguageQualifier();
        if (languageQualifier != null) {
            mLanguage.setText(languageQualifier.getValue());
            mCurrentConfig.setLanguageQualifier(languageQualifier);
        } else if (mLanguage.getText().length() > 0) {
            mLanguageIcon.setImage(mWarningImage);
        }
        
        mRegionIcon.setImage(mMatchImage);
        RegionQualifier regionQualifier = config.getRegionQualifier();
        if (regionQualifier != null) {
            mRegion.setText(regionQualifier.getValue());
            mCurrentConfig.setRegionQualifier(regionQualifier);
        } else if (mRegion.getText().length() > 0) {
            mRegionIcon.setImage(mWarningImage);
        }
        
        mOrientationIcon.setImage(mMatchImage);
        ScreenOrientationQualifier orientationQualifier = config.getScreenOrientationQualifier();
        if (orientationQualifier != null) {
            mOrientation.select(
                    ScreenOrientation.getIndex(orientationQualifier.getValue()) + 1);
            mCurrentConfig.setScreenOrientationQualifier(orientationQualifier);
        } else if (mOrientation.getSelectionIndex() != 0) {
            mOrientationIcon.setImage(mWarningImage);
        }
        
        mDensityIcon.setImage(mMatchImage);
        PixelDensityQualifier densityQualifier = config.getPixelDensityQualifier();
        if (densityQualifier != null) {
            mDensity.setText(String.format("%1$d", densityQualifier.getValue()));
            mCurrentConfig.setPixelDensityQualifier(densityQualifier);
        } else if (mDensity.getText().length() > 0) {
            mDensityIcon.setImage(mWarningImage);
        }
        
        mTouchIcon.setImage(mMatchImage);
        TouchScreenQualifier touchQualifier = config.getTouchTypeQualifier();
        if (touchQualifier != null) {
            mTouch.select(TouchScreenType.getIndex(touchQualifier.getValue()) + 1);
            mCurrentConfig.setTouchTypeQualifier(touchQualifier);
        } else if (mTouch.getSelectionIndex() != 0) {
            mTouchIcon.setImage(mWarningImage);
        }
        
        mKeyboardIcon.setImage(mMatchImage);
        KeyboardStateQualifier keyboardQualifier = config.getKeyboardStateQualifier();
        if (keyboardQualifier != null) {
            mKeyboard.select(KeyboardState.getIndex(keyboardQualifier.getValue()) + 1);
            mCurrentConfig.setKeyboardStateQualifier(keyboardQualifier);
        } else if (mKeyboard.getSelectionIndex() != 0) {
            mKeyboardIcon.setImage(mWarningImage);
        }

        mTextInputIcon.setImage(mMatchImage);
        TextInputMethodQualifier inputQualifier = config.getTextInputMethodQualifier();
        if (inputQualifier != null) {
            mTextInput.select(TextInputMethod.getIndex(inputQualifier.getValue()) + 1);
            mCurrentConfig.setTextInputMethodQualifier(inputQualifier);
        } else if (mTextInput.getSelectionIndex() != 0) {
            mTextInputIcon.setImage(mWarningImage);
        }
        
        mNavigationIcon.setImage(mMatchImage);
        NavigationMethodQualifier navigationQualifiter = config.getNavigationMethodQualifier();
        if (navigationQualifiter != null) {
            mNavigation.select(
                    NavigationMethod.getIndex(navigationQualifiter.getValue()) + 1);
            mCurrentConfig.setNavigationMethodQualifier(navigationQualifiter);
        } else if (mNavigation.getSelectionIndex() != 0) {
            mNavigationIcon.setImage(mWarningImage);
        }
        
        mSizeIcon.setImage(mMatchImage);
        ScreenDimensionQualifier sizeQualifier = config.getScreenDimensionQualifier();
        if (sizeQualifier != null) {
            mSize1.setText(String.format("%1$d", sizeQualifier.getValue1()));
            mSize2.setText(String.format("%1$d", sizeQualifier.getValue2()));
            mCurrentConfig.setScreenDimensionQualifier(sizeQualifier);
        } else if (mSize1.getText().length() > 0 && mSize2.getText().length() > 0) {
            mSizeIcon.setImage(mWarningImage);
        }
        
        // update the string showing the folder name
        String current = config.toDisplayString();
        mCurrentLayoutLabel.setText(current != null ? current : "(Default)");
        
        mDisableUpdates = false;
    }
    
    /**
     * Displays an error icon in front of all the non-null qualifiers.
     */
    void displayConfigError() {
        mCountryIcon.setImage(mMatchImage);
        CountryCodeQualifier countryQualifier = mCurrentConfig.getCountryCodeQualifier();
        if (countryQualifier != null) {
            mCountryIcon.setImage(mErrorImage);
        }
        
        mNetworkIcon.setImage(mMatchImage);
        NetworkCodeQualifier networkQualifier = mCurrentConfig.getNetworkCodeQualifier();
        if (networkQualifier != null) {
            mNetworkIcon.setImage(mErrorImage);
        }
        
        mLanguageIcon.setImage(mMatchImage);
        LanguageQualifier languageQualifier = mCurrentConfig.getLanguageQualifier();
        if (languageQualifier != null) {
            mLanguageIcon.setImage(mErrorImage);
        }
        
        mRegionIcon.setImage(mMatchImage);
        RegionQualifier regionQualifier = mCurrentConfig.getRegionQualifier();
        if (regionQualifier != null) {
            mRegionIcon.setImage(mErrorImage);
        }
        
        mOrientationIcon.setImage(mMatchImage);
        ScreenOrientationQualifier orientationQualifier =
            mCurrentConfig.getScreenOrientationQualifier();
        if (orientationQualifier != null) {
            mOrientationIcon.setImage(mErrorImage);
        }
        
        mDensityIcon.setImage(mMatchImage);
        PixelDensityQualifier densityQualifier = mCurrentConfig.getPixelDensityQualifier();
        if (densityQualifier != null) {
            mDensityIcon.setImage(mErrorImage);
        }
        
        mTouchIcon.setImage(mMatchImage);
        TouchScreenQualifier touchQualifier = mCurrentConfig.getTouchTypeQualifier();
        if (touchQualifier != null) {
            mTouchIcon.setImage(mErrorImage);
        }
        
        mKeyboardIcon.setImage(mMatchImage);
        KeyboardStateQualifier keyboardQualifier = mCurrentConfig.getKeyboardStateQualifier();
        if (keyboardQualifier != null) {
            mKeyboardIcon.setImage(mErrorImage);
        }

        mTextInputIcon.setImage(mMatchImage);
        TextInputMethodQualifier inputQualifier = mCurrentConfig.getTextInputMethodQualifier();
        if (inputQualifier != null) {
            mTextInputIcon.setImage(mErrorImage);
        }
        
        mNavigationIcon.setImage(mMatchImage);
        NavigationMethodQualifier navigationQualifiter =
            mCurrentConfig.getNavigationMethodQualifier();
        if (navigationQualifiter != null) {
            mNavigationIcon.setImage(mErrorImage);
        }
        
        mSizeIcon.setImage(mMatchImage);
        ScreenDimensionQualifier sizeQualifier = mCurrentConfig.getScreenDimensionQualifier();
        if (sizeQualifier != null) {
            mSizeIcon.setImage(mErrorImage);
        }
        
        // update the string showing the folder name
        String current = mCurrentConfig.toDisplayString();
        mCurrentLayoutLabel.setText(current != null ? current : "(Default)");
    }

    UiDocumentNode getModel() {
        return mLayoutEditor.getUiRootNode();
    }
    
    private void onCountryCodeChange() {
        // because mCountry triggers onCountryCodeChange at each modification, calling setText()
        // will trigger notifications, and we don't want that.
        if (mDisableUpdates == true) {
            return;
        }

        // update the current config
        String value = mCountry.getText();
        
        // empty string, means no qualifier.
        if (value.length() == 0) {
            mCurrentConfig.setCountryCodeQualifier(null);
        } else {
            try {
                CountryCodeQualifier qualifier = CountryCodeQualifier.getQualifier(
                        CountryCodeQualifier.getFolderSegment(Integer.parseInt(value)));
                if (qualifier != null) {
                    mCurrentConfig.setCountryCodeQualifier(qualifier);
                } else {
                    // Failure! Looks like the value is wrong (for instance a one letter string).
                    // We do nothing in this case.
                    mCountryIcon.setImage(mErrorImage);
                    return;
                }
            } catch (NumberFormatException e) {
                // Looks like the code is not a number. This should not happen since the text
                // field has a VerifyListener that prevents it.
                mCurrentConfig.setCountryCodeQualifier(null);
                mCountryIcon.setImage(mErrorImage);
            }
        }
        
        // look for a file to open/create
        onConfigurationChange();
    }

    private void onNetworkCodeChange() {
        // because mNetwork triggers onNetworkCodeChange at each modification, calling setText()
        // will trigger notifications, and we don't want that.
        if (mDisableUpdates == true) {
            return;
        }

        // update the current config
        String value = mNetwork.getText();
        
        // empty string, means no qualifier.
        if (value.length() == 0) {
            mCurrentConfig.setNetworkCodeQualifier(null);
        } else {
            try {
                NetworkCodeQualifier qualifier = NetworkCodeQualifier.getQualifier(
                        NetworkCodeQualifier.getFolderSegment(Integer.parseInt(value)));
                if (qualifier != null) {
                    mCurrentConfig.setNetworkCodeQualifier(qualifier);
                } else {
                    // Failure! Looks like the value is wrong (for instance a one letter string).
                    // We do nothing in this case.
                    mNetworkIcon.setImage(mErrorImage);
                    return;
                }
            } catch (NumberFormatException e) {
                // Looks like the code is not a number. This should not happen since the text
                // field has a VerifyListener that prevents it.
                mCurrentConfig.setNetworkCodeQualifier(null);
                mNetworkIcon.setImage(mErrorImage);
            }
        }
        
        // look for a file to open/create
        onConfigurationChange();
    }

    /**
     * Call back for language combo selection
     */
    private void onLanguageChange() {
        // because mLanguage triggers onLanguageChange at each modification, the filling
        // of the combo with data will trigger notifications, and we don't want that.
        if (mDisableUpdates == true) {
            return;
        }

        // update the current config
        String value = mLanguage.getText();
        
        updateRegionUi(null /* projectResources */, null /* frameworkResources */);
        
        // empty string, means no qualifier.
        if (value.length() == 0) {
            mCurrentConfig.setLanguageQualifier(null);
        } else {
            LanguageQualifier qualifier = null;
            String segment = LanguageQualifier.getFolderSegment(value);
            if (segment != null) {
                qualifier = LanguageQualifier.getQualifier(segment);
            }

            if (qualifier != null) {
                mCurrentConfig.setLanguageQualifier(qualifier);
            } else {
                // Failure! Looks like the value is wrong (for instance a one letter string).
                mCurrentConfig.setLanguageQualifier(null);
                mLanguageIcon.setImage(mErrorImage);
            }
        }
        
        // look for a file to open/create
        onConfigurationChange();
    }
    
    private void onRegionChange() {
        // because mRegion triggers onRegionChange at each modification, the filling
        // of the combo with data will trigger notifications, and we don't want that.
        if (mDisableUpdates == true) {
            return;
        }

        // update the current config
        String value = mRegion.getText();
        
        // empty string, means no qualifier.
        if (value.length() == 0) {
            mCurrentConfig.setRegionQualifier(null);
        } else {
            RegionQualifier qualifier = null;
            String segment = RegionQualifier.getFolderSegment(value);
            if (segment != null) {
                qualifier = RegionQualifier.getQualifier(segment);
            }

            if (qualifier != null) {
                mCurrentConfig.setRegionQualifier(qualifier);
            } else {
                // Failure! Looks like the value is wrong (for instance a one letter string).
                mCurrentConfig.setRegionQualifier(null);
                mRegionIcon.setImage(mErrorImage);
            }
        }
        
        // look for a file to open/create
        onConfigurationChange();
    }
    
    private void onOrientationChange() {
        // update the current config
        int index = mOrientation.getSelectionIndex();
        if (index != 0) {
            mCurrentConfig.setScreenOrientationQualifier(new ScreenOrientationQualifier(
                ScreenOrientation.getByIndex(index-1)));
        } else {
            mCurrentConfig.setScreenOrientationQualifier(null);
        }
        
        // look for a file to open/create
        onConfigurationChange();
    }
    
    private void onDensityChange() {
        // because mDensity triggers onDensityChange at each modification, calling setText()
        // will trigger notifications, and we don't want that.
        if (mDisableUpdates == true) {
            return;
        }

        // update the current config
        String value = mDensity.getText();
        
        // empty string, means no qualifier.
        if (value.length() == 0) {
            mCurrentConfig.setPixelDensityQualifier(null);
        } else {
            try {
                PixelDensityQualifier qualifier = PixelDensityQualifier.getQualifier(
                        PixelDensityQualifier.getFolderSegment(Integer.parseInt(value)));
                if (qualifier != null) {
                    mCurrentConfig.setPixelDensityQualifier(qualifier);
                } else {
                    // Failure! Looks like the value is wrong (for instance a one letter string).
                    // We do nothing in this case.
                    return;
                }
            } catch (NumberFormatException e) {
                // Looks like the code is not a number. This should not happen since the text
                // field has a VerifyListener that prevents it.
                // We do nothing in this case.
                mDensityIcon.setImage(mErrorImage);
                return;
            }
        }

        // look for a file to open/create
        onConfigurationChange();
    }
    
    private void onTouchChange() {
        // update the current config
        int index = mTouch.getSelectionIndex();
        if (index != 0) {
            mCurrentConfig.setTouchTypeQualifier(new TouchScreenQualifier(
                TouchScreenType.getByIndex(index-1)));
        } else {
            mCurrentConfig.setTouchTypeQualifier(null);
        }
        
        // look for a file to open/create
        onConfigurationChange();
    }

    private void onKeyboardChange() {
        // update the current config
        int index = mKeyboard.getSelectionIndex();
        if (index != 0) {
            mCurrentConfig.setKeyboardStateQualifier(new KeyboardStateQualifier(
                KeyboardState.getByIndex(index-1)));
        } else {
            mCurrentConfig.setKeyboardStateQualifier(null);
        }
        
        // look for a file to open/create
        onConfigurationChange();
    }
    
    private void onTextInputChange() {
        // update the current config
        int index = mTextInput.getSelectionIndex();
        if (index != 0) {
            mCurrentConfig.setTextInputMethodQualifier(new TextInputMethodQualifier(
                TextInputMethod.getByIndex(index-1)));
        } else {
            mCurrentConfig.setTextInputMethodQualifier(null);
        }
        
        // look for a file to open/create
        onConfigurationChange();
    }
    
    private void onNavigationChange() {
        // update the current config
        int index = mNavigation.getSelectionIndex();
        if (index != 0) {
            mCurrentConfig.setNavigationMethodQualifier(new NavigationMethodQualifier(
                NavigationMethod.getByIndex(index-1)));
        } else {
            mCurrentConfig.setNavigationMethodQualifier(null);
        }
        
        // look for a file to open/create
        onConfigurationChange();
    }
    
    private void onSizeChange() {
        // because mSize1 and mSize2 trigger onSizeChange at each modification, calling setText()
        // will trigger notifications, and we don't want that.
        if (mDisableUpdates == true) {
            return;
        }

        // update the current config
        String size1 = mSize1.getText();
        String size2 = mSize2.getText();
        
        // if only one of the strings is empty, do nothing
        if ((size1.length() == 0) ^ (size2.length() == 0)) {
            mSizeIcon.setImage(mErrorImage);
            return;
        } else if (size1.length() == 0 && size2.length() == 0) {
            // both sizes are empty: remove the qualifier.
            mCurrentConfig.setScreenDimensionQualifier(null);
        } else {
            ScreenDimensionQualifier qualifier = ScreenDimensionQualifier.getQualifier(size1,
                    size2);

            if (qualifier != null) {
                mCurrentConfig.setScreenDimensionQualifier(qualifier);
            } else {
                // Failure! Looks like the value is wrong.
                // we do nothing in this case.
                return;
            }
        }
        
        // look for a file to open/create
        onConfigurationChange();
    }

    
    /**
     * Looks for a file matching the new {@link FolderConfiguration} and attempts to open it.
     * <p/>If there is no match, notify the user.
     */
    private void onConfigurationChange() {
        mConfiguredFrameworkRes = mConfiguredProjectRes = null;

        if (mEditedFile == null || mEditedConfig == null) {
            return;
        }
        
        // get the resources of the file's project.
        ProjectResources resources = ResourceManager.getInstance().getProjectResources(
                mEditedFile.getProject());
        
        // from the resources, look for a matching file
        ResourceFile match = null;
        if (resources != null) {
            match = resources.getMatchingFile(mEditedFile.getName(),
                                              ResourceFolderType.LAYOUT,
                                              mCurrentConfig);
        }
        
        if (match != null) {
            if (match.getFile().equals(mEditedFile) == false) {
                try {
                    IDE.openEditor(
                            getSite().getWorkbenchWindow().getActivePage(),
                            match.getFile().getIFile());

                    // we're done!
                    return;
                } catch (PartInitException e) {
                    // FIXME: do something!
                }
            }

            // at this point, we have not opened a new file.

            // update the configuration icons with the new edited config.
            setConfiguration(mEditedConfig);
            
            // enable the create button if the current and edited config are not equals
            mCreateButton.setEnabled(mEditedConfig.equals(mCurrentConfig) == false);

            // Even though the layout doesn't change, the config changed, and referenced
            // resources need to be updated.
            recomputeLayout();
        } else {
            // update the configuration icons with the new edited config.
            displayConfigError();
            
            // enable the Create button
            mCreateButton.setEnabled(true);

            // display the error.
            String message = String.format(
                    "No resources match the configuration\n \n\t%1$s\n \nChange the configuration or create:\n \n\tres/%2$s/%3$s\n \nYou can also click the 'Create' button above.",
                    mCurrentConfig.toDisplayString(),
                    mCurrentConfig.getFolderName(ResourceFolderType.LAYOUT),
                    mEditedFile.getName());
            showErrorInEditor(message);
        }
    }
    
    private void onThemeChange() {
        int themeIndex = mThemeCombo.getSelectionIndex();
        if (themeIndex != -1) {
            String theme = mThemeCombo.getItem(themeIndex);
            
            if (theme.equals(THEME_SEPARATOR)) {
                mThemeCombo.select(0);
            }

            recomputeLayout();
        }
    }

    /**
     * Creates a composite with no margin/spacing, and puts a {@link Label} in it with the matching
     * icon.
     * @param parent the parent to receive the composite
     * @return the created {@link Label} object.
     */
    private Label createControlComposite(Composite parent, boolean grab) {
        GridLayout gl;

        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayout(gl = new GridLayout(2, false));
        gl.marginHeight = gl.marginWidth = 0;
        gl.horizontalSpacing = 0;
        if (grab) {
            composite.setLayoutData(
                    new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        }

        // create the label
        Label icon = new Label(composite, SWT.NONE);
        icon.setImage(mMatchImage);
        
        return icon;
    }
    
    /**
     * Recomputes the layout with the help of layoutlib.
     */
    void recomputeLayout() {
        try {
            // check that the resource exists. If the file is opened but the project is closed
            // or deleted for some reason (changed from outside of eclipse), then this will
            // return false;
            if (mEditedFile.exists() == false) {
                String message = String.format("Resource '%1$s' does not exist.",
                        mEditedFile.getFullPath().toString()); 

                showErrorInEditor(message);

                return;
            }

            IProject iProject = mEditedFile.getProject();

            if (mEditedFile.isSynchronized(IResource.DEPTH_ZERO) == false) {
                String message = String.format("%1$s is out of sync. Please refresh.",
                        mEditedFile.getName()); 

                showErrorInEditor(message);

                // also print it in the error console.
                EditorsPlugin.printErrorToConsole(iProject.getName(), message);
                return;
            }

            // check there is actually a model (maybe the file is empty).
            UiDocumentNode model = getModel();
            
            if (model.getUiChildren().size() == 0) {
                showErrorInEditor("No Xml content. Go to the Outline view and add nodes.");
                return;
            }

            EditorsPlugin plugin = EditorsPlugin.getDefault();
            LayoutBridge bridge = plugin.getLayoutBridge();

            if (bridge.bridge != null) { // bridge can never be null.
                ResourceManager resManager = ResourceManager.getInstance();

                ProjectResources projectRes = resManager.getProjectResources(iProject);
                if (projectRes == null) {
                    return;
                }

                // get the resources of the file's project.
                if (mConfiguredProjectRes == null) {
                    // make sure they are loaded
                    projectRes.loadAll();

                    // get the project resource values based on the current config
                    mConfiguredProjectRes = projectRes.getConfiguredResources(mCurrentConfig);
                }

                // get the framework resources
                Map<String, Map<String, IResourceValue>> frameworkResources =
                    getConfiguredFrameworkResources();

                if (mConfiguredProjectRes != null && frameworkResources != null) {
                    if (mProjectCallback == null) {
                        mProjectCallback = new ProjectCallback(
                                plugin.getLayoutlibBridgeClassLoader(), projectRes, iProject);
                    }

                    if (mLogger == null) {
                        mLogger = new ILayoutLog() {
                            public void error(String message) {
                                EditorsPlugin.printErrorToConsole(mEditedFile.getName(), message);
                            }

                            public void error(Throwable error) {
                                String message = error.getMessage();
                                if (message == null) {
                                    message = error.getClass().getName();
                                }

                                PrintStream ps = new PrintStream(EditorsPlugin.getErrorStream());
                                error.printStackTrace(ps);
                            }

                            public void warning(String message) {
                                EditorsPlugin.printToConsole(mEditedFile.getName(), message);
                            }
                        };
                    }

                    // get the selected theme
                    int themeIndex = mThemeCombo.getSelectionIndex();
                    if (themeIndex != -1) {
                        String theme = mThemeCombo.getItem(themeIndex);
                        
                        // change the string if it's a custom theme to make sure we can
                        // differentiate them
                        if (themeIndex >= mPlatformThemeCount) {
                            theme = "*" + theme; //$NON-NLS-1$
                        }

                        // Compute the layout
                        UiElementPullParser parser = new UiElementPullParser(getModel());
                        Rectangle rect = getBounds();
                        ILayoutResult result = bridge.bridge.computeLayout(parser, iProject,
                                rect.width, rect.height, theme,
                                mConfiguredProjectRes, frameworkResources, mProjectCallback,
                                mLogger);

                        // update the UiElementNode with the layout info.
                        if (result.getSuccess() == ILayoutResult.SUCCESS) {
                            model.setEditData(result.getImage());

                            updateNodeWithBounds(result.getRootView());
                        } else {
                            String message = result.getErrorMessage();

                            // Reset the edit data for all the nodes.
                            resetNodeBounds(model);

                            if (message != null) {
                                // set the error in the top element.
                                model.setEditData(message);
                            }
                        }

                        model.refreshUi();
                    }
                }
            } else {
                String message = null;

                // check whether the bridge managed to load, or not
                if (bridge.status == LayoutBridge.LoadStatus.LOADING) {
                    message = String.format(
                            "Eclipse is loading framework information and the Layout library from the SDK folder.\n%1$s will refresh automatically once the process is finished.",
                            mEditedFile.getName()); 
                } else {
                    message = String.format("Eclipse failed to load the framework information and the Layout library!"); 
                }
                
                showErrorInEditor(message);
            }
        } finally {
            // no matter the result, we are done doing the recompute based on the latest
            // resource/code change.
            mNeedsRecompute = false;
        }
    }

    private void showErrorInEditor(String message) {
        // get the model to display the error directly in the editor
        UiDocumentNode model = getModel();

        // Reset the edit data for all the nodes.
        resetNodeBounds(model);

        if (message != null) {
            // set the error in the top element.
            model.setEditData(message);
        }

        model.refreshUi();
    }
    
    private void resetNodeBounds(UiElementNode node) {
        node.setEditData(null);
        
        List<UiElementNode> children = node.getUiChildren();
        for (UiElementNode child : children) {
            resetNodeBounds(child);
        }
    }

    private void updateNodeWithBounds(ILayoutViewInfo r) {
        if (r != null) {
            // update the node itself, as the viewKey is the XML node in this implementation.
            Object viewKey = r.getViewKey();
            if (viewKey instanceof UiElementNode) {
                Rectangle bounds = new Rectangle(r.getLeft(), r.getTop(),
                        r.getRight()-r.getLeft(), r.getBottom() - r.getTop());
                
                ((UiElementNode)viewKey).setEditData(bounds);
            }
            
            // and then its children.
            ILayoutViewInfo[] children = r.getChildren();
            if (children != null) {
                for (ILayoutViewInfo child : children) {
                    updateNodeWithBounds(child);
                }
            }
        }
    }
    
    /*
     * (non-Javadoc)
     * @see com.android.ide.eclipse.editors.layout.LayoutReloadMonitor.ILayoutReloadListener#reloadLayout(boolean, boolean, boolean)
     * 
     * Called when the file changes triggered a redraw of the layout
     */
    public void reloadLayout(boolean codeChange, boolean rChange, boolean resChange) {
        boolean recompute = rChange;

        if (resChange) {
            recompute = true;

            // TODO: differentiate between single and multi resource file changed, and whether the resource change affects the cache. 

            // force a reparse in case a value XML file changed.
            mConfiguredProjectRes = null;
            
            // clear the cache in the bridge in case a bitmap/9-patch changed.
            EditorsPlugin plugin = EditorsPlugin.getDefault();
            LayoutBridge bridge = plugin.getLayoutBridge();

            if (bridge.bridge != null) {
                bridge.bridge.clearCaches(mEditedFile.getProject());
            }
            
            mParent.getDisplay().asyncExec(mUiUpdateFromResourcesRunnable);
        }

        if (codeChange) {
            // only recompute if the custom view loader was used to load some code.
            if (mProjectCallback != null && mProjectCallback.isUsed()) {
                mProjectCallback = null;
                recompute = true;
            }
        }
        
        if (recompute) {
            mParent.getDisplay().asyncExec(mConditionalRecomputeRunnable);
        }
    }

    /**
     * Responds to a page change that made the Graphical editor page the activated page.
     */
    void activated() {
        if (mNeedsRecompute) {
            recomputeLayout();
        }
    }
    
    /**
     * Updates the UI from values in the resources, such as languages, regions, themes, etc...
     * This must be called from the UI thread.
     */
    private void updateUIFromResources() {

        ResourceManager manager = ResourceManager.getInstance();
        
        ProjectResources frameworkProject = manager.getFrameworkResources();

        mDisableUpdates = true;
        
        // Reset stuff
        int selection = mThemeCombo.getSelectionIndex();
        mThemeCombo.removeAll();
        mPlatformThemeCount = 0;
        mLanguage.removeAll();
        
        Set<String> languages = new HashSet<String>();
        ArrayList<String> themes = new ArrayList<String>();
        
        // get the themes, and languages from the Framework.
        if (frameworkProject != null) {
            // get the configured resources for the framework
            Map<String, Map<String, IResourceValue>> frameworResources =
                getConfiguredFrameworkResources();
            
            if (frameworResources != null) {
                // get the styles.
                Map<String, IResourceValue> styles = frameworResources.get(
                        ResourceType.STYLE.getName());
                
                
                // collect the themes out of all the styles.
                for (IResourceValue value : styles.values()) {
                    String name = value.getName();
                    if (name.startsWith("Theme.") || name.equals("Theme")) {
                        themes.add(value.getName());
                        mPlatformThemeCount++;
                    }
                }

                // sort them and add them to the combo
                Collections.sort(themes);
                
                for (String theme : themes) {
                    mThemeCombo.add(theme);
                }
                
                mPlatformThemeCount = themes.size();
                themes.clear();
            }
            
            // now get the languages from the framework.
            Set<String> frameworkLanguages = frameworkProject.getLanguages();
            if (frameworkLanguages != null) {
                languages.addAll(frameworkLanguages);
            }
        }
        
        // now get the themes and languages from the project.
        ProjectResources project = null;
        if (mEditedFile != null) {
            project = manager.getProjectResources(mEditedFile.getProject());
            
            // in cases where the opened file is not linked to a project, this could be null.
            if (project != null) {
                // get the configured resources for the project 
                if (mConfiguredProjectRes == null) {
                    // make sure they are loaded
                    project.loadAll();

                    // get the project resource values based on the current config
                    mConfiguredProjectRes = project.getConfiguredResources(mCurrentConfig);
                }
                
                if (mConfiguredProjectRes != null) {
                    // get the styles.
                    Map<String, IResourceValue> styleMap = mConfiguredProjectRes.get(
                            ResourceType.STYLE.getName());
                    
                    if (styleMap != null) {
                        // collect the themes out of all the styles, ie styles that extend,
                        // directly or indirectly a platform theme.
                        for (IResourceValue value : styleMap.values()) {
                            if (isTheme(value, styleMap)) {
                                themes.add(value.getName());
                            }
                        }

                        // sort them and add them the to the combo.
                        if (mPlatformThemeCount > 0 && themes.size() > 0) {
                            mThemeCombo.add(THEME_SEPARATOR);
                        }
                        
                        Collections.sort(themes);
                        
                        for (String theme : themes) {
                            mThemeCombo.add(theme);
                        }
                    }
                }

                // now get the languages from the project.
                Set<String> projectLanguages = project.getLanguages();
                if (projectLanguages != null) {
                    languages.addAll(projectLanguages);
                }
            }
        }

        // add the languages to the Combo
        for (String language : languages) {
            mLanguage.add(language);
        }
        
        mDisableUpdates = false;

        // and update the Region UI based on the current language
        updateRegionUi(project, frameworkProject);

        // handle default selection of themes
        if (mThemeCombo.getItemCount() > 0) {
            mThemeCombo.setEnabled(true);
            if (selection == -1) {
                selection = 0;
            }

            if (mThemeCombo.getItemCount() <= selection) {
                mThemeCombo.select(0);
            } else {
                mThemeCombo.select(selection);
            }
        } else {
            mThemeCombo.setEnabled(false);
        }
    }

    /**
     * Returns whether the given <var>style</var> is a theme.
     * This is done by making sure the parent is a theme.
     * @param value the style to check
     * @param styleMap the map of styles for the current project. Key is the style name.
     * @return
     */
    private boolean isTheme(IResourceValue value, Map<String, IResourceValue> styleMap) {
        if (value instanceof IStyleResourceValue) {
            IStyleResourceValue style = (IStyleResourceValue)value;
            
            boolean frameworkStyle = false;
            String parentStyle = style.getParentStyle();
            if (parentStyle == null) {
                // if there is no specified parent style we look an implied one.
                // For instance 'Theme.light' is implied child style of 'Theme',
                // and 'Theme.light.fullscreen' is implied child style of 'Theme.light'
                String name = style.getName();
                int index = name.lastIndexOf('.');
                if (index != -1) {
                    parentStyle = name.substring(0, index);
                }
            } else {
                // remove the useless @ if it's there
                if (parentStyle.startsWith("@")) {
                    parentStyle = parentStyle.substring(1);
                }
                
                // check for framework identifier.
                if (parentStyle.startsWith("android:")) {
                    frameworkStyle = true;
                    parentStyle = parentStyle.substring("android:".length());
                }
                
                // at this point we could have the format style/<name>. we want only the name
                if (parentStyle.startsWith("style/")) {
                    parentStyle = parentStyle.substring("style/".length());
                }
            }

            if (frameworkStyle) {
                // if the parent is a framework style, it has to be 'Theme' or 'Theme.*'
                return parentStyle.equals("Theme") || parentStyle.startsWith("Theme.");
            } else {
                // if it's a project style, we check this is a theme.
                value = styleMap.get(parentStyle);
                if (value != null) {
                    return isTheme(value, styleMap);
                }
            }
        }

        return false;
    }

    /**
     * Update the Region UI widget based on the current language selection
     * @param projectResources the project resources or {@code null}.
     * @param frameworkResources the framework resource or {@code null}
     */
    private void updateRegionUi(ProjectResources projectResources,
            ProjectResources frameworkResources) {
        if (projectResources == null && mEditedFile != null) {
            projectResources = ResourceManager.getInstance().getProjectResources(
                    mEditedFile.getProject());
        }
        
        if (frameworkResources == null) {
            frameworkResources = ResourceManager.getInstance().getFrameworkResources();
        }
        
        String currentLanguage = mLanguage.getText();
        
        Set<String> set = null;
        
        if (projectResources != null) {
            set = projectResources.getRegions(currentLanguage);
        }

        if (frameworkResources != null) {
            if (set != null) {
                Set<String> set2 = frameworkResources.getRegions(currentLanguage);
                set.addAll(set2);
            } else {
                set = frameworkResources.getRegions(currentLanguage);
            }
        }

        if (set != null) {
            mDisableUpdates = true;

            mRegion.removeAll();
            for (String region : set) {
                mRegion.add(region);
            }

            mDisableUpdates = false;
        }
    }
    
    private Map<String, Map<String, IResourceValue>> getConfiguredFrameworkResources() {
        if (mConfiguredFrameworkRes == null) {
            ProjectResources frameworkRes = ResourceManager.getInstance().getFrameworkResources();

            if (frameworkRes == null) {
                EditorsPlugin.log(IStatus.ERROR, "Failed to get ProjectResource for the framework");
            }

            // get the framework resource values based on the current config
            mConfiguredFrameworkRes = frameworkRes.getConfiguredResources(mCurrentConfig);
        }
        
        return mConfiguredFrameworkRes;
    }

    /**
     * Returns the selection synchronizer object.
     * The synchronizer can be used to sync the selection of 2 or more EditPartViewers.
     * <p/>
     * This is changed from protected to public so that the outline can use it.
     * 
     * @return the synchronizer
     */
    @Override
    public SelectionSynchronizer getSelectionSynchronizer() {
        return super.getSelectionSynchronizer();
    }
    
    /**
     * Returns the edit domain.
     * <p/>
     * This is changed from protected to public so that the outline can use it.
     * 
     * @return the edit domain
     */
    @Override
    public DefaultEditDomain getEditDomain() {
        return super.getEditDomain();
    }

    /**
     * Creates a new layout file from the specificed {@link FolderConfiguration}.
     */
    private void createAlternateLayout(final FolderConfiguration config) {
        new Job("Create Alternate Resource") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                // get the folder name
                String folderName = config.getFolderName(ResourceFolderType.LAYOUT);
                try {
                    
                    // look to see if it exists.
                    // get the res folder
                    IFolder res = (IFolder)mEditedFile.getParent().getParent();
                    String path = res.getLocation().toOSString();
                    
                    File newLayoutFolder = new File(path + File.separator + folderName);
                    if (newLayoutFolder.isFile()) {
                        // this should not happen since aapt would have complained
                        // before, but if one disable the automatic build, this could
                        // happen.
                        String message = String.format("File 'res/%1$s' is in the way!",
                                folderName);
                        
                        EditorsPlugin.displayError("Layout Creation", message);
                        
                        return new Status(IStatus.ERROR,
                                AndroidConstants.EDITORS_PLUGIN_ID, message);
                    } else if (newLayoutFolder.exists() == false) {
                        // create it.
                        newLayoutFolder.mkdir();
                    }
                    
                    // now create the file
                    File newLayoutFile = new File(newLayoutFolder.getAbsolutePath() +
                                File.separator + mEditedFile.getName());

                    newLayoutFile.createNewFile();
                    
                    InputStream input = mEditedFile.getContents();
                    
                    FileOutputStream fos = new FileOutputStream(newLayoutFile);
                    
                    byte[] data = new byte[512];
                    int count;
                    while ((count = input.read(data)) != -1) {
                        fos.write(data, 0, count);
                    }
                    
                    input.close();
                    fos.close();
                    
                    // refreshes the res folder to show up the new
                    // layout folder (if needed) and the file.
                    // We use a progress monitor to catch the end of the refresh
                    // to trigger the edit of the new file.
                    res.refreshLocal(IResource.DEPTH_INFINITE, new IProgressMonitor() {
                        public void done() {
                            mCurrentConfig.set(config);
                            mParent.getDisplay().asyncExec(new Runnable() {
                                public void run() {
                                    onConfigurationChange();
                                };  
                            });
                        }

                        public void beginTask(String name, int totalWork) {
                            // pass
                        }

                        public void internalWorked(double work) {
                            // pass
                        }

                        public boolean isCanceled() {
                            // pass
                            return false;
                        }

                        public void setCanceled(boolean value) {
                            // pass
                        }

                        public void setTaskName(String name) {
                            // pass
                        }

                        public void subTask(String name) {
                            // pass
                        }

                        public void worked(int work) {
                            // pass
                        }
                    });
                } catch (IOException e2) {
                    String message = String.format(
                            "Failed to create File 'res/%1$s/%2$s' : %3$s",
                            folderName, mEditedFile.getName(), e2.getMessage());
                    
                    EditorsPlugin.displayError("Layout Creation", message);
                    
                    return new Status(IStatus.ERROR, AndroidConstants.EDITORS_PLUGIN_ID,
                            message, e2);
                } catch (CoreException e2) {
                    String message = String.format(
                            "Failed to create File 'res/%1$s/%2$s' : %3$s",
                            folderName, mEditedFile.getName(), e2.getMessage());
                    
                    EditorsPlugin.displayError("Layout Creation", message);

                    return e2.getStatus();
                }
                
                return Status.OK_STATUS;

            }
        }.schedule();
    }
}
