package net.minecraft.client.gui.screens;
import net.minecraft.client.*; import net.minecraft.client.gui.*;import net.minecraft.client.gui.components.events.*;import net.minecraft.network.chat.*;
public abstract class Screen {
 public int f_96543_,f_96544_;protected Font f_96547_;protected Minecraft f_96541_;protected final Component f_96539_=null;
 protected Screen(Component c){} protected void m_7856_(){} public void m_88315_(GuiGraphics g,int x,int y,float t){} public void m_7379_(){}public void m_86600_(){}public boolean m_7043_(){return false;}public void m_280273_(GuiGraphics g){}
 protected <T extends GuiEventListener> T m_142416_(T t){return t;}
}
