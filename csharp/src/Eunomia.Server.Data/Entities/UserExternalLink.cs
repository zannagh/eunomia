// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace Eunomia.Server.Data.Entities;

/// <summary>
/// Links a <see cref="User"/> to an external identity provider account. The composite key
/// (UserId, Provider) allows at most one link per provider per user.
/// </summary>
public class UserExternalLink
{
    public Guid UserId { get; set; }

    [ForeignKey(nameof(UserId))]
    public User User { get; set; } = null!;

    /// <summary>Provider key, e.g. "modrinth" or "discord".</summary>
    [Required]
    [MaxLength(64)]
    public required string Provider { get; set; }

    /// <summary>Stable id of the account at the provider.</summary>
    [Required]
    [MaxLength(256)]
    public required string ExternalId { get; set; }

    /// <summary>Human-readable handle shown to admins.</summary>
    [MaxLength(256)]
    public string Handle { get; set; } = string.Empty;

    public DateTime LinkedAt { get; set; } = DateTime.UtcNow;
}
