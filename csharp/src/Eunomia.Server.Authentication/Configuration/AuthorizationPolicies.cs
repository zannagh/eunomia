// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Authentication.Configuration;

/// <summary>
/// Names of the authorization policies registered by the authentication stack. Reference these from
/// <c>[Authorize(Policy = ...)]</c> so telemetry/blocking (staff) and role-management (admin) endpoints
/// stay in lockstep with the policy definitions.
/// </summary>
public static class AuthorizationPolicies
{
    /// <summary>Moderator or Admin — dashboards, telemetry, blocking.</summary>
    public const string StaffOnly = "StaffOnly";

    /// <summary>Admin only — user role management (a Moderator cannot promote others).</summary>
    public const string AdminOnly = "AdminOnly";
}
