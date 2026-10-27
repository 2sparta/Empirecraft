package ua.empirecraft.townymap.client;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ua.empirecraft.townymap.CurrencyNetwork;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Native Minecraft screen with a real time series, pair/period selection, purchases and a mint queue. */
public final class CurrencyScreen extends Screen {
    private JsonObject data;private boolean printing,waiting;private int ticks;
    private String from="@GOLD",to="",period="1d",token="",pending="",quote="",error="",notice="";
    private JsonObject preview;
    private EditBox amount;private Button fromButton,toButton,periodButton,confirm,cancel,resume,calculate;
    private int x,y,w,h,side,sideX,graphX,graphY,graphW,graphH;
    private double uiScale=1;
    private static final String[] PERIODS={"1h","6h","1d","7d","30d"};
    private static final String[] PERIOD_LABELS={"1 година","6 годин","1 доба","7 діб","30 діб"};
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.of("Europe/Kyiv"));
    CurrencyScreen(String mode,JsonObject data){super(Component.literal("Валютний ринок"));printing=mode.equals("print");this.data=data;from=text(data,"from","@GOLD");to=text(data,"to","");period=text(data,"period","1d");error=text(data,"error","");}
    static String text(JsonObject object,String key,String fallback){return object.has(key)&&!object.get(key).isJsonNull()?object.get(key).getAsString():fallback;}
    boolean flag(String key){return data.has(key)&&data.get(key).getAsBoolean();}
    static String label(String code){return code.equals("@GOLD")?"Золото":code;}
    private Button button(String title,int x,int y,int width,Button.OnPress action){return addRenderableWidget(Button.builder(Component.literal(title),action).bounds(x,y,width,20).build());}
    @Override protected void init(){
        String previous=amount==null?"":amount.getValue();clearWidgets();uiScale=Math.min(1.0,Math.min(width/600.0,height/350.0));x=10;y=12;w=(int)(width/uiScale)-20;h=(int)(height/uiScale)-24;side=Math.min(220,Math.max(140,w/3));sideX=x+w-side-12;graphX=x+12;graphY=y+85;graphW=sideX-graphX-18;graphH=Math.max(40,h-137);
        button("Ринок",x+Math.max(116,w-202),y+7,82,b->{printing=false;reset();init();request("view");});
        button("Друк",x+Math.max(202,w-116),y+7,82,b->{printing=true;String own=text(data,"own","");if(!own.isEmpty())to=own;reset();init();request("view");});
        fromButton=button(label(from),graphX,y+44,Math.max(65,(graphW-110)/2),b->cycle(false));
        toButton=button(label(to),graphX+Math.max(65,(graphW-110)/2)+8,y+44,Math.max(65,(graphW-110)/2),b->cycle(true));
        periodButton=button(periodLabel(),graphX+graphW-92,y+44,92,b->changePeriod(1));
        amount=addRenderableWidget(new EditBox(font,sideX+10,y+120,side-20,20,Component.literal("Сума")));amount.setMaxLength(26);amount.setValue(previous);amount.setFilter(v->v.matches("[0-9]*([.,][0-9]*)?"));amount.setResponder(v->reset());
        calculate=button("Розрахувати",sideX+10,y+146,side-20,b->request(printing?"preview":"quote"));
        confirm=button("Підтвердити",sideX+10,y+h-35,side-20,b->{if(!token.isEmpty())request(pending);});
        cancel=button("Скасувати чергу",sideX+10,y+h-84,side-20,b->{pending="cancel";request("cancel-preview");});
        resume=button("Продовжити чергу",sideX+10,y+h-60,side-20,b->{pending="resume";request("resume-preview");});
        refreshButtons();
    }
    void reset(){token="";pending="";quote="";preview=null;error="";if(confirm!=null)confirm.active=false;}
    List<String> codes(boolean target){var result=new ArrayList<String>();if(data.has("codes"))for(var value:data.getAsJsonArray("codes"))if(!target||!value.getAsString().equals("@GOLD"))result.add(value.getAsString());return result;}
    void cycle(boolean target){var codes=codes(target);if(codes.isEmpty())return;String current=target?to:from;String next=codes.get((codes.indexOf(current)+1)%codes.size());if(target)to=next;else from=next;reset();refreshButtons();request("view");}
    String periodLabel(){return PERIOD_LABELS[Math.max(0,Arrays.asList(PERIODS).indexOf(period))];}
    void changePeriod(int step){int index=Math.floorMod(Arrays.asList(PERIODS).indexOf(period)+step,PERIODS.length);period=PERIODS[index];periodButton.setMessage(Component.literal(periodLabel()));request("view");}
    void request(String action){
        if(waiting)return;waiting=true;error="";
        CurrencyNetwork.CHANNEL.sendToServer(new CurrencyNetwork.Request(action,from,to,amount==null?"":amount.getValue(),period,token));
        if(Set.of("buy","print","cancel","resume").contains(action))reset();refreshButtons();
    }
    public void update(JsonObject response){
        waiting=false;
        if(response.has("error")){error=response.get("error").getAsString();token="";pending="";}
        if(response.has("codes")){data=response;from=text(response,"from",from);to=text(response,"to",to);}
        if(response.has("message"))notice=response.get("message").getAsString();
        if(response.has("quote")){quote=response.get("quote").getAsString();pending="buy";}
        if(response.has("preview")){preview=response.getAsJsonObject("preview");pending="print";}
        if(response.has("token"))token=response.get("token").getAsString();
        refreshButtons();
    }
    void refreshButtons(){
        if(fromButton==null)return;fromButton.setMessage(Component.literal(label(from)));toButton.setMessage(Component.literal(label(to)));periodButton.setMessage(Component.literal(periodLabel()));
        fromButton.active=!waiting;toButton.active=!waiting;periodButton.active=!waiting;calculate.active=!waiting&&!to.isEmpty()&&(!printing||flag("manage")&&flag("eligible"));
        confirm.active=!waiting&&!token.isEmpty();confirm.setMessage(Component.literal(pending.equals("cancel")?"Підтвердити скасування":pending.equals("resume")?"Підтвердити продовження":"Підтвердити"));
        JsonObject job=data.has("job")?data.getAsJsonObject("job"):new JsonObject();String status=text(job,"status","");
        cancel.visible=printing&&flag("manage")&&Set.of("ACTIVE","PAUSED").contains(status);resume.visible=printing&&flag("manage")&&status.equals("PAUSED");cancel.active=!waiting;resume.active=!waiting;
    }
    @Override public void tick(){super.tick();if(amount!=null)amount.tick();if(++ticks%100==0&&!waiting)request("view");if(waiting && ticks%200==0){waiting=false;error="Немає відповіді сервера; повторіть запит.";refreshButtons();}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseClicked(double mx,double my,int button){return super.mouseClicked(mx/uiScale,my/uiScale,button);}
    @Override public boolean mouseReleased(double mx,double my,int button){return super.mouseReleased(mx/uiScale,my/uiScale,button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return super.mouseDragged(mx/uiScale,my/uiScale,button,dx/uiScale,dy/uiScale);}
    @Override public void mouseMoved(double mx,double my){super.mouseMoved(mx/uiScale,my/uiScale);}
    @Override public boolean mouseScrolled(double mx,double my,double delta){mx/=uiScale;my/=uiScale;if(mx>=graphX&&mx<graphX+graphW&&my>=graphY&&my<graphY+graphH){changePeriod(delta>0?-1:1);return true;}return super.mouseScrolled(mx,my,delta);}
    private void line(GuiGraphics g,int x1,int y1,int x2,int y2,int color){int n=Math.max(Math.abs(x2-x1),Math.abs(y2-y1));for(int i=0;i<=n;i++){int px=x1+(x2-x1)*i/Math.max(1,n),py=y1+(y2-y1)*i/Math.max(1,n);g.fill(px,py,px+2,py+2,color);}}
    private void text(GuiGraphics g,String value,int x,int y,int color,int maxWidth){g.drawString(font,font.plainSubstrByWidth(value,Math.max(10,maxWidth)),x,y,color,false);}
    private void graph(GuiGraphics g,int mx,int my){
        int left=graphX+8,top=graphY+15,gw=Math.max(10,graphW-20),gh=Math.max(20,graphH-30);g.fill(graphX,graphY,graphX+graphW,graphY+graphH,0xff0d1723);
        JsonArray points=data.has("history")?data.getAsJsonArray("history"):new JsonArray();
        if(points.isEmpty()){text(g,"Історії курсу поки немає",left,top+gh/2,0xffa9b9cc,gw);return;}
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;for(var row:points){double v=row.getAsJsonObject().get("price").getAsDouble();min=Math.min(min,v);max=Math.max(max,v);}double range=max-min;if(range==0){range=Math.max(0.000001,Math.abs(max)*0.1);min=Math.max(0,min-range/2);max=min+range;}
        for(int i=0;i<4;i++){int py=top+gh*i/3;g.fill(left,py,left+gw,py+1,0xff243144);text(g,String.format(Locale.ROOT,"%.6g",max-(max-min)*i/3),left+3,py+2,0xff7d91aa,gw);}
        long end=data.has("now")?data.get("now").getAsLong():System.currentTimeMillis();long duration=switch(period){case "1h"->3600000;case "6h"->21600000;case "7d"->604800000;case "30d"->2592000000L;default->86400000;};long start=end-duration;
        int px=0,py=0;JsonObject hover=null;int best=Integer.MAX_VALUE;
        for(var row:points){JsonObject p=row.getAsJsonObject();int nx=left+(int)((p.get("at").getAsLong()-start)*(double)gw/duration);int ny=top+gh-(int)((p.get("price").getAsDouble()-min)/(max-min)*gh);
            if(px!=0)line(g,px,py,nx,ny,0xffe9bd68);g.fill(nx-1,ny-1,nx+2,ny+2,0xffffd986);px=nx;py=ny;
            if(Math.abs(mx-nx)<best){best=Math.abs(mx-nx);hover=p;}
        }
        text(g,DATE.format(Instant.ofEpochMilli(start)),left,top+gh+6,0xff7d91aa,gw/2);String endText=DATE.format(Instant.ofEpochMilli(end));text(g,endText,left+gw-font.width(endText),top+gh+6,0xff7d91aa,gw/2);
        if(hover!=null&&mx>=left&&mx<=left+gw&&my>=top&&my<=top+gh){g.fill(mx,top,mx+1,top+gh,0x887f9db8);String value=DATE.format(Instant.ofEpochMilli(hover.get("at").getAsLong()))+" • "+hover.get("price").getAsString();g.fill(left,top-13,left+Math.min(gw,font.width(value)+6),top,0xff203349);text(g,value,left+3,top-11,0xfff4e9d2,gw-6);}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);g.pose().pushPose();g.pose().scale((float)uiScale,(float)uiScale,1);mx=(int)(mx/uiScale);my=(int)(my/uiScale);g.fill(x,y,x+w,y+h,0xf5152232);g.fill(x,y,x+3,y+h,0xffd6a75c);g.fill(sideX,y+38,x+w-8,y+h-8,0xff1d2d42);
        text(g,printing?"ДРУК МОНЕТ":"ВАЛЮТНИЙ РИНОК",x+13,y+13,0xfff2cf8f,w-218);
        text(g,"1 "+label(from)+" → "+label(to),graphX,graphY-14,0xffb8c7d8,graphW);graph(g,mx,my);
        text(g,text(data,"issuer","Державна валюта"),sideX+10,y+46,0xffe9bd68,side-20);
        text(g,"1 "+to+" = "+text(data,"value","—")+" злитка",sideX+10,y+63,0xffe2e8ef,side-20);
        text(g,printing?"Казна: "+text(data,"treasury","—")+" "+to:"Золото: "+text(data,"gold","0"),sideX+10,y+79,0xffa6bbd1,side-20);
        String balance=data.has("balances")?text(data.getAsJsonObject("balances"),from,"0"):"0";
        text(g,printing?"Кожні "+text(data,"interval","—")+" с — 1 монета":"Баланс: "+(from.equals("@GOLD")?text(data,"gold","0"):balance)+" "+label(from),sideX+10,y+95,0xffa6bbd1,side-20);
        text(g,printing?"Кількість цілих монет":"Витратити "+label(from),sideX+10,y+109,0xffe2e8ef,side-20);
        int detailY=y+174;
        if(preview!=null&&printing){text(g,"Після друку: "+text(preview,"after","—")+" зол./мон.",sideX+10,detailY,0xffe9bd68,side-20);text(g,"Тривалість: "+duration(text(preview,"seconds","0")),sideX+10,detailY+14,0xffb8c7d8,side-20);}
        if(!quote.isEmpty()&&!printing)text(g,"Отримаєте: "+quote+" "+to,sideX+10,detailY,0xffe9bd68,side-20);
        if(printing&&data.has("job")){var job=data.getAsJsonObject("job");String status=switch(text(job,"status","")){case "ACTIVE"->"Друкується";case "PAUSED"->"Призупинено";case "DONE"->"Завершено";default->"Скасовано";};text(g,status+": "+text(job,"issued","0")+" готово",sideX+10,detailY+32,0xffb8c7d8,side-20);text(g,"Залишок: "+text(job,"remaining","0")+" • "+duration(text(job,"seconds","0")),sideX+10,detailY+46,0xffb8c7d8,side-20);}
        if(printing&&!flag("manage"))text(g,"Потрібні права мера або Банкіра",graphX,y+h-35,0xffe99d8f,graphW);
        else text(g,waiting?"Оновлення…":error.isEmpty()?notice:error,graphX,y+h-35,error.isEmpty()?0xffa6bbd1:0xfff29e96,graphW);
        text(g,"Колесо над графіком: період • наведення: курс і час",graphX,y+h-19,0xff7891ac,graphW);
        super.render(g,mx,my,partial);g.pose().popPose();
    }
    static String duration(String seconds){long n=Long.parseLong(seconds);return n>=86400?n/86400+" д "+n%86400/3600+" год":n>=3600?n/3600+" год "+n%3600/60+" хв":n>=60?n/60+" хв "+n%60+" с":n+" с";}
}
