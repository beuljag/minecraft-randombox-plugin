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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 개봉 연출(룰렛).
 *
 * <p>보상은 연출을 시작하기 <b>전에</b> 이미 뽑혀 있다. 연출은 보여주기용이고,
 * 중간에 창을 닫거나 서버가 꺼져도 보상은 반드시 지급된다.
 *
 * <p>연출은 두 가지다.
 * <ul>
 *   <li><b>단일</b> — 보상 1개. 가로줄이 흐르다가 가운데에서 멈춘다.</li>
 *   <li><b>연차</b> — 보상 여러 개(10연차 등). 칸들이 동시에 돌다가 왼쪽부터 하나씩 확정된다.
 *       룰렛을 10번 이어 보여주면 30초가 걸려서, 한 화면에서 동시에 돌린다.</li>
 * </ul>
 */
public final class SpinGui extends Gui {

    /** 지금 연출 중인 플레이어들. 중복 개봉과 보상 증발을 막는 용도. */
    private static final Map<UUID, SpinGui> ACTIVE = new ConcurrentHashMap<>();

    /** 단일 연출에서 아이템이 흘러가는 가로줄. */
    private static final int[] TRACK = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int CENTER = 13;

    /** 연차 연출에서 결과가 놓이는 자리. 위 5칸 + 아래 5칸. */
    private static final int[] GRID = {2, 3, 4, 5, 6, 11, 12, 13, 14, 15};

    /** 한 칸이 확정될 때마다 두는 간격(틱). */
    private static final int REVEAL_STEP = 5;

    private final RandomBoxPlugin plugin;
    private final Player player;
    private final RandomBoxEntity box;
    private final List<RewardEntity> rewards;
    private final int totalTicks;

    /** 연차 연출인지. 보상이 2개 이상이면 이쪽. */
    private final boolean multi;
    /** 연차 연출에서 실제로 화면에 쓰는 칸. */
    private final int[] slots;
    /** 칸들이 다 같이 도는 시간. 이후로는 하나씩 확정된다. */
    private final int shuffleTicks;

    private BukkitTask task;
    private int elapsed;
    private int untilNextShift = 1;
    private int revealed;
    private boolean finished;
    private boolean closingSelf;

    public SpinGui(RandomBoxPlugin plugin, Player player, RandomBoxEntity box, List<RewardEntity> rewards) {
        this.plugin = plugin;
        this.player = player;
        this.box = box;
        this.rewards = rewards;
        this.totalTicks = plugin.getAnimationTicks();
        this.multi = rewards.size() > 1;

        int shown = Math.min(rewards.size(), GRID.length);
        this.slots = new int[shown];
        System.arraycopy(GRID, 0, this.slots, 0, shown);

        // 연차는 다 같이 도는 시간을 절반만 쓰고, 나머지를 한 칸씩 확정하는 데 쓴다.
        this.shuffleTicks = multi ? Math.max(10, totalTicks / 2) : totalTicks;

        String title = multi
                ? "&8" + rewards.size() + "연차: &r" + box.getDisplayName()
                : "&8개봉 중... &r" + box.getDisplayName();
        this.inventory = Bukkit.createInventory(this, 27, Text.of(title));

        fill(Material.BLACK_STAINED_GLASS_PANE, 0, 26);
        if (multi) {
            for (int slot : slots) {
                inventory.setItem(slot, randomDisplay());
            }
        } else {
            inventory.setItem(CENTER - 9, button(Material.HOPPER, "&e▼"));
            inventory.setItem(CENTER + 9, button(Material.HOPPER, "&e▲"));
            for (int slot : TRACK) {
                inventory.setItem(slot, randomDisplay());
            }
        }
    }

    public static boolean isPlaying(Player player) {
        return ACTIVE.containsKey(player.getUniqueId());
    }

    /** 접속을 끊은 플레이어의 연출을 정리한다. 남겨두면 재접속 후 "이미 개봉 중"으로 막힌다. */
    public static void finishFor(Player player) {
        SpinGui gui = ACTIVE.get(player.getUniqueId());
        if (gui != null) {
            gui.finish(true);
        }
    }

    /** 서버 종료 시 남은 연출을 전부 끝내고 보상을 지급한다. */
    public static void finishAll() {
        for (SpinGui gui : new ArrayList<>(ACTIVE.values())) {
            gui.finish(true);
        }
    }

    public void start() {
        ACTIVE.put(player.getUniqueId(), this);
        open(player);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 1L);
    }

    private void tick() {
        if (finished) {
            return;
        }
        elapsed++;
        if (multi) {
            tickMulti();
        } else {
            tickSingle();
        }
    }

    // ------------------------------------------------------------ 단일

    private void tickSingle() {
        if (--untilNextShift > 0) {
            return;
        }

        shift();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.2f);

        // 끝으로 갈수록 간격을 벌려 감속시킨다.
        double progress = Math.min(1.0, elapsed / (double) totalTicks);
        untilNextShift = 1 + (int) (progress * progress * 7);

        if (elapsed >= totalTicks) {
            finish(false);
        }
    }

    private void shift() {
        for (int i = 0; i < TRACK.length - 1; i++) {
            inventory.setItem(TRACK[i], inventory.getItem(TRACK[i + 1]));
        }
        inventory.setItem(TRACK[TRACK.length - 1], randomDisplay());
    }

    // ------------------------------------------------------------ 연차

    private void tickMulti() {
        // 아직 확정되지 않은 칸은 계속 돌린다.
        if (elapsed % 2 == 0) {
            for (int i = revealed; i < slots.length; i++) {
                inventory.setItem(slots[i], randomDisplay());
            }
        }

        if (elapsed < shuffleTicks) {
            if (elapsed % 4 == 0) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.4f);
            }
            return;
        }

        // 왼쪽부터 한 칸씩 확정.
        int due = (elapsed - shuffleTicks) / REVEAL_STEP;
        while (revealed < slots.length && revealed <= due) {
            inventory.setItem(slots[revealed], displayOf(rewards.get(revealed)));
            // 뒤로 갈수록 음이 올라가서 마지막이 제일 짜릿하다.
            float pitch = 1.0f + (revealed / (float) slots.length);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, pitch);
            revealed++;
        }

        if (revealed >= slots.length) {
            finish(false);
        }
    }

    // ------------------------------------------------------------ 종료

    /**
     * 연출 종료 + 보상 지급. 여러 번 불려도 한 번만 지급된다.
     *
     * @param abrupt 창이 이미 닫혔거나 서버가 내려가는 중 — 결과 표시를 건너뛴다.
     */
    private void finish(boolean abrupt) {
        if (finished) {
            return;
        }
        finished = true;
        ACTIVE.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }

        if (!abrupt && player.isOnline() && player.getOpenInventory().getTopInventory().equals(inventory)) {
            if (multi) {
                // 남은 칸이 있으면(이론상 없지만) 마저 채워서 결과를 온전히 보여준다.
                for (int i = revealed; i < slots.length; i++) {
                    inventory.setItem(slots[i], displayOf(rewards.get(i)));
                }
            } else {
                inventory.setItem(CENTER, displayOf(rewards.get(0)));
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);

            if (plugin.isEnabled()) {
                // 결과를 볼 시간을 준다. 연차는 눈으로 훑을 게 많아 조금 더 둔다.
                long linger = multi ? 70L : 40L;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(inventory)) {
                        closingSelf = true;
                        player.closeInventory();
                    }
                }, linger);
            }
        }

        plugin.getOpenService().grant(player, box, rewards, rewards.size() > 1);
    }

    // ------------------------------------------------------------ 표시

    private ItemStack randomDisplay() {
        List<RewardEntity> pool = box.getRewards();
        if (pool.isEmpty()) {
            return button(Material.PAPER, "&7?");
        }
        return displayOf(pool.get(ThreadLocalRandom.current().nextInt(pool.size())));
    }

    private ItemStack displayOf(RewardEntity reward) {
        ItemStack item = ItemCodec.decode(reward.getItemData());
        if (item == null) {
            item = new ItemStack(Material.PAPER);
        } else {
            item = item.clone();
        }
        if (reward.getLabel() != null && !reward.getLabel().isBlank()) {
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Text.of(reward.getLabel()));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        // 연출 중에는 아무것도 못 만지게 한다. (리스너가 이미 취소함)
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        if (closingSelf) {
            return;
        }
        // 중간에 닫아도 보상은 준다.
        finish(true);
    }
}
