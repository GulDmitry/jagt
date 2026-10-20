package dev.jagt.orchestrator.surface.board;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

class RefusedRequestsTest {

    private final RefusedRequests refusals = new RefusedRequests();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks/say");

    @Test
    void saysTheBodyIsNotJsonWithoutEchoingTheParserErrorBack() {
        var answered = refusals.malformed(new HttpMessageNotReadableException("Unexpected token at offset 12",
                new MockHttpInputMessage(new byte[0])), request);

        assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(answered.getBody()).containsEntry("error", "the request body is not readable JSON");
    }

    @Test
    void keepsTheStatusTheFrameworkChoseForARequestItCouldNotBind() {
        var answered = refusals.malformed(new HttpRequestMethodNotSupportedException("PUT"), request);

        assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(answered.getBody()).containsEntry("error", "Request method 'PUT' is not supported");
    }

    @Test
    void answersABadRequestForAParameterOfTheWrongType() {
        var answered = refusals.malformed(new MethodArgumentTypeMismatchException("x", Integer.class, "n", null,
                new NumberFormatException("x")), request);

        assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(answered.getBody()).containsKey("error");
    }
}
