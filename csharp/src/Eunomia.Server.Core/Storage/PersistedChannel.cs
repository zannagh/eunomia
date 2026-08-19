// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Core.Storage;

/// <summary>
/// On-disk representation of one (scope, channel) entry set. The original, unsanitized scope and
/// channel are stored alongside the entries since the sanitized file path is not reversible.
/// </summary>
public sealed record PersistedChannel(string Scope, string Channel, Dictionary<string, string> Entries);
