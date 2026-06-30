package com.example.apotheosis_spells.api;

import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * 学派 名称 ↔ id(1..9) ↔ SchoolRegistry 资源 的映射。id 顺序与 CastMixin.apoth_schoolIdToResource 一致：
 * 1=fire 2=ice 3=lightning 4=holy 5=ender 6=blood 7=evocation 8=nature 9=eldritch。
 */
public final class Schools {
    private Schools() {}

    /** JSON 里 "school" 字段(名称) → id；未知返回 0。 */
    public static int idFromName(String name) {
        if (name == null) return 0;
        switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "fire": return 1;
            case "ice": return 2;
            case "lightning": return 3;
            case "holy": return 4;
            case "ender": return 5;
            case "blood": return 6;
            case "evocation": return 7;
            case "nature": return 8;
            case "eldritch": return 9;
            default: return 0;
        }
    }

    /** id(1..9) → SchoolRegistry 资源；越界返回 null。 */
    public static ResourceLocation resource(int id) {
        switch (id) {
            case 1: return SchoolRegistry.FIRE_RESOURCE;
            case 2: return SchoolRegistry.ICE_RESOURCE;
            case 3: return SchoolRegistry.LIGHTNING_RESOURCE;
            case 4: return SchoolRegistry.HOLY_RESOURCE;
            case 5: return SchoolRegistry.ENDER_RESOURCE;
            case 6: return SchoolRegistry.BLOOD_RESOURCE;
            case 7: return SchoolRegistry.EVOCATION_RESOURCE;
            case 8: return SchoolRegistry.NATURE_RESOURCE;
            case 9: return SchoolRegistry.ELDRITCH_RESOURCE;
            default: return null;
        }
    }
}
