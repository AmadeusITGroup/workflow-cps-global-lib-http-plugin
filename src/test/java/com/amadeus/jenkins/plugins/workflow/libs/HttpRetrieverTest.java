package com.amadeus.jenkins.plugins.workflow.libs;

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Objects;

@RunWith(MockitoJUnitRunner.class)
public class HttpRetrieverTest {

    FilePath target;
    private FilePath archive;
    private static final String RSC_FILE = "http-lib-retriever-tests.zip";

    @Mock
    Run run;

    @Mock
    TaskListener listener;

    @Mock
    UsernamePasswordCredentials passwordCredentialsMock;

    @Mock
    Jenkins jenkins;

    @Mock
    FreeStyleProject parent;

    @Mock
    Computer computer;

    @Rule
    public WireMockRule wireMock = new WireMockRule(WireMockConfiguration.options().dynamicPort());

    HttpRetrieverStub retriever;

    @org.junit.Before
    public void setUp() throws Exception {

        target = new FilePath(Files.createTempDirectory("http-lib-retriever-tests").toFile());
        Mockito.when(run.getParent()).thenReturn(parent);
        Mockito.when(jenkins.getWorkspaceFor(parent)).thenReturn(target);
        Mockito.when(listener.getLogger()).thenReturn(System.out);
        Secret pwd = Secret.fromString("{cGFzc3dvcmQ=}");
        Mockito.when(passwordCredentialsMock.getPassword()).thenReturn(pwd);
        Mockito.when(passwordCredentialsMock.getUsername()).thenReturn("USER");

        createRetriever(getUrl(RSC_FILE), RSC_FILE);

        wireMock.stubFor(
          WireMock.any(WireMock.anyUrl())
            .atPriority(1)
            .andMatching(r -> MatchResult.of(!r.getMethod().equals(RequestMethod.HEAD) && !r.containsHeader(HttpHeaders.AUTHORIZATION)))
            .willReturn(WireMock.unauthorized().withHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic")));
    }

    @org.junit.After
    public void tearDown() throws Exception {
        target = null;
        archive = null;
    }

    private void createRetriever(String urlToCall, String relativeUrlToServe) throws IOException {
        createRetriever(urlToCall, relativeUrlToServe, HttpURLConnection.HTTP_OK);
    }

    private void createRetriever(String urlToCall, String relativeUrlToServe, int returnForCheck) throws IOException {
        InputStream archive = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream(RSC_FILE));
        wireMock.stubFor(
                WireMock.head(WireMock.urlMatching(".*" + relativeUrlToServe))
                        .atPriority(2)
                        .willReturn(WireMock.status(returnForCheck))
        );
        wireMock.stubFor(
                WireMock.get(WireMock.urlMatching(".*" + relativeUrlToServe))
                        .withBasicAuth("USER", "")
                        .withBasicAuth(passwordCredentialsMock.getUsername(), passwordCredentialsMock.getPassword().getPlainText())
                        .atPriority(2)
                        .willReturn(WireMock.aResponse().withBody(IOUtils.toByteArray(archive)))

        );
        retriever = new HttpRetrieverStub(urlToCall);
    }

    private String getUrl(String relativeUrlToServe) {
        return "http://localhost:" + wireMock.port() + "/" + relativeUrlToServe;
    }

    @Test
    public void retrieves() throws Exception {
        Assert.assertFalse(target.child("version.txt").exists());
        retriever.retrieve("http-lib-retriever-tests", "1.2.3", target, run, listener);
        Assert.assertTrue(target.child("version.txt").exists());
        Assert.assertTrue(target.child("src").exists());
        Assert.assertTrue(target.child("vars").exists());
        Assert.assertTrue(target.child("resources").exists());
    }

    @Test
    public void acceptsIncorrectVersionsDeclared() throws Exception {
        retriever.retrieve("http-lib-retriever-tests", "2.3.4", target, run, listener);
        Assert.assertTrue(target.child("version.txt").exists());
        Assert.assertTrue(target.child("src").exists());
        Assert.assertTrue(target.child("vars").exists());
        Assert.assertTrue(target.child("resources").exists());
    }

    @Test
    public void acceptsNoVersionsDeclared() throws Exception {
        createRetriever(getUrl("http-lib-retriever-tests-no-version.zip"), "/http-lib-retriever-tests-no-version.zip");
        retriever.retrieve("http-lib-retriever-tests", "1.2.3", target, run, listener);
        Assert.assertTrue(target.child("src").exists());
        Assert.assertTrue(target.child("vars").exists());
        Assert.assertTrue(target.child("resources").exists());
    }

    @Test
    public void replacesVersionInUrl() throws Exception {
        createRetriever(getUrl("http-lib-retriever-test2-${library.http-lib-retriever-test2.version}.zip"), "/http-lib-retriever-test2-1.2.3.zip");
        retriever.retrieve("http-lib-retriever-test2", "1.2.3", target, run, listener);
        Assert.assertTrue(target.child("src").exists());
        Assert.assertTrue(target.child("vars").exists());
        Assert.assertTrue(target.child("resources").exists());
    }

    @Test(expected = IOException.class)
    public void exceptionWhenRetrieveIncorrectUrl() throws Exception {
        Assert.assertFalse(target.child("version.txt").exists());
        createRetriever("does-not-exist.zip", RSC_FILE);
        retriever.retrieve("http-lib-retriever-tests", "1.2.3", target, run, listener);
        Assert.assertFalse(target.child("version.txt").exists());
    }

    @Test
    public void doesNotRetrieveIfUrlNotPassed() throws Exception {
        createRetriever(null, RSC_FILE);
        retriever.retrieve("http-lib-retriever-tests", "1.2.3", target, run, listener);
        Assert.assertFalse(target.child("src").exists());
    }

    @Test(expected = Exception.class)
    public void doesNotRetrieveIfUrlEmpty() throws Exception {
        createRetriever("", RSC_FILE);
        retriever.retrieve("http-lib-retriever-tests", "1.2.3", target, run, listener);
    }

    @Test(expected = IOException.class)
    public void exceptionIfUrNotFound() throws Exception {
        createRetriever(RSC_FILE, RSC_FILE, HttpURLConnection.HTTP_NOT_FOUND);
        retriever.retrieve("http-lib-retriever-tests", "1.2.3", target, run, listener);
        Assert.assertFalse(target.child("src").exists());
    }

    @Test
    public void validatesVersion() {
        FormValidation validation = retriever.validateVersion("library-name", "1.2.3");
        Assert.assertEquals(FormValidation.Kind.OK, validation.kind);
    }

    @Test
    public void validatesVersionReturnsWarningIfNotHttps() {
        retriever.httpsUsed = false;
        FormValidation validation = retriever.validateVersion("library-name", "1.2.3");
        Assert.assertEquals(FormValidation.Kind.WARNING, validation.kind);
    }

    @Test
    public void returnsWarningIfCantValidateVersion() throws IOException {
        createRetriever("http://localhost/does-not-exist.zip", RSC_FILE, HttpURLConnection.HTTP_NOT_FOUND);
        FormValidation validation = retriever.validateVersion("library-name", "1.2.3");
        Assert.assertEquals(FormValidation.Kind.WARNING, validation.kind);
    }

    @Test
    public void returnsWarningIfUnauthorizedWhenValidatesVersion() throws IOException {
        createRetriever("http://localhost/does-not-exist.zip", RSC_FILE, HttpURLConnection.HTTP_UNAUTHORIZED);
        FormValidation validation = retriever.validateVersion("library-name", "1.2.3");
        Assert.assertEquals(FormValidation.Kind.WARNING, validation.kind);
    }


    private class HttpRetrieverStub extends HttpRetriever {

        private boolean httpsUsed = true;

        public HttpRetrieverStub(String url) {
            super(url, "credentialsId", jenkins);
        }

        @Override
        boolean isSecure(URL url) {
            return httpsUsed;
        }

        @Override
        UsernamePasswordCredentials initPasswordCredentials(Run<?, ?> run) {
            return passwordCredentialsMock;
        }

        Computer getSlave() throws IOException {
            return computer;
        }

        WorkspaceList.Lease getWorkspace(FilePath dir, Computer computer) throws InterruptedException {
            return WorkspaceList.Lease.createDummyLease(dir);
        }
    }
}