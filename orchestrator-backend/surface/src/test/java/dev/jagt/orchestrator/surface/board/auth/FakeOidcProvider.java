package dev.jagt.orchestrator.surface.board.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class FakeOidcProvider {

  private final HttpServer server;
  private final RSAKey key;
  private final String clientId;
  private final Map<String, String> nonceByCode = new ConcurrentHashMap<>();

  FakeOidcProvider(String clientId) {
    this.clientId = clientId;
    try {
      this.key = new RSAKeyGenerator(2048).keyID("fake").generate();
      this.server =
          HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    } catch (IOException | JOSEException e) {
      throw new IllegalStateException(e);
    }
    server.createContext("/.well-known/openid-configuration", this::discovery);
    server.createContext(
        "/jwks", exchange -> reply(exchange, new JWKSet(key.toPublicJWK()).toString()));
    server.createContext("/token", this::token);
    server.start();
  }

  String issuer() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  void expectLogin(String code, String loginNonce) {
    nonceByCode.put(code, loginNonce);
  }

  void stop() {
    server.stop(0);
  }

  private void discovery(HttpExchange exchange) throws IOException {
    reply(
        exchange,
        """
        {"issuer":"%1$s","authorization_endpoint":"%1$s/authorize","token_endpoint":"%1$s/token",
        "jwks_uri":"%1$s/jwks","response_types_supported":["code"],
        "subject_types_supported":["public"],"id_token_signing_alg_values_supported":["RS256"],
        "token_endpoint_auth_methods_supported":["client_secret_basic"]}
        """
            .formatted(issuer()));
  }

  private void token(HttpExchange exchange) throws IOException {
    String code =
        URLDecoder.decode(
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                .replaceFirst(".*code=([^&]*).*", "$1"),
            StandardCharsets.UTF_8);
    try {
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .issuer(issuer())
              .audience(clientId)
              .subject(code)
              .claim("nonce", nonceByCode.get(code))
              .issueTime(Date.from(Instant.now(Clock.systemUTC())))
              .expirationTime(Date.from(Instant.now(Clock.systemUTC()).plusSeconds(300)))
              .build();
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
      jwt.sign(new RSASSASigner(key));
      reply(
          exchange,
          "{\"access_token\":\"at\",\"token_type\":\"Bearer\",\"expires_in\":300,\"id_token\":\""
              + jwt.serialize()
              + "\"}");
    } catch (JOSEException e) {
      throw new IOException(e);
    }
  }

  private static void reply(HttpExchange exchange, String json) throws IOException {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }
}
