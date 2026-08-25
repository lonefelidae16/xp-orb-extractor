package me.lonefelidae16.xpOrbExtractor.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockBehaviour.class)
public abstract class ExtendBlockBehaviourMixin {
    @WrapMethod(method = "useItemOn")
    protected InteractionResult xporbextractor$wrapUseItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult, Operation<InteractionResult> original) {
        return original.call(itemStack, state, level, pos, player, hand, hitResult);
    }
}
