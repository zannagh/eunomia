// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.ComponentModel.DataAnnotations;

namespace Eunomia.Server.Data.Entities;

/// <summary>
/// A dashboard user. <see cref="Identifier"/> is the canonical login identity in the form
/// <c>{name}__{providerId}</c> and carries a unique index.
/// </summary>
public class User
{
    [Key]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    [MaxLength(512)]
    public required string Identifier { get; set; }

    [MaxLength(256)]
    public string DisplayName { get; set; } = string.Empty;

    public IdentityRole Role { get; set; } = IdentityRole.User;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    /// <summary>External accounts (modrinth, discord, ...) linked to this user.</summary>
    public ICollection<UserExternalLink> ExternalLinks { get; set; } = [];

    /// <summary>Display name portion of <see cref="Identifier"/> (before the "__" separator).</summary>
    public string UserName => Identifier.Split("__").FirstOrDefault() ?? Identifier;
}
