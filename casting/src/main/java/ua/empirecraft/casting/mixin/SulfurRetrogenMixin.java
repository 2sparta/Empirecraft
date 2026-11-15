package ua.empirecraft.casting.mixin;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import ua.empirecraft.casting.CrucibleMaterials;
@Pseudo @Mixin(targets="ua.empirecraft.ores.OreRetrogen",remap=false)
public abstract class SulfurRetrogenMixin {
 @ModifyVariable(method="requireBlock",at=@At("HEAD"),argsOnly=true)private static String sulfur(String id){return CrucibleMaterials.sulfur(id);}
}
