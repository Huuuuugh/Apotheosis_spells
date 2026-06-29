package com.example.apotheosis_spells.api;

import com.example.apotheosis_spells.ApotheosisSpells;
import dev.shadowsoffire.apotheosis.adventure.affix.Affix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixRegistry;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.bonus.GemBonus;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 重铸数据缓存层。
 *
 * 重铸单位 = SpellContainer 的每个 SpellSlot。
 * 但 Scroll 整体被 Apotheosis 重铸时，affix 写到 Scroll 的 affix_data 顶层；
 * 铭刻到 SpellBook 后才下沉到 SpellSlot 子标签。
 *
 * 因此 Data 读取必须支持两个来源：
 *   1. SpellSlot 子标签 `affix_data` —— 铭刻后 SpellBook / 铭刻后 Scroll
 *   2. 物品顶层 `affix_data` —— 重铸后的 Scroll
 *
 * 优先级：SpellSlot 子标签 > 物品顶层。
 */
public class ReforgeCache {

    public static final String KEY = "iss_reforge";
    public static final String SLOT_AFFIX_DATA = "affix_data";

    public record Data(float dmg, float mana, float cd, float cast, int lvl,
                       float radius, float duration, int school) {
        public static final Data DEF = new Data(1, 1, 1, 1, 0, 1, 1, 0);

        public CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putFloat("d", dmg);
            t.putFloat("m", mana);
            t.putFloat("c", cd);
            t.putFloat("t", cast);
            t.putInt("l", lvl);
            t.putFloat("r", radius);
            t.putFloat("du", duration);
            t.putInt("sf", school);
            return t;
        }

        public static Data read(CompoundTag t) {
            if (t == null || t.isEmpty()) return DEF;
            return new Data(
                    t.contains("d") ? t.getFloat("d") : 1f,
                    t.contains("m") ? t.getFloat("m") : 1f,
                    t.contains("c") ? t.getFloat("c") : 1f,
                    t.contains("t") ? t.getFloat("t") : 1f,
                    t.contains("l") ? t.getInt("l") : 0,
                    t.contains("r") ? t.getFloat("r") : 1f,
                    t.contains("du") ? t.getFloat("du") : 1f,
                    t.contains("sf") ? t.getInt("sf") : 0
            );
        }

        public boolean isDefault() {
            return dmg == 1 && mana == 1 && cd == 1 && cast == 1 && lvl == 0
                    && radius == 1 && duration == 1 && school == 0;
        }
    }

    // ============================ 复合读取（核心修复） ============================

    /**
     * 从 SpellSlot 子标签读 Data（如果子标签有 iss_reforge，否则返回 null）。
     */
    public static Data fromSlotAffixData(CompoundTag slotTag) {
        if (slotTag == null) return null;
        CompoundTag dataTag = slotTag.getCompound(KEY);
        if (dataTag.isEmpty()) return null;
        Data d = Data.read(dataTag);
        return d.isDefault() ? null : d;
    }

    /**
     * 从物品顶层 affix_data 读 Data（重铸场景）。
     */
    public static Data fromItemAffixData(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CompoundTag affixData = stack.getTagElement(AffixHelper.AFFIX_DATA);
        if (affixData == null || affixData.isEmpty()) return null;
        Data d = computeData(affixData);
        return d.isDefault() ? null : d;
    }

    /**
     * 复合读取：从 SpellSlot 子标签 → 物品顶层 affix_data → DEF
     */
    public static Data read(CompoundTag slotTag, ItemStack stack) {
        Data d = fromSlotAffixData(slotTag);
        if (d != null) return d;
        d = fromItemAffixData(stack);
        if (d != null) return d;
        return Data.DEF;
    }

    // ============================ 兼容旧 API ============================

    public static Data get(ItemStack stack) {
        if (stack.isEmpty()) return Data.DEF;
        if (stack.getItem() instanceof Scroll) {
            return getFromScroll(stack);
        }
        return Data.DEF;
    }

    public static Data getFromScroll(ItemStack scroll) {
        if (scroll.isEmpty() || !(scroll.getItem() instanceof Scroll)) return Data.DEF;
        // 1. 优先 SpellSlot 0 子标签的 iss_reforge 缓存
        if (ISpellContainer.isSpellContainer(scroll)) {
            CompoundTag slot = getSlotTag(scroll, 0);
            Data d = getFromSlot(slot);
            if (!d.isDefault()) return d;
        }
        // 2. fallback Scroll 顶层 iss_reforge（重铸场景缓存）
        Data d2 = Data.read(scroll.getTagElement(KEY));
        if (!d2.isDefault()) return d2;
        // 3. 实时计算：从 SpellSlot 子标签的 affix_data
        if (ISpellContainer.isSpellContainer(scroll)) {
            CompoundTag slot = getSlotTag(scroll, 0);
            Data slotData = fromSlotAffixData(slot);
            if (slotData != null) return slotData;
        }
        // 4. 实时计算：从 Scroll 顶层 affix_data（重铸场景）
        Data itemData = fromItemAffixData(scroll);
        return itemData != null ? itemData : Data.DEF;
    }

    public static Data getFromSpellBook(ItemStack book, int spellIndex) {
        if (book.isEmpty() || !(book.getItem() instanceof SpellBook)) return Data.DEF;
        if (spellIndex < 0) {
            // 尝试 fallback：SpellBook 顶层 affix_data
            Data d = fromItemAffixData(book);
            return d != null ? d : Data.DEF;
        }
        CompoundTag slot = getSlotTag(book, spellIndex);
        Data d = fromSlotAffixData(slot);
        if (d != null) return d;
        // SpellBook 整体被重铸时，affix 在顶层
        d = fromItemAffixData(book);
        return d != null ? d : Data.DEF;
    }

    /**
     * 综合查找：从玩家当前施法物品解析 Data。
     */
    public static Data resolveDataFromStack(ItemStack item, Player player) {
        if (item.isEmpty()) return Data.DEF;
        if (item.getItem() instanceof Scroll) {
            return getFromScroll(item);
        }
        if (item.getItem() instanceof SpellBook) {
            int idx = resolveSelectedSpellIndex(item, player);
            return getFromSpellBook(item, idx);
        }
        return Data.DEF;
    }

    // ============================ SpellSlot 子标签操作 ============================

    public static CompoundTag getSlotTag(ItemStack stack, int index) {
        if (stack.isEmpty()) return null;
        if (!ISpellContainer.isSpellContainer(stack)) return null;
        CompoundTag root = stack.getTagElement(ISpellContainer.NBT);
        if (root == null) {
            root = stack.getTagElement(ISpellContainer.LEGACY_NBT);
            if (root == null) return null;
        }
        ListTag data = root.getList("data", 10);
        if (index < 0 || index >= data.size()) return null;
        return data.getCompound(index);
    }

    public static void putSlotTag(ItemStack stack, int index, CompoundTag slotTag) {
        if (stack.isEmpty() || slotTag == null) return;
        if (!ISpellContainer.isSpellContainer(stack)) return;
        CompoundTag root = stack.getTagElement(ISpellContainer.NBT);
        if (root == null) {
            root = stack.getTagElement(ISpellContainer.LEGACY_NBT);
            if (root == null) return;
        }
        ListTag data = root.getList("data", 10);
        if (index < 0 || index >= data.size()) return;
        data.set(index, slotTag);
        root.put("data", data);
    }

    public static Data getFromSlot(CompoundTag slotTag) {
        if (slotTag == null) return Data.DEF;
        return Data.read(slotTag.getCompound(KEY));
    }

    // ============================ selectionIndex 解析 ============================

    public static int resolveSelectedSpellIndex(ItemStack spellBook, Player player) {
        if (player == null || spellBook.isEmpty()) return -1;
        try {
            if (player.level().isClientSide()) {
                return resolveClient(player);
            } else {
                return resolveServer(player, spellBook);
            }
        } catch (Throwable t) {
            return -1;
        }
    }

    @SuppressWarnings("deprecation")
    private static int resolveClient(Player player) {
        var ssm = io.redspace.ironsspellbooks.player.ClientMagicData.getSpellSelectionManager();
        var sel = ssm.getSelection();
        if (sel == null) return -1;
        if (sel.slot.equals("mainhand") || sel.slot.equals("offhand")) {
            ItemStack hand = sel.slot.equals("mainhand") ? player.getMainHandItem() : player.getOffhandItem();
            return mapLocalIndexToNbtIndex(player, sel.slotIndex, hand);
        }
        if (sel.slot.equals(io.redspace.ironsspellbooks.compat.Curios.SPELLBOOK_SLOT)) {
            return mapLocalIndexToNbtIndex(player, sel.slotIndex, null);
        }
        return sel.slotIndex;
    }

    private static int resolveServer(Player player, ItemStack spellBook) {
        try {
            var ssm = new io.redspace.ironsspellbooks.api.magic.SpellSelectionManager(player);
            var sel = ssm.getSelection();
            if (sel == null) return -1;
            int localIndex = sel.slotIndex;
            String equipmentSlot = sel.slot;
            if (localIndex < 0) return -1;
            if (equipmentSlot.equals("mainhand") || equipmentSlot.equals("offhand")) {
                return mapLocalIndexToNbtIndex(player, localIndex, spellBook);
            }
            return mapLocalIndexToNbtIndex(player, localIndex, spellBook);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int mapLocalIndexToNbtIndex(Player player, int localIndex, ItemStack fallbackBook) {
        if (localIndex < 0) return -1;
        ItemStack book = fallbackBook;
        if (book == null || book.isEmpty() || !(book.getItem() instanceof SpellBook)) {
            book = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(player);
        }
        if (book == null || book.isEmpty() || !(book.getItem() instanceof SpellBook)) return -1;
        if (!ISpellContainer.isSpellContainer(book)) return -1;
        var active = ISpellContainer.get(book).getActiveSpells();
        if (localIndex >= active.size()) return -1;
        return active.get(localIndex).index();
    }

    // ============================ 同步入口 ============================

    public static void sync(ItemStack stack) {
        if (stack.isEmpty()) return;
        if (stack.getItem() instanceof Scroll) {
            syncScroll(stack, 0);
        }
    }

    public static void syncScroll(ItemStack scroll, int slotIndex) {
        if (scroll.isEmpty() || !(scroll.getItem() instanceof Scroll)) return;
        CompoundTag slot = ISpellContainer.isSpellContainer(scroll) ? getSlotTag(scroll, slotIndex) : null;
        CompoundTag slotAffix = slot != null ? slot.getCompound(SLOT_AFFIX_DATA) : null;
        CompoundTag itemAffix = scroll.getTagElement(AffixHelper.AFFIX_DATA);
        CompoundTag sourceAffix = (slotAffix != null && !slotAffix.isEmpty()) ? slotAffix : itemAffix;
        Data data = (sourceAffix == null || sourceAffix.isEmpty()) ? Data.DEF : computeData(sourceAffix);

        if (slot != null) {
            if (data.isDefault()) slot.remove(KEY);
            else slot.put(KEY, data.write());
            putSlotTag(scroll, slotIndex, slot);
        } else {
            if (data.isDefault()) scroll.removeTagKey(KEY);
            else scroll.addTagElement(KEY, data.write());
        }
    }

    public static void syncScrollSlot(ItemStack stack, int slotIndex) {
        if (stack.isEmpty() || !(stack.getItem() instanceof Scroll)) return;
        syncScroll(stack, slotIndex);
    }

    public static void syncSpellBookSlot(ItemStack book, int spellIndex) {
        if (book.isEmpty() || !(book.getItem() instanceof SpellBook)) return;
        CompoundTag slot = getSlotTag(book, spellIndex);
        CompoundTag slotAffix = slot != null ? slot.getCompound(SLOT_AFFIX_DATA) : null;
        CompoundTag itemAffix = book.getTagElement(AffixHelper.AFFIX_DATA);
        CompoundTag sourceAffix = (slotAffix != null && !slotAffix.isEmpty()) ? slotAffix : itemAffix;
        if (sourceAffix == null || sourceAffix.isEmpty()) {
            if (slot != null) slot.remove(KEY);
            putSlotTag(book, spellIndex, slot != null ? slot : new CompoundTag());
            return;
        }
        Data data = computeData(sourceAffix);
        if (slot == null) slot = new CompoundTag();
        if (data.isDefault()) {
            slot.remove(KEY);
        } else {
            slot.put(KEY, data.write());
        }
        putSlotTag(book, spellIndex, slot);
    }

    public static void syncSlotTag(CompoundTag slotTag) {
        if (slotTag == null) return;
        CompoundTag affixData = slotTag.getCompound(SLOT_AFFIX_DATA);
        Data data = computeData(affixData);
        if (data.isDefault()) {
            slotTag.remove(KEY);
        } else {
            slotTag.put(KEY, data.write());
        }
    }

    // ============================ computeData（保持不变） ============================

    public static Data computeData(CompoundTag affixData) {
        if (affixData == null || affixData.isEmpty()) return Data.DEF;

        float d = 1, m = 1, c = 1, ct = 1;
        int lv = 0;
        float radius = 1, duration = 1;
        int school = 0;

        LootRarity rarity = resolveRarity(affixData);

        CompoundTag affixesTag = affixData.getCompound(AffixHelper.AFFIXES);
        for (String key : affixesTag.getAllKeys()) {
            DynamicHolder<Affix> holder = AffixRegistry.INSTANCE.holder(new ResourceLocation(key));
            if (!holder.isBound()) continue;
            Affix affix = holder.get();
            if (affix == null) continue;
            float lvl = affixesTag.getFloat(key);

            if (affix instanceof com.example.apotheosis_spells.affix.SpellAffix sa) {
                int v = sa.getBaseValue(rarity, lvl);
                if (v == 0) continue;
                Data d2 = sa.contribute(v);
                d = d * d2.dmg();
                m = m * d2.mana();
                c = c * d2.cd();
                ct = ct * d2.cast();
                lv = Math.max(lv, d2.lvl());
                radius = radius * d2.radius();
                duration = duration * d2.duration();
                if (d2.school() != 0) school = d2.school();
            }
        }

        try {
            ListTag gemList = affixData.getList("gems", Tag.TAG_COMPOUND);
            for (Tag tag : gemList) {
                ItemStack gemStack = ItemStack.of((CompoundTag) tag);
                com.example.apotheosis_spells.gem.SpellGemBonus sgb =
                        com.example.apotheosis_spells.gem.GemRegistryHook.getForGemStack(gemStack, rarity);
                if (sgb == null) continue;
                Data d2 = sgb.contribute(rarity);
                d = d * d2.dmg();
                m = m * d2.mana();
                c = c * d2.cd();
                ct = ct * d2.cast();
                radius = radius * d2.radius();
                duration = duration * d2.duration();
            }
        } catch (Throwable t) {
            ApotheosisSpells.LOGGER.debug("[ReforgeCache] 读取 Gem 异常: {}", t.toString());
        }

        return new Data(d, m, c, ct, lv, radius, duration, school);
    }

    public static LootRarity resolveRarity(CompoundTag affixData) {
        try {
            DynamicHolder<LootRarity> holder = AffixHelper.getRarity(affixData);
            if (holder.isBound()) return holder.get();
        } catch (Throwable ignored) {
        }
        return RarityRegistry.getMinRarity().get();
    }

    // ============================ 工具方法 ============================

    public static int applyLevel(SpellData spellData, Data d) {
        return Math.max(1, spellData.getLevel() + d.lvl());
    }

    public static void clearSlot(CompoundTag slotTag) {
        if (slotTag == null) return;
        slotTag.remove(SLOT_AFFIX_DATA);
        slotTag.remove(KEY);
    }

    public static void rebuildAffixesToScroll(ItemStack scroll, CompoundTag affixData) {
        if (scroll.isEmpty() || affixData == null || affixData.isEmpty()) return;
        scroll.addTagElement(AffixHelper.AFFIX_DATA, affixData.copy());
    }

    // ============================ SpellData 等级临时修改 ============================

    private static final Field SPELL_LEVEL_FIELD;

    static {
        Field f = null;
        try {
            f = SpellData.class.getDeclaredField("spellLevel");
            f.setAccessible(true);
        } catch (Exception e) {
            ApotheosisSpells.LOGGER.error("[ReforgeCache] 反射获取 spellLevel 字段失败: {}", e.toString());
        }
        SPELL_LEVEL_FIELD = f;
    }

    /**
     * 临时修改 SpellData 的 spellLevel 字段（直接修改字段，不走 mixin）。
     *
     * 为什么需要这个：ISpellContainer.get(stack) 每次反序列化 NBT 都创建新的 SpellData 实例，
     * SpellDataMixin.getLevel 注入点的 self 和 ctx.spellData 永远不是同一实例，
     * identity check（==）必然失败。
     *
     * 替代方案：直接修改 spellData.spellLevel 字段本身。
     * SpellData.getLevel() 返回 this.spellLevel，字段改了所有 getLevel() 调用都生效。
     * 修改范围严格限于 entry mixin 设置的 spellData 实例 + 它在 formatScrollTooltip 等
     * 内部调用的"同一批"实例。
     *
     * 必须在 finally/onExit 中恢复原值，否则 spellData 实例会被污染。
     *
     * @return 原 spellLevel，用于 onExit 恢复；-1 表示反射失败
     */
    public static int boostLevel(SpellData spellData, int addLevel) {
        if (SPELL_LEVEL_FIELD == null || spellData == null || addLevel == 0) return -1;
        try {
            int original = spellData.getLevel();
            SPELL_LEVEL_FIELD.setInt(spellData, original + addLevel);
            return original;
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean restoreLevel(SpellData spellData, int originalLevel) {
        if (SPELL_LEVEL_FIELD == null || spellData == null || originalLevel < 0) return false;
        try {
            SPELL_LEVEL_FIELD.setInt(spellData, originalLevel);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
