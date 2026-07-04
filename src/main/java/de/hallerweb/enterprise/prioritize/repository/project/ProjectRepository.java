package de.hallerweb.enterprise.prioritize.repository.project;

import de.hallerweb.enterprise.prioritize.model.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByNameContainingIgnoreCase(String name);

    List<Project> findByManager_Id(Long managerId);

    List<Project> findByMembers_Id(Long userId);
}
