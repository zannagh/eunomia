// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace Eunomia.Server.Data.Entities;

/// <summary>
/// A single-use refresh token issued to a user; consumed on rotation.
/// </summary>
public class RefreshToken
{
    [Key]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    [MaxLength(16384)]
    public required string Token { get; set; }

    [Required]
    public required string UserId { get; set; }

    [Required]
    public required string UserName { get; set; }

    public DateTime ExpiresAt { get; set; }

    [NotMapped]
    public bool IsExpired => ExpiresAt < DateTime.UtcNow;

    public bool IsConsumed { get; set; }
}
