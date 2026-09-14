package net.teujaem.randombox.util;

import net.teujaem.randombox.entity.RandomBoxEntity;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 아이템에 "이건 어느 박스인가" 표식을 심고 읽는 곳.
 * 표식은 PDC 에 들어가므로 이름을 바꾸거나 옮겨도 유지된다.
 */
public final class BoxKeys {

    private final NamespacedKey boxItemKey;

    public BoxKeys(Plugin plugin) {
        this.boxItemKey = new NamespacedKey(plugin, "box_id");
    }

    /** 우클릭하면 열리는 "박스 아이템"에 박스 id 를 심는다. */
    public ItemStack tagAsBox(ItemStack item, String boxId) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(boxItemKey, PersistentDataType.STRING, boxId);
        item.setItemMeta(meta);
        return item;
    }

    /** 박스 아이템이면 그 박스 id, 아니면 null. */
    public String readBoxId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(boxItemKey, PersistentDataType.STRING);
    }

    /**
     * 지급할 박스 아이템을 만든다.
     *
     * <p>박스에 아이템이 지정돼 있으면 그걸 그대로 쓰고(이름·인챈트 등 보존),
     * 없으면 기본 상자에 박스 이름을 붙여서 만든다.
     * 어느 쪽이든 박스 id 표식은 반드시 심는다.
     */
    public ItemStack buildBoxItem(RandomBoxEntity box, int amount) {
        ItemStack item = ItemCodec.decode(box.getBoxItemData());
        if (item == null) {
            item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Text.of(box.getDisplayName()));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Text.of("&7우클릭해서 개봉"));
            lore.add(Text.of("&7웅크리고 우클릭하면 &f10연차"));
            lore.add(Text.of("&8개봉한 만큼 소모됩니다"));
            meta.lore(lore);
            item.setItemMeta(meta);
        } else {
            item = item.clone();
        }
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return tagAsBox(item, box.getId());
    }
}
