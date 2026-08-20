using System.Diagnostics;

namespace Eunomia.Server.Core.Telemetry;

public class OperationTimer : IDisposable
{
    private readonly string _operation;
    private readonly Guid? _serverId;
    private readonly Stopwatch _stopWatch;
    private readonly Activity? _activity;

    internal OperationTimer(string operation, Guid? serverId)
    {
        _operation = operation;
        _serverId = serverId;
        _stopWatch = new Stopwatch();
        _stopWatch.Start();
        _activity = EunomiaTelemetry.ActivitySource.StartActivity(operation);

        if (_activity != null && serverId.HasValue)
        {
            _activity.SetTag("server", serverId);
        }
    }

    /// <summary>
    /// Marks the operation (span) as failed and records the exception on it. The duration is
    /// still recorded on dispose, tagged <c>status=error</c>.
    /// </summary>
    public void Fail(Exception ex)
    {
        _activity?.SetStatus(ActivityStatusCode.Error, ex.Message);
        _activity?.AddException(ex);
    }

    public void Dispose()
    {
        var elapsedMs = _stopWatch.ElapsedMilliseconds;

        var tags = new TagList { { "operation", _operation } };
        if (_serverId.HasValue)
        {
            tags.Add("server", _serverId);
        }

        if (_activity?.Status == ActivityStatusCode.Error)
        {
            tags.Add("status", "error");
        }

        EunomiaMetrics.OperationDuration.Record(elapsedMs, tags);
        _activity?.Dispose();
    }
}
