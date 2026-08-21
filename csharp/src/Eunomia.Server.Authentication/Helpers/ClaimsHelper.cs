// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Security.Claims;

namespace Eunomia.Server.Authentication.Helpers;

/// <summary>
/// Bridges between OAuth/JWT claim shapes and the canonical <c>{name}__{providerId}</c> user identifier.
/// </summary>
public static class ClaimsHelper
{
    public static ClaimsIdentity ClaimsIdentityFromUserNameAndId(string userName, string userId, string authenticationScheme = "Bearer")
    {
        List<Claim> claims =
        [
            new(ClaimTypes.Name, userName),
            new(ClaimTypes.NameIdentifier, userId),
            new("unique_name", userName),
            new("nameid", userId),
        ];

        return new ClaimsIdentity(claims, authenticationScheme);
    }

    public static string ToUserId(this ClaimsIdentity identity)
    {
        return identity.FindFirst(ClaimTypes.NameIdentifier)?.Value
               ?? identity.FindFirst("nameid")?.Value
               ?? string.Empty;
    }

    public static string ToUserName(this ClaimsIdentity identity)
    {
        return identity.FindFirst(ClaimTypes.Name)?.Value
               ?? identity.FindFirst("unique_name")?.Value
               ?? string.Empty;
    }

    public static string ToUserIdentifier(this ClaimsIdentity identity)
    {
        string name = identity.ToUserName();
        string id = identity.ToUserId();
        return string.IsNullOrEmpty(name) || string.IsNullOrEmpty(id)
            ? string.Empty
            : $"{name}__{id}";
    }

    /// <summary>Builds the canonical identifier from a raw provider username and id.</summary>
    public static string ToUserIdentifier(string userName, string userId)
    {
        return string.IsNullOrEmpty(userName) || string.IsNullOrEmpty(userId)
            ? string.Empty
            : $"{userName}__{userId}";
    }
}
