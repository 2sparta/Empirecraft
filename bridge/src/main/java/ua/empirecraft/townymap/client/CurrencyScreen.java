package ua.empirecraft.townymap.client;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ua.empirecraft.townymap.CurrencyNetwork;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Native currency charts, protected treasury swaps, minting, policy and private claims. */
public final class CurrencyScreen extends Screen {
    private final String sessionPrefix=UUID.randomUUID().toString().substring(0,16);private final CurrencyViewState requests=new CurrencyViewState();
    public String session(){return sessionPrefix+":"+requests.revision();}
    private JsonObject data,trade,preview;private String mode,from="",to="",period="1d",token="",pending="",error="",notice="",claim="",cursor="";
    private boolean changing,policyDirty;private int ticks,logOffset;
    private EditBox amount,minimum;private EditBox[] policy=new EditBox[0];private final Map<String,WoodButton> tabs=new LinkedHashMap<>();
    private Button fromButton,toButton,swap,periodButton,confirm,cancel,resume,calculate,older,first,select;
    private int x,y,w,h,side,sideX,graphX,graphY,graphW,graphH;private double uiScale=1;
    private static final String[] PERIODS={"1h","6h","1d","7d","30d"},PERIOD_LABELS={"1 година","6 годин","1 доба","7 діб","30 діб"};
    private static final String[] KEYS={"buy","sell","buyBurn","sellBurn","trade","window","floor","daily"};
    private static final String[] FIELDS={"Купівля: комісія %","Продаж: комісія %","Купівля: спалити %","Продаж: спалити %","Угода: % резерву","24 години: % резерву","Мін. забезпечення %","Погашення/гравця/24г"};
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.of("Europe/Kyiv"));
    CurrencyScreen(String mode,JsonObject data){super(Component.literal("Валютний ринок"));this.data=data;this.mode=mode.equals("print")&&!flag("canPrint")?"market":mode;from=text(data,"from","");to=text(data,"to","");period=text(data,"period","1d");error=text(data,"error","");}
    static String text(JsonObject object,String key,String fallback){return object.has(key)&&!object.get(key).isJsonNull()?object.get(key).getAsString():fallback;}
    boolean flag(String key){return data.has(key)&&data.get(key).getAsBoolean();}
    static String label(String code){return code.isEmpty()?"—":code;}
    private static final ResourceLocation SPRUCE=new ResourceLocation("minecraft","textures/block/spruce_planks.png");
    private static void wood(GuiGraphics g,int x,int y,int width,int height,int shade){
        for(int py=y;py<y+height;py+=16)for(int px=x;px<x+width;px+=16)g.blit(SPRUCE,px,py,0f,0f,Math.min(16,x+width-px),Math.min(16,y+height-py),16,16);
        if(shade!=0)g.fill(x,y,x+width,y+height,shade);
    }
    private static void border(GuiGraphics g,int x,int y,int width,int height,int color){g.fill(x,y,x+width,y+1,color);g.fill(x,y+height-1,x+width,y+height,color);g.fill(x,y,x+1,y+height,color);g.fill(x+width-1,y,x+width,y+height,color);}
    private final class WoodButton extends Button {
        boolean selected;
        WoodButton(String title,int x,int y,int width,OnPress action){super(x,y,width,20,Component.literal(title),action,DEFAULT_NARRATION);}
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){
            wood(g,getX(),getY(),getWidth(),getHeight(),!active?0xc52b2017:selected?0x55422a13:isHoveredOrFocused()?0x222b180b:0x77402b17);
            border(g,getX(),getY(),getWidth(),getHeight(),!active?0xff715638:selected||isHoveredOrFocused()?0xffe4bd75:0xffad8250);
            if(selected)g.fill(getX()+2,getY()+getHeight()-3,getX()+getWidth()-2,getY()+getHeight()-1,0xffe4bd75);
            g.drawCenteredString(font,getMessage(),getX()+getWidth()/2,getY()+(getHeight()-8)/2,active?0xfff8ebd0:0xffb19b79);
        }
    }
    private WoodButton button(String title,int x,int y,int width,Button.OnPress action){return addRenderableWidget(new WoodButton(title,x,y,width,action));}
    private EditBox field(int px,int py,int width,String value,Runnable changed){var box=addRenderableWidget(new EditBox(font,px,py,width,20,Component.literal("Значення")));box.setBordered(false);box.setTextColor(0xfff7e8c9);box.setTextColorUneditable(0xffa99577);box.setMaxLength(26);box.setFilter(v->v.matches("[0-9]*([.,][0-9]*)?"));box.setValue(value);box.setResponder(v->{if(!changing)changed.run();});return box;}
    @Override protected void init(){
        String a=amount==null?"":amount.getValue(),m=minimum==null?"":minimum.getValue();String[] values=policyValues();clearWidgets();tabs.clear();
        uiScale=Math.min(1.0,Math.min(width/900.0,height/600.0));x=10;y=12;w=(int)(width/uiScale)-20;h=(int)(height/uiScale)-24;side=300;sideX=x+w-side-12;graphX=x+12;graphY=y+85;graphW=sideX-graphX-18;graphH=Math.max(40,h-137);
        String[] modes={"market","print","policy","log","redeem"},labels={"Ринок","Друк","Політика","Журнал","Погашення"};
        for(int i=0;i<modes.length;i++){final String next=modes[i];tabs.put(next,button(labels[i],x+w-440+i*86,y+7,82,b->{mode=next;if(Set.of("print","policy").contains(mode)){String own=text(data,mode.equals("print")?"printCode":"own","");if(!own.isEmpty()){to=own;if(from.equals(to))from=codes(false).stream().findFirst().orElse("");}}cursor="";logOffset=0;policyDirty=false;reset();request(mode.equals("log")?"log":"view");}));}
        fromButton=button(label(from),graphX,y+44,(graphW-148)/2,b->cycle(false));swap=button("↔",graphX+(graphW-148)/2+5,y+44,26,b->{String previous=from;from=to;to=previous;changing=true;minimum.setValue("");changing=false;cursor="";logOffset=0;reset();request(mode.equals("log")?"log":"view");});toButton=button(label(to),graphX+(graphW-148)/2+36,y+44,(graphW-148)/2,b->cycle(true));periodButton=button(periodLabel(),graphX+graphW-92,y+44,92,b->changePeriod(1));
        amount=field(sideX+10,y+123,side-20,a,this::reset);minimum=field(sideX+10,y+169,side-20,m,this::reset);
        calculate=button("Розрахувати",sideX+10,y+199,side-20,b->request(mode.equals("print")?"preview":mode.equals("policy")?"policy-quote":mode.equals("redeem")?"refund-quote":"quote"));
        confirm=button("Підтвердити",sideX+10,y+h-35,side-20,b->{if(!token.isEmpty())request(pending);});cancel=button("Скасувати друк",sideX+10,y+h-84,side-20,b->request("cancel-preview"));resume=button("Продовжити друк",sideX+10,y+h-60,side-20,b->request("resume-preview"));
        older=button("Старіші записи",graphX,y+h-51,150,b->{var rows=array("journal");if(!rows.isEmpty()){cursor=rows.get(rows.size()-1).getAsJsonObject().get("seq").getAsString();logOffset=0;request("log");}});first=button("Найновіші",graphX+158,y+h-51,130,b->{cursor="";logOffset=0;request("log");});
        select=button("Обрати заявку",sideX+10,y+123,side-20,b->{var rows=array("requests");var ids=new ArrayList<String>();for(var row:rows)if(text(row.getAsJsonObject(),"status","").equals("PENDING"))ids.add(text(row.getAsJsonObject(),"id",""));if(!ids.isEmpty())claim=ids.get((ids.indexOf(claim)+1)%ids.size());reset();refreshButtons();});
        policy=new EditBox[8];for(int i=0;i<8;i++){int col=i%2,row=i/2;policy[i]=field(graphX+col*(graphW/2),y+125+row*59,graphW/2-16,values[i],()->{policyDirty=true;reset();});}
        populate();refreshButtons();
    }
    String[] policyValues(){var result=new String[8];Arrays.fill(result,"");for(int i=0;i<policy.length;i++)result[i]=policy[i].getValue();return result;}
    void populate(){if(policyDirty||!data.has("settings"))return;var active=data.getAsJsonObject("settings").getAsJsonObject("active");changing=true;for(int i=0;i<policy.length;i++)policy[i].setValue(text(active,KEYS[i],""));changing=false;}
    void reset(){token="";pending="";trade=null;preview=null;error="";if(confirm!=null)confirm.active=false;}
    List<String> codes(boolean target){var result=new ArrayList<String>();for(var value:array("codes"))result.add(value.getAsString());if(target&&mode.equals("print")){String own=text(data,"printCode","");return own.isEmpty()?List.of():List.of(own);}return CurrencyViewState.choices(result,target?from:to);}
    boolean pair(){return !from.isEmpty()&&!to.isEmpty()&&!from.equals(to);}
    JsonArray array(String key){return data.has(key)?data.getAsJsonArray(key):new JsonArray();}
    void cycle(boolean target){var codes=codes(target);if(codes.isEmpty())return;String current=target?to:from,next=codes.get((codes.indexOf(current)+1)%codes.size());if(target)to=next;else from=next;changing=true;minimum.setValue("");changing=false;cursor="";logOffset=0;policyDirty=false;reset();refreshButtons();request(mode.equals("log")?"log":"view");}
    String periodLabel(){return PERIOD_LABELS[Math.max(0,Arrays.asList(PERIODS).indexOf(period))];}
    void changePeriod(int step){if(requests.busy())return;period=PERIODS[Math.floorMod(Arrays.asList(PERIODS).indexOf(period)+step,PERIODS.length)];request("view");}
    String config(){return mode.equals("policy")?String.join(";",policyValues()):mode.equals("log")?cursor:mode.equals("redeem")?claim:"";}
    void request(String action){request(action,false);}
    void request(String action,boolean background){if(!requests.begin(background,ticks))return;if(!background)error="";CurrencyNetwork.CHANNEL.sendToServer(new CurrencyNetwork.Request(action,from,to,amount.getValue(),minimum.getValue(),config(),period,token,session()));if(Set.of("buy","redeem","print","cancel","resume","policy-save","refund").contains(action))reset();refreshButtons();}
    public void update(JsonObject response){boolean background=requests.background();requests.complete();if(response.has("error")&&!background){error=response.get("error").getAsString();token="";pending="";}if(response.has("codes")){data=response;String oldFrom=from,oldTo=to;from=text(response,"from",from);to=text(response,"to",to);if(!from.equals(oldFrom)||!to.equals(oldTo))reset();if(mode.equals("print")&&!flag("canPrint")){mode="market";reset();}populate();}if(response.has("message"))notice=text(response,"message","");if(response.has("trade"))trade=response.getAsJsonObject("trade");if(response.has("preview"))preview=response.getAsJsonObject("preview");if(response.has("minimum")){changing=true;minimum.setValue(text(response,"minimum",""));changing=false;}if(response.has("nextAction"))pending=text(response,"nextAction","");if(response.has("token"))token=text(response,"token","");refreshButtons();}
    void refreshButtons(){if(fromButton==null)return;boolean waiting=requests.busy();var visible=CurrencyViewState.tabs(flag("canPrint"));int tabX=x+w-12-visible.size()*86;for(var entry:tabs.entrySet()){var tab=entry.getValue();tab.visible=visible.contains(entry.getKey());tab.active=!waiting;tab.selected=mode.equals(entry.getKey());if(tab.visible){tab.setX(tabX);tabX+=86;}}fromButton.setMessage(Component.literal(label(from)));toButton.setMessage(Component.literal(label(to)));periodButton.setMessage(Component.literal(periodLabel()));fromButton.active=!waiting&&!codes(false).isEmpty();toButton.active=!waiting&&!codes(true).isEmpty();periodButton.active=!waiting&&pair();
        boolean market=mode.equals("market"),print=mode.equals("print"),settings=mode.equals("policy"),claims=mode.equals("redeem");swap.visible=market||mode.equals("log");swap.active=!waiting&&pair();amount.visible=market||print;amount.setEditable(!waiting);minimum.visible=market;minimum.setEditable(!waiting);for(var field:policy){field.visible=settings;field.setEditable(!waiting&&flag("manage"));}
        calculate.visible=market||print||settings;calculate.setY(settings?y+h-65:claims?y+169:y+199);calculate.setMessage(Component.literal(settings?"Перевірити зміну":"Розрахувати"));calculate.active=!waiting&&(!settings&&!print||flag("manage"))&&(!print||flag("canPrintTarget"))&&(!market||pair());confirm.visible=market||print||settings||claims;confirm.active=!waiting&&!token.isEmpty();confirm.setMessage(Component.literal(pending.equals("policy-save")?"Зміна через 1 годину":pending.equals("redeem")?"У чергу погашення":pending.equals("refund")?"Повернути монети":"Підтвердити"));
        JsonObject job=data.has("job")?data.getAsJsonObject("job"):new JsonObject();String status=text(job,"status","");cancel.visible=print&&flag("canPrintTarget")&&Set.of("ACTIVE","PAUSED").contains(status);resume.visible=print&&flag("canPrintTarget")&&status.equals("PAUSED");cancel.active=resume.active=!waiting;
        older.visible=first.visible=mode.equals("log");older.active=first.active=!waiting;select.visible=claims;select.active=!waiting;
        if(getFocused() instanceof EditBox box&&!box.visible)setFocused(null);
        if(claims){calculate.visible=true;calculate.setMessage(Component.literal("Скасувати обрану заявку"));calculate.active=!waiting&&!claim.isEmpty();}
    }
    @Override public void tick(){super.tick();amount.tick();minimum.tick();for(var field:policy)field.tick();if(++ticks%100==0&&!requests.waiting())request(mode.equals("log")?"refresh-log":"refresh",true);if(requests.timedOut(ticks)){boolean foreground=requests.busy();requests.complete();if(foreground)error="Немає відповіді сервера; повторіть запит.";refreshButtons();}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseClicked(double mx,double my,int button){return super.mouseClicked(mx/uiScale,my/uiScale,button);}
    @Override public boolean mouseReleased(double mx,double my,int button){return super.mouseReleased(mx/uiScale,my/uiScale,button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return super.mouseDragged(mx/uiScale,my/uiScale,button,dx/uiScale,dy/uiScale);}
    @Override public void mouseMoved(double mx,double my){super.mouseMoved(mx/uiScale,my/uiScale);}
    @Override public boolean mouseScrolled(double mx,double my,double delta){if(requests.busy())return false;mx/=uiScale;my/=uiScale;if(mx>=graphX&&mx<graphX+graphW&&my>=graphY&&my<graphY+graphH){if(mode.equals("log"))logOffset=Math.max(0,logOffset+(delta>0?-1:1));else if(mode.equals("market")||mode.equals("print"))changePeriod(delta>0?-1:1);return true;}return super.mouseScrolled(mx,my,delta);}
    private void line(GuiGraphics g,int x1,int y1,int x2,int y2,int color){int n=Math.max(Math.abs(x2-x1),Math.abs(y2-y1));for(int i=0;i<=n;i++){int px=x1+(x2-x1)*i/Math.max(1,n),py=y1+(y2-y1)*i/Math.max(1,n);g.fill(px,py,px+2,py+2,color);}}
    private void text(GuiGraphics g,String value,int x,int y,int color,int maxWidth){g.drawString(font,font.plainSubstrByWidth(value,Math.max(10,maxWidth)),x,y,color,false);}
    private void graph(GuiGraphics g,int mx,int my){
        int left=graphX+8,top=graphY+15,gw=Math.max(10,graphW-20),gh=Math.max(20,graphH-30);wood(g,graphX,graphY,graphW,graphH,0xe0271c12);border(g,graphX,graphY,graphW,graphH,0xff86623c);
        JsonArray points=data.has("history")?data.getAsJsonArray("history"):new JsonArray();
        if(points.isEmpty()){text(g,pair()?"Історії курсу поки немає":"Для графіка потрібні дві різні валюти",left,top+gh/2,0xffc9b28c,gw);return;}
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;for(var row:points){double v=row.getAsJsonObject().get("price").getAsDouble();min=Math.min(min,v);max=Math.max(max,v);}double range=max-min;if(range==0){range=Math.max(0.000001,Math.abs(max)*0.1);min=Math.max(0,min-range/2);max=min+range;}
        for(int i=0;i<4;i++){int py=top+gh*i/3;g.fill(left,py,left+gw,py+1,0xff59462e);text(g,String.format(Locale.ROOT,"%.6g",max-(max-min)*i/3),left+3,py+2,0xffb69c75,gw);}
        long end=data.has("now")?data.get("now").getAsLong():System.currentTimeMillis();long duration=switch(period){case "1h"->3600000;case "6h"->21600000;case "7d"->604800000;case "30d"->2592000000L;default->86400000;};long start=end-duration;
        int px=0,py=0;JsonObject hover=null;int best=Integer.MAX_VALUE;
        for(var row:points){JsonObject p=row.getAsJsonObject();int nx=left+(int)((p.get("at").getAsLong()-start)*(double)gw/duration);int ny=top+gh-(int)((p.get("price").getAsDouble()-min)/(max-min)*gh);
            if(px!=0)line(g,px,py,nx,ny,0xffe9bd68);g.fill(nx-1,ny-1,nx+2,ny+2,0xffffd986);px=nx;py=ny;
            if(Math.abs(mx-nx)<best){best=Math.abs(mx-nx);hover=p;}
        }
        text(g,DATE.format(Instant.ofEpochMilli(start)),left,top+gh+6,0xffb69c75,gw/2);String endText=DATE.format(Instant.ofEpochMilli(end));text(g,endText,left+gw-font.width(endText),top+gh+6,0xffb69c75,gw/2);
        if(hover!=null&&mx>=left&&mx<=left+gw&&my>=top&&my<=top+gh){g.fill(mx,top,mx+1,top+gh,0x88e4bd75);String value=DATE.format(Instant.ofEpochMilli(hover.get("at").getAsLong()))+" • "+hover.get("price").getAsString();g.fill(left,top-13,left+Math.min(gw,font.width(value)+6),top,0xff60452c);text(g,value,left+3,top-11,0xfff4e9d2,gw-6);}
    }

    int wrap(GuiGraphics g,String value,int px,int py,int color,int width,int maxLines){int lines=0;for(var line:font.split(Component.literal(value),width)){if(lines++>=maxLines)break;g.drawString(font,line,px,py,color,false);py+=11;}return py;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);g.pose().pushPose();g.pose().scale((float)uiScale,(float)uiScale,1);mx=(int)(mx/uiScale);my=(int)(my/uiScale);wood(g,x,y,w,h,0x58332012);border(g,x,y,w,h,0xffbc9258);g.fill(x+4,y+34,x+w-4,y+35,0xff9e7544);wood(g,sideX,y+38,x+w-8-sideX,h-46,0xd83c2a1b);border(g,sideX,y+38,x+w-8-sideX,h-46,0xff8e693e);
        text(g,"ВАЛЮТИ ДЕРЖАВ",x+13,y+13,0xfff2cf8f,w-455);text(g,"1 "+label(from)+" → "+label(to),graphX,graphY-14,0xffdcc8a5,graphW);
        boolean market=mode.equals("market"),print=mode.equals("print"),settings=mode.equals("policy");
        if(market||print)graph(g,mx,my);
        else if(settings){text(g,"Політика емітента: відкладена зміна всіх параметрів",graphX,y+87,0xffe9bd68,graphW);for(int i=0;i<policy.length;i++)text(g,FIELDS[i],graphX+i%2*(graphW/2),y+111+i/2*59,0xffdcc8a5,graphW/2-16);wrap(g,"Зміна набирає чинності через 1 годину. Чинний мер/Банкір. Комісії обох емітентів додаються від спільної золотої суми. Мінімальне забезпечення: R / (M × 1 ЗО).",graphX,y+375,0xffcbb38d,graphW-8,8);}
        else if(mode.equals("log")){int py=y+89;var rows=array("journal");for(int i=Math.min(logOffset,Math.max(0,rows.size()-1));i<rows.size()&&py<y+h-95;i++){var row=rows.get(i).getAsJsonObject();py=wrap(g,"#"+text(row,"seq","")+" "+DATE.format(Instant.ofEpochMilli(row.get("at_time").getAsLong()))+" "+text(row,"event","")+(text(row,"actor_name","").isEmpty()?"":" — "+text(row,"actor_name","")),graphX,py,0xffe9bd68,graphW,2);py=wrap(g,text(row,"detail",""),graphX,py+3,0xffdcc8a5,graphW,Math.min(8,(y+h-95-py)/11));py+=14;}if(rows.isEmpty())text(g,"Записів поки немає",graphX,y+105,0xffdcc8a5,graphW);}
        else {int py=y+89;for(var raw:array("requests")){if(py>y+h-80)break;var row=raw.getAsJsonObject();String status=switch(text(row,"status","")){case "PENDING"->"У черзі";case "PAID"->"Виплачено";default->"Монети повернуто";};py=wrap(g,text(row,"id","")+" • "+status,graphX,py,0xffe9bd68,graphW,2);py=wrap(g,java.math.BigDecimal.valueOf(row.get("amount").getAsLong(),2).toPlainString()+" "+text(row,"code","")+"; min "+text(row,"min_gold","0")+" ЗО; виплата "+text(row,"paid","0")+" ЗО; "+DATE.format(Instant.ofEpochMilli(row.get("due").getAsLong()))+" "+text(row,"reason",""),graphX,py+3,0xffdcc8a5,graphW,3);py+=14;}}
        text(g,text(data,"issuer","Державна валюта"),sideX+10,y+46,0xffe9bd68,side-20);text(g,"Курс: "+text(data,"value","—")+" ЗО/мон.",sideX+10,y+63,0xfff2e4c8,side-20);text(g,"Казна: "+text(data,"treasury","—")+" • R="+text(data,"reserve","—"),sideX+10,y+80,0xffcbb38d,side-20);
        if(market||print){text(g,print?"Друк цілих монет (кожні "+text(data,"interval","—")+" с)":"Витратити "+label(from),sideX+10,y+108,0xfff2e4c8,side-20);if(market)text(g,"Мінімум до отримання (порожньо: −0.5%)",sideX+10,y+154,0xffdcc8a5,side-20);}
        int dy=y+235;
        if(market&&trade!=null){String[] details={"Отримаєте: "+text(trade,"output","—")+" "+label(to),"Комісії A + B: "+text(trade,"feeA","0")+" + "+text(trade,"feeB","0")+" ЗО","Спалення A / B: "+text(trade,"burnA","0")+" / "+text(trade,"burnB","0"),"Середній курс: "+text(trade,"average","—"),"Slippage AMM: "+text(trade,"slippage","—")+"%","Разом з комісіями: "+text(trade,"totalImpact","—")+"%","Після: 1 "+label(from)+" ≈ "+text(trade,"after","—")+" "+label(to),"Решта золота: "+text(trade,"change","0")};for(String detail:details)dy=wrap(g,detail,sideX+10,dy+4,0xffe9d9b9,side-20,2);}
        if(market){JsonObject balances=data.has("balances")?data.getAsJsonObject("balances"):new JsonObject();String balance=from.equals("@GOLD")?text(data,"gold","0"):text(balances,from,"0");dy=wrap(g,"Ваш баланс: "+balance+" "+label(from),sideX+10,dy+12,0xffcbb38d,side-20,2);if(data.has("liquidityA")){var liq=data.getAsJsonObject("liquidityA");dy=wrap(g,"Казна "+from+": "+text(liq,"stock","0")+" • R="+text(liq,"reserve","0")+" ЗО • ліміт 24г="+text(liq,"windowLeft","0"),sideX+10,dy+5,0xffcbb38d,side-20,3);}if(to.equals("@GOLD"))wrap(g,"Погашення: пакет через 10–20 хв. Спільний курс, пропорційна виплата; нижче min_out — повернення монет.",sideX+10,dy+5,0xffe9bd68,side-20,5);}
        if(print){if(preview!=null){dy=wrap(g,"Після друку: "+text(preview,"after","—")+" ЗО/мон.; час "+duration(text(preview,"seconds","0")),sideX+10,dy,0xffe9bd68,side-20,4);}if(data.has("job")){var job=data.getAsJsonObject("job");wrap(g,text(job,"status","")+": готово "+text(job,"issued","0")+"; залишок "+text(job,"remaining","0")+"; "+duration(text(job,"seconds","0")),sideX+10,dy+18,0xffdcc8a5,side-20,4);}}
        if(settings&&data.has("settings")){var s=data.getAsJsonObject("settings");int py=y+105;var active=s.getAsJsonObject("active");for(int i=0;i<8;i++)py=wrap(g,FIELDS[i]+": "+text(active,KEYS[i],""),sideX+10,py+4,0xffdcc8a5,side-20,2);if(s.has("pending")){var v=s.getAsJsonObject("pending");py=wrap(g,"Відкладено до "+DATE.format(Instant.ofEpochMilli(v.get("at").getAsLong()))+" • "+text(v,"actor",""),sideX+10,py+16,0xffe9bd68,side-20,3);var next=v.getAsJsonObject("settings");for(int i=0;i<8;i++)py=wrap(g,FIELDS[i]+": "+text(next,KEYS[i],""),sideX+10,py+2,0xffcbb38d,side-20,1);}}
        if(mode.equals("redeem"))wrap(g,"Обрана заявка: "+claim+". Скасування повертає всі її монети. Добова квота враховує також скасовані заявки.",sideX+10,y+250,0xffdcc8a5,side-20,8);
        text(g,requests.busy()?"Оновлення…":error.isEmpty()?notice:error,graphX,y+h-31,error.isEmpty()?0xffcbb38d:0xfff29e96,graphW);text(g,mode.equals("log")?"Колесо: прокрутити записи • Учасники обміну анонімні":"Графік: колесо — період • наведення — курс і час",graphX,y+h-17,0xffb49a72,graphW);
        var fields=new ArrayList<EditBox>(List.of(amount,minimum));fields.addAll(Arrays.asList(policy));for(var box:fields)if(box.visible){g.fill(box.getX()-4,box.getY()-4,box.getX()+box.getWidth()+4,box.getY()+box.getHeight()+2,0xe02b1f14);border(g,box.getX()-4,box.getY()-4,box.getWidth()+8,box.getHeight()+6,box.isFocused()?0xffe4bd75:0xff927044);}
        super.render(g,mx,my,partial);g.pose().popPose();
    }
    static String duration(String seconds){long n=Long.parseLong(seconds);return n>=86400?n/86400+" д "+n%86400/3600+" год":n>=3600?n/3600+" год "+n%3600/60+" хв":n>=60?n/60+" хв "+n%60+" с":n+" с";}
}
