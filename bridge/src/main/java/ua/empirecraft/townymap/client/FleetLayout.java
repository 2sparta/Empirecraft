package ua.empirecraft.townymap.client;
import java.util.*;

/** Logical GUI pixels, shared by rendering and hit boxes at every Minecraft GUI scale. */
public record FleetLayout(Rect frame, Rect sidebar, Rect content, int headerHeight, int footerY, int navStep) {
    public record Rect(int x,int y,int w,int h) {
        public int right(){return x+w;} public int bottom(){return y+h;}
        public boolean contains(double px,double py){return px>=x&&px<right()&&py>=y&&py<bottom();}
        public boolean encloses(Rect other){return other.x>=x&&other.y>=y&&other.right()<=right()&&other.bottom()<=bottom();}
    }
    public static FleetLayout of(int screenWidth,int screenHeight) {
        int w=Math.min(760,screenWidth-16),h=Math.min(440,screenHeight-16);
        int x=(screenWidth-w)/2,y=(screenHeight-h)/2,header=h<300?36:52,offset=h<300?34:42;
        int nav=w<410?98:w<540?128:154;
        Rect frame=new Rect(x,y,w,h),sidebar=new Rect(x+1,y+header,nav,h-header-1);
        Rect content=new Rect(x+nav+14,y+header+offset,w-nav-30,h-header-offset-62);
        return new FleetLayout(frame,sidebar,content,header,y+h-30,h<300?22:30);
    }
    public Rect nav(int index){return new Rect(sidebar.x()+7,sidebar.y()+8+index*navStep,sidebar.w()-14,navStep-4);}
    public Rect close(){return new Rect(sidebar.x()+7,footerY,sidebar.w()-14,20);}
    public int fieldRows(){return Math.max(1,(content.h()-25)/40);}
    public Rect field(int row){return new Rect(content.x()+8,content.y()+15+row*40,content.w()-16,19);}
    public int rowHeight(boolean registration){return Math.min(content.h(),registration?108:82);}
    public int formsPerView(){return Math.max(1,content.h()/36);}
    public int proseLines(){return Math.max(1,(content.h()-12)/12);}
    public int maxRowScroll(int size){return Math.max(0,size-1);}
    public record Placement(int index,Rect bounds,boolean registration){}
    public List<Placement> rows(List<Boolean> registration,int first,boolean toolbar){
        List<Placement> result=new ArrayList<>();int y=content.y()+(toolbar?28:0);
        for(int i=first;i<registration.size();i++){
            boolean hero=registration.get(i);int height=Math.min(rowHeight(hero),content.bottom()-y);if(height<64)break;
            result.add(new Placement(i,new Rect(content.x(),y,content.w(),height),hero));y+=height+8;
        }return List.copyOf(result);
    }
}
