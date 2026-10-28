package ua.empirecraft.shipcannons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import rbasamoyai.createbigcannons.cannon_control.carriage.CannonCarriageEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.shipcannons.*;

@Pseudo
@Mixin(targets="rbasamoyai.createbigcannons.cannon_control.carriage.CannonCarriageEntity",remap=false)
public abstract class CarriageMixin implements CarriageAccess {
    @Unique private static final EntityDataAccessor<Float> empirecraft$kickX=SynchedEntityData.defineId(CannonCarriageEntity.class,EntityDataSerializers.FLOAT);
    @Unique private static final EntityDataAccessor<Float> empirecraft$kickY=SynchedEntityData.defineId(CannonCarriageEntity.class,EntityDataSerializers.FLOAT);
    @Unique private static final EntityDataAccessor<Float> empirecraft$kickZ=SynchedEntityData.defineId(CannonCarriageEntity.class,EntityDataSerializers.FLOAT);
    @Unique private static final EntityDataAccessor<Integer> empirecraft$kickSequence=SynchedEntityData.defineId(CannonCarriageEntity.class,EntityDataSerializers.INT);
    @Unique private int empirecraft$seenKick;
    @Unique private ShipRaycast.Hit empirecraft$deck;
    @Unique private Vec3 empirecraft$inputBefore=Vec3.ZERO,empirecraft$recoil=Vec3.ZERO;
    @Unique private Long empirecraft$previousShip;
    @Inject(method="m_8097_()V",at=@At("RETURN"))
    private void empirecraft$syncRecoil(CallbackInfo ci) {
        var data=((Entity)(Object)this).getEntityData();
        data.define(empirecraft$kickX,0f);data.define(empirecraft$kickY,0f);data.define(empirecraft$kickZ,0f);data.define(empirecraft$kickSequence,0);
    }
    @Inject(method="m_8119_()V",at=@At("HEAD"))
    private void empirecraft$deck(CallbackInfo ci) {
        var self=(Entity)(Object)this;empirecraft$deck=CarriageDeck.support(self);
        var data=self.getEntityData();int sequence=data.get(empirecraft$kickSequence);
        if(sequence!=empirecraft$seenKick) {
            empirecraft$seenKick=sequence;
            empirecraft$recoil=empirecraft$recoil.add(data.get(empirecraft$kickX),data.get(empirecraft$kickY),data.get(empirecraft$kickZ));
            if(empirecraft$recoil.lengthSqr()>.18*.18)empirecraft$recoil=empirecraft$recoil.normalize().scale(.18);
        }
        CarriageDeck.refresh(self,empirecraft$deck,empirecraft$previousShip);
        empirecraft$previousShip=empirecraft$deck==null?null:empirecraft$deck.ship().getId();
        if(empirecraft$deck==null)empirecraft$recoil=Vec3.ZERO;
    }
    @Inject(method="moveCarriage",at=@At("HEAD"),cancellable=true)
    private void empirecraft$relativeMovement(CallbackInfo ci) {
        if(empirecraft$deck==null)return;
        Entity self=(Entity)(Object)this;var v=self.getDeltaMovement();var n=empirecraft$deck.normal();
        var velocity=DeckMath.contactVelocity(new Vector3d(v.x,v.y,v.z),new Vector3d(n.x,n.y,n.z));
        var kick=DeckMath.tangent(new Vector3d(empirecraft$recoil.x,empirecraft$recoil.y,empirecraft$recoil.z),new Vector3d(n.x,n.y,n.z));
        self.setDeltaMovement(new Vec3(velocity.x+kick.x,velocity.y+kick.y,velocity.z+kick.z));
        empirecraft$recoil=empirecraft$recoil.scale(.6);ci.cancel();
    }
    @Inject(method="controlCarriage",at=@At("HEAD"))
    private void empirecraft$beforeInput(CallbackInfo ci) {empirecraft$inputBefore=((Entity)(Object)this).getDeltaMovement();}
    @Inject(method="controlCarriage",at=@At("RETURN"))
    private void empirecraft$deckInput(CallbackInfo ci) {
        if(empirecraft$deck==null)return;
        Entity self=(Entity)(Object)this;Vec3 delta=self.getDeltaMovement().subtract(empirecraft$inputBefore),n=empirecraft$deck.normal();
        var v=DeckMath.input(new Vector3d(delta.x,delta.y,delta.z),new Vector3d(n.x,n.y,n.z));
        self.setDeltaMovement(empirecraft$inputBefore.add(v.x,v.y,v.z));
    }
    @Override public void empirecraft$addRecoil(Vec3 kick) {
        var data=((Entity)(Object)this).getEntityData();data.set(empirecraft$kickX,(float)kick.x);data.set(empirecraft$kickY,(float)kick.y);data.set(empirecraft$kickZ,(float)kick.z);
        data.set(empirecraft$kickSequence,data.get(empirecraft$kickSequence)+1);
    }
    @WrapOperation(method={"resetContraptionToOffset","m_6096_"},at=@At(value="INVOKE",target="Lrbasamoyai/createbigcannons/cannon_control/carriage/CannonCarriageEntity;m_20183_()Lnet/minecraft/core/BlockPos;"))
    private BlockPos empirecraft$deckBlockPosition(CannonCarriageEntity carriage,Operation<BlockPos> original) {
        var contact=CarriageDeck.support(carriage);
        return contact==null?original.call(carriage):BlockPos.containing(ShipRaycast.localPoint(contact.ship(),carriage.position()));
    }
    @WrapOperation(method={"resetContraptionToOffset","m_6096_"},at=@At(value="INVOKE",target="Lrbasamoyai/createbigcannons/cannon_control/carriage/CannonCarriageEntity;m_6350_()Lnet/minecraft/core/Direction;"))
    private Direction empirecraft$deckFacing(CannonCarriageEntity carriage,Operation<Direction> original) {
        Direction facing=original.call(carriage);var contact=CarriageDeck.support(carriage);if(contact==null)return facing;
        return Direction.fromYRot(DeckMath.localYaw(contact.ship().getTransform().getShipToWorldRotation(),carriage.getYRot()));
    }
}
