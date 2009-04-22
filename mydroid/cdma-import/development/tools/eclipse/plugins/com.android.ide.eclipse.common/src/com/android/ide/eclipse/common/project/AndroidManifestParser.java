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

package com.android.ide.eclipse.common.project;

import com.android.ide.eclipse.common.AndroidConstants;
import com.android.ide.eclipse.common.project.XmlErrorHandler.XmlErrorListener;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class AndroidManifestParser {

    private final static String ATTRIBUTE_PACKAGE = "package"; //$NON-NLS-1$
    private final static String ATTRIBUTE_NAME = "name"; //$NON-NLS-1$
    private final static String ATTRIBUTE_PROCESS = "process"; //$NON-NLS-$
    private final static String ATTRIBUTE_DEBUGGABLE = "debuggable"; //$NON-NLS-$
    private final static String NODE_MANIFEST = "manifest"; //$NON-NLS-1$
    private final static String NODE_APPLICATION = "application"; //$NON-NLS-1$
    private final static String NODE_ACTIVITY = "activity"; //$NON-NLS-1$
    private final static String NODE_SERVICE = "service"; //$NON-NLS-1$
    private final static String NODE_RECEIVER = "receiver"; //$NON-NLS-1$
    private final static String NODE_PROVIDER = "provider"; //$NON-NLS-1$
    private final static String NODE_INTENT = "intent-filter"; //$NON-NLS-1$
    private final static String NODE_ACTION = "action"; //$NON-NLS-1$
    private final static String NODE_CATEGORY = "category"; //$NON-NLS-1$

    private final static int LEVEL_MANIFEST = 0;
    private final static int LEVEL_APPLICATION = 1;
    private final static int LEVEL_ACTIVITY = 2;
    private final static int LEVEL_INTENT_FILTER = 3;
    private final static int LEVEL_CATEGORY = 4;

    private final static String ACTION_MAIN = "android.intent.action.MAIN"; //$NON-NLS-1$
    private final static String CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"; //$NON-NLS-1$
    
    private static class ManifestHandler extends XmlErrorHandler {
        
        //--- data read from the parsing
        
        /** Application package */
        private String mPackage;
        /** List of all activities */
        private final ArrayList<String> mActivities = new ArrayList<String>();
        /** Launcher activity */
        private String mLauncherActivity = null;
        /** list of process names declared by the manifest */
        private Set<String> mProcesses = null;
        /** debuggable attribute value. If null, the attribute is not present. */
        private Boolean mDebuggable = null;

        //--- temporary data/flags used during parsing
        private IJavaProject mJavaProject;
        private boolean mGatherData = false;
        private boolean mMarkErrors = false;
        private int mCurrentLevel = 0;
        private int mValidLevel = 0;
        private boolean mFoundMainAction = false;
        private boolean mFoundLauncherCategory = false;
        private String mCurrentActivity = null;
        private Locator mLocator;
        
        /**
         * 
         * @param manifestFile
         * @param errorListener
         * @param gatherData
         * @param javaProject
         * @param markErrors
         */
        ManifestHandler(IFile manifestFile, XmlErrorListener errorListener,
                boolean gatherData, IJavaProject javaProject, boolean markErrors) {
            super(manifestFile, errorListener);
            mGatherData = gatherData;
            mJavaProject = javaProject;
            mMarkErrors = markErrors;
        }

        /**
         * Returns the package defined in the manifest, if found.
         * @return The package name or null if not found.
         */
        String getPackage() {
            return mPackage;
        }
        
        /** 
         * Returns the list of activities found in the manifest.
         * @return An array of fully qualified class names, or empty if no activity were found.
         */
        String[] getActivities() {
            return mActivities.toArray(new String[mActivities.size()]);
        }
        
        /**
         * Returns the name of one activity found in the manifest, that is configured to show
         * up in the HOME screen.  
         * @return the fully qualified name of a HOME activity or null if none were found. 
         */
        String getLauncherActivity() {
            return mLauncherActivity;
        }
        
        /**
         * Returns the list of process names declared by the manifest.
         */
        String[] getProcesses() {
            if (mProcesses != null) {
                return mProcesses.toArray(new String[mProcesses.size()]);
            }
            
            return new String[0];
        }
        
        /**
         * Returns the debuggable attribute value or null if it is not set.
         */
        Boolean getDebuggable() {
            return mDebuggable;
        }
        
        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#setDocumentLocator(org.xml.sax.Locator)
         */
        @Override
        public void setDocumentLocator(Locator locator) {
            mLocator = locator;
            super.setDocumentLocator(locator);
        }
        
        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String,
         * java.lang.String, org.xml.sax.Attributes)
         */
        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes)
                throws SAXException {
            try {
                if (mGatherData == false) {
                    return;
                }

                // if we're at a valid level
                if (mValidLevel == mCurrentLevel) {
                    String processName;
                    switch (mValidLevel) {
                        case LEVEL_MANIFEST:
                            if (NODE_MANIFEST.equals(localName)) {
                                // lets get the package name.
                                mPackage = getAttributeValue(attributes, ATTRIBUTE_PACKAGE,
                                        false /* hasNamespace */);
                                mValidLevel++;
                            }
                            break;
                        case LEVEL_APPLICATION:
                            if (NODE_APPLICATION.equals(localName)) {
                                processName = getAttributeValue(attributes, ATTRIBUTE_PROCESS,
                                        true /* hasNamespace */);
                                if (processName != null) {
                                    addProcessName(processName);
                                }
                                
                                String debuggable = getAttributeValue(attributes,
                                        ATTRIBUTE_DEBUGGABLE, true /* hasNamespace*/);
                                if (debuggable != null) {
                                    mDebuggable = Boolean.parseBoolean(debuggable);
                                }
                                
                                mValidLevel++;
                            }
                            break;
                        case LEVEL_ACTIVITY:
                            if (NODE_ACTIVITY.equals(localName)) {
                                processActivityNode(attributes);
                                mValidLevel++;
                            } else if (NODE_SERVICE.equals(localName)) {
                                processNode(attributes, AndroidConstants.CLASS_SERVICE);
                                mValidLevel++;
                            } else if (NODE_RECEIVER.equals(localName)) {
                                processNode(attributes, AndroidConstants.CLASS_BROADCASTRECEIVER);
                                mValidLevel++;
                            } else if (NODE_PROVIDER.equals(localName)) {
                                processNode(attributes, AndroidConstants.CLASS_CONTENTPROVIDER);
                                mValidLevel++;
                            }
                            break;
                        case LEVEL_INTENT_FILTER:
                            // only process this level if we are in an activity
                            if (mCurrentActivity != null && NODE_INTENT.equals(localName)) {
                                // if we're at the intent level, lets reset some flag to
                                // be used when parsing the children
                                mFoundMainAction = false;
                                mFoundLauncherCategory = false;
                                mValidLevel++;
                            }
                            break;
                        case LEVEL_CATEGORY:
                            if (mCurrentActivity != null && mLauncherActivity == null) {
                                if (NODE_ACTION.equals(localName)) {
                                    // get the name attribute
                                    if (ACTION_MAIN.equals(
                                            getAttributeValue(attributes, ATTRIBUTE_NAME,
                                                    true /* hasNamespace */))) {
                                        mFoundMainAction = true;
                                    }
                                } else if (NODE_CATEGORY.equals(localName)) {
                                    if (CATEGORY_LAUNCHER.equals(
                                            getAttributeValue(attributes, ATTRIBUTE_NAME,
                                                    true /* hasNamespace */))) {
                                        mFoundLauncherCategory = true;
                                    }
                                }
                                
                                // no need to increase mValidLevel as we don't process anything
                                // below this level.
                            }
                            break;
                    }
                }

                mCurrentLevel++;
            } finally {
                super.startElement(uri, localName, name, attributes);
            }
        }

        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String,
         * java.lang.String)
         */
        @Override
        public void endElement(String uri, String localName, String name) throws SAXException {
            try {
                if (mGatherData == false) {
                    return;
                }
    
                // decrement the levels.
                if (mValidLevel == mCurrentLevel) {
                    mValidLevel--;
                }
                mCurrentLevel--;
                
                // if we're at a valid level
                // process the end of the element
                if (mValidLevel == mCurrentLevel) {
                    switch (mValidLevel) {
                        case LEVEL_ACTIVITY:
                            mCurrentActivity = null;
                            break;
                        case LEVEL_INTENT_FILTER:
                            // if we found both a main action and a launcher category, this is our
                            // launcher activity!
                            if (mCurrentActivity != null &&
                                    mFoundMainAction && mFoundLauncherCategory) {
                                mLauncherActivity = mCurrentActivity;
                            }
                            break;
                        default:
                            break;
                    }
    
                }
            } finally {
                super.endElement(uri, localName, name);
            }
        }
        
        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#error(org.xml.sax.SAXParseException)
         */
        @Override
        public void error(SAXParseException e) throws SAXException {
            if (mMarkErrors) {
                super.error(e);
            }
        }

        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#fatalError(org.xml.sax.SAXParseException)
         */
        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            if (mMarkErrors) {
                super.fatalError(e);
            }
        }

        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#warning(org.xml.sax.SAXParseException)
         */
        @Override
        public void warning(SAXParseException e) throws SAXException {
            if (mMarkErrors) {
                super.warning(e);
            }
        }
        
        /**
         * Processes the activity node.
         * @param attributes the attributes for the activity node.
         * @throws CoreException 
         */
        private void processActivityNode(Attributes attributes) {
            // lets get the activity name, and add it to the list
            String activityName = getAttributeValue(attributes, ATTRIBUTE_NAME,
                    true /* hasNamespace */);
            if (activityName != null) {
                mCurrentActivity = AndroidManifestHelper.combinePackageAndClassName(mPackage,
                        activityName);
                mActivities.add(mCurrentActivity);
                
                if (mMarkErrors) {
                    checkClass(mCurrentActivity, AndroidConstants.CLASS_ACTIVITY,
                            true /* testVisibility */);
                }
            } else {
                // no activity found! Aapt will output an error,
                // so we don't have to do anything
                mCurrentActivity = activityName;
            }
            
            String processName = getAttributeValue(attributes, ATTRIBUTE_PROCESS,
                    true /* hasNamespace */);
            if (processName != null) {
                addProcessName(processName);
            }
        }

        /**
         * Processes the service/receiver/provider nodes.
         * @param attributes the attributes for the activity node.
         * @param superClassName the fully qualified name of the super class that this
         * node is representing
         * @throws CoreException 
         */
        private void processNode(Attributes attributes, String superClassName) {
            // lets get the class name, and check it if required.
            String serviceName = getAttributeValue(attributes, ATTRIBUTE_NAME,
                    true /* hasNamespace */);
            if (serviceName != null) {
                serviceName = AndroidManifestHelper.combinePackageAndClassName(mPackage,
                        serviceName);
                
                if (mMarkErrors) {
                    checkClass(serviceName, superClassName, false /* testVisibility */);
                }
            }
            
            String processName = getAttributeValue(attributes, ATTRIBUTE_PROCESS,
                    true /* hasNamespace */);
            if (processName != null) {
                addProcessName(processName);
            }
        }

        /**
         * Checks that a class is valid and can be used in the Android Manifest.
         * <p/>
         * Errors are put as {@link IMarker} on the manifest file. 
         * @param className the fully qualified name of the class to test.
         * @param superClassName the fully qualified name of the class it is supposed to extend.
         * @param testVisibility if <code>true</code>, the method will check the visibility of
         * the class or of its constructors.
         */
        private void checkClass(String className, String superClassName, boolean testVisibility) {
            // we need to check the validity of the activity.
            String result = BaseProjectHelper.testClassForManifest(mJavaProject,
                    className, superClassName, testVisibility);
            if (result != BaseProjectHelper.TEST_CLASS_OK) {
                // get the line number
                int line = mLocator.getLineNumber();
                
                // mark the file
                IMarker marker = BaseProjectHelper.addMarker(getFile(),
                        AndroidConstants.MARKER_ANDROID,
                        result, line, IMarker.SEVERITY_ERROR);
                
                // add custom attributes to be used by the manifest editor.
                if (marker != null) {
                    try {
                        marker.setAttribute(AndroidConstants.MARKER_ATTR_TYPE,
                                AndroidConstants.MARKER_ATTR_TYPE_ACTIVITY);
                        marker.setAttribute(AndroidConstants.MARKER_ATTR_CLASS, className);
                    } catch (CoreException e) {
                    }
                }
            }
            
        }

        /**
         * Searches through the attributes list for a particular one and returns its value.
         * @param attributes the attribute list to search through
         * @param attributeName the name of the attribute to look for.
         * @param hasNamespace Indicates whether the attribute has an android namespace.
         * @return a String with the value or null if the attribute was not found.
         * @see AndroidConstants#NS_RESOURCES
         */
        private String getAttributeValue(Attributes attributes, String attributeName,
                boolean hasNamespace) {
            int count = attributes.getLength();
            for (int i = 0 ; i < count ; i++) {
                if (attributeName.equals(attributes.getLocalName(i)) &&
                        ((hasNamespace &&
                                AndroidConstants.NS_RESOURCES.equals(attributes.getURI(i))) ||
                                (hasNamespace == false && attributes.getURI(i).length() == 0))) {
                    return attributes.getValue(i);
                }
            }
            
            return null;
        }
        
        private void addProcessName(String processName) {
            if (mProcesses == null) {
                mProcesses = new TreeSet<String>();
            }
            
            mProcesses.add(processName);
        }
    }

    private static SAXParserFactory sParserFactory;
    
    private final String mJavaPackage;
    private final String[] mActivities;
    private final String mLauncherActivity;
    private final String[] mProcesses;
    private final Boolean mDebuggable;

    static {
        sParserFactory = SAXParserFactory.newInstance();
        sParserFactory.setNamespaceAware(true);
    }
    
    /**
     * Parses the Android Manifest, and returns an object containing
     * the result of the parsing.
     * @param javaProject The java project.
     * @param manifestFile the {@link IFile} representing the manifest file.
     * @param errorListener
     * @param gatherData indicates whether the parsing will extract data from the manifest.
     * @param markErrors indicates whether the error found during parsing should put a
     * marker on the file. For class validation errors to put a marker, <code>gatherData</code>
     * must be set to <code>true</code>
     * @return an {@link AndroidManifestParser} or null if the parsing failed.
     * @throws CoreException
     */
    public static AndroidManifestParser parse(IJavaProject javaProject, IFile manifestFile,
            XmlErrorListener errorListener, boolean gatherData, boolean markErrors)
            throws CoreException {
        try {
            SAXParser parser = sParserFactory.newSAXParser();

            ManifestHandler manifestHandler = new ManifestHandler(manifestFile,
                    errorListener, gatherData, javaProject, markErrors);

            parser.parse(new InputSource(manifestFile.getContents()), manifestHandler);
            
            // get the result from the handler
            
            return new AndroidManifestParser(manifestHandler.getPackage(),
                    manifestHandler.getActivities(), manifestHandler.getLauncherActivity(),
                    manifestHandler.getProcesses(), manifestHandler.getDebuggable());
        } catch (ParserConfigurationException e) {
        } catch (SAXException e) {
        } catch (IOException e) {
        } finally {
        }

        return null;
    }

    /**
     * Parses the Android Manifest for the specified project, and returns an object containing
     * the result of the parsing.
     * @param javaProject The java project. Required if <var>markErrors</var> is <code>true</code>
     * @param errorListener the {@link XmlErrorListener} object being notified of the presence
     * of errors. Optional.
     * @param gatherData indicates whether the parsing will extract data from the manifest.
     * @param markErrors indicates whether the error found during parsing should put a
     * marker on the file. For class validation errors to put a marker, <code>gatherData</code>
     * must be set to <code>true</code>
     * @return an {@link AndroidManifestParser} or null if the parsing failed.
     * @throws CoreException
     */
    public static AndroidManifestParser parse(IJavaProject javaProject,
            XmlErrorListener errorListener, boolean gatherData, boolean markErrors)
            throws CoreException {
        try {
            SAXParser parser = sParserFactory.newSAXParser();
            
            IFile manifestFile = AndroidManifestHelper.getManifest(javaProject.getProject());
            if (manifestFile != null) {
                ManifestHandler manifestHandler = new ManifestHandler(manifestFile,
                        errorListener, gatherData, javaProject, markErrors);

                parser.parse(new InputSource(manifestFile.getContents()), manifestHandler);
                
                // get the result from the handler
                return new AndroidManifestParser(manifestHandler.getPackage(),
                        manifestHandler.getActivities(), manifestHandler.getLauncherActivity(),
                        manifestHandler.getProcesses(), manifestHandler.getDebuggable());
            }
        } catch (ParserConfigurationException e) {
        } catch (SAXException e) {
        } catch (IOException e) {
        } finally {
        }
        
        return null;
    }

    /**
     * Parses the manifest file, collects data, and checks for errors.
     * @param javaProject The java project. Required.
     * @param manifestFile
     * @param errorListener the {@link XmlErrorListener} object being notified of the presence
     * of errors. Optional.
     * @return an {@link AndroidManifestParser} or null if the parsing failed.
     * @throws CoreException
     * @see {@link #parse(IJavaProject, IFile, XmlErrorListener, boolean, boolean)}
     */
    public static AndroidManifestParser parseForError(IJavaProject javaProject, IFile manifestFile,
            XmlErrorListener errorListener) throws CoreException {
        return parse(javaProject, manifestFile, errorListener, true, true);
    }

    /**
     * Parses the manifest file, and collects data.
     * @param manifestFile
     * @param errorListener the {@link XmlErrorListener} object being notified of the presence
     * of errors. Optional.
     * @return an {@link AndroidManifestParser} or null if the parsing failed.
     * @throws CoreException
     * @see {@link #parse(IJavaProject, IFile, XmlErrorListener, boolean, boolean)}
     */
    public static AndroidManifestParser parseForData(IFile manifestFile) throws CoreException {
        return parse(null /* javaProject */, manifestFile, null /* errorListener */,
                true /* gatherData */, false /* markErrors */);
    }

    /**
     * Returns the package defined in the manifest, if found.
     * @return The package name or null if not found.
     */
    public String getPackage() {
        return mJavaPackage;
    }

    /** 
     * Returns the list of activities found in the manifest.
     * @return An array of fully qualified class names, or empty if no activity were found.
     */
    public String[] getActivities() {
        return mActivities;
    }

    /**
     * Returns the name of one activity found in the manifest, that is configured to show
     * up in the HOME screen.  
     * @return the fully qualified name of a HOME activity or null if none were found. 
     */
    public String getLauncherActivity() {
        return mLauncherActivity;
    }
    
    /**
     * Returns the list of process names declared by the manifest.
     */
    public String[] getProcesses() {
        return mProcesses;
    }
    
    /**
     * Returns the debuggable attribute value or <code>null</code> if it is not set.
     */
    public Boolean getDebuggable() {
        return mDebuggable;
    }
    
    /**
     * Private constructor to enforce using
     * {@link #parse(IJavaProject, XmlErrorListener, boolean, boolean)},
     * {@link #parse(IJavaProject, IFile, XmlErrorListener, boolean, boolean)},
     * or {@link #parseForError(IJavaProject, IFile, XmlErrorListener)} to get an
     * {@link AndroidManifestParser} object.
     * @param javaPackage the package parsed from the manifest.
     * @param activities the list of activities parsed from the manifest.
     * @param launcherActivity the launcher activity parser from the manifest.
     * @param processes the list of custom processes declared in the manifest.
     * @param debuggable the debuggable attribute.
     */
    private AndroidManifestParser(String javaPackage, String[] activities,
            String launcherActivity, String[] processes, Boolean debuggable) {
        mJavaPackage = javaPackage;
        mActivities = activities;
        mLauncherActivity = launcherActivity;
        mProcesses = processes;
        mDebuggable = debuggable;
    }
}
