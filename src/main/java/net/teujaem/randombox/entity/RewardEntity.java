package net.teujaem.randombox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 박스 하나에 들어가는 보상 한 줄.
 * 아이템 지급과 명령어 실행을 둘 다 가질 수 있다.
 */
@Entity
@Table(name = "rb_reward")
public class RewardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 주의: LAZY 다. EntityManager 가 닫힌 뒤에는 getBox() 를 호출하지 말 것.
     * 박스는 항상 RandomBoxEntity 쪽에서 들고 다닌다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "box_id", nullable = false)
    private RandomBoxEntity box;

    /** 지급할 아이템(Base64). null 이면 명령어 전용 보상. */
    @Column(name = "item_data", length = 65535)
    private String itemData;

    /** 가중치. 확률 = weight / 박스 전체 weight 합. */
    @Column(name = "weight", nullable = false)
    private int weight = 10;

    /** 당첨 시 콘솔에서 실행할 명령어. %player% 가 치환된다. */
    @Column(name = "command_line", length = 512)
    private String commandLine;

    /** 메시지/방송에 쓸 이름. 비우면 아이템 이름을 쓴다. */
    @Column(name = "label", length = 128)
    private String label;

    /** JPA 용 기본 생성자. */
    public RewardEntity() {
    }

    public RewardEntity(String itemData, int weight) {
        this.itemData = itemData;
        this.weight = weight;
    }

    public Long getId() {
        return id;
    }

    public RandomBoxEntity getBox() {
        return box;
    }

    public void setBox(RandomBoxEntity box) {
        this.box = box;
    }

    public String getItemData() {
        return itemData;
    }

    public void setItemData(String itemData) {
        this.itemData = itemData;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = Math.max(0, Math.min(1000000, weight));
    }

    public String getCommandLine() {
        return commandLine;
    }

    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
