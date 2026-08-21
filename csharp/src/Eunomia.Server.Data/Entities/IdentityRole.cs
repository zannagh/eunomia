// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Data.Entities;

/// <summary>
/// Privilege level of a signed-in user, ordered ascending by privilege.
/// </summary>
public enum IdentityRole
{
    /// <summary>Unauthenticated or unrecognized caller; no dashboard access.</summary>
    Guest = 0,

    /// <summary>A recognized, signed-in user with default access.</summary>
    User = 1,

    /// <summary>Elevated user able to moderate servers/entries but not manage roles.</summary>
    Moderator = 2,

    /// <summary>Full administrative access.</summary>
    Admin = 3,
}
