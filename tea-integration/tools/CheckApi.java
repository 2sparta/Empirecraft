import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import java.util.*;import java.util.zip.*;import java.nio.file.*;import java.io.*;
public class CheckApi {
 static Map<String,ClassNode> base=new HashMap<>();static List<String> errors=new ArrayList<>();static int refs;
 static void load(byte[] bytes){ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);base.put(c.name,c);}
 static boolean member(String owner,String name,String desc,boolean method,Set<String> seen){if(!seen.add(owner))return false;var c=base.get(owner);if(c==null)return false;if(method){for(var m:c.methods)if(m.name.equals(name)&&m.desc.equals(desc))return true;}else for(var f:c.fields)if(f.name.equals(name)&&f.desc.equals(desc))return true;if(name.equals("<init>"))return false;if(c.superName!=null&&member(c.superName,name,desc,method,seen))return true;for(var i:c.interfaces)if(member(i,name,desc,method,seen))return true;return false;}
 static void check(String owner,String name,String desc,boolean method){if(!owner.startsWith("vectorwing/farmersdelight/")||owner.contains("/thirtyyearswar/"))return;refs++;if(!member(owner,name,desc,method,new HashSet<>()))errors.add(owner+"."+name+desc);}
 public static void main(String[] args)throws Exception{
  for(String path:args[0].split(java.io.File.pathSeparator))try(var z=new ZipFile(path)){for(var e:Collections.list(z.entries()))if(e.getName().endsWith(".class"))load(z.getInputStream(e).readAllBytes());}
  try(var z=new ZipFile(args[1])){for(var e:Collections.list(z.entries()))if(e.getName().endsWith(".class")){var c=new ClassNode();new ClassReader(z.getInputStream(e)).accept(c,0);if(c.superName!=null&&c.superName.startsWith("vectorwing/farmersdelight/")&&!c.superName.contains("/thirtyyearswar/")&&!base.containsKey(c.superName))errors.add("Missing superclass "+c.superName);for(var m:c.methods)for(var i:m.instructions){if(i instanceof MethodInsnNode x)check(x.owner,x.name,x.desc,true);if(i instanceof FieldInsnNode x)check(x.owner,x.name,x.desc,false);if(i instanceof InvokeDynamicInsnNode x)for(var a:x.bsmArgs)if(a instanceof Handle h)check(h.getOwner(),h.getName(),h.getDesc(),h.getTag()>=5);}}}
  System.out.println("TEA_API refs="+refs+" unresolved="+errors.size());errors.stream().distinct().forEach(System.out::println);if(!errors.isEmpty())System.exit(1);
 }
}
