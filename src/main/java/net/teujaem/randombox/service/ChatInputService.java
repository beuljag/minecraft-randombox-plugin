package net.teujaem.randombox.service;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * GUI 에서 값(이름, 명령어 등)을 입력받기 위한 1회성 채팅 대기.
 * 채팅 이벤트는 비동기이므로 콜백은 메인 스레드로 넘겨서 실행한다. ({@code ChatInputListener})
 */
public final class ChatInputService {

    /** 대기 중인 입력 요청. */
    public record Request(String description, Consumer<String> onInput) {
    }

    private final Map<UUID, Request> pending = new ConcurrentHashMap<>();

    public void await(Player player, String description, Consumer<String> onInput) {
        pending.put(player.getUniqueId(), new Request(description, onInput));
    }

    public Request take(UUID playerUuid) {
        return pending.remove(playerUuid);
    }

    public boolean isWaiting(UUID playerUuid) {
        return pending.containsKey(playerUuid);
    }

    public void cancel(UUID playerUuid) {
        pending.remove(playerUuid);
    }
}
