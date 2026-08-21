// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Net.WebSockets;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Http.Features;

namespace Eunomia.Server.Tests.TestSupport;

/// <summary>
/// A stub <see cref="IHttpWebSocketFeature"/> that reports the request as a websocket request and hands
/// back a <see cref="RecordingWebSocket"/> on accept, so middleware can be driven over a
/// <c>DefaultHttpContext</c> without a real transport.
/// </summary>
public sealed class FakeWebSocketFeature : IHttpWebSocketFeature
{
    public RecordingWebSocket Socket { get; } = new();

    public bool AcceptCalled { get; private set; }

    public bool IsWebSocketRequest => true;

    public Task<WebSocket> AcceptAsync(WebSocketAcceptContext context)
    {
        AcceptCalled = true;
        return Task.FromResult<WebSocket>(Socket);
    }
}
