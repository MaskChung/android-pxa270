/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.content.pm;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AndroidException;
import android.util.DisplayMetrics;

import java.io.File;
import java.util.List;

/**
 * Class for retrieving various kinds of information related to the application
 * packages that are currently installed on the device.
 *
 * You can find this class through {@link Context#getPackageManager}.
 */
public abstract class PackageManager {

    /**
     * This exception is thrown when a given package, application, or component
     * name can not be found.
     */
    public static class NameNotFoundException extends AndroidException {
        public NameNotFoundException() {
        }

        public NameNotFoundException(String name) {
            super(name);
        }
    }

    /**
     * {@link PackageInfo} flag: return information about
     * activities in the package in {@link PackageInfo#activities}.
     */
    public static final int GET_ACTIVITIES              = 0x00000001;

    /**
     * {@link PackageInfo} flag: return information about
     * intent receivers in the package in
     * {@link PackageInfo#receivers}.
     */
    public static final int GET_RECEIVERS               = 0x00000002;

    /**
     * {@link PackageInfo} flag: return information about
     * services in the package in {@link PackageInfo#services}.
     */
    public static final int GET_SERVICES                = 0x00000004;

    /**
     * {@link PackageInfo} flag: return information about
     * content providers in the package in
     * {@link PackageInfo#providers}.
     */
    public static final int GET_PROVIDERS               = 0x00000008;

    /**
     * {@link PackageInfo} flag: return information about
     * instrumentation in the package in
     * {@link PackageInfo#instrumentation}.
     */
    public static final int GET_INSTRUMENTATION         = 0x00000010;

    /**
     * {@link PackageInfo} flag: return information about the
     * intent filters supported by the activity.
     */
    public static final int GET_INTENT_FILTERS          = 0x00000020;

    /**
     * {@link PackageInfo} flag: return information about the
     * signatures included in the package.
     */
    public static final int GET_SIGNATURES          = 0x00000040;

    /**
     * {@link ResolveInfo} flag: return the IntentFilter that
     * was matched for a particular ResolveInfo in
     * {@link ResolveInfo#filter}.
     */
    public static final int GET_RESOLVED_FILTER         = 0x00000040;

    /**
     * {@link ComponentInfo} flag: return the {@link ComponentInfo#metaData}
     * data {@link android.os.Bundle}s that are associated with a component.
     * This applies for any API returning a ComponentInfo subclass.
     */
    public static final int GET_META_DATA               = 0x00000080;

    /**
     * {@link PackageInfo} flag: return the
     * {@link PackageInfo#gids group ids} that are associated with an
     * application.
     * This applies for any API returning an PackageInfo class, either
     * directly or nested inside of another.
     */
    public static final int GET_GIDS                    = 0x00000100;

    /**
     * {@link PackageInfo} flag: include disabled components in the returned info.
     */
    public static final int GET_DISABLED_COMPONENTS     = 0x00000200;

    /**
     * {@link ApplicationInfo} flag: return the
     * {@link ApplicationInfo#sharedLibraryFiles paths to the shared libraries}
     * that are associated with an application.
     * This applies for any API returning an ApplicationInfo class, either
     * directly or nested inside of another.
     */
    public static final int GET_SHARED_LIBRARY_FILES    = 0x00000400;

    /**
     * {@link ProviderInfo} flag: return the
     * {@link ProviderInfo#uriPermissionPatterns URI permission patterns}
     * that are associated with a content provider.
     * This applies for any API returning an ProviderInfo class, either
     * directly or nested inside of another.
     */
    public static final int GET_URI_PERMISSION_PATTERNS  = 0x00000800;
    /**
     * {@link PackageInfo} flag: return information about
     * permissions in the package in
     * {@link PackageInfo#permissions}.
     */
    public static final int GET_PERMISSIONS               = 0x00001000;

    /**
     * Permission check result: this is returned by {@link #checkPermission}
     * if the permission has been granted to the given package.
     */
    public static final int PERMISSION_GRANTED = 0;

    /**
     * Permission check result: this is returned by {@link #checkPermission}
     * if the permission has not been granted to the given package.
     */
    public static final int PERMISSION_DENIED = -1;

    /**
     * Signature check result: this is returned by {@link #checkSignatures}
     * if the two packages have a matching signature.
     */
    public static final int SIGNATURE_MATCH = 0;

    /**
     * Signature check result: this is returned by {@link #checkSignatures}
     * if neither of the two packages is signed.
     */
    public static final int SIGNATURE_NEITHER_SIGNED = 1;

    /**
     * Signature check result: this is returned by {@link #checkSignatures}
     * if the first package is not signed, but the second is.
     */
    public static final int SIGNATURE_FIRST_NOT_SIGNED = -1;

    /**
     * Signature check result: this is returned by {@link #checkSignatures}
     * if the second package is not signed, but the first is.
     */
    public static final int SIGNATURE_SECOND_NOT_SIGNED = -2;

    /**
     * Signature check result: this is returned by {@link #checkSignatures}
     * if both packages are signed but there is no matching signature.
     */
    public static final int SIGNATURE_NO_MATCH = -3;

    /**
     * Signature check result: this is returned by {@link #checkSignatures}
     * if either of the given package names are not valid.
     */
    public static final int SIGNATURE_UNKNOWN_PACKAGE = -4;

    /**
     * Resolution and querying flag: if set, only filters that support the
     * {@link android.content.Intent#CATEGORY_DEFAULT} will be considered for
     * matching.  This is a synonym for including the CATEGORY_DEFAULT in your
     * supplied Intent.
     */
    public static final int MATCH_DEFAULT_ONLY   = 0x00010000;

    public static final int COMPONENT_ENABLED_STATE_DEFAULT = 0;
    public static final int COMPONENT_ENABLED_STATE_ENABLED = 1;
    public static final int COMPONENT_ENABLED_STATE_DISABLED = 2;

    /**
     * Flag parameter for {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)} to
     * indicate that this package should be installed as forward locked, i.e. only the app itself
     * should have access to it's code and non-resource assets.
     */
    public static final int FORWARD_LOCK_PACKAGE = 0x00000001;

    /**
     * Flag parameter for {@link #installPackage} to indicate that you want to replace an already
     * installed package, if one exists
     */
    public static final int REPLACE_EXISTING_PACKAGE = 0x00000002;

    /**
     * Flag parameter for
     * {@link #setComponentEnabledSetting(android.content.ComponentName, int, int)} to indicate
     * that you don't want to kill the app containing the component.  Be careful when you set this
     * since changing component states can make the containing application's behavior unpredictable.
     */
    public static final int DONT_KILL_APP = 0x00000001;

    /**
     * Installation return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)} on success.
     */
    public static final int INSTALL_SUCCEEDED = 1;

    /**
     * Installation return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)} if the package is
     * already installed.
     */
    public static final int INSTALL_FAILED_ALREADY_EXISTS = -1;

    /**
     * Installation return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)} if the package archive
     * file is invalid.
     */
    public static final int INSTALL_FAILED_INVALID_APK = -2;

    /**
     * Installation return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)} if the URI passed in
     * is invalid.
     */
    public static final int INSTALL_FAILED_INVALID_URI = -3;

    /**
     * Installation return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)} if the package manager
     * service found that the device didn't have enough storage space to install the app
     */
    public static final int INSTALL_FAILED_INSUFFICIENT_STORAGE = -4;

    /**
     * Installation return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)} if a
     * package is already installed with the same name.
     */
    public static final int INSTALL_FAILED_DUPLICATE_PACKAGE = -5;

    /**
     * Installation return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)} if
     * the requested shared user does not exist.
     */
    public static final int INSTALL_FAILED_NO_SHARED_USER = -6;

    /**
     * Installation return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)} if
     * a previously installed package of the same name has a different signature
     * than the new package (and the old package's data was not removed).
     */
    public static final int INSTALL_FAILED_UPDATE_INCOMPATIBLE = -7;

    /**
     * Installation return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)} if
     * the new package is requested a shared user which is already installed on the
     * device and does not have matching signature.
     */
    public static final int INSTALL_FAILED_SHARED_USER_INCOMPATIBLE = -8;

    /**
     * Installation return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)} if
     * the new package uses a shared library that is not available.
     */
    public static final int INSTALL_FAILED_MISSING_SHARED_LIBRARY = -9;

    /**
     * Installation return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)} if
     * the new package uses a shared library that is not available.
     */
    public static final int INSTALL_FAILED_REPLACE_COULDNT_DELETE = -10;

    /**
     * Installation return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)} if
     * the new package failed while optimizing and validating its dex files,
     * either because there was not enough storage or the validation failed.
     */
    public static final int INSTALL_FAILED_DEXOPT = -11;

    /**
     * Installation return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)} if
     * the new package failed because the current SDK version is older than
     * that required by the package.
     */
    public static final int INSTALL_FAILED_OLDER_SDK = -12;

    /**
     * Installation parse return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)}
     * if the parser was given a path that is not a file, or does not end with the expected
     * '.apk' extension.
     */
    public static final int INSTALL_PARSE_FAILED_NOT_APK = -100;

    /**
     * Installation parse return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)}
     * if the parser was unable to retrieve the AndroidManifest.xml file.
     */
    public static final int INSTALL_PARSE_FAILED_BAD_MANIFEST = -101;

    /**
     * Installation parse return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)}
     * if the parser encountered an unexpected exception.
     */
    public static final int INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION = -102;

    /**
     * Installation parse return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)}
     * if the parser did not find any certificates in the .apk.
     */
    public static final int INSTALL_PARSE_FAILED_NO_CERTIFICATES = -103;

    /**
     * Installation parse return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)}
     * if the parser found inconsistent certificates on the files in the .apk.
     */
    public static final int INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES = -104;

    /**
     * Installation parse return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)}
     * if the parser encountered a CertificateEncodingException in one of the
     * files in the .apk.
     */
    public static final int INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING = -105;

    /**
     * Installation parse return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)}
     * if the parser encountered a bad or missing package name in the manifest.
     */
    public static final int INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME = -106;

    /**
     * Installation parse return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)}
     * if the parser encountered a bad shared user id name in the manifest.
     */
    public static final int INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID = -107;

    /**
     * Installation parse return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)}
     * if the parser encountered some structural problem in the manifest.
     */
    public static final int INSTALL_PARSE_FAILED_MANIFEST_MALFORMED = -108;

    /**
     * Installation parse return code: this is passed to the {@link IPackageInstallObserver} by
     * {@link #installPackage(android.net.Uri, IPackageInstallObserver, int)}
     * if the parser did not find any actionable tags (instrumentation or application)
     * in the manifest.
     */
    public static final int INSTALL_PARSE_FAILED_MANIFEST_EMPTY = -109;

    /**
     * Indicates the state of installation. Used by PackageManager to
     * figure out incomplete installations. Say a package is being installed
     * (the state is set to PKG_INSTALL_INCOMPLETE) and remains so till
     * the package installation is successful or unsuccesful lin which case
     * the PackageManager will no longer maintain state information associated
     * with the package. If some exception(like device freeze or battery being
     * pulled out) occurs during installation of a package, the PackageManager
     * needs this information to clean up the previously failed installation.
     */
    public static final int PKG_INSTALL_INCOMPLETE = 0;
    public static final int PKG_INSTALL_COMPLETE = 1;

    /**
     * Flag parameter for {@link #deletePackage} to indicate that you don't want to delete the
     * package's data directory.
     *
     * @hide
     */
    public static final int DONT_DELETE_DATA = 0x00000001;

    /**
     * Retrieve overall information about an application package that is
     * installed on the system.
     *
     * <p>Throws {@link NameNotFoundException} if a package with the given
     * name can not be found on the system.
     *
     * @param packageName The full name (i.e. com.google.apps.contacts) of the
     *                    desired package.
     * @param flags Optional flags to control what information is returned.  If
     *              0, none of the optional information is returned.
     *
     * @return Returns a PackageInfo containing information about the package.
     *
     * @see #GET_ACTIVITIES
     * @see #GET_RECEIVERS
     * @see #GET_SERVICES
     * @see #GET_INSTRUMENTATION
     * @see #GET_SIGNATURES
     */
    public abstract PackageInfo getPackageInfo(String packageName, int flags)
            throws NameNotFoundException;

    /**
     * Return an array of all of the secondary group-ids that have been
     * assigned to a package.
     *
     * <p>Throws {@link NameNotFoundException} if a package with the given
     * name can not be found on the system.
     *
     * @param packageName The full name (i.e. com.google.apps.contacts) of the
     *                    desired package.
     *
     * @return Returns an int array of the assigned gids, or null if there
     * are none.
     */
    public abstract int[] getPackageGids(String packageName)
            throws NameNotFoundException;

    /**
     * Retrieve all of the information we know about a particular permission.
     *
     * <p>Throws {@link NameNotFoundException} if a permission with the given
     * name can not be found on the system.
     *
     * @param name The fully qualified name (i.e. com.google.permission.LOGIN)
     *             of the permission you are interested in.
     * @param flags Additional option flags.  Use {@link #GET_META_DATA} to
     * retrieve any meta-data associated with the permission.
     *
     * @return Returns a {@link PermissionInfo} containing information about the
     *         permission.
     */
    public abstract PermissionInfo getPermissionInfo(String name, int flags)
            throws NameNotFoundException;

    /**
     * Query for all of the permissions associated with a particular group.
     *
     * <p>Throws {@link NameNotFoundException} if the given group does not
     * exist.
     *
     * @param group The fully qualified name (i.e. com.google.permission.LOGIN)
     *             of the permission group you are interested in.  Use null to
     *             find all of the permissions not associated with a group.
     * @param flags Additional option flags.  Use {@link #GET_META_DATA} to
     * retrieve any meta-data associated with the permissions.
     *
     * @return Returns a list of {@link PermissionInfo} containing information
     * about all of the permissions in the given group.
     */
    public abstract List<PermissionInfo> queryPermissionsByGroup(String group,
            int flags) throws NameNotFoundException;

    /**
     * Retrieve all of the information we know about a particular group of
     * permissions.
     *
     * <p>Throws {@link NameNotFoundException} if a permission group with the given
     * name can not be found on the system.
     *
     * @param name The fully qualified name (i.e. com.google.permission_group.APPS)
     *             of the permission you are interested in.
     * @param flags Additional option flags.  Use {@link #GET_META_DATA} to
     * retrieve any meta-data associated with the permission group.
     *
     * @return Returns a {@link PermissionGroupInfo} containing information
     * about the permission.
     */
    public abstract PermissionGroupInfo getPermissionGroupInfo(String name,
            int flags) throws NameNotFoundException;

    /**
     * Retrieve all of the known permission groups in the system.
     *
     * @param flags Additional option flags.  Use {@link #GET_META_DATA} to
     * retrieve any meta-data associated with the permission group.
     *
     * @return Returns a list of {@link PermissionGroupInfo} containing
     * information about all of the known permission groups.
     */
    public abstract List<PermissionGroupInfo> getAllPermissionGroups(int flags);

    /**
     * Retrieve all of the information we know about a particular
     * package/application.
     *
     * <p>Throws {@link NameNotFoundException} if an application with the given
     * package name can not be found on the system.
     *
     * @param packageName The full name (i.e. com.google.apps.contacts) of an
     *                    application.
     * @param flags Additional option flags.  Currently should always be 0.
     *
     * @return {@link ApplicationInfo} containing information about the
     *         application.
     */
    public abstract ApplicationInfo getApplicationInfo(String packageName,
            int flags) throws NameNotFoundException;

    /**
     * Retrieve all of the information we know about a particular activity
     * class.
     *
     * <p>Throws {@link NameNotFoundException} if an activity with the given
     * class name can not be found on the system.
     *
     * @param className The full name (i.e.
     *                  com.google.apps.contacts.ContactsList) of an Activity
     *                  class.
     * @param flags Additional option flags.  Usually 0.
     *
     * @return {@link ActivityInfo} containing information about the activity.
     *
     * @see #GET_INTENT_FILTERS
     */
    public abstract ActivityInfo getActivityInfo(ComponentName className,
            int flags) throws NameNotFoundException;

    /**
     * Retrieve all of the information we know about a particular receiver
     * class.
     *
     * <p>Throws {@link NameNotFoundException} if a receiver with the given
     * class name can not be found on the system.
     *
     * @param className The full name (i.e.
     *                  com.google.apps.contacts.CalendarAlarm) of a Receiver
     *                  class.
     * @param flags Additional option flags.  Usually 0.
     *
     * @return {@link ActivityInfo} containing information about the receiver.
     *
     * @see #GET_INTENT_FILTERS
     */
    public abstract ActivityInfo getReceiverInfo(ComponentName className,
            int flags) throws NameNotFoundException;

    /**
     * Retrieve all of the information we know about a particular service
     * class.
     *
     * <p>Throws {@link NameNotFoundException} if a service with the given
     * class name can not be found on the system.
     *
     * @param className The full name (i.e.
     *                  com.google.apps.media.BackgroundPlayback) of a Service
     *                  class.
     * @param flags Additional option flags.  Currently should always be 0.
     *
     * @return ServiceInfo containing information about the service.
     */
    public abstract ServiceInfo getServiceInfo(ComponentName className,
            int flags) throws NameNotFoundException;

    /**
     * Return a List of all packages that are installed
     * on the device.
     *
     * @param flags Optional flags to control what information is returned.  If
     *              0, none of the optional information is returned.
     *
     * @return A List of PackageInfo objects, one for each package that is
     *         installed on the device.  In the unlikely case of there being no
     *         installed packages, an empty list is returned.
     *
     * @see #GET_ACTIVITIES
     * @see #GET_RECEIVERS
     * @see #GET_SERVICES
     * @see #GET_INSTRUMENTATION
     * @see #GET_SIGNATURES
     */
    public abstract List<PackageInfo> getInstalledPackages(int flags);

    /**
     * Check whether a particular package has been granted a particular
     * permission.
     *
     * @param permName The name of the permission you are checking for,
     * @param pkgName The name of the package you are checking against.
     *
     * @return If the package has the permission, PERMISSION_GRANTED is
     * returned.  If it does not have the permission, PERMISSION_DENIED
     * is returned.
     *
     * @see #PERMISSION_GRANTED
     * @see #PERMISSION_DENIED
     */
    public abstract int checkPermission(String permName, String pkgName);

    /**
     * Add a new dynamic permission to the system.  For this to work, your
     * package must have defined a permission tree through the
     * {@link android.R.styleable#AndroidManifestPermissionTree
     * &lt;permission-tree&gt;} tag in its manifest.  A package can only add
     * permissions to trees that were defined by either its own package or
     * another with the same user id; a permission is in a tree if it
     * matches the name of the permission tree + ".": for example,
     * "com.foo.bar" is a member of the permission tree "com.foo".
     *
     * <p>It is good to make your permission tree name descriptive, because you
     * are taking possession of that entire set of permission names.  Thus, it
     * must be under a domain you control, with a suffix that will not match
     * any normal permissions that may be declared in any applications that
     * are part of that domain.
     *
     * <p>New permissions must be added before
     * any .apks are installed that use those permissions.  Permissions you
     * add through this method are remembered across reboots of the device.
     * If the given permission already exists, the info you supply here
     * will be used to update it.
     *
     * @param info Description of the permission to be added.
     *
     * @return Returns true if a new permission was created, false if an
     * existing one was updated.
     *
     * @throws SecurityException if you are not allowed to add the
     * given permission name.
     *
     * @see #removePermission(String)
     */
    public abstract boolean addPermission(PermissionInfo info);

    /**
     * Removes a permission that was previously added with
     * {@link #addPermission(PermissionInfo)}.  The same ownership rules apply
     * -- you are only allowed to remove permissions that you are allowed
     * to add.
     *
     * @param name The name of the permission to remove.
     *
     * @throws SecurityException if you are not allowed to remove the
     * given permission name.
     *
     * @see #addPermission(PermissionInfo)
     */
    public abstract void removePermission(String name);

    /**
     * Compare the signatures of two packages to determine if the same
     * signature appears in both of them.  If they do contain the same
     * signature, then they are allowed special privileges when working
     * with each other: they can share the same user-id, run instrumentation
     * against each other, etc.
     *
     * @param pkg1 First package name whose signature will be compared.
     * @param pkg2 Second package name whose signature will be compared.
     * @return Returns an integer indicating whether there is a matching
     * signature: the value is >= 0 if there is a match (or neither package
     * is signed), or < 0 if there is not a match.  The match result can be
     * further distinguished with the success (>= 0) constants
     * {@link #SIGNATURE_MATCH}, {@link #SIGNATURE_NEITHER_SIGNED}; or
     * failure (< 0) constants {@link #SIGNATURE_FIRST_NOT_SIGNED},
     * {@link #SIGNATURE_SECOND_NOT_SIGNED}, {@link #SIGNATURE_NO_MATCH},
     * or {@link #SIGNATURE_UNKNOWN_PACKAGE}.
     *
     * @see #SIGNATURE_MATCH
     * @see #SIGNATURE_NEITHER_SIGNED
     * @see #SIGNATURE_FIRST_NOT_SIGNED
     * @see #SIGNATURE_SECOND_NOT_SIGNED
     * @see #SIGNATURE_NO_MATCH
     * @see #SIGNATURE_UNKNOWN_PACKAGE
     */
    public abstract int checkSignatures(String pkg1, String pkg2);

    /**
     * Retrieve the names of all packages that are associated with a particular
     * user id.  In most cases, this will be a single package name, the package
     * that has been assigned that user id.  Where there are multiple packages
     * sharing the same user id through the "sharedUserId" mechanism, all
     * packages with that id will be returned.
     *
     * @param uid The user id for which you would like to retrieve the
     * associated packages.
     *
     * @return Returns an array of one or more packages assigned to the user
     * id, or null if there are no known packages with the given id.
     */
    public abstract String[] getPackagesForUid(int uid);

    /**
     * Retrieve the official name associated with a user id.  This name is
     * guaranteed to never change, though it is possibly for the underlying
     * user id to be changed.  That is, if you are storing information about
     * user ids in persistent storage, you should use the string returned
     * by this function instead of the raw user-id.
     *
     * @param uid The user id for which you would like to retrieve a name.
     * @return Returns a unique name for the given user id, or null if the
     * user id is not currently assigned.
     */
    public abstract String getNameForUid(int uid);

    /**
     * Return a List of all application packages that are installed on the
     * device.
     *
     * @param flags Additional option flags.  Currently should always be 0.
     *
     * @return A List of ApplicationInfo objects, one for each application that
     *         is installed on the device.  In the unlikely case of there being
     *         no installed applications, an empty list is returned.
     */
    public abstract List<ApplicationInfo> getInstalledApplications(int flags);

    /**
     * Determine the best action to perform for a given Intent.  This is how
     * {@link Intent#resolveActivity} finds an activity if a class has not
     * been explicitly specified.
     *
     * @param intent An intent containing all of the desired specification
     *               (action, data, type, category, and/or component).
     * @param flags Additional option flags.  The most important is
     *                    MATCH_DEFAULT_ONLY, to limit the resolution to only
     *                    those activities that support the CATEGORY_DEFAULT.
     *
     * @return Returns a ResolveInfo containing the final activity intent that
     *         was determined to be the best action.  Returns null if no
     *         matching activity was found.
     *
     * @see #MATCH_DEFAULT_ONLY
     * @see #GET_INTENT_FILTERS
     * @see #GET_RESOLVED_FILTER
     */
    public abstract ResolveInfo resolveActivity(Intent intent, int flags);

    /**
     * Retrieve all activities that can be performed for the given intent.
     *
     * @param intent The desired intent as per resolveActivity().
     * @param flags Additional option flags.  The most important is
     *                    MATCH_DEFAULT_ONLY, to limit the resolution to only
     *                    those activities that support the CATEGORY_DEFAULT.
     *
     * @return A List<ResolveInfo> containing one entry for each matching
     *         Activity. These are ordered from best to worst match -- that
     *         is, the first item in the list is what is returned by
     *         resolveActivity().  If there are no matching activities, an empty
     *         list is returned.
     *
     * @see #MATCH_DEFAULT_ONLY
     * @see #GET_INTENT_FILTERS
     * @see #GET_RESOLVED_FILTER
     */
    public abstract List<ResolveInfo> queryIntentActivities(Intent intent,
            int flags);

    /**
     * Retrieve a set of activities that should be presented to the user as
     * similar options.  This is like {@link #queryIntentActivities}, except it
     * also allows you to supply a list of more explicit Intents that you would
     * like to resolve to particular options, and takes care of returning the
     * final ResolveInfo list in a reasonable order, with no duplicates, based
     * on those inputs.
     *
     * @param caller The class name of the activity that is making the
     *               request.  This activity will never appear in the output
     *               list.  Can be null.
     * @param specifics An array of Intents that should be resolved to the
     *                  first specific results.  Can be null.
     * @param intent The desired intent as per resolveActivity().
     * @param flags Additional option flags.  The most important is
     *                    MATCH_DEFAULT_ONLY, to limit the resolution to only
     *                    those activities that support the CATEGORY_DEFAULT.
     *
     * @return A List<ResolveInfo> containing one entry for each matching
     *         Activity. These are ordered first by all of the intents resolved
     *         in <var>specifics</var> and then any additional activities that
     *         can handle <var>intent</var> but did not get included by one of
     *         the <var>specifics</var> intents.  If there are no matching
     *         activities, an empty list is returned.
     *
     * @see #MATCH_DEFAULT_ONLY
     * @see #GET_INTENT_FILTERS
     * @see #GET_RESOLVED_FILTER
     */
    public abstract List<ResolveInfo> queryIntentActivityOptions(
            ComponentName caller, Intent[] specifics, Intent intent, int flags);

    /**
     * Retrieve all receivers that can handle a broadcast of the given intent.
     *
     * @param intent The desired intent as per resolveActivity().
     * @param flags Additional option flags.  The most important is
     *                    MATCH_DEFAULT_ONLY, to limit the resolution to only
     *                    those activities that support the CATEGORY_DEFAULT.
     *
     * @return A List<ResolveInfo> containing one entry for each matching
     *         Receiver. These are ordered from first to last in priority.  If
     *         there are no matching receivers, an empty list is returned.
     *
     * @see #MATCH_DEFAULT_ONLY
     * @see #GET_INTENT_FILTERS
     * @see #GET_RESOLVED_FILTER
     */
    public abstract List<ResolveInfo> queryBroadcastReceivers(Intent intent,
            int flags);

    /**
     * Determine the best service to handle for a given Intent.
     *
     * @param intent An intent containing all of the desired specification
     *               (action, data, type, category, and/or component).
     * @param flags Additional option flags.
     *
     * @return Returns a ResolveInfo containing the final service intent that
     *         was determined to be the best action.  Returns null if no
     *         matching service was found.
     *
     * @see #GET_INTENT_FILTERS
     * @see #GET_RESOLVED_FILTER
     */
    public abstract ResolveInfo resolveService(Intent intent, int flags);

    /**
     * Retrieve all services that can match the given intent.
     *
     * @param intent The desired intent as per resolveService().
     * @param flags Additional option flags.
     *
     * @return A List<ResolveInfo> containing one entry for each matching
     *         ServiceInfo. These are ordered from best to worst match -- that
     *         is, the first item in the list is what is returned by
     *         resolveService().  If there are no matching services, an empty
     *         list is returned.
     *
     * @see #GET_INTENT_FILTERS
     * @see #GET_RESOLVED_FILTER
     */
    public abstract List<ResolveInfo> queryIntentServices(Intent intent,
            int flags);

    /**
     * Find a single content provider by its base path name.
     *
     * @param name The name of the provider to find.
     * @param flags Additional option flags.  Currently should always be 0.
     *
     * @return ContentProviderInfo Information about the provider, if found,
     *         else null.
     */
    public abstract ProviderInfo resolveContentProvider(String name,
            int flags);

    /**
     * Retrieve content provider information.
     *
     * <p><em>Note: unlike most other methods, an empty result set is indicated
     * by a null return instead of an empty list.</em>
     *
     * @param processName If non-null, limits the returned providers to only
     *                    those that are hosted by the given process.  If null,
     *                    all content providers are returned.
     * @param uid If <var>processName</var> is non-null, this is the required
     *        uid owning the requested content providers.
     * @param flags Additional option flags.  Currently should always be 0.
     *
     * @return A List<ContentProviderInfo> containing one entry for each
     *         content provider either patching <var>processName</var> or, if
     *         <var>processName</var> is null, all known content providers.
     *         <em>If there are no matching providers, null is returned.</em>
     */
    public abstract List<ProviderInfo> queryContentProviders(
            String processName, int uid, int flags);

    /**
     * Retrieve all of the information we know about a particular
     * instrumentation class.
     *
     * <p>Throws {@link NameNotFoundException} if instrumentation with the
     * given class name can not be found on the system.
     *
     * @param className The full name (i.e.
     *                  com.google.apps.contacts.InstrumentList) of an
     *                  Instrumentation class.
     * @param flags Additional option flags.  Currently should always be 0.
     *
     * @return InstrumentationInfo containing information about the
     *         instrumentation.
     */
    public abstract InstrumentationInfo getInstrumentationInfo(
            ComponentName className, int flags) throws NameNotFoundException;

    /**
     * Retrieve information about available instrumentation code.  May be used
     * to retrieve either all instrumentation code, or only the code targeting
     * a particular package.
     *
     * @param targetPackage If null, all instrumentation is returned; only the
     *                      instrumentation targeting this package name is
     *                      returned.
     * @param flags Additional option flags.  Currently should always be 0.
     *
     * @return A List<InstrumentationInfo> containing one entry for each
     *         matching available Instrumentation.  Returns an empty list if
     *         there is no instrumentation available for the given package.
     */
    public abstract List<InstrumentationInfo> queryInstrumentation(
            String targetPackage, int flags);

    /**
     * Retrieve an image from a package.  This is a low-level API used by
     * the various package manager info structures (such as
     * {@link ComponentInfo} to implement retrieval of their associated
     * icon.
     *
     * @param packageName The name of the package that this icon is coming from.
     * Can not be null.
     * @param resid The resource identifier of the desired image.  Can not be 0.
     * @param appInfo Overall information about <var>packageName</var>.  This
     * may be null, in which case the application information will be retrieved
     * for you if needed; if you already have this information around, it can
     * be much more efficient to supply it here.
     *
     * @return Returns a Drawable holding the requested image.  Returns null if
     * an image could not be found for any reason.
     */
    public abstract Drawable getDrawable(String packageName, int resid,
            ApplicationInfo appInfo);

    /**
     * Retrieve the icon associated with an activity.  Given the full name of
     * an activity, retrieves the information about it and calls
     * {@link ComponentInfo#loadIcon ComponentInfo.loadIcon()} to return its icon.
     * If the activity can not be found, NameNotFoundException is thrown.
     *
     * @param activityName Name of the activity whose icon is to be retrieved.
     *
     * @return Returns the image of the icon, or the default activity icon if
     * it could not be found.  Does not return null.
     * @throws NameNotFoundException Thrown if the resources for the given
     * activity could not be loaded.
     *
     * @see #getActivityIcon(Intent)
     */
    public abstract Drawable getActivityIcon(ComponentName activityName)
            throws NameNotFoundException;

    /**
     * Retrieve the icon associated with an Intent.  If intent.getClassName() is
     * set, this simply returns the result of
     * getActivityIcon(intent.getClassName()).  Otherwise it resolves the intent's
     * component and returns the icon associated with the resolved component.
     * If intent.getClassName() can not be found or the Intent can not be resolved
     * to a component, NameNotFoundException is thrown.
     *
     * @param intent The intent for which you would like to retrieve an icon.
     *
     * @return Returns the image of the icon, or the default activity icon if
     * it could not be found.  Does not return null.
     * @throws NameNotFoundException Thrown if the resources for application
     * matching the given intent could not be loaded.
     *
     * @see #getActivityIcon(ComponentName)
     */
    public abstract Drawable getActivityIcon(Intent intent)
            throws NameNotFoundException;

    /**
     * Return the generic icon for an activity that is used when no specific
     * icon is defined.
     *
     * @return Drawable Image of the icon.
     */
    public abstract Drawable getDefaultActivityIcon();

    /**
     * Retrieve the icon associated with an application.  If it has not defined
     * an icon, the default app icon is returned.  Does not return null.
     *
     * @param info Information about application being queried.
     *
     * @return Returns the image of the icon, or the default application icon
     * if it could not be found.
     *
     * @see #getApplicationIcon(String)
     */
    public abstract Drawable getApplicationIcon(ApplicationInfo info);

    /**
     * Retrieve the icon associated with an application.  Given the name of the
     * application's package, retrieves the information about it and calls
     * getApplicationIcon() to return its icon. If the application can not be
     * found, NameNotFoundException is thrown.
     *
     * @param packageName Name of the package whose application icon is to be
     *                    retrieved.
     *
     * @return Returns the image of the icon, or the default application icon
     * if it could not be found.  Does not return null.
     * @throws NameNotFoundException Thrown if the resources for the given
     * application could not be loaded.
     *
     * @see #getApplicationIcon(ApplicationInfo)
     */
    public abstract Drawable getApplicationIcon(String packageName)
            throws NameNotFoundException;

    /**
     * Retrieve text from a package.  This is a low-level API used by
     * the various package manager info structures (such as
     * {@link ComponentInfo} to implement retrieval of their associated
     * labels and other text.
     *
     * @param packageName The name of the package that this text is coming from.
     * Can not be null.
     * @param resid The resource identifier of the desired text.  Can not be 0.
     * @param appInfo Overall information about <var>packageName</var>.  This
     * may be null, in which case the application information will be retrieved
     * for you if needed; if you already have this information around, it can
     * be much more efficient to supply it here.
     *
     * @return Returns a CharSequence holding the requested text.  Returns null
     * if the text could not be found for any reason.
     */
    public abstract CharSequence getText(String packageName, int resid,
            ApplicationInfo appInfo);

    /**
     * Retrieve an XML file from a package.  This is a low-level API used to
     * retrieve XML meta data.
     *
     * @param packageName The name of the package that this xml is coming from.
     * Can not be null.
     * @param resid The resource identifier of the desired xml.  Can not be 0.
     * @param appInfo Overall information about <var>packageName</var>.  This
     * may be null, in which case the application information will be retrieved
     * for you if needed; if you already have this information around, it can
     * be much more efficient to supply it here.
     *
     * @return Returns an XmlPullParser allowing you to parse out the XML
     * data.  Returns null if the xml resource could not be found for any
     * reason.
     */
    public abstract XmlResourceParser getXml(String packageName, int resid,
            ApplicationInfo appInfo);

    /**
     * Return the label to use for this application.
     *
     * @return Returns the label associated with this application, or null if
     * it could not be found for any reason.
     * @param info The application to get the label of
     */
    public abstract CharSequence getApplicationLabel(ApplicationInfo info);

    /**
     * Retrieve the resources associated with an activity.  Given the full
     * name of an activity, retrieves the information about it and calls
     * getResources() to return its application's resources.  If the activity
     * can not be found, NameNotFoundException is thrown.
     *
     * @param activityName Name of the activity whose resources are to be
     *                     retrieved.
     *
     * @return Returns the application's Resources.
     * @throws NameNotFoundException Thrown if the resources for the given
     * application could not be loaded.
     *
     * @see #getResourcesForApplication(ApplicationInfo)
     */
    public abstract Resources getResourcesForActivity(ComponentName activityName)
            throws NameNotFoundException;

    /**
     * Retrieve the resources for an application.  Throws NameNotFoundException
     * if the package is no longer installed.
     *
     * @param app Information about the desired application.
     *
     * @return Returns the application's Resources.
     * @throws NameNotFoundException Thrown if the resources for the given
     * application could not be loaded (most likely because it was uninstalled).
     */
    public abstract Resources getResourcesForApplication(ApplicationInfo app)
            throws NameNotFoundException;

    /**
     * Retrieve the resources associated with an application.  Given the full
     * package name of an application, retrieves the information about it and
     * calls getResources() to return its application's resources.  If the
     * appPackageName can not be found, NameNotFoundException is thrown.
     *
     * @param appPackageName Package name of the application whose resources
     *                       are to be retrieved.
     *
     * @return Returns the application's Resources.
     * @throws NameNotFoundException Thrown if the resources for the given
     * application could not be loaded.
     *
     * @see #getResourcesForApplication(ApplicationInfo)
     */
    public abstract Resources getResourcesForApplication(String appPackageName)
            throws NameNotFoundException;

    /**
     * Retrieve overall information about an application package defined
     * in a package archive file
     *
     * @param archiveFilePath The path to the archive file
     * @param flags Optional flags to control what information is returned.  If
     *              0, none of the optional information is returned.
     *
     * @return Returns the information about the package. Returns
     * null if the package could not be successfully parsed.
     *
     * @see #GET_ACTIVITIES
     * @see #GET_RECEIVERS
     * @see #GET_SERVICES
     * @see #GET_INSTRUMENTATION
     * @see #GET_SIGNATURES
     */
    public PackageInfo getPackageArchiveInfo(String archiveFilePath, int flags) {
        PackageParser packageParser = new PackageParser(archiveFilePath);
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setToDefaults();
        final File sourceFile = new File(archiveFilePath);
        PackageParser.Package pkg = packageParser.parsePackage(
                sourceFile, archiveFilePath, metrics, 0);
        if (pkg == null) {
            return null;
        }
        return PackageParser.generatePackageInfo(pkg, null, flags);
    }

    /**
     * Install a package. Since this may take a little while, the result will
     * be posted back to the given observer.  An installation will fail if the calling context
     * lacks the {@link android.Manifest.permission#INSTALL_PACKAGES} permission, if the
     * package named in the package file's manifest is already installed, or if there's no space
     * available on the device.
     *
     * @param packageURI The location of the package file to install.  This can be a 'file:' or a
     * 'content:' URI.
     * @param observer An observer callback to get notified when the package installation is
     * complete. {@link IPackageInstallObserver#packageInstalled(String, int)} will be
     * called when that happens.  observer may be null to indicate that no callback is desired.
     * @param flags - possible values: {@link #FORWARD_LOCK_PACKAGE},
     * {@link #REPLACE_EXISTING_PACKAGE}
     *
     * @see #installPackage(android.net.Uri)
     */
    public abstract void installPackage(
            Uri packageURI, IPackageInstallObserver observer, int flags);

    /**
     * Attempts to delete a package.  Since this may take a little while, the result will
     * be posted back to the given observer.  A deletion will fail if the calling context
     * lacks the {@link android.Manifest.permission#DELETE_PACKAGES} permission, if the
     * named package cannot be found, or if the named package is a "system package".
     * (TODO: include pointer to documentation on "system packages")
     *
     * @param packageName The name of the package to delete
     * @param observer An observer callback to get notified when the package deletion is
     * complete. {@link android.content.pm.IPackageDeleteObserver#packageDeleted(boolean)} will be
     * called when that happens.  observer may be null to indicate that no callback is desired.
     * @param flags - possible values: {@link #DONT_DELETE_DATA}
     *
     * @hide
     */
    public abstract void deletePackage(
            String packageName, IPackageDeleteObserver observer, int flags);
    /**
     * Attempts to clear the user data directory of an application.
     * Since this may take a little while, the result will
     * be posted back to the given observer.  A deletion will fail if the
     * named package cannot be found, or if the named package is a "system package".
     *
     * @param packageName The name of the package
     * @param observer An observer callback to get notified when the operation is finished
     * {@link android.content.pm.IPackageDataObserver#onRemoveCompleted(String, boolean)}
     * will be called when that happens.  observer may be null to indicate that
     * no callback is desired.
     *
     * @hide
     */
    public abstract void clearApplicationUserData(String packageName,
            IPackageDataObserver observer);
    /**
     * Attempts to delete the cache files associated with an application.
     * Since this may take a little while, the result will
     * be posted back to the given observer.  A deletion will fail if the calling context
     * lacks the {@link android.Manifest.permission#DELETE_CACHE_FILES} permission, if the
     * named package cannot be found, or if the named package is a "system package".
     *
     * @param packageName The name of the package to delete
     * @param observer An observer callback to get notified when the cache file deletion
     * is complete.
     * {@link android.content.pm.IPackageDataObserver#onRemoveCompleted(String, boolean)}
     * will be called when that happens.  observer may be null to indicate that
     * no callback is desired.
     *
     * @hide
     */
    public abstract void deleteApplicationCacheFiles(String packageName,
            IPackageDataObserver observer);

    /**
     * Free storage by deleting LRU sorted list of cache files across all applications.
     * If the currently available free storage on the device is greater than or equal to the
     * requested free storage, no cache files are cleared. If the currently available storage on the
     * device is less than the requested free storage, some or all of the cache files across
     * all applications are deleted(based on last accessed time) to increase the free storage
     * space on the device to the requested value. There is no gurantee that clearing all
     * the cache files from all applications will clear up enough storage to achieve the desired
     * value.
     * @param freeStorageSize The number of bytes of storage to be
     * freed by the system. Say if freeStorageSize is XX,
     * and the current free storage is YY,
     * if XX is less than YY, just return. if not free XX-YY number of
     * bytes if possible.
     * @param observer callback used to notify when the operation is completed
     * {@link android.content.pm.IPackageDataObserver#onRemoveCompleted(String, boolean)}
     * will be called when that happens.  observer may be null to indicate that
     * no callback is desired.
     *
     * @hide
     */
    public abstract void freeApplicationCache(long freeStorageSize,
            IPackageDataObserver observer);

    /**
     * Retrieve the size information for a package.
     * Since this may take a little while, the result will
     * be posted back to the given observer.  The calling context
     * should have the {@link android.Manifest.permission#GET_PACKAGE_SIZE} permission.
     *
     * @param packageName The name of the package whose size information is to be retrieved
     * @param observer An observer callback to get notified when the operation
     * is complete.
     * {@link android.content.pm.IPackageStatsObserver#onGetStatsCompleted(PackageStats, boolean)}
     * The observer's callback is invoked with a PackageStats object(containing the
     * code, data and cache sizes of the package) and a boolean value representing
     * the status of the operation. observer may be null to indicate that
     * no callback is desired.
     *
     * @hide
     */
    public abstract void getPackageSizeInfo(String packageName,
            IPackageStatsObserver observer);

    /**
     * Install a package.
     *
     * @param packageURI The location of the package file to install
     *
     * @see #installPackage(android.net.Uri, IPackageInstallObserver, int)
     */
    public void installPackage(Uri packageURI) {
        installPackage(packageURI, null, 0);
    }

    /**
     * Add a new package to the list of preferred packages.  This new package
     * will be added to the front of the list (removed from its current location
     * if already listed), meaning it will now be preferred over all other
     * packages when resolving conflicts.
     *
     * @param packageName The package name of the new package to make preferred.
     */
    public abstract void addPackageToPreferred(String packageName);

    /**
     * Remove a package from the list of preferred packages.  If it was on
     * the list, it will no longer be preferred over other packages.
     *
     * @param packageName The package name to remove.
     */
    public abstract void removePackageFromPreferred(String packageName);

    /**
     * Retrieve the list of all currently configured preferred packages.  The
     * first package on the list is the most preferred, the last is the
     * least preferred.
     *
     * @param flags Optional flags to control what information is returned.  If
     *              0, none of the optional information is returned.
     *
     * @return Returns a list of PackageInfo objects describing each
     * preferred application, in order of preference.
     *
     * @see #GET_ACTIVITIES
     * @see #GET_RECEIVERS
     * @see #GET_SERVICES
     * @see #GET_INSTRUMENTATION
     * @see #GET_SIGNATURES
     */
    public abstract List<PackageInfo> getPreferredPackages(int flags);

    /**
     * Add a new preferred activity mapping to the system.  This will be used
     * to automatically select the given activity component when
     * {@link Context#startActivity(Intent) Context.startActivity()} finds
     * multiple matching activities and also matches the given filter.
     *
     * @param filter The set of intents under which this activity will be
     * made preferred.
     * @param match The IntentFilter match category that this preference
     * applies to.
     * @param set The set of activities that the user was picking from when
     * this preference was made.
     * @param activity The component name of the activity that is to be
     * preferred.
     */
    public abstract void addPreferredActivity(IntentFilter filter, int match,
            ComponentName[] set, ComponentName activity);

    /**
     * Remove all preferred activity mappings, previously added with
     * {@link #addPreferredActivity}, from the
     * system whose activities are implemented in the given package name.
     *
     * @param packageName The name of the package whose preferred activity
     * mappings are to be removed.
     */
    public abstract void clearPackagePreferredActivities(String packageName);

    /**
     * Retrieve all preferred activities, previously added with
     * {@link #addPreferredActivity}, that are
     * currently registered with the system.
     *
     * @param outFilters A list in which to place the filters of all of the
     * preferred activities, or null for none.
     * @param outActivities A list in which to place the component names of
     * all of the preferred activities, or null for none.
     * @param packageName An option package in which you would like to limit
     * the list.  If null, all activities will be returned; if non-null, only
     * those activities in the given package are returned.
     *
     * @return Returns the total number of registered preferred activities
     * (the number of distinct IntentFilter records, not the number of unique
     * activity components) that were found.
     */
    public abstract int getPreferredActivities(List<IntentFilter> outFilters,
            List<ComponentName> outActivities, String packageName);

    /**
     * Set the enabled setting for a package component (activity, receiver, service, provider).
     * This setting will override any enabled state which may have been set by the component in its
     * manifest.
     *
     * @param componentName The component to enable
     * @param newState The new enabled state for the component.  The legal values for this state
     *                 are:
     *                   {@link #COMPONENT_ENABLED_STATE_ENABLED},
     *                   {@link #COMPONENT_ENABLED_STATE_DISABLED}
     *                   and
     *                   {@link #COMPONENT_ENABLED_STATE_DEFAULT}
     *                 The last one removes the setting, thereby restoring the component's state to
     *                 whatever was set in it's manifest (or enabled, by default).
     * @param flags Optional behavior flags: {@link #DONT_KILL_APP} or 0.
     */
    public abstract void setComponentEnabledSetting(ComponentName componentName,
            int newState, int flags);


    /**
     * Return the the enabled setting for a package component (activity,
     * receiver, service, provider).  This returns the last value set by
     * {@link #setComponentEnabledSetting(ComponentName, int, int)}; in most
     * cases this value will be {@link #COMPONENT_ENABLED_STATE_DEFAULT} since
     * the value originally specified in the manifest has not been modified.
     *
     * @param componentName The component to retrieve.
     * @return Returns the current enabled state for the component.  May
     * be one of {@link #COMPONENT_ENABLED_STATE_ENABLED},
     * {@link #COMPONENT_ENABLED_STATE_DISABLED}, or
     * {@link #COMPONENT_ENABLED_STATE_DEFAULT}.  The last one means the
     * component's enabled state is based on the original information in
     * the manifest as found in {@link ComponentInfo}.
     */
    public abstract int getComponentEnabledSetting(ComponentName componentName);

    /**
     * Set the enabled setting for an application
     * This setting will override any enabled state which may have been set by the application in
     * its manifest.  It also overrides the enabled state set in the manifest for any of the
     * application's components.  It does not override any enabled state set by
     * {@link #setComponentEnabledSetting} for any of the application's components.
     *
     * @param packageName The package name of the application to enable
     * @param newState The new enabled state for the component.  The legal values for this state
     *                 are:
     *                   {@link #COMPONENT_ENABLED_STATE_ENABLED},
     *                   {@link #COMPONENT_ENABLED_STATE_DISABLED}
     *                   and
     *                   {@link #COMPONENT_ENABLED_STATE_DEFAULT}
     *                 The last one removes the setting, thereby restoring the applications's state to
     *                 whatever was set in its manifest (or enabled, by default).
     * @param flags Optional behavior flags: {@link #DONT_KILL_APP} or 0.
     */
    public abstract void setApplicationEnabledSetting(String packageName,
            int newState, int flags);
    
    /**
     * Return the the enabled setting for an application.  This returns
     * the last value set by
     * {@link #setApplicationEnabledSetting(String, int, int)}; in most
     * cases this value will be {@link #COMPONENT_ENABLED_STATE_DEFAULT} since
     * the value originally specified in the manifest has not been modified.
     *
     * @param packageName The component to retrieve.
     * @return Returns the current enabled state for the component.  May
     * be one of {@link #COMPONENT_ENABLED_STATE_ENABLED},
     * {@link #COMPONENT_ENABLED_STATE_DISABLED}, or
     * {@link #COMPONENT_ENABLED_STATE_DEFAULT}.  The last one means the
     * application's enabled state is based on the original information in
     * the manifest as found in {@link ComponentInfo}.
     */
    public abstract int getApplicationEnabledSetting(String packageName);
}