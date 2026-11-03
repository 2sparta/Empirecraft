package ua.empirecraft.townymap.client;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ua.empirecraft.townymap.MarketNetwork;
import java.util.*;

/** Native forms, paged terms and price gaps. Text fields are plain data, never chat commands. */
public final class MarketScreen extends Screen {
    private JsonObject data;private String section="goods",session,form="",selected="",requestId="";private JsonObject schema;
    private final String prefix=UUID.randomUUID().toString();private long revision;private int ticks,scroll,formOffset,fieldPage,focus;
    private boolean busy;private String lastAction="";private final List<EditBox> fields=new ArrayList<>();private final List<Hit> hits=new ArrayList<>();
    private record Hit(int x,int y,int w,int h,Runnable action) {boolean contains(double x,double y){return x>=this.x&&x<this.x+w&&y>=this.y&&y<this.y+h;}}
    private static final String[] SECTIONS={"goods","fx","jobs","credit","assets","mine","mail","manage","admin"};
    private static final String[] LABELS={"Товари","Валюти","Роботи","Кредити","Мої активи","Мої операції","Пошта","Керування","Огляд"};
    private MarketScreen(JsonObject data){super(Component.literal("Міський ринок"));this.data=data;session=text(data,"session","");section=text(data,"section","goods");}
    public static void receive(MarketNetwork.Reply reply){
        JsonObject next=JsonParser.parseString(reply.json()).getAsJsonObject();var mc=Minecraft.getInstance();
        if(reply.open()){mc.setScreen(new MarketScreen(next));return;}
        if(mc.screen instanceof MarketScreen screen && screen.requestId.equals(reply.screen())){screen.data=next;screen.busy=false;if(screen.lastAction.equals("prepare")&&next.has("confirmation"))screen.leaveForm();}
    }
    static String text(JsonObject json,String key,String fallback){return json.has(key)&&!json.get(key).isJsonNull()?json.get(key).getAsString():fallback;}
    JsonArray array(String key){return data.has(key)&&data.get(key).isJsonArray()?data.getAsJsonArray(key):new JsonArray();}
    void send(String action,String form,List<String> values,String token,int page){if(busy)return;lastAction=action;busy=true;ticks=0;requestId=prefix+":"+(++revision);MarketNetwork.CHANNEL.sendToServer(new MarketNetwork.Request(session,action,section,form,values,token,page,requestId));}
    @Override protected void init(){if(schema!=null)buildFields();}
    void choose(JsonObject form){this.form=text(form,"id","");schema=form;fields.clear();fieldPage=0;buildFields();}
    void buildFields(){List<String> previous=fields.stream().map(EditBox::getValue).toList();clearWidgets();fields.clear();int x=Math.max(112,width/3),w=Math.max(100,width-x-20);var labels=schema.getAsJsonArray("fields");
        for(int i=0;i<labels.size();i++){var field=new EditBox(font,x,76+(i%visibleFields())*38,w,18,Component.literal(labels.get(i).getAsString()));field.setMaxLength(4000);field.setValue(i<previous.size()?previous.get(i):i==0&&labels.get(i).getAsString().startsWith("ID")?selected:"");fields.add(field);addRenderableWidget(field);}updateFields();}
    int visibleFields(){return Math.max(1,(height-150)/38);}
    void updateFields(){for(int i=0;i<fields.size();i++){boolean visible=i/visibleFields()==fieldPage;fields.get(i).visible=visible;fields.get(i).active=visible;}}
    void button(GuiGraphics g,String label,int x,int y,int w,Runnable action){g.fill(x,y,x+w,y+18,0xff59422b);g.drawString(font,font.plainSubstrByWidth(label,w-8),x+4,y+5,0xfff0debd,false);hits.add(new Hit(x,y,w,18,action));}
    void leaveForm(){form="";schema=null;fields.clear();clearWidgets();scroll=0;}
    @Override public void tick(){if(++ticks>=40&&!busy&&schema==null&&!data.has("confirmation")&&!data.has("document")){ticks=0;send("view","",List.of(),"",1);}if(busy&&ticks>200){busy=false;data.addProperty("error","Відповідь затримується. Спробуйте оновити екран.");}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseClicked(double x,double y,int button){for(var hit:new ArrayList<>(hits))if(hit.contains(x,y)){hit.action.run();return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(schema==null)scroll=Math.max(0,scroll-(int)delta*3);else {fieldPage=Math.max(0,Math.min((fields.size()-1)/visibleFields(),fieldPage-(int)delta));updateFields();}return true;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        hits.clear();g.fill(0,0,width,height,0xff211b16);g.fill(5,5,width-5,height-5,0xff30271d);g.drawString(font,"Міський ринок · "+text(data,"marketName",""),12,12,0xffe9c57c,false);
        int nav=Math.max(90,Math.min(130,width/4));for(int i=0;i<SECTIONS.length;i++){final String target=SECTIONS[i];int color=section.equals(target)?0xff8b693c:0xff483726;g.fill(10,34+i*22,nav,53+i*22,color);button(g,LABELS[i],12,35+i*22,nav-14,()->{leaveForm();section=target;formOffset=0;send("view","",List.of(),"",1);});}
        int left=nav+12,w=width-left-12,y=38;
        if(schema!=null){
            g.drawString(font,font.plainSubstrByWidth(text(schema,"title",""),w),left,y,0xffe9c57c,false);
            var labels=schema.getAsJsonArray("fields");for(int i=fieldPage*visibleFields();i<Math.min(fields.size(),(fieldPage+1)*visibleFields());i++){int fy=60+(i%visibleFields())*38;g.drawString(font,font.plainSubstrByWidth(labels.get(i).getAsString(),w),Math.max(112,width/3),fy,0xffddcbb0,false);}
            button(g,"‹ Поля",left,height-64,75,()->{fieldPage=Math.max(0,fieldPage-1);updateFields();});button(g,"Поля ›",left+80,height-64,75,()->{fieldPage=Math.min((fields.size()-1)/visibleFields(),fieldPage+1);updateFields();});
            button(g,"Перевірити умови",left,height-42,Math.min(145,w/2),()->send("prepare",form,fields.stream().map(EditBox::getValue).toList(),"",1));button(g,"Назад",left+Math.min(150,w/2+5),height-42,75,this::leaveForm);
        }else {
            var forms=array("forms");int shown=Math.min(4,Math.max(1,(height-140)/30));for(int i=formOffset;i<Math.min(forms.size(),formOffset+shown);i++){var item=forms.get(i).getAsJsonObject();button(g,text(item,"title",""),left,y,w,()->choose(item));y+=22;}
            if(forms.size()>shown){button(g,"‹ Дії",left,y,65,()->formOffset=Math.max(0,formOffset-shown));button(g,"Дії ›",left+70,y,65,()->formOffset=Math.min(Math.max(0,forms.size()-shown),formOffset+shown));y+=22;}
            String reason="";for(var d:array("departments")){var dep=d.getAsJsonObject();if(text(dep,"id","").equals(section)&&!dep.get("open").getAsBoolean())reason="🔒 "+text(dep,"reason","");}
            if(!reason.isEmpty()){g.drawWordWrap(font,Component.literal(reason),left,y,w,0xffe0a181);y+=26;}
            var content=new ArrayList<net.minecraft.util.FormattedCharSequence>();for(var line:array("messages"))content.addAll(font.split(Component.literal(line.getAsString()),w));
            int lines=Math.max(1,(height-y-62)/11);for(int i=Math.min(scroll,Math.max(0,content.size()-1));i<Math.min(content.size(),scroll+lines);i++){g.drawString(font,content.get(i),left,y,0xffddcbb0,false);y+=11;}
            for(var entry:array("rows")){if(y>height-80)break;var row=entry.getAsJsonObject();String id=text(row,"id","");button(g,text(row,"label","")+" · "+id,left,y,w,()->{selected=id;String kind=text(row,"kind","");if(kind.equals("VASSAL")||kind.equals("TREATY"))send("document",kind,List.of(id),"",1);else Minecraft.getInstance().keyboardHandler.setClipboard(id);});y+=21;}
            if(data.has("document")){var d=data.getAsJsonObject("document");int page=d.get("page").getAsInt(),pages=d.get("pages").getAsInt();String kind=text(d,"kind",""),id=text(d,"id","");button(g,"Сторінка "+page+"/"+pages+" ›",left,height-64,135,()->send("document",kind,List.of(id),"",Math.min(pages,page+1)));if(!text(d,"token","").isEmpty())button(g,"Прийняти умови",left+140,height-64,Math.max(90,w-140),()->send("sign",kind,List.of(id),text(d,"token",""),page));}
            button(g,"Оновити",left,height-42,90,()->send("view","",List.of(),"",1));
        }
        if(data.has("confirmation"))button(g,"Підтвердити перевірену дію",Math.max(nav+12,width-215),height-22,200,()->{leaveForm();send("confirm","",List.of(),text(data,"confirmation",""),1);});
        String status=busy?"Оновлення…":text(data,"error","");if(!status.isEmpty())g.drawString(font,font.plainSubstrByWidth(status,Math.max(80,width-nav-25)),nav+12,height-9,0xffeeac8e,false);
        super.render(g,mx,my,partial);
    }
}
