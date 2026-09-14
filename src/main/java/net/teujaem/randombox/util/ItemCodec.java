package net.teujaem.randombox.util;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * ItemStack <-> Base64.
 *
 * <p>{@code ItemStack#serializeAsBytes()} 는 바닐라 NBT 를 그대로 쓰므로
 * 인챈트·커스텀 모델·PDC 까지 손실 없이 보존된다. (1.20.5+)
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static String encode(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    /** 디코드 실패 시 null. (마인크래프트 버전이 바뀌어 NBT 가 안 맞는 경우 등) */
    public static ItemStack decode(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return null;
        }
    }
}
