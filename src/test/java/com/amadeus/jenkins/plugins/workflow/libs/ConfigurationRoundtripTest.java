package com.amadeus.jenkins.plugins.workflow.libs;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.xmlunit.assertj.XmlAssert;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationRoundtripTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private UsernamePasswordCredentialsImpl credentials;
    private GlobalLibraries globalLibraries;

    @Before
    public void setUp() {
        credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "someCredentials", null, "username", "password");
        ExtensionList.lookupSingleton(SystemCredentialsProvider.class).getCredentials().add(credentials);
        globalLibraries = ExtensionList.lookupSingleton(GlobalLibraries.class);
    }

    @Test
    public void testConfigurationRoundtrip() throws Exception {
        assertThat(globalLibraries.getLibraries()).isEmpty();
        LibraryConfiguration originalTestLib = new LibraryConfiguration(
                "foo",
                new HttpRetriever("http://example.com/", credentials.getId(), true)
        );
        globalLibraries.getLibraries().add(originalTestLib);
        j.configRoundtrip();

        List<LibraryConfiguration> libs = globalLibraries.getLibraries();
        assertThat(libs).hasSize(1);
        LibraryConfiguration testLib = globalLibraries.getLibraries().get(0);
        assertThat(testLib).isNotSameAs(originalTestLib);
        assertThat(testLib.getName()).isEqualTo("foo");
        assertThat(testLib.getRetriever()).isExactlyInstanceOf(HttpRetriever.class);
        HttpRetriever retriever = (HttpRetriever) testLib.getRetriever();
        assertThat(retriever.getHttpURL()).isEqualTo("http://example.com/");
        assertThat(retriever.getCredentialsId())
                .isNotNull()
                .isEqualTo(credentials.getId());
        assertThat(retriever.isPreemptiveAuth()).isTrue();
    }

    @Test
    @LocalData
    public void testFormatDidNotChange() throws Exception {
        String previousConfig = getConfig();
        assertThat(previousConfig).isNotNull();
        assertThat(previousConfig).isNotEmpty();

        LibraryConfiguration lib = new LibraryConfiguration(
                "foo",
                new HttpRetriever("http://example.com/", credentials.getId(), true)
        );
        globalLibraries.getLibraries().clear();
        globalLibraries.getLibraries().add(lib);
        j.configRoundtrip();
        String currentConfig = getConfig();

        XmlAssert.assertThat(previousConfig).and(currentConfig).ignoreChildNodesOrder().areSimilar();
    }

    private String getConfig() throws IOException {
        String previousConfig = null;
        assertThat(globalLibraries.getLibraries()).isNotEmpty();
        File configFile = new File(j.jenkins.getRootDir(), globalLibraries.getId() + ".xml");
        assertThat(configFile).exists();
        try (FileInputStream input = new FileInputStream(configFile)) {
            previousConfig = IOUtils.toString(input);
        }
        return previousConfig;
    }


    @Test
    @LocalData
    public void testConfigurationRoundtrip123() throws Exception {
        assertThat(globalLibraries.getLibraries()).isNotEmpty();
        List<LibraryConfiguration> libs = globalLibraries.getLibraries();
        assertThat(libs).hasSize(1);
        LibraryConfiguration testLib = globalLibraries.getLibraries().get(0);
        assertThat(testLib.getName()).isEqualTo("foo");
        assertThat(testLib.getRetriever()).isExactlyInstanceOf(HttpRetriever.class);
        HttpRetriever retriever = (HttpRetriever) testLib.getRetriever();
        assertThat(retriever.getHttpURL()).isEqualTo("http://example.com/");
        assertThat(retriever.getCredentialsId())
                .isNotNull()
                .isEqualTo(credentials.getId());
        assertThat(retriever.isPreemptiveAuth()).isFalse();
    }
}
