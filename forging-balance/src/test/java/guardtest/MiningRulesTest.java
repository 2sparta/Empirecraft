package guardtest;
import java.util.*;
import ua.empirecraft.forging.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Compare the live correction against vanilla Efficiency over materials, blocks and penalties. */
public final class MiningRulesTest {
    static void near(float expected,float actual){if(Math.abs(expected-actual)>Math.max(0.00001f,Math.abs(expected)*0.000002f))throw new AssertionError(expected+" != "+actual);}
    static int ticks(float speed,float hardness,boolean harvest){return (int)Math.ceil(hardness*(harvest?30.0:100.0)/speed);}
    public static void main(String[] args)throws Exception {
        int scenarios=0;
        for(float base:new float[]{2,4,6,8,9,12})for(int level:new int[]{3,5}){
            String quality=level==3?"perfect":"master";
            for(float penalty:new float[]{1,1.4f,0.3f,0.09f,0.2f,0.04f}){
                float vanilla=(base+level*level+1)*penalty;
                float actual=MiningRules.speed(quality,base)*penalty;
                assert Float.floatToIntBits(vanilla)==Float.floatToIntBits(actual);
                for(float hardness:new float[]{0.5f,1.5f,3,50})for(boolean harvest:new boolean[]{true,false})
                    assert ticks(vanilla,hardness,harvest)==ticks(actual,hardness,harvest);
                scenarios++;
            }
        }
        for(String quality:List.of("poor","well","expert","perfect","master","")){
            // Pickaxes on dirt/logs have ITEM base speed 1, regardless of forging quality.
            near(1,MiningRules.speed(quality,1));near(0.2f,MiningRules.speed(quality,1)*0.2f);scenarios++;
        }
        near(6*0.55f,MiningRules.speed("poor",6));near(6,MiningRules.speed("well",6));near(6*1.55f,MiningRules.speed("expert",6));
        near(6,MiningRules.speed("",6));near(6,MiningRules.speed("unknown",6));
        var gate=new ToolNoticeGate();UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        assert gate.allow(a,0);assert !gate.allow(a,1);assert !gate.allow(a,999);assert gate.allow(a,1000);assert gate.allow(b,1000);
        gate.forget(a);assert gate.allow(a,1001);assert gate.allow(a,10);
        // Verify assumptions against the exact installed Forge-patched Player bytecode, not a wiki formula.
        var node=new ClassNode();try(var in=MiningRulesTest.class.getResourceAsStream("/net/minecraft/world/entity/player/Player.class")){new ClassReader(in).accept(node,0);}
        var method=node.methods.stream().filter(m->m.name.equals("getDigSpeed")).findFirst().orElseThrow();
        boolean nativeEfficiency=false,bonus=false,forgeEvent=false;
        for(var instruction:method.instructions){
            if(instruction instanceof MethodInsnNode call){
                if(call.owner.equals("net/minecraft/world/item/enchantment/EnchantmentHelper") && call.name.equals("m_44926_"))nativeEfficiency=true;
                if(call.owner.equals("net/minecraftforge/event/ForgeEventFactory") && call.name.equals("getBreakSpeed"))forgeEvent=true;
            }
            if(instruction.getOpcode()==Opcodes.IMUL && instruction.getNext().getOpcode()==Opcodes.ICONST_1 && instruction.getNext().getNext().getOpcode()==Opcodes.IADD)bonus=true;
        }
        assert nativeEfficiency && bonus && forgeEvent;
        System.out.println("PASS "+scenarios+" Efficiency III/V, mining-tick and wrong-tool comparisons; bit-exact vanilla speeds, notice throttle and native Forge formula");
    }
}
