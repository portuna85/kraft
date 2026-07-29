package com.kraft.winningnumber;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WinningNumberRepository extends JpaRepository<WinningNumber, Long> {

    Optional<WinningNumber> findTopByOrderByRoundDesc();

    Optional<WinningNumber> findByRound(Integer round);

    List<WinningNumber> findAllByCombinationMaskOrderByRoundDesc(Long combinationMask);

    Page<WinningNumber> findAllByOrderByRoundDesc(Pageable pageable);

    @Query("select w.round from WinningNumber w order by w.round asc")
    List<Integer> findAllRoundsOrderByRoundAsc();

    @Query("select w.round as round, w.n1 as n1, w.n2 as n2, w.n3 as n3, w.n4 as n4, w.n5 as n5, w.n6 as n6 from WinningNumber w")
    List<WinningBallsOnly> findAllBalls();

    @Query("select w.round as round, w.n1 as n1, w.n2 as n2, w.n3 as n3, w.n4 as n4, w.n5 as n5, w.n6 as n6 "
            + "from WinningNumber w order by w.round desc")
    List<WinningBallsOnly> findBallsByOrderByRoundDesc(Pageable pageable);
}
