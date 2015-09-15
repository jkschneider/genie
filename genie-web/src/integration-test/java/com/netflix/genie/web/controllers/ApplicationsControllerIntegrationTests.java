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
package com.netflix.genie.web.controllers;

import com.google.common.collect.Sets;
import com.netflix.genie.common.exceptions.GenieException;
import com.netflix.genie.common.model.Application;
import com.netflix.genie.common.model.ApplicationStatus;
import com.netflix.genie.common.model.Command;
import com.netflix.genie.common.model.CommandStatus;
import com.netflix.genie.core.repositories.jpa.ApplicationRepository;
import com.netflix.genie.core.repositories.jpa.CommandRepository;
import com.netflix.genie.web.configs.GenieConfig;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/**
 * Integration tests for the Applications REST API.
 *
 * @author tgianos
 * @since 3.0.0
 */
//TODO: Add tests for error conditions
@ActiveProfiles({"integration"})
@DirtiesContext
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = GenieConfig.class)
@WebIntegrationTest(randomPort = true)
public class ApplicationsControllerIntegrationTests {

    private static final String ID = UUID.randomUUID().toString();
    private static final String NAME = "spark";
    private static final String USER = "genie";
    private static final String VERSION = "0.15.0";

    // The TestRestTemplate overrides error handler so that errors pass through to user so can validate
    private final RestTemplate restTemplate = new TestRestTemplate();
    private final HttpHeaders headers = new HttpHeaders();

    // Since we're bringing the service up on random port need to figure out what it is
    @Value("${local.server.port}")
    private int port;
    private String appsBaseUrl;
    private String commandsBaseUrl;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private CommandRepository commandRepository;

    /**
     * Setup for tests.
     */
    @Before
    public void setup() {
        this.appsBaseUrl = "http://localhost:" + this.port + "/api/v3/applications";
        this.commandsBaseUrl = "http://localhost:" + this.port + "/api/v3/commands";
        this.headers.setContentType(MediaType.APPLICATION_JSON);
    }

    /**
     * Cleanup after tests.
     */
    @After
    public void cleanup() {
        this.commandRepository.deleteAll();
        this.applicationRepository.deleteAll();
    }

    /**
     * Test creating an application without an ID.
     *
     * @throws GenieException on configuration issue
     */
    @Test
    public void canCreateApplicationWithoutId() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        final ResponseEntity<Application> responseEntity
                = createApplication(null, NAME, USER, VERSION, ApplicationStatus.ACTIVE);

        Assert.assertThat(responseEntity.getStatusCode(), Matchers.is(HttpStatus.CREATED));
        final Application responseApp = responseEntity.getBody();
        Assert.assertThat(responseApp.getId(), Matchers.is(Matchers.notNullValue()));
        Assert.assertThat(responseApp.getName(), Matchers.is(NAME));
        Assert.assertThat(responseApp.getUser(), Matchers.is(USER));
        Assert.assertThat(responseApp.getVersion(), Matchers.is(VERSION));
        Assert.assertThat(responseApp.getTags(), Matchers.hasItem(Matchers.startsWith("genie.id:")));
        Assert.assertThat(responseApp.getTags(), Matchers.hasItem(Matchers.startsWith("genie.name:")));
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(1L));
    }

    /**
     * Test creating an application with an ID.
     *
     * @throws GenieException When issue in creation
     */
    @Test
    public void canCreateApplicationWithId() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        final ResponseEntity<Application> responseEntity
                = createApplication(ID, NAME, USER, VERSION, ApplicationStatus.ACTIVE);

        Assert.assertThat(responseEntity.getStatusCode(), Matchers.is(HttpStatus.CREATED));
        final Application responseApp = responseEntity.getBody();
        Assert.assertThat(responseApp.getId(), Matchers.is(ID));
        Assert.assertThat(responseApp.getName(), Matchers.is(NAME));
        Assert.assertThat(responseApp.getUser(), Matchers.is(USER));
        Assert.assertThat(responseApp.getVersion(), Matchers.is(VERSION));
        Assert.assertThat(responseApp.getTags().size(), Matchers.is(2));
        Assert.assertThat(responseApp.getTags(), Matchers.hasItem(Matchers.startsWith("genie.id:")));
        Assert.assertThat(responseApp.getTags(), Matchers.hasItem(Matchers.startsWith("genie.name:")));
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(1L));
    }

    /**
     * Test to make sure the post API can handle bad input.
     */
    @Test
    public void canHandleBadInputToCreateApplication() {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        final Application app = new Application();
        final HttpEntity<Application> entity = new HttpEntity<>(app, this.headers);
        final ResponseEntity<String> responseEntity
                = this.restTemplate.postForEntity(this.appsBaseUrl, entity, String.class);

        Assert.assertThat(responseEntity.getStatusCode(), Matchers.is(HttpStatus.PRECONDITION_FAILED));
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
    }

    /**
     * Test to make sure that you can retrieve a created application based on the location header.
     *
     * @throws GenieException on configuration error
     */
    @Test
    public void canGetCreatedApplication() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        final ResponseEntity<Application> responseEntity
                = createApplication(null, NAME, USER, VERSION, ApplicationStatus.ACTIVE);

        Assert.assertThat(responseEntity.getStatusCode(), Matchers.is(HttpStatus.CREATED));
        final Application createdApp = responseEntity.getBody();
        final String resourceURI = responseEntity.getHeaders().getLocation().toString();
        final ResponseEntity<Application> getResponse = this.restTemplate.getForEntity(resourceURI, Application.class);

        Assert.assertThat(getResponse.getStatusCode(), Matchers.is(HttpStatus.OK));
        final Application getApp = getResponse.getBody();
        Assert.assertThat(getApp.getId(), Matchers.is(createdApp.getId()));
        Assert.assertThat(getApp.getName(), Matchers.is(createdApp.getName()));
        Assert.assertThat(getApp.getVersion(), Matchers.is(createdApp.getVersion()));
        Assert.assertThat(getApp.getUser(), Matchers.is(createdApp.getUser()));
        Assert.assertThat(getApp.getDescription(), Matchers.is(createdApp.getDescription()));
        Assert.assertThat(getApp.getSetupFile(), Matchers.is(createdApp.getSetupFile()));
        Assert.assertThat(getApp.getConfigs(), Matchers.is(createdApp.getConfigs()));
        Assert.assertThat(getApp.getTags(), Matchers.is(createdApp.getTags()));
        Assert.assertThat(getApp.getDependencies(), Matchers.is(createdApp.getDependencies()));
        Assert.assertThat(getApp.getUpdated(), Matchers.is(createdApp.getUpdated()));
        Assert.assertThat(getApp.getCreated(), Matchers.is(createdApp.getCreated()));
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(1L));
    }

    /**
     * Test to make sure that you can search for applications by various parameters.
     *
     * @throws GenieException on configuration error
     */
    @Test
    public void canFindApplications() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        final String id1 = UUID.randomUUID().toString();
        final String id2 = UUID.randomUUID().toString();
        final String id3 = UUID.randomUUID().toString();
        final String name1 = UUID.randomUUID().toString();
        final String name2 = UUID.randomUUID().toString();
        final String name3 = UUID.randomUUID().toString();
        final String user1 = UUID.randomUUID().toString();
        final String user2 = UUID.randomUUID().toString();
        final String user3 = UUID.randomUUID().toString();
        final String version1 = UUID.randomUUID().toString();
        final String version2 = UUID.randomUUID().toString();
        final String version3 = UUID.randomUUID().toString();

        createApplication(id1, name1, user1, version1, ApplicationStatus.ACTIVE);
        createApplication(id2, name2, user2, version2, ApplicationStatus.DEPRECATED);
        createApplication(id3, name3, user3, version3, ApplicationStatus.INACTIVE);

        // Test finding all applications
        ResponseEntity<Application[]> getResponse
                = this.restTemplate.getForEntity(this.appsBaseUrl, Application[].class);

        Assert.assertThat(getResponse.getStatusCode(), Matchers.is(HttpStatus.OK));
        Application[] apps = getResponse.getBody();
        Assert.assertThat(apps.length, Matchers.is(3));

        // Try to limit the number of results
        URI uri = UriComponentsBuilder.fromHttpUrl(this.appsBaseUrl)
                .queryParam("limit", 2)
                .build()
                .encode()
                .toUri();
        getResponse = this.restTemplate.getForEntity(uri, Application[].class);

        Assert.assertThat(getResponse.getStatusCode(), Matchers.is(HttpStatus.OK));
        apps = getResponse.getBody();
        Assert.assertThat(apps.length, Matchers.is(2));

        // Query by name
        uri = UriComponentsBuilder.fromHttpUrl(this.appsBaseUrl)
                .queryParam("name", name2)
                .build()
                .encode()
                .toUri();
        getResponse = this.restTemplate.getForEntity(uri, Application[].class);
        apps = getResponse.getBody();
        Assert.assertThat(apps.length, Matchers.is(1));
        Assert.assertThat(apps[0].getId(), Matchers.is(id2));

        // Query by user
        uri = UriComponentsBuilder.fromHttpUrl(this.appsBaseUrl)
                .queryParam("userName", user3)
                .build()
                .encode()
                .toUri();
        getResponse = this.restTemplate.getForEntity(uri, Application[].class);
        apps = getResponse.getBody();
        Assert.assertThat(apps.length, Matchers.is(1));
        Assert.assertThat(apps[0].getId(), Matchers.is(id3));

        // Query by statuses
        uri = UriComponentsBuilder.fromHttpUrl(this.appsBaseUrl)
                .queryParam("status", ApplicationStatus.ACTIVE, ApplicationStatus.DEPRECATED)
                .build()
                .encode()
                .toUri();
        getResponse = this.restTemplate.getForEntity(uri, Application[].class);
        apps = getResponse.getBody();
        Assert.assertThat(apps.length, Matchers.is(2));
        Arrays.asList(apps).stream().forEach(
                application -> {
                    if (!application.getId().equals(id1) && !application.getId().equals(id2)) {
                        Assert.fail();
                    }
                }
        );

        // Query by tags
        uri = UriComponentsBuilder.fromHttpUrl(this.appsBaseUrl)
                .queryParam("tag", "genie.id:" + id1)
                .build()
                .encode()
                .toUri();
        getResponse = this.restTemplate.getForEntity(uri, Application[].class);
        apps = getResponse.getBody();
        Assert.assertThat(apps.length, Matchers.is(1));
        Assert.assertThat(apps[0].getId(), Matchers.is(id1));

        //TODO: Add tests for sort, orderBy etc

        Assert.assertThat(this.applicationRepository.count(), Matchers.is(3L));
    }

    /**
     * Test to make sure that an application can be updated.
     *
     * @throws GenieException on configuration errorsx
     */
    @Test
    public void canUpdateApplication() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        final ResponseEntity<Application> createResponse
                = createApplication(ID, NAME, USER, VERSION, ApplicationStatus.ACTIVE);

        Assert.assertThat(createResponse.getStatusCode(), Matchers.is(HttpStatus.CREATED));
        final Application createdApp = createResponse.getBody();
        Assert.assertThat(createdApp.getStatus(), Matchers.is(ApplicationStatus.ACTIVE));

        createdApp.setStatus(ApplicationStatus.INACTIVE);
        final HttpEntity<Application> entity = new HttpEntity<>(createdApp, this.headers);
        final ResponseEntity<Application> updateResponse = this.restTemplate
                .exchange(
                        this.appsBaseUrl + "/" + ID,
                        HttpMethod.PUT,
                        entity,
                        Application.class
                );

        Assert.assertThat(updateResponse.getStatusCode(), Matchers.is(HttpStatus.OK));
        final Application updateApp = updateResponse.getBody();
        Assert.assertThat(updateApp.getStatus(), Matchers.is(ApplicationStatus.INACTIVE));
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(1L));
    }

    /**
     * Make sure can successfully delete all applications.
     *
     * @throws GenieException on a configuration error
     */
    @Test
    public void canDeleteAllApplications() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        createApplication(null, NAME, USER, VERSION, ApplicationStatus.ACTIVE);
        createApplication(null, NAME, USER, VERSION, ApplicationStatus.DEPRECATED);
        createApplication(null, NAME, USER, VERSION, ApplicationStatus.INACTIVE);
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(3L));

        this.restTemplate.delete(this.appsBaseUrl);

        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
    }

    /**
     * Test to make sure that you can delete an application.
     *
     * @throws GenieException on configuration error
     */
    @Test
    public void canDeleteAnApplication() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        final String id1 = UUID.randomUUID().toString();
        final String id2 = UUID.randomUUID().toString();
        final String id3 = UUID.randomUUID().toString();
        final String name1 = UUID.randomUUID().toString();
        final String name2 = UUID.randomUUID().toString();
        final String name3 = UUID.randomUUID().toString();
        final String user1 = UUID.randomUUID().toString();
        final String user2 = UUID.randomUUID().toString();
        final String user3 = UUID.randomUUID().toString();
        final String version1 = UUID.randomUUID().toString();
        final String version2 = UUID.randomUUID().toString();
        final String version3 = UUID.randomUUID().toString();

        createApplication(id1, name1, user1, version1, ApplicationStatus.ACTIVE);
        createApplication(id2, name2, user2, version2, ApplicationStatus.DEPRECATED);
        createApplication(id3, name3, user3, version3, ApplicationStatus.INACTIVE);
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(3L));

        this.restTemplate.delete(this.appsBaseUrl + "/" + id2);

        final ResponseEntity<Application[]> getResponse
                = this.restTemplate.getForEntity(this.appsBaseUrl, Application[].class);

        Assert.assertThat(getResponse.getStatusCode(), Matchers.is(HttpStatus.OK));
        final Application[] apps = getResponse.getBody();
        Assert.assertThat(apps.length, Matchers.is(2));
        Arrays.asList(apps).stream().forEach(
                application -> {
                    if (!application.getId().equals(id1) && !application.getId().equals(id3)) {
                        Assert.fail();
                    }
                }
        );
    }

    /**
     * Test to make sure we can add configurations to the application after it is created.
     *
     * @throws GenieException on configuration problems
     */
    @Test
    public void canAddConfigsToApplication() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        createApplication(ID, NAME, USER, VERSION, ApplicationStatus.ACTIVE);

        ResponseEntity<String[]> configResponse = this.restTemplate.getForEntity(
                this.appsBaseUrl + "/" + ID + "/configs",
                String[].class
        );
        Assert.assertThat(configResponse.getBody().length, Matchers.is(0));

        final String config1 = UUID.randomUUID().toString();
        final String config2 = UUID.randomUUID().toString();
        final Set<String> configs = Sets.newHashSet(config1, config2);
        final HttpEntity<Set<String>> entity = new HttpEntity<>(configs, this.headers);
        configResponse = this.restTemplate.postForEntity(
                this.appsBaseUrl + "/" + ID + "/configs",
                entity,
                String[].class
        );

        Assert.assertThat(configResponse.getBody().length, Matchers.is(2));
        Assert.assertTrue(Arrays.asList(configResponse.getBody()).contains(config1));
        Assert.assertTrue(Arrays.asList(configResponse.getBody()).contains(config2));
    }

    /**
     * Test to make sure we can update the configurations for an application after it is created.
     *
     * @throws GenieException on configuration problems
     */
    @Test
    public void canUpdateConfigsForApplication() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        createApplication(ID, NAME, USER, VERSION, ApplicationStatus.ACTIVE);

        final String config1 = UUID.randomUUID().toString();
        final String config2 = UUID.randomUUID().toString();
        HttpEntity<Set<String>> entity = new HttpEntity<>(Sets.newHashSet(config1, config2), this.headers);
        this.restTemplate.postForEntity(
                this.appsBaseUrl + "/" + ID + "/configs",
                entity,
                String[].class
        );

        final String config3 = UUID.randomUUID().toString();
        entity = new HttpEntity<>(Sets.newHashSet(config3), this.headers);
        final ResponseEntity<String[]> configResponse = this.restTemplate.exchange(
                this.appsBaseUrl + "/" + ID + "/configs",
                HttpMethod.PUT,
                entity,
                String[].class
        );

        Assert.assertThat(configResponse.getBody().length, Matchers.is(1));
        Assert.assertThat(configResponse.getBody()[0], Matchers.is(config3));
    }

    /**
     * Test to make sure we can delete the configurations for an application after it is created.
     *
     * @throws GenieException on configuration problems
     */
    @Test
    public void canDeleteConfigsForApplication() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        createApplication(ID, NAME, USER, VERSION, ApplicationStatus.ACTIVE);

        final String config1 = UUID.randomUUID().toString();
        final String config2 = UUID.randomUUID().toString();
        final HttpEntity<Set<String>> entity = new HttpEntity<>(Sets.newHashSet(config1, config2), this.headers);
        this.restTemplate.postForEntity(
                this.appsBaseUrl + "/" + ID + "/configs",
                entity,
                String[].class
        );

        this.restTemplate.exchange(
                this.appsBaseUrl + "/" + ID + "/configs",
                HttpMethod.DELETE,
                null,
                String[].class
        );

        final ResponseEntity<String[]> configResponse = this.restTemplate.getForEntity(
                this.appsBaseUrl + "/" + ID + "/configs",
                String[].class
        );
        Assert.assertThat(configResponse.getBody().length, Matchers.is(0));
    }

    /**
     * Test to make sure we can add jars to the application after it is created.
     *
     * @throws GenieException on configuration problems
     */
    @Test
    public void canAddJarsToApplication() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        createApplication(ID, NAME, USER, VERSION, ApplicationStatus.ACTIVE);

        ResponseEntity<String[]> jarResponse = this.restTemplate.getForEntity(
                this.appsBaseUrl + "/" + ID + "/jars",
                String[].class
        );
        Assert.assertThat(jarResponse.getBody().length, Matchers.is(0));

        final String jar1 = UUID.randomUUID().toString();
        final String jar2 = UUID.randomUUID().toString();
        final Set<String> jars = Sets.newHashSet(jar1, jar2);
        final HttpEntity<Set<String>> entity = new HttpEntity<>(jars, this.headers);
        jarResponse = this.restTemplate.postForEntity(
                this.appsBaseUrl + "/" + ID + "/jars",
                entity,
                String[].class
        );

        Assert.assertThat(jarResponse.getBody().length, Matchers.is(2));
        Assert.assertTrue(Arrays.asList(jarResponse.getBody()).contains(jar1));
        Assert.assertTrue(Arrays.asList(jarResponse.getBody()).contains(jar2));
    }

    /**
     * Test to make sure we can update the jars for an application after it is created.
     *
     * @throws GenieException on configuration problems
     */
    @Test
    public void canUpdateJarsForApplication() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        createApplication(ID, NAME, USER, VERSION, ApplicationStatus.ACTIVE);

        final String jar1 = UUID.randomUUID().toString();
        final String jar2 = UUID.randomUUID().toString();
        HttpEntity<Set<String>> entity = new HttpEntity<>(Sets.newHashSet(jar1, jar2), this.headers);
        this.restTemplate.postForEntity(
                this.appsBaseUrl + "/" + ID + "/jars",
                entity,
                String[].class
        );

        final String jar3 = UUID.randomUUID().toString();
        entity = new HttpEntity<>(Sets.newHashSet(jar3), this.headers);
        final ResponseEntity<String[]> jarResponse = this.restTemplate.exchange(
                this.appsBaseUrl + "/" + ID + "/jars",
                HttpMethod.PUT,
                entity,
                String[].class
        );

        Assert.assertThat(jarResponse.getBody().length, Matchers.is(1));
        Assert.assertThat(jarResponse.getBody()[0], Matchers.is(jar3));
    }

    /**
     * Test to make sure we can delete the jars for an application after it is created.
     *
     * @throws GenieException on configuration problems
     */
    @Test
    public void canDeleteJarsForApplication() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        createApplication(ID, NAME, USER, VERSION, ApplicationStatus.ACTIVE);

        final String jar1 = UUID.randomUUID().toString();
        final String jar2 = UUID.randomUUID().toString();
        final HttpEntity<Set<String>> entity = new HttpEntity<>(Sets.newHashSet(jar1, jar2), this.headers);
        this.restTemplate.postForEntity(
                this.appsBaseUrl + "/" + ID + "/jars",
                entity,
                String[].class
        );

        this.restTemplate.exchange(
                this.appsBaseUrl + "/" + ID + "/jars",
                HttpMethod.DELETE,
                null,
                String[].class
        );

        final ResponseEntity<String[]> jarResponse = this.restTemplate.getForEntity(
                this.appsBaseUrl + "/" + ID + "/jars",
                String[].class
        );
        Assert.assertThat(jarResponse.getBody().length, Matchers.is(0));
    }

    /**
     * Test to make sure we can add tags to the application after it is created.
     *
     * @throws GenieException on configuration problems
     */
    @Test
    public void canAddTagsToApplication() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        createApplication(ID, NAME, USER, VERSION, ApplicationStatus.ACTIVE);

        ResponseEntity<String[]> tagResponse = this.restTemplate.getForEntity(
                this.appsBaseUrl + "/" + ID + "/tags",
                String[].class
        );
        Assert.assertThat(tagResponse.getBody().length, Matchers.is(2));

        final String tag1 = UUID.randomUUID().toString();
        final String tag2 = UUID.randomUUID().toString();
        final Set<String> tags = Sets.newHashSet(tag1, tag2);
        final HttpEntity<Set<String>> entity = new HttpEntity<>(tags, this.headers);
        tagResponse = this.restTemplate.postForEntity(
                this.appsBaseUrl + "/" + ID + "/tags",
                entity,
                String[].class
        );

        Assert.assertThat(tagResponse.getBody().length, Matchers.is(4));
        Assert.assertTrue(Arrays.asList(tagResponse.getBody()).contains("genie.id:" + ID));
        Assert.assertTrue(Arrays.asList(tagResponse.getBody()).contains("genie.name:" + NAME));
        Assert.assertTrue(Arrays.asList(tagResponse.getBody()).contains(tag1));
        Assert.assertTrue(Arrays.asList(tagResponse.getBody()).contains(tag2));
    }

    /**
     * Test to make sure we can update the tags for an application after it is created.
     *
     * @throws GenieException on configuration problems
     */
    @Test
    public void canUpdateTagsForApplication() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        createApplication(ID, NAME, USER, VERSION, ApplicationStatus.ACTIVE);

        final String tag1 = UUID.randomUUID().toString();
        final String tag2 = UUID.randomUUID().toString();
        HttpEntity<Set<String>> entity = new HttpEntity<>(Sets.newHashSet(tag1, tag2), this.headers);
        this.restTemplate.postForEntity(
                this.appsBaseUrl + "/" + ID + "/tags",
                entity,
                String[].class
        );

        final String tag3 = UUID.randomUUID().toString();
        entity = new HttpEntity<>(Sets.newHashSet(tag3), this.headers);
        final ResponseEntity<String[]> tagResponse = this.restTemplate.exchange(
                this.appsBaseUrl + "/" + ID + "/tags",
                HttpMethod.PUT,
                entity,
                String[].class
        );

        Assert.assertThat(tagResponse.getBody().length, Matchers.is(3));
        Assert.assertTrue(Arrays.asList(tagResponse.getBody()).contains("genie.id:" + ID));
        Assert.assertTrue(Arrays.asList(tagResponse.getBody()).contains("genie.name:" + NAME));
        Assert.assertTrue(Arrays.asList(tagResponse.getBody()).contains(tag3));
    }

    /**
     * Test to make sure we can delete the tags for an application after it is created.
     *
     * @throws GenieException on configuration problems
     */
    @Test
    public void canDeleteTagsForApplication() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        createApplication(ID, NAME, USER, VERSION, ApplicationStatus.ACTIVE);

        final String tag1 = UUID.randomUUID().toString();
        final String tag2 = UUID.randomUUID().toString();
        final HttpEntity<Set<String>> entity = new HttpEntity<>(Sets.newHashSet(tag1, tag2), this.headers);
        this.restTemplate.postForEntity(
                this.appsBaseUrl + "/" + ID + "/tags",
                entity,
                String[].class
        );

        this.restTemplate.exchange(
                this.appsBaseUrl + "/" + ID + "/tags",
                HttpMethod.DELETE,
                null,
                String[].class
        );

        final ResponseEntity<String[]> tagResponse = this.restTemplate.getForEntity(
                this.appsBaseUrl + "/" + ID + "/tags",
                String[].class
        );
        Assert.assertThat(tagResponse.getBody().length, Matchers.is(2));
        Assert.assertTrue(Arrays.asList(tagResponse.getBody()).contains("genie.id:" + ID));
        Assert.assertTrue(Arrays.asList(tagResponse.getBody()).contains("genie.name:" + NAME));
    }

    /**
     * Test to make sure we can delete a tag for an application after it is created.
     *
     * @throws GenieException on configuration problems
     */
    @Test
    public void canDeleteTagForApplication() throws GenieException {
        Assert.assertThat(this.applicationRepository.count(), Matchers.is(0L));
        createApplication(ID, NAME, USER, VERSION, ApplicationStatus.ACTIVE);

        final String tag1 = UUID.randomUUID().toString();
        final String tag2 = UUID.randomUUID().toString();
        final HttpEntity<Set<String>> entity = new HttpEntity<>(Sets.newHashSet(tag1, tag2), this.headers);
        this.restTemplate.postForEntity(
                this.appsBaseUrl + "/" + ID + "/tags",
                entity,
                String[].class
        );

        this.restTemplate.exchange(
                this.appsBaseUrl + "/" + ID + "/tags/" + tag1,
                HttpMethod.DELETE,
                null,
                String[].class
        );

        final ResponseEntity<String[]> tagResponse = this.restTemplate.getForEntity(
                this.appsBaseUrl + "/" + ID + "/tags",
                String[].class
        );
        Assert.assertThat(tagResponse.getBody().length, Matchers.is(3));
        Assert.assertTrue(Arrays.asList(tagResponse.getBody()).contains("genie.id:" + ID));
        Assert.assertTrue(Arrays.asList(tagResponse.getBody()).contains("genie.name:" + NAME));
        Assert.assertTrue(Arrays.asList(tagResponse.getBody()).contains(tag2));
    }

    /**
     * Make sure can get all the commands which use a given application.
     *
     * @throws GenieException on configuration error
     */
    @Test
    public void canGetCommandsForApplication() throws GenieException {
        createApplication(ID, NAME, USER, VERSION, ApplicationStatus.ACTIVE);
        final String placeholder = UUID.randomUUID().toString();
        final String command1Id = UUID.randomUUID().toString();
        final String command2Id = UUID.randomUUID().toString();
        final String command3Id = UUID.randomUUID().toString();
        createCommand(command1Id, placeholder, placeholder, placeholder, CommandStatus.ACTIVE, placeholder);
        createCommand(command2Id, placeholder, placeholder, placeholder, CommandStatus.ACTIVE, placeholder);
        createCommand(command3Id, placeholder, placeholder, placeholder, CommandStatus.ACTIVE, placeholder);

        final Set<String> appIds = Sets.newHashSet(ID);
        final HttpEntity<Set<String>> entity = new HttpEntity<>(appIds, this.headers);
        this.restTemplate.postForEntity(
                this.commandsBaseUrl + "/" + command1Id + "/applications",
                entity,
                Application[].class
        );
        this.restTemplate.postForEntity(
                this.commandsBaseUrl + "/" + command3Id + "/applications",
                entity,
                Application[].class
        );

        final ResponseEntity<Command[]> responseEntity = this.restTemplate.getForEntity(
                this.appsBaseUrl + "/" + ID + "/commands",
                Command[].class
        );

        Assert.assertThat(responseEntity.getStatusCode(), Matchers.is(HttpStatus.OK));
        Assert.assertThat(responseEntity.getBody().length, Matchers.is(2));
        Arrays.asList(responseEntity.getBody()).stream().forEach(
                command -> {
                    if (!command.getId().equals(command1Id) && !command.getId().equals(command3Id)) {
                        Assert.fail();
                    }
                }
        );
    }

    /**
     * Helper for creating an application used in testing.
     *
     * @param id      The id to use for the application or null/empty/blank for one to be assigned
     * @param name    The name to use for the application
     * @param user    The user to use for the application
     * @param version The version to use for the application
     * @param status  The status to use for the application
     * @throws GenieException for any misconfiguration
     */
    private ResponseEntity<Application> createApplication(
            final String id,
            final String name,
            final String user,
            final String version,
            final ApplicationStatus status
    ) throws GenieException {
        final Application app = new Application(name, user, version, status);
        if (StringUtils.isNotBlank(id)) {
            app.setId(id);
        }
        final HttpEntity<Application> entity = new HttpEntity<>(app, this.headers);
        return this.restTemplate.postForEntity(this.appsBaseUrl, entity, Application.class);
    }

    /**
     * Helper for creating a command used in testing.
     *
     * @param id         The id to use for the command or null/empty/blank for one to be assigned
     * @param name       The name to use for the command
     * @param user       The user to use for the command
     * @param version    The version to use for the command
     * @param status     The status to use for the command
     * @param executable The executable to use for the command
     * @throws GenieException for any misconfiguration
     */
    private ResponseEntity<Command> createCommand(
            final String id,
            final String name,
            final String user,
            final String version,
            final CommandStatus status,
            final String executable
    ) throws GenieException {
        final Command command = new Command(name, user, version, status, executable);
        if (StringUtils.isNotBlank(id)) {
            command.setId(id);
        }
        final HttpEntity<Command> entity = new HttpEntity<>(command, this.headers);
        return this.restTemplate.postForEntity(this.commandsBaseUrl, entity, Command.class);
    }
}
