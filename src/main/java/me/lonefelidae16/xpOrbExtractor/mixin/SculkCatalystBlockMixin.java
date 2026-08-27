package me.lonefelidae16.xpOrbExtractor.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.lonefelidae16.xpOrbExtractor.XpOrbExtractor;
import me.lonefelidae16.xpOrbExtractor.XpOrbExtractorConfig;
import me.lonefelidae16.xpOrbExtractor.state.DrainResult;
import me.lonefelidae16.xpOrbExtractor.util.PlayerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.nbt.ByteTag;
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
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(SculkCatalystBlock.class)
public abstract class SculkCatalystBlockMixin extends ExtendBlockBehaviourMixin {
    @Unique
    private static final Set<UUID> XPORBEX$PENDING_PLAYERS = ConcurrentHashMap.newKeySet();
    @Unique
    private static final float XPORBEX$ENTITY_SPAWN_VELOCITY_MULTIPLIER = 0.25f;

    @Override
    protected InteractionResult xporbextractor$wrapUseItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, @Nullable Player player, InteractionHand hand, BlockHitResult hitResult, Operation<InteractionResult> original) {
        if (itemStack.is(Items.GLASS_BOTTLE) && XpOrbExtractor.config().bModEnabled && player != null) {
            final UUID playerUUID = UUID.fromString(player.getUUID().toString());
            final Vec3 blockToPlayerDir = player.position().subtract(Vec3.atBottomCenterOf(pos)).normalize();
            if (level instanceof ServerLevel serverLevel && XPORBEX$PENDING_PLAYERS.add(playerUUID)) {
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return PlayerUtil.getAndDecreaseXp(player);
                    } catch (Exception ex) {
                        XpOrbExtractor.LOGGER.error("An error occurred getting xp count", ex);
                    }
                    return DrainResult.EMPTY;
                }).thenAcceptAsync(result -> {
                    serverLevel.getServer().execute(() -> {
                        if (xporbextractor$trySpawnBottleEntity(serverLevel, pos, blockToPlayerDir, result)) {
                            try {
                                itemStack.consume(1, player);
                                player.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE));
                            } catch (Exception ex) {
                                XpOrbExtractor.LOGGER.error("An error occurred access to player", ex);
                            }
                            xporbextractor$onSucceedFeedback(serverLevel, pos);
                        } else {
                            xporbextractor$onFailureFeedback(serverLevel, pos);
                        }
                    });
                    XPORBEX$PENDING_PLAYERS.remove(playerUUID);
                });
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.SUCCESS;
        }

        return super.xporbextractor$wrapUseItemOn(itemStack, state, level, pos, player, hand, hitResult, original);
    }

    @Unique
    private static boolean xporbextractor$trySpawnBottleEntity(ServerLevel serverLevel, BlockPos pos, Vec3 toPlayerDirection, DrainResult drainResult) {
        if (drainResult.amount == 0) {
            return false;
        }

        final Vec3 velocity = toPlayerDirection.multiply(XPORBEX$ENTITY_SPAWN_VELOCITY_MULTIPLIER, XPORBEX$ENTITY_SPAWN_VELOCITY_MULTIPLIER, XPORBEX$ENTITY_SPAWN_VELOCITY_MULTIPLIER);
        final BlockPos spawnTarget;
        if (serverLevel.getBlockState(pos.above()).isAir()) {
            spawnTarget = pos.above();
        } else {
            BlockPos playerDirOffset = pos.offset((int) Math.round(toPlayerDirection.x), (int) Math.round(toPlayerDirection.y), (int) Math.round(toPlayerDirection.z));
            if (serverLevel.getBlockState(playerDirOffset).isAir()) {
                spawnTarget = playerDirOffset;
            } else {
                spawnTarget = xporbextractor$searchAirBlock(serverLevel, playerDirOffset).orElse(pos);
            }
        }
        if (!drainResult.bDepleted || XpOrbExtractor.config().depletion == XpOrbExtractorConfig.DrainDepletion.ALLOW) {
            final ItemStack toExtract = new ItemStack(Items.EXPERIENCE_BOTTLE);
            final CompoundTag tag = new CompoundTag();
            final ItemLore lore = new ItemLore(List.of(Component.translatable("item.xporbextractor.xp_amount_text", drainResult.amount)));
            tag.put(XpOrbExtractor.TAG_XP_AMOUNT, IntTag.valueOf(drainResult.amount));
            tag.put(XpOrbExtractor.TAG_HAS_XP_AMOUNT, ByteTag.valueOf(true));
            toExtract.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            toExtract.set(DataComponents.LORE, lore);

            final ItemEntity entity = new ItemEntity(serverLevel, spawnTarget.getX() + 0.5f, spawnTarget.getY() + 0.5f, spawnTarget.getZ() + 0.5f, toExtract, velocity.x, velocity.y, velocity.z);
            if (serverLevel.addFreshEntity(entity)) {
                return true;
            }
        }

        ExperienceOrb.award(serverLevel, Vec3.atCenterOf(spawnTarget), drainResult.amount);
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

    @Unique
    private static Optional<BlockPos> xporbextractor$searchAirBlock(ServerLevel serverLevel, BlockPos origin) {
        final BlockPos[] searcher = new BlockPos[]{origin.above(), origin.north(), origin.west(), origin.south(), origin.east(), origin.below()};
        return Arrays.stream(searcher).filter(pos -> serverLevel.getBlockState(pos).isAir()).findFirst();
    }
}
