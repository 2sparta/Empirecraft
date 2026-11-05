package ua.empirecraft.townymap.client;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ua.empirecraft.townymap.FleetNetwork;

/** Role-checked city fleet forms; transactions always require the server's exact confirmation token. */
public final class FleetScreen extends Screen {
    private JsonObject data,schema;private final String session,prefix=UUID.randomUUID().toString();private String section,request="",detail;
    private final List<EditBox> fields=new ArrayList<>();private int ticks,revision,scroll,fieldPage,page=1;private boolean busy,choosing;
    private static final String[] SECTIONS={"fleet","market","auctions","manage","sightings"};
    private static final String[] LABELS={"Флот міста","Кораблі базару","Аукціони","Керування","Спостереження"};
    FleetScreen(JsonObject data){super(Component.literal("Міський флот"));this.data=data;session=text(data,"session","");section=text(data,"section","fleet");choosing=section.equals("manage");}
    static String text(JsonObject o,String key,String fallback){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}
    private JsonArray array(String key){return data.has(key)&&data.get(key).isJsonArray()?data.getAsJsonArray(key):new JsonArray();}
    public static void receive(FleetNetwork.Reply reply){
        JsonObject next=JsonParser.parseString(reply.json()).getAsJsonObject();if(next.has("marks")){FleetMapPlugin.marks(next.getAsJsonArray("marks"));return;}
        var mc=Minecraft.getInstance();if(reply.open()){mc.setScreen(new FleetScreen(next));return;}
        if(mc.screen instanceof FleetScreen screen && screen.request.equals(reply.screen())){
            screen.busy=false;
            if(next.has("error")){screen.data.addProperty("error",next.get("error").getAsString());return;}
            screen.data=next;screen.section=text(next,"section",screen.section);if(next.has("look")){FleetMapPlugin.look(next.getAsJsonObject("look"));screen.data.addProperty("notice","Мітку корабля додано до JourneyMap.");}
            if(next.has("confirmation"))screen.schema=null;screen.init();
        }
    }
    private void send(String action,String form,List<String> values,String token,int page){
        if(busy)return;busy=true;ticks=0;request=prefix+":"+(++revision);
        FleetNetwork.CHANNEL.sendToServer(new FleetNetwork.Request(session,action,section,form,values,token,page,request));
    }
    private int left(){return Math.min(150,Math.max(110,width/4));}
    private void button(String text,int x,int y,int width,Runnable run){addRenderableWidget(Button.builder(Component.literal(text),b->run.run()).bounds(x,y,Math.max(20,width),20).build());}
    private int visibleFields(){return Math.max(1,(height-145)/39);}
    @Override protected void init(){
        List<String> old=fields.stream().map(EditBox::getValue).toList();clearWidgets();fields.clear();int left=left(),w=width-left-18;
        for(int i=0;i<SECTIONS.length;i++){String target=SECTIONS[i];button(LABELS[i],10,35+i*26,left-20,()->{section=target;schema=null;detail=null;choosing=target.equals("manage");scroll=0;page=1;send("view","",List.of(),"",1);});}
        button("Закрити",10,height-30,left-20,this::onClose);
        if(detail!=null){button("До оголошень",left+10,height-40,150,()->{detail=null;scroll=0;init();});return;}
        if(data.has("confirmation")){
            var confirm=data.getAsJsonObject("confirmation");button("Підтвердити",left+10,height-55,Math.min(140,w/2),()->send("confirm","",List.of(),text(confirm,"token",""),page));
            button("Повернутися",left+20+Math.min(140,w/2),height-55,Math.min(140,w/2),()->{schema=null;choosing=false;scroll=0;send("discard","",List.of(),"",page);});return;
        }
        if(schema!=null){
            var labels=schema.getAsJsonArray("fields");for(int i=0;i<labels.size();i++){
                var field=new EditBox(font,left+10,77+(i%visibleFields())*39,w-15,19,Component.literal(labels.get(i).getAsString()));field.setMaxLength(2000);
                if(i<old.size())field.setValue(old.get(i));else if(i==0 && schema.has("prefill"))field.setValue(schema.get("prefill").getAsString());else if(schema.has("defaults")&&i<schema.getAsJsonArray("defaults").size())field.setValue(schema.getAsJsonArray("defaults").get(i).getAsString());
                field.visible=i/visibleFields()==fieldPage;field.active=field.visible;fields.add(field);addRenderableWidget(field);
            }
            button("Переглянути умови",left+10,height-54,Math.min(170,w-10),()->send("prepare",text(schema,"id",""),fields.stream().map(EditBox::getValue).toList(),"",page));
            button("Назад",left+10,height-29,80,()->{schema=null;choosing=true;init();});
            if(labels.size()>visibleFields())button("Поля ›",left+100,height-29,95,()->{fieldPage=(fieldPage+1)%((labels.size()+visibleFields()-1)/visibleFields());init();});return;
        }
        if(choosing){
            var forms=array("forms");int y=54;for(int i=scroll;i<forms.size() && y<height-65;i++,y+=27){var form=forms.get(i).getAsJsonObject();button(text(form,"title",""),left+10,y,w-10,()->{schema=form;fieldPage=0;choosing=false;init();});}
            button("До списку",left+10,height-45,115,()->{choosing=false;scroll=0;init();});return;
        }
        if(!array("forms").isEmpty())button("Дії та оголошення",left+10,34,Math.min(200,w),()->{choosing=true;scroll=0;init();});
        int y=array("forms").isEmpty()?45:63;var rows=array("rows");
        for(int i=scroll;i<rows.size() && y<height-90;i++){
            var row=rows.get(i).getAsJsonObject();int buttonY=y+49,x=left+10;
            if(row.has("buttons"))for(var raw:row.getAsJsonArray("buttons")){
                var b=raw.getAsJsonObject();String action=text(b,"action",""),form=text(b,"form",""),value=text(b,"value","");
                int bx=x;button(text(b,"label",""),bx,buttonY,Math.min(145,(w-12)/2),()->{
                    if(action.equals("form")){for(var f:array("forms"))if(text(f.getAsJsonObject(),"id","").equals(form)){schema=f.getAsJsonObject().deepCopy();schema.addProperty("prefill",value);fieldPage=0;init();break;}}
                    else send(action,form,List.of(value),"",page);
                });x+=Math.min(145,(w-12)/2)+4;
            }y+=79;
        }
        if(!section.equals("build")){button("‹",left+10,height-35,35,()->{scroll=0;send("view","",List.of(),"",Math.max(1,page-1));});button("›",left+105,height-35,35,()->{scroll=0;send("view","",List.of(),"",page+1);});}
    }
    @Override public void tick(){
        if(++ticks>=30 && !busy && schema==null && detail==null && !choosing && !data.has("confirmation")){ticks=0;send("view","",List.of(),"",page);}
        if(busy && ticks>160){busy=false;data.addProperty("error","Відповідь затримується. Спробуйте ще раз.");}
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){if(schema==null){String prose=detail!=null?detail:data.has("confirmation")?text(data.getAsJsonObject("confirmation"),"text",""):null;int max=prose!=null?Math.max(0,font.split(Component.literal(prose),width-left()-24).size()-(height-135)/13):Math.max(0,(choosing?array("forms"):array("rows")).size()-1);scroll=Math.max(0,Math.min(max,scroll-(int)delta));init();return true;}return super.mouseScrolled(x,y,delta);}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(detail==null&&schema==null&&!choosing&&!data.has("confirmation")&&x>left()&&x<width-12){
            int top=array("forms").isEmpty()?45:63;var rows=array("rows");
            for(int i=scroll;i<rows.size()&&top<height-90;i++,top+=79)if(y>=top&&y<top+42){var row=rows.get(i).getAsJsonObject();detail=text(row,"title","")+"\n\n"+text(row,"detail","")+"\n\nID: "+text(row,"id","");scroll=0;init();return true;}
        }return super.mouseClicked(x,y,button);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xff201e1a);g.fill(4,4,width-4,height-4,0xff302b23);g.drawString(font,"Міський флот · "+text(data,"city",""),12,12,0xffe7c981,false);int left=left(),w=width-left-24;
        if(detail!=null){var lines=font.split(Component.literal(detail),w);int y=45;for(int i=scroll;i<lines.size()&&y<height-65;i++,y+=13)g.drawString(font,lines.get(i),left+10,y,0xffeadfc5,false);}
        else if(data.has("confirmation")){
            var lines=font.split(Component.literal(text(data.getAsJsonObject("confirmation"),"text","")),w);int y=52;for(int i=scroll;i<lines.size()&&y<height-83;i++,y+=13)g.drawString(font,lines.get(i),left+10,y,0xffeadfc5,false);
        }else if(schema!=null){
            g.drawString(font,text(schema,"title",""),left+10,40,0xffe7c981,false);var labels=schema.getAsJsonArray("fields");
            for(int i=fieldPage*visibleFields();i<Math.min(labels.size(),(fieldPage+1)*visibleFields());i++)g.drawString(font,font.plainSubstrByWidth(labels.get(i).getAsString(),w),left+10,63+(i%visibleFields())*39,0xffeadfc5,false);
        }else if(!choosing){
            int y=array("forms").isEmpty()?45:63;var rows=array("rows");
            for(int i=scroll;i<rows.size() && y<height-90;i++){
                var row=rows.get(i).getAsJsonObject();g.drawString(font,font.plainSubstrByWidth(text(row,"title",""),w),left+10,y,0xffe7c981,false);
                var lines=font.split(Component.literal(text(row,"detail","")),w);for(int n=0;n<Math.min(2,lines.size());n++)g.drawString(font,lines.get(n),left+10,y+14+n*12,0xffe4d7bd,false);y+=79;
            }
            if(rows.isEmpty())g.drawString(font,"Поки що записів немає.",left+10,85,0xffb9aa90,false);
            page=data.has("page")?data.get("page").getAsInt():1;g.drawString(font,page+" / "+(data.has("pages")?data.get("pages").getAsInt():1),left+55,height-29,0xffeadfc5,false);
        }
        if(data.has("error"))g.drawString(font,font.plainSubstrByWidth(data.get("error").getAsString(),w),left+10,height-70,0xffff8d78,false);
        if(data.has("notice"))g.drawString(font,font.plainSubstrByWidth(data.get("notice").getAsString(),w),left+10,height-70,0xff9bd5a3,false);
        super.render(g,mx,my,partial);
    }
}
