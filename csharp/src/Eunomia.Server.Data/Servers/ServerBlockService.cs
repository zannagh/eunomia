// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Collections.Concurrent;
using System.Net.WebSockets;
using Eunomia.Server.Core.Communication;
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
    private readonly IDbContextFactory<EunomiaDbContext> contextFactory;
    private readonly ConnectionManager connectionManager;
    private readonly ILogger<ServerBlockService> logger;
    private readonly ConcurrentDictionary<string, byte> blocked = new(StringComparer.Ordinal);

    public ServerBlockService(
        IDbContextFactory<EunomiaDbContext> contextFactory,
        ConnectionManager connectionManager,
        ILogger<ServerBlockService> logger)
    {
        this.contextFactory = contextFactory;
        this.connectionManager = connectionManager;
        this.logger = logger;
    }

    public bool IsBlocked(string scope)
    {
        return blocked.ContainsKey(scope);
    }

    public async Task InitializeAsync(CancellationToken cancellationToken = default)
    {
        await using EunomiaDbContext context = await contextFactory.CreateDbContextAsync(cancellationToken);
        List<string> blockedScopes = await context.Servers
            .AsNoTracking()
            .Where(s => s.IsBlocked)
            .Select(s => s.Scope)
            .ToListAsync(cancellationToken);

        blocked.Clear();
        foreach (string scope in blockedScopes)
        {
            blocked[scope] = 0;
        }
    }

    public async Task BlockAsync(string scope, string? reason, CancellationToken cancellationToken = default)
    {
        await using EunomiaDbContext context = await contextFactory.CreateDbContextAsync(cancellationToken);
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

        blocked[scope] = 0;
        using (logger.BeginScope(ServerScope.Property(scope)))
        {
            logger.LogInformation("Server {Scope} blocked: {Reason}", scope, reason ?? "(no reason)");
        }

        await connectionManager.CloseScopeAsync(scope, WebSocketCloseStatus.PolicyViolation, "server blocked");
    }

    public async Task UnblockAsync(string scope, CancellationToken cancellationToken = default)
    {
        await using EunomiaDbContext context = await contextFactory.CreateDbContextAsync(cancellationToken);
        ServerRecord? server = await context.Servers.FindAsync([scope], cancellationToken);
        if (server is not null)
        {
            server.IsBlocked = false;
            server.BlockReason = null;
            await context.SaveChangesAsync(cancellationToken);
        }

        blocked.TryRemove(scope, out _);
        using (logger.BeginScope(ServerScope.Property(scope)))
        {
            logger.LogInformation("Server {Scope} unblocked", scope);
        }
    }
}
