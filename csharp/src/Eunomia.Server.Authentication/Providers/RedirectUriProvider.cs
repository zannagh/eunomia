// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Diagnostics.CodeAnalysis;
using Eunomia.Server.Authentication.Helpers;
using Eunomia.Server.Authentication.Resources;

namespace Eunomia.Server.Authentication.Providers;

/// <summary>
/// Short-lived, in-memory map from an OAuth <c>state</c> value to the provider/return URI it belongs to.
/// Entries are consumed (removed) on lookup and expire individually after ten minutes, so an abandoned
/// login only drops its own state rather than everyone else's in-flight handshakes.
/// </summary>
public class RedirectUriProvider
{
    private static readonly TimeSpan Lifetime = TimeSpan.FromMinutes(10);

    private readonly ExpiringMap<RedirectSettings> stateRedirectUris = new(Lifetime, TimeSpan.FromMinutes(1));

    public void AddRedirectUri(string state, RedirectSettings redirectSettings)
    {
        stateRedirectUris.Add(state, redirectSettings);
    }

    public bool GetRedirectUri(string state, [MaybeNullWhen(false)] out RedirectSettings redirectSettings)
    {
        return stateRedirectUris.TryConsume(state, out redirectSettings);
    }
}
