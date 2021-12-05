package com.programmers.film.api.auth.oauth2;

import static com.programmers.film.api.auth.util.CookieUtil.REDIRECT_URI_PARAM_COOKIE_NAME;
import static com.programmers.film.api.auth.util.CookieUtil.REFRESH_TOKEN;

import com.programmers.film.api.auth.service.AuthService;
import com.programmers.film.api.auth.util.CookieUtil;
import com.programmers.film.api.config.AppConfigure;
import com.programmers.film.domain.auth.domain.Auth;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends
	SavedRequestAwareAuthenticationSuccessHandler {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

	private final HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;

	private final AppConfigure appConfigure;
	private final AuthService authService;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
		Authentication authentication) throws ServletException, IOException {
		if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {

			String registrationId = oauth2Token.getAuthorizedClientRegistrationId();
			OAuth2User oAuth2User = oauth2Token.getPrincipal();

			Auth auth = processUserOAuth2UserJoin(oAuth2User, registrationId);
			String targetUrl = determineTargetUrl(request, response, auth);

			if (response.isCommitted()) {
				log.debug(
					"Response has already been committed. Unable to redirect to " + targetUrl);
				return;
			}

			clearAuthenticationAttributes(request, response);
			getRedirectStrategy().sendRedirect(request, response, targetUrl);
		} else {
			super.onAuthenticationSuccess(request, response, authentication);
		}
	}

	private Auth processUserOAuth2UserJoin(OAuth2User oAuth2User, String registrationId) {
		return authService.join(oAuth2User, registrationId);
	}

	protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
		Auth auth) {
		Optional<String> redirectUri = CookieUtil.getCookie(request, REDIRECT_URI_PARAM_COOKIE_NAME)
			.map(Cookie::getValue);

		if (redirectUri.isPresent() && !isAuthorizedRedirectUri(redirectUri.get())) {
			throw new IllegalArgumentException(
				"Sorry! We've got an Unauthorized Redirect URI and can't proceed with the authentication");
		}

		String targetUrl = redirectUri.orElse(getDefaultTargetUrl());

		OAuth2AuthorizedClient oAuth2AuthorizedClient = oAuth2AuthorizedClientService.loadAuthorizedClient(
			auth.getProvider(), auth.getProviderId());

		CookieUtil.clearCookie(request, response, REFRESH_TOKEN);

		OAuth2RefreshToken refreshToken = oAuth2AuthorizedClient.getRefreshToken();
		if (refreshToken != null && refreshToken.getExpiresAt() != null) {
			int maxAge = (int) refreshToken.getExpiresAt().getEpochSecond();
			CookieUtil.addCookie(response, REFRESH_TOKEN, refreshToken.getTokenValue(), maxAge);
		}

		return UriComponentsBuilder.fromUriString(targetUrl)
			.queryParam("token", oAuth2AuthorizedClient.getAccessToken().getTokenValue())
			.build().toUriString();
	}

	private boolean isAuthorizedRedirectUri(String uri) {
		URI clientRedirectUri = URI.create(uri);

		return appConfigure.getOauth2().getAuthorizedRedirectUris()
			.stream()
			.anyMatch(authorizedRedirectUri -> {
				URI authorizedURI = URI.create(authorizedRedirectUri);
				return authorizedURI.getHost().equalsIgnoreCase(clientRedirectUri.getHost())
					&& authorizedURI.getPort() == clientRedirectUri.getPort();
			});
	}

	protected void clearAuthenticationAttributes(HttpServletRequest request,
		HttpServletResponse response) {
		super.clearAuthenticationAttributes(request);
		httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(request,
			response);
	}

//	private String generateLoginSuccessJson(Auth auth) {
//		String token = generateToken(auth);
//		log.debug("Jwt({}) created for oauth2 login user {}", token, auth.getUsername());
//		return "{\"token\":\"" + token + "\", \"username\":\"" + auth.getUsername()
//			+ "\", \"group\":\"" + auth.getGroup().getName() + "\"}";
//	}
//
//	private String generateToken(Auth auth) {
//		return jwt.sign(Jwt.Claims.from(auth.getUsername(), new String[]{"ROLE_USER"}));
//	}
}
