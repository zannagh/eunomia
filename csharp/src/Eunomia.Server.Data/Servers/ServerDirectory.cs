// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Core.Communication;
using Eunomia.Server.Core.Servers;
using Eunomia.Server.Data.Entities;
using Microsoft.EntityFrameworkCore;

namespace Eunomia.Server.Data.Servers;

/// <summary>
/// Postgres-backed <see cref="IServerDirectory"/>. Joins the persisted <see cref="ServerRecord"/> rows
/// with live websocket presence from the <see cref="ConnectionManager"/> for the dashboard, and owns
/// the presence upsert on handshake. <c>UpdateCount</c> is never written here - it stays single-sourced
/// in the keyed packet store's stored-update path.
/// </summary>
public sealed class ServerDirectory : IServerDirectory
{
    private const int MaxLogLimit = 500;

    private readonly IDbContextFactory<EunomiaDbContext> contextFactory;
    private readonly ConnectionManager connectionManager;

    public ServerDirectory(
        IDbContextFactory<EunomiaDbContext> contextFactory,
        ConnectionManager connectionManager)
    {
        this.contextFactory = contextFactory;
        this.connectionManager = connectionManager;
    }

    public async Task TouchPresenceAsync(string scope, string? name, CancellationToken cancellationToken = default)
    {
        await using EunomiaDbContext context = await contextFactory.CreateDbContextAsync(cancellationToken);
        DateTime now = DateTime.UtcNow;
        ServerRecord? server = await context.Servers.FindAsync([scope], cancellationToken);
        if (server is null)
        {
            context.Servers.Add(new ServerRecord
            {
                Scope = scope,
                Name = name,
                FirstSeen = now,
                LastSeen = now,
            });
        }
        else
        {
            server.LastSeen = now;
            if (!string.IsNullOrEmpty(name))
            {
                server.Name = name;
            }
        }

        await context.SaveChangesAsync(cancellationToken);
    }

    public async Task<IReadOnlyList<ServerSummary>> ListAsync(CancellationToken cancellationToken = default)
    {
        await using EunomiaDbContext context = await contextFactory.CreateDbContextAsync(cancellationToken);
        List<ServerRecord> records = await context.Servers.AsNoTracking().ToListAsync(cancellationToken);

        HashSet<string> known = new(records.Select(r => r.Scope), StringComparer.Ordinal);
        List<ServerSummary> summaries = records.Select(ToSummary).ToList();

        // Live-connected scopes without a persisted record yet still surface, marked online.
        foreach (string scope in connectionManager.ActiveScopes())
        {
            if (known.Add(scope))
            {
                summaries.Add(new ServerSummary(
                    scope, null, connectionManager.LiveCount(scope), 0, default, default, false, null, true));
            }
        }

        return summaries.OrderByDescending(s => s.Online).ThenByDescending(s => s.LastSeen).ToList();
    }

    public async Task<ServerDetail?> GetAsync(string scope, CancellationToken cancellationToken = default)
    {
        await using EunomiaDbContext context = await contextFactory.CreateDbContextAsync(cancellationToken);
        ServerRecord? record = await context.Servers.AsNoTracking()
            .FirstOrDefaultAsync(s => s.Scope == scope, cancellationToken);

        bool online = connectionManager.LiveCount(scope) > 0;
        if (record is null && !online)
        {
            return null;
        }

        List<ChannelStat> channels = await context.KeyedEntries.AsNoTracking()
            .Where(e => e.Scope == scope)
            .GroupBy(e => e.Channel)
            .Select(g => new ChannelStat(g.Key, g.Count()))
            .ToListAsync(cancellationToken);

        ServerSummary summary = record is not null
            ? ToSummary(record)
            : new ServerSummary(scope, null, connectionManager.LiveCount(scope), 0, default, default, false, null, true);

        return new ServerDetail(summary, channels, channels.Sum(c => (long)c.EntryCount));
    }

    public async Task<IReadOnlyList<ServerLogRecord>> GetLogsAsync(
        string scope,
        string? level = null,
        int limit = 100,
        CancellationToken cancellationToken = default)
    {
        int capped = Math.Clamp(limit, 1, MaxLogLimit);
        await using EunomiaDbContext context = await contextFactory.CreateDbContextAsync(cancellationToken);
        IQueryable<ServerLogEntry> query = context.ServerLogs.AsNoTracking().Where(l => l.Scope == scope);
        if (!string.IsNullOrWhiteSpace(level))
        {
            query = query.Where(l => l.Level == level);
        }

        List<ServerLogEntry> entries = await query
            .OrderByDescending(l => l.Timestamp)
            .Take(capped)
            .ToListAsync(cancellationToken);

        return entries
            .Select(l => new ServerLogRecord(l.Id, l.Scope, l.Timestamp, l.Level, l.Message, l.Exception))
            .ToList();
    }

    private ServerSummary ToSummary(ServerRecord record)
    {
        int live = connectionManager.LiveCount(record.Scope);
        return new ServerSummary(
            record.Scope,
            record.Name,
            live,
            record.UpdateCount,
            record.FirstSeen,
            record.LastSeen,
            record.IsBlocked,
            record.BlockReason,
            live > 0);
    }
}
