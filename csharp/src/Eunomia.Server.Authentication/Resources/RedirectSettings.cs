// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Authentication.Resources;

/// <summary>The provider and return URI stashed against an OAuth <c>state</c> value.</summary>
public struct RedirectSettings
{
    public required string Uri { get; set; }

    public required string Provider { get; set; }

    /// <summary>
    /// The user this state was issued to, for flows started by an already-signed-in user (account
    /// linking). The callback refuses the state if it is presented by anyone else, so a state minted by
    /// an attacker cannot be replayed in a victim's browser to bind the attacker's external identity to
    /// the victim's account. Null for the anonymous login flow, which has no user yet.
    /// </summary>
    public Guid? OwnerUserId { get; set; }
}
