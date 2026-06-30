package com.example.apotheosis_spells.handler;

import com.example.apotheosis_spells.api.ReforgeCache;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 法术施法上下文 ThreadLocal。
 *
 * Iron's Spells 的 AbstractSpell 计算方法（getLevelFor/getManaCost/getSpellPower 等）是 static 风格
 * （虽然 AbstractSpell 是抽象类，但具体子类在调用时通过 (this) 隐式访问字段）。
 *
 * 为了让 CastMixin 能够拿到当前施法的物品与 SpellSlot 索引，我们用 ThreadLocal 记录：
 *   - stack：当前 attemptInitiateCast 正在被触发的物品
 *   - level：spellSlot.getLevel()
 *   - caster：尝试施法的玩家（castMixin 需要调用 MagicData）
 *   - data：从 stack + level 索引 解析的 ReforgeCache.Data
 *   - spellData：当前 ctx 关联的 SpellData 实例（用于 SpellDataMixin 严格身份匹配）
 *
 * spellData 字段的作用：SpellData.equals 是值相等（只比较 spell + level），不区分 affix_data。
 * 多个 SpellData 实例 spell+level 相同但 affix_data 不同时 equals=true。
 * 引用相等（==）才能保证"ctx 是为这个 SpellData 准备的"。
 *
 * 任何时候 attemptInitiateCast 完成后必须在 RETURN 处清理 ThreadLocal。
 */
public class SpellCastHooks {

    public record Context(ItemStack stack, Player caster, int spellSlotIndex, int spellLevel, ReforgeCache.Data data, SpellData spellData, boolean castContext) {
        /**
         * 兼容旧调用（显示路径）：castContext 默认 false。
         *
         * <p>castContext 区分"真正施法"与"显示"两种 ctx。CastMixin 的全局倍率钩子
         * （apoth_manaCost/apoth_spellPower/apoth_castTime，注入 getManaCost/getSpellPower/
         * getEffectiveCastTime 的 RETURN）只在 castContext=true 时施加倍率。显示路径
         * （TooltipUtils / InscriptionTableScreen / SpellWheel）各自的 redirect 已经施加过一次倍率，
         * 故建此 Context 时 castContext=false，让倍率在每条路径上各只施加一次（口径一致）。
         */
        public Context(ItemStack stack, Player caster, int spellSlotIndex, int spellLevel, ReforgeCache.Data data, SpellData spellData) {
            this(stack, caster, spellSlotIndex, spellLevel, data, spellData, false);
        }
    }

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    public static void set(Context ctx) {
        CURRENT.set(ctx);
    }

    public static Context get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 从 ItemStack + spellSlotIndex 建立 Context。
     *   - 若 stack 是 Scroll → spellSlotIndex 必为 0，data 从 Scroll 第 0 个 slot 读
     *   - 若 stack 是 SpellBook → spellSlotIndex 在 [0, getActiveSpells().size())，data 从对应 slot 读
     *   - 其他 → data = DEF
     */
    public static Context buildContext(ItemStack stack, Player caster, int spellSlotIndex, int spellLevel, SpellData spellData) {
        ReforgeCache.Data data = ReforgeCache.Data.DEF;
        if (stack != null && !stack.isEmpty()) {
            if (stack.getItem() instanceof Scroll) {
                data = ReforgeCache.getFromScroll(stack);
            } else if (stack.getItem() instanceof SpellBook) {
                data = ReforgeCache.getFromSpellBook(stack, spellSlotIndex);
            }
        }
        return new Context(stack, caster, spellSlotIndex, spellLevel, data, spellData);
    }

    /**
     * 兼容旧调用：spellData = null（不做身份检查）
     */
    public static Context buildContext(ItemStack stack, Player caster, int spellSlotIndex, int spellLevel) {
        return buildContext(stack, caster, spellSlotIndex, spellLevel, null);
    }
}
