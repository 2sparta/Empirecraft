/** JVM verification of the actual patched classes, without initializing the game or graphics. */
public final class VerifyClientClasses {
    public static void main(String[] args)throws Exception {
        String[] names={"config.Config","config.ConfigScreen","config.forge.ConfigScreenImpl","features.chattabs.ChatTab","features.chattabs.ChatTabs","features.HideChat","features.TranslateMessage","features.speechtotext.MicrophoneThread","features.PeekChat"};
        for(String name:names)Class.forName("com.ebicep.chatplus."+name,false,VerifyClientClasses.class.getClassLoader()).getDeclaredMethods();
        System.out.println("CHAT_CLIENT_JVM_VERIFY_PASS classes="+names.length+"; real bytecode and stack maps, no game initialization");
    }
}
