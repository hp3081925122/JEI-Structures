# JEI Structures

`/jei_structures export` 能搜集并提供一定信息，普通玩家用这个就行，但是无法搜集硬编码相关生物、战利品信息。

`/jei_structures export` can collect and provide a certain amount of information. This is enough for normal players, but it cannot collect hardcoded mob and loot information.

`/jei_structures debug_capture_quick` 几乎能搜寻所有生物、战利品信息，但是收集需要花费很长时间，使用方法请查看下面的项目介绍。

`/jei_structures debug_capture_quick` can find almost all mob and loot information, but the collection process can take a long time. Please check the project introduction below for usage.

## debug_capture_quick 使用方法 / debug_capture_quick Usage

`/jei_structures debug_capture_quick` 是给整合包作者和开发者使用的高级收集指令。它会实际定位结构、传送到结构内部、加载结构区块，并采集结构中的生物、容器物品、战利品表和特殊方块信息。

`/jei_structures debug_capture_quick` is an advanced collection command for modpack authors and developers. It actually locates structures, teleports into them, loads their chunks, and captures mobs, container items, loot tables, and special block information.

这个指令会先定位全部目标结构，然后一个一个传送过去采集信息。之所以需要这样做，是因为 Minecraft 原版和很多模组中存在大量硬编码逻辑，例如结构生成时直接生成生物、随机生成方块、或由方块在运行时调用战利品表。仅靠静态导出很难完整识别这些内容。

This command first locates all target structures, then teleports to them one by one to collect information. This is necessary because vanilla Minecraft and many mods contain a lot of hardcoded logic, such as mobs spawned directly during structure generation, randomly generated blocks, or blocks calling loot tables at runtime. Static export alone cannot reliably detect all of this information.

收集全部结构：

Collect all structures:

```mcfunction
/jei_structures debug_capture_quick all
```

只收集某个模组的结构：

Collect structures from one mod:

```mcfunction
/jei_structures debug_capture_quick mod cataclysm
```

只收集某个结构：

Collect one structure:

```mcfunction
/jei_structures debug_capture_quick structure minecraft:village_plains
```

只收集某个维度中的结构：

Collect structures in one dimension:

```mcfunction
/jei_structures debug_capture_quick dimension minecraft:overworld
```

继续收集剩余未完成的结构：

Continue collecting remaining unfinished structures:

```mcfunction
/jei_structures debug_capture_quick remaining
```

查看当前收集状态：

Check current collection status:

```mcfunction
/jei_structures debug_capture_quick status
```

停止当前收集任务：

Stop the current collection task:

```mcfunction
/jei_structures debug_capture_quick stop
```

## 收集完成后 / After Collection

1. 原始结果保存在 `config/jei_structures/reports/<批次ID>/`，这里的文件不会直接参与导出。

   Raw results are saved in `config/jei_structures/reports/<run_id>/`. They are not used by export directly.

2. 把整个批次文件夹复制到 `exports`：

   Copy the whole run folder into `exports`:

```text
config/jei_structures/reports/<run_id>/  →  config/jei_structures/exports/<run_id>/
```

3. 再执行一次导出，并重启客户端：

   Run export again, then restart the client:

```mcfunction
/jei_structures export
```

导出会合并所有批次（自动去重），生成JEI/EMI 共用读取的索引文件：

Export merges all runs (deduplicated) and generates the index file read by both JEI and EMI:

```text
config/jei_structures/structure_index.cache
```

旧版 `structure_index.json` 不再被读取，升级后需要重新执行一次 export。

The legacy `structure_index.json` is no longer read; run export again after upgrading.

整合包作者只需随整合包分发这个文件，玩家不需要自己采集。

Modpack authors only need to ship this file; players don't need to run the capture themselves.

## 数据包扩展 / Data Pack Extensions

如果想让某些结构里的方块作为“特殊方块”优先显示，需要把方块加入这个 tag：

If you want some blocks inside structures to be displayed first as special blocks, add them to this tag:

```text
data/jei_structures/tags/blocks/special_display_blocks.json
```

示例：

Example:

```json
{
  "replace": false,
  "values": [
    "minecraft:spawner",
    "minecraft:chest"
  ]
}
```

导出时，如果结构模板中包含这个 tag 里的方块，它们会进入 JEI 结构页的“特殊方块”区域。

During export, if a structure template contains blocks from this tag, they will be shown in the "Special Blocks" section of the JEI structure page.

如果想给某个生物绑定特殊介绍，可以在数据包中添加：

If you want to bind special information to an entity, add:

```text
data/<namespace>/structure_special_entities/<file_name>.json
```

示例：

Example:

```json
{
  "entity_id": "minecraft:warden",
  "translation_key": "jei_structures.special.minecraft.warden"
}
```

如果某个结构中包含这个生物，JEI 结构页顶部会显示“特殊信息”，并在对应刷怪蛋 tooltip 中显示这个汉化键的内容。

If a structure contains this entity, the JEI structure page will show "Special Information" at the top, and the translation text will be shown in the tooltip of the corresponding spawn egg.

如果想给某个方块绑定特殊介绍，可以在数据包中添加：

If you want to bind special information to a block, add:

```text
data/<namespace>/structure_special_blocks/<file_name>.json
```

示例：

Example:

```json
{
  "block_id": "minecraft:spawner",
  "translation_key": "jei_structures.special.minecraft.spawner"
}
```

如果某个结构中包含这个方块，JEI 结构页顶部会显示“特殊信息”，并在对应方块 tooltip 中显示这个汉化键的内容。

If a structure contains this block, the JEI structure page will show "Special Information" at the top, and the translation text will be shown in the tooltip of the corresponding block.

特殊介绍文件支持单个对象，也支持对象数组。

Special information files support either a single object or an array of objects.

Modpack authors can distribute this file with the modpack. Normal players usually only need this file and do not need to run `debug_capture_quick` themselves.

## 注意事项 / Notes

`debug_capture_quick` 可能会花费很长时间，具体时间取决于整合包中的结构数量、维度数量、世界生成速度和电脑性能。

`debug_capture_quick` may take a long time, depending on the number of structures, number of dimensions, world generation speed, and computer performance.

建议在测试世界或备份世界中执行，不建议在重要存档中直接运行。

It is recommended to run it in a test world or backup world, not directly in an important save.

收集过程中玩家会被传送，区块会被加载，游戏可能出现明显卡顿。

During collection, the player will be teleported, chunks will be loaded, and the game may lag noticeably.

信息收集通常只需要执行一次。只有在模组列表、结构生成、数据包、配置或战利品表发生变化后，才需要重新收集并重新导出。

Information collection usually only needs to be done once. You only need to collect and export again after the mod list, structure generation, data packs, configs, or loot tables change.
