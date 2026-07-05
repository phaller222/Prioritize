package de.hallerweb.enterprise.prioritize.repository.project;

import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectGoalRepository extends JpaRepository<ProjectGoal, Long> {
}
