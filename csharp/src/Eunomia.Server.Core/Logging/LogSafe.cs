// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Core.Logging;

/// <summary>
/// Neutralises client-supplied strings before they reach a log message. Scopes, channels, keys, and
/// reported server names all arrive from unauthenticated callers; written verbatim to a line-oriented
/// sink they can embed newlines and forge additional log entries, so control characters are replaced
/// and over-long values are clipped.
/// </summary>
public static class LogSafe
{
    private const int MaxLoggedLength = 256;

    private const string Replacement = "␣";

    /// <summary>Returns a single-line, length-bounded rendering of a client-supplied value.</summary>
    public static string Value(string? value)
    {
        if (string.IsNullOrEmpty(value))
        {
            return string.Empty;
        }

        string clipped = value.Length > MaxLoggedLength
            ? value[..MaxLoggedLength] + "..."
            : value;

        return string.Create(clipped.Length, clipped, static (span, source) =>
        {
            for (int i = 0; i < source.Length; i++)
            {
                char current = source[i];
                span[i] = char.IsControl(current) ? Replacement[0] : current;
            }
        });
    }
}
