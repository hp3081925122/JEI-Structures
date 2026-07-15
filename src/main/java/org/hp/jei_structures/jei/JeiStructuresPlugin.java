package org.hp.jei_structures.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.hp.jei_structures.JeiStructures;
import org.hp.jei_structures.compat.EmiStructureRecipeOpener;
import org.hp.jei_structures.data.StructureIndexCache;
import org.hp.jei_structures.data.StructureIndexCacheLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

@JeiPlugin
public final class JeiStructuresPlugin implements IModPlugin {

    private static volatile CachedRecipes cachedRecipes;
    private static volatile IJeiRuntime runtime;
    private static volatile StructureRecipeCategory category;
    private static volatile StructureRecipeLookupPlugin lookupPlugin;
    private static final ExecutorService LOOKUP_WARMUP_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "JEI Structures Lookup Warmup");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final ResourceLocation pluginId = ResourceLocation.fromNamespaceAndPath(JeiStructures.MODID, "plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return pluginId;
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        List<StructureBiomeIcon> biomes = buildBiomeIngredients(StructureIndexCacheLoader.load());
        registration.register(BiomeIngredient.INSTANCE, biomes, BiomeIngredient.INSTANCE, BiomeIngredient.INSTANCE);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        StructureRecipeCategory structureCategory = new StructureRecipeCategory(registration.getJeiHelpers().getGuiHelper());
        category = structureCategory;
        registration.addRecipeCategories(structureCategory);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(StructureRecipeCategory.createStructureBlockStack(), StructureRecipeCategory.TYPE);
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        StructureRecipeLookupPlugin plugin = new StructureRecipeLookupPlugin();
        lookupPlugin = plugin;
        registration.addTypedRecipeManagerPlugin(StructureRecipeCategory.TYPE, plugin);
        JeiStructures.LOGGER.debug("Registered lazy structure recipe lookup plugin with {} recipes", getSharedRecipes().size());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        StructureRecipeLookupPlugin plugin = lookupPlugin;
        if (plugin != null) {
            plugin.prewarm();
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    public static boolean openStructureRecipe(String structureId) {
        if (structureId == null || structureId.isBlank()) {
            return false;
        }
        List<StructureRecipe> matchedRecipes = getSharedRecipes().stream()
                .filter(recipe -> structureId.equals(recipe.getStructureId()))
                .toList();
        if (matchedRecipes.isEmpty()) {
            JeiStructures.LOGGER.debug("Cannot open current structure recipe because it is not present in the client cache: {}", structureId);
            return false;
        }
        if (ModList.get().isLoaded("emi") && EmiStructureRecipeOpener.open(matchedRecipes.getFirst())) {
            return true;
        }
        IJeiRuntime currentRuntime = runtime;
        StructureRecipeCategory currentCategory = category;
        if (currentRuntime == null || currentCategory == null) {
            JeiStructures.LOGGER.debug(
                    "Cannot open current structure recipe through JEI: runtimeAvailable={}, categoryAvailable={}, structureId={}",
                    currentRuntime != null,
                    currentCategory != null,
                    structureId
            );
            return false;
        }
        currentRuntime.getRecipesGui().showRecipes(currentCategory, matchedRecipes, List.of());
        return true;
    }

    public static List<StructureRecipe> getSharedRecipes() {
        StructureIndexCache cache = StructureIndexCacheLoader.load();
        CachedRecipes snapshot = cachedRecipes;
        if (snapshot != null && snapshot.sourceCache == cache) {
            return snapshot.recipes;
        }
        synchronized (JeiStructuresPlugin.class) {
            snapshot = cachedRecipes;
            if (snapshot == null || snapshot.sourceCache != cache) {
                cachedRecipes = new CachedRecipes(cache, buildRecipes(cache));
            }
            return cachedRecipes.recipes;
        }
    }

    private static List<StructureRecipe> buildRecipes(StructureIndexCache cache) {
        List<StructureRecipe> recipes = new ArrayList<>();
        for (StructureIndexCache.StructureEntry entry : cache.structures) {
            recipes.add(new StructureRecipe(entry));
        }
        return List.copyOf(recipes);
    }

    private static List<StructureBiomeIcon> buildBiomeIngredients(StructureIndexCache cache) {
        Map<ResourceLocation, List<String>> biomeDimensions = new LinkedHashMap<>();
        for (StructureIndexCache.StructureEntry entry : cache.structures) {
            addBiomeIds(biomeDimensions, entry.resolvedGenerationBiomes, entry.generationBiomeDimensions);
            addBiomeIds(biomeDimensions, entry.generationBiomes, entry.generationBiomeDimensions);
        }
        return biomeDimensions.entrySet().stream()
                .map(entry -> new StructureBiomeIcon(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static void addBiomeIds(Map<ResourceLocation, List<String>> biomeDimensions, List<String> rawIds, Map<String, List<String>> dimensionsByBiome) {
        for (String rawId : rawIds) {
            if (rawId == null || rawId.isBlank() || rawId.startsWith("#")) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            if (id != null) {
                biomeDimensions.putIfAbsent(id, dimensionsByBiome.getOrDefault(rawId, List.of()));
            }
        }
    }

    private record CachedRecipes(StructureIndexCache sourceCache, List<StructureRecipe> recipes) {
    }

    private static final class StructureRecipeLookupPlugin implements ISimpleRecipeManagerPlugin<StructureRecipe> {

        private volatile CachedLookup cachedLookup;
        private volatile CompletableFuture<CachedLookup> warmingLookup;

        private StructureRecipeLookupPlugin() {
        }

        private void prewarm() {
            List<StructureRecipe> recipes = getSharedRecipes();
            synchronized (this) {
                if (cachedLookup != null && cachedLookup.recipes == recipes) {
                    return;
                }
                CompletableFuture<CachedLookup> currentWarmup = warmingLookup;
                if (currentWarmup == null || currentWarmup.isDone()) {
                    warmingLookup = CompletableFuture.supplyAsync(
                            () -> new CachedLookup(recipes, indexRecipes(recipes)),
                            LOOKUP_WARMUP_EXECUTOR
                    ).whenComplete((lookup, exception) -> {
                        if (exception == null) {
                            cachedLookup = lookup;
                            JeiStructures.LOGGER.debug("Finished background structure recipe lookup warmup with {} recipes", recipes.size());
                        } else {
                            JeiStructures.LOGGER.error("Failed to prewarm structure recipe lookup", exception);
                        }
                    });
                }
            }
        }

        @Override
        public boolean isHandledInput(ITypedIngredient<?> ingredient) {
            return getMatchedRecipes(ingredient) != null;
        }

        @Override
        public boolean isHandledOutput(ITypedIngredient<?> ingredient) {
            return getMatchedRecipes(ingredient) != null;
        }

        @Override
        public List<StructureRecipe> getRecipesForInput(ITypedIngredient<?> ingredient) {
            List<StructureRecipe> recipes = getMatchedRecipes(ingredient);
            return recipes != null ? recipes : List.of();
        }

        @Override
        public List<StructureRecipe> getRecipesForOutput(ITypedIngredient<?> ingredient) {
            List<StructureRecipe> recipes = getMatchedRecipes(ingredient);
            return recipes != null ? recipes : List.of();
        }

        @Override
        public List<StructureRecipe> getAllRecipes() {
            return getSharedRecipes();
        }

        private List<StructureRecipe> getMatchedRecipes(ITypedIngredient<?> ingredient) {
            CachedLookup lookup = getLookup();
            return ingredient.getItemStack()
                    .map(ItemStack::getItem)
                    .map(lookup.recipesByItem::get)
                    .orElse(null);
        }

        private CachedLookup getLookup() {
            List<StructureRecipe> recipes = getSharedRecipes();
            CachedLookup snapshot = cachedLookup;
            if (snapshot != null && snapshot.recipes == recipes) {
                return snapshot;
            }
            CompletableFuture<CachedLookup> warmup = warmingLookup;
            if (warmup != null) {
                try {
                    snapshot = warmup.join();
                    if (snapshot.recipes == recipes) {
                        return snapshot;
                    }
                } catch (RuntimeException exception) {
                    JeiStructures.LOGGER.error("Failed to wait for structure recipe lookup warmup", exception);
                }
            }
            synchronized (this) {
                snapshot = cachedLookup;
                if (snapshot == null || snapshot.recipes != recipes) {
                    cachedLookup = new CachedLookup(recipes, indexRecipes(recipes));
                }
                return cachedLookup;
            }
        }

        private static Map<Item, List<StructureRecipe>> indexRecipes(List<StructureRecipe> recipes) {
            Map<Item, Set<StructureRecipe>> deduplicatedIndex = new IdentityHashMap<>();
            for (int index = 0; index < recipes.size(); index++) {
                StructureRecipe recipe = recipes.get(index);
                addStacks(deduplicatedIndex, recipe.getLookupInputsForIndex(), recipe);
                if ((index + 1) % 32 == 0) {
                    LockSupport.parkNanos(1_000_000L);
                }
            }
            Map<Item, List<StructureRecipe>> index = new IdentityHashMap<>();
            for (Map.Entry<Item, Set<StructureRecipe>> entry : deduplicatedIndex.entrySet()) {
                index.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return Collections.unmodifiableMap(index);
        }

        private static void addStacks(Map<Item, Set<StructureRecipe>> index, List<ItemStack> stacks, StructureRecipe recipe) {
            for (ItemStack stack : stacks) {
                index.computeIfAbsent(stack.getItem(), item -> new LinkedHashSet<>()).add(recipe);
            }
        }

        private record CachedLookup(List<StructureRecipe> recipes, Map<Item, List<StructureRecipe>> recipesByItem) {
        }
    }
}
