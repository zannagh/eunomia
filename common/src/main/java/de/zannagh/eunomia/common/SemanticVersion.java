package de.zannagh.eunomia.common;

import org.jspecify.annotations.Nullable;
import java.util.Comparator;

public class SemanticVersion implements Comparable<SemanticVersion>, Comparator<SemanticVersion> {

    public final int major;

    public final int minor;

    public final int patch;

    @Nullable
    public final String build;

    public SemanticVersion(int major, int minor, int patch, @Nullable String build) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.build = build;
    }

    @Override
    public String toString() {
        var returnValue = major + "." + minor + "." + patch;
        if (build != null && !build.isEmpty()) {
            returnValue += "-" + build;
        }
        return returnValue;
    }

    public boolean isSmallerThan(SemanticVersion other){
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(SemanticVersion otherVersion) {
        if (major > otherVersion.major) {
            return 1;
        }
        if (major < otherVersion.major) {
            return -1;
        }
        if (minor > otherVersion.minor) {
            return 1;
        }
        if (minor < otherVersion.minor) {
            return -1;
        }
        var patchResult = Integer.compare(patch, otherVersion.patch);
        if (patchResult == 0) {
            return compareBuild(otherVersion);
        }
        return patchResult;
    }

    private int compareBuild(SemanticVersion other){
        return 0; // TODO, compare SemVer builds which can contain string values.
    }

    @Override
    public int compare(SemanticVersion firstVersion, SemanticVersion secondVersion) {
        return firstVersion.compareTo(secondVersion);
    }
}
