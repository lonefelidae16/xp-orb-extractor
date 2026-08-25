package me.lonefelidae16.xpOrbExtractor.mixin;

import me.lonefelidae16.xpOrbExtractor.XpOrbExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ThrownExperienceBottle.class)
public abstract class ThrownExperienceBottleMixin extends ThrowableItemProjectile {
    public ThrownExperienceBottleMixin(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    @ModifyVariable(method = "onHit", at = @At("STORE"), name = "xpCount")
    private int xporbextractor$modifyXpCount(int original) {
        try {
            if (this.level() instanceof ServerLevel) {
                ItemStack stack = this.getEntityData().get(ThrowableItemProjectileAccessor.xporbextractor$accessDataItemStack());
                if (stack.is(Items.EXPERIENCE_BOTTLE) && stack.has(DataComponents.CUSTOM_DATA)) {
                    return stack.get(DataComponents.CUSTOM_DATA).copyTag().get(XpOrbExtractor.TAG_XP_AMOUNT).asInt().orElseThrow();
                }
            }
        } catch (Exception ignore) {
        }

        return original;
    }
}
