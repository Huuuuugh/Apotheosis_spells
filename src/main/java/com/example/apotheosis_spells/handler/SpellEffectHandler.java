package com.example.apotheosis_spells.handler;

import com.example.apotheosis_spells.ApotheosisSpells;
import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.api.SpellEffects;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「事件类特效」词条的统一结算处。监听 Iron's 的法术事件，从施法者手里的卷轴/书<b>实时解析</b>
 * 出 {@link SpellEffects}（不依赖 SpellCastHooks ThreadLocal —— 投射物飞出后命中那一刻施法上下文早已结束，
 * 唯有实时从施法者物品解析才能正确结算吸血/暴击/斩杀）。
 *
 * <p>第一梯队（伤害系）：吸血 / 法力虹吸 / 暴击 / 斩杀，均在 {@link SpellDamageEvent} 结算。
 * 后续梯队（超载/回响/免冷却/护盾/疾步…）在各自事件里追加。
 */
@Mod.EventBusSubscriber(modid = ApotheosisSpells.MODID)
public final class SpellEffectHandler {

    private SpellEffectHandler() {}

    /** 暴击伤害倍率。 */
    private static final float CRIT_MULT = 1.5f;
    /** 斩杀触发的目标血量阈值（占最大生命的比例）。 */
    private static final float EXECUTE_THRESHOLD = 0.25f;
    /** 回响递归保护：避免免费施法再次触发回响导致无限链。 */
    private static final ThreadLocal<Boolean> ECHOING = ThreadLocal.withInitial(() -> false);
    /** 施法疾步的持续时间（tick）。 */
    private static final int HASTE_DURATION = 100;

    @SubscribeEvent
    public static void onSpellDamage(SpellDamageEvent event) {
        SpellDamageSource src = event.getSpellDamageSource();
        if (src == null) return;
        // 施法者 = 伤害来源实体；仅对玩家用卷轴/书施放的法术生效。
        if (!(src.getEntity() instanceof Player player)) return;
        AbstractSpell spell = src.spell();
        if (spell == null) return;

        SpellEffects fx = resolveEffects(player, spell.getSpellId());
        if (fx.isEmpty()) return;

        float amount = event.getAmount();
        boolean changed = false;

        // 暴击
        if (fx.critChance() > 0 && player.getRandom().nextFloat() < fx.critChance()) {
            amount *= CRIT_MULT;
            changed = true;
        }
        // 斩杀：目标低血时增伤
        LivingEntity target = event.getEntity();
        if (fx.execute() > 0 && target != null && target.getMaxHealth() > 0
                && target.getHealth() / target.getMaxHealth() < EXECUTE_THRESHOLD) {
            amount *= (1f + fx.execute());
            changed = true;
        }
        if (changed) event.setAmount(amount);

        // 吸血 / 法力虹吸：按最终伤害结算
        if (fx.leech() > 0) {
            player.heal(amount * fx.leech());
        }
        if (fx.manaLeech() > 0) {
            MagicData md = MagicData.getPlayerMagicData(player);
            if (md != null) md.addMana(amount * fx.manaLeech());
        }
    }

    /**
     * 施法时（SpellOnCastEvent 由 AbstractSpell.castSpell 在扣蓝/onCast 之前 post，仅服务端真正施法）结算：
     *   - 施法护盾：刷新吸收护盾到 shield 点（不无限叠加）；
     *   - 施法疾步：短暂迅捷；
     *   - 回响：N% 几率免费再触发一次 onCast（onCast 自身不 post 本事件 → 不递归，ECHOING 再兜底）。
     */
    @SubscribeEvent
    public static void onSpellCast(SpellOnCastEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide) return;
        if (ECHOING.get()) return;

        SpellEffects fx = resolveEffects(player, event.getSpellId());
        if (fx.isEmpty()) return;

        // 施法护盾
        if (fx.shield() > 0 && player.getAbsorptionAmount() < fx.shield()) {
            player.setAbsorptionAmount(fx.shield());
        }
        // 施法疾步
        if (fx.haste() > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, HASTE_DURATION, fx.haste() - 1, false, true, true));
        }
        // 回响
        if (fx.echo() > 0 && player.getRandom().nextFloat() < fx.echo()) {
            AbstractSpell spell = SpellRegistry.REGISTRY.get().getValue(new ResourceLocation(event.getSpellId()));
            if (spell == null) return;
            MagicData md = MagicData.getPlayerMagicData(player);
            if (md == null) return;
            ECHOING.set(true);
            try {
                spell.onCast(player.level(), event.getSpellLevel(), player, event.getCastSource(), md);
            } catch (Exception e) {
                ApotheosisSpells.LOGGER.debug("[SpellEffectHandler] echo onCast failed: {}", e.toString());
            } finally {
                ECHOING.set(false);
            }
        }
    }

    // ===== 从施法者解析当前法术的特效词条 =====

    /** 供 mixin（连发 getRecastCount 等）调用：从施法实体解析特效。非玩家返回 NONE。 */
    public static SpellEffects resolveEffectsFor(LivingEntity caster, String spellId) {
        if (caster instanceof Player p) return resolveEffects(p, spellId);
        return SpellEffects.NONE;
    }

    /** 优先手持卷轴（其唯一法术匹配）；否则装备/主/副手的法术书里匹配 spellId 的那条法术。 */
    private static SpellEffects resolveEffects(Player player, String spellId) {
        if (spellId == null || spellId.isEmpty()) return SpellEffects.NONE;

        for (ItemStack hand : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (hand.getItem() instanceof Scroll && ISpellContainer.isSpellContainer(hand)) {
                SpellData sd = ISpellContainer.get(hand).getSpellAtIndex(0);
                if (sd != null && sd.getSpell() != null && spellId.equals(sd.getSpell().getSpellId())) {
                    return ReforgeCache.getEffectsFromScroll(hand);
                }
            }
        }

        ItemStack book = resolveBook(player);
        if (book != null && !book.isEmpty()) {
            int idx = indexBySpellId(book, spellId);
            if (idx >= 0) return ReforgeCache.getEffectsFromSpellBook(book, idx);
        }
        return SpellEffects.NONE;
    }

    private static ItemStack resolveBook(Player player) {
        ItemStack b = Utils.getPlayerSpellbookStack(player);
        if (b != null && !b.isEmpty() && b.getItem() instanceof SpellBook && ISpellContainer.isSpellContainer(b)) return b;
        ItemStack mh = player.getMainHandItem();
        if (mh.getItem() instanceof SpellBook && ISpellContainer.isSpellContainer(mh)) return mh;
        ItemStack oh = player.getOffhandItem();
        if (oh.getItem() instanceof SpellBook && ISpellContainer.isSpellContainer(oh)) return oh;
        return ItemStack.EMPTY;
    }

    private static int indexBySpellId(ItemStack book, String spellId) {
        try {
            for (Object o : ISpellContainer.get(book).getActiveSpells()) {
                SpellSlot ss = (SpellSlot) o;
                if (spellId.equals(ss.spellData().getSpell().getSpellId())) return ss.index();
            }
        } catch (Exception e) {
            ApotheosisSpells.LOGGER.debug("[SpellEffectHandler] indexBySpellId failed: {}", e.toString());
        }
        return -1;
    }
}
