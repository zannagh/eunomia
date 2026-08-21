// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Data.Entities;

namespace Eunomia.Server.Authentication.Services;

/// <summary>
/// Links external accounts (Modrinth, Discord) to a signed-in <see cref="User"/>. Discord is link-only;
/// Modrinth is also a login provider and its link is refreshed on Modrinth sign-in.
/// </summary>
public interface IAccountLinkService
{
    /// <summary>Whether <paramref name="provider"/> is a supported, enabled link provider.</summary>
    bool IsLinkable(string provider);

    /// <summary>
    /// Builds the provider authorize URL for a link flow, or null when the provider is unknown/disabled.
    /// </summary>
    string? BuildAuthorizeUrl(string provider, string state, string redirectUri);

    /// <summary>
    /// Exchanges the returned code, reads the external profile, and upserts the link for the user.
    /// Returns null when the provider is unsupported/disabled or verification fails.
    /// </summary>
    Task<UserExternalLink?> CompleteLinkAsync(string provider, string code, string redirectUri, Guid userId);

    /// <summary>Inserts or refreshes a link row for the given user/provider.</summary>
    Task<UserExternalLink> UpsertLinkAsync(Guid userId, string provider, string externalId, string handle);

    /// <summary>Reads all external links attached to a user (for the profile page and admin views).</summary>
    Task<IReadOnlyList<UserExternalLink>> GetLinksAsync(Guid userId);
}
