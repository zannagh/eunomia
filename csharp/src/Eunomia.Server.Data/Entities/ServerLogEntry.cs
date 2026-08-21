// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.ComponentModel.DataAnnotations;

namespace Eunomia.Server.Data.Entities;

/// <summary>
/// A single server-side log line associated with a <see cref="ServerRecord.Scope"/>, written by the
/// log sink so operators can review per-server activity from the dashboard.
/// </summary>
public class ServerLogEntry
{
    [Key]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    [MaxLength(512)]
    public required string Scope { get; set; }

    public DateTime Timestamp { get; set; }

    [Required]
    [MaxLength(32)]
    public required string Level { get; set; }

    [Required]
    public required string Message { get; set; }

    public string? Exception { get; set; }
}
