package com.example.apotheosis_spells;

import com.example.apotheosis_spells.gem.GemRegistryHook;
import com.example.apotheosis_spells.handler.ScrollLootCategory;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ApotheosisSpells.MODID)
public class ApotheosisSpells {

    public static final String MODID = "apotheosis_spells";
    public static final Logger LOGGER = LogManager.getLogger();

    public ApotheosisSpells() {
        // 注册两个 LootCategory：scroll（作用于 Scroll 本体）和 spellbook_slot（作用于 SpellBook 内 SpellSlot）
        ScrollLootCategory.register();
        // 注册默认 SpellGemBonus
        GemRegistryHook.registerDefaults();
        LOGGER.info("Apotheosis Spells loaded.");
    }
}
