package me.lonefelidae16.xpOrbExtractor.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ThrowableItemProjectile.class)
public interface ThrowableItemProjectileAccessor {
    @Accessor("DATA_ITEM_STACK")
    static EntityDataAccessor<ItemStack> xporbextractor$accessDataItemStack() {
        throw new RuntimeException("Can not access to ThrowableItemProjectile#DATA_ITEM_STACK");
    }
}
