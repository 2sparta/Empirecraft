package ua.empirecraft.townymap.client;

/** Pixel geometry shared by rendering and input, including small GUI scales. */
public record StrategyLayout(int width,int height,int panelWidth,int panelTop,int panelBottom) {
    public static StrategyLayout of(int width,int height) {
        return new StrategyLayout(width,height,Math.max(150,Math.min(280,width*2/5)),34,Math.max(70,height-28));
    }
    public boolean panel(double x,double y){return x>=6&&x<panelWidth+6&&y>=panelTop&&y<panelBottom;}
    public boolean toolbar(double x,double y){return x>=6&&x<Math.min(width-6,310)&&y>=6&&y<28;}
    public int rows(int start,int spacing){return Math.max(1,(panelBottom-start-8)/spacing);}
}
