package com.empirecraft.sails.test;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.*;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.service.*;

/** Headless application of the real Sponge Mixin transformer to the pinned JourneyMap bytecode. */
public final class JourneyMapMixinTest {
    public static final class Service extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {
        public String getName(){return "Sails JourneyMap test";}
        public boolean isValid(){return true;}
        public MixinEnvironment.Phase getInitialPhase(){return MixinEnvironment.Phase.DEFAULT;}
        public IClassProvider getClassProvider(){return this;}
        public IClassBytecodeProvider getBytecodeProvider(){return this;}
        public ITransformerProvider getTransformerProvider(){return this;}
        public IClassTracker getClassTracker(){return this;}
        public IMixinAuditTrail getAuditTrail(){return null;}
        public Collection<String> getPlatformAgents(){return List.of();}
        public IContainerHandle getPrimaryContainer(){return new ContainerHandleVirtual("sails-test");}
        public InputStream getResourceAsStream(String name){return JourneyMapMixinTest.class.getClassLoader().getResourceAsStream(name);}
        public URL[] getClassPath(){return new URL[0];}
        public Class<?> findClass(String name)throws ClassNotFoundException{return findClass(name,false);}
        public Class<?> findClass(String name,boolean initialize)throws ClassNotFoundException{return Class.forName(name,initialize,JourneyMapMixinTest.class.getClassLoader());}
        public Class<?> findAgentClass(String name,boolean initialize)throws ClassNotFoundException{return findClass(name,initialize);}
        public ClassNode getClassNode(String name)throws ClassNotFoundException,IOException{return getClassNode(name,false);}
        public ClassNode getClassNode(String name,boolean ignored)throws ClassNotFoundException,IOException {
            try(InputStream in=getResourceAsStream(name.replace('.','/')+".class")) {
                if(in==null)throw new ClassNotFoundException(name);
                ClassNode node=new ClassNode();new ClassReader(in).accept(node,ClassReader.EXPAND_FRAMES);return node;
            }
        }
        public Collection<ITransformer> getTransformers(){return List.of();}
        public Collection<ITransformer> getDelegatedTransformers(){return List.of();}
        public void addTransformerExclusion(String name){}
        public void registerInvalidClass(String name){}
        public boolean isClassLoaded(String name){return false;}
        public String getClassRestrictions(String name){return "";}
    }
    private record Key(String name) implements IPropertyKey {}
    public static final class Properties implements IGlobalPropertyService {
        private final Map<IPropertyKey,Object> values=new HashMap<>();
        public IPropertyKey resolveKey(String name){return new Key(name);}
        @SuppressWarnings("unchecked") public <T>T getProperty(IPropertyKey key){return(T)values.get(key);}
        public void setProperty(IPropertyKey key,Object value){values.put(key,value);}
        @SuppressWarnings("unchecked") public <T>T getProperty(IPropertyKey key,T fallback){return(T)values.getOrDefault(key,fallback);}
        public String getPropertyString(IPropertyKey key,String fallback){return String.valueOf(values.getOrDefault(key,fallback));}
    }
    public static void main(String[] args)throws Exception {
        // Supply only test services; neither Forge nor a graphical Minecraft client is initialized.
        MixinService.boot();var singleton=MixinService.class.getDeclaredMethod("getInstance");singleton.setAccessible(true);
        Object holder=singleton.invoke(null);
        var service=MixinService.class.getDeclaredField("service");service.setAccessible(true);service.set(holder,new Service());
        var properties=MixinService.class.getDeclaredField("propertyService");properties.setAccessible(true);properties.set(holder,new Properties());
        MixinBootstrap.init();MixinEnvironment env=MixinEnvironment.getCurrentEnvironment();env.setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("vs_sails-compat.mixins.json");
        var ctor=Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();ctor.setAccessible(true);
        IMixinTransformer transformer=(IMixinTransformer)ctor.newInstance();
        if(args.length>1 && args[1].equals("absent")) {
            String name="com.quintonc.vs_sails.client.MapHeading";
            byte[] bytes=JourneyMapMixinTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class").readAllBytes();
            transformer.transformClass(env,name,bytes);
            System.out.println("PASS: optional client compatibility starts without JourneyMap");return;
        }
        String[] targets={"journeymap.client.model.entity.EntityDTO","journeymap.client.render.draw.DrawEntityStep","journeymap.client.ui.minimap.MiniMap","journeymap.client.ui.fullscreen.Fullscreen"};
        int[] expected={1,2,5,2};int total=0;
        Path output=Path.of(args[0]);Files.createDirectories(output);
        for(int i=0;i<targets.length;i++) {
            String name=targets[i], resource=name.replace('.','/')+".class";
            byte[] before=JourneyMapMixinTest.class.getClassLoader().getResourceAsStream(resource).readAllBytes();
            byte[] after=transformer.transformClass(env,name,before);
            ClassNode node=new ClassNode();new ClassReader(after).accept(node,0);
            int hooked=0,raw=0;
            for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call) {
                if(call.owner.equals(node.name)&&call.name.contains("empirecraft$"))hooked++;
                if((call.owner.equals("net/minecraft/client/Camera")&&call.name.equals("m_90590_"))
                        ||((call.owner.equals("net/minecraft/world/entity/LivingEntity")||call.owner.equals("net/minecraft/world/entity/player/Player"))&&call.name.equals("m_146908_")))raw++;
            }
            if(hooked!=expected[i]||raw!=0)throw new AssertionError(name+" hooked="+hooked+" raw="+raw);
            Files.write(output.resolve(name.substring(name.lastIndexOf('.')+1)+".class"),after);
            total+=hooked;System.out.println("MIXIN_TARGET_PASS "+name+" sites="+hooked);
        }
        System.out.println("PASS: "+targets.length+" real JourneyMap classes transformed; "+total+" yaw reads corrected; no raw yaw reads remain");
    }
}
