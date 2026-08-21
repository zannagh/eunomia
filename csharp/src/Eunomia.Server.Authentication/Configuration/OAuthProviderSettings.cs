// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Authentication.Configuration;

/// <summary>
/// Client credentials and authorize-URL for a single OAuth 2.0 provider. Populated from
/// configuration/environment with placeholders only; <see cref="Enabled"/> gates whether the
/// provider is offered on the login/link screens.
/// </summary>
public class OAuthProviderSettings
{
    public bool Enabled { get; set; }

    public string ClientId { get; set; } = string.Empty;

    public string ClientSecret { get; set; } = string.Empty;

    /// <summary>The provider's authorization endpoint the browser is redirected to.</summary>
    public string OAuthUrl { get; set; } = string.Empty;
}
