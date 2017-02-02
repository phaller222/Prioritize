package de.hallerweb.enterprise.prioritize.model.project.task;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToMany;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoalRecord;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;

@Entity
@NamedQueries({ @NamedQuery(name = "findTaskById", query = "select t FROM Task t WHERE t.id = :taskId"),
	@NamedQuery(name = "findTasksByAssignee", query = "select t FROM Task t WHERE :assignee MEMBER OF  t.assignees"),
	@NamedQuery(name = "findTasksNotAssignedToUser", query = "select t FROM Task t WHERE NOT :assignee MEMBER OF  t.assignees")
	 })
public class Task extends PObject{
	
	
	private int priority;
	private String name;
	private String description;
	private TaskStatus taskStatus;
	@OneToOne(fetch=FetchType.EAGER)
	private Task parent;
	
	@OneToMany(fetch=FetchType.EAGER)
	private List<Task> subTasks;
	
	@JsonIgnore
	@OneToMany
	private List<Resource> resources;
	
	@JsonIgnore
	@OneToMany
	private List<Document> documents;
	
	@JsonIgnore
	@OneToMany
	private List<SkillRecord> requiredSkills;
	
	@ManyToMany(fetch = FetchType.EAGER)
	private List<PActor> assignees;
	
	@JsonIgnore
	@OneToOne 
	ProjectGoalRecord projectGoalRecord;
	

	public ProjectGoalRecord getProjectGoalRecord() {
		return projectGoalRecord;
	}

	public void setProjectGoalRecord(ProjectGoalRecord projectGoalRecord) {
		this.projectGoalRecord = projectGoalRecord;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public TaskStatus getTaskStatus() {
		return taskStatus;
	}

	public void setTaskStatus(TaskStatus taskStatus) {
		this.taskStatus = taskStatus;
	}

	public List<Resource> getResources() {
		return resources;
	}

	public void setResources(List<Resource> resources) {
		this.resources = resources;
	}

	public List<Document> getDocuments() {
		return documents;
	}

	public void setDocuments(List<Document> documents) {
		this.documents = documents;
	}

	public List<SkillRecord> getRequiredSkills() {
		return requiredSkills;
	}

	public void setRequiredSkills(List<SkillRecord> requiredSkills) {
		this.requiredSkills = requiredSkills;
	}

	public List<PActor> getAssignees() {
		return assignees;
	}

	public void setAssignees(List<PActor> assignees) {
		this.assignees = assignees;
	}

	public void setParent(Task parent) {
		this.parent = parent;
	}

	public void setSubTasks(List<Task> subTasks) {
		this.subTasks = subTasks;
	}

	public Task getParent() {
		return parent;
	}

	public List<Task> getSubTasks() {
		return subTasks;
	}

	public int getId() {
		return id;
	}

	public boolean isSubTask() {
		return parent != null;
	}

	public void addAssignee(PActor assignee) {
		if (this.assignees == null) {
			this.assignees = new ArrayList<PActor>();
		}
		this.assignees.add(assignee);
	}
	
	public void removeAssignee(PActor assignee) {
		this.assignees.remove(assignee);
	}
}
