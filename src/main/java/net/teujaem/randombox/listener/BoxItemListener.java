package net.teujaem.randombox.listener;

import net.teujaem.randombox.RandomBoxPlugin;
import net.teujaem.randombox.entity.RandomBoxEntity;
import net.teujaem.randombox.gui.PreviewGui;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 박스 아이템 조작.
 * <ul>
 *   <li>우클릭 — 개봉. 그 아이템이 소모된다.</li>
 *   <li>웅크린 채 우클릭 — 10개를 한 번에(10연차).</li>
 *   <li>왼손 키(F) — 확률표. 박스 설정에서 끌 수 있다.</li>
 * </ul>
 */
public final class BoxItemListener implements Listener {

    /** 웅크리고 우클릭할 때 한 번에 여는 개수. */
    private static final int BULK_COUNT = 10;

    private final RandomBoxPlugin plugin;

    public BoxItemListener(RandomBoxPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * {@code ignoreCancelled} 를 쓰지 않는다.
     *
     * <p>허공 우클릭은 서버가 이미 "취소됨" 상태로 넘겨줄 때가 있다.
     * (블록을 안 짚었으니 쓸 대상이 없다고 보는 것) 그걸 걸러내면
     * 허공에서는 상자가 아예 안 열린다.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // 오프핸드까지 두 번 처리되지 않게
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();

        // event.getItem() 은 구현에 따라 사본일 수 있어서, 거기다 setAmount 를 해도
        // 실제 인벤토리가 안 줄어들 수 있다. 소모는 반드시 인벤토리 쪽 스택으로 한다.
        ItemStack hand = player.getInventory().getItemInMainHand();
        String boxId = plugin.getBoxKeys().readBoxId(hand);
        if (boxId == null) {
            return;
        }

        // 상자가 CHEST 같은 설치 가능한 블록이면 우클릭에 설치돼 버린다.
        // 취소 + 두 Result 를 DENY 로 막아 어떤 경로로도 설치되지 않게 한다.
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        if (!player.hasPermission("randombox.use")) {
            plugin.message(player, "&c권한이 없습니다.");
            return;
        }

        RandomBoxEntity box = plugin.getBoxManager().get(boxId);
        if (box == null) {
            plugin.message(player, "&c삭제되었거나 존재하지 않는 박스입니다. &8(" + boxId + ")");
            return;
        }

        // 웅크린 채 우클릭 + 10개 이상 = 10연차. 모자라면 그냥 1개만 연다.
        int times = player.isSneaking() && hand.getAmount() >= BULK_COUNT ? BULK_COUNT : 1;

        // 열 수 있는지 먼저 확인한다. 순서가 바뀌면 상자만 없어지고 보상을 못 받는다.
        if (!plugin.getOpenService().canOpen(player, box, times)) {
            return;
        }

        consume(player, times);
        plugin.getOpenService().open(player, box, times);
    }

    /**
     * 상자를 들고 왼손 키(F)를 누르면 확률표를 띄운다.
     * 박스 설정에서 끌 수 있고, 꺼져 있으면 평소대로 손을 바꾼다.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        String boxId = plugin.getBoxKeys().readBoxId(player.getInventory().getItemInMainHand());
        if (boxId == null) {
            return;
        }

        RandomBoxEntity box = plugin.getBoxManager().get(boxId);
        if (box == null || !box.isPreviewOnSwap()) {
            return;
        }
        if (!player.hasPermission("randombox.use")) {
            return;
        }

        event.setCancelled(true);
        new PreviewGui(plugin, box, null).open(player);
    }

    /** 주손의 아이템을 지정한 개수만큼 줄인다. 인벤토리에 확실히 반영되도록 되돌려 넣는다. */
    private void consume(Player player, int count) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        int left = hand.getAmount() - count;
        if (left <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(left);
            player.getInventory().setItemInMainHand(hand);
        }
    }
}
