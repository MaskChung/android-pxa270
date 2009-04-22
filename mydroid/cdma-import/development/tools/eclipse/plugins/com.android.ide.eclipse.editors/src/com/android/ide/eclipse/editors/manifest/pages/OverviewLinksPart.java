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

package com.android.ide.eclipse.editors.manifest.pages;

import com.android.ide.eclipse.editors.EditorsPlugin;
import com.android.ide.eclipse.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.editors.manifest.ManifestEditor;
import com.android.ide.eclipse.editors.manifest.descriptors.AndroidManifestDescriptors;
import com.android.ide.eclipse.editors.ui.SectionHelper.ManifestSectionPart;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.widgets.FormText;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;

/**
 * Links section part for overview page.
 */
final class OverviewLinksPart extends ManifestSectionPart {

    public OverviewLinksPart(Composite body, FormToolkit toolkit, ManifestEditor editor) {
        super(body, toolkit, Section.TWISTIE | Section.EXPANDED, true /* description */);
        Section section = getSection();
        section.setText("Links");
        section.setDescription("The content of the Android Manifest is made up of three sections. You can also edit the XML directly.");

        Composite table = createTableLayout(toolkit, 2 /* numColumns */);
        
        StringBuffer buf = new StringBuffer();
        buf.append(String.format("<form><li style=\"image\" value=\"app_img\"><a href=\"page:%1$s\">", // $NON-NLS-1$
                ApplicationPage.PAGE_ID));
        buf.append("Application");
        buf.append("</a>");  //$NON-NLS-1$
        buf.append(": Activities, intent filters, providers, services and receivers.");
        buf.append("</li>"); //$NON-NLS-1$

        buf.append(String.format("<li style=\"image\" value=\"perm_img\"><a href=\"page:%1$s\">", // $NON-NLS-1$
                PermissionPage.PAGE_ID));
        buf.append("Permission");
        buf.append("</a>"); //$NON-NLS-1$
        buf.append(": Permissions defined and permissions used.");
        buf.append("</li>"); //$NON-NLS-1$

        buf.append(String.format("<li style=\"image\" value=\"inst_img\"><a href=\"page:%1$s\">", // $NON-NLS-1$
                InstrumentationPage.PAGE_ID));
        buf.append("Instrumentation");
        buf.append("</a>"); //$NON-NLS-1$
        buf.append(": Instrumentation defined.");
        buf.append("</li>"); //$NON-NLS-1$

        buf.append(String.format("<li style=\"image\" value=\"android_img\"><a href=\"page:%1$s\">", // $NON-NLS-1$
                ManifestEditor.TEXT_EDITOR_ID));
        buf.append("XML Source");
        buf.append("</a>"); //$NON-NLS-1$
        buf.append(": Directly edit the AndroidManifest.xml file.");
        buf.append("</li>"); //$NON-NLS-1$

        buf.append("<li style=\"image\" value=\"android_img\">"); // $NON-NLS-1$
        buf.append("<a href=\"http://code.google.com/android/devel/bblocks-manifest.html\">Documentation</a>: Documentation from the Android SDK for AndroidManifest.xml."); // $NON-NLS-1$
        buf.append("</li>"); //$NON-NLS-1$
        buf.append("</form>"); //$NON-NLS-1$

        FormText text = createFormText(table, toolkit, true, buf.toString(),
                false /* setupLayoutData */);
        text.setImage("android_img", EditorsPlugin.getAndroidLogo());
        text.setImage("app_img", getIcon(AndroidManifestDescriptors.APPLICATION_ELEMENT));
        text.setImage("perm_img", getIcon(AndroidManifestDescriptors.PERMISSION_ELEMENT));
        text.setImage("inst_img", getIcon(AndroidManifestDescriptors.INTRUMENTATION_ELEMENT));
        text.addHyperlinkListener(editor.createHyperlinkListener());
    }
    
    private Image getIcon(ElementDescriptor desc) {
        if (desc != null && desc.getIcon() != null) {
            return desc.getIcon();
        }
        
        return EditorsPlugin.getAndroidLogo();
    }
}
