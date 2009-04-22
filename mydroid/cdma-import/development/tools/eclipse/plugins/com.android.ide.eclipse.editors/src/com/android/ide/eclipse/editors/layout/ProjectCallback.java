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
import com.android.ide.eclipse.common.project.AndroidManifestHelper;
import com.android.ide.eclipse.editors.resources.manager.ProjectClassLoader;
import com.android.ide.eclipse.editors.resources.manager.ProjectResources;
import com.android.layoutlib.api.IProjectCallback;

import org.eclipse.core.resources.IProject;

import java.lang.reflect.Constructor;
import java.util.HashMap;

/**
 * Loader for Android Project class in order to use them in the layout editor.
 */
public final class ProjectCallback implements IProjectCallback {
    
    private final HashMap<String, Class<?>> mLoadedClasses = new HashMap<String, Class<?>>();
    private final IProject mProject;
    private final ClassLoader mParentClassLoader;
    private final ProjectResources mProjectRes;
    private boolean mUsed = false;
    private String mNamespace;
    
    ProjectCallback(ClassLoader classLoader, ProjectResources projectRes, IProject project) {
        mParentClassLoader = classLoader;
        mProjectRes = projectRes;
        mProject = project;
    }


    /**
     * {@inheritDoc}
     * 
     * This implementation goes through the output directory of the Eclipse project and loads the
     * <code>.class</code> file directly.
     */
    @SuppressWarnings("unchecked")
    public Object loadView(String className, Class[] constructorSignature,
            Object[] constructorParameters)
            throws ClassNotFoundException, Exception {
        
        // look for a cached version
        Class<?> clazz = mLoadedClasses.get(className);
        if (clazz != null) {
            return instantiateClass(clazz, constructorSignature, constructorParameters);
        }
        
        // load the class.
        ProjectClassLoader loader = new ProjectClassLoader(mParentClassLoader, mProject);
        clazz = loader.loadClass(className);
        
        if (clazz != null) {
            mUsed = true;
            mLoadedClasses.put(className, clazz);
            return instantiateClass(clazz, constructorSignature, constructorParameters);
        }
        
        return null;
    }
    
    /**
     * {@inheritDoc}
     * 
     * Returns the namespace for the project. The namespace contains a standard part + the
     * application package.
     */
    public String getNamespace() {
        if (mNamespace == null) {
            AndroidManifestHelper manifest = new AndroidManifestHelper(mProject);
            String javaPackage = manifest.getPackageName();
            
            mNamespace = String.format(AndroidConstants.NS_CUSTOM_RESOURCES, javaPackage);
        }

        return mNamespace;
    }

    /*
     * (non-Javadoc)
     * @see com.android.layoutlib.api.IProjectCallback#resolveResourceValue(int)
     */
    public String[] resolveResourceValue(int id) {
        if (mProjectRes != null) {
            return mProjectRes.resolveResourceValue(id);
        }

        return null;
    }

    /*
     * (non-Javadoc)
     * @see com.android.layoutlib.api.IProjectCallback#resolveResourceValue(int[])
     */
    public String resolveResourceValue(int[] id) {
        if (mProjectRes != null) {
            return mProjectRes.resolveResourceValue(id);
        }
        
        return null;
    }
    
    /*
     * (non-Javadoc)
     * @see com.android.layoutlib.api.IProjectCallback#getResourceValue(java.lang.String, java.lang.String)
     */
    public Integer getResourceValue(String type, String name) {
        if (mProjectRes != null) {
            return mProjectRes.getResourceValue(type, name);
        }
        
        return null;
    }
    
    /**
     * Returns whether the loader has received requests to load custom views.
     * <p/>This allows to efficiently only recreate when needed upon code change in the project.
     */
    boolean isUsed() {
        return mUsed;
    }

    /**
     * Instantiate a class object, using a specific constructor and parameters.
     * @param clazz the class to instantiate
     * @param constructorSignature the signature of the constructor to use
     * @param constructorParameters the parameters to use in the constructor.
     * @return
     * @throws Exception 
     */
    @SuppressWarnings("unchecked")
    private Object instantiateClass(Class<?> clazz, Class[] constructorSignature,
            Object[] constructorParameters) throws Exception {
        Constructor<?> constructor = clazz.getConstructor(constructorSignature);
        constructor.setAccessible(true);
        return constructor.newInstance(constructorParameters);
    }
}
