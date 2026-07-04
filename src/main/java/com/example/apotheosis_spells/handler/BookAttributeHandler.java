package com.example.apotheosis_spells.handler;

import com.example.apotheosis_spells.ApotheosisSpells;
import com.example.apotheosis_spells.api.ReforgeCache;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 铭刻进法术书的「属性词条 / 属性宝石」生效路径。
 *
 * 手持卷轴时，apotheosis:attribute 词条与宝石属性加成走 Apotheosis 原生的
 * ItemAttributeModifierEvent（scroll 类别注册了 MAINHAND/OFFHAND），无需本类。
 * 但铭刻进法术书后，词条数据只存在于书顶层的 apoth_book_affixes（按物理槽位索引），
 * 书本身不是 scroll 类别，原生钩子不会触发——需求是「仅当玩家当前选中该法术时生效」。
 *
 * 实现：服务端每 10 tick 解析一次当前选中法术：
 *   选中项来自装备中的法术书（排除主/副手卷轴——那是原生路径，避免双重加成）
 *   → getIndexForSpell 映射回物理槽位 → 取出存储的 affix_data
 *   → 还原成虚拟卷轴 ItemStack，借 Apotheosis 的 AffixInstance/SocketedGems.addModifiers
 *     收集属性修饰符 → 以本 mod 命名空间的确定性 UUID 作为临时修饰符挂到玩家身上。
 * 选中变化/换书/取消选择时撤销重挂；临时修饰符不落盘，重登由本类自然重建。
 */
@Mod.EventBusSubscriber(modid = ApotheosisSpells.MODID)
public class BookAttributeHandler {

    private static final String MODIFIER_NAME = "apotheosis_spells:book_affix";
    private static final int CHECK_INTERVAL_TICKS = 10;

    /** 玩家 UUID -> 已挂修饰符（用于撤销）。仅服务端线程访问。 */
    private static final Map<UUID, List<Applied>> APPLIED = new HashMap<>();
    /** 玩家 UUID -> 上次生效的 affix_data 签名，用于跳过无变化的循环。 */
    private static final Map<UUID, String> SIGNATURE = new HashMap<>();

    private record Applied(Attribute attribute, UUID id) {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) return;
        try {
            refresh(player);
        } catch (Exception e) {
            ApotheosisSpells.LOGGER.warn("BookAttributeHandler refresh failed", e);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        // 死亡/末地返回会换新实体，属性表是新的：丢掉缓存，下个周期在新实体上重挂。
        forget(event.getOriginal().getUUID());
    }

    private static void forget(UUID id) {
        APPLIED.remove(id);
        SIGNATURE.remove(id);
    }

    private static void refresh(ServerPlayer player) {
        CompoundTag affixData = resolveSelectedBookAffixes(player);
        String signature = affixData == null ? "" : affixData.toString();
        if (signature.equals(SIGNATURE.get(player.getUUID()))) return;

        List<Applied> old = APPLIED.remove(player.getUUID());
        if (old != null) {
            for (Applied a : old) {
                AttributeInstance inst = player.getAttribute(a.attribute());
                if (inst != null) inst.removeModifier(a.id());
            }
        }

        List<Applied> applied = new ArrayList<>();
        if (affixData != null && !affixData.isEmpty()) {
            List<Map.Entry<Attribute, AttributeModifier>> collected = collectModifiers(affixData);
            int i = 0;
            for (var entry : collected) {
                AttributeInstance inst = player.getAttribute(entry.getKey());
                if (inst == null) continue;
                UUID id = UUID.nameUUIDFromBytes((MODIFIER_NAME + '#' + i++).getBytes(StandardCharsets.UTF_8));
                if (inst.getModifier(id) != null) inst.removeModifier(id);
                AttributeModifier m = entry.getValue();
                inst.addTransientModifier(new AttributeModifier(id, MODIFIER_NAME, m.getAmount(), m.getOperation()));
                applied.add(new Applied(entry.getKey(), id));
            }
        }
        if (!applied.isEmpty()) APPLIED.put(player.getUUID(), applied);
        SIGNATURE.put(player.getUUID(), signature);
    }

    /**
     * 当前选中法术若来自装备的法术书且该槽位铭刻时带了词条数据，返回其 affix_data；否则 null。
     */
    private static CompoundTag resolveSelectedBookAffixes(ServerPlayer player) {
        ItemStack book = Utils.getPlayerSpellbookStack(player);
        if (book.isEmpty() || !ISpellContainer.isSpellContainer(book)) return null;

        SpellSelectionManager manager = new SpellSelectionManager(player);
        SpellSelectionManager.SelectionOption selection = manager.getSelection();
        if (selection == null || selection.spellData == null || selection.spellData.getSpell() == null) return null;
        // 主/副手卷轴的属性由原生 ItemAttributeModifierEvent 路径生效，这里跳过防止双重加成。
        if (SpellSelectionManager.MAINHAND.equals(selection.slot) || SpellSelectionManager.OFFHAND.equals(selection.slot)) return null;

        ISpellContainer container = ISpellContainer.get(book);
        int physicalIndex = container.getIndexForSpell(selection.spellData.getSpell());
        if (physicalIndex < 0) return null;
        return ReforgeCache.getBookAffix(book, physicalIndex);
    }

    /**
     * 把存储的 affix_data 还原到一个虚拟卷轴上，用 Apotheosis 自己的逻辑取属性修饰符
     * （词条 + 镶嵌宝石都覆盖，数值与手持卷轴时完全一致）。
     */
    private static List<Map.Entry<Attribute, AttributeModifier>> collectModifiers(CompoundTag affixData) {
        List<Map.Entry<Attribute, AttributeModifier>> collected = new ArrayList<>();
        ItemStack virtual = new ItemStack(ItemRegistry.SCROLL.get());
        virtual.getOrCreateTag().put(AffixHelper.AFFIX_DATA, affixData.copy());

        for (AffixInstance inst : AffixHelper.getAffixes(virtual).values()) {
            try {
                inst.addModifiers(EquipmentSlot.MAINHAND, (attr, m) -> collected.add(Map.entry(attr, m)));
            } catch (Exception ignored) {}
        }
        try {
            SocketHelper.getGems(virtual).addModifiers(ScrollLootCategory.SCROLL, EquipmentSlot.MAINHAND,
                (attr, m) -> collected.add(Map.entry(attr, m)));
        } catch (Exception ignored) {}
        return collected;
    }
}
