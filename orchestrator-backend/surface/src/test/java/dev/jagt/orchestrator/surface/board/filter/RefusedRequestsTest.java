package dev.jagt.orchestrator.surface.board.filter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.service.project.ConfigWriteFailed;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class RefusedRequestsTest {

  private final RefusedRequests refusals = new RefusedRequests();
  private final MockHttpServletRequest request =
      new MockHttpServletRequest("POST", "/api/tasks/say");

  @Test
  void saysTheBodyIsNotJsonWithoutEchoingTheParserErrorBack() {
    var answered =
        refusals.malformed(
            new HttpMessageNotReadableException(
                "Unexpected token at offset 12", new MockHttpInputMessage(new byte[0])),
            request);

    assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(answered.getBody()).containsEntry("error", "the request body is not readable JSON");
  }

  @Test
  void answersConflictAndTheReasonWhereTheConfigurationFileIsNotWritable() {
    var answered =
        refusals.notWritten(
            new ConfigWriteFailed(
                ConfigWriteFailed.Reason.NOT_WRITABLE, Path.of("/etc/jagt.yml"), "", null));

    assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(answered.getBody()).containsEntry("code", "NOT_WRITABLE");
  }

  @Test
  void answersServerFailureAndTheReasonWhereTheResultDidNotRead() {
    var answered =
        refusals.notWritten(
            new ConfigWriteFailed(
                ConfigWriteFailed.Reason.UNREADABLE_RESTORED,
                Path.of("/etc/jagt.yml"),
                "bad yaml",
                null));

    assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(answered.getBody()).containsEntry("code", "UNREADABLE_RESTORED");
  }

  @Test
  void keepsTheStatusTheFrameworkChoseForRequestItCouldNotBind() {
    var answered = refusals.malformed(new HttpRequestMethodNotSupportedException("PUT"), request);

    assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(answered.getBody()).containsEntry("error", "Request method 'PUT' is not supported");
  }

  @Test
  void answersBadRequestForParameterOfTheWrongType() throws Exception {
    var answered =
        refusals.malformed(
            new MethodArgumentTypeMismatchException(
                "x",
                Integer.class,
                "n",
                new MethodParameter(String.class.getMethod("valueOf", Object.class), 0),
                new NumberFormatException("x")),
            request);

    assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(answered.getBody()).containsKey("error");
  }
}
