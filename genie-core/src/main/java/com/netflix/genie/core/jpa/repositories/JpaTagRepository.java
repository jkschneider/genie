/*
 *
 *  Copyright 2017 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.genie.core.jpa.repositories;

import com.netflix.genie.core.jpa.entities.TagEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.Optional;
import java.util.Set;

/**
 * Repository for tags.
 *
 * @author tgianos
 * @since 3.3.0
 */
public interface JpaTagRepository extends JpaIdRepository<TagEntity> {

    /**
     * This is the query used to find and delete tags that aren't referenced by any of the other tables.
     */
    String DELETE_UNUSED_TAGS_SQL =
        "DELETE "
            + "FROM tags "
            + "WHERE id NOT IN (SELECT DISTINCT(tag_id) FROM applications_tags) "
            + "AND id NOT IN (SELECT DISTINCT(tag_id) FROM clusters_tags) "
            + "AND id NOT IN (SELECT DISTINCT(tag_id) FROM commands_tags) "
            + "AND id NOT IN (SELECT DISTINCT(tag_id) FROM criteria_tags) "
            + "AND id NOT IN (SELECT DISTINCT(tag_id) FROM jobs_tags) "
            + "AND created <= :createdThreshold ;";

    /**
     * Find a tag by its unique tag value.
     *
     * @param tag The tag value to search for
     * @return An Optional of a TagEntity
     */
    Optional<TagEntity> findByTag(final String tag);

    /**
     * Find out whether a tag entity with the given tag value exists.
     *
     * @param tag The tag value to check for
     * @return True if the tag exists
     */
    boolean existsByTag(final String tag);

    /**
     * Find tag entities where the tag value is in the given set of tags.
     *
     * @param tags The tags to find entities for
     * @return The tag entities
     */
    Set<TagEntity> findByTagIn(final Set<String> tags);

    /**
     * Delete all tags from the database that aren't referenced which were created before the supplied created
     * threshold.
     *
     * @param createdThreshold The instant in time where tags created before this time that aren't referenced
     *                         will be deleted. Inclusive
     * @return The number of tags deleted
     */
    @Modifying
    @Query(value = DELETE_UNUSED_TAGS_SQL, nativeQuery = true)
    int deleteUnusedTags(@Param("createdThreshold") final Date createdThreshold);
}