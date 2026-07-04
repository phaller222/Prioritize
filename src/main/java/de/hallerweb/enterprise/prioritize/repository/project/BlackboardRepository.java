package de.hallerweb.enterprise.prioritize.repository.project;

import de.hallerweb.enterprise.prioritize.model.project.Blackboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlackboardRepository extends JpaRepository<Blackboard, Long> {
}
