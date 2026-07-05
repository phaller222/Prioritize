package de.hallerweb.enterprise.prioritize.repository.project;

import de.hallerweb.enterprise.prioritize.model.project.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByBlackboard_Id(Long blackboardId);

    List<Task> findByAssignee_Id(Long assigneeId);

    List<Task> findByBlackboard_IdAndAssigneeIsNull(Long blackboardId);

    List<Task> findByGoal_Id(Long goalId);
}
