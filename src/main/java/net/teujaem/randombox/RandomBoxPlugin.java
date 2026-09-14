package net.teujaem.randombox;

import net.teujaem.randombox.command.RandomBoxCommand;
import net.teujaem.randombox.config.BoxYamlImporter;
import net.teujaem.randombox.config.BoxYamlStore;
import net.teujaem.randombox.gui.SpinGui;
import net.teujaem.randombox.listener.BoxItemListener;
import net.teujaem.randombox.listener.ChatInputListener;
import net.teujaem.randombox.listener.GuiListener;
import net.teujaem.randombox.service.BoxManager;
import net.teujaem.randombox.service.ChatInputService;
import net.teujaem.randombox.service.OpenService;
import net.teujaem.randombox.storage.BoxRepository;
import net.teujaem.randombox.util.BoxKeys;
import net.teujaem.randombox.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public final class RandomBoxPlugin extends JavaPlugin {

    private BoxRepository repository;
    private BoxManager boxManager;
    private OpenService openService;
    private ChatInputService chatInputService;
    private BoxKeys boxKeys;
    private BoxYamlStore yamlStore;

    private String prefix = "&6[랜덤박스] &f";
    private int animationTicks = 60;
    private boolean dropWhenFull = true;
    private boolean autoImportOnStart = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        readConfig();

        this.boxKeys = new BoxKeys(this);
        this.chatInputService = new ChatInputService();
        this.yamlStore = new BoxYamlStore(this);
        yamlStore.saveDefaultIfMissing();

        // depend 는 "로드 순서"만 보장한다. SP-Framework 가 로드된 뒤 enable 에서 죽으면
        // 이 플러그인은 그대로 실행되고, 프레임워크 클래스를 건드리는 순간
        // NoClassDefFoundError 가 난다. 엉뚱한 곳을 보게 되므로 먼저 걸러낸다.
        if (!getServer().getPluginManager().isPluginEnabled("SP-Framework")) {
            getLogger().severe("SP-Framework 가 활성화되지 않았습니다. RandomBox 를 끕니다.");
            getLogger().severe("먼저 위쪽 로그에서 SP-Framework 가 실패한 이유를 확인하세요.");
            getLogger().severe("(대개 plugins/SP-Framework/config.yaml 의 DB 접속 정보 문제입니다)");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            this.repository = new BoxRepository();
            getLogger().info("DB: SP-Framework 의 설정을 사용합니다.");
        } catch (Throwable t) {
            // DB 없이 돌면 박스가 전부 사라진 것처럼 보여 더 위험하다. 차라리 꺼버린다.
            getLogger().severe("엔티티 등록 실패: " + t.getMessage());
            getLogger().severe("plugins/SP-Framework/config.yaml 의 database 항목을 확인하세요.");
            t.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.boxManager = new BoxManager(this, repository);
        this.openService = new OpenService(this);

        boxManager.reload().whenComplete((count, error) -> {
            if (error != null) {
                getLogger().severe("박스 불러오기 실패: " + error.getMessage());
                return;
            }
            getLogger().info("랜덤박스 " + count + "개를 DB 에서 불러왔습니다.");
            if (autoImportOnStart) {
                new BoxYamlImporter(this).importAll(getServer().getConsoleSender(), null);
            }
        });

        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new BoxItemListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatInputListener(this), this);

        PluginCommand command = getCommand("randombox");
        if (command != null) {
            RandomBoxCommand executor = new RandomBoxCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        // 돌아가던 연출은 즉시 끝내고 보상을 지급한다. 안 그러면 보상이 증발한다.
        // DB 커넥션은 프레임워크가 만들고 프레임워크가 닫으므로 여기서 건드리지 않는다.
        SpinGui.finishAll();
    }

    public void readConfig() {
        reloadConfig();
        this.prefix = getConfig().getString("prefix", "&6[랜덤박스] &f");
        this.animationTicks = Math.max(20, getConfig().getInt("animation-ticks", 60));
        this.dropWhenFull = getConfig().getBoolean("drop-when-full", true);
        this.autoImportOnStart = getConfig().getBoolean("auto-import-on-start", false);
    }

    // ------------------------------------------------------------- 헬퍼

    /** 비동기 결과를 메인 스레드에서 이어받게 만든다. Bukkit API 를 쓰려면 반드시 거칠 것. */
    public <T> CompletableFuture<T> onMain(CompletableFuture<T> source) {
        CompletableFuture<T> result = new CompletableFuture<>();
        source.whenComplete((value, error) -> {
            if (!isEnabled()) {
                // 플러그인이 이미 내려갔으면 스케줄러를 못 쓴다.
                if (error != null) {
                    result.completeExceptionally(error);
                } else {
                    result.complete(value);
                }
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> {
                if (error != null) {
                    result.completeExceptionally(error);
                } else {
                    result.complete(value);
                }
            });
        });
        return result;
    }

    public void message(CommandSender target, String legacyMessage) {
        target.sendMessage(Text.of(prefix + legacyMessage));
    }

    public Component prefixed(String legacyMessage) {
        return Text.of(prefix + legacyMessage);
    }

    public BoxRepository getRepository() {
        return repository;
    }

    public BoxManager getBoxManager() {
        return boxManager;
    }

    public OpenService getOpenService() {
        return openService;
    }

    public ChatInputService getChatInputService() {
        return chatInputService;
    }

    public BoxKeys getBoxKeys() {
        return boxKeys;
    }

    public BoxYamlStore getYamlStore() {
        return yamlStore;
    }

    public int getAnimationTicks() {
        return animationTicks;
    }

    public boolean isDropWhenFull() {
        return dropWhenFull;
    }
}
