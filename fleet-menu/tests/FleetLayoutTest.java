import ua.empirecraft.townymap.client.FleetLayout;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Fit/hit-box invariants over real logical Minecraft window sizes, not widget substitutes. */
public final class FleetLayoutTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        var examples=new JsonArray();
        for(int width:new int[]{320,360,426,480,640,854,953,1280,1920})for(int height:new int[]{240,270,300,360,480,500,720,1080}){
            var layout=FleetLayout.of(width,height);var screen=new FleetLayout.Rect(0,0,width,height);var f=layout.frame();var a=layout.content();
            check(screen.encloses(f),"Centered frame remains on screen");check(f.encloses(a)&&a.w()>=150&&a.h()>=64,"Content has usable width and one complete card");
            check(f.x()==(width-f.w())/2&&f.y()==(height-f.h())/2,"Window is centered at any scale");
            for(int i=0;i<6;i++){check(layout.sidebar().encloses(layout.nav(i)),"Every tab fits sidebar");check(layout.nav(i).bottom()<layout.close().y(),"Tabs do not overlap Close");}
            check(f.encloses(layout.close()),"Close remains inside frame");
            for(int i=0;i<layout.fieldRows();i++){
                var field=layout.field(i);check(a.encloses(new FleetLayout.Rect(field.x()-2,field.y()-12,field.w()+4,field.h()+14)),"Label and edit box fit form card");
                check(field.w()-86>=40,"Ship/person selector retains a usable input");
            }
            check(layout.field(layout.fieldRows()-1).bottom()<a.bottom()-20,"Field pagination has its own row");
            check(a.bottom()<layout.footerY()-19,"Status does not overlap scrollable content");
            for(boolean hero:new boolean[]{false,true})check(layout.rowHeight(hero)>=64&&layout.rowHeight(hero)<=a.h(),"Complete row and actions fit smallest GUI");
            for(int n:new int[]{0,1,2,10})for(boolean toolbar:new boolean[]{false,true}){
                var registrations=new ArrayList<Boolean>();for(int i=0;i<n;i++)registrations.add(i==0);
                Set<Integer> reachable=new HashSet<>();
                for(int first=0;first<=layout.maxRowScroll(n);first++){
                    var placed=layout.rows(registrations,first,toolbar);check(n==0||!placed.isEmpty(),"At least one record remains visible beside actions at every GUI size");
                    for(var row:placed){reachable.add(row.index());var r=row.bounds();check(a.encloses(r),"Rendered and clickable row is fully inside clipping region");check(r.encloses(new FleetLayout.Rect(r.x()+10,r.bottom()-28,100,20)),"Row action remains inside its card");}
                }check(reachable.size()==n,"Scrolling reaches every record including the final one");
            }
            check(!a.contains(a.right(),a.y())&&!a.contains(a.x(),a.bottom()),"Right and bottom clipping edges cannot consume clicks");
            var row=new JsonObject();row.addProperty("screenWidth",width);row.addProperty("screenHeight",height);row.add("frame",new Gson().toJsonTree(f));row.add("content",new Gson().toJsonTree(a));row.addProperty("fieldRows",layout.fieldRows());examples.add(row);
        }
        var result=new JsonObject();result.addProperty("status","passed");result.addProperty("checks",checks);result.addProperty("sizes",examples.size());result.add("layouts",examples);Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        System.out.println("FLEET_LAYOUT_PASS sizes="+examples.size()+" checks="+checks);
    }
}
