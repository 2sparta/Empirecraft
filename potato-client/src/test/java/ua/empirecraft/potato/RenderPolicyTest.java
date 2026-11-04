package ua.empirecraft.potato;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RenderPolicyTest {
    @Test void onlySixCosmeticOptionsAreOwned() {
        assertEquals(Set.of("options.ao","options.renderClouds","options.entityShadows","options.mipmapLevels","options.biomeBlendRadius","options.graphics"),RenderPolicy.KEYS);
    }
    @Test void dangerousAndUnrelatedOptionsRemainTheSameObject() {
        for(String key:List.of("options.renderDistance","options.entityDistanceScaling","options.simulationDistance","options.particles",
                "options.gamma","options.fov","options.fovEffectScale","options.distortionEffectScale","options.damageTiltStrength",
                "options.sensitivity","options.invertMouse","options.guiScale","options.vsync","options.framerateLimit",
                "options.soundCategory.master","options.soundCategory.hostile","options.chat.visibility","options.attackIndicator",
                "options.showSubtitles","options.autoJump","options.useNativeTransport","options.screenEffectScale","mod.new-option")) {
            Object value=new Object();assertSame(value,RenderPolicy.value(key,value,true),key);
        }
    }
    @Test void disablingRestoresEveryValueWithoutASecondaryBackup() {
        for(String key:RenderPolicy.KEYS) { Object original=new Object();assertSame(original,RenderPolicy.value(key,original,false)); }
    }
    @Test void fancyAndFastLeavesKeepTheirOriginalVisibility() {
        assertSame(GraphicsStatus.FAST,RenderPolicy.value("options.graphics",GraphicsStatus.FAST,true));
        assertSame(GraphicsStatus.FANCY,RenderPolicy.value("options.graphics",GraphicsStatus.FANCY,true));
        assertSame(GraphicsStatus.FANCY,RenderPolicy.value("options.graphics",GraphicsStatus.FABULOUS,true));
    }
    @Test void reducesOnlyDecorationAndTextureOverhead() {
        assertEquals(false,RenderPolicy.value("options.ao",true,true));
        assertEquals(false,RenderPolicy.value("options.entityShadows",true,true));
        assertEquals(CloudStatus.OFF,RenderPolicy.value("options.renderClouds",CloudStatus.FANCY,true));
        assertEquals(0,RenderPolicy.value("options.mipmapLevels",4,true));
        assertEquals(0,RenderPolicy.value("options.biomeBlendRadius",7,true));
    }
    @Test void missingOrThirdPartyCaptionsAreNotAltered() {
        Object value=new Object();assertSame(value,RenderPolicy.value(null,value,true));assertSame(value,RenderPolicy.value("",value,true));
        for(String key:RenderPolicy.KEYS) assertSame(value,RenderPolicy.value(key,value,true),"Foreign option type: "+key);
    }
}
