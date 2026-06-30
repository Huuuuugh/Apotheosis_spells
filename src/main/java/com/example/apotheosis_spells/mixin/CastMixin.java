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
     * castSpell RETURN：清理 ctx
     */
    @Inject(method = "castSpell", at = @At("RETURN"))
    private void onCastSpellReturn(Level world, int spellLevel, ServerPlayer serverPlayer,
                                   CastSource castSource, boolean triggerCooldown, CallbackInfo ci) {
        ApotheosisSpells.LOGGER.info("{} castSpell RETURN: player={}, spellLevel={}, src={}",
                PREFIX, serverPlayer.getName().getString(), spellLevel, castSource);
        SpellCastHooks.clear();
    }

    // ===== Apothic Spells 修复 =====
    // 原作者用 @Redirect(method="getX", target="getX") 是自引用注入：在 getX 方法体里找对 getX 的调用，
    // 但这些方法不调用自己 → 注入 0 次、从不触发 → 法力/法强/施法时间/等级加成"显示有但不生效"。
    // 改为 @Inject(at=RETURN) 直接修改返回值（与 MagicManagerMixin 处理冷却同一套正确写法）。
    // 冷却由 MagicManagerMixin 统一处理，这里不再处理以免双重应用。

    /** 法术等级 +N：抬高 getLevelFor 的返回值，使施法全程（伤害/法力/施法时间…）都按 boosted 等级计算。 */
    @Inject(method = "getLevelFor", at = @At("RETURN"), cancellable = true)
    private void apoth_boostLevel(int level, LivingEntity caster, CallbackInfoReturnable<Integer> cir) {
        if (!(caster instanceof Player player)) return;
        // 仅服务端实际施法时抬高等级；客户端 tooltip 的等级显示由作者原有的 TooltipUtils 逻辑负责，
        // 否则会与其叠加导致"+N"被计算两次。
        if (player.level().isClientSide) return;
        ReforgeCache.Data d = resolveHeldData(player);
        if (d == null || d.lvl() == 0) return;
        cir.setReturnValue(Math.max(1, cir.getReturnValueI() + d.lvl()));
    }

    /** 法力消耗 ×mana()（ctx 在 attemptInitiateCast→castSpell 窗口内有效）。 */
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
