package guardtest;

import java.util.List;
import org.spongepowered.asm.mixin.MixinEnvironment;

public final class TacticalClientMixinTest {
    public static void main(String[] args) throws Exception {
        TacticalServerMixinTest.verify(MixinEnvironment.Side.CLIENT, List.of(
            "com.talhanation.recruits.client.gui.CommandScreen", "net.minecraft.client.model.HumanoidModel"));
    }
}
