package net.teujaem.randombox.service;

import net.teujaem.randombox.RandomBoxPlugin;
import net.teujaem.randombox.entity.BoxOpenLogEntity;
import net.teujaem.randombox.entity.RandomBoxEntity;
import net.teujaem.randombox.entity.RewardEntity;
import net.teujaem.randombox.gui.SpinGui;
import net.teujaem.randombox.util.ItemCodec;
import net.teujaem.randombox.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** 박스를 여는 흐름 전체: 열 수 있는지 검사 → 박스 아이템 소모 → 뽑기 → 연출 → 지급/기록. */
public final class OpenService {

    private final RandomBoxPlugin plugin;

    public OpenService(RandomBoxPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ 진입점

    /**
     * 열 수 있는 상태인지 검사한다. 실패하면 이유를 플레이어에게 알린다.
     *
     * <p>박스 아이템을 소모하기 <b>전에</b> 호출해야 한다. 순서가 바뀌면
     * "상자만 없어지고 아무것도 못 받는" 상황이 생긴다.
     */
    public boolean canOpen(Player player, RandomBoxEntity box) {
        return canOpen(player, box, 1);
    }

    /** @param times 몇 번 연속으로 열지 (10연차면 10) */
    public boolean canOpen(Player player, RandomBoxEntity box, int times) {
        if (box.getRewards().isEmpty() || box.totalWeight() <= 0) {
            plugin.message(player, "&c이 박스에는 아직 보상이 없습니다.");
            return false;
        }
        if (!plugin.isDropWhenFull() && freeSlots(player) < box.getRollCount() * times) {
            plugin.message(player, "&c인벤토리가 가득 찼습니다. 자리를 비우고 다시 시도하세요.");
            return false;
        }
        if (SpinGui.isPlaying(player)) {
            plugin.message(player, "&c이미 개봉 중입니다.");
            return false;
        }
        return true;
    }

    private int freeSlots(Player player) {
        int free = 0;
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (ItemStack slot : storage) {
            if (slot == null || slot.getType().isAir()) {
                free++;
            }
        }
        return free;
    }

    /** 실제 개봉. {@link #canOpen} 이 통과한 뒤에만 부를 것. */
    public void open(Player player, RandomBoxEntity box) {
        play(player, box, 1);
    }

    /** 연속 개봉. 2회 이상이면 연출을 건너뛰고 한 번에 지급한다. */
    public void open(Player player, RandomBoxEntity box, int times) {
        play(player, box, Math.max(1, times));
    }

    /** 아이템 소모 없이 연다. (관리자 테스트) */
    public void openFree(Player player, RandomBoxEntity box) {
        if (!canOpen(player, box)) {
            return;
        }
        play(player, box, 1);
    }

    // ------------------------------------------------------------ 내부

    private void play(Player player, RandomBoxEntity box, int times) {
        List<RewardEntity> rolled = new ArrayList<>();
        for (int i = 0; i < box.getRollCount() * times; i++) {
            RewardEntity reward = roll(box);
            if (reward != null) {
                rolled.add(reward);
            }
        }
        if (rolled.isEmpty()) {
            plugin.message(player, "&c보상 추첨에 실패했습니다. 관리자에게 문의하세요.");
            return;
        }

        if (box.isAnimate()) {
            // 연차도 룰렛을 쓴다. 10번 이어 돌리는 게 아니라 칸들이 동시에 돌다가
            // 하나씩 확정되는 방식이라 시간이 길어지지 않는다. (SpinGui 참고)
            new SpinGui(plugin, player, box, rolled).start();
        } else {
            grant(player, box, rolled, rolled.size() > 1);
        }
    }

    /** 가중치 추첨. */
    public RewardEntity roll(RandomBoxEntity box) {
        int total = box.totalWeight();
        if (total <= 0) {
            return null;
        }
        int pick = ThreadLocalRandom.current().nextInt(total);
        int cursor = 0;
        for (RewardEntity reward : box.getRewards()) {
            int weight = Math.max(0, reward.getWeight());
            if (weight == 0) {
                continue;
            }
            cursor += weight;
            if (pick < cursor) {
                return reward;
            }
        }
        return null;
    }

    /** 실제 지급. 반드시 메인 스레드에서 호출할 것. */
    public void grant(Player player, RandomBoxEntity box, List<RewardEntity> rewards) {
        grant(player, box, rewards, false);
    }

    /**
     * @param summarize 여러 개를 한 번에 열었을 때. 한 줄씩 띄우면 채팅이 도배되므로
     *                  같은 보상끼리 묶어서 한 번만 알린다.
     */
    public void grant(Player player, RandomBoxEntity box, List<RewardEntity> rewards, boolean summarize) {
        java.util.Map<String, Integer> summary = new java.util.LinkedHashMap<>();
        for (RewardEntity reward : rewards) {
            String label = labelOf(reward);

            ItemStack item = ItemCodec.decode(reward.getItemData());
            if (item != null) {
                if (!player.isOnline()) {
                    // 연출 도중에 나가버린 경우. 오프라인 Player 의 인벤토리에 넣으면
                    // 저장되지 않고 보상이 증발한다. 나간 자리에 떨어뜨리고 기록을 남긴다.
                    player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
                    plugin.getLogger().warning("개봉 중 접속을 종료해 보상을 바닥에 떨어뜨렸습니다: "
                            + player.getName() + " / " + box.getId() + " / " + Text.strip(label));
                } else {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
                    for (ItemStack rest : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), rest);
                    }
                    if (!leftover.isEmpty()) {
                        plugin.message(player, "&e인벤토리가 가득 차서 일부를 바닥에 떨어뜨렸습니다.");
                    }
                }
            }

            String command = reward.getCommandLine();
            if (command != null && !command.isBlank()) {
                String parsed = command.replace("%player%", player.getName())
                        .replace("%uuid%", player.getUniqueId().toString());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }

            if (summarize) {
                summary.merge(label, 1, Integer::sum);
            } else {
                plugin.message(player, "&a보상 획득! &f" + label);
            }

            if (box.isBroadcastWin()) {
                Bukkit.broadcast(plugin.prefixed(
                        "&f" + player.getName() + "&7님이 &f" + box.getDisplayName() + "&7에서 &e" + label + "&7 획득!"));
            }

            plugin.getRepository().logOpen(new BoxOpenLogEntity(
                    box.getId(), player.getUniqueId(), player.getName(), Text.strip(label)));
        }

        if (summarize && !summary.isEmpty()) {
            plugin.message(player, "&a" + rewards.size() + "개 개봉 결과");
            for (java.util.Map.Entry<String, Integer> entry : summary.entrySet()) {
                plugin.message(player, " &8- &f" + entry.getKey() + " &7x" + entry.getValue());
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
    }

    /** 메시지에 쓸 보상 이름. */
    public String labelOf(RewardEntity reward) {
        if (reward.getLabel() != null && !reward.getLabel().isBlank()) {
            return reward.getLabel();
        }
        ItemStack item = ItemCodec.decode(reward.getItemData());
        if (item != null) {
            String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? Text.plain(item.getItemMeta().displayName())
                    : item.getType().name().toLowerCase().replace('_', ' ');
            return name + " x" + item.getAmount();
        }
        if (reward.getCommandLine() != null && !reward.getCommandLine().isBlank()) {
            return "/" + reward.getCommandLine();
        }
        return "(빈 보상)";
    }
}
