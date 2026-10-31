import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
public class LinkCheck{
 record Info(String parent,String[] interfaces,Set<String> methods,Set<String> fields,int access){}
 static Map<String,Path> paths=new HashMap<>();static Map<String,Info> infos=new HashMap<>();static Set<String> unknown=new TreeSet<>(),errors=new TreeSet<>();static int verified;
 static Info info(String name){
  if(infos.containsKey(name))return infos.get(name);byte[] data=null;
  try{Path p=paths.get(name);if(p!=null){try(var z=new ZipFile(p.toFile())){data=z.getInputStream(z.getEntry(name+".class")).readAllBytes();}}else if(name.startsWith("java/")){try(var in=ClassLoader.getSystemResourceAsStream(name+".class")){if(in!=null)data=in.readAllBytes();}}
  }catch(Exception e){throw new RuntimeException(e);}
  if(data==null){unknown.add(name);infos.put(name,null);return null;}
  final Info[] found={null};new ClassReader(data).accept(new ClassVisitor(Opcodes.ASM8){
   public void visitPermittedSubclass(String s){} Set<String> ms=new HashSet<>(),fs=new HashSet<>();String p;String[] is;int access;
   public void visit(int v,int a,String n,String s,String parent,String[] i){p=parent;is=i;access=a;}
   public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){ms.add(n+d);return null;}
   public FieldVisitor visitField(int a,String n,String d,String s,Object o){fs.add(n+d);return null;}
   public void visitEnd(){found[0]=new Info(p,is,ms,fs,access);}
  },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);infos.put(name,found[0]);return found[0];
 }
 static Boolean has(String owner,String key,boolean field,Set<String> seen){
  if(owner==null||!seen.add(owner))return false;Info in=info(owner);if(in==null)return null;
  if((field?in.fields:in.methods).contains(key))return true;
  if(key.startsWith("<init>"))return false;
  boolean unknown=false;Boolean b=has(in.parent,key,field,seen);if(Boolean.TRUE.equals(b))return true;if(b==null)unknown=true;
  for(String it:in.interfaces){b=has(it,key,field,seen);if(Boolean.TRUE.equals(b))return true;if(b==null)unknown=true;}
  return unknown?null:false;
 }
 static void verify(String from,String owner,String name,String desc,boolean field){
  if(owner.startsWith("ua/posters/"))return;Boolean b=has(owner,name+desc,field,new HashSet<>());
  if(Boolean.FALSE.equals(b))errors.add(from+" -> "+owner+"."+name+desc);if(Boolean.TRUE.equals(b))verified++;
 }
 public static void main(String[] args)throws Exception{
  for(String s:Files.readString(Path.of(args[0])).split(":")){Path p=Path.of(s);if(!Files.isRegularFile(p)||!s.endsWith(".jar"))continue;try(var z=new ZipFile(p.toFile())){z.stream().filter(e->e.getName().endsWith(".class")).forEach(e->paths.putIfAbsent(e.getName().substring(0,e.getName().length()-6),p));}}
  try(var stream=Files.walk(Path.of(args[1]))){for(Path file:stream.filter(p->p.toString().endsWith(".class")).toList()){
   String from=file.getFileName().toString();new ClassReader(Files.readAllBytes(file)).accept(new ClassVisitor(Opcodes.ASM8){public MethodVisitor visitMethod(int a,String n,String d,String sig,String[] ex){return new MethodVisitor(Opcodes.ASM8){
    public void visitMethodInsn(int op,String owner,String name,String desc,boolean it){verify(from,owner,name,desc,false);}
    public void visitFieldInsn(int op,String owner,String name,String desc){verify(from,owner,name,desc,true);}
    public void visitInvokeDynamicInsn(String name,String desc,Handle h,Object... args){if(h.getOwner().equals("java/lang/invoke/LambdaMetafactory")&&args.length>0&&args[0] instanceof Type mt){Type ret=Type.getReturnType(desc);if(ret.getSort()==Type.OBJECT)verify(from,ret.getInternalName(),name,mt.getDescriptor(),false);}}
   };}},ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
  }}
  System.out.println("Verified runtime member references: "+verified);for(String e:errors)System.out.println("ERROR "+e);
  System.out.println("Unavailable client/runtime types (not validated): "+unknown);if(!errors.isEmpty())System.exit(1);
 }
}
