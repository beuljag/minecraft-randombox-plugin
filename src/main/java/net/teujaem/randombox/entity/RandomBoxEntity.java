package net.teujaem.randombox.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 랜덤박스 한 종류. PK 는 관리자가 정하는 박스 id.
 * 보상 목록은 EAGER 로 함께 읽는다 — EntityManager 가 닫힌 뒤에 접근하기 때문.
 */
@Entity
@Table(name = "rb_box")
public class RandomBoxEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "display_name", length = 128, nullable = false)
    private String displayName;

    /** 상자로 쓸 아이템(Base64). null 이면 기본 상자(CHEST). */
    @Column(name = "box_item_data", length = 65535)
    private String boxItemData;

    /** 한 번 열 때 뽑는 보상 개수. */
    @Column(name = "roll_count", nullable = false)
    private int rollCount = 1;

    @Column(name = "broadcast_win", nullable = false)
    private boolean broadcastWin = false;

    @Column(name = "animate", nullable = false)
    private boolean animate = true;

    /** 상자를 들고 왼손 키(F)를 누르면 확률표를 띄울지. */
    @Column(name = "preview_on_swap", nullable = false)
    private boolean previewOnSwap = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "box", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RewardEntity> rewards = new ArrayList<>();

    /** JPA 용 기본 생성자. */
    public RandomBoxEntity() {
    }

    public RandomBoxEntity(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBoxItemData() {
        return boxItemData;
    }

    public void setBoxItemData(String boxItemData) {
        this.boxItemData = boxItemData;
    }

    public int getRollCount() {
        return rollCount;
    }

    public void setRollCount(int rollCount) {
        this.rollCount = Math.max(1, Math.min(9, rollCount));
    }

    public boolean isBroadcastWin() {
        return broadcastWin;
    }

    public void setBroadcastWin(boolean broadcastWin) {
        this.broadcastWin = broadcastWin;
    }

    public boolean isAnimate() {
        return animate;
    }

    public void setAnimate(boolean animate) {
        this.animate = animate;
    }

    public boolean isPreviewOnSwap() {
        return previewOnSwap;
    }

    public void setPreviewOnSwap(boolean previewOnSwap) {
        this.previewOnSwap = previewOnSwap;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public List<RewardEntity> getRewards() {
        return rewards;
    }

    public void addReward(RewardEntity reward) {
        reward.setBox(this);
        rewards.add(reward);
    }

    public void removeReward(RewardEntity reward) {
        // orphanRemoval = true 이므로 목록에서 빼면 다음 merge 때 DELETE 된다.
        rewards.remove(reward);
    }

    public int totalWeight() {
        int sum = 0;
        for (RewardEntity reward : rewards) {
            sum += Math.max(0, reward.getWeight());
        }
        return sum;
    }
}
