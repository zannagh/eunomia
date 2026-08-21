// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Collections.Concurrent;
using System.Net.WebSockets;
using Eunomia.Server.Core.Communication;
using Eunomia.Server.Core.Logging;
using Eunomia.Server.Core.Servers;
using Eunomia.Server.Data.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace Eunomia.Server.Data.Servers;

/// <summary>
/// Postgres-backed <see cref="IServerBlockService"/> with an in-memory blocked-scope cache so the hot
/// path (<see cref="IsBlocked"/>, hit on every packet and handshake) never touches the database. The
/// cache is loaded once at startup and kept in lockstep on block/unblock.
/// </summary>
public sealed class ServerBlockService : IServerBlockService
{
    private readonly IDbContextFactory<EunomiaDbContext> _contextFactory;
    private readonly ConnectionManager _connectionManager;
    private readonly ILogger<ServerBlockService> _logger;
    private readonly ConcurrentDictionary<string, byte> _blocked = new(StringComparer.Ordinal);

    public ServerBlockService(
        IDbContextFactory<EunomiaDbContext> contextFactory,
        ConnectionManager connectionManager,
        ILogger<ServerBlockService> logger)
    {
        _contextFactory = contextFactory;
        _connectionManager = connectionManager;
        _logger = logger;
    }

    public bool IsBlocked(string scope)
    {
        return _blocked.ContainsKey(scope);
    }

    public async Task InitializeAsync(CancellationToken cancellationToken = default)
    {
        await using EunomiaDbContext context = await _contextFactory.CreateDbContextAsync(cancellationToken);
        List<string> blockedScopes = await context.Servers
            .AsNoTracking()
            .Where(s => s.IsBlocked)
            .Select(s => s.Scope)
            .ToListAsync(cancellationToken);

        _blocked.Clear();
        foreach (string scope in blockedScopes)
        {
            _blocked[scope] = 0;
        }
    }

    public async Task BlockAsync(string scope, string? reason, CancellationToken cancellationToken = default)
    {
        await using EunomiaDbContext context = await _contextFactory.CreateDbContextAsync(cancellationToken);
        DateTime now = DateTime.UtcNow;
        ServerRecord? server = await context.Servers.FindAsync([scope], cancellationToken);
        if (server is null)
        {
            server = new ServerRecord { Scope = scope, FirstSeen = now, LastSeen = now };
            context.Servers.Add(server);
        }

        server.IsBlocked = true;
        server.BlockReason = reason;
        await context.SaveChangesAsync(cancellationToken);

        _blocked[scope] = 0;
        using (_logger.BeginScope(ServerScope.Property(scope)))
        {
            _logger.LogInformation("Server {Scope} blocked: {Reason}", LogSafe.Value(scope), LogSafe.Value(reason ?? "(no reason)"));
        }

        await _connectionManager.CloseScopeAsync(scope, WebSocketCloseStatus.PolicyViolation, "server blocked");
    }

    public async Task UnblockAsync(string scope, CancellationToken cancellationToken = default)
    {
        await using EunomiaDbContext context = await _contextFactory.CreateDbContextAsync(cancellationToken);
        ServerRecord? server = await context.Servers.FindAsync([scope], cancellationToken);
        if (server is not null)
        {
            server.IsBlocked = false;
            server.BlockReason = null;
            await context.SaveChangesAsync(cancellationToken);
        }

        _blocked.TryRemove(scope, out _);
        using (_logger.BeginScope(ServerScope.Property(scope)))
        {
            _logger.LogInformation("Server {Scope} unblocked", LogSafe.Value(scope));
        }
    }
}
