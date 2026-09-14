package net.teujaem.randombox.storage;

import net.teujaem.jpalib.jpa.JpaManager;
import net.teujaem.plugin.SPFramework;
import net.teujaem.randombox.entity.BoxOpenLogEntity;
import net.teujaem.randombox.entity.RandomBoxEntity;
import net.teujaem.randombox.entity.RewardEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 이 플러그인에서 DB 를 직접 만지는 유일한 클래스.
 *
 * <p>공개 API 인 {@code net.teujaem.plugin.api.DataBase} 를 쓰지 않고
 * {@link JpaManager} 를 직접 쓰는 이유:
 * <ul>
 *   <li>{@code DataBase.find()} 는 PK 단건 조회뿐이다.
 *       "박스 전체 목록"을 읽으려면 JPQL 이 필요하다.</li>
 *   <li>{@code DataBase.save()} 는 {@code CompletableFuture<Boolean>} 을 돌려준다.
 *       박스는 보상을 cascade 로 함께 저장하는데, 새 보상의 {@code @GeneratedValue} id 는
 *       merge 가 만든 관리 인스턴스에만 채워진다. 그 인스턴스를 못 받으면 캐시의 보상 id 가
 *       계속 null 이라 다음 저장 때 중복 INSERT 가 난다. → merge 의 반환값이 필요하다.</li>
 * </ul>
 * (2026-09-08 수정본에서 {@code DataBase.save()} 가 UPSERT 로 바뀌어, "수정 저장 불가" 문제는
 * 해소되었다. 위 두 가지 이유만 남았다.)
 *
 * <p>{@code DatabaseController#createJpaManager} 와 {@code JpaManager} 생성자는 모두 public 이라
 * 그대로 쓸 수 있다. 팀에서 내부 패키지 사용을 막으면 이 파일만 고치면 된다.
 */
public final class BoxRepository {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("RandomBox");

    private final JpaManager jpa;

    /**
     * 접속 정보는 SP-Framework 의 {@code config.yaml} 을 그대로 쓴다.
     * 여기서는 엔티티만 등록하면 된다. 커넥션의 생성·종료도 프레임워크가 맡는다.
     */
    public BoxRepository() {
        this.jpa = SPFramework.getInstance()
                .getDatabaseController()
                .createJpaManager(
                        RandomBoxEntity.class,
                        RewardEntity.class,
                        BoxOpenLogEntity.class);
    }

    // ---------------------------------------------------------------- 박스

    /** 모든 박스를 보상까지 한 번에 읽는다. */
    public CompletableFuture<List<RandomBoxEntity>> loadAllBoxes() {
        return jpa.executeAsync(em -> em.createQuery(
                        "select distinct b from RandomBoxEntity b left join fetch b.rewards",
                        RandomBoxEntity.class)
                .getResultList());
    }

    /**
     * 저장(신규 INSERT / 기존 UPDATE 겸용).
     *
     * @return DB 에 반영된 인스턴스. 새로 만든 보상의 id 가 채워져 있으므로
     *         호출한 쪽 캐시는 반드시 이 반환값으로 교체해야 한다.
     */
    public CompletableFuture<RandomBoxEntity> saveBox(RandomBoxEntity box) {
        return jpa.executeAsync(em -> em.merge(box));
    }

    public CompletableFuture<Boolean> deleteBox(String boxId) {
        return jpa.executeAsync(em -> {
            RandomBoxEntity found = em.find(RandomBoxEntity.class, boxId);
            if (found == null) {
                return false;
            }
            em.remove(found);
            return true;
        });
    }

    /** 서버 종료 시점에 쓰는 동기 저장. 비동기 태스크가 잘려나가는 걸 막는다. */
    public void saveBoxSync(RandomBoxEntity box) {
        jpa.execute(em -> em.merge(box));
    }

    // ------------------------------------------------------------------ 로그

    /**
     * 개봉 기록 남기기. 실패해도 개봉 자체를 막지 않는다.
     * 다만 조용히 삼키면 나중에 로그가 비어 있는 이유를 알 수 없으므로 경고는 남긴다.
     * (서버 종료 중 호출되면 executor 가 이미 닫혀 있어 여기서 바로 던질 수 있다.)
     */
    public void logOpen(BoxOpenLogEntity log) {
        try {
            jpa.executeAsync(em -> {
                em.persist(log);
                return true;
            }).exceptionally(error -> {
                LOGGER.warning("개봉 로그 저장 실패: " + error.getMessage());
                return false;
            });
        } catch (RuntimeException e) {
            LOGGER.warning("개봉 로그 저장 실패: " + e.getMessage());
        }
    }
}
