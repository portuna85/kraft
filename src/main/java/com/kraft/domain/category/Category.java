package com.kraft.domain.category;

import com.kraft.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "categories", indexes = {
    @Index(name = "idx_category_name", columnList = "name", unique = true)
})
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    private int displayOrder = 0;

    @Builder
    private Category(String name, String description, Integer displayOrder) {
        this.name = name;
        this.description = description;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
    }

    public void update(String name, String description, Integer displayOrder) {
        this.name = name;
        this.description = description;
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
    }
}

