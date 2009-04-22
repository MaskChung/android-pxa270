package android.content.pm;

import android.content.res.XmlResourceParser;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcel;
import android.text.TextUtils;
import android.util.Printer;

import java.text.Collator;
import java.util.Comparator;

/**
 * Base class containing information common to all package items held by
 * the package manager.  This provides a very common basic set of attributes:
 * a label, icon, and meta-data.  This class is not intended
 * to be used by itself; it is simply here to share common definitions
 * between all items returned by the package manager.  As such, it does not
 * itself implement Parcelable, but does provide convenience methods to assist
 * in the implementation of Parcelable in subclasses.
 */
public class PackageItemInfo {
    /**
     * Public name of this item. From the "android:name" attribute.
     */
    public String name;
    
    /**
     * Name of the package that this item is in.
     */
    public String packageName;
    
    /**
     * A string resource identifier (in the package's resources) of this
     * component's label.  From the "label" attribute or, if not set, 0.
     */
    public int labelRes;
    
    /**
     * The string provided in the AndroidManifest file, if any.  You
     * probably don't want to use this.  You probably want
     * {@link PackageManager#getApplicationLabel}
     */
    public CharSequence nonLocalizedLabel;
    
    /**
     * A drawable resource identifier (in the package's resources) of this
     * component's icon.  From the "icon" attribute or, if not set, 0.
     */
    public int icon;
    
    /**
     * Additional meta-data associated with this component.  This field
     * will only be filled in if you set the
     * {@link PackageManager#GET_META_DATA} flag when requesting the info.
     */
    public Bundle metaData;
    
    public PackageItemInfo() {
    }

    public PackageItemInfo(PackageItemInfo orig) {
        name = orig.name;
        packageName = orig.packageName;
        labelRes = orig.labelRes;
        nonLocalizedLabel = orig.nonLocalizedLabel;
        icon = orig.icon;
        metaData = orig.metaData;
    }

    /**
     * Retrieve the current textual label associated with this item.  This
     * will call back on the given PackageManager to load the label from
     * the application.
     * 
     * @param pm A PackageManager from which the label can be loaded; usually
     * the PackageManager from which you originally retrieved this item.
     * 
     * @return Returns a CharSequence containing the item's label.  If the
     * item does not have a label, its name is returned.
     */
    public CharSequence loadLabel(PackageManager pm) {
        if (nonLocalizedLabel != null) {
            return nonLocalizedLabel;
        }
        if (labelRes != 0) {
            CharSequence label = pm.getText(packageName, labelRes, null);
            if (label != null) {
                return label;
            }
        }
        return name;
    }
    
    /**
     * Retrieve the current graphical icon associated with this item.  This
     * will call back on the given PackageManager to load the icon from
     * the application.
     * 
     * @param pm A PackageManager from which the icon can be loaded; usually
     * the PackageManager from which you originally retrieved this item.
     * 
     * @return Returns a Drawable containing the item's icon.  If the
     * item does not have an icon, the default activity icon is returned.
     */
    public Drawable loadIcon(PackageManager pm) {
        if (icon != 0) {
            Drawable dr = pm.getDrawable(packageName, icon, null);
            if (dr != null) {
                return dr;
            }
        }
        return pm.getDefaultActivityIcon();
    }
    
    /**
     * Load an XML resource attached to the meta-data of this item.  This will
     * retrieved the name meta-data entry, and if defined call back on the
     * given PackageManager to load its XML file from the application.
     * 
     * @param pm A PackageManager from which the XML can be loaded; usually
     * the PackageManager from which you originally retrieved this item.
     * @param name Name of the meta-date you would like to load.
     * 
     * @return Returns an XmlPullParser you can use to parse the XML file
     * assigned as the given meta-data.  If the meta-data name is not defined
     * or the XML resource could not be found, null is returned.
     */
    public XmlResourceParser loadXmlMetaData(PackageManager pm, String name) {
        if (metaData != null) {
            int resid = metaData.getInt(name);
            if (resid != 0) {
                return pm.getXml(packageName, resid, null);
            }
        }
        return null;
    }
    
    protected void dumpFront(Printer pw, String prefix) {
        pw.println(prefix + "name=" + name);
        pw.println(prefix + "packageName=" + packageName);
        pw.println(prefix + "labelRes=0x" + Integer.toHexString(labelRes)
                + " nonLocalizedLabel=" + nonLocalizedLabel
                + " icon=0x" + Integer.toHexString(icon));
    }
    
    protected void dumpBack(Printer pw, String prefix) {
        // no back here
    }
    
    public void writeToParcel(Parcel dest, int parcelableFlags) {
        dest.writeString(name);
        dest.writeString(packageName);
        dest.writeInt(labelRes);
        TextUtils.writeToParcel(nonLocalizedLabel, dest, parcelableFlags);
        dest.writeInt(icon);
        dest.writeBundle(metaData);
    }

    protected PackageItemInfo(Parcel source) {
        name = source.readString();
        packageName = source.readString();
        labelRes = source.readInt();
        nonLocalizedLabel
                = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
        icon = source.readInt();
        metaData = source.readBundle();
    }

    public static class DisplayNameComparator
            implements Comparator<PackageItemInfo> {
        public DisplayNameComparator(PackageManager pm) {
            mPM = pm;
        }

        public final int compare(PackageItemInfo aa, PackageItemInfo ab) {
            CharSequence  sa = aa.loadLabel(mPM);
            if (sa == null) sa = aa.name;
            CharSequence  sb = ab.loadLabel(mPM);
            if (sb == null) sb = ab.name;
            return sCollator.compare(sa, sb);
        }

        private final Collator   sCollator = Collator.getInstance();
        private PackageManager   mPM;
    }
}
