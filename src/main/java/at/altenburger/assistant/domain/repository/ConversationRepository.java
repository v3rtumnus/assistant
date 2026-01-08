package at.altenburger.assistant.domain.repository;

import at.altenburger.assistant.domain.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {

    @Query("SELECT c FROM ConversationEntity c LEFT JOIN FETCH c.messages WHERE c.id = :id")
    ConversationEntity findByIdWithMessages(String id);
}
