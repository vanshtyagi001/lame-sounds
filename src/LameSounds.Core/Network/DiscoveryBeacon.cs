using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace LameSounds.Core.Network
{
    public class DiscoveryBeacon : IDisposable
    {
        private const int DiscoveryPort = 50052;
        private UdpClient? _udpClient;
        private CancellationTokenSource? _cts;
        private string _ip = "127.0.0.1";
        private int _httpPort = 50050;
        private int _udpPort = 50051;

        public void Start(string ip, int httpPort, int udpPort)
        {
            Stop();

            _ip = ip;
            _httpPort = httpPort;
            _udpPort = udpPort;
            _cts = new CancellationTokenSource();

            try
            {
                _udpClient = new UdpClient();
                _udpClient.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
                _udpClient.Client.Bind(new IPEndPoint(IPAddress.Any, DiscoveryPort));
                _udpClient.EnableBroadcast = true;

                Task.Run(() => BroadcastLoopAsync(_cts.Token));
                Task.Run(() => ListenForProbesAsync(_cts.Token));
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[DiscoveryBeacon] Start error: {ex.Message}");
            }
        }

        private async Task BroadcastLoopAsync(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested && _udpClient != null)
            {
                try
                {
                    var msg = GetBeaconJson();
                    var bytes = Encoding.UTF8.GetBytes(msg);
                    var bcastAddresses = NetworkManager.GetBroadcastAddresses();
                    foreach (var bcast in bcastAddresses)
                    {
                        try
                        {
                            await _udpClient.SendAsync(bytes, bytes.Length, new IPEndPoint(bcast, DiscoveryPort));
                        }
                        catch { }
                    }
                }
                catch { }

                try
                {
                    await Task.Delay(2500, ct);
                }
                catch (OperationCanceledException)
                {
                    break;
                }
            }
        }

        private async Task ListenForProbesAsync(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested && _udpClient != null)
            {
                try
                {
                    var res = await _udpClient.ReceiveAsync(ct);
                    var str = Encoding.UTF8.GetString(res.Buffer);
                    if (str.Contains("whois_soundboard"))
                    {
                        var responseBytes = Encoding.UTF8.GetBytes(GetBeaconJson());
                        await _udpClient.SendAsync(responseBytes, responseBytes.Length, res.RemoteEndPoint);
                    }
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch { }
            }
        }

        private string GetBeaconJson()
        {
            return JsonSerializer.Serialize(new
            {
                app = "LameSounds",
                version = "1.0",
                machineName = Environment.MachineName,
                ip = _ip,
                httpPort = _httpPort,
                udpPort = _udpPort
            });
        }

        public void Stop()
        {
            _cts?.Cancel();
            _cts?.Dispose();
            _cts = null;

            _udpClient?.Close();
            _udpClient?.Dispose();
            _udpClient = null;
        }

        public void Dispose()
        {
            Stop();
        }
    }
}
