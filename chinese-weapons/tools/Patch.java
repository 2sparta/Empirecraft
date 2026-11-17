import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class Patch {
 static final String P="net/mirrorloong/chineseweapons/",H="ua/empirecraft/chinese/RegistryHooks";
 static final Set<String> ROOTS=Set.of("net/minecraft/world/item/Item","net/minecraft/world/item/SwordItem","net/minecraft/world/item/ArmorItem","net/minecraft/world/item/HorseArmorItem","net/minecraft/world/item/ShieldItem","net/minecraft/world/item/TieredItem");
 static Map<String,ClassNode> nodes=new HashMap<>();
 static boolean item(String name){if(ROOTS.contains(name))return true;var c=nodes.get(name);return c!=null&&c.superName!=null&&item(c.superName);}
 static void empty(MethodNode m){m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();var r=Type.getReturnType(m.desc);int t=r.getSort();if(t==Type.VOID)m.instructions.add(new InsnNode(Opcodes.RETURN));else if(t==Type.DOUBLE){m.instructions.add(new InsnNode(Opcodes.DCONST_0));m.instructions.add(new InsnNode(Opcodes.DRETURN));}else if(t==Type.FLOAT){m.instructions.add(new InsnNode(Opcodes.FCONST_0));m.instructions.add(new InsnNode(Opcodes.FRETURN));}else if(t==Type.LONG){m.instructions.add(new InsnNode(Opcodes.LCONST_0));m.instructions.add(new InsnNode(Opcodes.LRETURN));}else if(t==Type.OBJECT||t==Type.ARRAY){m.instructions.add(new InsnNode(Opcodes.ACONST_NULL));m.instructions.add(new InsnNode(Opcodes.ARETURN));}else{m.instructions.add(new InsnNode(Opcodes.ICONST_0));m.instructions.add(new InsnNode(Opcodes.IRETURN));}}
 static void noEnchant(ClassNode c,String name,String descriptor){c.methods.removeIf(m->m.name.equals(name)&&m.desc.equals(descriptor));var m=new MethodNode(Opcodes.ACC_PUBLIC,name,descriptor,null,null);empty(m);c.methods.add(m);}
 public static void main(String[] args)throws Exception{
  Path in=Path.of(args[0]),out=Path.of(args[1]);try(var paths=Files.walk(in)){for(var p:paths.filter(p->p.toString().endsWith(".class")).toList()){var c=new ClassNode();new ClassReader(Files.readAllBytes(p)).accept(c,0);nodes.put(c.name,c);}}
  for(var c:nodes.values()){
   boolean isItem=item(c.name),disable=c.name.startsWith(P+"procedures/weapon/")||c.name.startsWith(P+"procedures/armor/")&&!c.name.contains("DyeableItem")||c.name.equals(P+"procedures/TangDynastyHorseJianProcedure")||c.name.equals(P+"procedures/tips")||c.name.equals(P+"ChineseweaponsMod$ForgeEvent");
   boolean skillClient=c.name.startsWith(P+"client/Glaive")||c.name.equals(P+"client/TangDynastyHengSaberClientEvents")||c.name.equals(P+"item/GuDuoItem$Client");
   boolean plugin=c.name.contains("/jei/")||c.name.contains("/compat/emi/")||c.name.endsWith("/procedures/SectionBanner");
   if(disable||skillClient||plugin){if(c.visibleAnnotations!=null)c.visibleAnnotations.removeIf(a->a.desc.contains("EventBusSubscriber")||a.desc.contains("JeiPlugin"));for(var m:c.methods){if(m.visibleAnnotations!=null)m.visibleAnnotations.removeIf(a->a.desc.contains("SubscribeEvent"));if(disable&&!m.name.startsWith("<"))empty(m);}}
   if(isItem){Set<String> remove=new HashSet<>(Set.of("m_6883_","m_7373_","m_7579_","m_6880_"));if(c.name.contains("Glaive")||c.name.contains("glaive")||c.name.contains("DaggerAxe"))remove.add("m_7203_");c.methods.removeIf(m->remove.contains(m.name));noEnchant(c,"m_6473_","()I");noEnchant(c,"m_8120_","(Lnet/minecraft/world/item/ItemStack;)Z");noEnchant(c,"isBookEnchantable","(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z");noEnchant(c,"canApplyAtEnchantingTable","(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/Enchantment;)Z");}
   String kind=c.name.equals(P+"init/ChineseweaponsModItems")?"item":c.name.equals(P+"init/ChineseweaponsModBlocks")?"block":c.name.equals(P+"init/ChineseweaponsModBlockEntities")?"blockEntity":c.name.equals(P+"init/ChineseWeaponsModEffects")?"effect":null;
   for(var m:c.methods){
    if(kind!=null&&kind.equals("item")&&m.name.equals("block")&&m.desc.equals("(Lnet/minecraftforge/registries/RegistryObject;)Lnet/minecraftforge/registries/RegistryObject;")){empty(m);m.instructions.clear();m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"blockItem",m.desc,false));m.instructions.add(new InsnNode(Opcodes.ARETURN));}

    if(c.name.equals(P+"item/GuDuoItem$SquashPacket")&&m.name.equals("handle"))empty(m);
    if(c.name.equals(P+"item/GuDuoItem")&&Set.of("DFHurt","HurtFlyyy","HurtStopHo","performRiptide").contains(m.name))empty(m);
    for(var i:m.instructions.toArray()){
     if(c.name.equals(P+"entity/SongJavelinProjectile")&&i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTFIELD&&Set.of("loyaltyLevel","hasChanneling").contains(f.name)){var zero=new InsnList();zero.add(new InsnNode(Opcodes.POP));zero.add(new InsnNode(Opcodes.ICONST_0));m.instructions.insertBefore(i,zero);}
     if(c.name.equals(P+"entity/SongJavelinProjectile")&&m.name.equals("m_5790_")&&i.getOpcode()==Opcodes.FCONST_2&&i.getNext()!=null&&i.getNext().getOpcode()==Opcodes.FADD){m.instructions.set(i,new InsnNode(Opcodes.FCONST_0));continue;}
     if(!(i instanceof MethodInsnNode call))continue;
     if(call.owner.equals("net/minecraft/world/item/Item$Properties")&&call.name.equals("m_41486_")&&call.desc.equals("()Lnet/minecraft/world/item/Item$Properties;")){m.instructions.remove(call);continue;}
     if(Set.of("m_7292_","m_147207_").contains(call.name)&&call.desc.startsWith("(Lnet/minecraft/world/effect/MobEffectInstance;")){call.owner=H;call.name="rejectEffect";call.desc="(Lnet/minecraft/world/entity/LivingEntity;"+call.desc.substring(1);call.setOpcode(Opcodes.INVOKESTATIC);}

     if(kind!=null&&call.owner.equals("net/minecraftforge/registries/DeferredRegister")&&call.name.equals("register")&&call.desc.equals("(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/minecraftforge/registries/RegistryObject;")){call.setOpcode(Opcodes.INVOKESTATIC);call.owner=H;call.name=kind;call.desc="(Lnet/minecraftforge/registries/DeferredRegister;Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/minecraftforge/registries/RegistryObject;";}
     if(c.name.equals(P+"item/ReachSwordItem")&&call.owner.equals(P+"compat/WeaponScriptSettings")&&call.name.equals("getReachBonus")){call.owner=H;call.name="reach";}
     if(call.owner.equals("net/minecraft/world/item/enchantment/EnchantmentHelper")&&call.desc.equals("(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/item/ItemStack;)I")){call.owner=H;call.name="enchantmentLevel";call.setOpcode(Opcodes.INVOKESTATIC);}
    }
   }
   var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Path p=out.resolve(c.name+".class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());
  }
  System.out.println("PATCH_CLASSES "+nodes.size());
 }
}
