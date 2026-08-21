// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Authentication.Services;

/// <summary>
/// A single external account (e.g. Modrinth, Discord) linked to a user, projected for the admin
/// user-management view so an admin can recognise a person by their linked handles.
/// </summary>
public sealed record UserAdminLink(string Provider, string Handle, string ExternalId);
