// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Data;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;

namespace Eunomia.Server.Tests.TestSupport;

/// <summary>
/// An <see cref="IDbContextFactory{TContext}"/> backed by a temp-file SQLite database, standing in for
/// the real Postgres in storage tests. A file (rather than <c>:memory:</c>) is used so a second store
/// instance over the same factory reads the persisted rows back - the direct analog of the old
/// "reload across store instances" file check - and so SQLite's own write locking serializes the
/// concurrent-write tests. Disposing removes the temp database.
/// </summary>
public sealed class SqliteDbContextFactory : IDbContextFactory<EunomiaDbContext>, IDisposable
{
    private readonly string _databasePath =
        Path.Combine(Path.GetTempPath(), "eunomia-sqlite-" + Guid.NewGuid().ToString("N") + ".db");

    private readonly DbContextOptions<EunomiaDbContext> _options;

    public SqliteDbContextFactory()
    {
        _options = new DbContextOptionsBuilder<EunomiaDbContext>()
            .UseSqlite($"Data Source={_databasePath}")
            .Options;

        using EunomiaDbContext context = CreateDbContext();
        context.Database.EnsureCreated();
    }

    public EunomiaDbContext CreateDbContext()
    {
        return new EunomiaDbContext(_options);
    }

    public void Dispose()
    {
        // Release pooled connections before deleting the file, or the delete races the open handle.
        SqliteConnection.ClearAllPools();
        if (File.Exists(_databasePath))
        {
            File.Delete(_databasePath);
        }
    }
}
