import net.minecraftforge.srgutils.IMappingFile;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Server-only overlay builds use the exact mappings and SpecialSource already installed with Mohist. */
public final class OfflineMappings {
    public static void main(String[] args) throws Exception {
        var named=IMappingFile.load(new File(args[0])).chain(IMappingFile.load(new File(args[1])));
        List<String> lines=new ArrayList<>();
        for(var cls:named.getClasses()) {
            String owner=cls.getOriginal();lines.add("CL: "+owner+" "+owner);
            for(var f:cls.getFields())lines.add("FD: "+owner+"/"+f.getOriginal()+" "+owner+"/"+f.getMapped());
            for(var m:cls.getMethods())lines.add("MD: "+owner+"/"+m.getOriginal()+" "+m.getDescriptor()+" "+owner+"/"+m.getMapped()+" "+m.getDescriptor());
        }
        Collections.sort(lines);Files.write(Path.of(args[2]),lines);
    }
}
