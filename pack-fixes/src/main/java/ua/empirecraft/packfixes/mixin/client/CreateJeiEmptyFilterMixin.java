package ua.empirecraft.packfixes.mixin.client;

import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import ua.empirecraft.packfixes.JeiInvocations;

@Mixin(targets="ua.empirecraft.create.MedievalPolicy",remap=false)
public abstract class CreateJeiEmptyFilterMixin {
    @Redirect(method="hideJei",at=@At(value="INVOKE",target="Ljava/lang/reflect/Method;invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object empirecraft$emptyJeiRemoval(Method method,Object receiver,Object[] arguments)throws ReflectiveOperationException {
        return JeiInvocations.invoke(method,receiver,arguments);
    }
}
