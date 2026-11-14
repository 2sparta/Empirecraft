package ua.empirecraft.townymap.client;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ua.empirecraft.townymap.FleetNetwork;
import static ua.empirecraft.townymap.client.FleetStyle.*;

/** Native fleet UI; permissions, prices and confirmation tokens remain server-owned. */
public final class FleetScreen extends Screen {
    private JsonObject data,schema;private final String session,prefix=UUID.randomUUID().toString();private String section,request="",detail;
    private final List<EditBox> fields=new ArrayList<>();private final List<FleetStyle.Action> requests=new ArrayList<>();
    private final List<Row> rows=new ArrayList<>();private FleetLayout layout;private int ticks,revision,scroll,fieldPage,page=1;private boolean busy,choosing;
    private record Row(JsonObject data,FleetLayout.Rect bounds,boolean registration){}
    private static final String[] SECTIONS={"fleet","market","auctions","manage","sightings"};
    private static final String[] LABELS={"Флот міста","Кораблі базару","Аукціони","Керування","Спостереження"};
    FleetScreen(JsonObject data){super(Component.literal(ClientText.tr("Міський флот",text(data,"locale","en"))));this.data=data;session=text(data,"session","");section=text(data,"section","fleet");choosing=section.equals("manage");}
    static String text(JsonObject o,String key,String fallback){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}
    String tr(String source){return ClientText.tr(source,text(data,"locale","en"));}
    private JsonArray array(String key){return data.has(key)&&data.get(key).isJsonArray()?data.getAsJsonArray(key):new JsonArray();}
    private int pages(){return Math.max(1,data.has("pages")?data.get("pages").getAsInt():1);}
    private boolean picker(){return section.equals("picker");}
    private boolean waiting(){return busy&&!section.equals(text(data,"section",section));}
    private int left(){return layout.content().x()-10;}
    private int visibleFields(){return layout.fieldRows();}
    private void button(String text,int x,int y,int width,Runnable run){action(text,x,y,width,20,false,false,-1,run);}
    private FleetStyle.Action action(String label,int x,int y,int w,int h,boolean primary,boolean network,int icon,Runnable run){
        var button=new FleetStyle.Action(Component.literal(tr(label)),x,y,Math.max(18,w),h,primary,false,false,icon,run);addRenderableWidget(button);
        if(network){requests.add(button);button.active=!busy;}return button;
    }
    private void footer(String label,boolean primary,boolean network,boolean right,Runnable run){int w=Math.min(primary?180:100,(layout.content().w()-6)/2);action(label,right?layout.content().right()-w:layout.content().x(),layout.footerY(),w,20,primary,network,-1,run);}
    private void choose(JsonObject form){schema=form;fields.clear();fieldPage=0;choosing=false;scroll=0;init();}
    private void switchTo(String target){if(busy)return;section=target;schema=null;fields.clear();detail=null;choosing=target.equals("manage");scroll=0;page=1;send("view","",List.of(),"",1);init();}
    public static void receive(FleetNetwork.Reply reply){
        JsonObject next=JsonParser.parseString(reply.json()).getAsJsonObject();if(next.has("marks")){FleetMapPlugin.marks(next.getAsJsonArray("marks"));return;}
        var mc=Minecraft.getInstance();if(reply.open()){mc.setScreen(new FleetScreen(next));return;}
        if(mc.screen instanceof FleetScreen screen&&screen.request.equals(reply.screen())){
            screen.busy=false;
            if(next.has("error")){screen.data.addProperty("error",next.get("error").getAsString());screen.section=text(screen.data,"section",screen.section);screen.init();return;}
            screen.data=next;screen.section=text(next,"section",screen.section);
            if(next.has("look")){FleetMapPlugin.look(next.getAsJsonObject("look"));screen.data.addProperty("notice",screen.tr("Мітку корабля додано до JourneyMap."));}
            if(next.has("confirmation")){screen.schema=null;screen.choosing=false;}screen.init();
        }
    }
    private void send(String action,String form,List<String> values,String token,int page){
        values=FleetUiHooks.values(this,form,values);if(FleetUiHooks.send(this,action,form,values,token,page))return;
        if(busy)return;busy=true;ticks=0;requests.forEach(b->b.active=false);request=prefix+":"+(++revision);
        FleetNetwork.CHANNEL.sendToServer(new FleetNetwork.Request(session,action,section,form,values,token,page,request));
    }
    @Override protected void init(){try{
        List<String> old=fields.stream().map(EditBox::getValue).toList();clearWidgets();fields.clear();requests.clear();rows.clear();layout=FleetLayout.of(width,height);
        page=Math.max(1,Math.min(pages(),data.has("page")?data.get("page").getAsInt():1));var area=layout.content();
        for(int i=0;i<SECTIONS.length;i++){
            String target=SECTIONS[i];var r=layout.nav(i);boolean selected=section.equals(target)||(target.equals("manage")&&section.equals("flags"));var tab=new FleetStyle.Action(Component.literal(tr(LABELS[i])),r.x(),r.y(),r.w(),r.h(),false,selected,true,i,()->switchTo(target));
            tab.active=!busy&&!picker();requests.add(tab);addRenderableWidget(tab);
        }
        if(!picker()){
            var r=layout.nav(5);var create=new FleetStyle.Action(Component.literal(tr("Новий корабель")),r.x(),r.y(),r.w(),r.h(),false,section.equals("ship-request")||section.equals("ship-assign"),true,5,()->{if(!busy){schema=null;fields.clear();detail=null;choosing=false;scroll=0;send("new-ship-request","",List.of(),"",1);}});
            create.active=!busy;requests.add(create);addRenderableWidget(create);
        }
        var close=layout.close();button(picker()?"Повернутися":"Закрити",close.x(),close.y(),close.w(),this::onClose);
        if(waiting())return;
        if(detail!=null){footer("До оголошень",false,false,false,()->{detail=null;scroll=0;init();});return;}
        if(data.has("confirmation")){
            var confirm=data.getAsJsonObject("confirmation");footer("Підтвердити",true,true,false,()->send("confirm","",List.of(),text(confirm,"token",""),page));
            footer("Повернутися",false,true,true,()->{schema=null;choosing=false;scroll=0;send("discard","",List.of(),"",page);});return;
        }
        if(schema!=null){
            var labels=schema.getAsJsonArray("fields");fieldPage=Math.max(0,Math.min(fieldPage,Math.max(0,(labels.size()-1)/visibleFields())));
            for(int i=0;i<labels.size();i++){
                var r=layout.field(i%visibleFields());var field=new EditBox(font,r.x(),r.y(),r.w(),r.h(),Component.literal(labels.get(i).getAsString()));field.setMaxLength(2000);
                if(i<old.size())field.setValue(old.get(i));else if(i==0&&schema.has("prefill"))field.setValue(schema.get("prefill").getAsString());else if(schema.has("defaults")&&i<schema.getAsJsonArray("defaults").size())field.setValue(schema.getAsJsonArray("defaults").get(i).getAsString());
                field.setTextColor(INK);field.setTextColorUneditable(MUTED);field.setBordered(false);field.visible=i/visibleFields()==fieldPage;field.active=field.visible;fields.add(field);addRenderableWidget(field);
            }
            footer("Переглянути умови",true,true,false,()->send("prepare",text(schema,"id",""),fields.stream().map(EditBox::getValue).toList(),"",page));
            footer("Назад",false,false,true,()->{schema=null;fields.clear();choosing=true;init();});
            if(labels.size()>visibleFields()){
                action("‹",area.x()+8,area.bottom()-20,24,18,false,false,-1,()->{fieldPage=Math.max(0,fieldPage-1);init();}).active=fieldPage>0;
                action("›",area.right()-32,area.bottom()-20,24,18,false,false,-1,()->{fieldPage=Math.min((labels.size()-1)/visibleFields(),fieldPage+1);init();}).active=fieldPage<(labels.size()-1)/visibleFields();
            }return;
        }
        if(choosing){
            var forms=array("forms");scroll=Math.max(0,Math.min(scroll,Math.max(0,forms.size()-layout.formsPerView())));
            for(int i=scroll;i<Math.min(forms.size(),scroll+layout.formsPerView());i++){var form=forms.get(i).getAsJsonObject();action(text(form,"title",""),area.x(),area.y()+(i-scroll)*36,area.w(),30,false,false,-1,()->choose(form));}
            footer("До списку",false,false,false,()->{choosing=false;scroll=0;init();});return;
        }
        boolean toolbar=!array("forms").isEmpty();if(toolbar)action("Дії та оголошення",area.x(),area.y(),area.w(),22,true,false,3,()->{choosing=true;scroll=0;init();});
        var source=array("rows");scroll=Math.max(0,Math.min(scroll,layout.maxRowScroll(source.size())));
        List<Boolean> registrationRows=new ArrayList<>();for(var row:source)registrationRows.add(text(row.getAsJsonObject(),"id","").equals("ship-request")&&section.equals("fleet"));
        for(var placement:layout.rows(registrationRows,scroll,toolbar)){
            var row=source.get(placement.index()).getAsJsonObject();boolean registration=placement.registration();var r=placement.bounds();rows.add(new Row(row,r,registration));
            var buttons=row.has("buttons")?row.getAsJsonArray("buttons"):new JsonArray();int gap=6,bw=Math.min(165,(r.w()-20-Math.max(0,buttons.size()-1)*gap)/Math.max(1,buttons.size()));
            for(int n=0;n<buttons.size();n++){
                var b=buttons.get(n).getAsJsonObject();String command=text(b,"action",""),form=text(b,"form",""),value=text(b,"value","");
                boolean primary=registration||(section.equals("fleet")?command.equals("manage-ship")||buttons.size()==1:n==buttons.size()-1);
                action(text(b,"label",""),r.x()+10+n*(bw+gap),r.bottom()-28,bw,20,primary,true,-1,()->{
                    if(command.equals("form")){for(var f:array("forms"))if(text(f.getAsJsonObject(),"id","").equals(form)){var draft=f.getAsJsonObject().deepCopy();draft.addProperty("prefill",value);choose(draft);break;}}
                    else send(command,form,List.of(value),"",page);
                });
            }
        }
        if(pages()>1){action("‹",area.x(),layout.footerY(),28,20,false,true,-1,()->{scroll=0;send("view","",List.of(),"",page-1);}).active=!busy&&page>1;action("›",area.right()-28,layout.footerY(),28,20,false,true,-1,()->{scroll=0;send("view","",List.of(),"",page+1);}).active=!busy&&page<pages();}
    }finally{FleetUiHooks.init(this);}}
    @Override public void tick(){
        if(++ticks>=30&&!busy&&schema==null&&detail==null&&!choosing&&!picker()&&!data.has("confirmation")){ticks=0;send("view","",List.of(),"",page);}
        if(busy&&ticks>160){busy=false;data.addProperty("error",tr("Відповідь затримується. Спробуйте ще раз."));init();}
    }
    private String prose(){return detail!=null?detail:data.has("confirmation")?text(data.getAsJsonObject("confirmation"),"text",""):null;}
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(!layout.content().contains(x,y)||delta==0)return super.mouseScrolled(x,y,delta);
        int direction=delta>0?-1:1;
        if(schema!=null){fieldPage=Math.max(0,Math.min((fields.size()-1)/visibleFields(),fieldPage+direction));init();return true;}
        String prose=prose();int max=prose!=null?Math.max(0,font.split(Component.literal(prose),layout.content().w()-20).size()-layout.proseLines()):choosing?Math.max(0,array("forms").size()-layout.formsPerView()):layout.maxRowScroll(array("rows").size());
        scroll=Math.max(0,Math.min(max,scroll+direction));init();return true;
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(super.mouseClicked(x,y,button))return true;
        if(button==0&&!busy&&detail==null&&schema==null&&!choosing&&!data.has("confirmation"))for(var row:rows){
            var r=row.bounds();if(!row.registration()&&x>=r.x()&&x<r.right()&&y>=r.y()&&y<r.bottom()-30){var data=row.data();detail=text(data,"title","")+"\n\n"+text(data,"detail","")+"\n\nID: "+text(data,"id","");scroll=0;init();return true;}
        }return false;
    }
    @Override public boolean charTyped(char character,int modifiers){Boolean handled=FleetUiHooks.typed(this,character,modifiers);return handled==null?super.charTyped(character,modifiers):handled;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){Boolean handled=FleetUiHooks.key(this,key,scan,modifiers);return handled==null?super.keyPressed(key,scan,modifiers):handled;}
    @Override public void onClose(){if(!FleetUiHooks.close(this))super.onClose();}
    @Override public boolean isPauseScreen(){return false;}
    private void line(GuiGraphics g,String value,int x,int y,int w,int color){String shown=font.width(value)>w?font.plainSubstrByWidth(value,Math.max(0,w-font.width("…")))+"…":value;g.drawString(font,shown,x,y,color,false);}
    private String heading(){
        if(detail!=null)return tr("Відомості про корабель");if(data.has("confirmation"))return tr("Перевірка умов");if(schema!=null)return text(schema,"title","");if(choosing)return tr("Керування та дії");
        for(int i=0;i<SECTIONS.length;i++)if(section.equals(SECTIONS[i]))return tr(LABELS[i]);return text(data,"title",picker()?text(data,"city",tr("Вибір корабля")):tr("Міський флот"));
    }
    private void prose(GuiGraphics g,String prose){var a=layout.content();card(g,a,false);var lines=font.split(Component.literal(prose),a.w()-20);scroll=Math.min(scroll,Math.max(0,lines.size()-layout.proseLines()));for(int i=scroll;i<Math.min(lines.size(),scroll+layout.proseLines());i++)g.drawString(font,lines.get(i),a.x()+10,a.y()+7+(i-scroll)*12,INK,false);scrollbar(g,lines.size(),layout.proseLines());}
    private void scrollbar(GuiGraphics g,int total,int visible){if(total<=visible)return;var a=layout.content();int track=a.h()-8,thumb=Math.max(12,track*visible/total),offset=(track-thumb)*scroll/Math.max(1,total-visible);g.fill(a.right()+5,a.y()+4,a.right()+7,a.bottom()-4,LINE);g.fill(a.right()+5,a.y()+4+offset,a.right()+7,a.y()+4+offset+thumb,0xff8e947f);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        var f=layout.frame();var a=layout.content();g.fill(0,0,width,height,0xb50c141c);g.fill(f.x()+3,f.y()+5,f.right()+3,f.bottom()+5,0x88071016);
        g.fill(f.x(),f.y(),f.right(),f.bottom(),PAPER);g.fill(f.x(),f.y(),f.right(),f.y()+layout.headerHeight(),HEADER);g.fill(layout.sidebar().x(),layout.sidebar().y(),layout.sidebar().right(),layout.sidebar().bottom(),NAVY);border(g,f.x(),f.y(),f.w(),f.h(),BRASS);g.fill(f.x()+1,f.y()+layout.headerHeight()-1,f.right()-1,f.y()+layout.headerHeight(),BRASS);
        int cityWidth=f.w()>550?f.w()-240:f.w()-54;icon(g,0,f.x()+15,f.y()+13,BRASS);line(g,tr("Міський флот"),f.x()+38,f.y()+10,cityWidth,LIGHT);line(g,text(data,"city",""),f.x()+38,f.y()+24,cityWidth,BRASS);
        if(f.w()>550)line(g,tr("ПОРТОВА КАНЦЕЛЯРІЯ"),f.right()-180,f.y()+20,165,0xffa9b7b4);
        line(g,heading(),a.x(),a.y()-30,a.w(),INK);line(g,schema!=null?tr("Заповніть дані та перевірте умови перед підтвердженням."):data.has("confirmation")?tr("Підтвердіть лише після перевірки всіх умов."):tr("Кораблі, екіпажі та морська торгівля"),a.x(),a.y()-16,a.w(),MUTED);g.fill(a.x(),a.y()-5,a.right(),a.y()-4,LINE);
        g.enableScissor(a.x()-2,a.y()-2,a.right()+9,a.bottom()+3);
        if(waiting())line(g,tr("Оновлення…"),a.x()+12,a.y()+24,a.w()-24,MUTED);
        else if(prose()!=null)prose(g,prose());
        else if(schema!=null){
            card(g,a,false);var labels=schema.getAsJsonArray("fields");
            for(int i=fieldPage*visibleFields();i<Math.min(labels.size(),(fieldPage+1)*visibleFields());i++){var r=layout.field(i%visibleFields());line(g,labels.get(i).getAsString(),r.x(),r.y()-12,r.w(),MUTED);g.fill(r.x()-2,r.y()-2,r.right()+2,r.bottom()+2,0xfffffaf0);border(g,r.x()-2,r.y()-2,r.w()+4,r.h()+4,LINE);}
            if(labels.isEmpty())line(g,tr("Ця дія не потребує додаткових полів."),a.x()+10,a.y()+12,a.w()-20,MUTED);
            if(labels.size()>visibleFields())g.drawCenteredString(font,(fieldPage+1)+" / "+((labels.size()+visibleFields()-1)/visibleFields()),a.x()+a.w()/2,a.bottom()-14,MUTED);
        }else if(choosing)scrollbar(g,array("forms").size(),layout.formsPerView());
        else{
            for(var row:rows){var r=row.bounds();boolean hover=!row.registration()&&r.contains(mx,my)&&my<r.bottom()-30;card(g,r,hover);g.fill(r.x(),r.y(),r.x()+3,r.bottom(),row.registration()?0xff5d7e7b:BRASS);
                boolean engraving=row.registration()&&r.w()>375&&r.h()>=100;int textWidth=r.w()-(engraving?100:20);line(g,text(row.data(),"title",""),r.x()+10,r.y()+10,textWidth,INK);
                var lines=font.split(Component.literal(text(row.data(),"detail","")),textWidth);int count=Math.max(1,(r.h()-49)/12);for(int n=0;n<Math.min(count,lines.size());n++)g.drawString(font,lines.get(n),r.x()+10,r.y()+26+n*12,MUTED,false);
                if(engraving)ship(g,r.right()-83,r.y()+13,0xffc9bea1);
            }
            int remainingY=rows.isEmpty()?a.y():rows.get(rows.size()-1).bounds().bottom()+10;
            boolean onlyRegistration=array("rows").size()==1&&!rows.isEmpty()&&rows.get(0).registration();
            if((array("rows").isEmpty()||onlyRegistration)&&a.bottom()-remainingY>64){int center=a.x()+a.w()/2;ship(g,center-35,remainingY+3,0xffb7b69d);if(a.bottom()-remainingY>95)g.drawCenteredString(font,tr(onlyRegistration?"Ваш флот починається тут":"Поки що записів немає."),center,remainingY+68,MUTED);}
            scrollbar(g,array("rows").size(),Math.max(1,rows.size()));
        }
        g.disableScissor();
        String status=text(data,"error","");int statusColor=0xffa44838;if(status.isEmpty()){status=text(data,"notice","");statusColor=0xff3c7060;}if(status.isEmpty()&&busy){status=tr("Оновлення…");statusColor=MUTED;}
        if(!status.isEmpty()){g.fill(a.x(),layout.footerY()-19,a.right(),layout.footerY()-4,0xffded4bc);line(g,status,a.x()+5,layout.footerY()-15,a.w()-10,statusColor);if(mx>=a.x()&&mx<a.right()&&my>=layout.footerY()-19&&my<layout.footerY()-4)g.renderTooltip(font,font.split(Component.literal(status),Math.min(300,a.w())),mx,my);}
        if(schema==null&&detail==null&&!choosing&&!data.has("confirmation")&&!waiting()){
            if(pages()>1)g.drawCenteredString(font,page+" / "+pages(),a.x()+a.w()/2,layout.footerY()+6,MUTED);
            else if(array("rows").size()>rows.size())line(g,tr("Колесо миші — прокрутка"),a.x(),layout.footerY()+6,a.w(),MUTED);
        }
        super.render(g,mx,my,partial);
        if(mx>=f.x()+38&&mx<f.x()+38+cityWidth&&my>=f.y()+24&&my<f.y()+34&&font.width(text(data,"city",""))>cityWidth)g.renderTooltip(font,Component.literal(text(data,"city","")),mx,my);
    }
}
