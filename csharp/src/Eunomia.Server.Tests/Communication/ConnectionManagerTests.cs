// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Core.Clients;
using Eunomia.Server.Core.Communication;

namespace Eunomia.Server.Tests.Communication;

public class ConnectionManagerTests
{
    [Fact]
    public void IsConnected_ReturnsTrueOnlyForRegisteredClientInThatScope()
    {
        ConnectionManager manager = new();
        EunomiaClient client = NewClient("scope-a");

        Assert.False(manager.IsConnected("scope-a", client.Id));

        manager.OnConnectionAdded(client);

        Assert.True(manager.IsConnected("scope-a", client.Id));
        Assert.False(manager.IsConnected("scope-b", client.Id));
        Assert.False(manager.IsConnected("scope-a", Guid.NewGuid()));
    }

    [Fact]
    public void OnConnectionAdded_IsolatesClientsByScope()
    {
        ConnectionManager manager = new();
        EunomiaClient clientA = NewClient("scope-a");
        EunomiaClient clientB = NewClient("scope-b");

        Assert.True(manager.OnConnectionAdded(clientA));
        Assert.True(manager.OnConnectionAdded(clientB));

        Assert.True(manager.IsConnected("scope-a", clientA.Id));
        Assert.False(manager.IsConnected("scope-b", clientA.Id));
        Assert.True(manager.IsConnected("scope-b", clientB.Id));
    }

    [Fact]
    public void OnConnectionRemoved_MakesClientNoLongerConnected()
    {
        ConnectionManager manager = new();
        EunomiaClient client = NewClient("scope-a");
        manager.OnConnectionAdded(client);

        manager.OnConnectionRemoved("scope-a", client.Id);

        Assert.False(manager.IsConnected("scope-a", client.Id));
    }

    [Fact]
    public void OnConnectionAdded_RejectsConnectionsPastPerScopeCap()
    {
        ConnectionManager manager = new();
        const string scope = "capped-scope";

        for (int i = 0; i < 500; i++)
        {
            Assert.True(manager.OnConnectionAdded(NewClient(scope)));
        }

        EunomiaClient overflow = NewClient(scope);
        Assert.False(manager.OnConnectionAdded(overflow));
        Assert.False(manager.IsConnected(scope, overflow.Id));
    }

    [Fact]
    public void OnConnectionAdded_RejectsConnectionsPastPerIpCap()
    {
        ConnectionManager manager = new();
        const string remoteIp = "10.0.0.1";

        for (int i = 0; i < 20; i++)
        {
            Assert.True(manager.OnConnectionAdded(NewClient($"scope-{i}"), remoteIp));
        }

        EunomiaClient overflow = NewClient("scope-overflow");
        Assert.False(manager.OnConnectionAdded(overflow, remoteIp));
        Assert.False(manager.IsConnected("scope-overflow", overflow.Id));
    }

    [Fact]
    public void OnConnectionRemoved_FreesUpPerIpCapSlot()
    {
        ConnectionManager manager = new();
        const string remoteIp = "10.0.0.2";
        List<EunomiaClient> clients = new();

        for (int i = 0; i < 20; i++)
        {
            EunomiaClient client = NewClient($"scope-{i}");
            clients.Add(client);
            Assert.True(manager.OnConnectionAdded(client, remoteIp));
        }

        manager.OnConnectionRemoved(clients[0].Scope, clients[0].Id, remoteIp);

        EunomiaClient replacement = NewClient("scope-replacement");
        Assert.True(manager.OnConnectionAdded(replacement, remoteIp));
    }

    private static EunomiaClient NewClient(string scope)
    {
        return new EunomiaClient(Guid.NewGuid()) { Scope = scope };
    }
}
