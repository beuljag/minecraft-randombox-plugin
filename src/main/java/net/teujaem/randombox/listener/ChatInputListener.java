package net.teujaem.randombox.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.teujaem.randombox.RandomBoxPlugin;
import net.teujaem.randombox.service.ChatInputService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** GUI 가 요청한 채팅 입력을 받아 메인 스레드에서 콜백을 실행한다. */
public final class ChatInputListener implements Listener {

    private final RandomBoxPlugin plugin;

    public ChatInputListener(RandomBoxPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getChatInputService().isWaiting(player.getUniqueId())) {
            return;
        }

        ChatInputService.Request request = plugin.getChatInputService().take(player.getUniqueId());
        if (request == null) {
            return;
        }

        event.setCancelled(true); // 입력값이 채팅에 그대로 뿌려지지 않게
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (input.isEmpty() || input.equalsIgnoreCase("취소") || input.equalsIgnoreCase("cancel")) {
            plugin.message(player, "&7입력을 취소했습니다. &8(" + request.description() + ")");
            return;
        }

        // 여기는 비동기 스레드다. Bukkit API 를 만지는 콜백은 반드시 메인으로 넘긴다.
        Bukkit.getScheduler().runTask(plugin, () -> request.onInput().accept(input));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getChatInputService().cancel(event.getPlayer().getUniqueId());
    }
}
