package de.hallerweb.enterprise.prioritize.service;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceReservation;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.security.PermissionRecord;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.repository.document.DocumentGroupRepository;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceGroupRepository;
import de.hallerweb.enterprise.prioritize.repository.security.PermissionRecordRepository;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InitializationService {

    private final UserService userService;
    private final DocumentGroupRepository documentGroupRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final DepartmentRepository departmentRepository;
    private final PermissionRecordRepository permissionRepository;

    @Transactional
    public void initData() {
        PUser admin;
        Department defaultDept;

        // 1. Admin User sicherstellen
        admin = ensureAdminUser();

        // 2. Basis-Abteilung sicherstellen
        defaultDept = ensureDefaultDepartment();

        // 3. Default Dokumentengruppe & Permissions
        ensureDefaultDocumentGroup(defaultDept, admin);

        // 4. Default resource group & permissions
        ensureDefaultResourceGroup(defaultDept, admin);

        // 5. Global permissions for resource management (ID 0 = all instances)
        ensureGeneralResourcePermissions(admin);
    }

    private PUser ensureAdminUser() {
        if (userService.getAllUsers().isEmpty()) {
            PUser admin = new PUser();
            admin.setUsername("admin");
            admin.setPassword("p@ssword");
            admin.setAdmin(true);
            admin.setGender(PUser.Gender.OTHER);
            log.info("Initialer Admin-User 'admin' wurde erstellt.");
            return userService.createUser(admin);
        }
        return userService.getAllUsers().get(0);
    }

    private Department ensureDefaultDepartment() {
        return departmentRepository.findAll().stream().findFirst().orElseGet(() -> {
            Department dept = Department.builder()
                    .name("Main Department")
                    .description("Default department for initial structure")
                    .build();
            log.info("Initiales Department '{}' wurde erstellt.", dept.getName());
            return departmentRepository.save(dept);
        });
    }

    private void ensureDefaultResourceGroup(Department dept, PUser admin) {
        if (resourceGroupRepository.findByDepartment_Id(dept.getId()).isEmpty()) {
            ResourceGroup defResGroup = ResourceGroup.builder()
                    .name(ResourceGroup.DEFAULT_GROUP_NAME)
                    .department(dept)
                    .build();
            resourceGroupRepository.save(defResGroup);

            grantFullAccess(admin, ResourceGroup.class, defResGroup.getId());
            log.info("Default Ressourcengruppe für '{}' erstellt und Admin berechtigt.", dept.getName());
        }
    }

    private void ensureDefaultDocumentGroup(Department dept, PUser admin) {
        if (documentGroupRepository.findByDepartment_Id(dept.getId()).isEmpty()) {
            DocumentGroup defDocGroup = DocumentGroup.builder()
                    .name(DocumentGroup.DEFAULT_GROUP_NAME)
                    .department(dept)
                    .build();
            documentGroupRepository.save(defDocGroup);

            grantFullAccess(admin, DocumentGroup.class, defDocGroup.getId());
            log.info("Default Dokumentengruppe für '{}' erstellt und Admin berechtigt.", dept.getName());
        }
    }

    private void ensureGeneralResourcePermissions(PUser admin) {
        // ID 0 bedeutet laut unserem AuthService "alle Instanzen dieses Typs"
        grantFullAccess(admin, Resource.class, 0L);
        grantFullAccess(admin, ResourceReservation.class, 0L);
    }

    private void grantFullAccess(PUser user, Class<?> clazz, Long objectId) {
        PermissionRecord perm = PermissionRecord.builder()
                .absoluteObjectType(clazz.getCanonicalName())
                .objectId(objectId)
                .createPermission(true)
                .readPermission(true)
                .updatePermission(true)
                .deletePermission(true)
                .build();

        permissionRepository.save(perm);

        // Thanks to @Transactional, the session stays open here and the set can be loaded
        user.addPersonalPermission(perm);
        userService.updateUser(user);
    }
}