// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Net.Http;
using System.Net.WebSockets;
using Eunomia.Server.Api.Middlewares;
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
/// check.
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

    private static (WebSocketMiddleware Middleware, HttpContext Context) Build(
        bool blocked,
        IServerDirectory directory,
        FakeWebSocketFeature feature)
    {
        ConnectionManager connectionManager = new();
        WebSocketHandler handler = new(
            Substitute.For<IKeyedPacketStore>(), connectionManager, NullLogger<WebSocketHandler>.Instance);
        MojangProfileClient mojang = new(Substitute.For<IHttpClientFactory>(), NullLogger<MojangProfileClient>.Instance);
        IServerBlockService blockService = Substitute.For<IServerBlockService>();
        blockService.IsBlocked(Scope).Returns(blocked);

        WebSocketMiddleware middleware = new(
            _ => Task.CompletedTask,
            handler,
            connectionManager,
            mojang,
            blockService,
            directory,
            NullLogger<WebSocketMiddleware>.Instance);

        DefaultHttpContext context = new();
        context.Request.Path = "/ws";
        context.Request.QueryString = new QueryString($"?id={Guid.NewGuid()}&scope={Scope}&name=TestServer");
        context.Features.Set<IHttpWebSocketFeature>(feature);

        return (middleware, context);
    }
}
