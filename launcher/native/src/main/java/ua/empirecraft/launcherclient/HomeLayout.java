package ua.empirecraft.launcherclient;

/** GUI coordinates, independent of pixels and Minecraft's GUI scale. */
public record HomeLayout(int width, int height, int margin, int panelWidth,
                         int buttonX, int buttonWidth, int firstButtonY, int buttonHeight,
                         int stageLeft, int stageRight, int characterX, int feetY, int scale,
                         int appearanceY, int appearanceWidth) {
    public static HomeLayout of(int width, int height) {
        int margin = Math.max(8, Math.min(28, width / 40));
        int panel = Math.max(136, Math.min(248, width * 35 / 100));
        int stageLeft = margin + panel + 10, stageRight = width - margin;
        int stageWidth = stageRight - stageLeft;
        int feet = height - 69;
        int scale = Math.max(24, Math.min(360, Math.min((feet - 32) * 10 / 22, (stageWidth - 12) * 10 / 13)));
        int buttonHeight = height < 300 ? 22 : 27;
        int first = Math.max(87, height * 45 / 100);
        first = Math.min(first, height - margin - (buttonHeight + 6) * 3 - 14);
        return new HomeLayout(width, height, margin, panel, margin + 14, panel - 28,
            first, buttonHeight, stageLeft, stageRight, (stageLeft + stageRight) / 2,
            feet, scale, height - 35, Math.min(98, (stageWidth - 6) / 2));
    }
    public record Preview(int x, int feet, float scale) { }
    public static Preview editorPreview(int width, int height, boolean cape, int x, int feet, float scale) {
        // The skin editor has model/rotation controls above its bottom action rows.
        int bottom = height - (cape ? 43 : 122);
        int fittedFeet = Math.max(cape ? 76 : 100, Math.min(feet, bottom));
        float fittedScale = Math.max(20, Math.min(scale,
            Math.min((fittedFeet - (cape ? 28 : 52)) / 2.2f, (width / 4f - 16) / 1.3f)));
        int half = (int) Math.ceil(fittedScale * .65f);
        int fittedX = Math.max(width * 3 / 4 + half, Math.min(x, width - 8 - half));
        return new Preview(fittedX, fittedFeet, fittedScale);
    }
    public int buttonY(int index) { return firstButtonY + index * (buttonHeight + 6); }
    public boolean onCharacter(double x, double y) {
        return x >= stageLeft && x <= stageRight && y >= feetY - scale * 2.2 && y <= feetY + 3;
    }
}
