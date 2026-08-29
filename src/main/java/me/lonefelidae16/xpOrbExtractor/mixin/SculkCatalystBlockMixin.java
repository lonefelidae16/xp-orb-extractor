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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
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
import java.util.stream.Stream;

@Mixin(SculkCatalystBlock.class)
public abstract class SculkCatalystBlockMixin extends ExtendBlockBehaviourMixin {
    @Unique
    private static final Set<UUID> XPORBEX$PENDING_PLAYERS = ConcurrentHashMap.newKeySet();
    @Unique
    private static final Set<BlockPos> XPORBEX$PENDING_POSITIONS = ConcurrentHashMap.newKeySet();
    @Unique
    private static final Vec3 XPORBEX$ENTITY_SPAWN_VELOCITY_MULTIPLIER = new Vec3(0.25f, 0.25f, 0.25f);
    @Unique
    private static final int XPORBEX$PARTICLE_SPAWN_PERIOD_TICKS = 12;
    @Unique
    private static final int XPORBEX$UNLOCK_COOLDOWN_TICKS = 60;

    @Override
    protected InteractionResult xporbextractor$wrapUseItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult, Operation<InteractionResult> original) {
        if (itemStack.is(Items.GLASS_BOTTLE) && XpOrbExtractor.config().bModEnabled) {
            if (level instanceof ServerLevel serverLevel) {
                final MinecraftServer server = serverLevel.getServer();
                final UUID playerUUID = UUID.fromString(player.getUUID().toString());
                final ResourceKey<Level> dimension = level.dimension();
                if (!XPORBEX$PENDING_PLAYERS.add(playerUUID)) {
                    return InteractionResult.TRY_WITH_EMPTY_HAND;
                }
                if (!XPORBEX$PENDING_POSITIONS.add(pos)) {
                    XPORBEX$PENDING_PLAYERS.remove(playerUUID);
                    return InteractionResult.TRY_WITH_EMPTY_HAND;
                }

                itemStack.consume(1, player);
                player.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE));

                CompletableFuture.supplyAsync(() -> {
                    return PlayerUtil.getAndDecreaseXp(player);
                }).thenAcceptAsync(result -> {
                    server.execute(() -> {
                        Player targetPlayer = server.getPlayerList().getPlayer(playerUUID);
                        ServerLevel targetLevel = Optional.ofNullable(server.getLevel(dimension)).orElse(server.overworld());
                        if (xporbextractor$trySpawnExpBottleEntity(targetLevel, pos, targetPlayer, result)) {
                            xporbextractor$onSucceedFeedback(targetLevel, pos);
                        } else {
                            xporbextractor$spawnDustParticle(targetLevel, Vec3.upFromBottomCenterOf(pos, 1.1f), 10, 0.2f);
                            targetLevel.playSound(null, pos, XpOrbExtractor.SoundEvents.XP_DRAIN_FAIL, SoundSource.BLOCKS);
                        }
                    });

                    for (int i = 1; i < XPORBEX$UNLOCK_COOLDOWN_TICKS / XPORBEX$PARTICLE_SPAWN_PERIOD_TICKS; ++i) {
                        XpOrbExtractor.schedule(server, i * XPORBEX$PARTICLE_SPAWN_PERIOD_TICKS, serverx -> {
                            ServerLevel targetLevel = Optional.ofNullable(serverx.getLevel(dimension)).orElse(serverx.overworld());
                            xporbextractor$spawnDustParticle(targetLevel, Vec3.atCenterOf(pos), 10, 0.6f);
                            for (BlockPos airPos : xporbextractor$searchAirBlock(targetLevel, pos).toArray(BlockPos[]::new)) {
                                xporbextractor$spawnDustParticle(targetLevel, Vec3.upFromBottomCenterOf(airPos, 0.3f), 3, 0.4f);
                            }
                        });
                    }

                    XpOrbExtractor.schedule(server, XPORBEX$UNLOCK_COOLDOWN_TICKS, serverx -> {
                        XPORBEX$PENDING_PLAYERS.remove(playerUUID);
                        XPORBEX$PENDING_POSITIONS.remove(pos);
                    });
                });
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        return super.xporbextractor$wrapUseItemOn(itemStack, state, level, pos, player, hand, hitResult, original);
    }

    @Unique
    private static boolean xporbextractor$trySpawnExpBottleEntity(ServerLevel serverLevel, BlockPos pos, @Nullable Player player, DrainResult drainResult) {
        final Optional<Vec3> playerPosition = Optional.ofNullable(player).map(Entity::position);
        final BlockPos spawnTarget;
        if (serverLevel.getBlockState(pos.above()).isAir()) {
            spawnTarget = pos.above();
        } else {
            Vec3 toPlayerDirection = playerPosition.map(plPos -> plPos.subtract(Vec3.atBottomCenterOf(pos)).normalize()).orElse(Vec3.Y_AXIS);
            BlockPos playerDirOffset = pos.offset((int) Math.round(toPlayerDirection.x), (int) Math.round(toPlayerDirection.y), (int) Math.round(toPlayerDirection.z));
            if (serverLevel.getBlockState(playerDirOffset).isAir()) {
                spawnTarget = playerDirOffset;
            } else {
                spawnTarget = xporbextractor$searchAirBlock(serverLevel, playerDirOffset).findFirst().orElse(pos);
            }
        }
        final Vec3 spawnTargetCenter = Vec3.atCenterOf(spawnTarget);
        final Vec3 spawnVelocity = playerPosition.map(plPos -> plPos.subtract(spawnTargetCenter)).orElse(Vec3.Y_AXIS).multiply(XPORBEX$ENTITY_SPAWN_VELOCITY_MULTIPLIER);
        if ((!drainResult.bDepleted && !drainResult.bFatal) || XpOrbExtractor.config().depletion == XpOrbExtractorConfig.DrainDepletion.BOTTLES) {
            final ItemStack targetItemStack = new ItemStack(Items.EXPERIENCE_BOTTLE);
            final CompoundTag tag = new CompoundTag();
            final ItemLore lore = new ItemLore(List.of(Component.translatable("item.xporbextractor.xp_amount_text", drainResult.amount)));
            tag.put(XpOrbExtractor.TAG_XP_AMOUNT, IntTag.valueOf(drainResult.amount));
            tag.put(XpOrbExtractor.TAG_HAS_XP_AMOUNT, ByteTag.valueOf(true));
            targetItemStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            targetItemStack.set(DataComponents.LORE, lore);

            return xporbextractor$spawnItemEntity(serverLevel, targetItemStack, spawnTargetCenter, spawnVelocity);
        } else {
            final ItemStack targetItemStack = new ItemStack(Items.GLASS_BOTTLE);
            if (player == null) {
                serverLevel.getChunkSource().addTicket(new Ticket(TicketType.PLAYER_SIMULATION, 1), serverLevel.getChunk(pos).getPos());
                xporbextractor$spawnItemEntity(serverLevel, targetItemStack, spawnTargetCenter, spawnVelocity);
            } else if (!player.getAbilities().instabuild) {
                player.getInventory().add(targetItemStack);
            }

            if (drainResult.amount > 0) {
                ExperienceOrb.award(serverLevel, spawnTargetCenter, drainResult.amount);
            }
            return false;
        }
    }

    @Unique
    private static boolean xporbextractor$spawnItemEntity(ServerLevel serverLevel, ItemStack toSpawn, Vec3 target, Vec3 velocity) {
        final ItemEntity entity = new ItemEntity(serverLevel, target.x, target.y, target.z, toSpawn, velocity.x, velocity.y, velocity.z);
        return serverLevel.addFreshEntity(entity);
    }

    @Unique
    private static void xporbextractor$onSucceedFeedback(ServerLevel serverLevel, BlockPos pos) {
        final BlockState blockState = serverLevel.getBlockState(pos);
        serverLevel.setBlock(pos, blockState.setValue(SculkCatalystBlock.PULSE, true), 3);
        serverLevel.scheduleTick(pos, blockState.getBlock(), 14);
        serverLevel.playSound(null, pos, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS);
    }

    @Unique
    private static void xporbextractor$spawnDustParticle(ServerLevel serverLevel, Vec3 target, int count, double distributionMultiplier) {
        Vec3 dist = new Vec3(distributionMultiplier, 0, distributionMultiplier);
        serverLevel.sendParticles(DustColorTransitionOptions.SCULK_TO_REDSTONE, target.x, target.y, target.z, count, dist.x, dist.y, dist.z, 0.15f);
    }

    @Unique
    private static Stream<BlockPos> xporbextractor$searchAirBlock(ServerLevel serverLevel, BlockPos origin) {
        final BlockPos[] searcher = new BlockPos[]{origin.above(), origin.north(), origin.west(), origin.south(), origin.east(), origin.below()};
        return Arrays.stream(searcher).filter(pos -> serverLevel.getBlockState(pos).isAir());
    }
}
