// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Authentication.Resources;

namespace Eunomia.Server.Authentication.Services;

/// <summary>
/// Resolves an OAuth callback <c>code</c> to a persisted user. Shared by the JWT surface
/// (<c>/token</c>) and the browser cookie login (<c>/account/callback</c>) so both agree on how a code
/// becomes an identity, and so the cookie flow does not have to call back into the server over HTTP.
/// </summary>
public interface IOAuthLoginService
{
    /// <summary>
    /// Consumes <paramref name="code"/>, exchanges it with the provider that issued it, and ensures the
    /// resulting user exists. <paramref name="redirectUri"/> must be the same value that was sent to the
    /// provider on the authorize hop.
    /// </summary>
    Task<OAuthLoginResult> ResolveCodeAsync(string code, string redirectUri);
}
