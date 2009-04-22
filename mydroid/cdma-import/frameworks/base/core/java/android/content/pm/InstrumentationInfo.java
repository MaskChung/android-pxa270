package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.text.Collator;
import java.util.Comparator;

/**
 * Information you can retrieve about a particular piece of test
 * instrumentation.  This corresponds to information collected
 * from the AndroidManifest.xml's &lt;instrumentation&gt; tag.
 */
public class InstrumentationInfo extends PackageItemInfo implements Parcelable {
    /**
     * The name of the application package being instrumented.  From the
     * "package" attribute.
     */
    public String targetPackage;
    
    /**
     * Full path to the location of this package.
     */
    public String sourceDir;
    
    /**
     * Full path to the location of the publicly available parts of this package (i.e. the resources
     * and manifest).  For non-forward-locked apps this will be the same as {@link #sourceDir).
     */
    public String publicSourceDir;
    /**
     * Full path to a directory assigned to the package for its persistent
     * data.
     */
    public String dataDir;
    
    /**
     * Specifies whether or not this instrumentation will handle profiling.
     */
    public boolean handleProfiling;
    
    /** Specifies whether or not to run this instrumentation as a functional test */
    public boolean functionalTest;

    public InstrumentationInfo() {
    }

    public InstrumentationInfo(InstrumentationInfo orig) {
        super(orig);
        targetPackage = orig.targetPackage;
        sourceDir = orig.sourceDir;
        publicSourceDir = orig.publicSourceDir;
        dataDir = orig.dataDir;
        handleProfiling = orig.handleProfiling;
        functionalTest = orig.functionalTest;
    }

    public String toString() {
        return "InstrumentationInfo{"
            + Integer.toHexString(System.identityHashCode(this))
            + " " + packageName + "}";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int parcelableFlags) {
        super.writeToParcel(dest, parcelableFlags);
        dest.writeString(targetPackage);
        dest.writeString(sourceDir);
        dest.writeString(publicSourceDir);
        dest.writeString(dataDir);
        dest.writeInt((handleProfiling == false) ? 0 : 1);
    }

    public static final Parcelable.Creator<InstrumentationInfo> CREATOR
            = new Parcelable.Creator<InstrumentationInfo>() {
        public InstrumentationInfo createFromParcel(Parcel source) {
            return new InstrumentationInfo(source);
        }
        public InstrumentationInfo[] newArray(int size) {
            return new InstrumentationInfo[size];
        }
    };

    private InstrumentationInfo(Parcel source) {
        super(source);
        targetPackage = source.readString();
        sourceDir = source.readString();
        publicSourceDir = source.readString();
        dataDir = source.readString();
        handleProfiling = source.readInt() != 0;
    }
}
