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

package com.android.ide.eclipse.adt.preferences;

import com.android.ide.eclipse.adt.AdtPlugin;

import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import java.io.File;

/**
 * This class represents a preference page that is contributed to the
 * Preferences dialog. By subclassing <samp>FieldEditorPreferencePage</samp>,
 * we can use the field support built into JFace that allows us to create a page
 * that is small and knows how to save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They are stored in the
 * preference store that belongs to the main plug-in class. That way,
 * preferences can be accessed directly via the preference store.
 */

public class AndroidPreferencePage extends FieldEditorPreferencePage implements
        IWorkbenchPreferencePage {

    public AndroidPreferencePage() {
        super(GRID);
        setPreferenceStore(AdtPlugin.getDefault().getPreferenceStore());
        setDescription(Messages.AndroidPreferencePage_Title);
    }

    /**
     * Creates the field editors. Field editors are abstractions of the common
     * GUI blocks needed to manipulate various types of preferences. Each field
     * editor knows how to save and restore itself.
     */
    @Override
    public void createFieldEditors() {

        addField(new SdkDirectoryFieldEditor(AdtPlugin.PREFS_SDK_DIR,
                Messages.AndroidPreferencePage_SDK_Location_, getFieldEditorParent()));
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
     */
    public void init(IWorkbench workbench) {
    }

    /**
     * Custom version of DirectoryFieldEditor which validates that the directory really
     * contains an SDK.
     *
     * There's a known issue here, which is really a rare edge-case: if the pref dialog is open
     * which a given sdk directory and the *content* of the directory changes such that the sdk
     * state changed (i.e. from valid to invalid or vice versa), the pref panel will display or
     * hide the error as appropriate but the pref panel will fail to validate the apply/ok buttons
     * appropriately. The easy workaround is to cancel the pref panel and enter it again.
     */
    private static class SdkDirectoryFieldEditor extends DirectoryFieldEditor {

        public SdkDirectoryFieldEditor(String name, String labelText, Composite parent) {
            super(name, labelText, parent);
            setEmptyStringAllowed(false);
        }

        /**
         * Method declared on StringFieldEditor and overridden in DirectoryFieldEditor.
         * Checks whether the text input field contains a valid directory.
         *
         * @return True if the apply/ok button should be enabled in the pref panel
         */
        @Override
        protected boolean doCheckState() {
            String fileName = getTextControl().getText();
            fileName = fileName.trim();
            
            if (fileName.indexOf(',') >= 0 || fileName.indexOf(';') >= 0) {
                setErrorMessage(Messages.AndroidPreferencePage_ERROR_Reserved_Char);
                return false;  // Apply/OK must be disabled
            }
            
            File file = new File(fileName);
            if (!file.isDirectory()) {
                setErrorMessage(JFaceResources.getString(
                    "DirectoryFieldEditor.errorMessage")); //$NON-NLS-1$
                return false;
            }

            boolean ok = AdtPlugin.getDefault().checkSdkLocationAndId(fileName,
                    new AdtPlugin.CheckSdkErrorHandler() {
                @Override
                public boolean handleError(String message) {
                    setErrorMessage(message.replaceAll("\n", " ")); //$NON-NLS-1$ //$NON-NLS-2$
                    return false;  // Apply/OK must be disabled
                }

                @Override
                public boolean handleWarning(String message) {
                    showMessage(message.replaceAll("\n", " ")); //$NON-NLS-1$ //$NON-NLS-2$
                    return true;  // Apply/OK must be enabled
                }
            });
            if (ok) clearMessage();
            return ok;
        }

        @Override
        public Text getTextControl(Composite parent) {
            setValidateStrategy(VALIDATE_ON_KEY_STROKE);
            return super.getTextControl(parent);
        }
    }
}
