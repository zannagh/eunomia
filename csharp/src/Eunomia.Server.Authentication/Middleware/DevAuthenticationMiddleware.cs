// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Security.Claims;
using Eunomia.Server.Authentication.Services;
using Eunomia.Server.Data.Entities;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.DependencyInjection;

namespace Eunomia.Server.Authentication.Middleware;

/// <summary>
/// Development-only bypass that auto-signs-in as <c>EUNOMIA_DEV_USER</c> (a <c>{name}__{id}</c>
/// identifier) so the dashboard can be exercised without real OAuth. Only registered when the env var
/// is set. The signed-in cookie carries the user's real role (admin-promoted when configured), so
/// role-gated UI can be exercised too.
/// </summary>
public class DevAuthenticationMiddleware
{
    private readonly RequestDelegate next;
    private readonly string identifier;
    private readonly string name;
    private readonly string id;

    public DevAuthenticationMiddleware(RequestDelegate next)
    {
        this.next = next;

        identifier = Environment.GetEnvironmentVariable("EUNOMIA_DEV_USER") ?? "Dev Admin__dev-admin";
        int sep = identifier.IndexOf("__", StringComparison.Ordinal);
        if (sep > 0)
        {
            name = identifier[..sep];
            id = identifier[(sep + 2)..];
        }
        else
        {
            name = identifier;
            id = identifier;
        }
    }

    public async Task InvokeAsync(HttpContext context)
    {
        if (context.User.Identity?.IsAuthenticated != true
            && !context.Request.Path.StartsWithSegments("/account")
            && !context.Request.Path.StartsWithSegments("/_framework")
            && !context.Request.Path.StartsWithSegments("/css")
            && !context.Request.Path.StartsWithSegments("/js"))
        {
            ICurrentUserService currentUserService = context.RequestServices.GetRequiredService<ICurrentUserService>();
            User user = await currentUserService.EnsureUserAsync(identifier);

            List<Claim> claims =
            [
                new(ClaimTypes.NameIdentifier, id),
                new(ClaimTypes.Name, name),
                new("Name", name),
                new(ClaimTypes.Role, user.Role.ToString()),
            ];

            ClaimsPrincipal principal = new(new ClaimsIdentity(claims, CookieAuthenticationDefaults.AuthenticationScheme));
            await context.SignInAsync(CookieAuthenticationDefaults.AuthenticationScheme, principal);
            context.User = principal;
        }

        await next(context);
    }
}
