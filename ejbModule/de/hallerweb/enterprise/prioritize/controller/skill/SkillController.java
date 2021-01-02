/*
 * Copyright 2015-2020 Peter Michael Haller and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.hallerweb.enterprise.prioritize.controller.skill;

import de.hallerweb.enterprise.prioritize.controller.LoggingController;
import de.hallerweb.enterprise.prioritize.controller.LoggingController.Action;
import de.hallerweb.enterprise.prioritize.controller.security.AuthorizationController;
import de.hallerweb.enterprise.prioritize.controller.security.SessionController;
import de.hallerweb.enterprise.prioritize.controller.security.UserRoleController;
import de.hallerweb.enterprise.prioritize.model.security.User;
import de.hallerweb.enterprise.prioritize.model.skill.*;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.enterprise.context.ContextNotActiveException;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.List;
import java.util.Set;

/**
 * SkillController.java - Controls the creation, modification and deletion of {@link Skill} objects. Also the associated
 * {@link SkillCategory} and {@link SkillProperty} objects are handled here.
 */
@Stateless
public class SkillController {

    @PersistenceContext
    EntityManager em;
    @EJB
    LoggingController logger;
    @Inject
    SessionController sessionController;
    @EJB
    AuthorizationController authController;

    public static final String LITERAL_SKILLCATEGORY = "SkillCategory";
    /**
     * Creates a new SkillCategory with the given data
     *
     * @param name   - Name of category
     * @param parent Parent {@link SkillCategory}. null if this is a root category.
     * @return the created {@link SkillCategory}
     */
    public SkillCategory createSkillCategory(String name, String description, SkillCategory parent, User sessionUser) {
        if (authController.canCreate(parent, sessionUser)) {
            boolean alreadyExists = false;
            if (parent != null) {
                alreadyExists = categoryExists(name);
            }

            if (alreadyExists) {
                return null;
            } else {
                SkillCategory category = new SkillCategory();
                category.setName(name);
                category.setDescription(description);
                category.setParentCategory(parent);

                em.persist(category);
                if (parent != null) {
                    parent.addSubCategory(category);
                }

                em.flush();
                try {
                    logger.log(sessionController.getUser().getUsername(), LITERAL_SKILLCATEGORY, Action.CREATE, category.getId(),
                            " " + LITERAL_SKILLCATEGORY + " " + category.getName() + "\" created.");
                } catch (ContextNotActiveException ex) {
                    logger.log("SYSTEM", LITERAL_SKILLCATEGORY, Action.CREATE, category.getId(),
                            " " + LITERAL_SKILLCATEGORY + " " + category.getName() + "\" created.");
                }
                return category;
            }
        } else {
            return null;
        }
    }

    private boolean categoryExists(String name) {
        List<SkillCategory> categories = getAllCategories();
        for (SkillCategory category : categories) {
            if (category.getName().equals(name)) {
                // TODO: Dubletten in unterschiedlichen ebenen erlauben!
                return true;
            }
        }
        return false;
    }

    public Skill createSkill(String name, String description, String keywords, SkillCategory category, Set<SkillProperty> properties,
                             User sessionUser) {
        Skill skill = new Skill();
        // Check permissions first
        if (authController.canCreate(skill, sessionUser)) {

            skill.setName(name);
            skill.setDescription(description);
            skill.setKeywords(keywords);
            skill.setCategory(category);
            em.persist(skill);

            if (properties != null) {
                for (SkillProperty prop : properties) {
                    if (prop instanceof SkillPropertyNumeric) {
                        SkillPropertyNumeric propNumeric = (SkillPropertyNumeric) prop;
                        propNumeric.setSkill(skill);
                        em.persist(propNumeric);
                        skill.addSkillProperty(propNumeric);
                    } else if (prop instanceof SkillPropertyText) {
                        SkillPropertyText propText = (SkillPropertyText) prop;
                        propText.setSkill(skill);
                        em.persist(propText);
                        skill.addSkillProperty(propText);
                    }
                }
            }

            em.flush();
            try {
                logger.log(sessionController.getUser().getUsername(), "Skill", Action.CREATE, skill.getId(),
                        " Skill \"" + skill.getName() + "\" created.");
            } catch (ContextNotActiveException ex) {
                logger.log("SYSTEM", "Skill", Action.CREATE, skill.getId(), " Skill \"" + skill.getName() + "\" created.");
            }
            return skill;
        } else {
            return null;
        }
    }

    public SkillRecord createSkillRecord(Skill skill, Set<SkillRecordProperty> propertyRecords, int enthusiasm) {
        SkillRecord rec = new SkillRecord();

        if (propertyRecords != null) {
            for (SkillRecordProperty prop : propertyRecords) {
                em.persist(prop);
                em.flush();
            }
        }
        rec.setSkillProperties(propertyRecords);
        rec.setSkill(skill);
        rec.setEnthusiasm(enthusiasm);
        em.persist(rec);
        return rec;
    }

    /**
     * Find all {@link SkillCategory} objects at the top level (parent=root).
     *
     * @return
     */
    public List<SkillCategory> getRootCategories() {
        Query query = em.createNamedQuery("findRootCategories");

        List<SkillCategory> result = query.getResultList();
        if (result.isEmpty()) {
            return null;
        } else {
            return result;
        }
    }

    public List<SkillCategory> getAllCategories() {
        Query query = em.createNamedQuery("findAllCategories");

        List<SkillCategory> result = query.getResultList();
        if (!result.isEmpty()) {
            return result;
        } else {
            return null;
        }
    }

    public List<Skill> getAllSkills(User sessionUser) {
        Query query = em.createNamedQuery("findAllSkills");
        List<Skill> result = query.getResultList();
        if (result.isEmpty()) {
            return null;
        } else {
            Skill s = result.get(0);
            if (authController.canRead(s, sessionUser)) {
                return result;
            } else {
                return null;
            }
        }
    }

    public SkillCategory getCategoryByName(String categoryName) {
        Query query = em.createNamedQuery("findCategoryByName");
        SkillCategory category = null;
        query.setParameter("categoryName", categoryName);
        try {
            category = (SkillCategory) query.getSingleResult();
        } catch (NoResultException ex) {
            return category;
        }
        return category;
    }

    public SkillCategory getCategoryById(String categoryId) {
        Query query = em.createNamedQuery("findCategoryById");
        query.setParameter("categoryId", Integer.parseInt(categoryId));
        return (SkillCategory) query.getSingleResult();
    }

    public List<Skill> getSkillsForCategory(SkillCategory cat, User sessionUser) {
        Query query = em.createNamedQuery("findSkillsForCategory");
        query.setParameter("catId", cat.getId());

        @SuppressWarnings("unchecked")
        List<Skill> result = query.getResultList();
        if (!result.isEmpty()) {
            Skill s = result.get(0);
            if (authController.canRead(s, sessionUser)) {
                return result;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public List<SkillProperty> getSkillPropertiesForSkill(Skill skill) {
        Query query = em.createNamedQuery("findSkillPropertiesForSkill");
        query.setParameter("skillId", skill.getId());

        @SuppressWarnings("unchecked")
        List<SkillProperty> result = query.getResultList();
        if (result.isEmpty()) {
            return null;
        } else {
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    public List<SkillRecord> getSkillRecordsForSkill(Skill skill) {
        Query query = em.createNamedQuery("findSkillRecordsForSkill");
        query.setParameter("skillId", skill.getId());
        return (List<SkillRecord>) query.getResultList();
    }

    public void deleteSkill(int skillId, User sessionUser) {
        Skill skill = em.find(Skill.class, skillId);
        if (authController.canDelete(skill, sessionUser)) {

            // Remove all SkillRecords for that skill from Users and delete them.
            List<SkillRecord> skillRecords = getSkillRecordsForSkill(skill);
            if (!skillRecords.isEmpty()) {
                for (SkillRecord record : skillRecords) {
                    if (record.getUser() != null) {
                        record.getUser().removeSkill(record);
                    }
                    em.remove(record);
                }
            }
            em.remove(skill);
            em.flush();
            try {
                logger.log(sessionController.getUser().getUsername(), "Skill", Action.DELETE, skill.getId(),
                        " Skill \"" + skill.getName() + "\" deleted.");
            } catch (ContextNotActiveException ex) {
                logger.log("SYSTEM", "Skill", Action.DELETE, skill.getId(), " Skill \"" + skill.getName() + "\" deleted.");
            }
        }
    }

    public void deleteSkillPropertyNumeric(int propertyId) {
        SkillPropertyNumeric prop = em.find(SkillPropertyNumeric.class, propertyId);
        em.remove(prop);
        em.flush();
    }

    public void deleteSkillPropertyText(int propertyId) {
        SkillPropertyText prop = em.find(SkillPropertyText.class, propertyId);
        em.remove(prop);
        em.flush();
    }

    public void deleteSkillCategory(int categoryId, User sessionUser) {
        SkillCategory category = em.find(SkillCategory.class, categoryId);

        // First traverse all subcategories and delete all skills within
        List<SkillCategory> subcategories = findSubCategoriesForCategory(category);
        if (subcategories != null) {
            for (SkillCategory cat : subcategories) {
                deleteSkillsInCategory(cat, sessionUser);
                deleteSkillCategory(cat.getId(), sessionUser);
            }
        }

        // then delete all skills within this category and at last remove this category.
        deleteSkillsInCategory(category, sessionUser);
        em.remove(category);
        em.flush();

        try {
            logger.log(sessionController.getUser().getUsername(), "SkillCategory", Action.DELETE, category.getId(),
                    " SkillCategory \"" + category.getName() + "\" deleted.");
        } catch (ContextNotActiveException ex) {
            logger.log("SYSTEM", "SkillCategory", Action.DELETE, category.getId(),
                    " SkillCategory \"" + category.getName() + "\" deleted.");
        }
    }

    public void deleteSkillsInCategory(SkillCategory category, User sessionUser) {
        List<Skill> skills = getSkillsForCategory(category, sessionUser);
        if (skills != null) {
            for (Skill skill : skills) {
                if (authController.canDelete(skill, sessionUser)) {
                    // first find all instances (skillRecords) of this skill and remove them.
                    List<SkillRecord> records = getSkillRecordsForSkill(skill);
                    for (SkillRecord record : records) {
                        if (record.getUser() != null) {
                            record.getUser().removeSkill(record);
                        }
                        em.remove(record);
                        em.flush();
                    }

                    em.remove(skill);
                    em.flush();
                }
            }
        }
    }

    public List<SkillCategory> findSubCategoriesForCategory(SkillCategory cat) {
        Query query = em.createNamedQuery("findSubCategoriesForCategory");
        query.setParameter("parentCategoryId", cat.getId());

        List<SkillCategory> result = query.getResultList();
        if (result.isEmpty()) {
            return null;
        } else {
            return result;
        }
    }
}
