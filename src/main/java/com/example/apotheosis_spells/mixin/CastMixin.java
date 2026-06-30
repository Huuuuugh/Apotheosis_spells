package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.ApotheosisSpells;
import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.handler.SpellCastHooks;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractSpell.class, remap = false)
public class CastMixin {

    private static final String PREFIX = "[CastMixin]";

    /**
     * attemptInitiateCast HEAD：设置 ctx
     */
    @Inject(method = "attemptInitiateCast", at = @At("HEAD"))
    private void onAttemptInitiateCastHead(ItemStack stack, int spellLevel, Level level,
                                          Player player, CastSource src,
                                          boolean triggerCooldown, String slot,
                                          CallbackInfoReturnable<Boolean> cir) {
        SpellCastHooks.clear();
        if (player == null || !(player instanceof ServerPlayer)) return;

        ApotheosisSpells.LOGGER.info("{} attemptInitiateCast HEAD: player={}, spellLevel={}, src={}, slot={}",
                PREFIX, player.getName().getString(), spellLevel, src, slot);

        ItemStack castingStack = resolveCastingStack(stack, slot, player);
        if (castingStack.isEmpty()) {
            ApotheosisSpells.LOGGER.info("{}   castingStack is empty", PREFIX);
            return;
        }

        int spellSlotIndex = -1;
        ItemStack affixStack = castingStack;
        if (castingStack.getItem() instanceof SpellBook) {
            spellSlotIndex = 0;
            ApotheosisSpells.LOGGER.info("{}   source: SpellBook", PREFIX);
        } else if (castingStack.getItem() instanceof Scroll) {
            spellSlotIndex = 0;
            ApotheosisSpells.LOGGER.info("{}   source: Scroll", PREFIX);
        } else {
            ApotheosisSpells.LOGGER.info("{}   source: {}", PREFIX, castingStack.getItem().getClass().getSimpleName());
            ItemStack spellbook = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(player);
            if (spellbook == null || spellbook.isEmpty() || !(spellbook.getItem() instanceof SpellBook)) {
                ItemStack mainHand = player.getMainHandItem();
                if (mainHand.getItem() instanceof SpellBook) {
                    spellbook = mainHand;
                } else {
                    ItemStack offHand = player.getOffhandItem();
                    if (offHand.getItem() instanceof SpellBook) {
                        spellbook = offHand;
                    }
                }
            }
            if (spellbook != null && !spellbook.isEmpty() && spellbook.getItem() instanceof SpellBook) {
                affixStack = spellbook;
                try {
                    spellSlotIndex = ReforgeCache.resolveSelectedSpellIndex(affixStack, player);
                } catch (Exception ignored) {}
            }
        }

        ReforgeCache.Data d = ReforgeCache.Data.DEF;
        if (affixStack.getItem() instanceof SpellBook && ISpellContainer.isSpellContainer(affixStack)) {
            d = ReforgeCache.getFromSpellBook(affixStack, spellSlotIndex);
            if (d.isDefault()) {
                try {
                    int selIdx = ReforgeCache.resolveSelectedSpellIndex(affixStack, player);
                    d = ReforgeCache.getFromSpellBook(affixStack, selIdx);
                } catch (Exception ignored) {}
            }
        } else if (affixStack.getItem() instanceof Scroll) {
            d = ReforgeCache.getFromScroll(affixStack);
        }

        ApotheosisSpells.LOGGER.info("{}   affixStack: {}, data={}", PREFIX, affixStack.getItem().getClass().getSimpleName(), d);

        if (d == null) return;

        SpellData castingSpellData = null;
        if (ISpellContainer.isSpellContainer(affixStack)) {
            castingSpellData = ISpellContainer.get(affixStack).getSpellAtIndex(0);
        }
        SpellCastHooks.set(new SpellCastHooks.Context(affixStack, player, spellSlotIndex, spellLevel, d, castingSpellData));

        ApotheosisSpells.LOGGER.info("{}   ctx set: spellSlotIndex={}, spellLevel={}, data=lvl={}, dmg={}, mana={}, cd={}, cast={}",
                PREFIX, spellSlotIndex, spellLevel, d.lvl(), d.dmg(), d.mana(), d.cd(), d.cast());
    }

    /**
     * attemptInitiateCast RETURN：清理本窗口内为"法力检查 / 施法时间"设置的 ctx，避免泄漏影响
     * 后续 tick 的其它计算（真正施法的 ctx 由下面 castSpell HEAD 重新解析设置）。
     */
    @Inject(method = "attemptInitiateCast", at = @At("RETURN"))
    private void apoth_attemptInitiateCastReturn(ItemStack stack, int spellLevel, Level level, Player player,
                                                 CastSource src, boolean triggerCooldown, String slot,
                                                 CallbackInfoReturnable<Boolean> cir) {
        SpellCastHooks.clear();
    }

    /**
     * castSpell HEAD —— 真正的施法/扣费点。它由 MagicManager.tick 在后续 tick 调用，和 attemptInitiateCast
     * 不在同一调用栈，所以 attemptInitiateCast 设置的 ThreadLocal ctx 早已失效。这里直接从
     * MagicData.getPlayerCastingItem() 解析本次施法物品的词缀并设置 ctx，使真正扣法力(getManaCost)与真正
     * 上冷却(addCooldown→getEffectiveSpellCooldown)时，现有 ctx 门控钩子 apoth_manaCost / MagicManagerMixin
     * 才会生效。根治"显示打了折、实际不打折"（法力 48 vs 24、冷却不打折）。每次施法恰好应用一次。
     */
    @Inject(method = "castSpell", at = @At("HEAD"))
    private void apoth_castSiteSet(Level world, int spellLevel, ServerPlayer serverPlayer,
                                  CastSource castSource, boolean triggerCooldown, CallbackInfo ci) {
        SpellCastHooks.clear();
        if (serverPlayer == null) return;
        MagicData md = MagicData.getPlayerMagicData(serverPlayer);
        ItemStack item = md.getPlayerCastingItem();
        if (item == null || item.isEmpty()) return;
        ReforgeCache.Data d;
        int idx = -1;
        if (item.getItem() instanceof Scroll) {
            d = ReforgeCache.getFromScroll(item);
        } else if (item.getItem() instanceof SpellBook && ISpellContainer.isSpellContainer(item)) {
            idx = resolveCastingPhysicalIndex(item, md, serverPlayer);
            d = ReforgeCache.getFromSpellBook(item, idx);
        } else {
            return;
        }
        if (d == null || d.isDefault()) return;
        SpellCastHooks.set(new SpellCastHooks.Context(item, serverPlayer, idx, spellLevel, d, null));
    }

    /**
     * castSpell RETURN：清理 ctx
     */
    @Inject(method = "castSpell", at = @At("RETURN"))
    private void onCastSpellReturn(Level world, int spellLevel, ServerPlayer serverPlayer,
                                   CastSource castSource, boolean triggerCooldown, CallbackInfo ci) {
        SpellCastHooks.clear();
    }

    // ===== Apothic Spells 修复 =====
    // 原作者用 @Redirect(method="getX", target="getX") 是自引用注入：在 getX 方法体里找对 getX 的调用，
    // 但这些方法不调用自己 → 注入 0 次、从不触发 → 法力/法强/施法时间/等级加成"显示有但不生效"。
    // 改为 @Inject(at=RETURN) 直接修改返回值（与 MagicManagerMixin 处理冷却同一套正确写法）。
    // 冷却由 MagicManagerMixin 统一处理，这里不再处理以免双重应用。

    /**
     * 法术等级 +N（真正生效的入口）：在施法入口 attemptInitiateCast 直接抬高 spellLevel 参数。
     *
     * 注意 castSpell/onCast 全程使用传入的 spellLevel，<b>从不调用 getLevelFor</b>（getLevelFor 在
     * AbstractSpell 内无任何调用者，只服务于显示）。因此只有在这里抬高 spellLevel，伤害(getSpellPower)、
     * 法力(getManaCost)、施法时间(getEffectiveCastTime)、以及 onCast 里按 spellLevel 直接计算的
     * 弹射物数量/范围/持续时间等，才会全部按提升后的等级走原版的等级曲线 —— 等同于把卷轴升 N 级。
     * 与单独的 dmg/mana/cast 倍率词缀是相互独立的两条线。
     */
    @ModifyVariable(method = "attemptInitiateCast", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int apoth_boostCastLevel(int spellLevel, ItemStack stack, int spellLevelArg, Level level,
                                     Player player, CastSource src, boolean triggerCooldown, String slot) {
        if (level.isClientSide || !(player instanceof ServerPlayer)) return spellLevel;
        ReforgeCache.Data d = resolveCastData(stack, slot, player);
        if (d == null || d.lvl() == 0) return spellLevel;
        return Math.max(1, spellLevel + d.lvl());
    }

    // 注意：不要再钩 getLevelFor 来抬等级。施法路径的调用方 Utils.serverSideInitiateCast 会先调
    // getLevelFor(stored) 再把结果传给 attemptInitiateCast，若在 getLevelFor 也 +lvl，就会和下面的
    // apoth_boostCastLevel 叠加成 +2*lvl（实际施法等级翻倍）。等级 boost 只在 apoth_boostCastLevel
    // 一处进行；显示路径的等级由 TooltipUtils / InscriptionTableScreen / SpellWheel 各自的 mixin 负责。

    /** 法力消耗 ×mana()（ctx 在 castSpell 窗口内由 apoth_castSiteSet 设置）。 */
    @Inject(method = "getManaCost", at = @At("RETURN"), cancellable = true)
    private void apoth_manaCost(int level, CallbackInfoReturnable<Integer> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().mana() == 1f) return;
        cir.setReturnValue(Math.max(0, Math.round(cir.getReturnValueI() * ctx.data().mana())));
    }

    /** 法术强度（伤害）×dmg()。 */
    @Inject(method = "getSpellPower", at = @At("RETURN"), cancellable = true)
    private void apoth_spellPower(int spellLevel, net.minecraft.world.entity.Entity source, CallbackInfoReturnable<Float> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().dmg() == 1f) return;
        cir.setReturnValue(cir.getReturnValueF() * ctx.data().dmg());
    }

    /** 施法时间 ×cast()。 */
    @Inject(method = "getEffectiveCastTime", at = @At("RETURN"), cancellable = true)
    private void apoth_castTime(int spellLevel, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().cast() == 1f) return;
        cir.setReturnValue(Math.max(0, Math.round(cir.getReturnValueI() * ctx.data().cast())));
    }

    /** 用 MagicData 当前正在施放的法术 id 匹配法术书的物理槽位，得到正确的词缀键（避免硬编码 index 0）。 */
    private static int resolveCastingPhysicalIndex(ItemStack book, MagicData md, Player player) {
        try {
            String castingId = md.getCastingSpellId();
            if (castingId != null && !castingId.isEmpty()) {
                for (Object o : ISpellContainer.get(book).getActiveSpells()) {
                    var ss = (io.redspace.ironsspellbooks.api.spells.SpellSlot) o;
                    if (String.valueOf(ss.spellData().getSpell().getSpellId()).equals(castingId)) return ss.index();
                }
            }
        } catch (Exception ignored) {}
        try { return ReforgeCache.resolveSelectedSpellIndex(book, player); } catch (Exception e) { return 0; }
    }

    /** 施法入口解析词缀数据：优先按本次施法物品(卷轴/法术书)，否则回退到持握/装备的法术书。 */
    private static ReforgeCache.Data resolveCastData(ItemStack stack, String slot, Player player) {
        ItemStack cast = resolveCastingStack(stack, slot, player);
        if (cast != null && !cast.isEmpty()) {
            if (cast.getItem() instanceof Scroll) return ReforgeCache.getFromScroll(cast);
            if (cast.getItem() instanceof SpellBook && ISpellContainer.isSpellContainer(cast)) {
                return ReforgeCache.getFromSpellBook(cast, ReforgeCache.resolveSelectedSpellIndex(cast, player));
            }
        }
        return resolveHeldData(player);
    }

    /** getLevelFor 在 ctx 设置之前调用，故等级加成从玩家当前持握/装备的卷轴或法术书解析。 */
    private static ReforgeCache.Data resolveHeldData(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof Scroll) return ReforgeCache.getFromScroll(main);
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof Scroll) return ReforgeCache.getFromScroll(off);
        ItemStack book = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(player);
        if (book != null && !book.isEmpty() && book.getItem() instanceof SpellBook) {
            return ReforgeCache.getFromSpellBook(book, ReforgeCache.resolveSelectedSpellIndex(book, player));
        }
        return null;
    }

    private static ItemStack resolveCastingStack(ItemStack stack, String slot, Player player) {
        ItemStack castingStack = stack;
        if (castingStack != null && !castingStack.isEmpty()) return castingStack;
        if (slot == null) return ItemStack.EMPTY;
        if (slot.equals(io.redspace.ironsspellbooks.compat.Curios.SPELLBOOK_SLOT)) {
            return io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(player);
        }
        if ("mainhand".equals(slot)) return player.getMainHandItem();
        if ("offhand".equals(slot)) return player.getOffhandItem();
        if ("head".equals(slot)) return player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if ("chest".equals(slot)) return player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        if ("legs".equals(slot)) return player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
        if ("feet".equals(slot)) return player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
        return ItemStack.EMPTY;
    }
}
