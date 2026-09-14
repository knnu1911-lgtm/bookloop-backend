package bookloop_backend.repository;

import bookloop_backend.model.Community;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityRepository extends JpaRepository<Community, Long> {

    Optional<Community> findByCode(String code);

    boolean existsByCode(String code);
}
