// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Net.WebSockets;
using Asp.Versioning;
using Eunomia.Server.Api.Versioning;
using Eunomia.Server.Core.Clients;
using Eunomia.Server.Core.Communication;
using Eunomia.Server.Core.Logging;
using Eunomia.Server.Core.Servers;
using Eunomia.Server.Core.Storage;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;

namespace Eunomia.Server.Api.Middlewares;

/// <summary>
/// Accepts websocket connections on the <c>/ws</c> path (and anything below it). Clients connect with
/// <c>?id=&lt;playerUuid&gt;&amp;scope=&lt;scope&gt;&amp;name=&lt;serverName&gt;&amp;v=&lt;apiVersion&gt;</c>;
/// <c>v</c> is optional and its absence marks a pre-versioning client, which is served the oldest
/// supported version (mirroring the REST <c>DefaultApiVersion</c>). <c>id</c> is verified
/// against the Mojang session server before the socket is accepted, to keep unauthenticated
/// connections from being spoofed by identities that don't exist. A blocked scope is rejected by
/// accepting then closing with 1008 (PolicyViolation) so the client reads the block and falls back to
/// vanilla transport. An unsupported <c>v</c> is closed with <see cref="UnsupportedVersionCloseCode"/>
/// instead, which is deliberately distinct from the 1008 refusals so a client can tell "upgrade me"
/// apart from "this server blocked you". See <see cref="MojangProfileClient"/> for the (secure-by-default) offline dev
/// bypass of the identity check.
/// </summary>
public class WebSocketMiddleware
{
    /// <summary>
    /// Application-defined close code (4000-4999 range) for a handshake carrying an API version this
    /// build does not serve. Kept out of the 1008 (PolicyViolation) space so it cannot be confused with
    /// the "server blocked" refusal, which deliberately triggers the client's vanilla-transport fallback.
    /// </summary>
    public const int UnsupportedVersionCloseCode = 4001;

    /// <summary>How long to wait for a peer's close frame before abandoning a graceful close.</summary>
    private static readonly TimeSpan s_closeTimeout = TimeSpan.FromSeconds(5);

    private readonly RequestDelegate _next;
    private readonly WebSocketHandler _webSocketHandler;
    private readonly ConnectionManager _connManager;
    private readonly MojangProfileClient _mojang;
    private readonly IServerBlockService _blockService;
    private readonly IServerDirectory _directory;
    private readonly ILogger<WebSocketMiddleware> _logger;

    public WebSocketMiddleware(
        RequestDelegate next,
        WebSocketHandler webSocketHandler,
        ConnectionManager connManager,
        MojangProfileClient mojang,
        IServerBlockService blockService,
        IServerDirectory directory,
        ILogger<WebSocketMiddleware> logger)
    {
        _next = next;
        _webSocketHandler = webSocketHandler;
        _connManager = connManager;
        _mojang = mojang;
        _blockService = blockService;
        _directory = directory;
        _logger = logger;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        // StartsWithSegments, not Contains: a substring test also matches "/wsfoo" and any path that
        // merely embeds "/ws", handing unrelated endpoints to this middleware.
        if (!context.Request.Path.StartsWithSegments("/ws") || !context.WebSockets.IsWebSocketRequest)
        {
            await _next(context);
            return;
        }

        if (!TryReadHandshake(context, out Guid guid, out string scope, out string? name))
        {
            context.Response.StatusCode = 400;
            return;
        }

        if (!EunomiaApiVersions.TryResolve(context.Request.Query["v"], out ApiVersion version))
        {
            await RejectUnsupportedVersionAsync(context, scope);
            return;
        }

        if (_blockService.IsBlocked(scope))
        {
            await RejectBlockedAsync(context, scope);
            return;
        }

        if (!await _mojang.IsRealAccountAsync(guid, context.RequestAborted))
        {
            using (_logger.BeginScope(ServerScope.Property(scope)))
            {
                _logger.LogWarning("Rejected websocket for {Scope}: Mojang did not verify {PlayerId}", LogSafe.Value(scope), guid);
            }

            context.Response.StatusCode = 403;
            return;
        }

        await AcceptAndRegisterAsync(context, guid, scope, name, version);
    }

    private static bool TryReadHandshake(HttpContext context, out Guid guid, out string scope, out string? name)
    {
        guid = Guid.Empty;
        scope = string.Empty;
        name = null;

        IQueryCollection query = context.Request.Query;
        if (!query.TryGetValue("id", out var id) || !Guid.TryParse(id, out guid))
        {
            return false;
        }

        if (!query.TryGetValue("scope", out var scopeValue) || string.IsNullOrEmpty(scopeValue))
        {
            return false;
        }

        scope = scopeValue.ToString();
        if (query.TryGetValue("name", out var nameValue) && !string.IsNullOrEmpty(nameValue))
        {
            name = nameValue.ToString();
        }

        // Both are persisted into varchar(512) by TouchPresenceAsync; refuse the handshake outright
        // rather than accepting the socket and blowing up on the presence write afterwards.
        return StorageLimits.IsWithinLimit(scope) && StorageLimits.IsWithinLimit(name);
    }

    /// <summary>
    /// Closes with 1008 (PolicyViolation) without letting an unresponsive peer stall the request:
    /// <see cref="WebSocket.CloseAsync"/> waits for the peer's close frame, which may never arrive.
    /// </summary>
    private static async Task CloseQuietlyAsync(
        WebSocket webSocket,
        WebSocketCloseStatus status,
        string reason,
        CancellationToken cancellationToken)
    {
        try
        {
            await webSocket.CloseAsync(status, reason, cancellationToken);
        }
        catch (Exception)
        {
            // The socket is being refused either way; a peer that will not complete the handshake
            // must not hold the request open.
        }
    }

    private async Task RejectUnsupportedVersionAsync(HttpContext context, string scope)
    {
        WebSocket webSocket = await context.WebSockets.AcceptWebSocketAsync();
        using CancellationTokenSource timeout = new(s_closeTimeout);
        await CloseQuietlyAsync(
            webSocket, (WebSocketCloseStatus)UnsupportedVersionCloseCode, "unsupported api version", timeout.Token);
        using (_logger.BeginScope(ServerScope.Property(scope)))
        {
            _logger.LogInformation(
                "Refused websocket for {Scope}: unsupported api version {Version}",
                LogSafe.Value(scope),
                LogSafe.Value(context.Request.Query["v"].ToString()));
        }
    }

    private async Task RejectBlockedAsync(HttpContext context, string scope)
    {
        WebSocket webSocket = await context.WebSockets.AcceptWebSocketAsync();
        using CancellationTokenSource timeout = new(s_closeTimeout);
        await CloseQuietlyAsync(webSocket, WebSocketCloseStatus.PolicyViolation, "server blocked", timeout.Token);
        using (_logger.BeginScope(ServerScope.Property(scope)))
        {
            _logger.LogInformation("Refused websocket for blocked scope {Scope}", LogSafe.Value(scope));
        }
    }

    private async Task AcceptAndRegisterAsync(
        HttpContext context,
        Guid guid,
        string scope,
        string? name,
        ApiVersion version)
    {
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
            using CancellationTokenSource timeout = new(s_closeTimeout);
            await CloseQuietlyAsync(
                webSocket, WebSocketCloseStatus.PolicyViolation, "connection limit reached", timeout.Token);
            return;
        }

        // Presence only: records the reported name + last-seen, never touches UpdateCount.
        await _directory.TouchPresenceAsync(scope, name, context.RequestAborted);
        using (_logger.BeginScope(ServerScope.Property(scope)))
        {
            _logger.LogInformation(
                "Websocket connected for {Scope} ({PlayerId}) on api version {Version}",
                LogSafe.Value(scope),
                guid,
                version);
        }

        // Owns the socket's lifetime until it closes; must not be wrapped in a using here.
        await _webSocketHandler.AddSocket(client);
    }
}
