package com.amadeus.jenkins.plugins.workflow.libs;

import hudson.ExtensionList;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.yaml.YamlSource;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.LibraryRetriever;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationAsCodeTest {
  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Test
  public void testConfigurationAsCode() throws Exception {
    GlobalLibraries globalLibraries = loadConfiguration("simpleConfiguration.yaml");
    List<LibraryConfiguration> libraries = globalLibraries.getLibraries();
    assertThat(libraries).hasSize(1);
    LibraryConfiguration testLibrary = libraries.get(0);
    assertThat(testLibrary.getName()).isEqualTo("awesome-lib");
    LibraryRetriever retriever = testLibrary.getRetriever();
    assertThat(retriever).isInstanceOf(HttpRetriever.class);
    HttpRetriever httpRetriever = (HttpRetriever) retriever;
    assertThat(httpRetriever.getHttpURL()).isEqualTo("http://example.org/123");
    assertThat(httpRetriever.getCredentialsId()).isEqualTo("someCredentials");
  }

  private GlobalLibraries loadConfiguration(String name) throws ConfiguratorException {
    ConfigurationAsCode.get().configureWith(new YamlSource<>(ClassLoader.getSystemResourceAsStream(name)));
    return ExtensionList.lookupSingleton(GlobalLibraries.class);
  }
}
