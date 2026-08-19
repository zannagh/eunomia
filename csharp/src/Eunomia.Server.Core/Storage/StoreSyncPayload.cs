// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Core.Storage;

/// <summary>
/// A batch snapshot of every stored entry for one (scope, channel), pushed to a client on
/// (re)connect. <see cref="Entries"/> maps a key string to the payload re-serialized as a JSON
/// string, since the Java client deserializes each entry independently.
/// </summary>
public sealed record StoreSyncPayload(string Channel, IReadOnlyDictionary<string, string> Entries);
