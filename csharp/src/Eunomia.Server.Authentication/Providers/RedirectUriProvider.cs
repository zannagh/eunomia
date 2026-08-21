// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Collections.Concurrent;
using System.Diagnostics.CodeAnalysis;
using Eunomia.Server.Authentication.Helpers;
using Eunomia.Server.Authentication.Resources;

namespace Eunomia.Server.Authentication.Providers;

/// <summary>
/// Short-lived, in-memory map from an OAuth <c>state</c> value to the provider/return URI it belongs to.
/// Entries are consumed (removed) on lookup and the whole map is periodically cleared so abandoned
/// logins do not accumulate.
/// </summary>
public class RedirectUriProvider
{
    private ConcurrentDictionary<string, RedirectSettings> StateRedirectUris { get; } = new();

    public RedirectUriProvider()
    {
        RecurringTask.Create(
            () => StateRedirectUris.Clear(),
            TimeSpan.FromMinutes(10),
            CancellationToken.None);
    }

    public void AddRedirectUri(string state, RedirectSettings redirectSettings)
    {
        StateRedirectUris.TryAdd(state, redirectSettings);
    }

    public bool GetRedirectUri(string state, [MaybeNullWhen(false)] out RedirectSettings redirectSettings)
    {
        return StateRedirectUris.TryRemove(state, out redirectSettings);
    }
}
