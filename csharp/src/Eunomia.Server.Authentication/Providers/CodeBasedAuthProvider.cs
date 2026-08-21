// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Diagnostics.CodeAnalysis;
using Eunomia.Server.Authentication.Helpers;

namespace Eunomia.Server.Authentication.Providers;

/// <summary>
/// Remembers which provider issued a given authorization <c>code</c> between the <c>/oauth-callback</c>
/// hop and the token exchange, so the exchange knows whose endpoint to call. Entries are consumed on
/// lookup and expire individually after ten minutes.
/// </summary>
public class CodeBasedAuthProvider
{
    private static readonly TimeSpan Lifetime = TimeSpan.FromMinutes(10);

    private readonly ExpiringMap<string> codeIdentityProviders = new(Lifetime, TimeSpan.FromMinutes(1));

    public void AddCodeIdentityProvider(string code, string provider)
    {
        codeIdentityProviders.Add(code, provider);
    }

    public bool GetIdentityProviderByCode(string code, [MaybeNullWhen(false)] out string? provider)
    {
        return codeIdentityProviders.TryConsume(code, out provider);
    }
}
