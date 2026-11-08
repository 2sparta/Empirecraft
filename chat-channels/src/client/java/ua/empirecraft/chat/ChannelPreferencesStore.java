package ua.empirecraft.chat;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Only the extra tab preference lives here; ChatPlus keeps its native enabled flag. */
public final class ChannelPreferencesStore {
    private final Path path;private boolean merged;
    public ChannelPreferencesStore(Path path){this.path=path;}
    public boolean merged(){return merged;}
    public void load()throws IOException {
        if(!Files.exists(path)){merged=false;return;}
        try {
            var root=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
            var value=root.get("mergeGlobalAndSystem");
            if(value!=null&&(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean()))throw new IllegalArgumentException("mergeGlobalAndSystem must be a boolean");
            merged=value!=null&&value.getAsBoolean();
        }catch(RuntimeException malformed){throw new IOException("Invalid ChatPlus channel preferences: "+path,malformed);}
    }
    public void save(boolean value)throws IOException {
        Path parent=path.toAbsolutePath().getParent();Files.createDirectories(parent);
        Path temporary=Files.createTempFile(parent,"empirecraft-channels-",".tmp");
        try {
            Files.writeString(temporary,"{\n  \"mergeGlobalAndSystem\": "+value+"\n}\n",StandardCharsets.UTF_8);
            try{Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
            merged=value;
        }finally{Files.deleteIfExists(temporary);}
    }
}
