package net.teujaem.randombox.gui;

import net.teujaem.randombox.RandomBoxPlugin;
import net.teujaem.randombox.entity.RandomBoxEntity;
import net.teujaem.randombox.util.ItemCodec;
import net.teujaem.randombox.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** 박스 자체의 설정. 값은 편집 화면(BoxEditorGui)이 들고 있는 인스턴스에 바로 반영한다. */
public final class BoxSettingsGui extends Gui {

    private static final int SLOT_NAME = 10;
    private static final int SLOT_BOX_ITEM = 11;
    private static final int SLOT_ROLL_COUNT = 12;
    private static final int SLOT_BROADCAST = 14;
    private static final int SLOT_ANIMATE = 15;
    private static final int SLOT_PREVIEW_SWAP = 16;
    private static final int SLOT_BACK = 22;

    private final RandomBoxPlugin plugin;
    private final BoxEditorGui parent;
    /** 편집 화면으로 되돌아가는 중인지. 이때는 저장하지 않는다(편집 화면이 계속 열려 있으므로). */
    private boolean returningToParent;

    public BoxSettingsGui(RandomBoxPlugin plugin, BoxEditorGui parent) {
        this.plugin = plugin;
        this.parent = parent;
        this.inventory = Bukkit.createInventory(this, 27, Text.of("&8박스 설정: &r" + parent.getBox().getDisplayName()));
        render();
    }

    private RandomBoxEntity box() {
        return parent.getBox();
    }

    public void render() {
        inventory.clear();
        fill(Material.GRAY_STAINED_GLASS_PANE, 0, 26);
        RandomBoxEntity box = box();

        inventory.setItem(SLOT_NAME, button(Material.NAME_TAG, "&b표시 이름",
                "&7현재: &r" + box.getDisplayName(),
                "&8id: " + box.getId(),
                "&e클릭해서 채팅으로 변경"));

        ItemStack boxItem = ItemCodec.decode(box.getBoxItemData());
        if (boxItem == null) {
            inventory.setItem(SLOT_BOX_ITEM, button(Material.CHEST, "&b상자 아이템 &7(기본 상자)",
                    "&f아래 내 인벤토리에서",
                    "&f쓰고 싶은 아이템을 클릭하세요.",
                    "",
                    "&7지금은 기본 상자(CHEST)를 씁니다."));
        } else {
            ItemStack display = boxItem.clone();
            display.setAmount(1);
            ItemMeta meta = display.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Text.of("&8────────────"));
            lore.add(Text.of("&7이 아이템을 상자로 씁니다."));
            lore.add(Text.of("&f아래 인벤토리 아이템을 클릭하면 교체"));
            lore.add(Text.of("&e우클릭 &c기본 상자로 되돌리기"));
            meta.lore(lore);
            display.setItemMeta(meta);
            inventory.setItem(SLOT_BOX_ITEM, display);
        }

        inventory.setItem(SLOT_ROLL_COUNT, button(Material.REPEATER, "&b한 번에 뽑는 개수",
                "&7현재: &f" + box.getRollCount() + "개",
                "&e좌클릭 &7+1 &8/ &e우클릭 &7-1"));

        inventory.setItem(SLOT_BROADCAST, toggleButton(box.isBroadcastWin(), "&b전체 방송",
                "당첨 결과를 서버 전체에 알립니다."));

        inventory.setItem(SLOT_ANIMATE, toggleButton(box.isAnimate(), "&b개봉 연출",
                "룰렛 연출 후 보상을 줍니다."));

        inventory.setItem(SLOT_PREVIEW_SWAP, toggleButton(box.isPreviewOnSwap(), "&b왼손 키로 확률 보기",
                "상자를 들고 F 를 누르면 확률표가 뜹니다."));

        inventory.setItem(SLOT_BACK, button(Material.ARROW, "&f보상 편집으로 돌아가기"));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        RandomBoxEntity box = box();

        // 아래쪽 내 인벤토리를 클릭하면 그 아이템을 상자 아이템으로 지정한다.
        if (event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory())) {
            setBoxItem(player, event.getCurrentItem());
            return;
        }

        switch (event.getRawSlot()) {
            case SLOT_NAME -> {
                askName(player);
                return;
            }
            case SLOT_BOX_ITEM -> {
                if (!event.getClick().isRightClick()) {
                    return; // 지정은 아래 인벤토리 클릭으로 한다
                }
                if (box.getBoxItemData() == null) {
                    return;
                }
                box.setBoxItemData(null);
                plugin.message(player, "&7상자 아이템을 기본 상자로 되돌렸습니다.");
            }
            case SLOT_ROLL_COUNT -> box.setRollCount(box.getRollCount() + (event.getClick().isRightClick() ? -1 : 1));
            case SLOT_BROADCAST -> box.setBroadcastWin(!box.isBroadcastWin());
            case SLOT_ANIMATE -> box.setAnimate(!box.isAnimate());
            case SLOT_PREVIEW_SWAP -> box.setPreviewOnSwap(!box.isPreviewOnSwap());
            case SLOT_BACK -> {
                returningToParent = true;
                parent.open(player);
                return;
            }
            default -> {
                return;
            }
        }

        parent.markDirty();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.5f);
        render();
    }

    @Override
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        // 편집 화면은 이미 닫혀 있다. 여기서 그냥 나가면 변경사항을 잃으므로 대신 저장해준다.
        if (returningToParent) {
            returningToParent = false;
            return;
        }
        Player player = (Player) event.getPlayer();
        if (plugin.getChatInputService().isWaiting(player.getUniqueId())) {
            return;
        }
        parent.saveIfDirty(player);
    }

    /** 클릭한 아이템을 상자 아이템으로 지정한다. 아이템은 그대로 남는다. */
    private void setBoxItem(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        ItemStack one = clicked.clone();
        one.setAmount(1);
        box().setBoxItemData(ItemCodec.encode(one));
        parent.markDirty();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
        render();
        plugin.message(player, "&a상자 아이템을 지정했습니다: &r" + one.getType().name().toLowerCase().replace('_', ' '));
    }

    private void askName(Player player) {
        plugin.message(player, "&e새 표시 이름을 채팅에 입력하세요. &7(색코드 &, 취소는 &f취소&7)");
        plugin.getChatInputService().await(player, "박스 이름", input -> {
            box().setDisplayName(input);
            parent.markDirty();
            parent.render();
            render();
            open(player);
            plugin.message(player, "&a이름을 변경했습니다: &r" + input);
        });
        player.closeInventory();
    }
}
