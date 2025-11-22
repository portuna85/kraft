package com.kraft.domain.category;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("카테고리를 저장하고 조회할 수 있다")
    void saveAndFind() {
        // given
        Category category = Category.builder()
                .name("테스트")
                .description("테스트 카테고리")
                .displayOrder(1)
                .build();

        // when
        Category savedCategory = categoryRepository.save(category);
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<Category> found = categoryRepository.findById(savedCategory.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("테스트");
        assertThat(found.get().getDescription()).isEqualTo("테스트 카테고리");
        assertThat(found.get().getDisplayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("이름으로 카테고리를 찾을 수 있다")
    void findByName() {
        // given
        Category category = Category.builder()
                .name("공지사항")
                .description("공지사항 카테고리")
                .displayOrder(0)
                .build();
        entityManager.persist(category);
        entityManager.flush();

        // when
        Optional<Category> found = categoryRepository.findByName("공지사항");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("공지사항");
    }

    @Test
    @DisplayName("존재하지 않는 이름으로 조회하면 empty를 반환한다")
    void findByName_notFound() {
        // when
        Optional<Category> found = categoryRepository.findByName("존재하지않음");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("카테고리명 존재 여부를 확인할 수 있다")
    void existsByName() {
        // given
        Category category = Category.builder()
                .name("질문")
                .description("질문 카테고리")
                .displayOrder(2)
                .build();
        entityManager.persist(category);
        entityManager.flush();

        // when
        boolean exists = categoryRepository.existsByName("질문");
        boolean notExists = categoryRepository.existsByName("없는카테고리");

        // then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("모든 카테고리를 표시 순서대로 조회할 수 있다")
    void findAllOrderByDisplayOrder() {
        // given
        Category category1 = Category.builder()
                .name("자유")
                .displayOrder(3)
                .build();
        Category category2 = Category.builder()
                .name("공지사항")
                .displayOrder(0)
                .build();
        Category category3 = Category.builder()
                .name("질문")
                .displayOrder(2)
                .build();

        entityManager.persist(category1);
        entityManager.persist(category2);
        entityManager.persist(category3);
        entityManager.flush();

        // when
        List<Category> categories = categoryRepository.findAllOrderByDisplayOrder();

        // then
        assertThat(categories).hasSize(3);
        assertThat(categories.get(0).getName()).isEqualTo("공지사항");
        assertThat(categories.get(1).getName()).isEqualTo("질문");
        assertThat(categories.get(2).getName()).isEqualTo("자유");
    }

    @Test
    @DisplayName("같은 이름으로 중복 저장하면 예외가 발생한다")
    void duplicateName() {
        // given
        Category category1 = Category.builder()
                .name("중복")
                .displayOrder(1)
                .build();
        entityManager.persist(category1);
        entityManager.flush();

        Category category2 = Category.builder()
                .name("중복")
                .displayOrder(2)
                .build();

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> {
                    categoryRepository.save(category2);
                    entityManager.flush();
                }
        );
    }
}

