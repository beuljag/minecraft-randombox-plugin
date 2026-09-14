package net.teujaem.randombox.config;

import net.teujaem.randombox.RandomBoxPlugin;
import net.teujaem.randombox.entity.RandomBoxEntity;
import net.teujaem.randombox.entity.RewardEntity;
import net.teujaem.randombox.service.BoxManager;
import net.teujaem.randombox.util.ItemCodec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code boxes.yml} 과 DB 사이의 다리.
 *
 * <p><b>데이터 원본은 어디까지나 MariaDB 다.</b> 이 파일은 "설정을 파일로도 만질 수 있게" 하는
 * 입출력 창구일 뿐이고, 서버는 항상 DB 를 보고 동작한다. 그래서 import 하기 전까지
 * yml 을 고쳐도 게임에는 아무 영향이 없다.
 *
 * <ul>
 *   <li>{@code /rbox import} — yml 을 읽어 DB 에 반영 (파일이 이김)</li>
 *   <li>{@code /rbox export} — DB 내용을 yml 로 뽑기 (DB 가 이김)</li>
 * </ul>
 */
public final class BoxYamlStore {

    private static final String FILE_NAME = "boxes.yml";

    private final RandomBoxPlugin plugin;
    private final File file;

    public BoxYamlStore(RandomBoxPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
    }

    /** 없으면 주석 달린 예시 파일을 깔아둔다. */
    public void saveDefaultIfMissing() {
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
    }

    public File getFile() {
        return file;
    }

    public YamlConfiguration load() {
        saveDefaultIfMissing();
        return YamlConfiguration.loadConfiguration(file);
    }

    /** yml 에 적힌 박스 id 목록. */
    public List<String> idsIn(YamlConfiguration yaml) {
        ConfigurationSection boxes = yaml.getConfigurationSection("boxes");
        if (boxes == null) {
            return List.of();
        }
        return new ArrayList<>(boxes.getKeys(false));
    }

    // ------------------------------------------------------------ import

    /** 결과 보고용. */
    public record ImportResult(List<RandomBoxEntity> boxes, List<String> errors) {
    }

    /**
     * yml -> 엔티티. DB 저장은 호출한 쪽에서 한다.
     *
     * @param only null 이면 전부, 아니면 그 id 하나만
     */
    public ImportResult read(YamlConfiguration yaml, String only) {
        List<RandomBoxEntity> result = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        ConfigurationSection boxes = yaml.getConfigurationSection("boxes");
        if (boxes == null) {
            errors.add("'boxes:' 항목이 없습니다.");
            return new ImportResult(result, errors);
        }

        Set<String> targets = new LinkedHashSet<>(boxes.getKeys(false));
        if (only != null) {
            String wanted = BoxManager.normalizeId(only);
            targets.removeIf(id -> !BoxManager.normalizeId(id).equals(wanted));
            if (targets.isEmpty()) {
                errors.add("yml 에 '" + only + "' 박스가 없습니다.");
                return new ImportResult(result, errors);
            }
        }

        for (String rawId : targets) {
            String id = BoxManager.normalizeId(rawId);
            if (!BoxManager.isValidId(id)) {
                errors.add(rawId + ": id 는 영문/숫자/_/- 32자 이내여야 합니다.");
                continue;
            }
            ConfigurationSection section = boxes.getConfigurationSection(rawId);
            if (section == null) {
                errors.add(rawId + ": 내용이 비어 있습니다.");
                continue;
            }
            try {
                result.add(toEntity(id, section));
            } catch (RuntimeException e) {
                errors.add(rawId + ": " + e.getMessage());
            }
        }
        return new ImportResult(result, errors);
    }

    /**
     * 한 박스를 엔티티로 만든다.
     * 이미 DB 에 있는 박스면 <b>그 인스턴스를 재사용</b>해서 보상만 갈아끼운다.
     * (새 인스턴스를 만들면 merge 가 기존 보상 행을 남겨두거나 PK 충돌을 낸다)
     */
    private RandomBoxEntity toEntity(String id, ConfigurationSection section) {
        RandomBoxEntity box = plugin.getBoxManager().get(id);
        if (box == null) {
            box = new RandomBoxEntity(id, section.getString("display-name", "&6" + id));
        } else {
            box.setDisplayName(section.getString("display-name", box.getDisplayName()));
        }

        box.setRollCount(section.getInt("roll-count", 1));

        // 상자로 쓸 아이템. 없으면 기본 상자(CHEST).
        ItemStack boxItem = ItemYamlCodec.read(section.getConfigurationSection("box-item"));
        box.setBoxItemData(boxItem == null ? null : ItemCodec.encode(boxItem));
        box.setBroadcastWin(section.getBoolean("broadcast", false));
        box.setAnimate(section.getBoolean("animate", true));
        box.setPreviewOnSwap(section.getBoolean("preview-on-swap", true));

        // 보상은 통째로 교체한다. orphanRemoval 이 켜져 있어서 빠진 행은 DELETE 된다.
        box.getRewards().clear();

        List<?> rewardList = section.getList("rewards");
        if (rewardList == null || rewardList.isEmpty()) {
            throw new IllegalArgumentException("rewards 가 비어 있습니다. 보상이 없으면 박스를 열 수 없습니다.");
        }

        int index = 0;
        for (Object raw : rewardList) {
            index++;
            if (!(raw instanceof java.util.Map<?, ?> map)) {
                throw new IllegalArgumentException("rewards[" + index + "] 형식이 잘못되었습니다.");
            }
            // List 안의 Map 은 ConfigurationSection 이 아니라서 임시 섹션으로 감싼다.
            YamlConfiguration wrapper = new YamlConfiguration();
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                wrapper.set(String.valueOf(entry.getKey()), entry.getValue());
            }

            RewardEntity reward = new RewardEntity(null, wrapper.getInt("weight", 10));

            ItemStack item;
            try {
                item = ItemYamlCodec.read(wrapper.getConfigurationSection("item"));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("rewards[" + index + "] " + e.getMessage());
            }
            reward.setItemData(item == null ? null : ItemCodec.encode(item));

            String command = wrapper.getString("command");
            if (command != null && !command.isBlank()) {
                reward.setCommandLine(command.startsWith("/") ? command.substring(1) : command);
            }
            reward.setLabel(wrapper.getString("label"));

            if (reward.getItemData() == null && reward.getCommandLine() == null) {
                throw new IllegalArgumentException("rewards[" + index + "] 에 item 도 command 도 없습니다.");
            }
            box.addReward(reward);
        }

        if (box.totalWeight() <= 0) {
            throw new IllegalArgumentException("weight 합계가 0 입니다. 최소 하나는 1 이상이어야 합니다.");
        }
        return box;
    }

    // ------------------------------------------------------------ export

    /**
     * DB 내용을 yml 로 쓴다. 대상 박스만 갈아끼우고 나머지 항목은 파일에 남겨둔다.
     *
     * @param targets 내보낼 박스들
     */
    public void write(List<RandomBoxEntity> targets) throws IOException {
        YamlConfiguration yaml = load();

        for (RandomBoxEntity box : targets) {
            ConfigurationSection section = yaml.createSection("boxes." + box.getId());
            section.set("display-name", box.getDisplayName());
            section.set("roll-count", box.getRollCount());

            ItemStack boxItem = ItemCodec.decode(box.getBoxItemData());
            if (boxItem != null) {
                ItemYamlCodec.write(section, "box-item", boxItem);
            }
            section.set("broadcast", box.isBroadcastWin());
            section.set("animate", box.isAnimate());
            section.set("preview-on-swap", box.isPreviewOnSwap());

            List<Object> rewards = new ArrayList<>();
            for (RewardEntity reward : box.getRewards()) {
                // 보상은 리스트라서 섹션을 만들 수 없다. 임시 설정에 쓴 뒤 Map 으로 뽑아 담는다.
                YamlConfiguration scratch = new YamlConfiguration();
                scratch.set("weight", reward.getWeight());
                ItemStack item = ItemCodec.decode(reward.getItemData());
                if (item != null) {
                    ItemYamlCodec.write(scratch, "item", item);
                }
                if (reward.getCommandLine() != null && !reward.getCommandLine().isBlank()) {
                    scratch.set("command", reward.getCommandLine());
                }
                if (reward.getLabel() != null && !reward.getLabel().isBlank()) {
                    scratch.set("label", reward.getLabel());
                }
                rewards.add(toPlainMap(scratch));
            }
            section.set("rewards", rewards);
        }

        yaml.save(file);
    }

    /** YamlConfiguration -> 중첩 Map (리스트 원소로 넣기 위해). */
    private static java.util.Map<String, Object> toPlainMap(ConfigurationSection section) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                map.put(key, toPlainMap(child));
            } else {
                map.put(key, value);
            }
        }
        return map;
    }
}
