package org.hp.jei_structures.debug;

import net.minecraft.world.entity.Entity;

public final class StructureCaptureEntityFilter {

    public static final String NATURAL_SPAWN_MARKER = "jei_structures:natural_spawn";

    private StructureCaptureEntityFilter() {
    }

    // 将自然生成标记写入实体的持久化 Tags，避免扫描时为未标记生物创建空数据。
    public static void markNaturalSpawn(Entity entity) {
        if (entity != null) {
            entity.addTag(NATURAL_SPAWN_MARKER);
        }
    }

    // 判断生物是否已经被标记为自然生成。
    public static boolean isNaturalSpawn(Entity entity) {
        return entity != null && entity.entityTags().contains(NATURAL_SPAWN_MARKER);
    }
}
