#!/usr/bin/env python3
"""Exercise deferred color callbacks on the JVM, including an unregistered chorus block."""
import subprocess,tempfile
from pathlib import Path
from client_colors import compile_tools,retrofit,BUILDERS
from build import BUILD,EXPORTS,archive,entries,relocate_helper

def main():
 compile_tools()
 with tempfile.TemporaryDirectory(dir=BUILD) as temp:
  root=Path(temp);src=root/'src';classes=root/'classes';src.mkdir();classes.mkdir()
  def source(name,text):
   path=src/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text)
  hook='ua/empirecraft/nativecut/j0123456789ab/PolicyHooks'
  for kind in ['Block','Item']:
   source('com/tterrag/registrate/builders/'+kind+'Builder.java','''package com.tterrag.registrate.builders;
import java.util.*;
public class KINDBuilder {
 public static final String NATIVE_HOOK="__HOOK__";
 public final List<Runnable> listeners=new ArrayList<>();
 private final String name,key;public int colors;
 public KINDBuilder(String name,String key){this.name=name;this.key=key;}
 public Owner getOwner(){return new Owner();}public String getName(){return name;}
 public String getRegistryKey(){return key;}
 public static class Owner {public String getModid(){return "fruitsdelight";}}
 public Object getEntry(){if(name.startsWith("chorus_"))throw new IllegalArgumentException("Unknown registration "+name);return this;}
 protected void registerKINDColor(){listeners.add(()->{getEntry();colors++;});}
 public void color(){registerKINDColor();}
}
'''.replace('KIND',kind).replace('__HOOK__',hook))
  source('TestColors.java','''import com.tterrag.registrate.builders.*;
public class TestColors {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  var removed=new BlockBuilder("chorus_jelly_block","minecraft:block]");removed.color();
  check(removed.listeners.isEmpty());check(removed.colors==0);
  var kept=new BlockBuilder("apple_jelly_block","minecraft:block]");kept.color();
  check(kept.listeners.size()==1);kept.listeners.forEach(Runnable::run);check(kept.colors==1);
  var item=new ItemBuilder("chorus_jelly","minecraft:item]");item.color();check(item.listeners.isEmpty());
  var keptItem=new ItemBuilder("apple_jelly","minecraft:item]");keptItem.color();
  check(keptItem.listeners.size()==1);keptItem.listeners.forEach(Runnable::run);check(keptItem.colors==1);
  var otherRegistry=new ItemBuilder("chorus_jelly","minecraft:other]");otherRegistry.color();
  check(otherRegistry.listeners.size()==1);
  System.out.println("PASS removed block/item colors skipped; retained colors execute; other registries unchanged");
 }
}
''')
  subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(classes),*[str(p) for p in src.rglob('*.java')]],check=True)
  data={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
  data[hook+'.class']=relocate_helper((BUILD/'shared/TEMPLATE/PolicyHooks.class').read_bytes(),hook.rsplit('/',1)[0].replace('/','.'))
  data[hook.rsplit('/',1)[0]+'/aliases.tsv']=b'fruitsdelight:chorus_jelly_block\tfruitsdelight:apple_jelly_block\nfruitsdelight:chorus_jelly\tfruitsdelight:apple_jelly\n'
  original=archive(data);patched,names=retrofit(original);assert set(names)==BUILDERS
  again,duplicate=retrofit(patched);assert again==patched and not duplicate
  for name,value in entries(patched).items():
   path=classes/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(value)
  subprocess.run(['java','-Xverify:all','-cp',str(classes),'TestColors'],check=True)
  # JarJar copies must receive the same fix; unrelated entries stay byte-identical.
  nested=archive({'META-INF/jarjar/Registrate.jar':original,'unchanged.txt':b'keep'})
  result,names=retrofit(nested);assert len(names)==2
  assert entries(result)['unchanged.txt']==b'keep'
  assert entries(result)['META-INF/jarjar/Registrate.jar']==patched
 print('PASS client-color regression, JVM verification, idempotency and nested JarJar coverage')

if __name__=='__main__':main()
