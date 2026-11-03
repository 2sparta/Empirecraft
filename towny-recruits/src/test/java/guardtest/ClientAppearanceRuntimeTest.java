package guardtest;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import java.io.InputStream;
import java.lang.reflect.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CPU-only linkage/execution check for the actual compiled hooks, not a graphical-client boot. */
public final class ClientAppearanceRuntimeTest {
 static final class Loader extends ClassLoader {Class<?> define(byte[] bytes){return defineClass(null,bytes,0,bytes.length);}}
 public static void main(String[] args)throws Exception{
  String source="ua/townyrecruits/mixin/HumanAppearanceMixin",target="guardtest/generated/AppearanceHookHarness";
  byte[] original;try(InputStream in=ClientAppearanceRuntimeTest.class.getClassLoader().getResourceAsStream(source+".class")){original=in.readAllBytes();}
  ClassWriter writer=new ClassWriter(0);
  ClassVisitor visitor=new ClassRemapper(new ClassVisitor(Opcodes.ASM9,writer){
   @Override public void visit(int version,int access,String name,String signature,String superName,String[] interfaces){super.visit(version,access&~Opcodes.ACC_ABSTRACT,name,signature,superName,interfaces);}
   @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible){return null;}
  },new SimpleRemapper(source,target));
  new ClassReader(original).accept(visitor,0);
  Class<?> harness=new Loader().define(writer.toByteArray());Object renderer=harness.getConstructor().newInstance();
  // Reflection resolves the real descriptors, so a missing dependency cannot hide in an unused method.
  assert harness.getDeclaredMethods().length>=4;
  Field scale=harness.getDeclaredField("townyrecruits$scale");scale.setAccessible(true);
  Method apply=harness.getDeclaredMethod("scalePose",PoseStack.class);apply.setAccessible(true);
  Method restore=harness.getDeclaredMethod("restore",CallbackInfo.class);restore.setAccessible(true);
  PoseStack pose=new PoseStack();pose.m_85837_(1,2,3);pose.m_85841_(2,3,4);
  Matrix4f initial=new Matrix4f(pose.m_85850_().m_252922_());
  scale.setFloat(renderer,0.75f);assert apply.invoke(renderer,pose)==pose;
  assert Math.abs(pose.m_85850_().m_252922_().m11()-2.25f)<0.00001f;
  Matrix4f outer=new Matrix4f(pose.m_85850_().m_252922_());
  scale.setFloat(renderer,1.25f);apply.invoke(renderer,pose);
  assert Math.abs(pose.m_85850_().m_252922_().m11()-2.8125f)<0.00001f;
  restore.invoke(renderer,new CallbackInfo("nested",false));assert pose.m_85850_().m_252922_().equals(outer,0.00001f);
  restore.invoke(renderer,new CallbackInfo("outer",false));assert pose.m_85850_().m_252922_().equals(initial,0.00001f);
  restore.invoke(renderer,new CallbackInfo("empty",false));assert pose.m_85850_().m_252922_().equals(initial,0.00001f);
  Field skins=harness.getDeclaredField("townyrecruits$skins");skins.setAccessible(true);assert ((Object[])skins.get(null)).length==128;
  System.out.println("PASS actual compiled client hooks link and execute: 128 texture resources, real PoseStack scaling, nested renders and exact restoration; no graphics boot simulated");
 }
}
