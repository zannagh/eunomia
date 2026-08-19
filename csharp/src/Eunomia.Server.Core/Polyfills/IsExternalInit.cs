// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace System.Runtime.CompilerServices;

/// <summary>
/// Polyfill required for C# 9+ <c>init</c> accessors and records to compile against
/// netstandard2.1, which predates this runtime marker type.
/// </summary>
internal static class IsExternalInit
{
}
