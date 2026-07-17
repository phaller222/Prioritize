/*
 * Copyright 2026 Peter Michael Haller and contributors
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

package de.hallerweb.enterprise.prioritize.repository.document;

import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentInfoRepository extends JpaRepository<DocumentInfo, Long> {

    /**
     * Finds all DocumentInfo-Objects (logical document entities referencing the current document),
     * which belong to the given group.
     */
    List<DocumentInfo> findByDocumentGroup_Id(Long groupId);

    /** Paged variant of {@link #findByDocumentGroup_Id(Long)} for the admin document list (lazy grid). */
    Page<DocumentInfo> findByDocumentGroup_Id(Long groupId, Pageable pageable);

    /** Number of documents in a group — the count callback for the lazy admin document grid. */
    long countByDocumentGroup_Id(Long groupId);

    @Query("SELECT d FROM DocumentInfo d LEFT JOIN FETCH d.lockedBy WHERE d.id = :id")
    Optional<DocumentInfo> findByIdWithLockedBy(@Param("id") Long id);

    // Search via the "currentDocument" relationship on the "name" field
    List<DocumentInfo> findByCurrentDocument_NameContainingIgnoreCase(String name);


    // Search by tags (if you use the 'tag' field in Document)
    List<DocumentInfo> findByCurrentDocument_Tag(String tag);

    // The newest X documents (for a dashboard)
    List<DocumentInfo> findTop10ByOrderByCurrentDocument_LastModifiedDesc();


    // Search via the comment in the current version:
    List<DocumentInfo> findByCurrentDocument_ChangesContainingIgnoreCase(String comment);

}

