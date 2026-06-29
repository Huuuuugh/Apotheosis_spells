package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.ApotheosisSpells;
import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.api.SlotOnTakeState;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

/**
 * 拦截 Slot.onTake，在 InscriptionTableMenu 的 resultSlot（index=2）被取走时
 * 恢复剩余法术的 affix_data
 */
@Mixin(Slot.class)
public class SlotOnTakeMixin {

    @Inject(method = "onTake", at = @At("HEAD"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void onTakeHead(net.minecraft.world.entity.player.Player player, ItemStack taken, CallbackInfo ci) {
        Slot self = (Slot)(Object)this;
        ApotheosisSpells.LOGGER.info("[SlotOnTakeMixin] onTake HEAD: slot.index={}, container={}",
                self.index, self.container.getClass().getSimpleName());

        // resultSlot 的特征：container 是 ResultContainer
        // InscriptionTableMenu 中 TE slots: 36=spellBookSlot, 37=scrollSlot, 38=resultSlot
        if (!(self.container instanceof ResultContainer)) {
            ApotheosisSpells.LOGGER.info("[SlotOnTakeMixin] onTake: rejected - container is not ResultContainer, container={}",
                    self.container.getClass().getSimpleName());
            return;
        }

        ApotheosisSpells.LOGGER.info("[SlotOnTakeMixin] onTake: detected resultSlot! container=ResultContainer");

        // 检查是否有状态
        if (!SlotOnTakeState.isActive()) {
            ApotheosisSpells.LOGGER.info("[SlotOnTakeMixin] onTake: rejected - SlotOnTakeState not active");
            return;
        }

        int removedIndex = SlotOnTakeState.getRemovedIndex();
        List<CompoundTag> remainingData = SlotOnTakeState.getRemainingData();

        if (remainingData == null || remainingData.isEmpty()) {
            ApotheosisSpells.LOGGER.info("[SlotOnTakeMixin] onTake: rejected - remainingData empty");
            SlotOnTakeState.clear();
            return;
        }

        ApotheosisSpells.LOGGER.info("[SlotOnTakeMixin] onTake: restoring {} remaining affix_data, removedIndex={}",
                remainingData.size(), removedIndex);

        try {
            // 获取 spellBookSlot
            Slot spellBookSlot = null;
            try {
                spellBookSlot = ((InscriptionTableMenu) SlotOnTakeState.getMenu()).getSpellBookSlot();
            } catch (Exception e) {
                ApotheosisSpells.LOGGER.warn("[SlotOnTakeMixin] failed to get spellBookSlot: {}", e.getMessage());
                SlotOnTakeState.clear();
                return;
            }

            if (spellBookSlot == null) {
                SlotOnTakeState.clear();
                return;
            }

            ItemStack spellBookStack = spellBookSlot.getItem();
            if (spellBookStack.isEmpty() || !ISpellContainer.isSpellContainer(spellBookStack)) {
                SlotOnTakeState.clear();
                return;
            }

            // 恢复剩余法术的 affix_data
            CompoundTag bookNbt = spellBookStack.getOrCreateTag();
            CompoundTag containerNbt = bookNbt.getCompound(ISpellContainer.NBT);
            ListTag dataList = containerNbt.getList("data", 10);

            int restored = 0;
            for (int i = 0; i < dataList.size() && restored < remainingData.size(); i++) {
                CompoundTag slotTag = dataList.getCompound(i);
                int idx = slotTag.getInt("index");

                // 跳过被移除的 slot
                if (idx == removedIndex) continue;

                // 如果 slot 没有 affix_data，恢复它
                if (!slotTag.contains(ReforgeCache.SLOT_AFFIX_DATA)) {
                    CompoundTag toRestore = remainingData.get(restored);
                    if (toRestore != null && !toRestore.isEmpty()) {
                        slotTag.put(ReforgeCache.SLOT_AFFIX_DATA, toRestore.copy());

                        // 恢复 iss_reforge 缓存
                        CompoundTag issReforge = toRestore.getCompound(ReforgeCache.KEY);
                        if (issReforge != null && !issReforge.isEmpty()) {
                            slotTag.put(ReforgeCache.KEY, issReforge.copy());
                        }

                        dataList.set(i, slotTag);
                        restored++;
                        ApotheosisSpells.LOGGER.info("[SlotOnTakeMixin] onTake: restored affix_data for slot idx={}", idx);
                    }
                }
            }

            containerNbt.put("data", dataList);
            bookNbt.put(ISpellContainer.NBT, containerNbt);
            spellBookStack.setTag(bookNbt);

            ApotheosisSpells.LOGGER.info("[SlotOnTakeMixin] onTake: restored {} slots", restored);

        } catch (Exception e) {
            ApotheosisSpells.LOGGER.error("[SlotOnTakeMixin] onTake: restore failed: {}", e.toString());
        } finally {
            SlotOnTakeState.clear();
        }
    }
}
