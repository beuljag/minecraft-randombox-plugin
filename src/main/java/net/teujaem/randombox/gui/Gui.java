package net.teujaem.randombox.gui;

import net.teujaem.randombox.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI 공통 뼈대.
 *
 * <p>InventoryHolder 를 직접 구현해서 "이 인벤토리가 우리 GUI 인지"를
 * 제목 문자열 비교 없이 판별한다. (제목 비교는 색코드/번역 때문에 잘 깨진다.)
 */
public abstract class Gui implements InventoryHolder {

    protected Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /** 클릭 처리. 호출한 리스너가 이미 이벤트를 취소한 상태로 들어온다. */
    public abstract void onClick(InventoryClickEvent event);

    public void onClose(InventoryCloseEvent event) {
    }

    // ------------------------------------------------------------- 도구

    protected static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.of(name));
        if (lore.length > 0) {
            List<net.kyori.adventure.text.Component> lines = new ArrayList<>(lore.length);
            for (String line : lore) {
                lines.add(Text.of(line));
            }
            meta.lore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }

    protected static ItemStack toggleButton(boolean on, String name, String description) {
        return button(on ? Material.LIME_DYE : Material.GRAY_DYE,
                name + (on ? " &a[켜짐]" : " &c[꺼짐]"),
                "&7" + description,
                "&e클릭해서 전환");
    }

    protected void fill(Material material, int from, int to) {
        ItemStack filler = button(material, " ");
        for (int slot = from; slot <= to; slot++) {
            inventory.setItem(slot, filler);
        }
    }
}
