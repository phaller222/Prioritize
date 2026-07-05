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

package de.hallerweb.enterprise.prioritize.model.search;

import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import lombok.*;

import java.util.Set;

/**
 * DTO for representing a search result.
 * Holds information about the found object and a text excerpt.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResult implements Comparable<SearchResult> {

    private PAuthorizedObject result;
    private String resultType;  // Here you could later use a SearchResultType enum
    private String excerpt;
    private boolean providesExcerpt;

    private Set<SearchResult> subresults;

    @Override
    public int compareTo(SearchResult other) {
        if (this.excerpt == null || other.getExcerpt() == null) {
            return 0;
        }
        return this.excerpt.compareTo(other.getExcerpt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchResult that = (SearchResult) o;
        return result != null ? result.getId().equals(that.result.getId()) : that.result == null;
    }

    @Override
    public int hashCode() {
        return result != null ? result.getId().hashCode() : 0;
    }
}