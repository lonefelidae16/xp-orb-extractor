package me.lonefelidae16.xpOrbExtractor;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

public class XpOrbExtractorCommands {
    private static final String COMMAND_XP_ORB_EXTRACTOR_SETTINGS = "xporbex";
    private static final String COMMAND_XP_ORB_STATUS = "status";
    private static final String COMMAND_XP_ORB_GET = "get";
    private static final String COMMAND_XP_ORB_SET = "set";
    private static final String COMMAND_XP_ORB_ADD = "add";
    private static final String COMMAND_XP_ORB_MOD = "mod";
    private static final String ARG_XP_ORB_DRAIN_ENABLED = "enabled";
    private static final String COMMAND_XP_ORB_DRAIN_TARGET = "target";
    private static final String ARG_XP_ORB_DRAIN_TARGET = "target";
    private static final String COMMAND_XP_ORB_DRAIN_AMOUNT = "amount";
    private static final String ARG_XP_ORB_DRAIN_AMOUNT = "amount";
    private static final String COMMAND_XP_ORB_DEPLETION = "depletion";
    private static final String ARG_XP_ORB_DEPLETION = "depletion";

    private static final XpOrbExtractorConfig CONFIG = XpOrbExtractor.config();

    private static final SuggestionProvider<CommandSourceStack> DRAIN_TARGET_SUGGESTION_PROVIDER = CommandUtil.makeEnumSuggester(XpOrbExtractorConfig.DrainTarget.class);
    private static final SuggestionProvider<CommandSourceStack> DRAIN_DEPLETION_SUGGESTION_PROVIDER = CommandUtil.makeEnumSuggester(XpOrbExtractorConfig.DrainDepletion.class);
    private static final SuggestionProvider<CommandSourceStack> FUNCTIONAL_SUGGESTION_PROVIDER = CommandUtil.makeEnumSuggester(FunctionalOp.class);

    public enum FunctionalOp {
        ENABLE,
        DISABLE,
    }

    public static void setup() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            dispatcher.register(
                    Commands.literal(COMMAND_XP_ORB_EXTRACTOR_SETTINGS)
                            .then(Commands.literal(COMMAND_XP_ORB_STATUS)
                                    .executes(XpOrbExtractorCommands.CommandOperation::printStatus)
                            )
                            .then(CommandUtil.buildGetCommands())
                            .then(CommandUtil.buildSetCommands())
                            .then(CommandUtil.buildAddCommands())
            );
        });
    }

    private static final class CommandUtil {
        private static SuggestionProvider<CommandSourceStack> makeEnumSuggester(Class<? extends Enum<?>> targetType) {
            return (context, builder) -> {
                for (var type : targetType.getFields()) {
                    builder.suggest(type.getName().toLowerCase(Locale.ROOT));
                }
                return builder.buildFuture();
            };
        }

        private static ArgumentBuilder<CommandSourceStack, ?> buildGetCommands() {
            return Commands.literal(COMMAND_XP_ORB_GET)
                    .then(Commands.literal(COMMAND_XP_ORB_DRAIN_TARGET)
                            .executes(XpOrbExtractorCommands.CommandOperation::printDrainTarget)
                    )
                    .then(Commands.literal(COMMAND_XP_ORB_DRAIN_AMOUNT)
                            .executes(XpOrbExtractorCommands.CommandOperation::printDrainAmount)
                    )
                    .then(Commands.literal(COMMAND_XP_ORB_DEPLETION)
                            .executes(XpOrbExtractorCommands.CommandOperation::printAllowDepletion)
                    );
        }

        private static ArgumentBuilder<CommandSourceStack, ?> buildSetCommands() {
            return Commands.literal(COMMAND_XP_ORB_SET)
                    .then(Commands.literal(COMMAND_XP_ORB_MOD)
                            .then(Commands.argument(ARG_XP_ORB_DRAIN_ENABLED, StringArgumentType.word()).suggests(FUNCTIONAL_SUGGESTION_PROVIDER)
                                    .executes(XpOrbExtractorCommands.CommandOperation::updateModEnabled)
                            )
                    )
                    .then(Commands.literal(COMMAND_XP_ORB_DRAIN_TARGET)
                            .then(Commands.argument(ARG_XP_ORB_DRAIN_TARGET, StringArgumentType.word()).suggests(DRAIN_TARGET_SUGGESTION_PROVIDER)
                                    .executes(XpOrbExtractorCommands.CommandOperation::updateDrainTarget)
                            )
                    )
                    .then(Commands.literal(COMMAND_XP_ORB_DRAIN_AMOUNT)
                            .then(Commands.argument(ARG_XP_ORB_DRAIN_AMOUNT, IntegerArgumentType.integer())
                                    .executes(context -> {
                                        final int amount = IntegerArgumentType.getInteger(context, ARG_XP_ORB_DRAIN_AMOUNT);
                                        return CommandOperation.updateDrainAmount(context, amount);
                                    })
                            )
                    )
                    .then(Commands.literal(COMMAND_XP_ORB_DEPLETION)
                            .then(Commands.argument(ARG_XP_ORB_DEPLETION, StringArgumentType.word()).suggests(DRAIN_DEPLETION_SUGGESTION_PROVIDER)
                                    .executes(XpOrbExtractorCommands.CommandOperation::updateDepletion)
                            )
                    );
        }

        private static ArgumentBuilder<CommandSourceStack, ?> buildAddCommands() {
            return Commands.literal(COMMAND_XP_ORB_ADD)
                    .then(Commands.literal(COMMAND_XP_ORB_DRAIN_AMOUNT)
                            .then(Commands.argument(ARG_XP_ORB_DRAIN_AMOUNT, IntegerArgumentType.integer())
                                    .executes(context -> {
                                        final int amount = IntegerArgumentType.getInteger(context, ARG_XP_ORB_DRAIN_AMOUNT);
                                        return CommandOperation.updateDrainAmount(context, (long) CONFIG.amountToDrain + amount);
                                    })
                            )
                    );
        }
    }

    private static final class CommandOperation {
        private static int updateDepletion(CommandContext<CommandSourceStack> context) {
            if (!CONFIG.bModEnabled) {
                return printStatus(context);
            }

            final String input = StringArgumentType.getString(context, ARG_XP_ORB_DEPLETION);
            XpOrbExtractorConfig.DrainDepletion target = null;
            try {
                target = XpOrbExtractorConfig.DrainDepletion.valueOf(input.toUpperCase(Locale.ROOT));
            } catch (Exception ignore) {
            }

            if (target == null) {
                context.getSource().sendFailure(Component.translatable("command.xporbextractor.fail.invalid_input", input));
            } else {
                CONFIG.depletion = target;
                XpOrbExtractorConfig.save(CONFIG);
                context.getSource().sendSuccess(() -> {
                    return Component.translatable("command.xporbextractor.text.mod_name")
                            .append(" - ")
                            .append(Component.translatable("command.xporbextractor.success.drain_depletion", CONFIG.depletion.asComponent()));
                }, false);
            }
            return Command.SINGLE_SUCCESS;
        }

        private static int printAllowDepletion(CommandContext<CommandSourceStack> context) {
            if (!CONFIG.bModEnabled) {
                return printStatus(context);
            }

            context.getSource().sendSuccess(() -> {
                return Component.translatable("command.xporbextractor.text.mod_name")
                        .append(" - ")
                        .append(Component.translatable("command.xporbextractor.details.allow_depletion", CONFIG.depletion.asComponent()));
            }, false);
            return Command.SINGLE_SUCCESS;
        }

        private static int updateDrainAmount(CommandContext<CommandSourceStack> context, long amount) {
            if (!CONFIG.bModEnabled) {
                return printStatus(context);
            }

            if (amount <= 0) {
                context.getSource().sendFailure(Component.translatable("command.xporbextractor.fail.drain_amount_negative"));
            } else if (amount > Integer.MAX_VALUE) {
                context.getSource().sendFailure(Component.translatable("command.xporbextractor.fail.drain_amount_too_high"));
            } else {
                CONFIG.amountToDrain = (int) amount;
                XpOrbExtractorConfig.save(CONFIG);
                context.getSource().sendSuccess(() -> {
                    return Component.translatable("command.xporbextractor.text.mod_name")
                            .append(" - ")
                            .append(Component.translatable("command.xporbextractor.success.drain_amount", amount));
                }, false);
            }
            return Command.SINGLE_SUCCESS;
        }

        private static int printDrainAmount(CommandContext<CommandSourceStack> context) {
            if (!CONFIG.bModEnabled) {
                return printStatus(context);
            }

            context.getSource().sendSuccess(() -> {
                return Component.translatable("command.xporbextractor.text.mod_name")
                        .append(" - ")
                        .append(Component.translatable("command.xporbextractor.details.amount", CONFIG.amountToDrain));
            }, false);
            return Command.SINGLE_SUCCESS;
        }

        private static int updateDrainTarget(CommandContext<CommandSourceStack> context) {
            if (!CONFIG.bModEnabled) {
                return printStatus(context);
            }

            final String input = StringArgumentType.getString(context, ARG_XP_ORB_DRAIN_TARGET);
            XpOrbExtractorConfig.DrainTarget target = null;
            try {
                target = XpOrbExtractorConfig.DrainTarget.valueOf(input.toUpperCase(Locale.ROOT));
            } catch (Exception ignore) {
            }

            if (target == null) {
                context.getSource().sendFailure(Component.translatable("command.xporbextractor.fail.invalid_input", input));
            } else {
                CONFIG.drainTarget = target;
                XpOrbExtractorConfig.save(CONFIG);
                context.getSource().sendSuccess(() -> {
                    return Component.translatable("command.xporbextractor.text.mod_name")
                            .append(" - ")
                            .append(Component.translatable("command.xporbextractor.success.drain_target", CONFIG.drainTarget.asComponent()));
                }, false);
            }
            return Command.SINGLE_SUCCESS;
        }

        private static int printDrainTarget(CommandContext<CommandSourceStack> context) {
            if (!CONFIG.bModEnabled) {
                return printStatus(context);
            }

            context.getSource().sendSuccess(() -> {
                return Component.translatable("command.xporbextractor.text.mod_name")
                        .append(" - ")
                        .append(Component.translatable("command.xporbextractor.details.drain_target", CONFIG.drainTarget.asComponent()));
            }, false);
            return Command.SINGLE_SUCCESS;
        }

        private static int updateModEnabled(CommandContext<CommandSourceStack> context) {
            final String input = StringArgumentType.getString(context, ARG_XP_ORB_DRAIN_ENABLED);
            FunctionalOp op = null;
            try {
                op = FunctionalOp.valueOf(input.toUpperCase(Locale.ROOT));
            } catch (Exception ignore) {
            }

            final boolean bEnabled = op == FunctionalOp.ENABLE;
            if (op == null) {
                context.getSource().sendFailure(Component.translatable("command.xporbextractor.fail.invalid_input", input));
            } else if (CONFIG.bModEnabled == bEnabled) {
                context.getSource().sendSuccess(() -> {
                    final Component workingState = bEnabled ? Component.translatable("command.xporbextractor.text.enabled") : Component.translatable("command.xporbextractor.text.disabled");
                    return Component.translatable("command.xporbextractor.text.mod_name")
                            .append(" - ")
                            .append(Component.translatable("command.xporbextractor.notice.same_value", workingState));
                }, false);
            } else {
                CONFIG.bModEnabled = bEnabled;
                XpOrbExtractorConfig.save(CONFIG);
                context.getSource().sendSuccess(() -> {
                    final Component workingState = bEnabled ? Component.translatable("command.xporbextractor.text.on") : Component.translatable("command.xporbextractor.text.off");
                    return Component.translatable("command.xporbextractor.text.mod_name")
                            .append(" ")
                            .append(Component.translatable("command.xporbextractor.mod.turns", workingState));
                }, false);
            }
            return Command.SINGLE_SUCCESS;
        }

        private static int printStatus(CommandContext<CommandSourceStack> context) {
            context.getSource().sendSuccess(() -> {
                final boolean bEnabled = CONFIG.bModEnabled;
                final Component workingState = bEnabled ? Component.translatable("command.xporbextractor.text.enabled") : Component.translatable("command.xporbextractor.text.disabled");
                final MutableComponent status = Component.translatable("command.xporbextractor.text.mod_name")
                        .append(" - ")
                        .append(workingState);
                if (bEnabled) {
                    status.append("\n");
                    status.append(Component.translatable("command.xporbextractor.details.drain_target", CONFIG.drainTarget.asComponent()));
                    status.append("\n");
                    status.append(Component.translatable("command.xporbextractor.details.amount", CONFIG.amountToDrain));
                    status.append("\n");
                    status.append(Component.translatable("command.xporbextractor.details.allow_depletion", CONFIG.depletion.asComponent()));
                }
                return status;
            }, false);
            return Command.SINGLE_SUCCESS;
        }
    }
}
