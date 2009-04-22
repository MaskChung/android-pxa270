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

package com.android.ide.eclipse.adt.project.internal;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.project.AndroidNature;
import com.android.ide.eclipse.adt.project.ProjectHelper;
import com.android.ide.eclipse.common.AndroidConstants;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.actions.OpenJavaPerspectiveAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A "New Android Project" Wizard.
 * <p/>
 * Note: this class is public so that it can be accessed from unit tests.
 * It is however an internal class. Its API may change without notice.
 * It should semantically be considered as a private final class.
 * Do not derive from this class. 

 */
public class NewProjectWizard extends Wizard implements INewWizard {

    private static final String PARAM_SDK_TOOLS_DIR = "ANDROID_SDK_TOOLS"; //$NON-NLS-1$
    private static final String PARAM_ACTIVITY = "ACTIVITY_NAME"; //$NON-NLS-1$
    private static final String PARAM_APPLICATION = "APPLICATION_NAME"; //$NON-NLS-1$
    private static final String PARAM_PACKAGE = "PACKAGE"; //$NON-NLS-1$
    private static final String PARAM_PROJECT = "PROJECT_NAME"; //$NON-NLS-1$
    private static final String PARAM_STRING_NAME = "STRING_NAME"; //$NON-NLS-1$
    private static final String PARAM_STRING_CONTENT = "STRING_CONTENT"; //$NON-NLS-1$
    private static final String PARAM_IS_NEW_PROJECT = "IS_NEW_PROJECT"; //$NON-NLS-1$
    private static final String PARAM_SRC_FOLDER = "SRC_FOLDER"; //$NON-NLS-1$

    private static final String PH_ACTIVITIES = "ACTIVITIES"; //$NON-NLS-1$
    private static final String PH_INTENT_FILTERS = "INTENT_FILTERS"; //$NON-NLS-1$
    private static final String PH_STRINGS = "STRINGS"; //$NON-NLS-1$

    private static final String BIN_DIRECTORY =
        AndroidConstants.FD_BINARIES + AndroidConstants.WS_SEP;
    private static final String RES_DIRECTORY =
        AndroidConstants.FD_RESOURCES + AndroidConstants.WS_SEP;
    private static final String ASSETS_DIRECTORY =
        AndroidConstants.FD_ASSETS + AndroidConstants.WS_SEP;
    private static final String DRAWABLE_DIRECTORY =
        AndroidConstants.FD_DRAWABLE + AndroidConstants.WS_SEP;
    private static final String LAYOUT_DIRECTORY =
        AndroidConstants.FD_LAYOUT + AndroidConstants.WS_SEP;
    private static final String VALUES_DIRECTORY =
        AndroidConstants.FD_VALUES + AndroidConstants.WS_SEP;

    private static final String TEMPLATES_DIRECTORY = "templates/"; //$NON-NLS-1$
    private static final String TEMPLATE_MANIFEST = TEMPLATES_DIRECTORY
            + "AndroidManifest.template"; //$NON-NLS-1$
    private static final String TEMPLATE_ACTIVITIES = TEMPLATES_DIRECTORY
            + "activity.template"; //$NON-NLS-1$
    private static final String TEMPLATE_INTENT_LAUNCHER = TEMPLATES_DIRECTORY
    		+ "launcher_intent_filter.template"; //$NON-NLS-1$
    
    private static final String TEMPLATE_STRINGS = TEMPLATES_DIRECTORY
            + "strings.template"; //$NON-NLS-1$
    private static final String TEMPLATE_STRING = TEMPLATES_DIRECTORY
            + "string.template"; //$NON-NLS-1$
    private static final String ICON = "icon.png"; //$NON-NLS-1$

    private static final String STRINGS_FILE = "strings.xml"; //$NON-NLS-1$

    private static final String STRING_RSRC_PREFIX = "@string/"; //$NON-NLS-1$
    private static final String STRING_APP_NAME = "app_name"; //$NON-NLS-1$
    private static final String STRING_HELLO_WORLD = "hello"; //$NON-NLS-1$

    private static final String[] DEFAULT_DIRECTORIES = new String[] {
            BIN_DIRECTORY, RES_DIRECTORY, ASSETS_DIRECTORY };
    private static final String[] RES_DIRECTORIES = new String[] {
            DRAWABLE_DIRECTORY, LAYOUT_DIRECTORY, VALUES_DIRECTORY};

    private static final String PROJECT_LOGO_LARGE = "icons/android_large.png"; //$NON-NLS-1$
    private static final String JAVA_ACTIVITY_TEMPLATE = "java_file.template"; //$NON-NLS-1$
    private static final String LAYOUT_TEMPLATE = "layout.template"; //$NON-NLS-1$
    private static final String MAIN_LAYOUT_XML = "main.xml"; //$NON-NLS-1$
    
    protected static final String MAIN_PAGE_NAME = "newAndroidProjectPage"; //$NON-NLS-1$

    private NewProjectCreationPage mMainPage;

    /**
     * Initializes this creation wizard using the passed workbench and object
     * selection. Inherited from org.eclipse.ui.IWorkbenchWizard
     */
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        setHelpAvailable(false); // TODO have help
        setWindowTitle("New Android Project");
        setImageDescriptor();

        mMainPage = createMainPage();
        mMainPage.setTitle("New Android Project");
        mMainPage.setDescription("Creates a new Android Project resource.");
    }
    
    /**
     * Creates the wizard page.
     * <p/>
     * Please do NOT override this method.
     * <p/>
     * This is protected so that it can be overridden by unit tests.
     * However the contract of this class is private and NO ATTEMPT will be made
     * to maintain compatibility between different versions of the plugin.
     */
    protected NewProjectCreationPage createMainPage() {
        return new NewProjectCreationPage(MAIN_PAGE_NAME);
    }

    // -- Methods inherited from org.eclipse.jface.wizard.Wizard --
    // The Wizard class implements most defaults and boilerplate code needed by
    // IWizard

    /**
     * Adds pages to this wizard.
     */
    @Override
    public void addPages() {
        addPage(mMainPage);
    }

    /**
     * Performs any actions appropriate in response to the user having pressed
     * the Finish button, or refuse if finishing now is not permitted: here, it
     * actually creates the workspace project and then switch to the Java
     * perspective.
     *
     * @return True
     */
    @Override
    public boolean performFinish() {
        if (!createAndroidProject()) {
            return false;
        }

        // Open the default Java Perspective
        OpenJavaPerspectiveAction action = new OpenJavaPerspectiveAction();
        action.run();
        return true;
    }

    // -- Custom Methods --

    /**
     * Before actually creating the project for a new project (as opposed to using an
     * existing project), we check if the target location is a directory that either does
     * not exist or is empty.
     * 
     * If it's not empty, ask the user for confirmation.
     *  
     * @param destination The destination folder where the new project is to be created.
     * @return True if the destination doesn't exist yet or is an empty directory or is
     *         accepted by the user.
     */
    private boolean validateNewProjectLocationIsEmpty(IPath destination) {
        File f = new File(destination.toOSString());
        if (f.isDirectory() && f.list().length > 0) {
            return AdtPlugin.displayPrompt("New Android Project",
                    "You are going to create a new Android Project in an existing, non-empty, directory. Are you sure you want to proceed?");
        }
        return true;
    }

    /**
     * Creates the android project.
     * @return True if the project could be created.
     */
    private boolean createAndroidProject() {
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        final IProject project = workspace.getRoot().getProject(mMainPage.getProjectName());
        final IProjectDescription description = workspace.newProjectDescription(project.getName());

        final Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(PARAM_PROJECT, mMainPage.getProjectName());
        parameters.put(PARAM_PACKAGE, mMainPage.getPackageName());
        parameters.put(PARAM_APPLICATION, STRING_RSRC_PREFIX + STRING_APP_NAME);
        parameters.put(PARAM_SDK_TOOLS_DIR, AdtPlugin.getOsSdkToolsFolder());
        parameters.put(PARAM_IS_NEW_PROJECT, Boolean.toString(mMainPage.isNewProject()));
        parameters.put(PARAM_SRC_FOLDER, mMainPage.getSourceFolder());

        if (mMainPage.isCreateActivity()) {
            // An activity name can be of the form ".package.Class" or ".Class".
            // The initial dot is ignored, as it is always added later in the templates.
            String activityName = mMainPage.getActivityName();
            if (activityName.startsWith(".")) { //$NON-NLS-1$
                activityName = activityName.substring(1);
            }
            parameters.put(PARAM_ACTIVITY, activityName);
        }

        // create a dictionary of string that will contain name+content.
        // we'll put all the strings into values/strings.xml
        final HashMap<String, String> stringDictionary = new HashMap<String, String>();
        stringDictionary.put(STRING_APP_NAME, mMainPage.getApplicationName());

        IPath path = mMainPage.getLocationPath();
        IPath defaultLocation = Platform.getLocation();
        if (!path.equals(defaultLocation)) {
            description.setLocation(path);
        }
        
        if (mMainPage.isNewProject() && !mMainPage.useDefaultLocation() &&
                !validateNewProjectLocationIsEmpty(path)) {
            return false;
        }

        // Create a monitored operation to create the actual project
        WorkspaceModifyOperation op = new WorkspaceModifyOperation() {
            @Override
            protected void execute(IProgressMonitor monitor) throws InvocationTargetException {
                createProjectAsync(project, description, monitor, parameters, stringDictionary);
            }
        };

        // Run the operation in a different thread
        runAsyncOperation(op);
        return true;
    }

    /**
     * Runs the operation in a different thread and display generated
     * exceptions.
     *
     * @param op The asynchronous operation to run.
     */
    private void runAsyncOperation(WorkspaceModifyOperation op) {
        try {
            getContainer().run(true /* fork */, true /* cancelable */, op);
        } catch (InvocationTargetException e) {
            // The runnable threw an exception
            Throwable t = e.getTargetException();
            if (t instanceof CoreException) {
                CoreException core = (CoreException) t;
                if (core.getStatus().getCode() == IResourceStatus.CASE_VARIANT_EXISTS) {
                    // The error indicates the file system is not case sensitive
                    // and there's a resource with a similar name.
                    MessageDialog.openError(getShell(), "Error", "Error: Case Variant Exists");
                } else {
                    ErrorDialog.openError(getShell(), "Error", null, core.getStatus());
                }
            } else {
                // Some other kind of exception
                MessageDialog.openError(getShell(), "Error", t.getMessage());
            }
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates the actual project, sets its nature and adds the required folders
     * and files to it. This is run asynchronously in a different thread.
     *
     * @param project The project to create.
     * @param description A description of the project.
     * @param monitor An existing monitor.
     * @param parameters Template parameters.
     * @param stringDictionary String definition.
     * @throws InvocationTargetException to wrap any unmanaged exception and
     *         return it to the calling thread. The method can fail if it fails
     *         to create or modify the project or if it is canceled by the user.
     */
    private void createProjectAsync(IProject project, IProjectDescription description,
            IProgressMonitor monitor, Map<String, String> parameters,
            Map<String, String> stringDictionary)
            throws InvocationTargetException {
        monitor.beginTask("Create Android Project", 100);
        try {
            // Create project and open it
            project.create(description, new SubProgressMonitor(monitor, 10));
            if (monitor.isCanceled()) throw new OperationCanceledException();
            project.open(IResource.BACKGROUND_REFRESH, new SubProgressMonitor(monitor, 10));

            // Add the Java and android nature to the project
            AndroidNature.setupProjectNatures(project, monitor);

            // Create folders in the project if they don't already exist
            addDefaultDirectories(project, AndroidConstants.WS_ROOT, DEFAULT_DIRECTORIES, monitor);
            String[] sourceFolder = new String[] { parameters.get(PARAM_SRC_FOLDER) };
            addDefaultDirectories(project, AndroidConstants.WS_ROOT, sourceFolder, monitor);

            // Create the resource folders in the project if they don't already exist.
            addDefaultDirectories(project, RES_DIRECTORY, RES_DIRECTORIES, monitor);

            // Setup class path
            IJavaProject javaProject = JavaCore.create(project);
            setupSourceFolder(javaProject, sourceFolder[0], monitor);

            if (Boolean.parseBoolean(parameters.get(PARAM_IS_NEW_PROJECT))) {
                // Create files in the project if they don't already exist
                addManifest(project, parameters, stringDictionary, monitor);

                // add the default app icon
                addIcon(project, monitor);

                // Create the default package components
                addSampleCode(project, sourceFolder[0], parameters, stringDictionary, monitor);

                // add the string definition file if needed
                if (stringDictionary.size() > 0) {
                    addStringDictionaryFile(project, stringDictionary, monitor);
                }

                // Set output location
                javaProject.setOutputLocation(project.getFolder(BIN_DIRECTORY).getFullPath(),
                        monitor);
            }

            // Fix the project to make sure all properties are as expected.
            // Necessary for existing projects and good for new ones to.
            ProjectHelper.fixProject(project);

        } catch (CoreException e) {
            throw new InvocationTargetException(e);
        } catch (IOException e) {
            throw new InvocationTargetException(e);
        } finally {
            monitor.done();
        }
    }

    /**
     * Adds default directories to the project.
     *
     * @param project The Java Project to update.
     * @param parentFolder The path of the parent folder. Must end with a
     *        separator.
     * @param folders Folders to be added.
     * @param monitor An existing monitor.
     * @throws CoreException if the method fails to create the directories in
     *         the project.
     */
    private void addDefaultDirectories(IProject project, String parentFolder,
            String[] folders, IProgressMonitor monitor) throws CoreException {
        for (String name : folders) {
            if (name.length() > 0) {
                IFolder folder = project.getFolder(parentFolder + name);
                if (!folder.exists()) {
                    folder.create(true /* force */, true /* local */,
                            new SubProgressMonitor(monitor, 10));
                }
            }
        }
    }

    /**
     * Adds the manifest to the project.
     *
     * @param project The Java Project to update.
     * @param parameters Template Parameters.
     * @param stringDictionary String List to be added to a string definition
     *        file. This map will be filled by this method.
     * @param monitor An existing monitor.
     * @throws CoreException if the method fails to update the project.
     * @throws IOException if the method fails to create the files in the
     *         project.
     */
    private void addManifest(IProject project, Map<String, String> parameters,
            Map<String, String> stringDictionary, IProgressMonitor monitor)
            throws CoreException, IOException {

        // get IFile to the manifest and check if it's not already there.
        IFile file = project.getFile(AndroidConstants.FN_ANDROID_MANIFEST);
        if (!file.exists()) {

            // Read manifest template
            String manifestTemplate = AdtPlugin.readEmbeddedTextFile(TEMPLATE_MANIFEST);

            // Replace all keyword parameters
            manifestTemplate = replaceParameters(manifestTemplate, parameters);

            if (parameters.containsKey(PARAM_ACTIVITY)) {
                // now get the activity template
                String activityTemplate = AdtPlugin.readEmbeddedTextFile(TEMPLATE_ACTIVITIES);
    
                // Replace all keyword parameters to make main activity.
                String activities = replaceParameters(activityTemplate, parameters);
    
                // set the intent.
                String intent = AdtPlugin.readEmbeddedTextFile(TEMPLATE_INTENT_LAUNCHER);
                
                // set the intent to the main activity
                activities = activities.replaceAll(PH_INTENT_FILTERS, intent);
    
                // set the activity(ies) in the manifest
                manifestTemplate = manifestTemplate.replaceAll(PH_ACTIVITIES, activities);
            } else {
                // remove the activity(ies) from the manifest
                manifestTemplate = manifestTemplate.replaceAll(PH_ACTIVITIES, "");
            }

            // Save in the project as UTF-8
            InputStream stream = new ByteArrayInputStream(
                    manifestTemplate.getBytes("UTF-8")); //$NON-NLS-1$
            file.create(stream, false /* force */, new SubProgressMonitor(monitor, 10));
        }
    }

    /**
     * Adds the string resource file.
     *
     * @param project The Java Project to update.
     * @param strings The list of strings to be added to the string file.
     * @param monitor An existing monitor.
     * @throws CoreException if the method fails to update the project.
     * @throws IOException if the method fails to create the files in the
     *         project.
     */
    private void addStringDictionaryFile(IProject project,
            Map<String, String> strings, IProgressMonitor monitor)
            throws CoreException, IOException {

        // create the IFile object and check if the file doesn't already exist.
        IFile file = project.getFile(RES_DIRECTORY + AndroidConstants.WS_SEP
                                     + VALUES_DIRECTORY + AndroidConstants.WS_SEP + STRINGS_FILE);
        if (!file.exists()) {
            // get the Strings.xml template
            String stringDefinitionTemplate = AdtPlugin.readEmbeddedTextFile(TEMPLATE_STRINGS);

            // get the template for one string
            String stringTemplate = AdtPlugin.readEmbeddedTextFile(TEMPLATE_STRING);

            // get all the string names
            Set<String> stringNames = strings.keySet();

            // loop on it and create the string definitions
            StringBuilder stringNodes = new StringBuilder();
            for (String key : stringNames) {
                // get the value from the key
                String value = strings.get(key);

                // place them in the template
                String stringDef = stringTemplate.replace(PARAM_STRING_NAME, key);
                stringDef = stringDef.replace(PARAM_STRING_CONTENT, value);

                // append to the other string
                if (stringNodes.length() > 0) {
                    stringNodes.append("\n");
                }
                stringNodes.append(stringDef);
            }

            // put the string nodes in the Strings.xml template
            stringDefinitionTemplate = stringDefinitionTemplate.replace(PH_STRINGS,
                                                                        stringNodes.toString());

            // write the file as UTF-8
            InputStream stream = new ByteArrayInputStream(
                    stringDefinitionTemplate.getBytes("UTF-8")); //$NON-NLS-1$
            file.create(stream, false /* force */, new SubProgressMonitor(monitor, 10));
        }
    }


    /**
     * Adds default application icon to the project.
     *
     * @param project The Java Project to update.
     * @param monitor An existing monitor.
     * @throws CoreException if the method fails to update the project.
     */
    private void addIcon(IProject project, IProgressMonitor monitor)
            throws CoreException {
        IFile file = project.getFile(RES_DIRECTORY + AndroidConstants.WS_SEP
                                     + DRAWABLE_DIRECTORY + AndroidConstants.WS_SEP + ICON);
        if (!file.exists()) {
            // read the content from the template
            byte[] buffer = AdtPlugin.readEmbeddedFile(TEMPLATES_DIRECTORY + ICON);

            // if valid
            if (buffer != null) {
                // Save in the project
                InputStream stream = new ByteArrayInputStream(buffer);
                file.create(stream, false /* force */, new SubProgressMonitor(monitor, 10));
            }
        }
    }

    /**
     * Creates the package folder and copies the sample code in the project.
     *
     * @param project The Java Project to update.
     * @param parameters Template Parameters.
     * @param stringDictionary String List to be added to a string definition
     *        file. This map will be filled by this method.
     * @param monitor An existing monitor.
     * @throws CoreException if the method fails to update the project.
     * @throws IOException if the method fails to create the files in the
     *         project.
     */
    private void addSampleCode(IProject project, String sourceFolder,
            Map<String, String> parameters, Map<String, String> stringDictionary,
            IProgressMonitor monitor) throws CoreException, IOException {
        // create the java package directories.
        IFolder pkgFolder = project.getFolder(sourceFolder);
        String packageName = parameters.get(PARAM_PACKAGE);
        
        // The PARAM_ACTIVITY key will be absent if no activity should be created,
        // in which case activityName will be null.
        String activityName = parameters.get(PARAM_ACTIVITY);
        Map<String, String> java_activity_parameters = parameters;
        if (activityName != null) {
            if (activityName.indexOf('.') >= 0) {
                // There are package names in the activity name. Transform packageName to add
                // those sub packages and remove them from activityName.
                packageName += "." + activityName; //$NON-NLS-1$
                int pos = packageName.lastIndexOf('.');
                activityName = packageName.substring(pos + 1);
                packageName = packageName.substring(0, pos);
                
                // Also update the values used in the JAVA_FILE_TEMPLATE below
                // (but not the ones from the manifest so don't change the caller's dictionary)
                java_activity_parameters = new HashMap<String, String>(parameters);
                java_activity_parameters.put(PARAM_PACKAGE, packageName);
                java_activity_parameters.put(PARAM_ACTIVITY, activityName);
            }
        }

        String[] components = packageName.split(AndroidConstants.RE_DOT);
        for (String component : components) {
            pkgFolder = pkgFolder.getFolder(component);
            if (!pkgFolder.exists()) {
                pkgFolder.create(true /* force */, true /* local */,
                        new SubProgressMonitor(monitor, 10));
            }
        }

        if (activityName != null) {
            // create the main activity Java file
            String activityJava = activityName + AndroidConstants.DOT_JAVA;
            IFile file = pkgFolder.getFile(activityJava);
            if (!file.exists()) {
                copyFile(JAVA_ACTIVITY_TEMPLATE, file, java_activity_parameters, monitor);
            }
        }

        // create the layout file
        IFolder layoutfolder = project.getFolder(RES_DIRECTORY).getFolder(LAYOUT_DIRECTORY);
        IFile file = layoutfolder.getFile(MAIN_LAYOUT_XML);
        if (!file.exists()) {
            copyFile(LAYOUT_TEMPLATE, file, parameters, monitor);
            if (activityName != null) {
                stringDictionary.put(STRING_HELLO_WORLD, "Hello World, " + activityName + "!");
            } else {
                stringDictionary.put(STRING_HELLO_WORLD, "Hello World!");
            }
        }
    }

    /**
     * Adds the given folder to the project's class path.
     *
     * @param javaProject The Java Project to update.
     * @param sourceFolder Template Parameters.
     * @param monitor An existing monitor.
     * @throws JavaModelException if the classpath could not be set.
     */
    private void setupSourceFolder(IJavaProject javaProject, String sourceFolder,
            IProgressMonitor monitor) throws JavaModelException {
        IProject project = javaProject.getProject();

        // Add "src" to class path
        IFolder srcFolder = project.getFolder(sourceFolder);

        IClasspathEntry[] entries = javaProject.getRawClasspath();
        entries = removeSourceClasspath(entries, srcFolder);
        entries = removeSourceClasspath(entries, srcFolder.getParent());

        entries = ProjectHelper.addEntryToClasspath(entries,
                JavaCore.newSourceEntry(srcFolder.getFullPath()));

        javaProject.setRawClasspath(entries, new SubProgressMonitor(monitor, 10));
    }


    /**
     * Removes the corresponding source folder from the class path entries if
     * found.
     *
     * @param entries The class path entries to read. A copy will be returned.
     * @param folder The parent source folder to remove.
     * @return A new class path entries array.
     */
    private IClasspathEntry[] removeSourceClasspath(IClasspathEntry[] entries, IContainer folder) {
        if (folder == null) {
            return entries;
        }
        IClasspathEntry source = JavaCore.newSourceEntry(folder.getFullPath());
        int n = entries.length;
        for (int i = n - 1; i >= 0; i--) {
            if (entries[i].equals(source)) {
                IClasspathEntry[] newEntries = new IClasspathEntry[n - 1];
                if (i > 0) System.arraycopy(entries, 0, newEntries, 0, i);
                if (i < n - 1) System.arraycopy(entries, i + 1, newEntries, i, n - i - 1);
                n--;
                entries = newEntries;
            }
        }
        return entries;
    }


    /**
     * Copies the given file from our resource folder to the new project.
     * Expects the file to the US-ASCII or UTF-8 encoded.
     *
     * @throws CoreException from IFile if failing to create the new file.
     * @throws MalformedURLException from URL if failing to interpret the URL.
     * @throws FileNotFoundException from RandomAccessFile.
     * @throws IOException from RandomAccessFile.length() if can't determine the
     *         length.
     */
    private void copyFile(String resourceFilename, IFile destFile,
            Map<String, String> parameters, IProgressMonitor monitor)
            throws CoreException, IOException {

        // Read existing file.
        String template = AdtPlugin.readEmbeddedTextFile(
                TEMPLATES_DIRECTORY + resourceFilename);

        // Replace all keyword parameters
        template = replaceParameters(template, parameters);

        // Save in the project as UTF-8
        InputStream stream = new ByteArrayInputStream(template.getBytes("UTF-8")); //$NON-NLS-1$
        destFile.create(stream, false /* force */, new SubProgressMonitor(monitor, 10));
    }

    /**
     * Returns an image descriptor for the wizard logo.
     */
    private void setImageDescriptor() {
        ImageDescriptor desc = AdtPlugin.getImageDescriptor(PROJECT_LOGO_LARGE);
        setDefaultPageImageDescriptor(desc);
    }

    /**
     * Replaces placeholders found in a string with values.
     *
     * @param str the string to search for placeholders.
     * @param parameters a map of <placeholder, Value> to search for in the
     *        string
     * @return A new String object with the placeholder replaced by the values.
     */
    private String replaceParameters(String str, Map<String, String> parameters) {
        for (String key : parameters.keySet()) {
            str = str.replaceAll(key, parameters.get(key));
        }

        return str;
    }
}
