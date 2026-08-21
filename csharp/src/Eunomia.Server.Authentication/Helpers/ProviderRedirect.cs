// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Authentication.Helpers;

/// <summary>
/// Guards the outbound hops to an OAuth provider's authorize endpoint. The target is assembled from
/// configuration rather than from the request, but the provider is picked by a query parameter and the
/// URL itself is operator-editable, so it is checked to be an absolute https endpoint before it is
/// handed to a redirect - a misconfigured or relative value would otherwise turn the login entry point
/// into an open redirect.
/// </summary>
public static class ProviderRedirect
{
    /// <summary>Whether <paramref name="url"/> is safe to redirect a browser to.</summary>
    public static bool IsAllowed(string? url)
    {
        return Uri.TryCreate(url, UriKind.Absolute, out Uri? parsed)
               && parsed.Scheme == Uri.UriSchemeHttps
               && !string.IsNullOrEmpty(parsed.Host);
    }
}
