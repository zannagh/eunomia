// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using Eunomia.Server.Core.Clients;
using Eunomia.Server.Core.Serialization;
using Eunomia.Server.Core.Servers;
using Eunomia.Server.Core.Storage;
using Microsoft.Extensions.Logging;

namespace Eunomia.Server.Core.Communication;

/// <summary>
/// Owns the lifetime of an accepted client socket: pushes the store snapshot on connect, then
/// pumps receives (pings/close/heartbeats only, since data flows server-&gt;client) until the
/// socket closes.
/// </summary>
public class WebSocketHandler
{
    private const int ReceiveBufferSize = 4 * 1024;

    private readonly IKeyedPacketStore _store;
    private readonly ConnectionManager _connectionManager;
    private readonly ILogger<WebSocketHandler> _logger;

    public WebSocketHandler(IKeyedPacketStore store, ConnectionManager connectionManager, ILogger<WebSocketHandler> logger)
    {
        _store = store;
        _connectionManager = connectionManager;
        _logger = logger;
    }

    /// <summary>
    /// Blocks until <paramref name="client"/>'s socket closes so the caller's HTTP request stays
    /// alive for the lifetime of the connection.
    /// </summary>
    public async Task AddSocket(EunomiaClient client)
    {
        if (client.Socket is null)
        {
            return;
        }

        try
        {
            await PushSnapshotAsync(client);
            await PumpReceiveLoopAsync(client);
        }
        catch (Exception ex)
        {
            using (_logger.BeginScope(ServerScope.Property(client.Scope)))
            {
                _logger.LogWarning(ex, "WebSocket session for {ClientId} on {Scope} ended abnormally", client.Id, client.Scope);
            }
        }
        finally
        {
            _connectionManager.OnConnectionRemoved(client.Scope, client.Id, client.RemoteIp);
            client.Socket.Dispose();
            using (_logger.BeginScope(ServerScope.Property(client.Scope)))
            {
                _logger.LogInformation("Websocket disconnected for {Scope} ({ClientId})", client.Scope, client.Id);
            }
        }
    }

    private async Task PushSnapshotAsync(EunomiaClient client)
    {
        foreach (StoreSyncPayload payload in _store.SnapshotFor(client.Scope))
        {
            string json = JsonSerializer.Serialize(WsFrame<StoreSyncPayload>.StoreSync(payload), EunomiaJsonOptions.Wire);
            await client.SendAsync(json, CancellationToken.None);
        }
    }

    private async Task PumpReceiveLoopAsync(EunomiaClient client)
    {
        WebSocket socket = client.Socket!;
        byte[] buffer = new byte[ReceiveBufferSize];

        while (socket.State == WebSocketState.Open)
        {
            WebSocketReceiveResult result = await socket.ReceiveAsync(new ArraySegment<byte>(buffer), CancellationToken.None);

            if (result.MessageType == WebSocketMessageType.Close)
            {
                await socket.CloseAsync(WebSocketCloseStatus.NormalClosure, "closing", CancellationToken.None);
                return;
            }

            if (!client.TryConsumeInboundToken())
            {
                continue;
            }

            // Inbound messages are heartbeats/pings only; server->client is the only data channel.
            _ = Encoding.UTF8.GetString(buffer, 0, result.Count);
        }
    }
}
