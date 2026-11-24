package ua.empirecraft.feudal;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;

final class ForgeContactMail {
    private ForgeContactMail() {}

    static void attach(MarketMail mail) throws SQLException {
        if (!ModList.get().isLoaded("contact")) return;
        Method handle;
        try {
            handle = Class.forName("ua.empirecraft.townymap.contact.ContactMail")
                    .getMethod("handleNative", Map.class);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            LogUtils.getLogger().warn("Feudal Contact mail bridge unavailable", unavailable);
            return;
        }
        mail.paperSpec = mail.m.registerSpec(ForgeFeudalItems.prototype(paper()), false);
        mail.gateway = request -> dispatch(handle, request);
    }

    static ItemStack paper() {
        return new ItemStack(Items.f_42516_);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dispatch(Method handle, Map<String, Object> request) {
        try {
            if (!"template".equals(request.get("action"))) return (Map<String, Object>) handle.invoke(null, request);

            var response = (Map<String, Object>) handle.invoke(null, request);
            if (!(response.get("item") instanceof ItemStack stack) || stack.m_41619_()) {
                throw new IllegalStateException("Contact postcard unavailable");
            }
            return Map.of("item", ForgeFeudalItems.prototype(stack), "template", response.get("template"));
        } catch (InvocationTargetException wrapped) {
            if (wrapped.getCause() instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalStateException("Пошта Contact тимчасово недоступна.", wrapped.getCause());
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Пошта Contact тимчасово недоступна.", failure);
        }
    }
}
