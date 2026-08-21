// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Core.Storage;

/// <summary>
/// Length ceilings for the client-supplied identifiers that end up in persisted columns. These mirror
/// the column widths configured on the entities: rejecting an over-long value at the edge turns what
/// would otherwise surface as a Postgres <c>22001 value too long</c> (thrown well after the request was
/// accepted) into a plain client error.
/// </summary>
public static class StorageLimits
{
    /// <summary>Maximum length of a scope, channel, key, or reported server name.</summary>
    public const int MaxIdentifierLength = 512;

    /// <summary>Whether a client-supplied identifier fits its persisted column.</summary>
    public static bool IsWithinLimit(string? value)
    {
        return value is null || value.Length <= MaxIdentifierLength;
    }
}
