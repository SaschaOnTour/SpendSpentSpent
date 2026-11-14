package com.ftpix.sss.filters;

import static org.junit.jupiter.api.Assertions.*;

import com.ftpix.sss.TestConfig;
import com.ftpix.sss.TestContainerTest;
import com.ftpix.sss.controllers.ApplicationController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Fork only: apps before 2.14.0 keep working with this backend until they are updated. */
@Import(TestConfig.class)
public class LegacyAppBridgeTest extends TestContainerTest {

  @Autowired private VersionFilter versionFilter;
  @Autowired private ApplicationController applicationController;
  @Autowired private BuildProperties buildProperties;

  private record Answer(int status, String backendVersion, Map<String, Object> config) {}

  /** Asks for the config like an app sending the given version. */
  private Answer config(String clientVersion) throws Exception {
    var request = new MockHttpServletRequest("GET", "/config");
    if (clientVersion != null) {
      request.addHeader(VersionFilter.VERSION_HEADER, clientVersion);
    }
    var response = new MockHttpServletResponse();
    var config = new Object[1];
    versionFilter.doFilter(
        request,
        response,
        (req, res) -> {
          RequestContextHolder.setRequestAttributes(
              new ServletRequestAttributes((HttpServletRequest) req));
          try {
            config[0] = applicationController.getConfig();
          } catch (Exception e) {
            throw new RuntimeException(e);
          } finally {
            RequestContextHolder.resetRequestAttributes();
          }
        });
    @SuppressWarnings("unchecked")
    var map = (Map<String, Object>) config[0];
    return new Answer(response.getStatus(), response.getHeader(VersionFilter.VERSION_HEADER), map);
  }

  @Test
  public void appsFrom148OnBefore2140KeepWorkingWithIntegers() throws Exception {
    for (var version : new String[] {"148", "149"}) {
      var answer = config(version);

      assertEquals(200, answer.status(), version);
      // they compare it to their minimum backend version as an integer
      assertEquals("149", answer.backendVersion(), version);
      // their config model has an int there
      assertEquals(149, answer.config().get("backendVersion"), version);
      assertEquals("148", answer.config().get("minAppVersion"), version);
    }
  }

  @Test
  public void olderAppsAreStillToldToUpdate() throws Exception {
    for (var version : new String[] {"147", "100", "abc", "2.14"}) {
      var answer = config(version);

      assertEquals(426, answer.status(), version);
      assertEquals("9999", answer.backendVersion(), version);
    }
  }

  @Test
  public void currentAppsGetTheVersionAsUpstream() throws Exception {
    var answer = config("2.14.0");

    assertEquals(200, answer.status());
    assertEquals(buildProperties.getVersion(), answer.backendVersion());
    assertEquals(buildProperties.getVersion(), answer.config().get("backendVersion"));
    assertEquals(
        ApplicationController.MIN_APP_VERSION.toString(), answer.config().get("minAppVersion"));

    assertEquals(426, config("2.13.9").status());
  }

  @Test
  public void withoutAVersionNothingChanges() throws Exception {
    var answer = config(null);

    assertEquals(200, answer.status());
    assertEquals(buildProperties.getVersion(), answer.backendVersion());
    assertEquals(buildProperties.getVersion(), answer.config().get("backendVersion"));
  }
}
