package net.teujaem.randombox.command;

import net.teujaem.randombox.RandomBoxPlugin;
import net.teujaem.randombox.config.BoxYamlImporter;
import net.teujaem.randombox.entity.RandomBoxEntity;
import net.teujaem.randombox.gui.BoxEditorGui;
import net.teujaem.randombox.gui.PreviewGui;
import net.teujaem.randombox.service.BoxManager;
import net.teujaem.randombox.util.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RandomBoxCommand implements TabExecutor {

    private static final String ADMIN = "randombox.admin";
    private static final String USE = "randombox.use";

    private final RandomBoxPlugin plugin;

    public RandomBoxCommand(RandomBoxPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(sender);
            case "create" -> create(sender, args);
            case "delete", "remove" -> delete(sender, args);
            case "edit" -> edit(sender, args);
            case "preview" -> preview(sender, args);
            case "test" -> test(sender, args);
            case "item" -> giveBoxItem(sender, args);
            case "import" -> importYaml(sender, args);
            case "export" -> exportYaml(sender, args);
            case "reload" -> reload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    // ------------------------------------------------------------ 서브명령

    private void list(CommandSender sender) {
        if (!check(sender, USE)) {
            return;
        }
        List<RandomBoxEntity> boxes = new ArrayList<>(plugin.getBoxManager().all());
        if (boxes.isEmpty()) {
            plugin.message(sender, "&7등록된 박스가 없습니다. &f/rbox create <id>");
            return;
        }
        plugin.message(sender, "&e박스 " + boxes.size() + "개");
        boxes.sort(java.util.Comparator.comparing(RandomBoxEntity::getId));
        for (RandomBoxEntity box : boxes) {
            plugin.message(sender, " &8- &f" + box.getId() + " &7(" + box.getDisplayName()
                    + "&7, 보상 " + box.getRewards().size() + "종"
                    + ")");
        }
    }

    private void create(CommandSender sender, String[] args) {
        if (!check(sender, ADMIN)) {
            return;
        }
        if (args.length < 2) {
            plugin.message(sender, "&c사용법: /rbox create <id> [표시이름]");
            return;
        }
        String id = BoxManager.normalizeId(args[1]);
        if (!BoxManager.isValidId(id)) {
            plugin.message(sender, "&cid 는 영문/숫자/_/- 만, 32자 이내여야 합니다.");
            return;
        }
        if (plugin.getBoxManager().exists(id)) {
            plugin.message(sender, "&c이미 있는 id 입니다.");
            return;
        }

        String displayName = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : "&6" + id;

        plugin.getBoxManager().create(id, displayName).whenComplete((box, error) -> {
            if (error != null) {
                plugin.message(sender, "&c생성 실패: " + error.getMessage());
                return;
            }
            plugin.message(sender, "&a박스 '" + id + "' 생성 완료. &7/rbox edit " + id + " 로 보상을 넣으세요.");
            if (sender instanceof Player player) {
                new BoxEditorGui(plugin, player, box).open(player);
            }
        });
    }

    private void delete(CommandSender sender, String[] args) {
        if (!check(sender, ADMIN)) {
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            plugin.message(sender, "&c되돌릴 수 없습니다. 확인: &f/rbox delete <id> confirm");
            return;
        }
        RandomBoxEntity box = resolve(sender, args[1]);
        if (box == null) {
            return;
        }
        plugin.getBoxManager().delete(box.getId()).whenComplete((removed, error) -> {
            if (error != null) {
                plugin.message(sender, "&c삭제 실패: " + error.getMessage());
                return;
            }
            plugin.message(sender, removed ? "&a삭제했습니다: " + box.getId() : "&cDB 에서 찾지 못했습니다.");
        });
    }

    private void edit(CommandSender sender, String[] args) {
        if (!check(sender, ADMIN) || !(requirePlayer(sender) instanceof Player player)) {
            return;
        }
        if (args.length < 2) {
            plugin.message(sender, "&c사용법: /rbox edit <id>");
            return;
        }
        RandomBoxEntity box = resolve(sender, args[1]);
        if (box == null) {
            return;
        }
        new BoxEditorGui(plugin, player, box).open(player);
    }

    private void preview(CommandSender sender, String[] args) {
        if (!check(sender, USE) || !(requirePlayer(sender) instanceof Player player)) {
            return;
        }
        if (args.length < 2) {
            plugin.message(sender, "&c사용법: /rbox preview <id>");
            return;
        }
        RandomBoxEntity box = resolve(sender, args[1]);
        if (box == null) {
            return;
        }
        new PreviewGui(plugin, box, null).open(player);
    }

    private void test(CommandSender sender, String[] args) {
        if (!check(sender, ADMIN) || !(requirePlayer(sender) instanceof Player player)) {
            return;
        }
        if (args.length < 2) {
            plugin.message(sender, "&c사용법: /rbox test <id>");
            return;
        }
        RandomBoxEntity box = resolve(sender, args[1]);
        if (box == null) {
            return;
        }
        plugin.message(sender, "&7상자 소모 없이 테스트 개봉합니다.");
        plugin.getOpenService().openFree(player, box);
    }

    private void giveBoxItem(CommandSender sender, String[] args) {
        if (!check(sender, ADMIN)) {
            return;
        }
        if (args.length < 2) {
            plugin.message(sender, "&c사용법: /rbox item <id> [대상] [개수]");
            return;
        }
        RandomBoxEntity box = resolve(sender, args[1]);
        if (box == null) {
            return;
        }
        Player target = resolveTarget(sender, args, 2);
        if (target == null) {
            return;
        }
        int amount = parseAmount(args, 3, 1);

        ItemStack item = plugin.getBoxKeys().buildBoxItem(box, amount);
        giveOrDrop(target, item);
        plugin.message(sender, "&a" + target.getName() + " 에게 박스 아이템 " + amount + "개 지급.");
    }

    /** /rbox import &lt;id|all&gt; — boxes.yml 을 읽어 DB 에 반영. */
    private void importYaml(CommandSender sender, String[] args) {
        if (!check(sender, ADMIN)) {
            return;
        }
        if (args.length < 2) {
            plugin.message(sender, "&c사용법: /rbox import <id|all>");
            return;
        }
        String only = args[1].equalsIgnoreCase("all") ? null : args[1];
        new BoxYamlImporter(plugin).importAll(sender, only);
    }

    /** /rbox export &lt;id|all&gt; — DB 내용을 boxes.yml 로 뽑기. */
    private void exportYaml(CommandSender sender, String[] args) {
        if (!check(sender, ADMIN)) {
            return;
        }
        if (args.length < 2) {
            plugin.message(sender, "&c사용법: /rbox export <id|all>");
            return;
        }
        String only = args[1].equalsIgnoreCase("all") ? null : args[1];
        new BoxYamlImporter(plugin).export(sender, only);
    }

    private void reload(CommandSender sender) {
        if (!check(sender, ADMIN)) {
            return;
        }
        plugin.readConfig();
        plugin.getBoxManager().reload().whenComplete((count, error) -> {
            if (error != null) {
                plugin.message(sender, "&c불러오기 실패: " + error.getMessage());
                return;
            }
            plugin.message(sender, "&aDB 에서 박스 " + count + "개를 다시 불러왔습니다.");
        });
    }

    private void sendHelp(CommandSender sender) {
        plugin.message(sender, "&e랜덤박스 명령어");
        plugin.message(sender, " &f/rbox list &7- 박스 목록");
        plugin.message(sender, " &f/rbox preview <id> &7- 보상/확률 보기");
        if (!sender.hasPermission(ADMIN)) {
            return;
        }
        plugin.message(sender, "&c[관리]");
        plugin.message(sender, " &f/rbox create <id> [표시이름] &7- 생성 후 편집창");
        plugin.message(sender, " &f/rbox edit <id> &7- 보상/설정 편집");
        plugin.message(sender, " &f/rbox delete <id> confirm &7- 삭제");
        plugin.message(sender, " &f/rbox item <id> [대상] [개수] &7- 박스 아이템 지급");
        plugin.message(sender, " &f/rbox test <id> &7- 상자 소모 없이 테스트 개봉");
        plugin.message(sender, " &f/rbox import <id|all> &7- boxes.yml -> DB");
        plugin.message(sender, " &f/rbox export <id|all> &7- DB -> boxes.yml");
        plugin.message(sender, " &f/rbox reload &7- DB 에서 다시 불러오기");
    }

    // ------------------------------------------------------------ 도우미

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.message(sender, "&c권한이 없습니다.");
        return false;
    }

    private CommandSender requirePlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return sender;
        }
        plugin.message(sender, "&c게임 안에서만 쓸 수 있는 명령어입니다.");
        return null;
    }

    private RandomBoxEntity resolve(CommandSender sender, String id) {
        RandomBoxEntity box = plugin.getBoxManager().get(id);
        if (box == null) {
            plugin.message(sender, "&c그런 박스가 없습니다: &f" + id);
        }
        return box;
    }

    /** 인자에 대상이 있으면 그 사람, 없으면 명령어를 친 본인. */
    private Player resolveTarget(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player target = Bukkit.getPlayerExact(args[index]);
            if (target == null) {
                plugin.message(sender, "&c접속 중인 플레이어가 아닙니다: " + args[index]);
            }
            return target;
        }
        if (sender instanceof Player player) {
            return player;
        }
        plugin.message(sender, "&c콘솔에서는 대상을 지정해야 합니다.");
        return null;
    }

    private int parseAmount(String[] args, int index, int fallback) {
        if (args.length <= index) {
            return fallback;
        }
        try {
            return Math.max(1, Math.min(64, Integer.parseInt(args[index])));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void giveOrDrop(Player target, ItemStack item) {
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), rest);
        }
    }

    // ------------------------------------------------------------ 탭완성

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN);

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("list", "preview"));
            if (admin) {
                subs.addAll(List.of("create", "edit", "delete", "item",
                        "test", "import", "export", "reload"));
            }
            return filter(subs, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "preview", "edit", "delete", "item", "test" ->
                        filter(plugin.getBoxManager().ids(), args[1]);
                case "export" -> filter(withAll(plugin.getBoxManager().ids()), args[1]);
                case "import" -> filter(withAll(yamlIds()), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "item" -> filter(onlineNames(), args[2]);
                case "delete" -> filter(List.of("confirm"), args[2]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> withAll(List<String> source) {
        List<String> result = new ArrayList<>(source.size() + 1);
        result.add("all");
        result.addAll(source);
        return result;
    }

    /** 탭완성용으로 boxes.yml 에 적힌 id 를 읽는다. 파일이 깨져 있으면 조용히 비워둔다. */
    private List<String> yamlIds() {
        try {
            return plugin.getYamlStore().idsIn(plugin.getYamlStore().load());
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        return names;
    }

    private List<String> filter(List<String> source, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : source) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
