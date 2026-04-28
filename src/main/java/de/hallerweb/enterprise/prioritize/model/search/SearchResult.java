package de.hallerweb.enterprise.prioritize.model.search;

import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import lombok.*;

import java.util.Set;

/**
 * DTO zur Darstellung eines Suchergebnisses.
 * Hält Informationen über das gefundene Objekt und einen Textauszug (Excerpt).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResult implements Comparable<SearchResult> {

    private PAuthorizedObject result;
    private String resultType;  // Hier könntest du später ein Enum SearchResultType nutzen
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