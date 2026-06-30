package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.handler.SpellCastHooks;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让重铸词缀（×法力 / ×冷却 / ×法术强度 / +等级）在 Iron's 的两段式施法里正确生效。
 *
 * 施法两段：
 *   1) attemptInitiateCast（触发那刻）——做"法力是否足够"判定 + 算施法时间 + initiateCast 记录等级；
 *   2) castSpell（由 MagicManager.tick 在后续 tick 调用）——真正扣法力 / 算伤害 / 上冷却。
 * 两段不在同一调用栈，所以两段都要各自把 ctx 设好，倍率钩子（getManaCost/getSpellPower/
 * getEffectiveCastTime RETURN、以及 MagicManagerMixin 的 getEffectiveSpellCooldown）才会生效。
 *
 * 词缀来源统一解析（卷轴 / 法术书直接施法 / 法杖等武器施法都覆盖）见 {@link #apoth_resolveCastContext}。
 */
@Mixin(value = AbstractSpell.class, remap = false)
public class CastMixin {

    /**
     * 等级 +N（真正生效的唯一入口）：在 attemptInitiateCast 抬高 spellLevel 参数。
     * initiateCast 会把它存进 castingSpellLevel，castSpell 全程用它 → 伤害/法力/施法时间/数量/范围等
     * 都按提升后的等级走原版曲线；本方法内的"法力判定"也因此用提升后等级（先涨再减）。
     */
    @ModifyVariable(method = "attemptInitiateCast", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int apoth_boostCastLevel(int spellLevel, ItemStack stack, int spellLevelArg, Level level,
                                     Player player, CastSource src, boolean triggerCooldown, String slot) {
        if (level.isClientSide || !(player instanceof ServerPlayer)) return spellLevel;
        SpellCastHooks.Context ctx = apoth_resolveCastContext(player, spellLevel, stack, slot, null);
        if (ctx == null || ctx.data().lvl() == 0) return spellLevel;
        return Math.max(1, spellLevel + ctx.data().lvl());
    }

    /**
     * attemptInitiateCast HEAD：设置 ctx，供本方法内的"法力是否足够"判定(canBeCastedBy→getManaCost)
     * 与施法时间(getEffectiveCastTime)应用 ×mana / ×cast —— 修复"减了消耗却没减判定"。
     * 此时 MagicData 尚未记录施法法术，按当前选中法术的槽位解析。
     */
    @Inject(method = "attemptInitiateCast", at = @At("HEAD"))
    private void onAttemptInitiateCastHead(ItemStack stack, int spellLevel, Level level, Player player,
                                           CastSource src, boolean triggerCooldown, String slot,
                                           CallbackInfoReturnable<Boolean> cir) {
        SpellCastHooks.clear();
        if (!(player instanceof ServerPlayer)) return;
        SpellCastHooks.Context ctx = apoth_resolveCastContext(player, spellLevel, stack, slot, null);
        if (ctx != null) SpellCastHooks.set(ctx);
    }

    /** attemptInitiateCast RETURN：清理本窗口 ctx，避免泄漏到后续 tick（真正施法的 ctx 由 castSpell HEAD 设）。 */
    @Inject(method = "attemptInitiateCast", at = @At("RETURN"))
    private void apoth_attemptInitiateCastReturn(ItemStack stack, int spellLevel, Level level, Player player,
                                                 CastSource src, boolean triggerCooldown, String slot,
                                                 CallbackInfoReturnable<Boolean> cir) {
        SpellCastHooks.clear();
    }

    /**
     * castSpell HEAD —— 真正的施法/扣费点（后续 tick 调用，与 attemptInitiateCast 不同栈）。
     * 重新解析并设置 ctx，使真正扣法力(getManaCost)、上冷却(getEffectiveSpellCooldown)、算伤害(getSpellPower)
     * 时倍率钩子生效，每次施法恰好一次。注意延迟施法时 getPlayerCastingItem 常已为空，统一解析里会回退。
     */
    @Inject(method = "castSpell", at = @At("HEAD"))
    private void apoth_castSiteSet(Level world, int spellLevel, ServerPlayer serverPlayer,
                                  CastSource castSource, boolean triggerCooldown, CallbackInfo ci) {
        SpellCastHooks.clear();
        if (serverPlayer == null) return;
        MagicData md = MagicData.getPlayerMagicData(serverPlayer);
        SpellCastHooks.Context ctx = apoth_resolveCastContext(
                serverPlayer, spellLevel, md.getPlayerCastingItem(), md.getCastingEquipmentSlot(), md.getCastingSpellId());
        if (ctx != null) SpellCastHooks.set(ctx);
    }

    /** castSpell RETURN：清理 ctx。 */
    @Inject(method = "castSpell", at = @At("RETURN"))
    private void onCastSpellReturn(Level world, int spellLevel, ServerPlayer serverPlayer,
                                   CastSource castSource, boolean triggerCooldown, CallbackInfo ci) {
        SpellCastHooks.clear();
    }

    // ===== 倍率钩子（均以 SpellCastHooks ctx 门控；ctx 由上面三处在对应阶段设置）=====
    // 原作者用 @Redirect(self) 自引用注入从不触发；改为 @Inject(RETURN) 直接改返回值。
    // 冷却由 MagicManagerMixin 在 getEffectiveSpellCooldown 一处统一施加，这里不处理以免双重应用。

    // 仅在"真正施法"(castContext=true)时施加倍率；显示路径的倍率由各显示 mixin 自身的 redirect 施加，
    // 全局钩子在显示时不重复应用，保证每条路径上倍率只计一次（口径一致）。

    /** 法力消耗 ×mana()。 */
    @Inject(method = "getManaCost", at = @At("RETURN"), cancellable = true)
    private void apoth_manaCost(int level, CallbackInfoReturnable<Integer> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || !ctx.castContext() || ctx.data() == null || ctx.data().mana() == 1f) return;
        cir.setReturnValue(Math.max(0, Math.round(cir.getReturnValueI() * ctx.data().mana())));
    }

    /** 法术强度（伤害）×dmg()。 */
    // 法术强度（伤害）×dmg()：不按 castContext 区分。tooltip/抄写台/法术轮盘的“伤害”行是经
    // getUniqueInfo→getDamage 嵌套调用 getSpellPower 算出的，显示路径的 redirect 拦不到那次嵌套调用，
    // 必须由本全局钩子统一施加。各显示 mixin 自身的 getSpellPower redirect 已去掉 ×dmg，避免重复。
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
        if (ctx == null || !ctx.castContext() || ctx.data() == null || ctx.data().cast() == 1f) return;
        cir.setReturnValue(Math.max(0, Math.round(cir.getReturnValueI() * ctx.data().cast())));
    }

    // ===== 统一解析 =====

    /**
     * 统一解析"本次施法应取词缀的来源"，返回可直接用的 ctx（无重铸返回 null）。覆盖：
     *   - 卷轴施法：词缀来自卷轴本身；
     *   - 法术书直接施法 / 法杖等武器施法：词缀来自玩家装备(Curios)或主/副手的法术书里被施放的那条法术。
     * castingSpellId 非空（castSpell 阶段）→ 按法术 id 精确匹配物理槽位；为空（attemptInitiateCast 阶段，
     * 此时尚未记录施法法术）→ 用当前选中法术的槽位。
     */
    private static SpellCastHooks.Context apoth_resolveCastContext(Player player, int spellLevel,
                                                                  ItemStack castStack, String slot, String castingSpellId) {
        ItemStack cast = (castStack != null && !castStack.isEmpty())
                ? castStack : resolveCastingStack(ItemStack.EMPTY, slot, player);
        if (cast != null && !cast.isEmpty() && cast.getItem() instanceof Scroll) {
            ReforgeCache.Data sd = ReforgeCache.getFromScroll(cast);
            return (sd == null || sd.isDefault()) ? null
                    : new SpellCastHooks.Context(cast, player, 0, spellLevel, sd, null, true);
        }
        ItemStack book = resolveCastingSpellBook(cast, player);
        if (book == null || book.isEmpty()) return null;
        int idx = (castingSpellId != null && !castingSpellId.isEmpty())
                ? apoth_indexBySpellId(book, castingSpellId, player)
                : ReforgeCache.resolveSelectedSpellIndex(book, player);
        ReforgeCache.Data d = ReforgeCache.getFromSpellBook(book, idx);
        return (d == null || d.isDefault()) ? null
                : new SpellCastHooks.Context(book, player, idx, spellLevel, d, null, true);
    }

    /** 用施放法术 id 匹配法术书物理槽位；匹配不到时回退选中槽位。 */
    private static int apoth_indexBySpellId(ItemStack book, String castingSpellId, Player player) {
        try {
            for (Object o : ISpellContainer.get(book).getActiveSpells()) {
                var ss = (io.redspace.ironsspellbooks.api.spells.SpellSlot) o;
                if (String.valueOf(ss.spellData().getSpell().getSpellId()).equals(castingSpellId)) return ss.index();
            }
        } catch (Exception ignored) {}
        try { return ReforgeCache.resolveSelectedSpellIndex(book, player); } catch (Exception e) { return 0; }
    }

    /** 本次施法取词缀的法术书：施法物品本身是书→用它；否则用玩家装备(Curios)/主手/副手的法术书。 */
    private static ItemStack resolveCastingSpellBook(ItemStack castItem, Player player) {
        if (castItem != null && castItem.getItem() instanceof SpellBook && ISpellContainer.isSpellContainer(castItem)) return castItem;
        ItemStack book = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(player);
        if (book != null && !book.isEmpty() && book.getItem() instanceof SpellBook && ISpellContainer.isSpellContainer(book)) return book;
        ItemStack mh = player.getMainHandItem();
        if (mh.getItem() instanceof SpellBook && ISpellContainer.isSpellContainer(mh)) return mh;
        ItemStack oh = player.getOffhandItem();
        if (oh.getItem() instanceof SpellBook && ISpellContainer.isSpellContainer(oh)) return oh;
        return ItemStack.EMPTY;
    }

    /** 从施法物品参数或施法装备槽解析出施法物品本体。 */
    private static ItemStack resolveCastingStack(ItemStack stack, String slot, Player player) {
        if (stack != null && !stack.isEmpty()) return stack;
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
