import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Exact bytecode surgery on the pinned uploaded binary; unrelated implementations remain intact. */
public final class Patch implements Opcodes {
    static final String P="sfiomn/legendarysurvivaloverhaul/", H="ua/empirecraft/survival/SurvivalRules", C="ua/empirecraft/survival/SurvivalClient";
    static final Set<String> OFF=Set.of("healthOverhaulEnabled","localizedBodyDamageEnabled","customHealthRegenEnabled");
    static final Map<String,Integer> changed=new TreeMap<>();
    static void reset(MethodNode m){m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();m.visibleLocalVariableAnnotations=null;m.invisibleLocalVariableAnnotations=null;}
    static void zero(MethodNode m){reset(m);Type t=Type.getReturnType(m.desc);switch(t.getSort()){
        case Type.VOID -> m.instructions.add(new InsnNode(RETURN));
        case Type.FLOAT -> {m.instructions.add(new InsnNode(FCONST_0));m.instructions.add(new InsnNode(FRETURN));}
        case Type.DOUBLE -> {m.instructions.add(new InsnNode(DCONST_0));m.instructions.add(new InsnNode(DRETURN));}
        case Type.LONG -> {m.instructions.add(new InsnNode(LCONST_0));m.instructions.add(new InsnNode(LRETURN));}
        case Type.OBJECT,Type.ARRAY -> {m.instructions.add(new InsnNode(ACONST_NULL));m.instructions.add(new InsnNode(ARETURN));}
        default -> {m.instructions.add(new InsnNode(ICONST_0));m.instructions.add(new InsnNode(IRETURN));}
    }}
    static void delegate(MethodNode m,String owner,String name,int... locals){reset(m);for(int local:locals)m.instructions.add(new VarInsnNode(ALOAD,local));
        m.instructions.add(new MethodInsnNode(INVOKESTATIC,owner,name,m.desc,false));m.instructions.add(new InsnNode(Type.getReturnType(m.desc).getOpcode(IRETURN)));}
    static void patch(ClassNode c){for(MethodNode m:c.methods){
        if((m.access&(ACC_ABSTRACT|ACC_NATIVE))!=0)continue;
        int before=m.instructions.size();boolean edit=false;
        if(c.name.equals(P+"common/capabilities/ModCapabilities")&&m.name.equals("attachCapabilityPlayer")){delegate(m,H,"attach",0);edit=true;}
        if(c.name.equals(P+"LegendarySurvivalOverhaul")&&m.name.equals("registerCapabilities")){delegate(m,H,"capabilities",1);edit=true;}
        if(c.name.equals(P+"common/events/CommonModBusEvents")&&m.name.equals("onEntityAttributesChange")){delegate(m,H,"attributes",0);edit=true;}
        if(c.name.equals(P+"common/events/CommonForgeEvents")&&Set.of("onItemUse","onEntityHurt","onEntityHurtDamage","onSleepFinished","onPlayerEffect","onLevelLoad","shouldApplyLocalizedBodyDamage","shouldApplyHealthOverhaul").contains(m.name)){zero(m);edit=true;}
        if(Set.of(P+"common/recipe/PurificationSmeltingRecipe",P+"common/recipe/PurificationBlastingRecipe").contains(c.name)&&m.name.equals("m_5874_")){delegate(m,H,"purify",1,2);edit=true;}
        if(c.name.equals(P+"common/integration/jei/JeiIntegration")&&m.name.equals("addSewingRecipe")){delegate(m,H,"jeiSewing",1,2);edit=true;}
        if(c.name.equals(P+"client/events/ClientModBusEvents")&&m.name.equals("registerGuiOverlays")){delegate(m,C,"overlays",0);edit=true;}
        if(c.name.equals(P+"registry/KeyMappingRegistry")&&m.name.equals("registerKeyMappingsEvent")){delegate(m,C,"keys",0);edit=true;}
        if(c.name.equals(P+"client/ClientHooks")&&m.name.equals("openBodyHealthScreen")||c.name.equals(P+"registry/MobEffectRegistry")&&m.name.equals("registerBrewingRecipes")||c.name.equals(P+"client/tooltips/TooltipHandler")&&m.name.equals("addHealingText")){zero(m);edit=true;}
        if(c.name.equals(P+"common/items/CoatItem")&&m.name.equals("m_7373_")){
            reset(m);m.instructions.add(new VarInsnNode(ALOAD,1));m.instructions.add(new VarInsnNode(ALOAD,3));
            m.instructions.add(new MethodInsnNode(INVOKESTATIC,H,"tooltip","(Lnet/minecraft/world/item/ItemStack;Ljava/util/List;)V",false));m.instructions.add(new InsnNode(RETURN));edit=true;
        }
        if(c.name.equals(P+"config/Config$Baked")&&m.name.equals("bakeCommon")){
            for(var n:m.instructions.toArray())if(n.getOpcode()==RETURN)m.instructions.insertBefore(n,new MethodInsnNode(INVOKESTATIC,H,"lockConfiguration","()V",false));edit=true;
        }
        if(c.name.equals(P+"common/integration/curios/CuriosUtil")&&m.name.equals("isCurioItemEquipped")){zero(m);edit=true;}
        if((c.name.equals(P+"api/health/HealthUtil")||c.name.equals(P+"util/internal/HealthUtilInternal"))&&!m.name.startsWith("<")){
            zero(m);int player=(m.access&ACC_STATIC)!=0?0:1;
            if(m.name.equals("hurtPlayer")){reset(m);m.instructions.add(new VarInsnNode(FLOAD,player+1));m.instructions.add(new InsnNode(FRETURN));}
            if(Set.of("getPlayerMaxHealth","getPlayerStableMaxHealth","getDefaultMaxHealth").contains(m.name)){
                reset(m);m.instructions.add(new VarInsnNode(ALOAD,player));m.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,"net/minecraft/world/entity/player/Player","m_21233_","()F",false));m.instructions.add(new InsnNode(F2D));m.instructions.add(new InsnNode(DRETURN));
            }edit=true;
        }
        if((c.name.equals(P+"api/bodydamage/BodyDamageUtil")||c.name.equals(P+"util/internal/BodyDamageUtilInternal"))&&!m.name.startsWith("<")){zero(m);edit=true;}
        if(c.name.equals(P+"common/temperature/attribute/CoatAttributeModifier")&&m.name.equals("getItemAttributes")){delegate(m,H,"lining",1);edit=true;}
        if(c.name.equals(P+"common/containers/SewingTableContainer")&&m.name.equals("isItemArmor")){delegate(m,H,"inner",0);edit=true;}
        if(Set.of(P+"common/containers/SewingTableContainer",P+"common/containers/AbstractThermalContainer").contains(c.name)&&m.name.equals("<init>")){
            for(var n:m.instructions.toArray())if(n.getOpcode()==RETURN){var code=new InsnList();code.add(new VarInsnNode(ALOAD,0));code.add(new VarInsnNode(ALOAD,2));code.add(new MethodInsnNode(INVOKESTATIC,H,"menuOwner","(Lnet/minecraft/world/inventory/AbstractContainerMenu;Lnet/minecraft/world/entity/player/Inventory;)V",false));m.instructions.insertBefore(n,code);}edit=true;
        }
        if(c.name.equals(P+"util/internal/TemperatureUtilInternal")&&m.name.equals("setArmorCoatTag")){
            reset(m);m.instructions.add(new VarInsnNode(ALOAD,1));m.instructions.add(new VarInsnNode(ALOAD,2));m.instructions.add(new MethodInsnNode(INVOKESTATIC,H,"setCoat",m.desc,false));m.instructions.add(new InsnNode(RETURN));edit=true;
        }
        if(c.name.equals(P+"util/internal/TemperatureUtilInternal")&&m.name.equals("getArmorCoatTag")){delegate(m,H,"coat",1);edit=true;}
        if(c.name.equals(P+"registry/CreativeTabRegistry")&&m.desc.endsWith("Lnet/minecraft/world/item/CreativeModeTab$Output;)V")){
            reset(m);m.instructions.add(new VarInsnNode(ALOAD,1));m.instructions.add(new MethodInsnNode(INVOKESTATIC,H,"creative","(Lnet/minecraft/world/item/CreativeModeTab$Output;)V",false));m.instructions.add(new InsnNode(RETURN));edit=true;
        }
        if(c.name.startsWith(P+"common/listeners/Body")&&m.name.equals("sendDataToClient")){zero(m);edit=true;}
        if(c.name.equals(P+"registry/ItemRegistry")&&m.name.equals("register")&&m.desc.equals("(Lnet/minecraftforge/eventbus/api/IEventBus;)V")){
            m.instructions.insert(new MethodInsnNode(INVOKESTATIC,H,"registerExtra","()V",false));edit=true;
        }
        for(AbstractInsnNode n:m.instructions.toArray()){
            if(n instanceof FieldInsnNode f&&f.getOpcode()==GETSTATIC&&f.owner.equals(P+"registry/EnchantmentRegistry")&&f.desc.equals("Lnet/minecraftforge/registries/RegistryObject;")){
                var get=f.getNext();while(get!=null&&get.getOpcode()<0)get=get.getNext();var cast=get==null?null:get.getNext();while(cast!=null&&cast.getOpcode()<0)cast=cast.getNext();
                if(get instanceof MethodInsnNode call&&call.name.equals("get")&&cast instanceof TypeInsnNode t&&t.getOpcode()==CHECKCAST){m.instructions.set(f,new InsnNode(ACONST_NULL));m.instructions.remove(get);m.instructions.remove(cast);edit=true;}
            }
            if(n instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/world/item/enchantment/EnchantmentHelper")&&call.desc.equals("(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/item/ItemStack;)I")){
                call.owner=H;call.name="noEnchantment";call.itf=false;edit=true;
            }
            if(n instanceof FieldInsnNode f&&f.getOpcode()==GETSTATIC&&f.owner.equals(P+"config/Config$Baked")&&OFF.contains(f.name)){
                m.instructions.set(f,new InsnNode(ICONST_0));edit=true;
            }
            if(n instanceof FieldInsnNode f&&f.getOpcode()==GETSTATIC&&f.owner.equals(P+"config/Config$Baked")&&f.name.equals("baseFoodExhaustion")){
                m.instructions.set(f,new InsnNode(DCONST_0));edit=true;
            }
            if(c.name.equals(P+"network/NetworkHandler")&&n instanceof LdcInsnNode value&&"1".equals(value.cst)){value.cst="empirecraft-2.4.7-1";edit=true;}
            if(c.name.equals(P+"common/capabilities/wetness/WetnessCapability")&&n instanceof FieldInsnNode f&&f.getOpcode()==GETSTATIC&&f.name.equals("wetnessRainIncrease")){
                var code=new InsnList();code.add(new VarInsnNode(ALOAD,1));code.add(new MethodInsnNode(INVOKESTATIC,H,"rainGain","(ILnet/minecraft/world/entity/player/Player;)I",false));m.instructions.insert(f,code);edit=true;
            }
            if(n instanceof MethodInsnNode call&&call.owner.equals("net/minecraftforge/registries/DeferredRegister")&&call.name.equals("register")&&call.desc.equals("(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/minecraftforge/registries/RegistryObject;")){
                call.setOpcode(INVOKESTATIC);call.owner=H;call.name="register";call.desc="(Lnet/minecraftforge/registries/DeferredRegister;Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/minecraftforge/registries/RegistryObject;";call.itf=false;edit=true;
            }
            if(c.name.equals(P+"network/NetworkHandler")&&n instanceof MethodInsnNode call&&call.name.equals("registerMessage")){
                call.setOpcode(INVOKESTATIC);call.owner=H;call.name="packet";call.desc=call.desc.replace("(","(Lnet/minecraftforge/network/simple/SimpleChannel;").replace(")Lnet/minecraftforge/network/simple/IndexedMessageCodec$MessageHandler;",")Ljava/lang/Object;");call.itf=false;edit=true;
            }
            if(c.name.equals(P+"LegendarySurvivalOverhaul")&&n instanceof MethodInsnNode call&&call.owner.equals("net/minecraftforge/event/AddReloadListenerEvent")&&call.name.equals("addListener")){
                call.setOpcode(INVOKESTATIC);call.owner=H;call.name="reload";call.desc="(Lnet/minecraftforge/event/AddReloadListenerEvent;Lnet/minecraft/server/packs/resources/PreparableReloadListener;)V";call.itf=false;edit=true;
            }
        }
        if(c.name.equals(P+"registry/CommandRegistry")&&m.name.equals("registerCommandsEvent")){
            // Keep only temperature administration; removed health/body commands cannot mutate anything.
            for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode f&&Set.of("BODY_DAMAGE","HEALTH_COMMAND","FERN_TEST").contains(f.name)){
                AbstractInsnNode begin=n.getPrevious(),end=n;while(end!=null&&end.getOpcode()!=POP)end=end.getNext();
                if(end==null)throw new IllegalStateException("Command pattern changed");AbstractInsnNode stop=end.getNext();while(begin!=stop){AbstractInsnNode next=begin.getNext();m.instructions.remove(begin);begin=next;}edit=true;
            }
        }
        if(edit)changed.merge(c.name+"#"+m.name,1,Integer::sum);
    }}
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[1]);Files.createDirectories(out);
        try(var zip=new ZipFile(args[0])){for(var entry:Collections.list(zip.entries())){
            if(!entry.getName().endsWith(".class"))continue;
            byte[] raw=zip.getInputStream(entry).readAllBytes();var c=new ClassNode();new ClassReader(raw).accept(c,0);int count=changed.size();patch(c);
            if(count==changed.size())continue;
            var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(writer);byte[] result=writer.toByteArray();var checked=new ClassNode();new ClassReader(result).accept(checked,0);
            for(var m:checked.methods)if((m.access&(ACC_ABSTRACT|ACC_NATIVE))==0)new Analyzer<BasicValue>(new BasicVerifier()).analyze(checked.name,m);
            Path target=out.resolve(entry.getName());Files.createDirectories(target.getParent());Files.write(target,result);
        }}
        for(var entry:changed.entrySet())System.out.println("SURVIVAL_PATCH "+entry.getKey());
        if(changed.size()<30)throw new IllegalStateException("Missing patch targets: "+changed.size());
        System.out.println("SURVIVAL_PATCH_PASS methods="+changed.size());
    }
}
