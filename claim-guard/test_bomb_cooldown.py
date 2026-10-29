#!/usr/bin/env python3
"""Execute the production cooldown helper with small SRG API fixtures; no runtime download."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent
SOURCES = {
    'net/minecraft/world/item/Item.java': 'package net.minecraft.world.item; public class Item {}',
    'net/mehvahdjukaar/supplementaries/common/items/BombItem.java': 'package net.mehvahdjukaar.supplementaries.common.items; public class BombItem extends net.minecraft.world.item.Item {}',
    'net/minecraft/world/item/ItemCooldowns.java': '''package net.minecraft.world.item;
public class ItemCooldowns {
    public final java.util.Map<Item,Integer> times=new java.util.HashMap<>();
    public boolean m_41519_(Item item){return times.getOrDefault(item,0)>0;}
    public void m_41524_(Item item,int ticks){times.put(item,ticks);}
    public void tick(){times.replaceAll((item,ticks)->Math.max(0,ticks-1));}
}''',
    'net/minecraft/world/entity/player/Player.java': '''package net.minecraft.world.entity.player;
public class Player {
    private final net.minecraft.world.item.ItemCooldowns cooldowns=new net.minecraft.world.item.ItemCooldowns();
    public net.minecraft.world.item.ItemCooldowns m_36335_(){return cooldowns;}
}''',
    'net/minecraftforge/registries/ForgeRegistries.java': '''package net.minecraftforge.registries;
public class ForgeRegistries {
    public static final Registry ITEMS=new Registry();
    public static class Registry {
        public final java.util.List<net.minecraft.world.item.Item> values=new java.util.ArrayList<>();
        public java.util.Collection<net.minecraft.world.item.Item> getValues(){return values;}
    }
}''',
    'CooldownTest.java': '''import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.mehvahdjukaar.supplementaries.common.items.BombItem;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.claimguard.BombCooldown;
public class CooldownTest {
    public static void main(String[] args){
        Item regular=new BombItem(),blue=new BombItem(),spiky=new BombItem(),other=new Item();
        ForgeRegistries.ITEMS.values.addAll(java.util.List.of(regular,blue,spiky,other));
        Player a=new Player(),b=new Player();
        assert !BombCooldown.active(a);
        BombCooldown.start(a);
        for(Item item:java.util.List.of(regular,blue,spiky)) assert a.m_36335_().times.get(item)==800;
        assert !a.m_36335_().m_41519_(other);
        assert !BombCooldown.active(b);
        for(int i=0;i<799;i++)a.m_36335_().tick();
        assert BombCooldown.active(a);
        a.m_36335_().tick();assert !BombCooldown.active(a);
        a.m_36335_().m_41524_(spiky,10);assert BombCooldown.active(a);
        System.out.println("PASS shared bomb types, independent players, 799/800-tick boundary and unrelated items");
    }
}''',
}

def main():
    java = str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    compiler = [shutil.which('javac')] if shutil.which('javac') else [java, 'com.sun.tools.javac.Main']
    with tempfile.TemporaryDirectory(prefix='bomb-cooldown-') as directory:
        build = Path(directory)
        files = []
        for name, source in SOURCES.items():
            path = build/name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source)
            files.append(path)
        subprocess.run([*compiler, '--release', '17', '-d', str(build), *map(str, files), str(ROOT/'src/main/java/ua/empirecraft/claimguard/BombCooldown.java')], check=True)
        subprocess.run([java, '-ea', '-cp', str(build), 'CooldownTest'], check=True)

if __name__ == '__main__':
    main()
