package net.teujaem.randombox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/** 개봉 기록. 확률 시비가 붙었을 때 근거가 된다. 쓰기 전용. */
@Entity
@Table(name = "rb_open_log")
public class BoxOpenLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "box_id", length = 64, nullable = false)
    private String boxId;

    @Column(name = "player_uuid", length = 36, nullable = false)
    private String playerUuid;

    @Column(name = "player_name", length = 32)
    private String playerName;

    @Column(name = "reward_label", length = 128)
    private String rewardLabel;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    /** JPA 용 기본 생성자. */
    public BoxOpenLogEntity() {
    }

    public BoxOpenLogEntity(String boxId, UUID playerUuid, String playerName, String rewardLabel) {
        this.boxId = boxId;
        this.playerUuid = playerUuid.toString();
        this.playerName = playerName;
        this.rewardLabel = rewardLabel;
        this.openedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getBoxId() {
        return boxId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getRewardLabel() {
        return rewardLabel;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }
}
