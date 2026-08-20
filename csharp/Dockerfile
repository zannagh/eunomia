# Multi-stage build for the eunomia C# server (Eunomia.Server.Web). The build stage restores/publishes
# with the SDK image; the runtime stage only carries the published output on the smaller ASP.NET image.
#
# Build context is csharp/ (see .github/workflows/docker-publish.yml: context: csharp, file: csharp/Dockerfile).

FROM --platform=$BUILDPLATFORM mcr.microsoft.com/dotnet/sdk:10.0 AS build
ARG TARGETARCH
WORKDIR /src

# Copy the shared build/versioning config and every csproj first so `dotnet restore` is cached across
# source-only changes.
COPY Directory.Build.props Directory.Packages.props global.json .stylecop.json Directory.Versioning.targets ./
COPY src/Eunomia.Server.Core/Eunomia.Server.Core.csproj src/Eunomia.Server.Core/
COPY src/Eunomia.Server.Api/Eunomia.Server.Api.csproj src/Eunomia.Server.Api/
COPY src/Eunomia.Server.Web/Eunomia.Server.Web.csproj src/Eunomia.Server.Web/
# SkipEunomiaGitVersion bypasses Directory.Versioning.targets' git-based versioning, which would
# otherwise shell out to `dotnet-gitversion`/`git` against a .git directory the build context omits.
RUN dotnet restore src/Eunomia.Server.Web/Eunomia.Server.Web.csproj -a $TARGETARCH -p:SkipEunomiaGitVersion=true

COPY src/ src/

# Publish an optimized Release build. The git-based versioning is skipped (no .git in the build
# context); the real semver is injected as APP_VERSION by the publish workflow (default 0.0.0 for a
# plain `docker build`) and also applied as the image tag.
ARG APP_VERSION=0.0.0
RUN dotnet publish src/Eunomia.Server.Web/Eunomia.Server.Web.csproj -c Release -o /app -a $TARGETARCH --no-restore \
    -p:SkipEunomiaGitVersion=true -p:Version=$APP_VERSION

FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
WORKDIR /app
COPY --from=build /app .

ENV ASPNETCORE_URLS=http://+:8080
EXPOSE 8080

ENTRYPOINT ["dotnet", "Eunomia.Server.Web.dll"]
