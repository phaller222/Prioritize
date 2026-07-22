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

package de.hallerweb.enterprise.prioritize.service.process;

import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinition;
import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinitionState;
import de.hallerweb.enterprise.prioritize.repository.process.ProcessDefinitionRepository;
import de.hallerweb.enterprise.prioritize.service.document.DocumentDeletionEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Decides what happens to a {@link ProcessDefinition} when the document carrying its BPMN is deleted.
 * <p>
 * A draft was never deployed — nothing ran from it, so it goes with its document and the deletion
 * proceeds. A definition that has been deployed is a different matter: the engine holds a deployment
 * and possibly history, and this entity is the only thing tying that back to a document. Deleting the
 * source of something that has run would leave the deployment unattributable, so the deletion is
 * <b>vetoed</b> — consistent with {@code unregister}, which refuses for the same reason.
 * <p>
 * The veto works because {@code DocumentDeletionEvent} is handled synchronously inside the deleting
 * transaction: the exception propagates out of the publish call and the whole delete rolls back. The
 * thrown {@code IllegalStateException} surfaces as a 409 with a message saying what to do instead.
 *
 * @author peter haller
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class ProcessDefinitionDocumentCleanup {

    private final ProcessDefinitionRepository definitionRepository;

    @EventListener
    public void onDocumentDeletion(DocumentDeletionEvent event) {
        List<ProcessDefinition> definitions = definitionRepository.findByDocumentInfo_Id(event.documentInfoId());
        if (definitions.isEmpty()) {
            return;
        }

        for (ProcessDefinition definition : definitions) {
            if (definition.getState() != ProcessDefinitionState.DRAFT) {
                throw new IllegalStateException("Document " + event.documentInfoId()
                        + " is the source of the deployed process definition '" + definition.getProcessKey()
                        + "' and cannot be deleted; deactivate the definition instead.");
            }
        }

        definitionRepository.deleteAll(definitions);
        log.info("Removed {} draft process definition(s) of deleted document {}.",
                definitions.size(), event.documentInfoId());
    }
}
