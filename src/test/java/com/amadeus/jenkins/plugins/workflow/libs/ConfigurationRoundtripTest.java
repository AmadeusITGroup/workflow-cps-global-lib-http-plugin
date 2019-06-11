package com.amadeus.jenkins.plugins.workflow.libs;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import java.util.List;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

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
      new HttpRetriever("http://example.com/", credentials.getId())
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
  }
}
