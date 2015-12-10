/*
 *
 *  Copyright 2015 Netflix, Inc.
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
package com.netflix.genie.common.dto;

import com.google.common.collect.Sets;
import com.netflix.genie.test.categories.UnitTest;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * Tests for the Command DTO.
 *
 * @author tgianos
 * @since 3.0.0
 */
@Category(UnitTest.class)
public class CommandUnitTests {

    private static final String NAME = UUID.randomUUID().toString();
    private static final String USER = UUID.randomUUID().toString();
    private static final String VERSION = UUID.randomUUID().toString();
    private static final String EXECUTABLE = UUID.randomUUID().toString();

    /**
     * Test to make sure we can build a command using the default builder constructor.
     */
    @Test
    public void canBuildCommand() {
        final Command command = new Command.Builder(NAME, USER, VERSION, CommandStatus.ACTIVE, EXECUTABLE).build();
        Assert.assertThat(command.getName(), Matchers.is(NAME));
        Assert.assertThat(command.getUser(), Matchers.is(USER));
        Assert.assertThat(command.getVersion(), Matchers.is(VERSION));
        Assert.assertThat(command.getStatus(), Matchers.is(CommandStatus.ACTIVE));
        Assert.assertThat(command.getExecutable(), Matchers.is(EXECUTABLE));
        Assert.assertThat(command.getSetupFile(), Matchers.nullValue());
        Assert.assertThat(command.getConfigs(), Matchers.empty());
        Assert.assertThat(command.getCreated(), Matchers.nullValue());
        Assert.assertThat(command.getDescription(), Matchers.nullValue());
        Assert.assertThat(command.getId(), Matchers.nullValue());
        Assert.assertThat(command.getTags(), Matchers.empty());
        Assert.assertThat(command.getUpdated(), Matchers.nullValue());
    }

    /**
     * Test to make sure we can build a command with all optional parameters.
     */
    @Test
    public void canBuildCommandWithOptionals() {
        final Command.Builder builder = new Command.Builder(NAME, USER, VERSION, CommandStatus.ACTIVE, EXECUTABLE);

        final String setupFile = UUID.randomUUID().toString();
        builder.withSetupFile(setupFile);

        final Set<String> configs = Sets.newHashSet(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        builder.withConfigs(configs);

        final Date created = new Date();
        builder.withCreated(created);

        final String description = UUID.randomUUID().toString();
        builder.withDescription(description);

        final String id = UUID.randomUUID().toString();
        builder.withId(id);

        final Set<String> tags = Sets.newHashSet(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        builder.withTags(tags);

        final Date updated = new Date();
        builder.withUpdated(updated);

        final Command command = builder.build();
        Assert.assertThat(command.getName(), Matchers.is(NAME));
        Assert.assertThat(command.getUser(), Matchers.is(USER));
        Assert.assertThat(command.getVersion(), Matchers.is(VERSION));
        Assert.assertThat(command.getStatus(), Matchers.is(CommandStatus.ACTIVE));
        Assert.assertThat(command.getExecutable(), Matchers.is(EXECUTABLE));
        Assert.assertThat(command.getSetupFile(), Matchers.is(setupFile));
        Assert.assertThat(command.getConfigs(), Matchers.is(configs));
        Assert.assertThat(command.getCreated(), Matchers.is(created));
        Assert.assertThat(command.getDescription(), Matchers.is(description));
        Assert.assertThat(command.getId(), Matchers.is(id));
        Assert.assertThat(command.getTags(), Matchers.is(tags));
        Assert.assertThat(command.getUpdated(), Matchers.is(updated));
    }

    /**
     * Test to make sure we can build a command with null collection parameters.
     */
    @Test
    public void canBuildCommandNullOptionals() {
        final Command.Builder builder = new Command.Builder(NAME, USER, VERSION, CommandStatus.ACTIVE, EXECUTABLE);
        builder.withSetupFile(null);
        builder.withConfigs(null);
        builder.withCreated(null);
        builder.withDescription(null);
        builder.withId(null);
        builder.withTags(null);
        builder.withUpdated(null);

        final Command command = builder.build();
        Assert.assertThat(command.getName(), Matchers.is(NAME));
        Assert.assertThat(command.getUser(), Matchers.is(USER));
        Assert.assertThat(command.getVersion(), Matchers.is(VERSION));
        Assert.assertThat(command.getStatus(), Matchers.is(CommandStatus.ACTIVE));
        Assert.assertThat(command.getExecutable(), Matchers.is(EXECUTABLE));
        Assert.assertThat(command.getSetupFile(), Matchers.nullValue());
        Assert.assertThat(command.getConfigs(), Matchers.empty());
        Assert.assertThat(command.getCreated(), Matchers.nullValue());
        Assert.assertThat(command.getDescription(), Matchers.nullValue());
        Assert.assertThat(command.getId(), Matchers.nullValue());
        Assert.assertThat(command.getTags(), Matchers.empty());
        Assert.assertThat(command.getUpdated(), Matchers.nullValue());
    }
}
