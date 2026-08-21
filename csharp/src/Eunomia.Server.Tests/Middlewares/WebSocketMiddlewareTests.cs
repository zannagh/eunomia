// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Net.Http;
using System.Net.WebSockets;
using Eunomia.Server.Api.Middlewares;
using Eunomia.Server.Api.Versioning;
using Eunomia.Server.Core.Clients;
using Eunomia.Server.Core.Communication;
using Eunomia.Server.Core.Servers;
using Eunomia.Server.Core.Storage;
using Eunomia.Server.Tests.TestSupport;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Http.Features;
using Microsoft.Extensions.Logging.Abstractions;
using NSubstitute;

namespace Eunomia.Server.Tests.Middlewares;

/// <summary>
/// Drives the websocket handshake middleware over a <see cref="DefaultHttpContext"/> with a stubbed
/// websocket feature, covering the block contract: a blocked scope is closed with 1008
/// (PolicyViolation) before any registration, and a non-blocked scope falls through to the identity
/// check; the path gate only claims /ws; and the handshake's optional <c>v</c> version parameter is
/// resolved with a missing value meaning "legacy client, oldest supported version".
/// </summary>
public class WebSocketMiddlewareTests
{
    private const string Scope = "mc.ws-tests:25565";

    [Fact]
    public async Task InvokeAsync_BlockedScope_ClosesWith1008_AndDoesNotTouchPresence()
    {
        IServerDirectory directory = Substitute.For<IServerDirectory>();
        FakeWebSocketFeature feature = new();
        (WebSocketMiddleware middleware, HttpContext context) = Build(blocked: true, directory, feature);

        await middleware.InvokeAsync(context);

        Assert.True(feature.AcceptCalled);
        Assert.Equal(WebSocketCloseStatus.PolicyViolation, feature.Socket.RecordedStatus);
        await directory.DidNotReceiveWithAnyArgs().TouchPresenceAsync(default!, default, default);
    }

    [Fact]
    public async Task InvokeAsync_NonBlockedScope_FallsThroughToIdentityCheck()
    {
        IServerDirectory directory = Substitute.For<IServerDirectory>();
        FakeWebSocketFeature feature = new();
        (WebSocketMiddleware middleware, HttpContext context) = Build(blocked: false, directory, feature);

        await middleware.InvokeAsync(context);

        // Identity verification (Mojang) fails closed with the stub factory, so the request is refused
        // with 403 without ever accepting the socket - proving the block gate did not short-circuit it.
        Assert.False(feature.AcceptCalled);
        Assert.Equal(403, context.Response.StatusCode);
    }

    [Theory]
    [InlineData("/wsfoo")]
    [InlineData("/api/v0.3/ws")]
    public async Task InvokeAsync_PathThatOnlyEmbedsWs_IsNotClaimed(string path)
    {
        IServerDirectory directory = Substitute.For<IServerDirectory>();
        FakeWebSocketFeature feature = new();
        bool nextCalled = false;
        (WebSocketMiddleware middleware, HttpContext context) = Build(
            blocked: false, directory, feature, path: path, next: _ =>
            {
                nextCalled = true;
                return Task.CompletedTask;
            });

        await middleware.InvokeAsync(context);

        // A substring test on "/ws" would have claimed both of these and refused them at the identity
        // check instead of passing them on to the rest of the pipeline.
        Assert.True(nextCalled);
        Assert.False(feature.AcceptCalled);
    }

    [Fact]
    public async Task InvokeAsync_SupportedVersion_FallsThroughToIdentityCheck()
    {
        IServerDirectory directory = Substitute.For<IServerDirectory>();
        FakeWebSocketFeature feature = new();
        (WebSocketMiddleware middleware, HttpContext context) = Build(
            blocked: false, directory, feature, version: EunomiaApiVersions.Oldest.ToString());

        await middleware.InvokeAsync(context);

        Assert.False(feature.AcceptCalled);
        Assert.Equal(403, context.Response.StatusCode);
    }

    [Fact]
    public async Task InvokeAsync_MissingVersion_IsTreatedAsALegacyClient()
    {
        IServerDirectory directory = Substitute.For<IServerDirectory>();
        FakeWebSocketFeature feature = new();
        (WebSocketMiddleware middleware, HttpContext context) = Build(blocked: false, directory, feature);

        await middleware.InvokeAsync(context);

        // No "v" in the handshake means a pre-versioning client: it must be served the oldest supported
        // version, not refused, so it reaches the identity check and fails there (403) instead.
        Assert.False(feature.AcceptCalled);
        Assert.Equal(403, context.Response.StatusCode);
    }

    [Theory]
    [InlineData("99.0")]
    [InlineData("not-a-version")]
    public async Task InvokeAsync_UnsupportedVersion_ClosesWithADistinctCode(string version)
    {
        IServerDirectory directory = Substitute.For<IServerDirectory>();
        FakeWebSocketFeature feature = new();
        (WebSocketMiddleware middleware, HttpContext context) = Build(
            blocked: false, directory, feature, version: version);

        await middleware.InvokeAsync(context);

        Assert.True(feature.AcceptCalled);

        // Deliberately not 1008: the client must be able to tell "upgrade me" apart from the
        // "server blocked" refusal that triggers its vanilla-transport fallback.
        Assert.Equal(
            (WebSocketCloseStatus)WebSocketMiddleware.UnsupportedVersionCloseCode,
            feature.Socket.RecordedStatus);
        Assert.NotEqual(WebSocketCloseStatus.PolicyViolation, feature.Socket.RecordedStatus);
        await directory.DidNotReceiveWithAnyArgs().TouchPresenceAsync(default!, default, default);
    }

    private static (WebSocketMiddleware Middleware, HttpContext Context) Build(
        bool blocked,
        IServerDirectory directory,
        FakeWebSocketFeature feature,
        string path = "/ws",
        string? version = null,
        RequestDelegate? next = null)
    {
        ConnectionManager connectionManager = new();
        WebSocketHandler handler = new(
            Substitute.For<IKeyedPacketStore>(), connectionManager, NullLogger<WebSocketHandler>.Instance);
        MojangProfileClient mojang = new(Substitute.For<IHttpClientFactory>(), NullLogger<MojangProfileClient>.Instance);
        IServerBlockService blockService = Substitute.For<IServerBlockService>();
        blockService.IsBlocked(Scope).Returns(blocked);

        WebSocketMiddleware middleware = new(
            next ?? (_ => Task.CompletedTask),
            handler,
            connectionManager,
            mojang,
            blockService,
            directory,
            NullLogger<WebSocketMiddleware>.Instance);

        DefaultHttpContext context = new();
        context.Request.Path = path;
        string versionQuery = version is null ? string.Empty : $"&v={version}";
        context.Request.QueryString =
            new QueryString($"?id={Guid.NewGuid()}&scope={Scope}&name=TestServer{versionQuery}");
        context.Features.Set<IHttpWebSocketFeature>(feature);

        return (middleware, context);
    }
}
