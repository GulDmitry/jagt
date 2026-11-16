package dev.jagt.orchestrator.surface.board.auth;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;

/** Discovers the provider on the first login, so a provider that is down never stops the start. */
final class IssuerRegistrations implements ClientRegistrationRepository {

  static final String ID = "oidc";

  private final String issuerUri;
  private final String clientId;
  private final String clientSecret;
  private @Nullable ClientRegistration discovered;

  IssuerRegistrations(String issuerUri, String clientId, String clientSecret) {
    this.issuerUri = issuerUri;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  @Override
  public synchronized @Nullable ClientRegistration findByRegistrationId(String registrationId) {
    if (!ID.equals(registrationId)) {
      return null;
    }
    if (discovered == null) {
      discovered =
          ClientRegistrations.fromIssuerLocation(issuerUri)
              .registrationId(ID)
              .clientId(clientId)
              .clientSecret(clientSecret)
              .scope("openid", "email")
              .build();
    }
    return discovered;
  }
}
