package net.teujaem.randombox.gui;

import net.teujaem.randombox.RandomBoxPlugin;
import net.teujaem.randombox.entity.RandomBoxEntity;
import net.teujaem.randombox.entity.RewardEntity;
import net.teujaem.randombox.util.ItemCodec;
import net.teujaem.randombox.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** 플레이어에게 보여주는 보상/확률 목록. 편집 화면에서 미리보기로도 쓴다. */
public final class PreviewGui extends Gui {

    private final RandomBoxPlugin plugin;
    private final RandomBoxEntity box;
    /** 편집 화면에서 열었을 때만 채워진다. */
    private final BoxEditorGui parent;
    private boolean returningToParent;

    public PreviewGui(RandomBoxPlugin plugin, RandomBoxEntity box, BoxEditorGui parent) {
        this.plugin = plugin;
        this.box = box;
        this.parent = parent;

        int rows = Math.min(6, Math.max(2, (box.getRewards().size() + 8) / 9 + 1));
        this.inventory = Bukkit.createInventory(this, rows * 9, Text.of("&8확률: &r" + box.getDisplayName()));
        render();
    }

    private void render() {
        inventory.clear();
        int total = box.totalWeight();
        List<RewardEntity> rewards = box.getRewards();
        int capacity = inventory.getSize() - 9;

        for (int i = 0; i < Math.min(rewards.size(), capacity); i++) {
            RewardEntity reward = rewards.get(i);
            ItemStack item = ItemCodec.decode(reward.getItemData());
            if (item == null) {
                item = new ItemStack(Material.PAPER);
            } else {
                item = item.clone();
            }

            double percent = total <= 0 ? 0 : reward.getWeight() * 100.0 / total;
            ItemMeta meta = item.getItemMeta();
            if (reward.getLabel() != null && !reward.getLabel().isBlank()) {
                meta.displayName(Text.of(reward.getLabel()));
            }
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Text.of("&7확률: &e" + String.format("%.2f", percent) + "%"));
            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(i, item);
        }

        int lastRow = inventory.getSize() - 9;
        fill(Material.BLACK_STAINED_GLASS_PANE, lastRow, inventory.getSize() - 1);
        inventory.setItem(inventory.getSize() - 5, button(Material.BOOK, "&f" + box.getDisplayName(),
                "&7보상 " + rewards.size() + "종",
                "&8가중치 합계 " + total));

        if (parent != null) {
            inventory.setItem(inventory.getSize() - 9, button(Material.ARROW, "&f편집 화면으로 돌아가기"));
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (parent == null || !event.getInventory().equals(inventory)) {
            return;
        }
        if (event.getRawSlot() == inventory.getSize() - 9) {
            returningToParent = true;
            parent.open((Player) event.getWhoClicked());
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        if (parent == null) {
            return;
        }
        if (returningToParent) {
            returningToParent = false;
            return;
        }
        // 편집 화면은 이미 닫혀 있으므로 미저장 변경분을 여기서 넘겨 저장한다.
        parent.saveIfDirty((Player) event.getPlayer());
    }
}
