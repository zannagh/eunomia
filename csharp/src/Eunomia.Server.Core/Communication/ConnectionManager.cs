// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Collections.Concurrent;
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
}
