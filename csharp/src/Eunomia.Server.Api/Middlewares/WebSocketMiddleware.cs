// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Net.WebSockets;
using Eunomia.Server.Core.Clients;
using Eunomia.Server.Core.Communication;
using Microsoft.AspNetCore.Http;

namespace Eunomia.Server.Api.Middlewares;

/// <summary>
/// Accepts websocket connections on any path containing "/ws". Clients connect with
/// <c>?id=&lt;playerUuid&gt;&amp;scope=&lt;scope&gt;</c>; <c>id</c> is verified against the Mojang
/// session server before the socket is accepted, to keep unauthenticated connections from being
/// spoofed by identities that don't exist. See <see cref="MojangProfileClient"/> for the
/// (secure-by-default) offline dev bypass of that check.
/// </summary>
public class WebSocketMiddleware
{
    private readonly RequestDelegate _next;
    private readonly WebSocketHandler _webSocketHandler;
    private readonly ConnectionManager _connManager;
    private readonly MojangProfileClient _mojang;

    public WebSocketMiddleware(
        RequestDelegate next,
        WebSocketHandler webSocketHandler,
        ConnectionManager connManager,
        MojangProfileClient mojang)
    {
        _next = next;
        _webSocketHandler = webSocketHandler;
        _connManager = connManager;
        _mojang = mojang;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        if (!context.Request.Path.ToString().Contains("/ws") || !context.WebSockets.IsWebSocketRequest)
        {
            await _next(context);
            return;
        }

        if (!context.Request.QueryString.HasValue)
        {
            context.Response.StatusCode = 400;
            return;
        }

        IQueryCollection queryParams = context.Request.Query;
        if (!queryParams.TryGetValue("id", out var id) || !Guid.TryParse(id, out Guid guid))
        {
            context.Response.StatusCode = 400;
            return;
        }

        if (!queryParams.TryGetValue("scope", out var scopeValue) || string.IsNullOrEmpty(scopeValue))
        {
            context.Response.StatusCode = 400;
            return;
        }

        string scope = scopeValue.ToString();

        if (!await _mojang.IsRealAccountAsync(guid, context.RequestAborted))
        {
            context.Response.StatusCode = 403;
            return;
        }

        WebSocket webSocket = await context.WebSockets.AcceptWebSocketAsync();
        string? remoteIp = context.Connection.RemoteIpAddress?.ToString();
        EunomiaClient client = new(guid)
        {
            Socket = webSocket,
            Scope = scope,
            RemoteIp = remoteIp,
        };

        if (!_connManager.OnConnectionAdded(client, remoteIp))
        {
            await webSocket.CloseAsync(WebSocketCloseStatus.PolicyViolation, "connection limit reached", CancellationToken.None);
            return;
        }

        // Owns the socket's lifetime until it closes; must not be wrapped in a using here.
        await _webSocketHandler.AddSocket(client);
    }
}
