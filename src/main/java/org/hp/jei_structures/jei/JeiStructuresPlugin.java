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
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
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

@JeiPlugin
public final class JeiStructuresPlugin implements IModPlugin {

    private static volatile CachedRecipes cachedRecipes;
    private static volatile IJeiRuntime runtime;
    private static volatile StructureRecipeCategory category;
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
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(StructureRecipeCategory.TYPE, getSharedRecipes());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(StructureRecipeCategory.createStructureBlockStack(), StructureRecipeCategory.TYPE);
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.addTypedRecipeManagerPlugin(StructureRecipeCategory.TYPE, new StructureRecipeLookupPlugin());
    }

    // 保存 JEI 运行时对象，供当前结构按键打开对应配方页面。
    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        JeiStructures.LOGGER.debug("JEI runtime became available for current structure opener");
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        JeiStructures.LOGGER.debug("JEI runtime became unavailable for current structure opener");
    }

    // 根据当前结构 ID 打开 EMI 原生页面，失败时回退到 JEI 页面。
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
        if (ModList.get().isLoaded("emi") && EmiStructureRecipeOpener.open(matchedRecipes.get(0))) {
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
        JeiStructures.LOGGER.debug("Opened current structure recipe through JEI: {}", structureId);
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

        private StructureRecipeLookupPlugin() {
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
            return getLookup().recipes;
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
            for (StructureRecipe recipe : recipes) {
                addStacks(deduplicatedIndex, recipe.getLookupInputs(), recipe);
                addStacks(deduplicatedIndex, recipe.getLookupOutputs(), recipe);
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
