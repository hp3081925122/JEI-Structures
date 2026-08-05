package org.hp.jei_structures.jei;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.TooltipFlag;
import org.hp.jei_structures.JeiStructures;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class BiomeIngredient implements IIngredientType<StructureBiomeIcon>, IIngredientHelper<StructureBiomeIcon>, IIngredientRenderer<StructureBiomeIcon> {

    public static final BiomeIngredient INSTANCE = new BiomeIngredient();
    private static final Identifier MISSING_SPRITE_ID = Identifier.fromNamespaceAndPath(JeiStructures.MODID, "biome_icon/missing");
    private static final Set<Identifier> LOGGED_SPRITES = ConcurrentHashMap.newKeySet();

    private BiomeIngredient() {
    }

    @Override
    public IIngredientType<StructureBiomeIcon> getIngredientType() {
        return this;
    }

    @Override
    public Class<? extends StructureBiomeIcon> getIngredientClass() {
        return StructureBiomeIcon.class;
    }

    @Override
    public String getDisplayName(StructureBiomeIcon biome) {
        return getBiomeComponent(biome).getString();
    }

    @Override
    public Object getUid(StructureBiomeIcon biome, UidContext uidContext) {
        Identifier id = getIdentifier(biome);
        return id != null ? id.toString() : "unknown";
    }

    @Override
    public Identifier getIdentifier(StructureBiomeIcon biome) {
        return biome != null ? biome.biomeId() : null;
    }

    @Override
    public StructureBiomeIcon copyIngredient(StructureBiomeIcon biome) {
        return biome;
    }

    @Override
    public String getErrorInfo(@Nullable StructureBiomeIcon biome) {
        return biome == null ? "unknown biome" : String.valueOf(getUid(biome, UidContext.Ingredient));
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, StructureBiomeIcon biome) {
        Identifier biomeId = getIdentifier(biome);
        if (biomeId == null) {
            return;
        }
        Identifier spriteId = Identifier.fromNamespaceAndPath(
                biomeId.getNamespace(),
                "biome_icon/" + biomeId.getPath()
        );
        TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(spriteId);
        if (sprite == atlas.missingSprite()) {
            sprite = atlas.getSprite(MISSING_SPRITE_ID);
            spriteId = MISSING_SPRITE_ID;
        }
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 1, 1, 14, 14);
        if (LOGGED_SPRITES.add(spriteId)) {
            JeiStructures.LOGGER.debug("Rendered biome atlas sprite: biome={}, sprite={}", biomeId, spriteId);
        }
    }

    @Override
    @Deprecated(forRemoval = true)
    public List<Component> getTooltip(StructureBiomeIcon biome, TooltipFlag tooltipFlag) {
        return List.of();
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, StructureBiomeIcon biome, TooltipFlag tooltipFlag) {
        tooltip.add(getBiomeComponent(biome));
        Identifier id = getIdentifier(biome);
        if (id != null) {
            tooltip.add(StructureTextHelper.component("jei_structures.tooltip.biome_id", id.toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (biome != null && !biome.dimensionIds().isEmpty()) {
            tooltip.add(StructureTextHelper.component("jei_structures.tooltip.biome_dimensions", StructureTextHelper.joinDimensionNames(biome.dimensionIds(), 6)));
        }
        if (biome != null && !biome.sourceSelectors().isEmpty()) {
            tooltip.add(StructureTextHelper.component("jei_structures.tooltip.biome_source", StructureTextHelper.joinRawValues(biome.sourceSelectors(), 4)).withStyle(ChatFormatting.GRAY));
        }
        if (id != null && Minecraft.getInstance().options.advancedItemTooltips) {
            tooltip.add(Component.literal(id.toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Component getBiomeComponent(StructureBiomeIcon biome) {
        Identifier id = INSTANCE.getIdentifier(biome);
        if (id == null) {
            return Component.literal("unknown");
        }
        return StructureTextHelper.getBiomeComponent(id.toString());
    }
}
