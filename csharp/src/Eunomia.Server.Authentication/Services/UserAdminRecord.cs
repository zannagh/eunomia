// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Data.Entities;

namespace Eunomia.Server.Authentication.Services;

/// <summary>
/// Admin-facing view of one dashboard user: identity, current role, and their linked external accounts.
/// </summary>
public sealed record UserAdminRecord(
    Guid Id,
    string Identifier,
    string DisplayName,
    IdentityRole Role,
    IReadOnlyList<UserAdminLink> Links);
