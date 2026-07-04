package com.example.apotheosis_spells.api;

/**
 * 标记「当前线程正在为法术卷轴 roll 词条」的作用域开关。
 *
 * 背景：法术修饰词条是 {@code AffixType.POTION}（在 Apotheosis-Artifice 的 JEI 中显示为前缀页），
 * 但 Apotheosis 原版稀有度 JSON 的 rules 里没有任何 potion 规则，导致这些词条在
 * LootController.createLootItem（重铸台预览/成品、战利品注入共用入口）里永远 roll 不出来。
 *
 * 直接给 LootRarity.getRules() 无脑追加 POTION 规则会污染所有装备的 roll
 * （武器/护甲会额外多出原生药水词条），所以用本开关把注入限定在卷轴类别的 roll 过程内：
 * LootControllerMixin 在 createLootItem 进入时按类别置位，LootRarityRulesMixin 只在
 * 置位时扩充规则列表。
 */
public final class ScrollRollScope {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ScrollRollScope() {}

    public static void set(boolean value) {
        ACTIVE.set(value);
    }

    public static boolean active() {
        return ACTIVE.get();
    }
}
