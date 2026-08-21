// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Data.Entities;
using Microsoft.EntityFrameworkCore;

namespace Eunomia.Server.Data;

/// <summary>
/// EF Core context for the Eunomia server: dashboard users/auth plus the relayed keyed-packet store
/// and per-server bookkeeping.
/// </summary>
public class EunomiaDbContext : DbContext
{
    public EunomiaDbContext(DbContextOptions<EunomiaDbContext> options)
        : base(options)
    {
    }

    public DbSet<User> Users => Set<User>();

    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();

    public DbSet<UserExternalLink> UserExternalLinks => Set<UserExternalLink>();

    public DbSet<ServerRecord> Servers => Set<ServerRecord>();

    public DbSet<ServerLogEntry> ServerLogs => Set<ServerLogEntry>();

    public DbSet<KeyedEntry> KeyedEntries => Set<KeyedEntry>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        modelBuilder.Entity<User>(entity =>
        {
            entity.HasIndex(u => u.Identifier).IsUnique();
        });

        modelBuilder.Entity<UserExternalLink>(entity =>
        {
            entity.HasKey(l => new { l.UserId, l.Provider });

            entity.HasOne(l => l.User)
                .WithMany(u => u.ExternalLinks)
                .HasForeignKey(l => l.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasIndex(l => new { l.Provider, l.ExternalId });
        });

        modelBuilder.Entity<ServerRecord>(entity =>
        {
            entity.HasKey(s => s.Scope);
        });

        modelBuilder.Entity<ServerLogEntry>(entity =>
        {
            entity.HasIndex(l => new { l.Scope, l.Timestamp });
        });

        modelBuilder.Entity<KeyedEntry>(entity =>
        {
            entity.HasKey(e => new { e.Scope, e.Channel, e.Key });
        });
    }
}
