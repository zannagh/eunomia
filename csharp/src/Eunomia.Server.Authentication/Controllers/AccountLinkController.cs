// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Authentication.Helpers;
using Eunomia.Server.Authentication.Providers;
using Eunomia.Server.Authentication.Resources;
using Eunomia.Server.Authentication.Services;
using Eunomia.Server.Data.Entities;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Eunomia.Server.Authentication.Controllers;

/// <summary>
/// Signed-in account linking for Modrinth/Discord. The Profile page starts a link at
/// <c>/account/link/{provider}</c>; the provider returns to <c>/account/link/{provider}/callback</c>,
/// which stores the link against the current user. Linking requires an authenticated cookie (any role).
/// </summary>
[Authorize]
public class AccountLinkController : Controller
{
    private readonly IAccountLinkService accountLinkService;
    private readonly RedirectUriProvider redirectUriProvider;
    private readonly ICurrentUserService currentUserService;

    public AccountLinkController(
        IAccountLinkService accountLinkService,
        RedirectUriProvider redirectUriProvider,
        ICurrentUserService currentUserService)
    {
        this.accountLinkService = accountLinkService;
        this.redirectUriProvider = redirectUriProvider;
        this.currentUserService = currentUserService;
    }

    private string BaseUrl => $"{Request.Scheme}://{Request.Host}";

    [HttpGet("/account/link/{provider}")]
    public async Task<IActionResult> StartLink(string provider)
    {
        if (!accountLinkService.IsLinkable(provider))
        {
            return Redirect("/profile?error=link_unavailable");
        }

        string state = Guid.NewGuid().ToString();
        string redirectUri = $"{BaseUrl}/account/link/{provider}/callback";
        string? authorizeUrl = accountLinkService.BuildAuthorizeUrl(provider, state, redirectUri);
        if (authorizeUrl is null || !ProviderRedirect.IsAllowed(authorizeUrl))
        {
            return Redirect("/profile?error=link_unavailable");
        }

        User owner = await currentUserService.GetCurrentUserAsync();
        redirectUriProvider.AddRedirectUri(state, new RedirectSettings
        {
            Uri = redirectUri,
            Provider = provider,
            OwnerUserId = owner.Id,
        });
        return Redirect(authorizeUrl);
    }

    [HttpGet("/account/link/{provider}/callback")]
    public async Task<IActionResult> LinkCallback(string provider, [FromQuery] string code, [FromQuery] string state)
    {
        if (string.IsNullOrEmpty(code) || string.IsNullOrEmpty(state))
        {
            return Redirect("/profile?error=link_failed");
        }

        if (!redirectUriProvider.GetRedirectUri(state, out RedirectSettings redirect) || redirect.Provider != provider)
        {
            return Redirect("/profile?error=link_state");
        }

        User user = await currentUserService.GetCurrentUserAsync();

        // The state must have been minted by *this* user's StartLink. Without this, anyone could start a
        // link, keep the code+state, and have a signed-in victim open the callback URL - binding the
        // attacker's external identity to the victim's account (and, via UpsertLinkAsync's conflict
        // cleanup, stripping it from its rightful owner).
        if (redirect.OwnerUserId != user.Id)
        {
            return Redirect("/profile?error=link_state");
        }

        string redirectUri = $"{BaseUrl}/account/link/{provider}/callback";
        UserExternalLink? link = await accountLinkService.CompleteLinkAsync(provider, code, redirectUri, user.Id);

        return Redirect(link is null
            ? "/profile?error=link_failed"
            : $"/profile?linked={Uri.EscapeDataString(provider)}");
    }

    [HttpGet("/account/links")]
    [Produces("application/json")]
    public async Task<IActionResult> MyLinks()
    {
        User user = await currentUserService.GetCurrentUserAsync();
        IReadOnlyList<UserExternalLink> links = await accountLinkService.GetLinksAsync(user.Id);
        return Ok(links.Select(l => new { l.Provider, l.ExternalId, l.Handle, l.LinkedAt }));
    }
}
