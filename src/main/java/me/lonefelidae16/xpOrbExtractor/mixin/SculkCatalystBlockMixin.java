package me.lonefelidae16.xpOrbExtractor.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.lonefelidae16.xpOrbExtractor.XpOrbExtractor;
import me.lonefelidae16.xpOrbExtractor.XpOrbExtractorConfig;
import me.lonefelidae16.xpOrbExtractor.state.DrainResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SculkCatalystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;

@Mixin(SculkCatalystBlock.class)
public abstract class SculkCatalystBlockMixin extends ExtendBlockBehaviourMixin {
    @Unique
    private static final Set<UUID> XPORBEXT$PENDING_PLAYERS = new ConcurrentSkipListSet<>();

    @Override
    protected InteractionResult xporbextractor$wrapUseItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult, Operation<InteractionResult> original) {
        if (itemStack.is(Items.GLASS_BOTTLE) && XpOrbExtractor.config().bModEnabled) {
            if (level instanceof ServerLevel serverLevel) {
                if (XPORBEXT$PENDING_PLAYERS.add(player.getUUID())) {
                    CompletableFuture.supplyAsync(() -> {
                        return xporbextractor$getAndDecreaseXp(player);
                    }).thenAcceptAsync(result -> {
                        serverLevel.getServer().execute(() -> {
                            if (xporbextractor$trySpawnBottleEntity(serverLevel, pos, result)) {
                                itemStack.consume(1, player);
                                player.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE));
                                xporbextractor$onSucceedFeedback(serverLevel, pos);
                            } else {
                                xporbextractor$onFailureFeedback(serverLevel, pos);
                            }
                        });
                        XPORBEXT$PENDING_PLAYERS.remove(player.getUUID());
                    });
                }
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.SUCCESS;
        }

        return super.xporbextractor$wrapUseItemOn(itemStack, state, level, pos, player, hand, hitResult, original);
    }

    @Unique
    private static DrainResult xporbextractor$getAndDecreaseXp(Player player) {
        final int maxAmount = XpOrbExtractor.config().amountToDrain;
        final XpOrbExtractorConfig.DrainTarget drainTarget = XpOrbExtractor.config().drainTarget;
        if (maxAmount < 0 || drainTarget == null) {
            return DrainResult.EMPTY;
        }

        switch (drainTarget) {
            case LEVEL -> {
                int xp = 0;
                int drainedLevel = 0;
                while (drainedLevel < maxAmount && xp < Integer.MAX_VALUE && player.experienceLevel > 0) {
                    final int remaining = Integer.MAX_VALUE - xp;
                    final int toDrain = Math.min(player.getXpNeededForNextLevel(), remaining);
                    player.giveExperiencePoints(-toDrain);
                    xp += toDrain;
                    ++drainedLevel;
                }
                boolean bDepleted = drainedLevel < maxAmount && xp < Integer.MAX_VALUE;
                if (bDepleted) {
                    while (player.experienceProgress - 1f / player.getXpNeededForNextLevel() > 0f) {
                        player.giveExperiencePoints(-1);
                        ++xp;
                    }
                }
                return new DrainResult(xp, bDepleted);
            }
            case XP -> {
                int xp = 0;
                while (player.experienceLevel > 0 && xp < maxAmount) {
                    final int remaining = maxAmount - xp;
                    final int toDrain = Math.min(player.getXpNeededForNextLevel(), remaining);
                    player.giveExperiencePoints(-toDrain);
                    xp += toDrain;
                }
                if (xp < maxAmount) {
                    while (player.experienceProgress - 1f / player.getXpNeededForNextLevel() > 0f && xp < maxAmount) {
                        player.giveExperiencePoints(-1);
                        ++xp;
                    }
                }
                return new DrainResult(xp, xp < maxAmount);
            }
        }
        return DrainResult.EMPTY;
    }

    @Unique
    private static boolean xporbextractor$trySpawnBottleEntity(ServerLevel serverLevel, BlockPos pos, DrainResult drainResult) {
        if (drainResult.amount == 0) {
            return false;
        }

        if (!drainResult.bDepleted || XpOrbExtractor.config().depletion == XpOrbExtractorConfig.DrainDepletion.ALLOW) {
            final ItemStack toExtract = new ItemStack(Items.EXPERIENCE_BOTTLE);
            final CompoundTag tag = new CompoundTag();
            final ItemLore lore = new ItemLore(List.of(Component.translatable("item.xporbextractor.xp_amount_text", drainResult.amount)));
            tag.put(XpOrbExtractor.TAG_XP_AMOUNT, IntTag.valueOf(drainResult.amount));
            toExtract.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            toExtract.set(DataComponents.LORE, lore);

            final ItemEntity entity = new ItemEntity(serverLevel, pos.getX() + 0.5f, pos.getY() + 1f, pos.getZ() + 0.5f, toExtract, 0f, 0.25f, 0f);
            if (serverLevel.addFreshEntity(entity)) {
                return true;
            }
        }

        ExperienceOrb.award(serverLevel, Vec3.upFromBottomCenterOf(pos, 1.5f), drainResult.amount);
        return false;
    }

    @Unique
    private static void xporbextractor$onSucceedFeedback(ServerLevel serverLevel, BlockPos pos) {
        final BlockState blockState = serverLevel.getBlockState(pos);
        serverLevel.setBlock(pos, blockState.setValue(SculkCatalystBlock.PULSE, true), 3);
        serverLevel.scheduleTick(pos, blockState.getBlock(), 14);
        serverLevel.playSound(null, pos, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS);
    }

    @Unique
    private static void xporbextractor$onFailureFeedback(ServerLevel level, BlockPos pos) {
        level.sendParticles(DustColorTransitionOptions.SCULK_TO_REDSTONE, pos.getX() + 0.5f, pos.getY() + 1.1f, pos.getZ() + 0.5f, 8, 0.2f, 0f, 0.2f, 0.1f);
        level.playSound(null, pos, XpOrbExtractor.SoundEvents.XP_DRAIN_FAIL, SoundSource.BLOCKS);
    }
}
