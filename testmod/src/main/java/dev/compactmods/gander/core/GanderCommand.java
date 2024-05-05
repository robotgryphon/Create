package dev.compactmods.gander.core;

import java.util.Collection;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import dev.compactmods.gander.level.VirtualChunkGenerator;
import dev.compactmods.gander.level.VirtualLevel;
import dev.compactmods.gander.network.OpenGanderUiForDeferredStructureRequest;

import dev.compactmods.gander.network.OpenGanderUiForStructureRequest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class GanderCommand {
	public static final SuggestionProvider<CommandSourceStack> ANY_STRUCTURE = (ctx, builder)
			-> SharedSuggestionProvider.suggestResource(ctx.getSource().getServer().getStructureManager().listTemplates(), builder);

	public static LiteralArgumentBuilder<CommandSourceStack> make() {
		var root = Commands.literal("gander")
				.requires(cs -> cs.hasPermission(0));

		var scene = Commands.literal("scene")
				.then(Commands.argument("scene", ResourceLocationArgument.id())
						.suggests(ANY_STRUCTURE)
						.executes(ctx -> openTemplateScene(ResourceLocationArgument.getId(ctx, "scene"), ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("targets", EntityArgument.players())
								.requires(cs -> cs.hasPermission(2))
								.executes(ctx -> openTemplateScene(ResourceLocationArgument.getId(ctx, "scene"), EntityArgument.getPlayers(ctx, "targets")))
						)
				);

		var structure = Commands.literal("structure")
				.then(Commands.argument("structure", ResourceKeyArgument.key(Registries.STRUCTURE))
						.executes(ctx -> openStructureScene(ctx, ResourceKeyArgument.getStructure(ctx, "structure"), ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("targets", EntityArgument.players())
								.requires(cs -> cs.hasPermission(2))
								.executes(ctx -> openTemplateScene(ResourceLocationArgument.getId(ctx, "scene"), EntityArgument.getPlayers(ctx, "targets")))
						)
				);

		root.then(scene);
		root.then(structure);
		return root;
	}

	private static int openTemplateScene(ResourceLocation sceneId, ServerPlayer player) {
		return openTemplateScene(sceneId, ImmutableList.of(player));
	}

	private static int openTemplateScene(ResourceLocation sceneId, Collection<? extends ServerPlayer> players) {
		for (ServerPlayer player : players) {
			if (player instanceof FakePlayer)
				continue;

			PacketDistributor.sendToPlayer(player, new OpenGanderUiForDeferredStructureRequest(sceneId));
		}

		return Command.SINGLE_SUCCESS;
	}

	private static int openStructureScene(CommandContext<CommandSourceStack> ctx, Holder.Reference<Structure> key, ServerPlayer player) {
		return openStructureScene(ctx, key, ImmutableList.of(player));
	}

	private static int openStructureScene(CommandContext<CommandSourceStack> ctx, Holder.Reference<Structure> key, Collection<ServerPlayer> players) {
		for (ServerPlayer player : players) {
			if (player instanceof FakePlayer)
				continue;

			final var server = ctx.getSource().getServer();

			RegistryAccess regAccess = ctx.getSource().registryAccess();

			VirtualLevel level = new VirtualLevel(regAccess);

			final var biomeReg = regAccess
					.registry(Registries.BIOME)
					.get();

			final var theVoid = biomeReg.getHolderOrThrow(Biomes.THE_VOID);
			final var biomeSource = new FixedBiomeSource(theVoid);
			final var chunkGenerator = new VirtualChunkGenerator(biomeSource);

			final var structureStart = key.value().generate(
					regAccess,
					chunkGenerator,
					biomeSource,
					RandomState.create(NoiseGeneratorSettings.dummy(), regAccess.lookupOrThrow(Registries.NOISE), 0),
					server.getStructureManager(),
					0,
					ChunkPos.ZERO,
					0,
					level,
					p_214580_ -> true
			);

			if (!structureStart.isValid()) {
				return -1;
			}

			// GENERATE
			BoundingBox boundingbox = structureStart.getBoundingBox();
			ChunkPos startChunk = new ChunkPos(SectionPos.blockToSectionCoord(boundingbox.minX()), SectionPos.blockToSectionCoord(boundingbox.minZ()));
			ChunkPos endChunk = new ChunkPos(SectionPos.blockToSectionCoord(boundingbox.maxX()), SectionPos.blockToSectionCoord(boundingbox.maxZ()));

			ChunkPos.rangeClosed(startChunk, endChunk)
					.forEach(
							chunkPos -> structureStart.placeInChunk(
									level,
									server.overworld().structureManager(),
									chunkGenerator,
									level.getRandom(),
									new BoundingBox(
											chunkPos.getMinBlockX(),
											level.getMinBuildHeight(),
											chunkPos.getMinBlockZ(),
											chunkPos.getMaxBlockX(),
											level.getMaxBuildHeight(),
											chunkPos.getMaxBlockZ()
									),
									chunkPos
							)
					);

			final var finalStructure = new StructureTemplate();
			finalStructure.fillFromWorld(level, BlockPos.ZERO, boundingbox.getLength(), false, null);

			PacketDistributor.sendToPlayer(player, new OpenGanderUiForStructureRequest(Component.literal("Generated: " + key.key().location()), finalStructure));
		}

		return Command.SINGLE_SUCCESS;
	}
}
