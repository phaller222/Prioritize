package de.hallerweb.enterprise.prioritize.view.skill;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.NamedQuery;

import org.primefaces.model.DefaultTreeNode;

import de.hallerweb.enterprise.prioritize.controller.resource.ResourceController;
import de.hallerweb.enterprise.prioritize.controller.security.AuthorizationController;
import de.hallerweb.enterprise.prioritize.controller.security.SessionController;
import de.hallerweb.enterprise.prioritize.controller.security.UserRoleController;
import de.hallerweb.enterprise.prioritize.controller.skill.SkillController;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.User;
import de.hallerweb.enterprise.prioritize.model.skill.Skill;
import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;
import de.hallerweb.enterprise.prioritize.model.skill.SkillProperty;
import de.hallerweb.enterprise.prioritize.model.skill.SkillPropertyNumeric;
import de.hallerweb.enterprise.prioritize.model.skill.SkillPropertyText;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecordProperty;
import de.hallerweb.enterprise.prioritize.service.mqtt.MQTTService;

/**
 * LoggingBean - JSF Backing-Bean to manage skills
 * 
 * <p>
 * Copyright: (c) 2015
 * </p>
 * <p>
 * Peter Haller
 * </p>
 * 
 * @author peter
 */
@Named
@SessionScoped
public class SkillBean implements Serializable {

	@EJB
	SkillController controller;
	@EJB
	AuthorizationController authController;
	@Inject
	SessionController sessionController;
	@EJB
	UserRoleController roleController;
	@EJB
	ResourceController resourceController;
	@Inject
	MQTTService service;

	SkillCategory selectedSkillCategory;
	Skill selectedSkill;
	List<SkillCategory> skillCategories;
	private DefaultTreeNode root;

	private static final String TYPE_ROOT = "rootnode";
	private static final String TYPE_CATEGORY = "category";
	private static final String TYPE_SKILL = "skill";
	private static final String TYPE_SKILL_PROPERTY = "skillproperty";

	public Skill getSelectedSkill() {
		return selectedSkill;
	}

	public void setSelectedSkill(Object selectedSkill) {
		if (selectedSkillItem instanceof Skill) {
			this.selectedSkill = (Skill) selectedSkillItem;
			this.selectedSkill = (Skill) selectedSkill;
		}

	}

	private Object selectedSkillItem = null;
	private SkillCategory newSkillCategory = new SkillCategory();
	private String currentTreeOrientation = "horizontal";

	private static boolean dummyDataCreated = false; // TODO: Remove for production!

	int selectedEnthusiasmLevel;

	public int getSelectedEnthusiasmLevel() {
		return selectedEnthusiasmLevel = 0;
	}

	public void setSelectedEnthusiasmLevel(int selectedEnthusiasmLevel) {
		this.selectedEnthusiasmLevel = selectedEnthusiasmLevel;
	}

	public String getCurrentTreeOrientation() {
		return currentTreeOrientation;
	}

	public void setCurrentTreeOrientation(String currentTreeOrientation) {
		this.currentTreeOrientation = currentTreeOrientation;
	}

	public SkillCategory getNewSkillCategory() {
		return newSkillCategory;
	}

	public void setNewSkillCategory(SkillCategory newSkillCategory) {
		this.newSkillCategory = newSkillCategory;
	}

	public String getTYPE_ROOT() {
		return TYPE_ROOT;
	}

	public Object getSelectedSkillItem() {
		return selectedSkillItem;
	}

	public void setSelectedSkillItem(Object selectedSkillItem) {
		if (selectedSkillItem != null) {
			this.selectedSkillItem = selectedSkillItem;
			if (((DefaultTreeNode) selectedSkillItem).getData() instanceof Skill) {
				this.selectedSkill = (Skill) ((DefaultTreeNode) selectedSkillItem).getData();// selectedSkillItem;
			}
		}
	}

	public String getTYPE_CATEGORY() {
		return TYPE_CATEGORY;
	}

	public String getTYPE_SKILL() {
		return TYPE_SKILL;
	}

	public String getTYPE_SKILL_PROPERTY() {
		return TYPE_SKILL_PROPERTY;
	}

	/**
	 * Build the skill hierarchy and return a DefaultTreeNode.
	 * 
	 * @return
	 */
	public DefaultTreeNode getRoot() {
		Skill sk = new Skill();
		if (authController.canRead(sk, sessionController.getUser())) {
		return root;
		} else {
			return null;
		}
	}

	@Named
	public SkillCategory createSkillCategory(String name, String description, SkillCategory parent) {
		return controller.createSkillCategory(name, description, parent);
	}


	@Named
	public String deleteSkillItem(Object skillItem) {
		if (skillItem instanceof SkillPropertyNumeric) {
			SkillPropertyNumeric numProp = (SkillPropertyNumeric) skillItem;
			controller.deleteSkillPropertyNumeric(numProp.getId());
		} else if (skillItem instanceof SkillPropertyText) {
			SkillPropertyText txtProp = (SkillPropertyText) skillItem;
			controller.deleteSkillPropertyText(txtProp.getId());
		} else if (skillItem instanceof SkillCategory) {
			SkillCategory cat = (SkillCategory) skillItem;
			controller.deleteSkillCategory(cat.getId(), sessionController.getUser());
		} else if (skillItem instanceof Skill) {
			Skill s = (Skill) skillItem;
			controller.deleteSkill(s.getId(), sessionController.getUser());
		}
		return "skills";
	}

	public void deleteSelectedSkillItem() {
		DefaultTreeNode selectedNode = (DefaultTreeNode) this.selectedSkillItem;
		if (!(selectedNode.getData() instanceof DefaultTreeNode)) {
			deleteSkillItem(selectedNode.getData());
		}
		selectedNode.getChildren().clear();
		selectedNode.getParent().getChildren().remove(selectedNode);
		selectedNode.setParent(null);

		selectedNode = null;

	}

	public void addSubcategoryToSelectedItem() {
		DefaultTreeNode selectedNode = (DefaultTreeNode) this.selectedSkillItem;
		if (selectedNode.getData() instanceof SkillCategory) {
			SkillCategory category = (SkillCategory) selectedNode.getData();
			controller.createSkillCategory(newSkillCategory.getName(), newSkillCategory.getDescription(), category);
		}
	}

	@Named
	public String editItem(int skillItem) {
		return "skills";
	}

	@Named
	public String deleteJavaCategory() {
		SkillCategory cat = controller.getCategoryByName("Java");
		controller.deleteSkillCategory(cat.getId(), sessionController.getUser());
		return "/skills";
	}

	/**
	 * Creates dummy data for debugging Purposes.
	 * 
	 * 
	 * @return
	 */
	@PostConstruct
	public void createDummyData() {
		if (dummyDataCreated) {
			return;
		}
		SkillCategory cat2 = controller.createSkillCategory("Programmierung", "Programmierung/Programmiersprachen", null);
		SkillCategory cat22 = controller.createSkillCategory("Java", "Kenntnisse im Bereich Java", cat2);
		SkillCategory cat23 = controller.createSkillCategory("Apple", "Apple Kenntnisse", cat2);

		SkillCategory catCoaching = controller.createSkillCategory("Coaching", "Schulungen/Workshops/Coaching", null);
		Skill workshops = controller.createSkill("Workshops", "Durchführung von Workshops", "coaching,workshops", catCoaching, null,
				AuthorizationController.getSystemUser());

		SkillPropertyNumeric prop1 = new SkillPropertyNumeric();
		prop1.setName("JPA");
		prop1.setDescription("JPA Kenntnisse (0 bis 6).");
		prop1.setNumericProperty(true);
		prop1.setMinValue(0);
		prop1.setMaxValue(6);

		SkillPropertyNumeric prop2 = new SkillPropertyNumeric();
		prop2.setName("JDBC");
		prop2.setDescription("JDBC Kenntnisse (0 bis 6).");
		prop2.setNumericProperty(true);
		prop2.setMinValue(0);
		prop2.setMaxValue(6);

		SkillPropertyText prop22 = new SkillPropertyText();
		prop22.setName("JSF");
		prop22.setDescription("JSF Kenntnisse (Tagnamen).");
		prop22.setText("");
		prop22.setNumericProperty(false);

		HashSet props = new HashSet<SkillProperty>();
		props.add(prop1);
		props.add(prop2);
		props.add(prop22);

		SkillPropertyNumeric prop3 = new SkillPropertyNumeric();
		prop3.setName("Objective-C");
		prop3.setDescription("Objectiv-C Kenntnisse (0 bis 6).");
		prop3.setNumericProperty(true);
		prop3.setMinValue(0);
		prop3.setMaxValue(6);

		SkillPropertyNumeric prop4 = new SkillPropertyNumeric();
		prop4.setName("Swift");
		prop4.setDescription("Swift Kenntnisse (0 bis 6).");
		prop4.setNumericProperty(true);
		prop4.setMinValue(0);
		prop4.setMaxValue(6);

		HashSet props2 = new HashSet<SkillProperty>();
		props2.add(prop3);
		props2.add(prop4);

		Skill sk1 = controller.createSkill("J2EE", "J2EE Kenntnisse", "JPA, JavaMail, EJB", cat22, props, AuthorizationController.getSystemUser());
		Skill sk2 = controller.createSkill("iOS", "iOS Kenntnisse", "Swift, objective-c...", cat23, props2, AuthorizationController.getSystemUser());

		
		
		
		
		
		// // Define Skill Record with properties
		/*
		 * SkillRecordProperty recProp = new SkillRecordProperty(); recProp.setProperty(prop1); recProp.setPropertyValueNumeric(4);
		 * 
		 * SkillRecordProperty recProp2 = new SkillRecordProperty(); recProp2.setProperty(prop22); recProp2.setPropertyValueString(
		 * "form, panelgrid, panel");
		 * 
		 * HashSet<SkillRecordProperty> propsSkill = new HashSet<SkillRecordProperty>(); propsSkill.add(recProp); propsSkill.add(recProp2);
		 * int enthusiasm = 5; SkillRecord rec = controller.createSkillRecord(sk1, propsSkill, enthusiasm); SkillRecord rec2 =
		 * controller.createSkillRecord(workshops, null, enthusiasm);
		 * 
		 * roleController.addSkillToUser(roleController.findUserByUsername("admin").getId(), rec.getId());
		 */
		dummyDataCreated = true;

		// List<SkillCategory> root = controller.getRootCategories();
		// for (SkillCategory cat : root) {
		// System.out.println(cat.getName());
		//
		// List<Skill> skills = controller.getSkillsForCategory(cat);
		// if (skills != null) {
		// for (Skill s : skills) {
		// System.out.println("--->" + s.getName());
		// }
		// }
		// }
		// buildSkillTree();
		// return "/skills";
	}

	private void traverseSkillCategories(DefaultTreeNode parentNode, SkillCategory currentCategory) {

		// Create new Category node (root) and traverse direct children (Skills)
		DefaultTreeNode newNode = new DefaultTreeNode(TYPE_CATEGORY, currentCategory, parentNode);

		// check if Category has subcategories and recursively traverse through
		// them
		List<SkillCategory> categories = controller.findSubCategoriesForCategory(currentCategory);
		if (categories != null) {
			for (SkillCategory category : categories) {
				traverseSkillCategories(newNode, category);
			}
		}

		traverseSkills(newNode, currentCategory);
	}

	private void traverseSkills(DefaultTreeNode parentNode, SkillCategory category) {
		List<Skill> skills = controller.getSkillsForCategory(category, AuthorizationController.getSystemUser());
		if (skills != null) {
			for (Skill skill : skills) {
				DefaultTreeNode skillNode = new DefaultTreeNode(TYPE_SKILL, skill, parentNode);
				List<SkillProperty> skillProperties = controller.getSkillPropertiesForSkill(skill);
				if (skillProperties != null) {
					for (SkillProperty prop : skillProperties) {
						new DefaultTreeNode(TYPE_SKILL_PROPERTY, prop, skillNode);
					}
				}
			}
		}
	}

	@Named
	public boolean isSkillCategory(Object o) {
		return o instanceof SkillCategory;
	}

	@Named
	public boolean isSkill(Object o) {
		return o instanceof Skill;
	}

	@Named
	public boolean isSkillProperty(Object o) {
		return o instanceof SkillProperty;
	}

	public String addSkillCategory(Object category) {
		return "skills";
	}

	@Named
	public void buildSkillTree() {
		List<SkillCategory> categories = controller.getRootCategories();
		root = new DefaultTreeNode(TYPE_ROOT, categories, null);
		root.setData("Skills");

		if (categories != null) {
			for (SkillCategory cat : categories) {
				traverseSkillCategories(root, cat);
			}
		}
	}

	public void assignSkillToUser(String userid) {

		DefaultTreeNode node = (DefaultTreeNode) getSelectedSkillItem();
		Skill selectedSkill = (Skill) node.getData();
		HashSet<SkillRecordProperty> propertyRecords = new HashSet<SkillRecordProperty>();
		Set<SkillProperty> skillProperties = selectedSkill.getSkillProperties();
		for (SkillProperty prop : skillProperties) {
			if (prop.getNumericProperty()) {
				SkillPropertyNumeric numProperty = (SkillPropertyNumeric) prop;
				SkillRecordProperty recNew = new SkillRecordProperty();
				recNew.setProperty(numProperty);
				recNew.setPropertyValueNumeric(numProperty.getTempValue());
				propertyRecords.add(recNew);
			} else {
				SkillPropertyText txtProperty = (SkillPropertyText) prop;
				SkillRecordProperty recNew = new SkillRecordProperty();
				recNew.setProperty(txtProperty);
				recNew.setPropertyValueString(txtProperty.getText());
				propertyRecords.add(recNew);
			}
		}

		User userToAssignSkill = roleController.getUserById(Integer.parseInt(userid), sessionController.getUser());
		SkillRecord rec = controller.createSkillRecord(selectedSkill, propertyRecords, this.selectedEnthusiasmLevel);
		roleController.addSkillToUser(userToAssignSkill.getId(), rec.getId(), sessionController.getUser());
	}

	public void assignSkillToResource(String resourceId) {

		DefaultTreeNode node = (DefaultTreeNode) getSelectedSkillItem();
		Skill selectedSkill = (Skill) node.getData();
		Set<SkillRecordProperty> propertyRecords = new HashSet<SkillRecordProperty>();
		Set<SkillProperty> skillProperties = selectedSkill.getSkillProperties();
		for (SkillProperty prop : skillProperties) {
			if (prop.getNumericProperty()) {
				SkillPropertyNumeric numProperty = (SkillPropertyNumeric) prop;
				SkillRecordProperty recNew = new SkillRecordProperty();
				recNew.setProperty(numProperty);
				recNew.setPropertyValueNumeric(numProperty.getTempValue());
				propertyRecords.add(recNew);
			} else {
				SkillPropertyText txtProperty = (SkillPropertyText) prop;
				SkillRecordProperty recNew = new SkillRecordProperty();
				recNew.setProperty(txtProperty);
				recNew.setPropertyValueString(txtProperty.getText());
				propertyRecords.add(recNew);
			}
		}

		Resource resourceToAssignSkill = resourceController.getResource(Integer.parseInt(resourceId), sessionController.getUser());
		SkillRecord rec = controller.createSkillRecord(selectedSkill, propertyRecords, this.selectedEnthusiasmLevel);
		resourceController.addSkillToResource(resourceToAssignSkill.getId(), rec.getId(), sessionController.getUser());
	}
}
