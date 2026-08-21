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
    private readonly RequestDelegate _next;
    private readonly string _identifier;
    private readonly string _name;
    private readonly string _id;

    public DevAuthenticationMiddleware(RequestDelegate next)
    {
        _next = next;

        _identifier = Environment.GetEnvironmentVariable("EUNOMIA_DEV_USER") ?? "Dev Admin__dev-admin";
        int sep = _identifier.IndexOf("__", StringComparison.Ordinal);
        if (sep > 0)
        {
            _name = _identifier[..sep];
            _id = _identifier[(sep + 2)..];
        }
        else
        {
            _name = _identifier;
            _id = _identifier;
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
            User user = await currentUserService.EnsureUserAsync(_identifier);

            List<Claim> claims =
            [
                new(ClaimTypes.NameIdentifier, _id),
                new(ClaimTypes.Name, _name),
                new("Name", _name),
                new(ClaimTypes.Role, user.Role.ToString()),
            ];

            ClaimsPrincipal principal = new(new ClaimsIdentity(claims, CookieAuthenticationDefaults.AuthenticationScheme));
            await context.SignInAsync(CookieAuthenticationDefaults.AuthenticationScheme, principal);
            context.User = principal;
        }

        await _next(context);
    }
}
