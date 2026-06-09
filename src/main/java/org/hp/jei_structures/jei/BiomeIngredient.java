package org.hp.jei_structures.jei;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import org.hp.jei_structures.JeiStructures;

import java.util.List;

public final class BiomeIngredient implements IIngredientType<StructureBiomeIcon>, IIngredientHelper<StructureBiomeIcon>, IIngredientRenderer<StructureBiomeIcon> {

    public static final BiomeIngredient INSTANCE = new BiomeIngredient();
    private static final ResourceLocation MISSING_TEXTURE = new ResourceLocation(JeiStructures.MODID, "textures/biome_icon/missing.png");

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
    public void render(PoseStack pose, StructureBiomeIcon biome) {
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 150.0F);
        RenderSystem.setShaderTexture(0, getTexture(biome));
        GuiComponent.blit(pose, 0, 0, 0, 0, 16, 16, 16, 16);
        pose.popPose();
    }

    @Override
    public List<Component> getTooltip(StructureBiomeIcon biome, TooltipFlag tooltipFlag) {
        java.util.ArrayList<Component> tooltip = new java.util.ArrayList<>();
        tooltip.add(getBiomeComponent(biome));
        ResourceLocation id = getResourceLocation(biome);
        if (biome != null && !biome.dimensionIds().isEmpty()) {
            tooltip.add(StructureTextHelper.component("jei_structures.tooltip.biome_dimensions", StructureTextHelper.joinDimensionNames(biome.dimensionIds(), 6)));
        }
        if (id != null) {
            tooltip.add(StructureTextHelper.component("jei_structures.tooltip.biome_id", id.toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (biome != null && !biome.sourceSelectors().isEmpty()) {
            tooltip.add(StructureTextHelper.component("jei_structures.tooltip.biome_source", String.join(", ", biome.sourceSelectors())).withStyle(ChatFormatting.GRAY));
        }
        return tooltip;
    }

    private static Component getBiomeComponent(StructureBiomeIcon biome) {
        ResourceLocation id = INSTANCE.getResourceLocation(biome);
        if (id == null) {
            return Component.literal("unknown");
        }
        return StructureTextHelper.getBiomeComponent(id.toString());
    }

    private static ResourceLocation getTexture(StructureBiomeIcon biome) {
        ResourceLocation id = INSTANCE.getResourceLocation(biome);
        if (id == null) {
            return MISSING_TEXTURE;
        }
        ResourceLocation texture = new ResourceLocation(id.getNamespace(), "textures/biome_icon/" + id.getPath() + ".png");
        if (Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()) {
            return texture;
        }
        return MISSING_TEXTURE;
    }
}
