package net.teujaem.randombox.listener;

import net.teujaem.randombox.gui.Gui;
import net.teujaem.randombox.gui.SpinGui;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

/** 모든 GUI 클릭/닫기를 해당 Gui 인스턴스로 넘겨준다. */
public final class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Gui gui)) {
            return;
        }
        // GUI 가 열려 있는 동안에는 아래쪽 본인 인벤토리 조작도 전부 막는다.
        // (쉬프트 클릭으로 아이템이 GUI 로 빨려 들어가는 걸 원천 차단)
        event.setCancelled(true);
        gui.onClick(event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Gui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Gui gui) {
            gui.onClose(event);
        }
    }

    /**
     * 연출 도중 나간 사람 정리. 창 닫기 이벤트가 안 오는 경우가 있어서 여기서도 받아둔다.
     * 보상은 {@code finish} 안에서 지급되므로 증발하지 않는다.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SpinGui.finishFor(event.getPlayer());
    }
}
