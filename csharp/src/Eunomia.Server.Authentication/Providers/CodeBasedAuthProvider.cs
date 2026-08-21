// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Collections.Concurrent;
using System.Diagnostics.CodeAnalysis;
using Eunomia.Server.Authentication.Helpers;

namespace Eunomia.Server.Authentication.Providers;

/// <summary>
/// Remembers which provider issued a given authorization <c>code</c> between the <c>/oauth-callback</c>
/// hop and the <c>/token</c> exchange, so the exchange knows whose endpoint to call. Entries are
/// consumed on lookup and cleared periodically.
/// </summary>
public class CodeBasedAuthProvider
{
    private ConcurrentDictionary<string, string> CodeIdentityProviders { get; } = new();

    public CodeBasedAuthProvider()
    {
        RecurringTask.Create(
            () => CodeIdentityProviders.Clear(),
            TimeSpan.FromMinutes(10),
            CancellationToken.None);
    }

    public void AddCodeIdentityProvider(string code, string provider)
    {
        CodeIdentityProviders.TryAdd(code, provider);
    }

    public bool GetIdentityProviderByCode(string code, [MaybeNullWhen(false)] out string? provider)
    {
        return CodeIdentityProviders.TryRemove(code, out provider);
    }
}
