package net.teujaem.randombox.gui;

import net.teujaem.randombox.RandomBoxPlugin;
import net.teujaem.randombox.entity.RandomBoxEntity;
import net.teujaem.randombox.entity.RewardEntity;
import net.teujaem.randombox.util.ItemCodec;
import net.teujaem.randombox.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 보상 편집 화면.
 *
 * <p>보상 추가는 <b>아래쪽 내 인벤토리에서 아이템을 클릭</b>하는 방식이다.
 * 드래그로 넣게 하면 인벤토리 복사 버그가 나기 쉬워서, 클릭은 전부 취소하고
 * 아이템 정보만 복사해 온다. 아이템은 인벤토리에 그대로 남는다.
 */
public final class BoxEditorGui extends Gui {

    private static final int REWARD_SLOTS = 45;

    private static final int SLOT_ADD_ITEM = 45;
    private static final int SLOT_ADD_COMMAND = 46;
    private static final int SLOT_SETTINGS = 48;
    private static final int SLOT_SAVE = 49;
    private static final int SLOT_PREVIEW = 50;
    private static final int SLOT_CLOSE = 53;

    private final RandomBoxPlugin plugin;
    private final Player viewer;
    private RandomBoxEntity box;

    private boolean dirty;
    /** 저장 중에는 닫기 자동저장이 두 번 돌지 않게 막는다. */
    private boolean saving;
    /**
     * 하위 GUI 로 넘어가는 중인지. 이때 자동저장이 돌면 merge 결과로 box 인스턴스가
     * 교체되면서, 하위 GUI 가 붙잡고 있던 옛 인스턴스에 가한 수정이 날아간다.
     */
    private boolean navigating;

    public BoxEditorGui(RandomBoxPlugin plugin, Player viewer, RandomBoxEntity box) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.box = box;
        this.inventory = Bukkit.createInventory(this, 54, Text.of("&8보상 편집: &r" + box.getDisplayName()));
        render();
    }

    public RandomBoxEntity getBox() {
        return box;
    }

    // ------------------------------------------------------------ 그리기

    public void render() {
        inventory.clear();

        List<RewardEntity> rewards = box.getRewards();
        int total = box.totalWeight();

        for (int i = 0; i < Math.min(rewards.size(), REWARD_SLOTS); i++) {
            inventory.setItem(i, describe(rewards.get(i), total));
        }

        fill(Material.BLACK_STAINED_GLASS_PANE, 45, 53);

        inventory.setItem(SLOT_ADD_ITEM, button(Material.CHEST, "&a보상 추가하는 법",
                "&f아래 내 인벤토리에서",
                "&f넣고 싶은 아이템을 클릭하세요.",
                "",
                "&7개수까지 그대로 저장되고,",
                "&7아이템은 없어지지 않습니다.",
                "&8기본 가중치 10"));

        inventory.setItem(SLOT_ADD_COMMAND, button(Material.COMMAND_BLOCK, "&a명령어 보상 추가",
                "&7당첨 시 콘솔에서 실행할 명령어를",
                "&7채팅으로 입력받습니다.",
                "&8%player% 가 닉네임으로 치환됩니다."));

        inventory.setItem(SLOT_SETTINGS, button(Material.COMPARATOR, "&b박스 설정",
                "&7이름, 뽑기 개수, 방송, 연출"));

        inventory.setItem(SLOT_SAVE, button(dirty ? Material.EMERALD_BLOCK : Material.EMERALD,
                dirty ? "&a저장 &e(변경사항 있음)" : "&a저장됨",
                "&7DB 에 즉시 반영합니다.",
                "&8창을 닫아도 자동 저장됩니다."));

        inventory.setItem(SLOT_PREVIEW, button(Material.SPYGLASS, "&e확률 미리보기",
                "&7플레이어에게 보이는 화면"));

        inventory.setItem(SLOT_CLOSE, button(Material.BARRIER, "&c닫기"));
    }

    /** 보상 아이템 위에 가중치/확률/조작법을 얹어서 보여준다. */
    private ItemStack describe(RewardEntity reward, int totalWeight) {
        ItemStack item = ItemCodec.decode(reward.getItemData());
        if (item == null) {
            item = new ItemStack(Material.PAPER);
        } else {
            item = item.clone();
        }

        double percent = totalWeight <= 0 ? 0 : reward.getWeight() * 100.0 / totalWeight;

        ItemMeta meta = item.getItemMeta();
        if (reward.getLabel() != null && !reward.getLabel().isBlank()) {
            meta.displayName(Text.of(reward.getLabel()));
        }

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Text.of("&8────────────"));
        lore.add(Text.of("&7가중치: &f" + reward.getWeight() + " &8(&e" + String.format("%.2f", percent) + "%&8)"));
        if (reward.getCommandLine() != null && !reward.getCommandLine().isBlank()) {
            lore.add(Text.of("&7명령어: &f/" + reward.getCommandLine()));
        }
        lore.add(Text.of("&8────────────"));
        lore.add(Text.of("&e좌클릭 &7가중치 +1 &8/ &e쉬프트 &7+10"));
        lore.add(Text.of("&e우클릭 &7가중치 -1 &8/ &e쉬프트 &7-10"));
        lore.add(Text.of("&e가운데클릭 &7표시 이름 지정"));
        lore.add(Text.of("&eQ(버리기) &c이 보상 삭제"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------ 클릭

    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        // 아래쪽(플레이어 본인 인벤토리)을 클릭하면 그 아이템을 보상으로 넣는다.
        // getInventory() 는 위쪽 창을 돌려주므로, 어디를 눌렀는지는
        // getClickedInventory() 로 봐야 한다.
        if (event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory())) {
            addReward(player, event.getCurrentItem());
            return;
        }

        int slot = event.getRawSlot();

        if (slot < REWARD_SLOTS) {
            handleRewardClick(player, slot, event.getClick());
            return;
        }

        switch (slot) {
            case SLOT_ADD_ITEM -> plugin.message(player, "&e아래 내 인벤토리에서 아이템을 클릭하면 보상으로 추가됩니다.");
            case SLOT_ADD_COMMAND -> askCommand(player);
            case SLOT_SETTINGS -> openChild(player, new BoxSettingsGui(plugin, this));
            case SLOT_SAVE -> save(player, false);
            case SLOT_PREVIEW -> openChild(player, new PreviewGui(plugin, box, this));
            case SLOT_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleRewardClick(Player player, int slot, ClickType click) {
        List<RewardEntity> rewards = box.getRewards();
        if (slot >= rewards.size()) {
            return;
        }
        RewardEntity reward = rewards.get(slot);

        switch (click) {
            case LEFT -> changeWeight(reward, 1);
            case SHIFT_LEFT -> changeWeight(reward, 10);
            case RIGHT -> changeWeight(reward, -1);
            case SHIFT_RIGHT -> changeWeight(reward, -10);
            case MIDDLE -> {
                askLabel(player, reward);
                return;
            }
            case DROP, CONTROL_DROP -> {
                box.removeReward(reward);
                dirty = true;
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.6f, 1.0f);
                plugin.message(player, "&7보상을 삭제했습니다. &8(저장해야 반영됩니다)");
            }
            default -> {
                return;
            }
        }
        render();
    }

    private void changeWeight(RewardEntity reward, int delta) {
        reward.setWeight(reward.getWeight() + delta);
        dirty = true;
    }

    /**
     * 클릭한 아이템을 보상으로 등록한다.
     * 클릭은 리스너가 이미 취소했으므로 아이템은 인벤토리에 그대로 남는다 (복사가 아니라 "찍어오기").
     */
    private void addReward(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        if (box.getRewards().size() >= REWARD_SLOTS) {
            plugin.message(player, "&c보상은 최대 " + REWARD_SLOTS + "개까지입니다.");
            return;
        }
        box.addReward(new RewardEntity(ItemCodec.encode(clicked.clone()), 10));
        dirty = true;
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        render();
        plugin.message(player, "&a보상 추가: &r" + describeItem(clicked) + " &7(가중치 10)");
    }

    /** 메시지에 쓸 아이템 이름. */
    private String describeItem(ItemStack item) {
        String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? Text.plain(item.getItemMeta().displayName())
                : item.getType().name().toLowerCase().replace('_', ' ');
        return name + " x" + item.getAmount();
    }

    private void askCommand(Player player) {
        plugin.message(player, "&e실행할 명령어를 채팅에 입력하세요. &7(앞의 / 는 빼고, 취소는 &f취소&7)");
        plugin.message(player, "&8예: give %player% diamond 1");
        // await 를 먼저 걸어야 closeInventory() 가 부르는 onClose 에서 자동저장을 건너뛴다.
        plugin.getChatInputService().await(player, "명령어 보상", input -> {
            RewardEntity reward = new RewardEntity(null, 10);
            reward.setCommandLine(input);
            reward.setLabel("&f명령어 보상");
            box.addReward(reward);
            dirty = true;
            render();
            open(player);
            plugin.message(player, "&a명령어 보상을 추가했습니다: &f/" + input);
        });
        player.closeInventory();
    }

    private void askLabel(Player player, RewardEntity reward) {
        plugin.message(player, "&e보상에 표시할 이름을 입력하세요. &7(색코드 &, 취소는 &f취소&7)");
        plugin.getChatInputService().await(player, "보상 이름", input -> {
            reward.setLabel(input);
            dirty = true;
            render();
            open(player);
            plugin.message(player, "&a이름을 지정했습니다: &r" + input);
        });
        player.closeInventory();
    }

    // ------------------------------------------------------------ 저장

    public void markDirty() {
        this.dirty = true;
    }

    /** 하위 GUI 에서 그냥 나갔을 때 대신 저장해주기 위한 통로. */
    public void saveIfDirty(Player player) {
        if (dirty) {
            save(player, true);
        }
    }

    /** DB 저장. merge 결과로 캐시와 이 화면의 참조를 갈아끼운다. */
    public void save(Player player, boolean closing) {
        if (saving) {
            return;
        }
        saving = true;
        plugin.getBoxManager().save(box).whenComplete((saved, error) -> {
            saving = false;
            if (error != null) {
                plugin.message(player, "&c저장 실패: " + error.getMessage());
                plugin.getLogger().warning("박스 저장 실패 (" + box.getId() + "): " + error.getMessage());
                return;
            }
            this.box = saved;
            this.dirty = false;
            if (!closing) {
                render();
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
            }
            plugin.message(player, "&a'" + saved.getId() + "' 저장 완료. &7(보상 " + saved.getRewards().size() + "개)");
        });
    }

    /** 설정/미리보기 화면으로 이동. 자동저장을 한 번 건너뛴다. */
    private void openChild(Player player, Gui child) {
        navigating = true;
        child.open(player);
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // 하위 GUI 이동이나 채팅 입력 대기 중이면 저장하지 않는다.
        if (navigating) {
            navigating = false;
            return;
        }
        if (plugin.getChatInputService().isWaiting(viewer.getUniqueId())) {
            return;
        }
        if (dirty) {
            save(viewer, true);
        }
    }
}
