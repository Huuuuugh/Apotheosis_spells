package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.api.ScrollRollScope;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixType;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 仅在 {@link ScrollRollScope} 生效（= 正在为法术卷轴 roll 词条）时，
 * 给稀有度规则列表按 STAT 规则镜像追加等概率的 POTION 规则：
 * STAT 槽 roll 学派法强属性词条（后缀），POTION 槽 roll 法术修饰词条（前缀），
 * 两类词条密度与原版装备一致，且互不挤占。
 *
 * 朋友版的 LootRarityGetRulesMixin 用 @At("HEAD") 读 getReturnValue() 恒为 null，
 * 是一个从未生效的 no-op；本实现改为 RETURN 注入并加了作用域限定。
 */
@Mixin(value = LootRarity.class, remap = false)
public class LootRarityRulesMixin {

    @Inject(method = "getRules", at = @At("RETURN"), cancellable = true, remap = false)
    private void apothSpells$scrollPotionRules(CallbackInfoReturnable<List<LootRarity.LootRule>> cir) {
        if (!ScrollRollScope.active()) return;
        List<LootRarity.LootRule> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;
        List<LootRarity.LootRule> extended = new ArrayList<>(original);
        for (LootRarity.LootRule rule : original) {
            if (rule.type() == AffixType.STAT) {
                extended.add(new LootRarity.LootRule(AffixType.POTION, rule.chance()));
            }
        }
        cir.setReturnValue(extended);
    }
}
