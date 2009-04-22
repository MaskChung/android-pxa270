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

package com.android.ide.eclipse.editors.manifest.descriptors;

import com.android.ide.eclipse.editors.descriptors.TextAttributeDescriptor;
import com.android.ide.eclipse.editors.manifest.model.UiPackageAttributeNode;
import com.android.ide.eclipse.editors.uimodel.UiAttributeNode;
import com.android.ide.eclipse.editors.uimodel.UiElementNode;

/**
 * Describes a package XML attribute. It is displayed by a {@link UiPackageAttributeNode}.
 */
public class PackageAttributeDescriptor extends TextAttributeDescriptor {

    public PackageAttributeDescriptor(String xmlLocalName, String uiName, String nsUri,
            String tooltip) {
        super(xmlLocalName, uiName, nsUri, tooltip);
    }
    
    /**
     * @return A new {@link UiPackageAttributeNode} linked to this descriptor.
     */
    @Override
    public UiAttributeNode createUiNode(UiElementNode uiParent) {
        return new UiPackageAttributeNode(this, uiParent);
    }
}
