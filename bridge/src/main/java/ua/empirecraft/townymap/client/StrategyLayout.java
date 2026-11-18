package ua.empirecraft.townymap.client;

/** Shared drawing and hit bounds at each GUI scale. */
public record StrategyLayout(int width,int height,int panelWidth,int panelTop,int panelBottom) {
    public static final int LEFT = 6, TOP = 6, ROW = 20, MENU_TOP = 30;
    private static final int GAP = 4;

    public static StrategyLayout of(int width,int height) {
        return of(width,height,0);
    }

    public static StrategyLayout of(int width,int height,int contentHeight) {
        int panelWidth = Math.max(1, Math.min(width-2*LEFT, Math.max(188, Math.min(278, width*2/5))));
        int top = 34;
        int bottom = Math.max(top, height-14);
        if (contentHeight > 0) bottom = Math.min(bottom, top+contentHeight);
        return new StrategyLayout(width,height,panelWidth,top,bottom);
    }

    public int toolWidth(int index) {
        int available = Math.max(4, Math.min(width-2*LEFT, 310)-3*GAP);
        int other = Math.max(1, available/6);
        return index==0 ? available-3*other : other;
    }

    public int toolLeft(int index) {
        return index==0 ? LEFT : LEFT+toolWidth(0)+GAP+(index-1)*(toolWidth(1)+GAP);
    }

    public int menuLeft(boolean layers) {
        return layers ? Math.max(LEFT, Math.min(toolLeft(1), width-LEFT-menuWidth())) : LEFT;
    }

    public int menuWidth() {
        return Math.max(1, Math.min(174, width-2*LEFT));
    }

    public int menuRows(int count) {
        return Math.max(0, Math.min(count, (height-MENU_TOP-LEFT)/ROW));
    }

    public boolean menu(boolean layers,int count,double x,double y) {
        int left=menuLeft(layers);
        return x>=left&&x<left+menuWidth()&&y>=MENU_TOP&&y<MENU_TOP+menuRows(count)*ROW;
    }

    public boolean panel(double x,double y){return x>=LEFT&&x<panelWidth+LEFT&&y>=panelTop&&y<panelBottom;}
    public boolean toolbar(double x,double y){return x>=LEFT&&x<toolLeft(3)+toolWidth(3)&&y>=TOP&&y<TOP+18;}
    public int rows(int start,int spacing){return Math.max(1,(panelBottom-start-8)/spacing);}
}
