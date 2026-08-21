// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Collections.Concurrent;
using System.Net.WebSockets;
using Eunomia.Server.Core.Clients;

namespace Eunomia.Server.Core.Communication;

/// <summary>
/// Tracks connected <see cref="EunomiaClient"/>s per scope (MC-server identity) and relays
/// messages within a scope.
/// </summary>
public class ConnectionManager
{
    private const int MaxConnectionsPerScope = 500;
    private const int MaxConnectionsPerIp = 20;

    /// <summary>How long to wait for a peer's close frame before abandoning a graceful close.</summary>
    private static readonly TimeSpan s_closeTimeout = TimeSpan.FromSeconds(5);

    private readonly ConcurrentDictionary<string, ConcurrentDictionary<Guid, EunomiaClient>> _clientsByScope = new();
    private readonly ConcurrentDictionary<string, int> _connectionsByIp = new();

    /// <summary>
    /// Registers a newly-connected client. Returns false if the scope or IP connection cap was hit,
    /// in which case the caller must not proceed to add the socket.
    /// </summary>
    public bool OnConnectionAdded(EunomiaClient client, string? remoteIp = null)
    {
        ConcurrentDictionary<Guid, EunomiaClient> scopeClients =
            _clientsByScope.GetOrAdd(client.Scope, _ => new ConcurrentDictionary<Guid, EunomiaClient>());

        if (scopeClients.Count >= MaxConnectionsPerScope)
        {
            return false;
        }

        if (remoteIp is not null)
        {
            int ipCount = _connectionsByIp.AddOrUpdate(remoteIp, 1, (_, count) => count + 1);
            if (ipCount > MaxConnectionsPerIp)
            {
                _connectionsByIp.AddOrUpdate(remoteIp, 0, (_, count) => Math.Max(0, count - 1));
                return false;
            }
        }

        scopeClients[client.Id] = client;
        return true;
    }

    /// <summary>
    /// Removes a disconnected client from its scope.
    /// </summary>
    public void OnConnectionRemoved(string scope, Guid id, string? remoteIp = null)
    {
        if (_clientsByScope.TryGetValue(scope, out ConcurrentDictionary<Guid, EunomiaClient>? scopeClients))
        {
            scopeClients.TryRemove(id, out _);
        }

        if (remoteIp is not null)
        {
            _connectionsByIp.AddOrUpdate(remoteIp, 0, (_, count) => Math.Max(0, count - 1));
        }
    }

    /// <summary>
    /// Returns true if the given identity has a live websocket session in the given scope.
    /// </summary>
    public bool IsConnected(string scope, Guid id)
    {
        return _clientsByScope.TryGetValue(scope, out ConcurrentDictionary<Guid, EunomiaClient>? scopeClients)
            && scopeClients.ContainsKey(id);
    }

    /// <summary>
    /// Returns the number of live websocket sessions in <paramref name="scope"/>.
    /// </summary>
    public int LiveCount(string scope)
    {
        return _clientsByScope.TryGetValue(scope, out ConcurrentDictionary<Guid, EunomiaClient>? scopeClients)
            ? scopeClients.Count
            : 0;
    }

    /// <summary>
    /// Returns the scopes that currently have at least one live websocket session.
    /// </summary>
    public IReadOnlyCollection<string> ActiveScopes()
    {
        return _clientsByScope
            .Where(pair => !pair.Value.IsEmpty)
            .Select(pair => pair.Key)
            .ToList();
    }

    /// <summary>
    /// Closes every live socket in <paramref name="scope"/> with the given status and reason, used to
    /// evict connections when a scope is blocked. Best-effort; dead sockets are removed rather than
    /// throwing.
    /// </summary>
    public async Task CloseScopeAsync(string scope, WebSocketCloseStatus status, string reason)
    {
        if (!_clientsByScope.TryGetValue(scope, out ConcurrentDictionary<Guid, EunomiaClient>? scopeClients))
        {
            return;
        }

        // Close every client concurrently and under a deadline: CloseAsync waits for the peer's close
        // frame, so one unresponsive relay would otherwise stall the admin request that triggered the
        // block and hold up eviction of every remaining socket in the scope.
        await Task.WhenAll(scopeClients.Values.Select(client => CloseClientAsync(scopeClients, client, status, reason)));
    }

    /// <summary>
    /// Sends <paramref name="json"/> to every connected client in <paramref name="scope"/>, except
    /// <paramref name="exceptId"/> if given. Dead sockets are removed rather than throwing.
    /// </summary>
    public async Task BroadcastToScopeAsync(string scope, string json, Guid? exceptId = null)
    {
        if (!_clientsByScope.TryGetValue(scope, out ConcurrentDictionary<Guid, EunomiaClient>? scopeClients))
        {
            return;
        }

        foreach (EunomiaClient client in scopeClients.Values)
        {
            if (exceptId.HasValue && client.Id == exceptId.Value)
            {
                continue;
            }

            try
            {
                await client.SendAsync(json, CancellationToken.None);
            }
            catch (Exception)
            {
                scopeClients.TryRemove(client.Id, out _);
            }
        }
    }

    private static async Task CloseClientAsync(
        ConcurrentDictionary<Guid, EunomiaClient> scopeClients,
        EunomiaClient client,
        WebSocketCloseStatus status,
        string reason)
    {
        try
        {
            if (client.Socket is { State: WebSocketState.Open })
            {
                using CancellationTokenSource timeout = new(s_closeTimeout);
                await client.Socket.CloseAsync(status, reason, timeout.Token);
            }
        }
        catch (Exception)
        {
            // Deliberately swallowed: a socket that will not close cleanly is still being evicted.
        }
        finally
        {
            // Deregister on every path. Leaving successfully-closed clients registered kept a blocked
            // scope reporting as live until its receive loop happened to notice.
            scopeClients.TryRemove(client.Id, out _);
        }
    }
}
