package ua.empirecraft.shipcannons.test;

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

/** Headless application of the real Sponge Mixin transformer to the pinned Supplementaries/Moonlight bytecode. */
public final class CannonsMixinTest {
    public static final class Service extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {
        public String getName(){return "Ship cannon compatibility test";}
        public boolean isValid(){return true;}
        public MixinEnvironment.Phase getInitialPhase(){return MixinEnvironment.Phase.DEFAULT;}
        public IClassProvider getClassProvider(){return this;}
        public IClassBytecodeProvider getBytecodeProvider(){return this;}
        public ITransformerProvider getTransformerProvider(){return this;}
        public IClassTracker getClassTracker(){return this;}
        public IMixinAuditTrail getAuditTrail(){return null;}
        public Collection<String> getPlatformAgents(){return List.of();}
        public IContainerHandle getPrimaryContainer(){return new ContainerHandleVirtual("sails-test");}
        public InputStream getResourceAsStream(String name){return CannonsMixinTest.class.getClassLoader().getResourceAsStream(name);}
        public URL[] getClassPath(){return new URL[0];}
        public Class<?> findClass(String name)throws ClassNotFoundException{return findClass(name,false);}
        public Class<?> findClass(String name,boolean initialize)throws ClassNotFoundException{return Class.forName(name,initialize,CannonsMixinTest.class.getClassLoader());}
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
        MixinService.boot();var singleton=MixinService.class.getDeclaredMethod("getInstance");singleton.setAccessible(true);
        Object holder=singleton.invoke(null);
        var service=MixinService.class.getDeclaredField("service");service.setAccessible(true);service.set(holder,new Service());
        var properties=MixinService.class.getDeclaredField("propertyService");properties.setAccessible(true);properties.set(holder,new Properties());
        MixinBootstrap.init();MixinEnvironment env=MixinEnvironment.getCurrentEnvironment();env.setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("empirecraft-ship-cannons.mixins.json");
        var ctor=Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();ctor.setAccessible(true);
        IMixinTransformer transformer=(IMixinTransformer)ctor.newInstance();
        String name="net.mehvahdjukaar.supplementaries.client.cannon.CannonController";
        byte[] before=CannonsMixinTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class").readAllBytes();
        byte[] after=transformer.transformClass(env,name,before);
        ClassNode node=new ClassNode();new ClassReader(after).accept(node,0);int hooked=0,raw=0;
        for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call) {
            if(call.owner.equals(node.name)&&call.name.contains("empirecraft$"))hooked++;
            if(call.owner.equals("net/minecraft/core/BlockPos") && (call.name.equals("m_203193_")||call.name.equals("m_252807_")))raw++;
        }
        if(hooked!=4||raw!=0)throw new AssertionError("hooked="+hooked+" raw="+raw);
        System.out.println("PASS: real CannonController transformed, four hooks, no uncorrected range/camera reads");
        String projectile="net.mehvahdjukaar.moonlight.api.entity.ImprovedProjectileEntity";
        byte[] original=CannonsMixinTest.class.getClassLoader().getResourceAsStream(projectile.replace('.','/')+".class").readAllBytes();
        ClassNode moonlight=new ClassNode();new ClassReader(transformer.transformClass(env,projectile,original)).accept(moonlight,0);
        boolean item=false,ball=false,transition=false;
        for(MethodNode method:moonlight.methods)for(AbstractInsnNode insn:method.instructions) {
            if(insn instanceof TypeInsnNode type && type.getOpcode()==org.objectweb.asm.Opcodes.INSTANCEOF) {
                item |= type.desc.endsWith("/SlingshotProjectileEntity");ball |= type.desc.endsWith("/CannonBallEntity");
            }
            if(insn instanceof MethodInsnNode call && call.owner.endsWith("/WorldEntityHandler") && call.name.equals("moveEntityFromShipyardToWorld"))transition=true;
        }
        if(!item || !ball || !transition)throw new AssertionError("Missing item/cannonball world-transition hook");
        System.out.println("PASS: real Moonlight tick transformed for both cannonballs and item projectiles");
        int checks=0;
        for(int yaw=-180;yaw<=180;yaw+=30)for(int pitch=-60;pitch<=60;pitch+=30)for(int shipYaw=-180;shipYaw<=180;shipYaw+=30) {
            var rotation=new org.joml.Quaterniond().rotateY(Math.toRadians(shipYaw)).rotateX(.15).rotateZ(-.1);
            var local=ua.empirecraft.shipcannons.AimMath.localDirection(rotation,yaw,pitch);
            var actual=rotation.transform(local);
            if(actual.distance(ua.empirecraft.shipcannons.AimMath.direction(yaw,pitch))>1e-9)throw new AssertionError("aim rotation");
            checks++;
        }
        System.out.println("PASS: "+checks+" aim transforms including ship yaw, roll and pitch");
    }
}
