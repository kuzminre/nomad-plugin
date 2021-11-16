package org.jenkinsci.plugins.nomad;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import java.util.UUID;

import org.jenkinsci.plugins.nomad.Api.JobInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

import hudson.util.FormValidation;

/**
 * Checks that the NomadApi is working as expected.
 */
@RunWith(MockitoJUnitRunner.class)
public class NomadApiTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(0);

    @Mock
    NomadWorkerTemplate template;

    @Mock
    NomadCloud cloud;

    @InjectMocks
    NomadApi api;

    @Test
    public void testCheckConnection() {
        // GIVEN
        stubFor(get(urlEqualTo("/v1/agent/self"))
                .willReturn(ok("{}")));
        when(cloud.getNomadUrl()).thenReturn(wireMockRule.baseUrl());

        // WHEN
        FormValidation response = api.checkConnection();

        // THEN
        assertThat(response.kind, is(FormValidation.Kind.OK));
        assertThat(response.getMessage(), is("Nomad API request succeeded."));
    }

    @Test
    public void testStartWorker() {
        // GIVEN
        stubFor(put(urlEqualTo("/v1/jobs"))
                .willReturn(ok()));
        when(cloud.getNomadUrl()).thenReturn(wireMockRule.baseUrl());
        when(template.getJobTemplate()).thenReturn("{}");
        when(template.getRemoteFs()).thenReturn("");

        // WHEN
        String workerName = UUID.randomUUID().toString();
        String secret = UUID.randomUUID().toString();
        api.startWorker(workerName, secret, template);

        // THEN
        verify(putRequestedFor(urlEqualTo("/v1/jobs"))
                .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
        );
    }

    @Test
    public void testStopWorker() {
        // GIVEN
        String workerName = UUID.randomUUID().toString();
        stubFor(delete(urlEqualTo("/v1/job/" + workerName))
                .willReturn(ok()));
        when(cloud.getNomadUrl()).thenReturn(wireMockRule.baseUrl());

        // WHEN
        api.stopWorker(workerName);

        // THEN
        verify(deleteRequestedFor(urlEqualTo("/v1/job/" + workerName)));
    }

    @Test
    public void testValidateTemplateJSON() {
        // GIVEN
        stubFor(post(urlMatching("/v1/job/([a-f0-9-]*)/plan"))
                .willReturn(ok()));
        when(cloud.getNomadUrl()).thenReturn(wireMockRule.baseUrl());
        when(template.getJobTemplate()).thenReturn("{}");
        when(template.getRemoteFs()).thenReturn("");

        // WHEN
        FormValidation response = api.validateTemplate(template);

        // THEN
        assertThat(response.kind, is(FormValidation.Kind.OK));
        assertThat(response.getMessage(), is("OK"));
    }

    @Test
    public void testValidateTemplateHCL() {
        // GIVEN
        stubFor(post(urlMatching("/v1/jobs/parse"))
                .willReturn(ok("{}")));
        stubFor(post(urlMatching("/v1/job/([a-f0-9-]*)/plan"))
                .willReturn(ok()));
        when(cloud.getNomadUrl()).thenReturn(wireMockRule.baseUrl());
        when(template.getJobTemplate()).thenReturn("job \"example\" { type = \"service\" group \"cache\" {} }");
        when(template.getRemoteFs()).thenReturn("");

        // WHEN
        FormValidation response = api.validateTemplate(template);

        // THEN
        assertThat(response.kind, is(FormValidation.Kind.OK));
        assertThat(response.getMessage(), is("OK"));
        verify(postRequestedFor(urlEqualTo("/v1/jobs/parse")));
    }

    @Test
    public void testGetJobs() {
        // GIVEN
        stubFor(get(urlMatching("/v1/jobs\\?prefix=(.*)"))
                .willReturn(ok("[{\"ID\":\"jenkins-A\",\"Name\":\"jenkins-A\",\"Priority\":50,\"Status\":\"pending\"}]")));
        when(cloud.getNomadUrl()).thenReturn(wireMockRule.baseUrl());

        // WHEN
        JobInfo[] jobs = api.getRunningWorkers("jenkins");

        // THEN
        assertThat(jobs.length, is(1));
        assertThat(jobs[0].getID(), is("jenkins-A"));
        assertThat(jobs[0].getName(), is("jenkins-A"));
        assertThat(jobs[0].getStatus(), is("pending"));
        assertThat(jobs[0].getPriority(), is(50));
    }

    @Test
    public void testGetJobsIsEmpty() {
        // GIVEN
        stubFor(get(urlMatching("/v1/jobs\\?prefix=(.*)"))
                .willReturn(ok("[]")));
        when(cloud.getNomadUrl()).thenReturn(wireMockRule.baseUrl());

        // WHEN
        JobInfo[] jobs = api.getRunningWorkers("jenkins");

        // THEN
        assertThat(jobs.length, is(0));
    }

    @Test
    public void testACLTokenIsPresent() {
        // GIVEN
        stubFor(get(urlEqualTo("/v1/agent/self"))
                .willReturn(ok("{}")));
        when(cloud.getNomadUrl()).thenReturn(wireMockRule.baseUrl());
        when(cloud.getNomadACL()).thenReturn("my-acl-token");

        // WHEN
        api.checkConnection();

        // THEN
        verify(getRequestedFor(urlEqualTo("/v1/agent/self"))
                .withHeader("X-Nomad-Token", equalTo("my-acl-token")));
    }
}