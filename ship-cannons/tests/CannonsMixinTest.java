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
        MixinBootstrap.init();
        MixinEnvironment env=MixinEnvironment.getCurrentEnvironment();env.setSide(MixinEnvironment.Side.valueOf(args[0]));
        boolean cbc=args.length<2 || !args[1].equals("without-cbc");
        if(cbc)Mixins.addConfiguration("vs-cbc-smoke.mixins.json");
        Mixins.addConfiguration("empirecraft-ship-cannons.mixins.json");
        var ctor=Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();ctor.setAccessible(true);
        IMixinTransformer transformer=(IMixinTransformer)ctor.newInstance();
        env.setActiveTransformer(transformer);com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        List<String> targets=new ArrayList<>(List.of(
            "net.mehvahdjukaar.supplementaries.common.block.tiles.CannonBlockTile",
            "net.mehvahdjukaar.moonlight.api.entity.ImprovedProjectileEntity",
            "org.valkyrienskies.mod.common.util.GameToPhysicsAdapter"));
        if(args[0].equals("CLIENT"))targets.add("net.mehvahdjukaar.supplementaries.client.cannon.CannonController");
        if(cbc)targets.addAll(List.of(
            "rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption",
            "rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption",
            "rbasamoyai.createbigcannons.cannon_control.carriage.CannonCarriageEntity",
            "rbasamoyai.createbigcannons.munitions.big_cannon.DisintegratingBigCannonProjectile",
            "rbasamoyai.createbigcannons.munitions.big_cannon.grapeshot.GrapeshotBagProjectile",
            "rbasamoyai.createbigcannons.munitions.big_cannon.shrapnel.ShrapnelBurst",
            "rbasamoyai.ritchiesprojectilelib.projectile_burst.ProjectileBurst"));
        int total=0;
        for(String target:targets) {
            byte[] original;
            try(var in=CannonsMixinTest.class.getClassLoader().getResourceAsStream(target.replace('.','/')+".class")) {if(in==null)throw new AssertionError(target);original=in.readAllBytes();}
            byte[] after=transformer.transformClass(env,target,original);
            ClassNode node=new ClassNode();new ClassReader(after).accept(node,0);int hooks=0;
            for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call &&
                (call.name.contains("empirecraft$") || call.owner.startsWith("ua/empirecraft/shipcannons/")))hooks++;
            if(hooks==0)throw new AssertionError("No production hook in "+target);
            if(target.endsWith("MountedBigCannonContraption") || target.endsWith("MountedAutocannonContraption")) {
                boolean nativeHook=false,customHook=false;
                for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call) {
                    nativeHook|=call.name.contains("vs$handleRecoil");customHook|=call.owner.endsWith("/CBCRecoil") && call.name.equals("capture");
                }
                if(!nativeHook || !customHook)throw new AssertionError("VS/CBC hook composition failed: "+target);
            }
            total+=hooks;System.out.println("MIXIN_TARGET_PASS "+target+" hooks="+hooks);
        }
        int checks=0;
        for(int yaw=-180;yaw<=180;yaw+=30)for(int pitch=-60;pitch<=60;pitch+=30)for(int shipYaw=-180;shipYaw<=180;shipYaw+=30) {
            var rotation=new org.joml.Quaterniond().rotateY(Math.toRadians(shipYaw)).rotateX(.15).rotateZ(-.1);
            var local=ua.empirecraft.shipcannons.AimMath.localDirection(rotation,yaw,pitch);
            if(rotation.transform(local).distance(ua.empirecraft.shipcannons.AimMath.direction(yaw,pitch))>1e-9)throw new AssertionError("Aim rotation");checks++;
        }
        System.out.println("CANNON_MIXINS_PASS side="+args[0]+" cbc="+cbc+" targets="+targets.size()+" hooks="+total+" aim_checks="+checks);
    }
}
