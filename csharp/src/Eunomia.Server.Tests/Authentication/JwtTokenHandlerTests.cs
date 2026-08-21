// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Authentication.Handlers;
using Eunomia.Server.Authentication.Resources;
using Eunomia.Server.Tests.TestSupport;
using NSubstitute;

namespace Eunomia.Server.Tests.Authentication;

public class JwtTokenHandlerTests
{
    [Fact]
    public async Task VerifyModrinth_ReturnsUsernameAndId_AndSendsRawAuthorizationHeaders()
    {
        RoutingHttpMessageHandler handler = new(
            ("oauth/token", "{\"access_token\":\"mr-token\",\"token_type\":\"Bearer\"}"),
            ("v2/user", "{\"id\":\"mr-id\",\"username\":\"Zannagh\"}"));
        JwtTokenHandler tokenHandler = NewHandler(handler);

        VerificationResult result = await tokenHandler.VerifyModrinthAuthentication("mr-client", "mr-secret", "code", "https://host/oauth-callback");

        Assert.True(result.Success);
        Assert.Equal("Zannagh", result.UserName);
        Assert.Equal("mr-id", result.UserId);

        HttpRequestMessage tokenRequest = handler.Requests.Single(r => r.RequestUri!.ToString().Contains("oauth/token"));
        Assert.Equal("mr-secret", tokenRequest.Headers.GetValues("Authorization").Single());

        HttpRequestMessage userRequest = handler.Requests.Single(r => r.RequestUri!.ToString().Contains("v2/user"));
        Assert.Equal("mr-token", userRequest.Headers.GetValues("Authorization").Single());
    }

    [Fact]
    public async Task VerifyDiscord_ReturnsUsernameAndId_AndSendsBearerToUserEndpoint()
    {
        RoutingHttpMessageHandler handler = new(
            ("oauth2/token", "{\"access_token\":\"dc-token\",\"token_type\":\"Bearer\"}"),
            ("users/@me", "{\"id\":\"dc-id\",\"username\":\"Coyote\"}"));
        JwtTokenHandler tokenHandler = NewHandler(handler);

        VerificationResult result = await tokenHandler.VerifyDiscordAuthentication("dc-client", "dc-secret", "code", "https://host/link/discord/callback");

        Assert.True(result.Success);
        Assert.Equal("Coyote", result.UserName);
        Assert.Equal("dc-id", result.UserId);

        HttpRequestMessage userRequest = handler.Requests.Single(r => r.RequestUri!.ToString().Contains("users/@me"));
        Assert.Equal("Bearer dc-token", userRequest.Headers.GetValues("Authorization").Single());
    }

    [Fact]
    public async Task VerifyGitHub_ReadsLoginAndNumericId()
    {
        RoutingHttpMessageHandler handler = new(
            ("login/oauth/access_token", "{\"access_token\":\"gh-token\"}"),
            ("github.com/user", "{\"login\":\"octocat\",\"id\":583231}"));
        JwtTokenHandler tokenHandler = NewHandler(handler);

        VerificationResult result = await tokenHandler.VerifyGitHubAuthentication("gh-client", "gh-secret", "code");

        Assert.True(result.Success);
        Assert.Equal("octocat", result.UserName);
        Assert.Equal("583231", result.UserId);
    }

    [Fact]
    public async Task VerifyModrinth_FailsWhenUserPayloadMissingFields()
    {
        RoutingHttpMessageHandler handler = new(
            ("oauth/token", "{\"access_token\":\"mr-token\"}"),
            ("v2/user", "{\"id\":\"mr-id\"}"));
        JwtTokenHandler tokenHandler = NewHandler(handler);

        VerificationResult result = await tokenHandler.VerifyModrinthAuthentication("mr-client", "mr-secret", "code", "https://host/oauth-callback");

        Assert.False(result.Success);
    }

    private static JwtTokenHandler NewHandler(RoutingHttpMessageHandler handler)
    {
        return new JwtTokenHandler(new SingleHandlerHttpClientFactory(handler), Substitute.For<IRefreshTokenHandler>());
    }
}
