package exhibition.module.impl.combat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAppleGold;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemGlassBottle;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionHelper;
import net.minecraft.util.MathHelper;
import exhibition.event.Event;
import exhibition.event.RegisterEvent;
import exhibition.event.impl.EventMotionUpdate;
import exhibition.event.impl.EventPacket;
import exhibition.module.Module;
import exhibition.module.data.ModuleData;
import exhibition.module.data.MultiBool;
import exhibition.module.data.Setting;
import exhibition.util.RotationUtils;
import exhibition.util.misc.ChatUtil;
import exhibition.util.misc.Timer;

/*
 * Made by LeakedPvP
 */
public class AutoPot extends Module {

	public static final String HEALTH = "HEALTH";
	public static final String PREDICT = "PREDICT";
	private String TARGETING = "Potion";
    public AutoPot(ModuleData data) {   	   
        super(data);
        settings.put(HEALTH, new Setting(HEALTH, 6, "Maximum health before healing.", 0.5, 0.5, 10));
        settings.put(PREDICT, new Setting(PREDICT, false, "Predicts where to pot when moving."));
        List ents = Arrays.asList(new Setting("SPEED", true, "no desc"), new Setting("REGEN", false, "no desc."));
        this.settings.put(this.TARGETING, new Setting(this.TARGETING, new MultiBool("Active Potion", ents), "Properties the aura will target."));
        
    }

    public static boolean potting;
    Timer timer = new Timer();
    @Override
    public void onEnable() {
        super.onEnable();
    }
    public static boolean isPotting(){
    	return potting;
    }
    @Override
    @RegisterEvent(events = {EventMotionUpdate.class})
    public void onEvent(Event event) {
    	if(event instanceof EventMotionUpdate){
    		EventMotionUpdate em =(EventMotionUpdate)event;
            MultiBool multi = (MultiBool)((Setting)this.settings.get(this.TARGETING)).getValue();
            final boolean speed = ((Boolean)multi.getSetting("SPEED").getValue()).booleanValue();
            final boolean regen = ((Boolean)multi.getSetting("REGEN").getValue()).booleanValue();
            if(!em.isPre())
            	return;
            if(timer.check(200)){
            	if(potting)
            		potting = false;
            }
            int spoofSlot = getBestSpoofSlot();
            int pots[] = {6,-1,-1};
            if(regen)
            	pots[1] = 10;
            if(speed)
            	pots[2] = 1;
            
            for(int i = 0; i < pots.length; i ++){
            	if(pots[i] == -1)
            		continue;
            	if(pots[i] == 6 || pots[i] == 10){
            		if(timer.check(500) && !mc.thePlayer.isPotionActive(pots[i])){
                		if(mc.thePlayer.getHealth() < ((Number)((Setting) settings.get(HEALTH)).getValue()).doubleValue()*2){
                			getBestPot(spoofSlot, pots[i]);
                		}
            		}
            	}else
            	if(timer.check(1000) && !mc.thePlayer.isPotionActive(pots[i])){
            		getBestPot(spoofSlot, pots[i]);               		
            	}
            }       
    	}
    }
  
    public void swap(int slot1, int hotbarSlot){
    	mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slot1, hotbarSlot, 2, mc.thePlayer);
    }
    float[] getRotations(){    	
        double movedPosX = mc.thePlayer.posX + mc.thePlayer.motionX * 26.0D;
        double movedPosY = mc.thePlayer.getEntityBoundingBox().minY - 3.6D;
        double movedPosZ = mc.thePlayer.posZ + mc.thePlayer.motionZ * 26.0D;	
        if((Boolean) ((Setting) settings.get(PREDICT)).getValue())
        	return RotationUtils.getRotationFromPosition(movedPosX, movedPosZ, movedPosY);
        else
        	return new float[]{mc.thePlayer.rotationYaw, 90};
    }
    int getBestSpoofSlot(){  	
    	int spoofSlot = 5;
    	for (int i = 36; i < 45; i++) {       		
    		if (!mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) {
     			spoofSlot = i - 36;
     			break;
            }else if(mc.thePlayer.inventoryContainer.getSlot(i).getStack().getItem() instanceof ItemPotion) {
            	spoofSlot = i - 36;
     			break;
            }
        }
    	return spoofSlot;
    }
    void getBestPot(int hotbarSlot, int potID){
    	for (int i = 9; i < 45; i++) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).getHasStack() &&(mc.currentScreen == null || mc.currentScreen instanceof GuiInventory)) {
                ItemStack is = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
                if(is.getItem() instanceof ItemPotion){
              	  ItemPotion pot = (ItemPotion)is.getItem();
              	  if(pot.getEffects(is).isEmpty())
              		  return;
              	  PotionEffect effect = (PotionEffect) pot.getEffects(is).get(0);              	  
                  int potionID = effect.getPotionID();
                  if(potionID == potID)
              	  if(ItemPotion.isSplash(is.getItemDamage()) && isBestPot(pot, is)){
              		  if(36 + hotbarSlot != i)
              			  swap(i, hotbarSlot);
              		  timer.reset();
              		  boolean canpot = true;
              		  int oldSlot = mc.thePlayer.inventory.currentItem;
              		  mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(hotbarSlot));
          			  mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(getRotations()[0], getRotations()[1], mc.thePlayer.onGround));
          			  mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getCurrentItem()));
          			  mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(oldSlot));
          			  potting = true;
          			  break;
              	  }               	  
                }              
            }
        }
    }
    
    boolean isBestPot(ItemPotion potion, ItemStack stack){
    	if(potion.getEffects(stack) == null || potion.getEffects(stack).size() != 1)
    		return false;
        PotionEffect effect = (PotionEffect) potion.getEffects(stack).get(0);
        int potionID = effect.getPotionID();
        int amplifier = effect.getAmplifier(); 
        int duration = effect.getDuration();
    	for (int i = 9; i < 45; i++) {    		
            if (mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) {           	
                ItemStack is = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
                if(is.getItem() instanceof ItemPotion){
                	ItemPotion pot = (ItemPotion)is.getItem();
                	 if (pot.getEffects(is) != null) {
                         for (Object o : pot.getEffects(is)) {
                             PotionEffect effects = (PotionEffect) o;
                             int id = effects.getPotionID();
                             int ampl = effects.getAmplifier(); 
                             int dur = effects.getDuration();
                             if (id == potionID && ItemPotion.isSplash(is.getItemDamage())){
                            	 if(ampl > amplifier){
                            		 return false;
                            	 }else if (ampl == amplifier && dur > duration){
                            		 return false;
                            	 }
                             }                            
                         }
                     }
                }
            }
        }
    	return true;
    }
}
