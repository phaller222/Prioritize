package de.hallerweb.enterprise.prioritize.repository.security;

import de.hallerweb.enterprise.prioritize.model.security.PermissionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PermissionRecordRepository extends JpaRepository<PermissionRecord, Integer> {

    List<PermissionRecord> findByDepartment_Id(int departmentId);

    List<PermissionRecord> findByAbsoluteObjectType(String type);
}