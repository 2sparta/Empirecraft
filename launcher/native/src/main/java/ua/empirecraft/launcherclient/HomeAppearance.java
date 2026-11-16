package ua.empirecraft.launcherclient;

import com.quickskin.mod.client.rendering.LocalLookPreview;
import com.quickskin.mod.client.rendering.PreviewPlayerData;
import com.quickskin.mod.client.rendering.PreviewRenderBackend;
import com.quickskin.mod.client.services.CapeService;
import com.quickskin.mod.client.services.LocalAssetManager;
import com.quickskin.mod.common.data.TextureQuality;
import com.quickskin.mod.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Reuses Quick Skin's selected local appearance; no world or fake player is created. */
final class HomeAppearance {
    private final PreviewPlayerData data = new PreviewPlayerData();
    private String lastSkin = null, lastCape = null;
    private boolean warned;
    HomeAppearance() {
        data.setSkinLocation(new ResourceLocation("minecraft", "textures/entity/player/wide/steve.png"));
        data.setModelType("classic");
        data.setCurrentAnimation("idle");
    }
    void invalidate() { lastSkin = null; lastCape = null; }
    void refresh() {
        try {
            String skin = LocalLookPreview.skinHash(false);
            if (!java.util.Objects.equals(lastSkin, skin)) {
                var assets = LocalAssetManager.getInstance();
                var metadata = skin == null || skin.isEmpty() ? null : assets.getMetadata(skin);
                if (skin != null && !skin.isEmpty() && metadata == null) return;
                if (metadata != null) {
                    var texture = assets.getTextureLocation(skin, TextureQuality.FULL);
                    if (texture == null) return; // Retry after asynchronous texture loading.
                    data.setSkinLocation(texture);
                    String model = assets.getSkinModelPreference(skin);
                    data.setModelType("auto".equals(model) ? metadata.skinModel() : model);
                } else {
                    data.setSkinLocation(new ResourceLocation("minecraft", "textures/entity/player/wide/steve.png"));
                    data.setModelType("classic");
                }
                lastSkin = skin;
            }
            String cape = ClientConfig.getInstance().activeCapeHash;
            if (!java.util.Objects.equals(lastCape, cape)) {
                var texture = cape == null || cape.isEmpty() ? null : CapeService.getInstance().getCapeLocation(null, cape);
                if (cape != null && !cape.isEmpty() && texture == null) return;
                data.setCapeLocation(texture);
                data.setCapeId(cape);
                data.markCapeAuthoritative();
                lastCape = cape;
            }
        } catch (RuntimeException e) {
            if (!warned) {
                com.mojang.logging.LogUtils.getLogger().warn("Home appearance unavailable; using default skin", e);
                warned = true;
            }
        }
    }
    void render(GuiGraphics graphics, HomeLayout layout, float rotation) {
        data.setYRotation(rotation);
        // Fixed head pose avoids a character that twists toward the navigation panel.
        data.setHeadYaw(0);
        data.setHeadPitch(0);
        PreviewRenderBackend.INSTANCE.renderPlayerModel(graphics, layout.characterX(), layout.feetY(),
            layout.scale(), rotation, data, layout.characterX(), layout.feetY() - layout.scale(), false);
    }
}
