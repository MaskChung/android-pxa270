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

package com.android.ide.eclipse.adt.debug.ui;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.debug.launching.LaunchConfigDelegate;
import com.android.ide.eclipse.adt.debug.ui.SkinRepository.Skin;
import com.android.ide.eclipse.ddms.DdmsPlugin;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/**
 * Launch configuration tab to control the parameters of the Emulator
 */
public class EmulatorConfigTab extends AbstractLaunchConfigurationTab {

    private final static String[][] NETWORK_SPEEDS = new String[][] {
        { "Full", "full" }, //$NON-NLS-2$
        { "GSM", "gsm" }, //$NON-NLS-2$
        { "HSCSD", "hscsd" }, //$NON-NLS-2$
        { "GPRS", "gprs" }, //$NON-NLS-2$
        { "EDGE", "edge" }, //$NON-NLS-2$
        { "UMTS", "umts" }, //$NON-NLS-2$
        { "HSPDA", "hsdpa" }, //$NON-NLS-2$
    };

    private final static String[][] NETWORK_LATENCIES = new String[][] {
        { "None", "none" }, //$NON-NLS-2$
        { "GPRS", "gprs" }, //$NON-NLS-2$
        { "EDGE", "edge" }, //$NON-NLS-2$
        { "UMTS", "umts" }, //$NON-NLS-2$
    };

    private Combo mSpeedCombo;

    private Combo mDelayCombo;

    private Group mEmulatorOptionsGroup;

    private Text mEmulatorCLOptions;

    private Combo mSkinCombo;

    private Button mAutoTargetButton;

    private Button mManualTargetButton;

    private Button mWipeDataButton;

    private Button mNoBootAnimButton;

    /**
     * Returns the emulator ready speed option value.
     * @param value The index of the combo selection.
     */
    public static String getSpeed(int value) {
        try {
            return NETWORK_SPEEDS[value][1];
        } catch (ArrayIndexOutOfBoundsException e) {
            return NETWORK_SPEEDS[LaunchConfigDelegate.DEFAULT_SPEED][1];
        }
    }

    /**
     * Returns the emulator ready network latency value.
     * @param value The index of the combo selection.
     */
    public static String getDelay(int value) {
        try {
            return NETWORK_LATENCIES[value][1];
        } catch (ArrayIndexOutOfBoundsException e) {
            return NETWORK_LATENCIES[LaunchConfigDelegate.DEFAULT_DELAY][1];
        }
    }

    /**
     *
     */
    public EmulatorConfigTab() {
    }

    /* (non-Javadoc)
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#createControl(org.eclipse.swt.widgets.Composite)
     */
    public void createControl(Composite parent) {
        Font font = parent.getFont();

        Composite topComp = new Composite(parent, SWT.NONE);
        setControl(topComp);
        GridLayout topLayout = new GridLayout();
        topLayout.numColumns = 1;
        topLayout.verticalSpacing = 0;
        topComp.setLayout(topLayout);
        topComp.setFont(font);

        GridData gd;
        GridLayout layout;
        
        // radio button for the target mode
        Group targetModeGroup = new Group(topComp, SWT.NONE);
        targetModeGroup.setText("Device Target Selection Mode");
        gd = new GridData(GridData.FILL_HORIZONTAL);
        targetModeGroup.setLayoutData(gd);
        layout = new GridLayout();
        layout.numColumns = 1;
        targetModeGroup.setLayout(layout);
        targetModeGroup.setFont(font);

        // add the radio button
        mAutoTargetButton = new Button(targetModeGroup, SWT.RADIO);
        mAutoTargetButton.setText("Automatic");
        mAutoTargetButton.setSelection(true);
        mAutoTargetButton.addSelectionListener(new SelectionAdapter() {
            // called when selection changes
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateLaunchConfigurationDialog();
            }
        });

        mManualTargetButton = new Button(targetModeGroup, SWT.RADIO);
        mManualTargetButton.setText("Manual");
        // Since there are only 2 radio buttons, we can put a listener on only
        // one (they
        // are both called on select and unselect event.

        // emulator size
        mEmulatorOptionsGroup = new Group(topComp, SWT.NONE);
        mEmulatorOptionsGroup.setText("Emulator launch parameters:");
        mEmulatorOptionsGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        layout = new GridLayout();
        layout.numColumns = 2;
        mEmulatorOptionsGroup.setLayout(layout);
        mEmulatorOptionsGroup.setFont(font);

        new Label(mEmulatorOptionsGroup, SWT.NONE).setText("Screen Size:");

        mSkinCombo = new Combo(mEmulatorOptionsGroup, SWT.READ_ONLY);
        Skin[] skins = SkinRepository.getInstance().getSkins();
        if (skins != null) {
            for (Skin skin : skins) {
                mSkinCombo.add(skin.getDescription());
            }
        }
        mSkinCombo.addSelectionListener(new SelectionAdapter() {
            // called when selection changes
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateLaunchConfigurationDialog();
            }
        });
        mSkinCombo.pack();

        // network options
        new Label(mEmulatorOptionsGroup, SWT.NONE).setText("Network Speed:");

        mSpeedCombo = new Combo(mEmulatorOptionsGroup, SWT.READ_ONLY);
        for (String[] speed : NETWORK_SPEEDS) {
            mSpeedCombo.add(speed[0]);
        }
        mSpeedCombo.addSelectionListener(new SelectionAdapter() {
            // called when selection changes
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateLaunchConfigurationDialog();
            }
        });
        mSpeedCombo.pack();

        new Label(mEmulatorOptionsGroup, SWT.NONE).setText("Network Latency:");

        mDelayCombo = new Combo(mEmulatorOptionsGroup, SWT.READ_ONLY);

        for (String[] delay : NETWORK_LATENCIES) {
            mDelayCombo.add(delay[0]);
        }
        mDelayCombo.addSelectionListener(new SelectionAdapter() {
            // called when selection changes
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateLaunchConfigurationDialog();
            }
        });
        mDelayCombo.pack();

        // wipe data option
        mWipeDataButton = new Button(mEmulatorOptionsGroup, SWT.CHECK);
        mWipeDataButton.setText("Wipe User Data");
        mWipeDataButton.setToolTipText("Check this if you want to wipe your user data each time you start the emulator. You will be prompted for confirmation when the emulator starts.");
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        mWipeDataButton.setLayoutData(gd);
        mWipeDataButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateLaunchConfigurationDialog();
            }
        });

        // no boot anim option
        mNoBootAnimButton = new Button(mEmulatorOptionsGroup, SWT.CHECK);
        mNoBootAnimButton.setText("Disable Boot Animation");
        mNoBootAnimButton.setToolTipText("Check this if you want to disable the boot animation. This can help the emulator start faster on slow machines.");
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        mNoBootAnimButton.setLayoutData(gd);
        mNoBootAnimButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateLaunchConfigurationDialog();
            }
        });
        
        // custom command line option for emulator
        Label l = new Label(mEmulatorOptionsGroup, SWT.NONE);
        l.setText("Additional Emulator Command Line Options");
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        l.setLayoutData(gd);

        mEmulatorCLOptions = new Text(mEmulatorOptionsGroup, SWT.BORDER);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        mEmulatorCLOptions.setLayoutData(gd);
        mEmulatorCLOptions.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                updateLaunchConfigurationDialog();
            }
        });
    }

    /* (non-Javadoc)
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#getName()
     */
    public String getName() {
        return "Target";
    }

    @Override
    public Image getImage() {
        return DdmsPlugin.getImageLoader().loadImage("emulator.png", null); //$NON-NLS-1$
    }

    /* (non-Javadoc)
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#initializeFrom(org.eclipse.debug.core.ILaunchConfiguration)
     */
    public void initializeFrom(ILaunchConfiguration configuration) {
        boolean value = LaunchConfigDelegate.DEFAULT_TARGET_MODE; // true ==
                                                                    // automatic
        try {
            value = configuration.getAttribute(LaunchConfigDelegate.ATTR_TARGET_MODE, value);
        } catch (CoreException e) {
            // let's not do anything here, we'll use the default value
        }
        mAutoTargetButton.setSelection(value);
        mManualTargetButton.setSelection(!value);

        value = LaunchConfigDelegate.DEFAULT_WIPE_DATA;
        try {
            value = configuration.getAttribute(LaunchConfigDelegate.ATTR_WIPE_DATA, value);
        } catch (CoreException e) {
            // let's not do anything here, we'll use the default value
        }
        mWipeDataButton.setSelection(value);

        value = LaunchConfigDelegate.DEFAULT_NO_BOOT_ANIM;
        try {
            value = configuration.getAttribute(LaunchConfigDelegate.ATTR_NO_BOOT_ANIM, value);
        } catch (CoreException e) {
            // let's not do anything here, we'll use the default value
        }
        mNoBootAnimButton.setSelection(value);

        int index = -1;
        try {
            String skin = configuration.getAttribute(LaunchConfigDelegate.ATTR_SKIN, (String)null);
            if (skin != null) {
                index = getSkinIndex(skin);
            }
        } catch (CoreException e) {
            index = getSkinIndex(SkinRepository.getInstance().checkSkin(
                    LaunchConfigDelegate.DEFAULT_SKIN));
        }

        if (index == -1) {
            mSkinCombo.clearSelection();
        } else {
            mSkinCombo.select(index);
        }

        index = LaunchConfigDelegate.DEFAULT_SPEED;
        try {
            index = configuration.getAttribute(LaunchConfigDelegate.ATTR_SPEED,
                    index);
        } catch (CoreException e) {
            // let's not do anything here, we'll use the default value
        }
        if (index == -1) {
            mSpeedCombo.clearSelection();
        } else {
            mSpeedCombo.select(index);
        }

        index = LaunchConfigDelegate.DEFAULT_DELAY;
        try {
            index = configuration.getAttribute(LaunchConfigDelegate.ATTR_DELAY,
                    index);
        } catch (CoreException e) {
            // let's not do anything here, we'll put a proper value in
            // performApply anyway
        }
        if (index == -1) {
            mDelayCombo.clearSelection();
        } else {
            mDelayCombo.select(index);
        }

        String commandLine = null;
        try {
            commandLine = configuration.getAttribute(
                    LaunchConfigDelegate.ATTR_COMMANDLINE, ""); //$NON-NLS-1$
        } catch (CoreException e) {
            // let's not do anything here, we'll use the default value
        }
        if (commandLine != null) {
            mEmulatorCLOptions.setText(commandLine);
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#performApply(org.eclipse.debug.core.ILaunchConfigurationWorkingCopy)
     */
    public void performApply(ILaunchConfigurationWorkingCopy configuration) {
        configuration.setAttribute(LaunchConfigDelegate.ATTR_TARGET_MODE,
                mAutoTargetButton.getSelection());
        configuration.setAttribute(LaunchConfigDelegate.ATTR_SKIN,
                getSkinNameByIndex(mSkinCombo.getSelectionIndex()));
        configuration.setAttribute(LaunchConfigDelegate.ATTR_SPEED,
                mSpeedCombo.getSelectionIndex());
        configuration.setAttribute(LaunchConfigDelegate.ATTR_DELAY,
                mDelayCombo.getSelectionIndex());
        configuration.setAttribute(LaunchConfigDelegate.ATTR_COMMANDLINE,
                mEmulatorCLOptions.getText());
        configuration.setAttribute(LaunchConfigDelegate.ATTR_WIPE_DATA,
                mWipeDataButton.getSelection());
        configuration.setAttribute(LaunchConfigDelegate.ATTR_NO_BOOT_ANIM,
                mNoBootAnimButton.getSelection());
   }

    /* (non-Javadoc)
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#setDefaults(org.eclipse.debug.core.ILaunchConfigurationWorkingCopy)
     */
    public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
        configuration.setAttribute(LaunchConfigDelegate.ATTR_TARGET_MODE,
                LaunchConfigDelegate.DEFAULT_TARGET_MODE);
        configuration.setAttribute(LaunchConfigDelegate.ATTR_SKIN,
                LaunchConfigDelegate.DEFAULT_SKIN);
        configuration.setAttribute(LaunchConfigDelegate.ATTR_SPEED,
                LaunchConfigDelegate.DEFAULT_SPEED);
        configuration.setAttribute(LaunchConfigDelegate.ATTR_DELAY,
                LaunchConfigDelegate.DEFAULT_DELAY);
        configuration.setAttribute(LaunchConfigDelegate.ATTR_WIPE_DATA,
                LaunchConfigDelegate.DEFAULT_WIPE_DATA);
        configuration.setAttribute(LaunchConfigDelegate.ATTR_NO_BOOT_ANIM,
                LaunchConfigDelegate.DEFAULT_NO_BOOT_ANIM);
        
        IPreferenceStore store = AdtPlugin.getDefault().getPreferenceStore();
        String emuOptions = store.getString(AdtPlugin.PREFS_EMU_OPTIONS);
        configuration.setAttribute(LaunchConfigDelegate.ATTR_COMMANDLINE, emuOptions);
   }

    private String getSkinNameByIndex(int index) {
        return SkinRepository.getInstance().getSkinNameByIndex(index);
    }

    private int getSkinIndex(String name) {
        return SkinRepository.getInstance().getSkinIndex(name);
    }

}
