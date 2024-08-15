package dev.compactmods.gander.runtime.baked.model;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Transformation;
import dev.compactmods.gander.render.baked.model.BakedMesh;
import dev.compactmods.gander.render.baked.model.DisplayableMesh;
import dev.compactmods.gander.render.baked.model.DisplayableMeshGroup;
import dev.compactmods.gander.render.baked.model.DisplayableMeshGroup.Mode;
import dev.compactmods.gander.runtime.baked.model.archetype.ArchetypeBaker;
import dev.compactmods.gander.render.baked.model.archetype.ArchetypeComponent;
import dev.compactmods.gander.render.baked.model.material.MaterialInstance;
import dev.compactmods.gander.render.baked.model.material.MaterialParent;
import dev.compactmods.gander.runtime.mixin.accessor.BlockModelShaperAccessor;
import dev.compactmods.gander.runtime.mixin.accessor.ModelBakeryAccessor;
import dev.compactmods.gander.runtime.mixin.accessor.ModelManagerAccessor;
import dev.compactmods.gander.runtime.mixin.accessor.MultiPartAccessor;
import dev.compactmods.gander.runtime.mixin.accessor.TransformationAccessor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import net.minecraft.client.renderer.block.model.multipart.Selector;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.NamedRenderTypeManager;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Rebakes meshes to not use an interleaved format for their data. Leaves the
 * original data as-is for mods which rely on the interleaved data for their own
 * purposes.
 */
public final class ModelRebaker
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelRebaker.class);

    private static final VarHandle _rebakerField;
    static
    {
        try
        {
            _rebakerField = MethodHandles.lookup()
                .findVarHandle(ModelManager.class, "modelRebaker", ModelRebaker.class);
        }
        catch (NoSuchFieldException | IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static ModelRebaker of(ModelManager manager)
    {
        return (ModelRebaker)_rebakerField.get(manager);
    }

    // Map of model -> parent instances
    private Map<DisplayableMesh, Set<MaterialInstance>> modelMaterialInstances;
    // Multimap of model -> baked mesh
    private Map<ModelResourceLocation, DisplayableMeshGroup> bakedModelMeshes;

    public DisplayableMeshGroup getArchetypes(ModelResourceLocation model)
    {
        return bakedModelMeshes.getOrDefault(model, DisplayableMeshGroup.of());
    }

    public Set<MaterialInstance> getMaterialInstances(DisplayableMesh mesh)
    {
        // This SHOULD be unmodifiable, but just in case...
        return Collections.unmodifiableSet(
            modelMaterialInstances.getOrDefault(
                mesh,
                Set.of()));
    }

    public void rebakeModels(ModelManagerAccessor manager, ProfilerFiller reloadProfiler)
    {
        try
        {
            var bakery = manager.getModelBakery();

            reloadProfiler.startTick();

            reloadProfiler.push("archetype_discovery");
            var archetypeSet = new HashSet<ModelResourceLocation>();
            var modelArchetypes = new HashMap<ModelResourceLocation, BiMap<ModelResourceLocation, UnbakedModel>>();
            for (var pair : manager.getBakedRegistry().entrySet())
            {
                if (LOGGER.isTraceEnabled())
                    LOGGER.trace("Discovering archetypes of model {} ", pair.getKey());

                reloadProfiler.push(pair.getKey().toString());
                var archetypes = ArchetypeBaker.getArchetypes(pair.getKey(), manager, bakery);
                modelArchetypes.put(pair.getKey(), archetypes);
                archetypeSet.addAll(archetypes.keySet());
                reloadProfiler.pop();
            }

            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Computed {} different archetype models", archetypeSet.size());

            reloadProfiler.popPush("archetype_baking");
            var archetypeMeshes = LinkedHashMultimap.<ModelResourceLocation, ModelResourceLocation>create();
            var bakedComponents = LinkedHashMultimap.<ModelResourceLocation, ArchetypeComponent>create();
            for (var archetype : archetypeSet)
            {
                var model = bakery.getModel(archetype.id());
                if (LOGGER.isTraceEnabled())
                    LOGGER.trace("Baking archetype model {} of model type {}", archetype, model.getClass());

                var components = ArchetypeBaker.bakeArchetypeComponents(archetype, model)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
                if (components.isEmpty())
                {
                    LOGGER.warn("Archetype model {} returned no archetype components?", archetype);
                    continue;
                }

                for (var component : components)
                {
                    bakedComponents.put(component.name(), component);
                    archetypeMeshes.put(archetype, component.name());
                }
            }

            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Baked {} different archetype meshes", archetypeMeshes.size());

            reloadProfiler.popPush("archetype_association");

            var reverseMap = ((BlockModelShaperAccessor)manager.getBlockModelShaper())
                .getModelByStateCache()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                    it -> BlockModelShaper.stateToModelLocation(it.getKey()),
                    Entry::getKey));

            var bakedModelMeshes = modelArchetypes.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                    it -> getDisplayMesh(
                        it.getKey(),
                        it.getValue(),
                        reverseMap.getOrDefault(it.getKey(), null),
                        bakery,
                        it.getValue().keySet().stream()
                            .map(archetypeMeshes::get)
                            .flatMap(Set::stream)
                            .map(bakedComponents::get)
                            .flatMap(Set::stream)
                            .collect(Collectors.toSet()))));

            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Associated {} unique models to {} display meshes ({} min {} avg {} max display meshes per model)",
                    bakedModelMeshes.keySet().size(),
                    bakedModelMeshes.size(),
                    bakedModelMeshes.values().stream().mapToLong(it -> it.allMeshes().count()).min().orElse(0),
                    bakedModelMeshes.values().stream().mapToLong(it -> it.allMeshes().count()).average().orElse(0),
                    bakedModelMeshes.values().stream().mapToLong(it -> it.allMeshes().count()).max().orElse(0));

            reloadProfiler.popPush("archetype_cache");
            this.bakedModelMeshes = Collections.unmodifiableMap(bakedModelMeshes);

            reloadProfiler.popPush("material_instances");
            var modelMaterials = new HashMap<DisplayableMesh, Set<MaterialInstance>>();
            for (var pair : bakedModelMeshes.entrySet())
            {
                if (LOGGER.isTraceEnabled())
                    LOGGER.trace("Baking material instances of model {}", pair.getKey());

                var unbakedModel = ((ModelBakeryAccessor)bakery).getTopLevelModels()
                    .get(pair.getKey());

                pair.getValue().allMeshes()
                    .flatMap(it -> getMaterialInstances(
                        bakery,
                        pair.getKey(),
                        unbakedModel,
                        it))
                    .forEach(it -> modelMaterials.put(it.getKey(), it.getValue()));
            }

            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Baked {} different material instances", modelMaterials.size());

            reloadProfiler.popPush("material_instances_cache");
            modelMaterialInstances = Collections.unmodifiableMap(modelMaterials);

            reloadProfiler.pop();
            reloadProfiler.endTick();
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to rebake models", e);
        }
    }

    // TODO: support custom UnbakedModel types?
    private Stream<Map.Entry<DisplayableMesh, Set<MaterialInstance>>> getMaterialInstances(
        ModelBakery bakery, ModelResourceLocation key,
        UnbakedModel model,
        DisplayableMesh component)
    {
        switch (model)
        {
            case BlockModel block ->
            {
                return Stream.of(getBlockModelMaterials(key, block, component));
            }
            case MultiPart multiPart ->
            {
                return multiPart.getMultiVariants()
                    .stream()
                    .map(MultiVariant::getVariants)
                    .flatMap(List::stream)
                    .map(Variant::getModelLocation)
                    .map(bakery::getModel)
                    .flatMap(it -> getMaterialInstances(bakery, key, it, component));
            }
            case MultiVariant multiVariant ->
            {
                return multiVariant.getVariants()
                    .stream()
                    .map(Variant::getModelLocation)
                    .map(bakery::getModel)
                    .flatMap(it -> getMaterialInstances(bakery, key, it, component));
            }
            default -> throw new IllegalStateException("Unexpected value: " + model);
        }
    }

    private Map.Entry<DisplayableMesh, Set<MaterialInstance>> getBlockModelMaterials(
        ModelResourceLocation name,
        final BlockModel model,
        final DisplayableMesh component)
    {
        Stream<Entry<String, Either<Material, String>>> materials = Stream.of();
        for (var mdl = model; mdl != null; mdl = mdl.parent)
        {
            materials = Stream.concat(materials, mdl.textureMap.entrySet().stream());
        }

        // All materials used by this model and its parents
        var fullMaterialMap = new HashSet<Map.Entry<String, Either<Material, String>>>();
        materials
            .sorted(Map.Entry.<String, Either<Material, String>>comparingByKey()
                .thenComparing((left, right) -> {
                    var leftMatOrRef = left.getValue();
                    var rightMatOrRef = right.getValue();

                    var result = leftMatOrRef.mapBoth(
                        leftMaterial -> rightMatOrRef.mapBoth(
                            rightMaterial -> 0,
                            rightRef -> -1
                        ),
                        leftRef -> rightMatOrRef.mapBoth(
                            rightMaterial -> 1,
                            rightRef -> 0
                        ));

                    return Either.unwrap(Either.unwrap(result));
                }))
            .forEach(fullMaterialMap::add);

        // All of the material parents pulled from the parent meshes
        // TODO: is .put here sane? What if two parts of an archetype use a name
        // in different ways?
        var parentsByName = new HashMap<String, MaterialParent>();
        getArchetypes(name)
            .allMeshes()
            .map(DisplayableMesh::mesh)
            .map(BakedMesh::materials)
            .flatMap(List::stream)
            .forEach(material -> {
                parentsByName.put(material.name(), material);
            });

        // All of the overriden material instances in this mesh
        // Any missing parents will be created, and the instance will be populated
        // based on this new parent.
        var overridenByName = new HashMap<String, MaterialInstance>();
        fullMaterialMap.stream()
            .map(it -> Pair.of(it.getKey(), it.getValue().left().orElse(null)))
            .filter(it -> it.getSecond() != null)
            .forEach(material -> {
                var missingFromParent = new MaterialParent(material.getFirst(),
                    material.getSecond().atlasLocation(),
                    material.getSecond().texture());
                var parent = parentsByName.putIfAbsent(
                    material.getFirst(),
                    missingFromParent);
                if (parent != null)
                {
                    // If the parent already exists, we override it with
                    // whatever texture we were supplied
                    overridenByName.put(
                        material.getFirst(),
                        new MaterialInstance(
                            material.getFirst(),
                            parent,
                            material.getSecond().texture()));
                }
                else
                {
                    // If it doesn't, we create a new material instance inheriting
                    // from the parent we just made, using the same name.
                    overridenByName.put(
                        missingFromParent.name(),
                        new MaterialInstance(
                            missingFromParent.name(),
                            missingFromParent,
                            null));
                }
            });

        // Now we have enough information to build the final set of material instances
        // from the full material map for this model
        var allMaterials = fullMaterialMap.stream()
            .map(it -> it.getValue()
                // If it's a direct material, we look for its instance in the
                // overriden material map where we made it
                .map(material -> Map.entry(it.getKey(),
                        overridenByName.get(it.getKey())),
                    // If it's a reference, we look for an overriden material
                    // by the original name, or create a new material instance
                    // referring to the parent we're referring to
                    ref -> Map.entry(it.getKey(),
                        overridenByName.getOrDefault(
                            it.getKey(),
                            new MaterialInstance(
                                it.getKey(),
                                parentsByName.getOrDefault(ref,
                                    MaterialParent.MISSING),
                                null)))))
            .filter(Objects::nonNull)
            .map(Map.Entry::getValue)
            .collect(Collectors.toSet());

        return Map.entry(component, allMaterials);
    }

    private DisplayableMeshGroup getDisplayMesh(
        ModelResourceLocation model,
        BiMap<ModelResourceLocation, UnbakedModel> archetypes,
        BlockState sourceBlockState,
        ModelBakery bakery,
        Set<ArchetypeComponent> components)
    {
        if (sourceBlockState == null)
        {
            LOGGER.warn("No matching blockstate for model {}", model);

            return components.stream()
                .map(component -> new DisplayableMesh(
                    component.name(),
                    component.bakedMesh(),
                    // TODO: is this the correct value? (Probably not)
                    RenderType.solid(),
                    new Matrix4f(),
                    1,
                    this::getMaterialInstances))
                .collect(Collectors.collectingAndThen(
                    Collectors.toList(),
                    // TODO: is All the correct value?
                    l -> DisplayableMeshGroup.ofMeshes(Mode.All, 1, l)));
        }

        var unbakedModel = ((ModelBakeryAccessor)bakery).getTopLevelModels().get(model);
        return getGroup(archetypes, sourceBlockState, bakery, model, unbakedModel, components);
    }

    // TODO: support custom UnbakedModel types?
    private DisplayableMeshGroup getGroup(
        BiMap<ModelResourceLocation, UnbakedModel> archetypes,
        BlockState sourceBlockState,
        ModelBakery bakery,
        ModelResourceLocation model,
        UnbakedModel unbakedModel,
        Collection<ArchetypeComponent> components)
    {
        switch (unbakedModel)
        {
            case null ->
            {
                LOGGER.error("Failed to get unbaked model {}", model);
                return components.stream()
                    .map(component -> new DisplayableMesh(
                        component.name(),
                        component.bakedMesh(),
                        getRenderType(component.renderType(), sourceBlockState),
                        new Matrix4f(),
                        1,
                        this::getMaterialInstances))
                    .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        l -> DisplayableMeshGroup.ofMeshes(Mode.All, 1, l)));
            }
            case BlockModel block ->
            {
                LOGGER.trace("Model {} is its own archetype?", model);
                return components.stream()
                    .map(component -> new DisplayableMesh(
                        component.name(),
                        component.bakedMesh(),
                        getRenderType(component.renderType(), sourceBlockState),
                        new Matrix4f(),
                        1,
                        this::getMaterialInstances))
                    .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        l -> DisplayableMeshGroup.ofMeshes(Mode.All, 1, l)));
            }
            case MultiPart multiPart ->
            {
                var accessor = (MultiPartAccessor)multiPart;
                return accessor.getSelectors()
                    .stream()
                    .<Map.Entry<BlockState, Selector>>mapMulti((selector, consumer) -> {
                        var predicate = selector.getPredicate(accessor.getDefinition());
                        if (predicate.test(sourceBlockState))
                            consumer.accept(Map.entry(sourceBlockState, selector));
                    })
                    .map(it -> Map.entry(it.getKey(), it.getValue().getVariant()))
                    .map(it -> getGroup(archetypes, it.getKey(), bakery, model, it.getValue(), components))
                    .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        x -> DisplayableMeshGroup.ofGroups(Mode.All, 1, x)));
            }
            case MultiVariant multiVariant ->
            {
                var modelToArchetype = archetypes.inverse();
                return multiVariant.getVariants()
                    .stream()
                    .map(it -> getVariantGroup(modelToArchetype, sourceBlockState, bakery, it, model, components))
                    .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        x -> DisplayableMeshGroup.ofGroups(Mode.Weighted, 1, x)));
            }
            default -> {
                LOGGER.error("Unknown unbaked model type {}", unbakedModel.getClass());
                return null;
            }
        }
    }

    private DisplayableMeshGroup getVariantGroup(
        BiMap<UnbakedModel, ModelResourceLocation> modelToArchetype,
        BlockState sourceBlockState,
        ModelBakery bakery,
        Variant variant,
        ModelResourceLocation model,
        Collection<ArchetypeComponent> components)
    {
        // TODO: as of 1.21 getModel can only return a BlockModel. This may change in the future...
        var variantModel = (BlockModel)bakery.getModel(variant.getModelLocation());
        while (variantModel.parent != null)
        {
            if (modelToArchetype.containsKey(variantModel))
                break;

            variantModel = variantModel.parent;
        }

        var archetypeModel = modelToArchetype.get(variantModel);
        return components.stream()
            .filter(p -> p.name().id().equals(archetypeModel.id()))
            .map(component -> new DisplayableMesh(
                component.name(),
                component.bakedMesh(),
                getRenderType(component.renderType(), sourceBlockState),
                ((TransformationAccessor)(Object)variant.getRotation()).gander$matrix(),
                variant.getWeight(),
                this::getMaterialInstances))
            .collect(Collectors.collectingAndThen(
                Collectors.toList(),
                l -> DisplayableMeshGroup.ofMeshes(Mode.All, variant.getWeight(), l)));
    }

    @SuppressWarnings("deprecation")
    private static RenderType getRenderType(ResourceLocation hint, BlockState state)
    {
        // If we have no hint, we have to fall back to the Vanilla map
        if (hint == null)
            return ItemBlockRenderTypes.getChunkRenderType(state);

        // If we do, pull it from the same source Neo does
        var group = NamedRenderTypeManager.get(hint);
        // If it doesn't exist... :ohno:
        if (group.isEmpty())
            return RenderType.solid();

        return group.block();
    }
}
