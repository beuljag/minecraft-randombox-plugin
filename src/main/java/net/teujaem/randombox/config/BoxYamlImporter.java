package net.teujaem.randombox.config;

import net.teujaem.randombox.RandomBoxPlugin;
import net.teujaem.randombox.entity.RandomBoxEntity;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code boxes.yml} 가져오기/내보내기 실행부.
 * 캐시를 건드리므로 <b>메인 스레드에서만</b> 호출할 것.
 */
public final class BoxYamlImporter {

    private final RandomBoxPlugin plugin;

    public BoxYamlImporter(RandomBoxPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * yml -> DB.
     *
     * @param only null 이면 파일에 있는 박스 전부
     */
    public void importAll(CommandSender sender, String only) {
        YamlConfiguration yaml;
        try {
            yaml = plugin.getYamlStore().load();
        } catch (RuntimeException e) {
            plugin.message(sender, "&cboxes.yml 을 읽지 못했습니다: " + e.getMessage());
            return;
        }

        BoxYamlStore.ImportResult parsed = plugin.getYamlStore().read(yaml, only);

        for (String error : parsed.errors()) {
            plugin.message(sender, "&c[boxes.yml] " + error);
        }
        if (parsed.boxes().isEmpty()) {
            plugin.message(sender, "&7가져올 박스가 없습니다.");
            return;
        }

        List<CompletableFuture<RandomBoxEntity>> saves = new ArrayList<>();
        for (RandomBoxEntity box : parsed.boxes()) {
            saves.add(plugin.getBoxManager().save(box));
        }

        List<String> failed = new ArrayList<>();
        CompletableFuture.allOf(saves.toArray(new CompletableFuture[0]))
                .whenComplete((ignored, error) -> {
                    int ok = 0;
                    for (int i = 0; i < saves.size(); i++) {
                        if (saves.get(i).isCompletedExceptionally()) {
                            failed.add(parsed.boxes().get(i).getId());
                        } else {
                            ok++;
                        }
                    }
                    plugin.message(sender, "&aboxes.yml -> DB: " + ok + "개 반영 완료.");
                    if (!failed.isEmpty()) {
                        plugin.message(sender, "&c저장 실패: " + String.join(", ", failed)
                                + " &7(/rbox reload 로 DB 상태를 다시 읽으세요)");
                    }
                });
    }

    /**
     * DB -> yml.
     *
     * @param only null 이면 등록된 박스 전부
     */
    public void export(CommandSender sender, String only) {
        List<RandomBoxEntity> targets = new ArrayList<>();
        if (only == null) {
            targets.addAll(plugin.getBoxManager().all());
        } else {
            RandomBoxEntity box = plugin.getBoxManager().get(only);
            if (box == null) {
                plugin.message(sender, "&c그런 박스가 없습니다: &f" + only);
                return;
            }
            targets.add(box);
        }

        if (targets.isEmpty()) {
            plugin.message(sender, "&7내보낼 박스가 없습니다.");
            return;
        }

        try {
            plugin.getYamlStore().write(targets);
        } catch (IOException | RuntimeException e) {
            plugin.message(sender, "&c저장 실패: " + e.getMessage());
            plugin.getLogger().warning("boxes.yml 내보내기 실패: " + e);
            return;
        }

        plugin.message(sender, "&aDB -> boxes.yml: " + targets.size() + "개 기록 완료.");
        plugin.message(sender, "&7파일: &f" + plugin.getYamlStore().getFile().getPath());
    }
}
