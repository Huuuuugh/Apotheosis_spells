package com.example.apotheosis_spells.api;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 装饰器模式：集中处理重铸属性计算
 * 使用方法：在需要计算重铸加成后的属性时，用此类代替直接调用 ItemStack 上的方法
 *
 * 示例：
 * ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromStack(stack, player);
 * float damage = calc.getSpellPower(level);
 * int mana = calc.getManaCost(level);
 */
public class ReforgedSpellCalculator {

    private final ItemStack stack;
    private final ReforgeCache.Data data;
    private final int spellSlotIndex;

    private ReforgedSpellCalculator(ItemStack stack, ReforgeCache.Data data, int spellSlotIndex) {
        this.stack = stack;
        this.data = data != null ? data : ReforgeCache.Data.DEF;
        this.spellSlotIndex = spellSlotIndex;
    }

    /**
     * 从 ItemStack 创建计算器
     */
    public static ReforgedSpellCalculator fromStack(ItemStack stack, LivingEntity caster) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        ReforgeCache.Data data = ReforgeCache.Data.DEF;
        int slotIndex = -1;

        if (stack.getItem() instanceof Scroll) {
            data = ReforgeCache.getFromScroll(stack);
            slotIndex = 0;
        } else if (stack.getItem() instanceof SpellBook) {
            if (caster instanceof net.minecraft.world.entity.player.Player player) {
                try {
                    slotIndex = ReforgeCache.resolveSelectedSpellIndex(stack, player);
                } catch (Exception e) {
                    slotIndex = -1;
                }
            } else {
                slotIndex = -1;
            }
            data = ReforgeCache.getFromSpellBook(stack, slotIndex);
        }

        return new ReforgedSpellCalculator(stack, data, slotIndex);
    }

    /**
     * 从 SpellBook 的指定槽位创建计算器
     */
    public static ReforgedSpellCalculator fromSpellSlot(ItemStack spellbook, int slotIndex) {
        if (spellbook == null || spellbook.isEmpty() || slotIndex < 0) {
            return null;
        }
        ReforgeCache.Data data = ReforgeCache.getFromSpellBook(spellbook, slotIndex);
        return new ReforgedSpellCalculator(spellbook, data, slotIndex);
    }

    // ==================== 属性计算 ====================

    /**
     * 获取 boosted 法术等级 = 原始等级 + d.lvl
     */
    public int getBoostedLevel(int storedLevel, LivingEntity caster) {
        AbstractSpell spell = getSpell();
        if (spell == null) {
            return storedLevel;
        }
        int afterAffinity = spell.getLevelFor(storedLevel, caster);
        return afterAffinity + data.lvl();
    }

    /**
     * 获取法术威力 = getSpellPower(boostedLevel) × d.dmg()
     */
    public float getSpellPower(int storedLevel, LivingEntity caster) {
        AbstractSpell spell = getSpell();
        if (spell == null) {
            return 0f;
        }
        int boostedLevel = getBoostedLevel(storedLevel, caster);
        float base = spell.getSpellPower(boostedLevel, caster);
        return base * data.dmg();
    }

    /**
     * 获取法力消耗 = getManaCost(boostedLevel) × d.mana()
     */
    public int getManaCost(int storedLevel) {
        AbstractSpell spell = getSpell();
        if (spell == null) {
            return 0;
        }
        int boostedLevel = storedLevel + data.lvl();
        int base = spell.getManaCost(boostedLevel);
        return Math.round(base * data.mana());
    }

    /**
     * 获取冷却时间 = getSpellCooldown() × d.cd()
     */
    public int getSpellCooldown() {
        AbstractSpell spell = getSpell();
        if (spell == null) {
            return 0;
        }
        int base = spell.getSpellCooldown();
        return Math.round(base * data.cd());
    }

    /**
     * 获取吟唱时间 = getEffectiveCastTime(boostedLevel) × d.cast()
     */
    public int getEffectiveCastTime(int storedLevel, LivingEntity caster) {
        AbstractSpell spell = getSpell();
        if (spell == null) {
            return 0;
        }
        int boostedLevel = storedLevel + data.lvl();
        int base = spell.getEffectiveCastTime(boostedLevel, caster);
        return Math.round(base * data.cast());
    }

    // ==================== 工具方法 ====================

    /**
     * 获取当前槽位的法术
     */
    public AbstractSpell getSpell() {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (!ISpellContainer.isSpellContainer(stack)) {
            return null;
        }

        ISpellContainer container = ISpellContainer.get(stack);

        SpellData spellData;
        if (spellSlotIndex >= 0) {
            spellData = container.getSpellAtIndex(spellSlotIndex);
        } else {
            if (!container.isEmpty()) {
                spellData = container.getSpellAtIndex(0);
            } else {
                return null;
            }
        }

        if (spellData == null || spellData == SpellData.EMPTY) {
            return null;
        }
        return spellData.getSpell();
    }

    /**
     * 获取存储的原始等级
     */
    public int getStoredLevel() {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        if (!ISpellContainer.isSpellContainer(stack)) {
            return 0;
        }

        ISpellContainer container = ISpellContainer.get(stack);

        SpellData spellData;
        if (spellSlotIndex >= 0) {
            spellData = container.getSpellAtIndex(spellSlotIndex);
        } else {
            if (!container.isEmpty()) {
                spellData = container.getSpellAtIndex(0);
            } else {
                return 0;
            }
        }

        if (spellData == null || spellData == SpellData.EMPTY) {
            return 0;
        }
        return spellData.getLevel();
    }

    /**
     * 是否有重铸加成
     */
    public boolean hasReforge() {
        return data != null && !data.isDefault();
    }

    public ReforgeCache.Data getData() {
        return data;
    }

    public ItemStack getStack() {
        return stack;
    }

    public int getSpellSlotIndex() {
        return spellSlotIndex;
    }

    // ==================== 静态工具 ====================

    public static int calcBoostedLevel(int storedLevel, int lvlBonus) {
        return storedLevel + lvlBonus;
    }

    public static int calcModifiedMana(int baseMana, float manaMultiplier) {
        return Math.round(baseMana * manaMultiplier);
    }

    public static int calcModifiedCooldown(int baseCooldown, float cdMultiplier) {
        return Math.round(baseCooldown * cdMultiplier);
    }

    public static int calcModifiedCastTime(int baseCastTime, float castMultiplier) {
        return Math.round(baseCastTime * castMultiplier);
    }

    public static float calcModifiedPower(float basePower, float dmgMultiplier) {
        return basePower * dmgMultiplier;
    }
}
