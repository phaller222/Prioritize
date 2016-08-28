package de.hallerweb.enterprise.prioritize.model.project.goal;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;

import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.task.Task;

/**
 * ProjectGoalRecord - A concrete ProjectGoal. Depending if this ProjectGoalRecord indicates a target goal (and value)
 * or a currently achived goal by a task the Task field is the link to the current task assigned to this goal or null 
 * if this ProjectGoalRecord is just the description of what should be achieved (target goal).
 * @author peter
 *
 */
@Entity
@NamedQueries({
		@NamedQuery(name = "findProjectGoalRecordById", query = "select pgr FROM ProjectGoalRecord pgr WHERE pgr.id = :projectGoalRecordId"),
		@NamedQuery(name = "findProjectGoalRecordsByProject", query = "select pgr FROM ProjectGoalRecord pgr WHERE pgr.project.id = :projectId"),
		@NamedQuery(name = "findActiveProjectGoalRecordsByProject", query = "select pgr FROM ProjectGoalRecord pgr WHERE pgr.project.id = :projectId AND pgr.task IS NOT NULL")})
public class ProjectGoalRecord {
	
	
	public ProjectGoalRecord(ProjectGoalRecord origin, ProjectGoalPropertyRecord rec, Task task) {
		this.project = origin.getProject();
		this.task = task;
		this.projectGoal = origin.getProjectGoal();
		this.propertyRecord = rec;
	}
	
	public ProjectGoalRecord() {
		super();
	}

	@Id
	@GeneratedValue
	int id;

	@OneToOne
	Task task;											// null if describing target goal, Link to task if concrete progress.

	@OneToOne
	Project project;									// Project this ProjectGoalRecord belongs to.
	
	@OneToOne
	ProjectGoal projectGoal;							// The base ProjectGoal
	
	int percentage;										// percentage of completion of this ProjectGoalRecord

	public int getPercentage() {
		return percentage;
	}

	public void setPercentage(int percentage) {
		this.percentage = percentage;
	}

	public ProjectGoal getProjectGoal() {
		return projectGoal;
	}

	public void setProjectGoal(ProjectGoal projectGoal) {
		this.projectGoal = projectGoal;
	}

	public Project getProject() {
		return project;
	}

	public void setProject(Project project) {
		this.project = project;
	}

	@OneToOne
	ProjectGoalPropertyRecord propertyRecord;					// Value to achieve if target goal, otherwise current value of underlying task.

	public Task getTask() {
		return task;
	}

	public void setTask(Task task) {
		this.task = task;
	}

	public ProjectGoalPropertyRecord getPropertyRecord() {
		return propertyRecord;
	}

	public void setPropertyRecord(ProjectGoalPropertyRecord property) {
		this.propertyRecord = property;
	}

	public int getId() {
		return id;
	}
}
