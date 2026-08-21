// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Authentication.Resources;

/// <summary>The provider and return URI stashed against an OAuth <c>state</c> value.</summary>
public struct RedirectSettings
{
    public required string Uri { get; set; }

    public required string Provider { get; set; }
}
