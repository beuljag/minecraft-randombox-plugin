package net.teujaem.randombox.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.teujaem.randombox.util.ItemCodec;
import net.teujaem.randombox.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ItemStack &lt;-&gt; yml 섹션.
 *
 * <p>사람이 직접 고칠 수 있게 <b>읽을 수 있는 형식</b>으로 쓰는 게 목적이다.
 * 다만 읽을 수 있는 형식으로는 표현이 안 되는 아이템(포션 효과, 주민 거래,
 * 다른 플러그인의 PDC, 속성 수정자 등)도 있으므로, 내보낼 때 왕복 검사를 해서
 * 손실이 생기면 {@code data:} 에 Base64 를 함께 적어둔다.
 *
 * <p>읽을 때 {@code data:} 가 있으면 그쪽이 우선이다. 사람이 위쪽 항목을 손으로
 * 고쳤는데 무시당하는 상황을 막으려고, 내보낼 때 그 줄에 경고 주석을 붙인다.
 */
public final class ItemYamlCodec {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private ItemYamlCodec() {
    }

    // ------------------------------------------------------------ 읽기

    /**
     * @param section 아이템 노드. {@code null} 이면 {@code null} 반환.
     * @throws IllegalArgumentException 재료 이름이 틀렸을 때 (어디가 틀렸는지 알려주기 위해)
     */
    public static ItemStack read(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        // Base64 가 있으면 그게 원본이다.
        String base64 = section.getString("data");
        if (base64 != null && !base64.isBlank()) {
            ItemStack decoded = ItemCodec.decode(base64);
            if (decoded != null) {
                return decoded;
            }
            // 디코드 실패(버전 차이 등)면 아래 사람이 읽는 형식으로라도 살려본다.
        }

        String materialName = section.getString("material");
        if (materialName == null || materialName.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(materialName.trim().toUpperCase(Locale.ROOT));
        if (material == null || material.isAir()) {
            throw new IllegalArgumentException("알 수 없는 material: " + materialName);
        }

        ItemStack item = new ItemStack(material, Math.max(1, section.getInt("amount", 1)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String name = section.getString("name");
        if (name != null && !name.isBlank()) {
            meta.displayName(Text.of(name));
        }

        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>(lore.size());
            for (String line : lore) {
                lines.add(Text.of(line));
            }
            meta.lore(lines);
        }

        if (section.getBoolean("unbreakable", false)) {
            meta.setUnbreakable(true);
        }

        if (section.contains("custom-model-data")) {
            // deprecated 지만 아직 동작한다. 후속 API(CustomModelDataComponent)는 int 하나가 아니라
            // float/문자열/색 목록이라 yml 형식까지 바뀌므로, 리소스팩 쪽 요구가 생기면 그때 옮길 것.
            meta.setCustomModelData(section.getInt("custom-model-data"));
        }

        ConfigurationSection enchants = section.getConfigurationSection("enchants");
        if (enchants != null) {
            for (String key : enchants.getKeys(false)) {
                Enchantment enchantment = findEnchantment(key);
                if (enchantment == null) {
                    throw new IllegalArgumentException("알 수 없는 인챈트: " + key);
                }
                meta.addEnchant(enchantment, Math.max(1, enchants.getInt(key)), true);
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    private static Enchantment findEnchantment(String raw) {
        String key = raw.trim().toLowerCase(Locale.ROOT);
        NamespacedKey namespaced = key.contains(":")
                ? NamespacedKey.fromString(key)
                : NamespacedKey.minecraft(key);
        if (namespaced == null) {
            return null;
        }
        // Registry.ENCHANTMENT 는 deprecated. RegistryAccess 를 거치는 게 현재 방식이다.
        return RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(namespaced);
    }

    // ------------------------------------------------------------ 쓰기

    /**
     * 아이템을 {@code parent} 아래 {@code key} 섹션으로 기록한다.
     * 사람이 읽는 형식으로 표현이 안 되면 {@code data:} (Base64) 를 덧붙인다.
     */
    public static void write(ConfigurationSection parent, String key, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ConfigurationSection section = parent.createSection(key);
        section.set("material", item.getType().name());
        if (item.getAmount() != 1) {
            section.set("amount", item.getAmount());
        }

        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null) {
            if (meta.hasDisplayName()) {
                section.set("name", LEGACY.serialize(meta.displayName()));
            }
            if (meta.hasLore()) {
                List<String> lore = new ArrayList<>();
                for (Component line : meta.lore()) {
                    lore.add(LEGACY.serialize(line));
                }
                section.set("lore", lore);
            }
            if (meta.isUnbreakable()) {
                section.set("unbreakable", true);
            }
            if (meta.hasCustomModelData()) {
                section.set("custom-model-data", meta.getCustomModelData());
            }
            Map<Enchantment, Integer> enchants = item.getEnchantments();
            if (!enchants.isEmpty()) {
                ConfigurationSection enchantSection = section.createSection("enchants");
                for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                    enchantSection.set(entry.getKey().getKey().getKey(), entry.getValue());
                }
            }
        }

        // 왕복 검사: 방금 쓴 걸 그대로 읽었을 때 원본과 같은가?
        if (!isLossless(section, item)) {
            section.set("data", ItemCodec.encode(item));
            section.setComments("data", List.of(
                    "이 줄이 있으면 위 항목들은 무시되고 이 값이 쓰입니다.",
                    "(yml 로 표현할 수 없는 정보가 있는 아이템이라 원본을 그대로 담아둔 것)",
                    "위 항목을 손으로 고쳐서 반영하고 싶으면 이 data 줄을 지우세요."));
        }
    }

    private static boolean isLossless(ConfigurationSection written, ItemStack original) {
        try {
            ItemStack rebuilt = read(written);
            return rebuilt != null
                    && rebuilt.getAmount() == original.getAmount()
                    && rebuilt.isSimilar(original);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
