package guardtest;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

/** Forge's mod dependency checks use these Maven version range types. */
public final class CalendarDependencyTest {
    public static void main(String[] args)throws Exception {
        var oldRange=VersionRange.createFromVersionSpec("[1.0.1]");
        if(oldRange.containsVersion(new DefaultArtifactVersion("1.0.2")))throw new AssertionError("Negative control must reproduce the uploaded startup failure");
        var range=VersionRange.createFromVersionSpec(args[0]);
        for(String supported:new String[]{"1.0.1","1.0.2","1.0.3",args[1]})
            if(!range.containsVersion(new DefaultArtifactVersion(supported)))throw new AssertionError("Installed World Rules rejected: "+supported);
        for(String untested:new String[]{"1.0.0","1.0.4","1.1.0","2.0.0"})
            if(range.containsVersion(new DefaultArtifactVersion(untested)))throw new AssertionError("Untested World Rules accepted: "+untested);
        System.out.println("PASS native Maven/Forge dependency ranges: original startup failure reproduced; World Rules 1.0.1/1.0.2/1.0.3 accepted; untested releases rejected");
    }
}
