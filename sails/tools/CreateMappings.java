import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import net.minecraftforge.srgutils.IMappingFile;
public class CreateMappings {
    public static void main(String[] args) throws Exception {
        IMappingFile namedToOfficial = IMappingFile.load(new File(args[0]));
        IMappingFile officialToSrg = IMappingFile.load(new File(args[1]));
        IMappingFile mapping = namedToOfficial.chain(officialToSrg);
        var lines = new ArrayList<String>();
        lines.add("tiny\t2\t0\tnamed\tsrg");
        for (var cls : mapping.getClasses()) {
            // Forge retains Mojang class names while using SRG member names.
            lines.add("c\t" + cls.getOriginal() + "\t" + cls.getOriginal());
            for (var field : cls.getFields()) if (field.getDescriptor() != null)
                lines.add("\tf\t" + field.getDescriptor() + "\t" + field.getOriginal() + "\t" + field.getMapped());
            for (var method : cls.getMethods())
                lines.add("\tm\t" + method.getDescriptor() + "\t" + method.getOriginal() + "\t" + method.getMapped());
        }
        Files.write(Path.of(args[2]), lines);
        if (args.length > 3) {
            var client = namedToOfficial.reverse();
            var clientLines = new ArrayList<String>();
            clientLines.add("tiny\t2\t0\tofficial\tnamed");
            for (var cls : client.getClasses()) {
                clientLines.add("c\t" + cls.getOriginal() + "\t" + cls.getMapped());
                for (var field : cls.getFields()) if (field.getDescriptor() != null)
                    clientLines.add("\tf\t" + field.getDescriptor() + "\t" + field.getOriginal() + "\t" + field.getMapped());
                for (var method : cls.getMethods())
                    clientLines.add("\tm\t" + method.getDescriptor() + "\t" + method.getOriginal() + "\t" + method.getMapped());
            }
            Files.write(Path.of(args[3]), clientLines);
        }
    }
}
