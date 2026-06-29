package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.ApotheosisSpells;
import com.example.apotheosis_spells.api.ReforgeCache;
import dev.shadowsoffire.apotheosis.adventure.affix.Affix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixRegistry;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 铭刻台 NBT 同步修复
 *
 * 核心问题：
 *   1. ISpellContainer.set() 使用 SpellContainer.CODEC 序列化，只保留 (id, level, locked, index)
 *   2. 在 RETURN 处用 ReforgeCache.putSlotTag 写入的 affix_data，会在下一次 ISpellContainer.get() 时
 *      被 CODEC 解码丢弃（因为 CODEC 不识别 affix_data 字段）
 *
 * 解决方案：
 *   1. 改用直接 NBT 操作：直接读取/写入 spellBookItemStack 的 NBT，不经过 ISpellContainer.get/set
 *   2. 在 setupResultSlot RETURN 处，恢复 Scroll 的顶层 affix_data（因为 createScrollContainer 会清除它）
 */
@Mixin(value = InscriptionTableMenu.class, remap = false)
public class InscribeMixin {

    @Shadow private int selectedSpellIndex;

    @Shadow public Slot getScrollSlot() { return null; }
    @Shadow public Slot getSpellBookSlot() { return null; }
    @Shadow public Slot getResultSlot() { return null; }

    @Unique private static CompoundTag cachedScrollSlotNbt = null;
    @Unique private static CompoundTag cachedScrollTopAffixData = null;
    @Unique private static List<CompoundTag> cachedAllBookSlots = null;
    @Unique private static volatile boolean isRestoringAffixData = false;

    /**
     * doInscription HEAD：在 scroll 被消耗之前，保存完整 NBT
     * 1. 保存 scroll SpellSlot 0 的完整 NBT（包含 affix_data + iss_reforge）
     * 2. 保存 scroll 顶层的 affix_data（重铸场景下 affix_data 在顶层，不在 SpellSlot 子标签）
     * 3. 保存 spellbook 所有已有 slot 的完整 NBT（防止 ISpellContainer.set() 覆盖后丢失）
     */
    @Inject(method = "doInscription", at = @At("HEAD"))
    private void beforeDoInscription(int selectedIndex, CallbackInfo ci) {
        InscriptionTableMenu self = (InscriptionTableMenu) (Object) this;

        // 1. 保存 scroll Slot 0 完整 NBT
        ItemStack scrollStack = self.getScrollSlot().getItem();
        cachedScrollSlotNbt = ReforgeCache.getSlotTag(scrollStack, 0);

        // 2. 保存 scroll 顶层的 affix_data（Apotheosis 重铸时 affix 在顶层）
        cachedScrollTopAffixData = scrollStack.getTagElement(AffixHelper.AFFIX_DATA);

        // 3. 保存 spellbook 所有已有 slot 的完整 NBT
        ItemStack bookStack = self.getSpellBookSlot().getItem();
        cachedAllBookSlots = new ArrayList<>();
        if (!bookStack.isEmpty() && ISpellContainer.isSpellContainer(bookStack)) {
            CompoundTag root = bookStack.getTagElement(ISpellContainer.NBT);
            if (root == null) root = bookStack.getTagElement(ISpellContainer.LEGACY_NBT);
            if (root != null) {
                ListTag dataList = root.getList("data", 10);
                for (int i = 0; i < dataList.size(); i++) {
                    CompoundTag slot = dataList.getCompound(i);
                    cachedAllBookSlots.add(slot.copy());
                }
            }
        }

        ApotheosisSpells.LOGGER.info("[InscribeMixin] beforeDoInscription: scrollSlotNbt={}, scrollTopAffix={}, bookSlots={}",
                cachedScrollSlotNbt != null, cachedScrollTopAffixData != null,
                cachedAllBookSlots != null ? cachedAllBookSlots.size() : 0);
    }

    /**
     * doInscription INVOKE：在 ISpellContainer.set 之后执行
     *
     * ISpellContainer.set() 会用 CODEC 重新编码整个容器，导致所有 slot 的自定义字段丢失。
     *
     * 关键修复：按 index 字段匹配而非数组下标。
     * 原因：addSpellAtIndex 后新增的 slot 在 cachedAllBookSlots 中不存在（缓存时还没添加）。
     * 解决方案：
     *   1. 遍历 dataList，按 index 字段找到对应的 cached slot
     *   2. 对于已有 slot（旧法术）：完整恢复 NBT
     *   3. 对于新增 slot（新抄写法术）：合并 scroll 的 affix_data 和 iss_reforge
     */
    @Inject(method = "doInscription", at = @At(value = "INVOKE", target = "Lio/redspace/ironsspellbooks/api/spells/ISpellContainer;set(Lnet/minecraft/world/item/ItemStack;Lio/redspace/ironsspellbooks/api/spells/ISpellContainer;)V", ordinal = 0, shift = At.Shift.AFTER))
    private void afterSetSpellBook(int selectedIndex, CallbackInfo ci) {
        InscriptionTableMenu self = (InscriptionTableMenu) (Object) this;
        ItemStack bookStack = self.getSpellBookSlot().getItem();
        if (bookStack.isEmpty()) {
            ApotheosisSpells.LOGGER.info("[InscribeMixin] afterSet: bookStack is empty, skipping");
            return;
        }

        ApotheosisSpells.LOGGER.info("[InscribeMixin] afterSet RETURN: bookStack.hashCode={}, selectedIndex={}",
                bookStack.hashCode(), selectedIndex);

        // 从 scroll 的 NBT 中提取 affix_data
        // 优先级：SpellSlot 子标签 > Scroll 顶层
        CompoundTag scrollAffixData = null;
        if (cachedScrollSlotNbt != null) {
            scrollAffixData = cachedScrollSlotNbt.getCompound(ReforgeCache.SLOT_AFFIX_DATA);
            ApotheosisSpells.LOGGER.info("[InscribeMixin] afterSet: fromSlot={}, empty={}",
                    scrollAffixData != null, scrollAffixData != null ? scrollAffixData.isEmpty() : true);
            if (scrollAffixData == null || scrollAffixData.isEmpty()) {
                scrollAffixData = cachedScrollTopAffixData;
            }
        } else {
            scrollAffixData = cachedScrollTopAffixData;
        }
        ApotheosisSpells.LOGGER.info("[InscribeMixin] afterSet: scrollAffixData={}, scrollTopAffix={}",
                scrollAffixData != null, cachedScrollTopAffixData != null);

        if (cachedAllBookSlots == null || cachedAllBookSlots.isEmpty()) {
            ApotheosisSpells.LOGGER.info("[InscribeMixin] afterSet: cachedAllBookSlots is empty, selectedIndex={}", selectedIndex);
        }

        CompoundTag bookNbt = bookStack.getOrCreateTag();
        CompoundTag containerNbt = bookNbt.getCompound(ISpellContainer.NBT);
        ListTag dataList = containerNbt.getList("data", 10);

        ApotheosisSpells.LOGGER.info("[InscribeMixin] afterSet: dataList.size={}, cachedAllBookSlots.size={}, selectedIndex={}",
                dataList.size(),
                cachedAllBookSlots != null ? cachedAllBookSlots.size() : -1,
                selectedIndex);

        int restored = 0;
        for (int i = 0; i < dataList.size(); i++) {
            CompoundTag currentSlot = dataList.getCompound(i);
            int idx = currentSlot.getInt("index");

            // 按 index 字段查找对应的 cached slot
            CompoundTag cachedSlot = null;
            for (CompoundTag cached : cachedAllBookSlots) {
                if (cached != null && cached.getInt("index") == idx) {
                    cachedSlot = cached;
                    break;
                }
            }

            if (cachedSlot != null) {
                // 已有法术：完整恢复 NBT
                dataList.set(i, cachedSlot.copy());
                restored++;
                ApotheosisSpells.LOGGER.info("[InscribeMixin] afterSet: slot[{}] idx={} restored from cache", i, idx);
            } else if (idx == selectedIndex && scrollAffixData != null) {
                // 新增法术：合并 scroll 的 affix_data 和 iss_reforge
                ApotheosisSpells.LOGGER.info("[InscribeMixin] afterSet: slot[{}] idx={} == selectedIndex={}, writing affix_data",
                        i, idx, selectedIndex);
                currentSlot.put(ReforgeCache.SLOT_AFFIX_DATA, scrollAffixData.copy());
                ReforgeCache.Data data = ReforgeCache.computeData(scrollAffixData);
                if (!data.isDefault()) {
                    currentSlot.put(ReforgeCache.KEY, data.write());
                }
                dataList.set(i, currentSlot);
                restored++;
            } else {
                ApotheosisSpells.LOGGER.info("[InscribeMixin] afterSet: slot[{}] idx={} skipped (cachedSlot=null, idx==selectedIndex={}, scrollAffixData={})",
                        i, idx, idx == selectedIndex, scrollAffixData != null);
            }
        }

        containerNbt.put("data", dataList);
        bookNbt.put(ISpellContainer.NBT, containerNbt);

        // 强制将修改后的 NBT 写入 ItemStack（确保 tag 引用一致）
        bookStack.setTag(bookNbt);

        // 新增（根治）：把刚抄入法术的词缀写到书顶层并行存储，按其 index 键存。
        // 这是新的权威来源，Iron's 后续重序列化法术容器不会抹掉它。scrollAffixData 为空则清除该键
        //（索引复用时抄入无词缀法术不会继承旧词缀）。
        ReforgeCache.setBookAffix(bookStack, selectedIndex, scrollAffixData);

        // 关键修复：通知 Slot 数据已变化
        self.getSpellBookSlot().setChanged();

        // 验证写入结果 - 打印所有 slot 的 affix_data 状态
        for (int i = 0; i < dataList.size(); i++) {
            CompoundTag slot = dataList.getCompound(i);
            boolean hasAffix = slot.contains(ReforgeCache.SLOT_AFFIX_DATA);
            String affixKeys = "none";
            if (hasAffix) {
                CompoundTag ad = slot.getCompound(ReforgeCache.SLOT_AFFIX_DATA);
                CompoundTag affixes = ad.getCompound(AffixHelper.AFFIXES);
                affixKeys = affixes.getAllKeys().toString();
            }
            ApotheosisSpells.LOGGER.info("[InscribeMixin] afterSet: verify slot[{}] idx={} hasAffixData={}, affixes={}",
                    i, slot.getInt("index"), hasAffix, affixKeys);
        }

        // 最终验证：从 Slot 重新读取 ItemStack，确认 NBT 已正确写入
        ItemStack verifyStack = self.getSpellBookSlot().getItem();
        CompoundTag verifyNbt = verifyStack.getTagElement(ISpellContainer.NBT);
        if (verifyNbt != null) {
            ListTag verifyData = verifyNbt.getList("data", 10);
            for (int i = 0; i < verifyData.size(); i++) {
                CompoundTag verifySlot = verifyData.getCompound(i);
                boolean vHasAffix = verifySlot.contains(ReforgeCache.SLOT_AFFIX_DATA);
                ApotheosisSpells.LOGGER.info("[InscribeMixin] afterSet: FINAL verify slot[{}] idx={} hasAffixData={}",
                        i, verifySlot.getInt("index"), vHasAffix);
            }
        }
    }

    @Inject(method = "doInscription", at = @At("RETURN"))
    private void afterDoInscription(int selectedIndex, CallbackInfo ci) {
        cachedScrollSlotNbt = null;
        cachedScrollTopAffixData = null;
        cachedAllBookSlots = null;
    }

    /**
     * 拦截 quickMoveStack，处理双击取走卷轴的情况
     * 双击时 quickPickItem 不处理 resultSlot（因为 mayPlace=false），所以实际走的是 quickMoveStack
     */
    @Inject(method = "quickMoveStack", at = @At("HEAD"))
    private void onQuickMoveStack(net.minecraft.world.entity.player.Player player, int index, CallbackInfo ci) {
        ApotheosisSpells.LOGGER.info("[InscribeMixin] quickMoveStack HEAD: index={}, resultSlotIndex=38", index);
    }

    @Inject(method = "quickMoveStack", at = @At("RETURN"))
    private void onQuickMoveStackReturn(net.minecraft.world.entity.player.Player player, int index, CallbackInfo ci) {
        ApotheosisSpells.LOGGER.info("[InscribeMixin] quickMoveStack RETURN: index={}", index);
    }

    /**
     * setupResultSlot RETURN：提取法术到卷轴时，通过 AffixHelper.setAffixes() 重建重铸词缀
     *
     * 需求3：不仅要取出完整 NBT，还要通过 Apotheosis 重铸功能重建为和放入时一样的卷轴，
     * 以便重铸、强化功能正常工作（直接复制 NBT 会导致重铸、强化功能丢失）。
     *
     * 步骤：
     *   1. 从 SpellSlot NBT 读取完整的 affix_data
     *   2. 读取词缀 ID、等级、稀有度信息
     *   3. 用 AffixHelper.setAffixes() 真正应用词缀（不是裸复制 NBT）
     *   4. 同步 ReforgeCache 缓存
     */
    @Inject(method = "setupResultSlot", at = @At("RETURN"))
    private void afterSetupResultSlot(CallbackInfo ci) {
        InscriptionTableMenu self = (InscriptionTableMenu) (Object) this;
        ItemStack resultStack = self.getResultSlot().getItem();
        if (resultStack.isEmpty() || !resultStack.is(ItemRegistry.SCROLL.get())) return;

        ItemStack bookStack = self.getSpellBookSlot().getItem();
        if (bookStack.isEmpty() || !(bookStack.getItem() instanceof io.redspace.ironsspellbooks.item.SpellBook)) return;
        if (selectedSpellIndex < 0) {
            ApotheosisSpells.LOGGER.info("[InscribeMixin] setupResultSlot: selectedSpellIndex={} < 0, skipping", selectedSpellIndex);
            return;
        }

        var spellList = ISpellContainer.get(bookStack);
        // 关键根因修复：selectedSpellIndex 是“物理槽位下标”——铭刻台界面按 getAllSpells() 的稀疏数组
        // 位置发送下标，Iron 的 getSpellAtIndex / removeSpellAtIndex 也都按物理下标取/删。
        // 旧代码用 activeSpells.get(selectedSpellIndex)（压缩后的活动列表位置）：书没有空槽时两者
        // 巧合相等；一旦书有空槽(gap)，activeSpells.get() 会取错槽位或越界 -> targetIndex 错 ->
        // getBookAffix 返回 null -> 提前 return -> 结果槽里留下 Iron 每次重建的原版无词缀卷轴 ->
        // 取出来就是“未重铸”。这就是“拿掉A后取B变回原样 / 末影箭取出来是猩红刺”错乱家族的根因。
        SpellData spellData = spellList.getSpellAtIndex(selectedSpellIndex);
        if (spellData == null || spellData == SpellData.EMPTY || !spellData.canRemove()) {
            ApotheosisSpells.LOGGER.info("[InscribeMixin] setupResultSlot: physical slot {} empty/cannot-remove, skipping", selectedSpellIndex);
            return;
        }

        int targetIndex = selectedSpellIndex; // 物理下标：与抄入时 setBookAffix 的键、Iron 的 removeSpellAtIndex 一致

        // 词缀数据从书顶层并行存储读取（权威来源，不会被 Iron's 重序列化抹掉）。
        // 不再需要"保存剩余法术 + onTake 恢复"那套 —— 其它法术的词缀本就独立存在书顶层，移除一个不影响其余。
        CompoundTag affixData = ReforgeCache.getBookAffix(bookStack, targetIndex);
        if (affixData == null || affixData.isEmpty()) {
            ApotheosisSpells.LOGGER.info("[InscribeMixin] setupResultSlot: no book-level affix for targetIndex={}", targetIndex);
            return;
        }

        CompoundTag affixesTag = affixData.getCompound(AffixHelper.AFFIXES);
        ApotheosisSpells.LOGGER.info("[InscribeMixin] afterSetupResultSlot: found affix_data, affixes keys={}, rarity={}",
                affixesTag.getAllKeys(), affixData.getString(AffixHelper.RARITY));

        try {
            ItemStack newScroll = new ItemStack(ItemRegistry.SCROLL.get());

            // 1. 使用 Iron's Spells 创建卷轴基础数据
            ISpellContainer.createScrollContainer(spellData.getSpell(), spellData.getLevel(), newScroll);

            // 2. 从 affix_data 读取词缀信息，构建 AffixInstance Map
            Map<DynamicHolder<? extends Affix>, AffixInstance> affixMap = new LinkedHashMap<>();
            DynamicHolder<LootRarity> rarityHolder = AffixHelper.getRarity(affixData);
            LootRarity rarity = rarityHolder.isBound() ? rarityHolder.get() : RarityRegistry.getMinRarity().get();

            for (String key : affixesTag.getAllKeys()) {
                DynamicHolder<Affix> holder = AffixRegistry.INSTANCE.holder(new ResourceLocation(key));
                if (!holder.isBound()) continue;
                float lvl = affixesTag.getFloat(key);
                ApotheosisSpells.LOGGER.info("[InscribeMixin] afterSetupResultSlot: affix={}, lvl={}", key, lvl);
                affixMap.put(holder, new AffixInstance(holder, newScroll, rarityHolder, lvl));
            }

            // 3. 通过 AffixHelper.setAffixes() 真正应用词缀（触发 Apotheosis 重铸系统）
            if (!affixMap.isEmpty()) {
                AffixHelper.setAffixes(newScroll, affixMap);
            }

            // 4. 设置稀有度（setAffixes 不写 rarity，需要单独设置）
            if (rarityHolder.isBound()) {
                AffixHelper.setRarity(newScroll, rarity);
            }

            // 5. 设置名称（如果原法术有自定义名称）
            String customName = affixData.getString(AffixHelper.NAME);
            if (customName != null && !customName.isEmpty()) {
                try {
                    net.minecraft.network.chat.Component name = net.minecraft.network.chat.Component.Serializer.fromJson(customName);
                    if (name != null) {
                        AffixHelper.setName(newScroll, name);
                    }
                } catch (Exception e) {
                    ApotheosisSpells.LOGGER.warn("[InscribeMixin] failed to parse custom name: {}", customName);
                }
            }

            // 6. 同步 ReforgeCache 缓存
            ReforgeCache.sync(newScroll);

            // 7. 直接复制完整的 affix_data NBT（包含 gems 等额外数据）
            // 注意：这会覆盖之前设置的 affixes/rarity，需要在 sync 之后调用
            ReforgeCache.rebuildAffixesToScroll(newScroll, affixData);

            // 5. 强制更新 slot
            self.getResultSlot().set(ItemStack.EMPTY);
            self.getResultSlot().set(newScroll);

            // 不再需要 SlotOnTake 的"保存剩余 + onTake 恢复"：其它法术的词缀本就独立存在书顶层并行存储里，
            // 取出一个法术不影响其余；被取出索引的键会在该索引被新法术抄入时自动覆盖/清除。

            ApotheosisSpells.LOGGER.info("[InscribeMixin] afterSetupResultSlot SUCCESS for spell={}, affixCount={}",
                    spellData.getSpell().getSpellId(), affixMap.size());

        } catch (Exception e) {
            ApotheosisSpells.LOGGER.error("[InscribeMixin] afterSetupResultSlot failed: {}", e.toString());
            e.printStackTrace();
        }
    }

    }