package net.teujaem.randombox.service;

import net.teujaem.randombox.RandomBoxPlugin;
import net.teujaem.randombox.entity.RandomBoxEntity;
import net.teujaem.randombox.storage.BoxRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 박스 캐시. 읽기는 전부 메모리에서, 쓰기는 DB 반영 후 캐시를 교체한다.
 *
 * <p>merge 는 새 인스턴스를 돌려주기 때문에(새 보상의 id 가 이때 채워진다)
 * 저장 후에는 반드시 반환값으로 캐시를 갈아끼워야 한다.
 */
public final class BoxManager {

    private final RandomBoxPlugin plugin;
    private final BoxRepository repository;
    private final Map<String, RandomBoxEntity> cache = new ConcurrentHashMap<>();

    public BoxManager(RandomBoxPlugin plugin, BoxRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    /** 박스 id 규칙: 영문/숫자/_/- 만, 최대 32자. 명령어 인자로 쓰기 때문에 공백 금지. */
    public static boolean isValidId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,32}");
    }

    public static String normalizeId(String id) {
        return id == null ? null : id.toLowerCase(Locale.ROOT);
    }

    public CompletableFuture<Integer> reload() {
        return plugin.onMain(repository.loadAllBoxes()).thenApply(boxes -> {
            cache.clear();
            for (RandomBoxEntity box : boxes) {
                cache.put(box.getId(), box);
            }
            return cache.size();
        });
    }

    public RandomBoxEntity get(String id) {
        return cache.get(normalizeId(id));
    }

    public boolean exists(String id) {
        return cache.containsKey(normalizeId(id));
    }

    public Collection<RandomBoxEntity> all() {
        return new ArrayList<>(cache.values());
    }

    public List<String> ids() {
        return new ArrayList<>(cache.keySet());
    }

    public CompletableFuture<RandomBoxEntity> create(String id, String displayName) {
        RandomBoxEntity box = new RandomBoxEntity(normalizeId(id), displayName);
        return save(box);
    }

    /** 저장하고 캐시를 갱신한다. 완료 콜백은 메인 스레드에서 돈다. */
    public CompletableFuture<RandomBoxEntity> save(RandomBoxEntity box) {
        box.touch();
        return plugin.onMain(repository.saveBox(box)).thenApply(saved -> {
            cache.put(saved.getId(), saved);
            return saved;
        });
    }

    public CompletableFuture<Boolean> delete(String id) {
        String boxId = normalizeId(id);
        return plugin.onMain(repository.deleteBox(boxId)).thenApply(removed -> {
            if (removed) {
                cache.remove(boxId);
            }
            return removed;
        });
    }
}
