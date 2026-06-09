package org.hp.jei_structures.jei;

import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import org.hp.jei_structures.JeiStructures;

import java.util.List;

public final class BiomeIngredient implements IIngredientType<StructureBiomeIcon>, IIngredientHelper<StructureBiomeIcon>, IIngredientRenderer<StructureBiomeIcon> {

    public static final BiomeIngredient INSTANCE = new BiomeIngredient();
    private static final ResourceLocation MISSING_SPRITE_ID = ResourceLocation.fromNamespaceAndPath(JeiStructures.MODID, "biome_icon/missing");

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
    public String getUniqueId(StructureBiomeIcon biome, UidContext uidContext) {
        ResourceLocation id = getResourceLocation(biome);
        return id != null ? id.toString() : "unknown";
    }

    @Override
    public ResourceLocation getResourceLocation(StructureBiomeIcon biome) {
        return biome != null ? biome.biomeId() : null;
    }

    @Override
    public StructureBiomeIcon copyIngredient(StructureBiomeIcon biome) {
        return biome;
    }

    @Override
    public String getErrorInfo(@Nullable StructureBiomeIcon biome) {
        return biome == null ? "unknown biome" : getUniqueId(biome, UidContext.Ingredient);
    }

    @Override
    public void render(GuiGraphics guiGraphics, StructureBiomeIcon biome) {
        TextureAtlasSprite sprite;
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 150.0F);
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        ResourceLocation id = getResourceLocation(biome);
        if (id == null) {
            sprite = atlas.getSprite(MISSING_SPRITE_ID);
        } else {
            sprite = atlas.getSprite(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "biome_icon/" + id.getPath()));
            if (MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name())) {
                sprite = atlas.getSprite(MISSING_SPRITE_ID);
            }
        }
        guiGraphics.blit(0, 0, 0, 16, 16, sprite);
        pose.popPose();
    }

    @Override
    @Deprecated(forRemoval = true)
    public List<Component> getTooltip(StructureBiomeIcon biome, TooltipFlag tooltipFlag) {
        return List.of();
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, StructureBiomeIcon biome, TooltipFlag tooltipFlag) {
        tooltip.add(getBiomeComponent(biome));
        ResourceLocation id = getResourceLocation(biome);
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
        ResourceLocation id = INSTANCE.getResourceLocation(biome);
        if (id == null) {
            return Component.literal("unknown");
        }
        return StructureTextHelper.getBiomeComponent(id.toString());
    }
}
