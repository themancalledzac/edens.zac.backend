package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.Adventure;
import org.springframework.data.jpa.repository.JpaRepository;

import javax.swing.text.html.Option;
import java.util.Optional;

public interface AdventureRepository extends JpaRepository<Adventure, Long> {

    Optional<Adventure> findByName(String name);
}
